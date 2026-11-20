package com.example.core

import com.example.data.model.AnswerStatus
import com.example.data.model.ExamAnswerKey
import com.example.data.model.Examination
import com.example.data.model.GradingStatus
import com.example.data.model.QuestionAnalysis
import com.example.data.model.QuestionType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

data class EvaluationOutcome(
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val unattemptedCount: Int,
    val pendingManualCount: Int,
    val totalMarks: Double,
    val obtainedMarks: Double,
    val percentage: Double,
    val passed: Boolean,
    val gradingStatus: GradingStatus,
    val questionAnalyses: List<QuestionAnalysis>
)

object EvaluationEngine {

    fun evaluate(
        exam: Examination,
        answerKey: ExamAnswerKey,
        responses: Map<String, String>
    ): EvaluationOutcome {
        var correct = 0
        var wrong = 0
        var unattempted = 0
        var pendingManual = 0
        var totalMarks = 0.0
        var obtainedMarks = 0.0
        val analyses = mutableListOf<QuestionAnalysis>()

        exam.questions.forEach { q ->
            val qMarks = if (q.marks > 0.0) q.marks else answerKey.marksPerQuestion
            totalMarks += qMarks
            val rawStudent = responses[q.questionId]?.trim().orEmpty()
            val rawCorrect = answerKey.answers[q.questionId]?.trim().orEmpty()
            val qType = QuestionType.fromRaw(q.questionType)

            when {
                rawStudent.isBlank() -> {
                    unattempted++
                    analyses.add(
                        QuestionAnalysis(
                            questionId = q.questionId,
                            questionText = q.text,
                            questionType = qType.name,
                            options = q.options,
                            studentAnswer = "",
                            correctAnswer = rawCorrect,
                            status = AnswerStatus.UNATTEMPTED.name,
                            marksObtained = 0.0,
                            maxMarks = qMarks,
                            needsManualReview = false
                        )
                    )
                }

                qType == QuestionType.SHORT_ANSWER -> {
                    pendingManual++
                    analyses.add(
                        QuestionAnalysis(
                            questionId = q.questionId,
                            questionText = q.text,
                            questionType = qType.name,
                            options = q.options,
                            studentAnswer = rawStudent,
                            correctAnswer = rawCorrect,
                            status = AnswerStatus.PENDING.name,
                            marksObtained = 0.0,
                            maxMarks = qMarks,
                            needsManualReview = true
                        )
                    )
                }

                else -> {
                    val matched = isAnswerMatch(qType, rawStudent, rawCorrect, q.options)
                    if (matched) {
                        correct++
                        obtainedMarks += qMarks
                        analyses.add(
                            QuestionAnalysis(
                                questionId = q.questionId,
                                questionText = q.text,
                                questionType = qType.name,
                                options = q.options,
                                studentAnswer = rawStudent,
                                correctAnswer = rawCorrect,
                                status = AnswerStatus.CORRECT.name,
                                marksObtained = qMarks,
                                maxMarks = qMarks,
                                needsManualReview = false
                            )
                        )
                    } else {
                        wrong++
                        val deduction = answerKey.negativeMarks
                        obtainedMarks -= deduction
                        analyses.add(
                            QuestionAnalysis(
                                questionId = q.questionId,
                                questionText = q.text,
                                questionType = qType.name,
                                options = q.options,
                                studentAnswer = rawStudent,
                                correctAnswer = rawCorrect,
                                status = AnswerStatus.WRONG.name,
                                marksObtained = if (deduction > 0.0) -deduction else 0.0,
                                maxMarks = qMarks,
                                needsManualReview = false
                            )
                        )
                    }
                }
            }
        }

        val safeTotal = if (totalMarks > 0.0) totalMarks else max(exam.totalMarks, 1.0)
        val rawPct = if (safeTotal > 0.0) (max(0.0, obtainedMarks) / safeTotal) * 100.0 else 0.0
        val clampedPct = round(min(100.0, max(0.0, rawPct)) * 10.0) / 10.0
        val passed = clampedPct >= answerKey.passingPercentage
        val gradingStatus = if (pendingManual > 0) GradingStatus.PENDING_REVIEW else GradingStatus.FINALIZED

        return EvaluationOutcome(
            totalQuestions = exam.questions.size.coerceAtLeast(1),
            correctCount = correct,
            wrongCount = wrong,
            unattemptedCount = unattempted,
            pendingManualCount = pendingManual,
            totalMarks = round(safeTotal * 100.0) / 100.0,
            obtainedMarks = round(obtainedMarks * 100.0) / 100.0,
            percentage = clampedPct,
            passed = passed,
            gradingStatus = gradingStatus,
            questionAnalyses = analyses
        )
    }

    private fun isAnswerMatch(
        type: QuestionType,
        student: String,
        correct: String,
        options: List<String>
    ): Boolean {
        if (correct.isBlank()) return false
        return when (type) {
            QuestionType.MULTI_SELECT -> {
                val sSet = student.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
                val cSet = correct.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
                sSet.isNotEmpty() && sSet == cSet
            }
            QuestionType.MCQ -> {
                val sNorm = student.trim().uppercase()
                val cNorm = correct.trim().uppercase()
                if (sNorm == cNorm) return true
                // Also support matching option letter ("A") against option text or vice versa
                val letterIdx = when (cNorm) {
                    "A" -> 0; "B" -> 1; "C" -> 2; "D" -> 3; "E" -> 4; else -> -1
                }
                if (letterIdx in options.indices && options[letterIdx].trim().equals(student.trim(), ignoreCase = true)) {
                    return true
                }
                false
            }
            QuestionType.TRUE_FALSE -> {
                student.trim().equals(correct.trim(), ignoreCase = true)
            }
            QuestionType.FILL_BLANK -> {
                val sClean = student.trim().replace(Regex("\\s+"), " ")
                correct.split("|").any { variant ->
                    variant.trim().replace(Regex("\\s+"), " ").equals(sClean, ignoreCase = true)
                }
            }
            QuestionType.SHORT_ANSWER -> false
        }
    }

    fun formatDuration(seconds: Int): String {
        val safe = max(0, seconds)
        val hrs = safe / 3600
        val mins = (safe % 3600) / 60
        val secs = safe % 60
        return if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
