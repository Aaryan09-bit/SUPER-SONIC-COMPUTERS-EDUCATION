package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.EvaluationEngine
import com.example.data.model.AnswerStatus
import com.example.data.model.ExamAnswerKey
import com.example.data.model.SanitizedExamQuestion
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.GradingStatus
import com.example.data.model.QuestionType
import com.example.data.repository.BundledSetsRepository
import com.example.data.repository.PdfAiExtractorRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SuperSonicCoreTest {

    @Test
    fun `evaluate all five question types with negative marking and canonical percentage`() {
        val exam = Examination(
            id = "exam_full_types",
            title = "Comprehensive Computer Test",
            subject = "Computer Fundamentals",
            durationMinutes = 30,
            totalMarks = 10.0,
            marksPerQuestion = 2.0,
            negativeMarks = 0.5,
            passingPercentage = 40.0,
            questionCount = 5,
            questions = listOf(
                SanitizedExamQuestion("q1", "In MS Word, Ctrl+K inserts?", QuestionType.MCQ.name, listOf("Bold", "Italic", "Table", "Hyperlink"), 2.0, 0),
                SanitizedExamQuestion("q2", "Select volatile memories", QuestionType.MULTI_SELECT.name, listOf("RAM", "ROM", "Cache", "SSD"), 2.0, 1),
                SanitizedExamQuestion("q3", "CPU stands for Central Processing Unit.", QuestionType.TRUE_FALSE.name, listOf("True", "False"), 2.0, 2),
                SanitizedExamQuestion("q4", "Shortcut to paste is ____.", QuestionType.FILL_BLANK.name, emptyList(), 2.0, 3),
                SanitizedExamQuestion("q5", "Explain primary key in DBMS.", QuestionType.SHORT_ANSWER.name, emptyList(), 2.0, 4)
            )
        )
        val key = ExamAnswerKey(
            examId = exam.id,
            answers = mapOf(
                "q1" to "D",
                "q2" to "A,C",
                "q3" to "True",
                "q4" to "Ctrl+V|Control+V",
                "q5" to "Uniquely identifies each record in a table"
            ),
            marksPerQuestion = 2.0,
            negativeMarks = 0.5,
            passingPercentage = 40.0
        )

        val responses = mapOf(
            "q1" to "Hyperlink",       // Option text match -> +2.0
            "q2" to "c, a",            // Order-insensitive multi-select -> +2.0
            "q3" to "false",           // Wrong -> -0.5
            "q4" to "  control+v ",    // Variant & case-insensitive fill blank -> +2.0
            "q5" to "A unique column"  // Short answer -> pending manual review (0.0)
        )

        val outcome = EvaluationEngine.evaluate(exam, key, responses)

        assertEquals(5, outcome.totalQuestions)
        assertEquals(3, outcome.correctCount)
        assertEquals(1, outcome.wrongCount)
        assertEquals(0, outcome.unattemptedCount)
        assertEquals(1, outcome.pendingManualCount)
        assertEquals(10.0, outcome.totalMarks, 0.001)
        assertEquals(5.5, outcome.obtainedMarks, 0.001)
        assertEquals(55.0, outcome.percentage, 0.001)
        assertTrue(outcome.passed)
        assertEquals(GradingStatus.PENDING_REVIEW, outcome.gradingStatus)
        assertEquals(AnswerStatus.PENDING.name, outcome.questionAnalyses.last().status)
    }

    @Test
    fun `negative marks clamp percentage to zero without NaN or negative percentages`() {
        val exam = Examination(
            id = "exam_neg",
            title = "Negative Marking Edge",
            questions = listOf(
                SanitizedExamQuestion("q1", "Q1", QuestionType.MCQ.name, listOf("A", "B"), 2.0, 0),
                SanitizedExamQuestion("q2", "Q2", QuestionType.MCQ.name, listOf("A", "B"), 2.0, 1)
            )
        )
        val key = ExamAnswerKey(
            examId = exam.id,
            answers = mapOf("q1" to "A", "q2" to "A"),
            marksPerQuestion = 2.0,
            negativeMarks = 1.0,
            passingPercentage = 40.0
        )
        val outcome = EvaluationEngine.evaluate(exam, key, mapOf("q1" to "B", "q2" to "B"))
        assertEquals(-2.0, outcome.obtainedMarks, 0.001)
        assertEquals(0.0, outcome.percentage, 0.001)
        assertFalse(outcome.passed)
        assertEquals(0.0, EvaluationEngine.calculatePercentage(10.0, 0.0), 0.001)
        assertEquals(100.0, EvaluationEngine.calculatePercentage(120.0, 100.0), 0.001)
    }

    @Test
    fun `manual grading recalculates score, status, and custom passing percentage consistently`() {
        val exam = Examination(
            id = "exam_manual",
            title = "DBMS Short Test",
            passingPercentage = 60.0,
            questions = listOf(
                SanitizedExamQuestion("q1", "Q1", QuestionType.MCQ.name, listOf("A", "B"), 2.0, 0),
                SanitizedExamQuestion("q2", "Explain SQL JOIN", QuestionType.SHORT_ANSWER.name, emptyList(), 3.0, 1)
            )
        )
        val key = ExamAnswerKey(
            examId = exam.id,
            answers = mapOf("q1" to "A", "q2" to "Combines rows from tables"),
            marksPerQuestion = 2.0,
            negativeMarks = 0.0,
            passingPercentage = 60.0
        )
        val initial = EvaluationEngine.evaluate(exam, key, mapOf("q1" to "A", "q2" to "Joins two tables"))
        assertEquals(GradingStatus.PENDING_REVIEW, initial.gradingStatus)
        assertFalse(initial.passed) // 2/5 = 40% < 60%

        val result = ExamResult(
            id = "exam_manual_stu1",
            examId = exam.id,
            totalQuestions = initial.totalQuestions,
            correctCount = initial.correctCount,
            wrongCount = initial.wrongCount,
            unattemptedCount = initial.unattemptedCount,
            pendingManualCount = initial.pendingManualCount,
            totalMarks = initial.totalMarks,
            obtainedMarks = initial.obtainedMarks,
            percentage = initial.percentage,
            passed = initial.passed,
            gradingStatus = initial.gradingStatus.name,
            questionAnalyses = initial.questionAnalyses
        )

        // Awarding 5.0 should clamp to maxMarks = 3.0; total becomes 5/5 = 100% >= 60%
        val recalc = EvaluationEngine.recalculateAfterManualGrading(
            result = result,
            questionId = "q2",
            awardedMarks = 5.0,
            passingPercentage = 60.0
        )

        assertEquals(2, recalc.correctCount)
        assertEquals(0, recalc.wrongCount)
        assertEquals(0, recalc.pendingManualCount)
        assertEquals(5.0, recalc.obtainedMarks, 0.001)
        assertEquals(100.0, recalc.percentage, 0.001)
        assertTrue(recalc.passed)
        assertEquals(GradingStatus.FINALIZED, recalc.gradingStatus)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty question paper is rejected safely`() {
        val emptyExam = Examination(id = "exam_empty", title = "Empty", questions = emptyList())
        EvaluationEngine.evaluate(emptyExam, ExamAnswerKey(examId = "exam_empty"), emptyMap())
    }

    @Test
    fun `server anchored timer recovers accurately across refresh, sleep, and deadlines`() {
        val startMillis = 1_700_000_000_000L
        val afterFiveMin = startMillis + 300_000L

        // Normal mid-exam recovery after app refresh (30 min exam, 5 min elapsed)
        val mid = EvaluationEngine.computeServerAnchoredTimer(
            durationMinutes = 30,
            serverCreatedAtMillis = startMillis,
            fallbackStartedAtMillis = startMillis + 60_000L, // ignored because serverCreatedAtMillis wins
            previouslyRecordedElapsedSec = 120,
            deadlineAtMillis = 0L,
            nowMillis = afterFiveMin
        )
        assertEquals(300, mid.elapsedSeconds)
        assertEquals(1500, mid.remainingSeconds)
        assertFalse(mid.expired)

        // Clock rollback attempt cannot reduce previously recorded elapsed time
        val rollback = EvaluationEngine.computeServerAnchoredTimer(
            durationMinutes = 30,
            serverCreatedAtMillis = startMillis,
            fallbackStartedAtMillis = startMillis,
            previouslyRecordedElapsedSec = 900,
            deadlineAtMillis = 0L,
            nowMillis = startMillis + 10_000L
        )
        assertEquals(900, rollback.elapsedSeconds)
        assertEquals(900, rollback.remainingSeconds)
        assertFalse(rollback.expired)

        // Deadline closer than remaining duration enforces earlier cutoff
        val deadlineTight = EvaluationEngine.computeServerAnchoredTimer(
            durationMinutes = 30,
            serverCreatedAtMillis = startMillis,
            fallbackStartedAtMillis = startMillis,
            previouslyRecordedElapsedSec = 0,
            deadlineAtMillis = afterFiveMin + 45_000L,
            nowMillis = afterFiveMin
        )
        assertEquals(45, deadlineTight.remainingSeconds)
        assertFalse(deadlineTight.expired)

        // Expired when past duration
        val expired = EvaluationEngine.computeServerAnchoredTimer(
            durationMinutes = 10,
            serverCreatedAtMillis = startMillis,
            fallbackStartedAtMillis = startMillis,
            previouslyRecordedElapsedSec = 0,
            deadlineAtMillis = 0L,
            nowMillis = startMillis + 700_000L
        )
        assertEquals(600, expired.elapsedSeconds)
        assertEquals(0, expired.remainingSeconds)
        assertTrue(expired.expired)
    }

    @Test
    fun `pdf question fingerprint normalizes whitespace and case for duplicate prevention`() {
        val fp1 = PdfAiExtractorRepository.fingerprintQuestion("  In MS-Word,   Ctrl+K is used for? ")
        val fp2 = PdfAiExtractorRepository.fingerprintQuestion("in ms-word, ctrl+k is used for?")
        val fp3 = PdfAiExtractorRepository.fingerprintQuestion("In MS-Excel, Ctrl+K is used for?")

        assertEquals(fp1, fp2)
        assertNotEquals(fp1, fp3)
    }

    @Test
    fun `bundled 42 PDF question sets parse completely with 1237 valid MCQs`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BundledSetsRepository(context)
        val sets = repo.loadBundledSets()

        assertEquals(42, sets.size)
        val totalQuestions = sets.sumOf { it.questions.size }
        assertEquals(1237, totalQuestions)

        sets.forEach { set ->
            assertTrue("Set title blank: ${set.setId}", set.title.isNotBlank())
            assertTrue("Set has no questions: ${set.setId}", set.questions.isNotEmpty())
            set.questions.forEach { q ->
                assertTrue("Blank question text in ${set.setId}", q.text.isNotBlank())
                assertTrue("Question has fewer than 2 options in ${set.setId}", q.options.size >= 2)
            }
        }
    }

    @Test
    fun `large scale evaluation and 500 student aggregation runs under 500ms`() {
        val questions = (1..200).map { i ->
            SanitizedExamQuestion(
                questionId = "q_$i",
                text = "Question number $i about computer systems?",
                questionType = QuestionType.MCQ.name,
                options = listOf("Opt A", "Opt B", "Opt C", "Opt D"),
                marks = 1.0,
                orderIndex = i - 1
            )
        }
        val exam = Examination(
            id = "exam_perf",
            title = "Mega 200-Q Exam",
            subject = "Computer Fundamentals",
            totalMarks = 200.0,
            marksPerQuestion = 1.0,
            negativeMarks = 0.25,
            passingPercentage = 40.0,
            questionCount = 200,
            questions = questions
        )
        val key = ExamAnswerKey(
            examId = exam.id,
            answers = questions.associate { it.questionId to "A" },
            marksPerQuestion = 1.0,
            negativeMarks = 0.25,
            passingPercentage = 40.0
        )
        val responses = questions.associateIndexed { idx, q ->
            q.questionId to when (idx % 4) {
                0, 1, 2 -> "A"
                else -> "B"
            }
        }

        val startNs = System.nanoTime()
        val outcome = EvaluationEngine.evaluate(exam, key, responses)
        val simulatedResults = (1..500).map { s ->
            ExamResult(
                id = "res_$s",
                examId = exam.id,
                subject = if (s % 2 == 0) "MS Word" else "MS Excel",
                studentId = "stu_$s",
                studentUid = "uid_$s",
                percentage = outcome.percentage,
                passed = outcome.passed
            )
        }
        val groupedBySubject = simulatedResults.groupBy { it.subject }
            .mapValues { (_, list) -> list.map { it.percentage }.average() }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        assertEquals(150, outcome.correctCount)
        assertEquals(50, outcome.wrongCount)
        assertEquals(2, groupedBySubject.size)
        assertTrue("Expected < 500ms but took ${elapsedMs}ms", elapsedMs < 500L)
    }

    private inline fun <T, K, V> List<T>.associateIndexed(transform: (Int, T) -> Pair<K, V>): Map<K, V> {
        val map = LinkedHashMap<K, V>(size)
        forEachIndexed { index, item ->
            val (k, v) = transform(index, item)
            map[k] = v
        }
        return map
    }
}
