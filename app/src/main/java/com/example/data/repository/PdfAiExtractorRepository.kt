package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
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
import java.util.UUID

data class PdfExtractionResult(
    val detectedPaperTitle: String,
    val detectedSubject: String,
    val pagesProcessed: Int,
    val totalPagesInPdf: Int,
    val answerKeyFoundInPdf: Boolean,
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
                    optionalProperties = listOf("chapter", "topic")
                )
            )
        )
    )

    suspend fun extractQuestionsFromPdfUri(
        uri: Uri,
        fallbackFileName: String
    ): Result<PdfExtractionResult> = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext Result.failure(IllegalArgumentException("Unable to open the selected PDF file."))

            var totalPages = 0
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    totalPages = renderer.pageCount
                    val pagesToRender = totalPages.coerceAtMost(MAX_PAGES_PER_EXTRACTION)
                    for (i in 0 until pagesToRender) {
                        renderer.openPage(i).use { page ->
                            val targetWidth = 1100
                            val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                }
            }

            if (bitmaps.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("The selected PDF contains no readable pages."))
            }

            val model = Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = generationConfig {
                    temperature = 0.1f
                    responseMimeType = "application/json"
                    responseSchema = this@PdfAiExtractorRepository.responseSchema
                }
            )

            val promptContent = content {
                bitmaps.forEach { image(it) }
                text(
                    """
                    You are an examination digitization assistant for Super Sonic Computers Education.
                    Extract all examination questions visible across these PDF pages.
                    Rules:
                    1. Support MCQ, MULTI_SELECT, TRUE_FALSE, FILL_BLANK, and SHORT_ANSWER questions.
                    2. Strip option prefixes like (A), A., (B), etc. from option text strings so options are clean text.
                    3. CRITICAL: Do NOT guess or fabricate correct answers if the PDF does not explicitly contain an answer key or highlighted/marked answers!
                       If no answer key is present in the PDF, set answerKeyFound=false, answerPresentInPdf=false, and correctAnswer="".
                    4. If an Answer Key section IS present on any page (e.g. Q1: C | Q2: B), map each question number accurately to its letter/answer and set answerPresentInPdf=true.
                    5. Classify each question into the most appropriate computer education subject and difficulty.
                    """.trimIndent()
                )
            }

            val responseText = withTimeout(60_000L) {
                model.generateContent(promptContent).text.orEmpty()
            }

            if (responseText.isBlank()) {
                return@withContext Result.failure(IllegalStateException("AI returned an empty response while reading the PDF."))
            }

            val json = JSONObject(responseText)
            val paperTitle = json.optString("paperTitle", fallbackFileName.removeSuffix(".pdf"))
            val primarySubject = json.optString("primarySubject", "Computer Fundamentals")
            val answerKeyFound = json.optBoolean("answerKeyFound", false)
            val qArr = json.optJSONArray("questions")
            val drafts = mutableListOf<ExtractedDraftQuestion>()

            if (qArr != null) {
                for (i in 0 until qArr.length()) {
                    val qObj = qArr.getJSONObject(i)
                    val optsArr = qObj.optJSONArray("options")
                    val opts = mutableListOf<String>()
                    if (optsArr != null) {
                        for (k in 0 until optsArr.length()) {
                            val opt = optsArr.optString(k).trim()
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
                    val text = qObj.optString("questionText", "").trim()
                    if (text.isNotEmpty()) {
                        drafts.add(
                            ExtractedDraftQuestion(
                                tempId = UUID.randomUUID().toString(),
                                questionText = text,
                                questionType = qType,
                                options = normalizedOpts,
                                correctAnswer = detectedAns,
                                subject = qObj.optString("subject", primarySubject).ifBlank { primarySubject },
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

            Result.success(
                PdfExtractionResult(
                    detectedPaperTitle = paperTitle,
                    detectedSubject = primarySubject,
                    pagesProcessed = bitmaps.size,
                    totalPagesInPdf = totalPages,
                    answerKeyFoundInPdf = answerKeyFound,
                    questions = drafts
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "PDF AI extraction failed", e)
            Result.failure(e)
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private companion object {
        const val TAG = "PdfAiExtractor"
        const val MAX_PAGES_PER_EXTRACTION = 6
    }
}
