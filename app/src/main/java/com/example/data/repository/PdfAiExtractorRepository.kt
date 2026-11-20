package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.DifficultyLevel
import com.example.data.model.ExtractedDraftQuestion
import com.example.data.model.QuestionType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class PdfExtractionProgress(
    val stageLabel: String,
    val progressFraction: Float,
    val pagesProcessed: Int,
    val totalPages: Int
)

data class PdfExtractionResult(
    val detectedPaperTitle: String,
    val detectedSubject: String,
    val pagesProcessed: Int,
    val totalPagesInPdf: Int,
    val answerKeyFoundInPdf: Boolean,
    val fileSizeBytes: Long,
    val questions: List<ExtractedDraftQuestion>
)

class PdfAiExtractorRepository(private val appContext: Context) {

    private val responseSchema = Schema.obj(
        mapOf(
            "paperTitle" to Schema.string("Detected title of the question paper"),
            "primarySubject" to Schema.string("Primary computer/education subject such as MS Word, MS Excel, MS PowerPoint, MS Access & DBMS, Internet & Networking, Programming & Web, Operating Systems, or Computer Fundamentals"),
            "answerKeyFound" to Schema.boolean("True ONLY if the PDF explicitly contains an answer key or marked correct answers; false if no answer key is shown"),
            "questions" to Schema.array(
                Schema.obj(
                    mapOf(
                        "questionNumber" to Schema.integer("Original question number printed in the PDF, if visible"),
                        "questionText" to Schema.string("Clean question statement without leading Q1. numbering"),
                        "questionType" to Schema.enumeration(
                            listOf("MCQ", "MULTI_SELECT", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER"),
                            "Question format type"
                        ),
                        "options" to Schema.array(Schema.string("Answer option text without (A)/(B) prefix")),
                        "correctAnswer" to Schema.string("Detected correct answer (e.g. A, B, C, D, True, False, comma-separated letters for MULTI_SELECT, or expected text). MUST be empty string if not explicitly provided in the PDF."),
                        "answerPresentInPdf" to Schema.boolean("True only if the correct answer was explicitly printed in the PDF"),
                        "subject" to Schema.string("Specific subject for this question"),
                        "chapter" to Schema.string("Chapter or module name if inferable, otherwise General"),
                        "topic" to Schema.string("Topic tag if inferable, otherwise Core Concepts"),
                        "difficulty" to Schema.enumeration(listOf("EASY", "MEDIUM", "HARD"), "Estimated difficulty")
                    ),
                    optionalProperties = listOf("questionNumber", "chapter", "topic")
                )
            )
        )
    )

    suspend fun extractQuestionsFromPdfUri(
        uri: Uri,
        fallbackFileName: String,
        onProgress: (PdfExtractionProgress) -> Unit = {}
    ): Result<PdfExtractionResult> = withContext(Dispatchers.IO) {
        try {
            // 1. Validate PDF MIME type & File Size before decoding
            onProgress(PdfExtractionProgress("Validating PDF file…", 0.05f, 0, 0))
            val mimeType = appContext.contentResolver.getType(uri).orEmpty()
            val isPdfMime = mimeType.equals("application/pdf", ignoreCase = true)
            val isPdfExt = fallbackFileName.endsWith(".pdf", ignoreCase = true)
            if (mimeType.isNotBlank() && !isPdfMime && !isPdfExt) {
                return@withContext Result.failure(
                    IllegalArgumentException("Unsupported file type ($mimeType). Please select a valid .pdf question paper.")
                )
            }

            var fileSizeBytes = 0L
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    fileSizeBytes = cursor.getLong(sizeIdx)
                }
            }
            if (fileSizeBytes > MAX_PDF_SIZE_BYTES) {
                val mb = fileSizeBytes / (1024.0 * 1024.0)
                return@withContext Result.failure(
                    IllegalArgumentException(
                        String.format("PDF is too large (%.1f MB). Maximum supported size is 25 MB.", mb)
                    )
                )
            }

            val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Unable to open the selected PDF file. Please verify file permissions.")
                )

            val statSize = pfd.statSize
            if (fileSizeBytes <= 0L && statSize > 0L) fileSizeBytes = statSize
            if (statSize == 0L) {
                pfd.close()
                return@withContext Result.failure(
                    IllegalArgumentException("The selected PDF file is empty (0 bytes).")
                )
            }
            if (fileSizeBytes > MAX_PDF_SIZE_BYTES) {
                pfd.close()
                return@withContext Result.failure(
                    IllegalArgumentException("PDF exceeds the 25 MB upload limit.")
                )
            }

            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = generationConfig {
                    temperature = 0.1f
                    responseMimeType = "application/json"
                    responseSchema = this@PdfAiExtractorRepository.responseSchema
                }
            )

            var totalPages = 0
            var pagesProcessed = 0
            var detectedTitle = fallbackFileName.removeSuffix(".pdf")
            var detectedSubject = "Computer Fundamentals"
            var anyAnswerKeyFound = false
            val allDrafts = mutableListOf<ExtractedDraftQuestion>()
            val seenFingerprints = mutableSetOf<String>()

            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    totalPages = renderer.pageCount
                    if (totalPages <= 0) {
                        return@withContext Result.failure(
                            IllegalStateException("The selected PDF contains 0 readable pages.")
                        )
                    }

                    val pagesToProcess = totalPages.coerceAtMost(MAX_TOTAL_PAGES)
                    var pageIdx = 0
                    while (pageIdx < pagesToProcess) {
                        val batchEnd = (pageIdx + PAGES_PER_AI_BATCH).coerceAtMost(pagesToProcess)
                        val bitmaps = mutableListOf<Bitmap>()
                        try {
                            onProgress(
                                PdfExtractionProgress(
                                    stageLabel = "Rendering pages ${pageIdx + 1}–$batchEnd of $pagesToProcess…",
                                    progressFraction = 0.10f + (pageIdx.toFloat() / pagesToProcess) * 0.40f,
                                    pagesProcessed = pageIdx,
                                    totalPages = pagesToProcess
                                )
                            )
                            for (i in pageIdx until batchEnd) {
                                renderer.openPage(i).use { page ->
                                    val targetWidth = 1050
                                    val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1).coerceAtMost(2400)
                                    val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bmp)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmaps.add(bmp)
                                }
                            }

                            onProgress(
                                PdfExtractionProgress(
                                    stageLabel = "Extracting questions (pages ${pageIdx + 1}–$batchEnd of $pagesToProcess)…",
                                    progressFraction = 0.35f + (batchEnd.toFloat() / pagesToProcess) * 0.55f,
                                    pagesProcessed = pageIdx,
                                    totalPages = pagesToProcess
                                )
                            )

                            val promptContent = content {
                                bitmaps.forEach { image(it) }
                                text(
                                    """
                                    You are an examination digitization assistant for Super Sonic Computers Education.
                                    Extract all examination questions visible across pages ${pageIdx + 1} to $batchEnd in exact sequential order.
                                    Rules:
                                    1. Support MCQ, MULTI_SELECT, TRUE_FALSE, FILL_BLANK, and SHORT_ANSWER questions.
                                    2. Preserve original question numbering in questionNumber and clean question text.
                                    3. Strip option prefixes like (A), A., (B), etc. from option text strings so options are clean text.
                                    4. CRITICAL: NEVER guess or fabricate correct answers if the PDF does not explicitly show an answer key or marked answer!
                                       If no answer key is printed, set answerKeyFound=false, answerPresentInPdf=false, and correctAnswer="".
                                    5. If an Answer Key IS explicitly printed on any page (e.g. Q1: C | Q2: B), map each question accurately and set answerPresentInPdf=true.
                                    6. Classify each question into the appropriate computer education subject and difficulty.
                                    """.trimIndent()
                                )
                            }

                            val responseText = withTimeout(65_000L) {
                                model.generateContent(promptContent).text.orEmpty()
                            }

                            if (responseText.isNotBlank()) {
                                val json = JSONObject(responseText)
                                if (pageIdx == 0) {
                                    detectedTitle = json.optString("paperTitle", detectedTitle).ifBlank { detectedTitle }
                                    detectedSubject = json.optString("primarySubject", detectedSubject).ifBlank { detectedSubject }
                                }
                                if (json.optBoolean("answerKeyFound", false)) {
                                    anyAnswerKeyFound = true
                                }
                                val qArr = json.optJSONArray("questions")
                                if (qArr != null) {
                                    for (k in 0 until qArr.length()) {
                                        val qObj = qArr.getJSONObject(k)
                                        val text = qObj.optString("questionText", "").trim()
                                        if (text.isEmpty()) continue

                                        // Prevent duplicate extraction across batches/pages
                                        val fp = fingerprintQuestion(text)
                                        if (!seenFingerprints.add(fp)) continue

                                        val optsArr = qObj.optJSONArray("options")
                                        val opts = mutableListOf<String>()
                                        if (optsArr != null) {
                                            for (m in 0 until optsArr.length()) {
                                                val opt = optsArr.optString(m).trim()
                                                if (opt.isNotEmpty()) opts.add(opt)
                                            }
                                        }
                                        val answerPresent = qObj.optBoolean("answerPresentInPdf", false)
                                        val detectedAns = if (answerPresent) qObj.optString("correctAnswer", "").trim() else ""
                                        val qType = QuestionType.fromRaw(qObj.optString("questionType", "MCQ"))
                                        val normalizedOpts = when {
                                            qType == QuestionType.TRUE_FALSE && opts.isEmpty() -> listOf("True", "False")
                                            else -> opts
                                        }
                                        allDrafts.add(
                                            ExtractedDraftQuestion(
                                                tempId = UUID.randomUUID().toString(),
                                                questionText = text,
                                                questionType = qType,
                                                options = normalizedOpts,
                                                correctAnswer = detectedAns,
                                                subject = qObj.optString("subject", detectedSubject).ifBlank { detectedSubject },
                                                chapter = qObj.optString("chapter", "General").ifBlank { "General" },
                                                topic = qObj.optString("topic", "Core Concepts").ifBlank { "Core Concepts" },
                                                difficulty = DifficultyLevel.fromRaw(qObj.optString("difficulty", "MEDIUM")),
                                                marks = 2.0,
                                                answerDetectedInPdf = answerPresent && detectedAns.isNotBlank(),
                                                approved = answerPresent && detectedAns.isNotBlank()
                                            )
                                        )
                                    }
                                }
                            }
                            pagesProcessed = batchEnd
                        } finally {
                            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                        }
                        pageIdx = batchEnd
                    }
                }
            }

            if (allDrafts.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "No readable examination questions were detected in this PDF. You can add questions manually in the Question Bank tab."
                    )
                )
            }

            onProgress(
                PdfExtractionProgress(
                    stageLabel = "Extracted ${allDrafts.size} questions from $pagesProcessed page(s)",
                    progressFraction = 1f,
                    pagesProcessed = pagesProcessed,
                    totalPages = totalPages
                )
            )

            Result.success(
                PdfExtractionResult(
                    detectedPaperTitle = detectedTitle,
                    detectedSubject = detectedSubject,
                    pagesProcessed = pagesProcessed,
                    totalPagesInPdf = totalPages,
                    answerKeyFoundInPdf = anyAnswerKeyFound,
                    fileSizeBytes = fileSizeBytes,
                    questions = allDrafts
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "PDF file permission denied", e)
            Result.failure(IllegalStateException("Unable to read the selected PDF (permission denied or corrupted file)."))
        } catch (e: Exception) {
            Log.e(TAG, "PDF AI extraction failed", e)
            Result.failure(
                IllegalStateException(
                    e.localizedMessage ?: "PDF extraction failed. Check your connection or add questions manually."
                )
            )
        }
    }

    companion object {
        private const val TAG = "PdfAiExtractor"
        private const val PAGES_PER_AI_BATCH = 6
        private const val MAX_TOTAL_PAGES = 18
        private const val MAX_PDF_SIZE_BYTES = 25L * 1024L * 1024L // 25 MB

        fun fingerprintQuestion(text: String): String {
            val normalized = text.trim().lowercase().replace(Regex("\\s+"), " ")
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.take(10).joinToString("") { "%02x".format(it) }
        }
    }
}
