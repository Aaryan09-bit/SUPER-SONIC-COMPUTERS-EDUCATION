package com.example.core

import com.example.data.model.AnswerStatus
import com.example.data.model.ExamAnswerKey
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.GradingStatus
import com.example.data.model.QuestionAnalysis
import com.example.data.model.QuestionType
import java.util.Locale
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

data class ManualGradingRecalculation(
    val correctCount: Int,
    val wrongCount: Int,
    val unattemptedCount: Int,
    val pendingManualCount: Int,
    val obtainedMarks: Double,
    val percentage: Double,
    val passed: Boolean,
    val gradingStatus: GradingStatus,
    val updatedAnalyses: List<QuestionAnalysis>
)

data class ServerTimerSnapshot(
    val elapsedSeconds: Int,
    val remainingSeconds: Int,
    val expired: Boolean
)

/**
 * Single source of truth for deterministic examination evaluation, subjective grading
 * recalculation, and server-anchored timer calculations across Admin, Teacher, and Student portals.
 */
object EvaluationEngine {

    /**
     * Canonical Percentage formula:
     * Percentage = (Obtained Marks / Total Marks) * 100, clamped cleanly to [0.0, 100.0]
     * and rounded to 1 decimal place.
     */
    fun calculatePercentage(obtainedMarks: Double, totalMarks: Double): Double {
        if (totalMarks <= 0.0) return 0.0
        val rawPct = (max(0.0, obtainedMarks) / totalMarks) * 100.0
        return round(min(100.0, max(0.0, rawPct)) * 10.0) / 10.0
    }

    /**
     * Computes server-controlled elapsed & remaining exam time anchored to Firestore's
     * server createdAt timestamp (falling back to startedAtMillis while offline) and
     * bounded by the exam's absolute deadline if configured.
     */
    fun computeServerAnchoredTimer(
        durationMinutes: Int,
        serverCreatedAtMillis: Long?,
        fallbackStartedAtMillis: Long,
        previouslyRecordedElapsedSec: Int,
        deadlineAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): ServerTimerSnapshot {
        val totalAllowedSec = (durationMinutes * 60).coerceAtLeast(60)
        val anchorStart = when {
            serverCreatedAtMillis != null && serverCreatedAtMillis > 0L -> serverCreatedAtMillis
            fallbackStartedAtMillis > 0L -> fallbackStartedAtMillis
            else -> nowMillis
        }
        val elapsedByClock = ((nowMillis - anchorStart) / 1000L).toInt().coerceAtLeast(0)
        val safeElapsed = max(elapsedByClock, previouslyRecordedElapsedSec.coerceAtLeast(0))
            .coerceAtMost(totalAllowedSec)

        val remainingByDuration = (totalAllowedSec - safeElapsed).coerceAtLeast(0)
        val remainingByDeadline = if (deadlineAtMillis > 0L) {
            ((deadlineAtMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)
        } else {
            remainingByDuration
        }
        val effectiveRemaining = min(remainingByDuration, remainingByDeadline)
        return ServerTimerSnapshot(
            elapsedSeconds = safeElapsed,
            remainingSeconds = effectiveRemaining,
            expired = effectiveRemaining <= 0
        )
    }

    fun evaluate(
        exam: Examination,
        answerKey: ExamAnswerKey,
        responses: Map<String, String>
    ): EvaluationOutcome {
        require(exam.questions.isNotEmpty()) {
            "Cannot evaluate an empty examination paper (0 questions)."
        }

        var correct = 0
        var wrong = 0
        var unattempted = 0
        var pendingManual = 0
        var totalMarks = 0.0
        var obtainedMarks = 0.0
        val analyses = mutableListOf<QuestionAnalysis>()

        exam.questions.forEach { q ->
            val qMarks = if (q.marks > 0.0) q.marks else answerKey.marksPerQuestion.coerceAtLeast(0.5)
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
                        val deduction = answerKey.negativeMarks.coerceAtLeast(0.0)
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

        val safeTotal = round(max(totalMarks, 0.5) * 100.0) / 100.0
        val roundedObtained = round(obtainedMarks * 100.0) / 100.0
        val pct = calculatePercentage(roundedObtained, safeTotal)
        val passed = pct >= answerKey.passingPercentage
        val gradingStatus = if (pendingManual > 0) GradingStatus.PENDING_REVIEW else GradingStatus.FINALIZED

        return EvaluationOutcome(
            totalQuestions = exam.questions.size,
            correctCount = correct,
            wrongCount = wrong,
            unattemptedCount = unattempted,
            pendingManualCount = pendingManual,
            totalMarks = safeTotal,
            obtainedMarks = roundedObtained,
            percentage = pct,
            passed = passed,
            gradingStatus = gradingStatus,
            questionAnalyses = analyses
        )
    }

    /**
     * Deterministic recalculation when a teacher/admin grades a SHORT_ANSWER response.
     * Uses the exact same formula and passing percentage as automatic evaluation.
     */
    fun recalculateAfterManualGrading(
        result: ExamResult,
        questionId: String,
        awardedMarks: Double,
        passingPercentage: Double
    ): ManualGradingRecalculation {
        val updatedAnalyses = result.questionAnalyses.map { qa ->
            if (qa.questionId == questionId) {
                val safeMarks = round(awardedMarks.coerceIn(0.0, qa.maxMarks) * 100.0) / 100.0
                qa.copy(
                    marksObtained = safeMarks,
                    status = if (safeMarks > 0.0) AnswerStatus.CORRECT.name else AnswerStatus.WRONG.name,
                    needsManualReview = false
                )
            } else {
                qa
            }
        }

        val correct = updatedAnalyses.count { it.statusEnum() == AnswerStatus.CORRECT }
        val wrong = updatedAnalyses.count { it.statusEnum() == AnswerStatus.WRONG }
        val unattempted = updatedAnalyses.count { it.statusEnum() == AnswerStatus.UNATTEMPTED }
        val pending = updatedAnalyses.count { it.needsManualReview }
        val totalObtained = round(updatedAnalyses.sumOf { it.marksObtained } * 100.0) / 100.0
        val pct = calculatePercentage(totalObtained, result.totalMarks)
        val newStatus = if (pending == 0) GradingStatus.FINALIZED else GradingStatus.PENDING_REVIEW

        return ManualGradingRecalculation(
            correctCount = correct,
            wrongCount = wrong,
            unattemptedCount = unattempted,
            pendingManualCount = pending,
            obtainedMarks = totalObtained,
            percentage = pct,
            passed = pct >= passingPercentage,
            gradingStatus = newStatus,
            updatedAnalyses = updatedAnalyses
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
                    "A" -> 0; "B" -> 1; "C" -> 2; "D" -> 3; "E" -> 4; "F" -> 5; else -> -1
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
            String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
