package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.friendlyFirestoreMessage
import com.example.data.model.ExamAttempt
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.SanitizedExamQuestion
import com.example.data.model.StudentProfile
import com.example.data.repository.InstituteExamRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ExamSessionUiState(
    val loading: Boolean = true,
    val exam: Examination? = null,
    val attempt: ExamAttempt? = null,
    val orderedQuestions: List<SanitizedExamQuestion> = emptyList(),
    // Maps questionId -> list of Pair(originalOptionLetter, optionText) to preserve correct letter evaluation even if options are shuffled
    val displayOptionsByQuestion: Map<String, List<Pair<String, String>>> = emptyMap(),
    val currentIndex: Int = 0,
    val responses: Map<String, String> = emptyMap(),
    val flaggedIds: Set<String> = emptySet(),
    val remainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val savingProgress: Boolean = false,
    val lastSavedLabel: String = "Auto-save ready",
    val submitting: Boolean = false,
    val completedResult: ExamResult? = null,
    val error: String? = null
)

class ExamSessionViewModel(
    private val examRepo: InstituteExamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamSessionUiState())
    val uiState: StateFlow<ExamSessionUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var activeStudentProfile: StudentProfile? = null
    private var submittedGuard = false

    fun beginSession(
        exam: Examination,
        studentProfile: StudentProfile?,
        studentFallbackName: String
    ) {
        timerJob?.cancel()
        submittedGuard = false
        activeStudentProfile = studentProfile
        _uiState.value = ExamSessionUiState(loading = true, exam = exam)

        viewModelScope.launch {
            examRepo.startOrResumeAttempt(exam, studentProfile, studentFallbackName).fold(
                onSuccess = { attempt ->
                    val seed = (exam.id + attempt.studentUid).hashCode()
                    val rng = Random(seed)
                    val ordered = if (exam.randomizeQuestions) {
                        exam.questions.shuffled(rng)
                    } else {
                        exam.questions.sortedBy { it.orderIndex }
                    }
                    val letters = listOf("A", "B", "C", "D", "E", "F")
                    val optionsMap = ordered.associate { q ->
                        val paired = q.options.mapIndexed { idx, optText ->
                            val letter = letters.getOrElse(idx) { "${idx + 1}" }
                            letter to optText
                        }
                        q.questionId to if (exam.randomizeOptions && paired.size > 1) paired.shuffled(Random(seed + q.questionId.hashCode())) else paired
                    }

                    val totalAllowedSec = (exam.durationMinutes * 60).coerceAtLeast(60)
                    val elapsedFromStart = ((System.currentTimeMillis() - attempt.startedAtMillis) / 1000L)
                        .toInt()
                        .coerceAtLeast(attempt.timeTakenSeconds)
                    val remaining = (totalAllowedSec - elapsedFromStart).coerceAtLeast(0)

                    _uiState.value = ExamSessionUiState(
                        loading = false,
                        exam = exam,
                        attempt = attempt,
                        orderedQuestions = ordered,
                        displayOptionsByQuestion = optionsMap,
                        currentIndex = attempt.currentQuestionIndex.coerceIn(0, (ordered.size - 1).coerceAtLeast(0)),
                        responses = attempt.responses,
                        flaggedIds = attempt.flaggedQuestionIds.toSet(),
                        remainingSeconds = remaining,
                        elapsedSeconds = elapsedFromStart,
                        lastSavedLabel = "Progress synced"
                    )

                    if (remaining == 0) {
                        submitExam(autoSubmitted = true)
                    } else {
                        startCountdownTimer()
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = friendlyFirestoreMessage(err, "Unable to start examination.")
                        )
                    }
                }
            )
        }
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var tickCounter = 0
            while (true) {
                delay(1000L)
                val cur = _uiState.value
                if (cur.submitting || cur.completedResult != null) break
                val nextRemaining = (cur.remainingSeconds - 1).coerceAtLeast(0)
                val nextElapsed = cur.elapsedSeconds + 1
                _uiState.update {
                    it.copy(remainingSeconds = nextRemaining, elapsedSeconds = nextElapsed)
                }
                tickCounter++
                if (nextRemaining <= 0) {
                    submitExam(autoSubmitted = true)
                    break
                }
                if (tickCounter % 15 == 0) {
                    persistProgress()
                }
            }
        }
    }

    fun selectSingleAnswer(questionId: String, value: String) {
        _uiState.update {
            it.copy(responses = it.responses + (questionId to value))
        }
    }

    fun toggleMultiSelectAnswer(questionId: String, letter: String) {
        _uiState.update { cur ->
            val currentSet = cur.responses[questionId]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (currentSet.contains(letter)) currentSet.remove(letter) else currentSet.add(letter)
            val serialized = currentSet.sorted().joinToString(",")
            val nextResponses = if (serialized.isBlank()) {
                cur.responses - questionId
            } else {
                cur.responses + (questionId to serialized)
            }
            cur.copy(responses = nextResponses)
        }
    }

    fun clearAnswer(questionId: String) {
        _uiState.update { it.copy(responses = it.responses - questionId) }
    }

    fun toggleFlagQuestion(questionId: String) {
        _uiState.update { cur ->
            val next = if (cur.flaggedIds.contains(questionId)) cur.flaggedIds - questionId else cur.flaggedIds + questionId
            cur.copy(flaggedIds = next)
        }
    }

    fun goToQuestion(index: Int) {
        val cur = _uiState.value
        val bounded = index.coerceIn(0, (cur.orderedQuestions.size - 1).coerceAtLeast(0))
        _uiState.update { it.copy(currentIndex = bounded) }
        persistProgress()
    }

    fun nextQuestion() = goToQuestion(_uiState.value.currentIndex + 1)

    fun previousQuestion() = goToQuestion(_uiState.value.currentIndex - 1)

    private fun persistProgress() {
        val cur = _uiState.value
        val attemptId = cur.attempt?.id ?: return
        if (cur.submitting || cur.completedResult != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(savingProgress = true) }
            examRepo.autoSaveAttemptProgress(
                attemptId = attemptId,
                responses = cur.responses,
                flaggedQuestionIds = cur.flaggedIds.toList(),
                currentQuestionIndex = cur.currentIndex,
                timeTakenSeconds = cur.elapsedSeconds
            )
            _uiState.update { it.copy(savingProgress = false, lastSavedLabel = "Auto-saved") }
        }
    }

    fun submitExam(autoSubmitted: Boolean = false) {
        if (submittedGuard) return
        val cur = _uiState.value
        val exam = cur.exam ?: return
        val attempt = cur.attempt ?: return
        submittedGuard = true
        timerJob?.cancel()
        _uiState.update { it.copy(submitting = true, error = null) }

        viewModelScope.launch {
            examRepo.submitAndEvaluateExam(
                exam = exam,
                attempt = attempt,
                studentProfile = activeStudentProfile,
                responses = cur.responses,
                flaggedQuestionIds = cur.flaggedIds.toList(),
                currentQuestionIndex = cur.currentIndex,
                timeTakenSeconds = cur.elapsedSeconds
            ).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            completedResult = result,
                            lastSavedLabel = if (autoSubmitted) "Time expired • Auto-submitted" else "Submitted"
                        )
                    }
                },
                onFailure = { err ->
                    submittedGuard = false
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            error = friendlyFirestoreMessage(err, "Submission failed. Please retry.")
                        )
                    }
                }
            )
        }
    }

    fun resetSession() {
        timerJob?.cancel()
        submittedGuard = false
        _uiState.value = ExamSessionUiState()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
