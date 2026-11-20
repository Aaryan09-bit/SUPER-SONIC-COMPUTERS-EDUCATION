package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.ExamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExportManager {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val fileFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun exportResultsPdf(
        context: Context,
        reportTitle: String,
        subtitle: String,
        results: List<ExamResult>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outFile = File(dir, "SuperSonic_Report_${fileFmt.format(Date())}.pdf")
            val pdf = PdfDocument()

            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f

            val headerBgPaint = Paint().apply { color = Color.rgb(22, 75, 120) }
            val accentPaint = Paint().apply { color = Color.rgb(112, 197, 240) }
            val whiteTitlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val whiteSubPaint = Paint().apply {
                color = Color.rgb(215, 240, 253)
                textSize = 9.5f
                isAntiAlias = true
            }
            val sectionPaint = Paint().apply {
                color = Color.rgb(24, 52, 77)
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(24, 52, 77)
                textSize = 9.5f
                isAntiAlias = true
            }
            val mutedPaint = Paint().apply {
                color = Color.rgb(83, 110, 135)
                textSize = 8.5f
                isAntiAlias = true
            }
            val dividerPaint = Paint().apply {
                color = Color.rgb(214, 233, 248)
                strokeWidth = 1f
            }

            var pageNumber = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas

            fun drawHeader() {
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 76f, headerBgPaint)
                canvas.drawRect(0f, 76f, pageWidth.toFloat(), 80f, accentPaint)
                canvas.drawText("SUPER SONIC COMPUTERS EDUCATION", margin, 30f, whiteTitlePaint)
                canvas.drawText("Learn Smart. Practice More. Achieve Excellence.", margin, 46f, whiteSubPaint)
                canvas.drawText(
                    "$reportTitle  •  Generated ${dateFmt.format(Date())}",
                    margin,
                    63f,
                    whiteSubPaint
                )
            }

            drawHeader()
            var y = 104f
            canvas.drawText(subtitle, margin, y, sectionPaint)
            y += 18f

            val totalCount = results.size
            val passCount = results.count { it.passed }
            val avgPct = if (totalCount > 0) results.map { it.percentage }.average() else 0.0
            val passPct = if (totalCount > 0) (passCount * 100.0 / totalCount) else 0.0

            canvas.drawText(
                String.format(
                    Locale.getDefault(),
                    "Records: %d   |   Average Score: %.1f%%   |   Pass Rate: %.1f%% (%d/%d)",
                    totalCount, avgPct, passPct, passCount, totalCount
                ),
                margin,
                y,
                bodyPaint
            )
            y += 16f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 18f

            // Table columns
            val colStudent = margin
            val colExam = 175f
            val colScore = 365f
            val colPct = 440f
            val colStatus = 505f

            fun drawTableHeader() {
                canvas.drawText("Student", colStudent, y, sectionPaint)
                canvas.drawText("Examination", colExam, y, sectionPaint)
                canvas.drawText("Marks", colScore, y, sectionPaint)
                canvas.drawText("Percent", colPct, y, sectionPaint)
                canvas.drawText("Result", colStatus, y, sectionPaint)
                y += 8f
                canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
                y += 16f
            }

            drawTableHeader()

            results.forEach { r ->
                if (y > pageHeight - 60f) {
                    pdf.finishPage(page)
                    pageNumber++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    drawHeader()
                    y = 104f
                    drawTableHeader()
                }
                val studentLabel = "${r.studentName} (${r.studentCode.ifBlank { r.studentUid.take(6) }})".take(26)
                val examLabel = "${r.examTitle} [${r.subject}]".take(34)
                val marksLabel = String.format(Locale.getDefault(), "%.1f/%.1f", r.obtainedMarks, r.totalMarks)
                val pctLabel = String.format(Locale.getDefault(), "%.1f%%", r.percentage)
                val statusLabel = if (r.passed) "PASS" else "FAIL"

                canvas.drawText(studentLabel, colStudent, y, bodyPaint)
                canvas.drawText(examLabel, colExam, y, bodyPaint)
                canvas.drawText(marksLabel, colScore, y, bodyPaint)
                canvas.drawText(pctLabel, colPct, y, bodyPaint)
                canvas.drawText(statusLabel, colStatus, y, bodyPaint)
                y += 13f
                canvas.drawText(
                    "Correct: ${r.correctCount}  Wrong: ${r.wrongCount}  Unattempted: ${r.unattemptedCount}  Time: ${EvaluationEngine.formatDuration(r.timeTakenSeconds)}",
                    colStudent,
                    y,
                    mutedPaint
                )
                y += 11f
                canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
                y += 14f
            }

            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportResultsCsv(
        context: Context,
        results: List<ExamResult>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outFile = File(dir, "SuperSonic_Results_${fileFmt.format(Date())}.csv")
            val sb = StringBuilder()
            sb.appendLine("Student Name,Student Code,Batch,Assigned Teacher,Examination,Subject,Total Questions,Correct,Wrong,Unattempted,Obtained Marks,Total Marks,Percentage,Status,Time Taken,Submitted At")
            results.forEach { r ->
                val submittedStr = r.submittedAt?.toDate()?.let { dateFmt.format(it) } ?: ""
                sb.appendLine(
                    listOf(
                        csvEscape(r.studentName),
                        csvEscape(r.studentCode),
                        csvEscape(r.batch),
                        csvEscape(r.assignedTeacherName),
                        csvEscape(r.examTitle),
                        csvEscape(r.subject),
                        r.totalQuestions.toString(),
                        r.correctCount.toString(),
                        r.wrongCount.toString(),
                        r.unattemptedCount.toString(),
                        String.format(Locale.US, "%.2f", r.obtainedMarks),
                        String.format(Locale.US, "%.2f", r.totalMarks),
                        String.format(Locale.US, "%.1f", r.percentage),
                        if (r.passed) "PASS" else "FAIL",
                        EvaluationEngine.formatDuration(r.timeTakenSeconds),
                        csvEscape(submittedStr)
                    ).joinToString(",")
                )
            }
            outFile.writeText(sb.toString())
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Super Sonic Computers Education - Performance Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share or Save Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun csvEscape(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
