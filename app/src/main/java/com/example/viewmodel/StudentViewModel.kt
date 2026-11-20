package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.OperationType
import com.example.core.friendlyFirestoreMessage
import com.example.core.handleFirestoreError
import com.example.data.model.ExamAttempt
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.NotificationItem
import com.example.data.model.StudentProfile
import com.example.data.model.UiState
import com.example.data.repository.InstituteDirectoryRepository
import com.example.data.repository.InstituteExamRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudentViewModel(
    private val directoryRepo: InstituteDirectoryRepository,
    private val examRepo: InstituteExamRepository,
    private val studentUid: String
) : ViewModel() {

    val studentProfileState: StateFlow<UiState<StudentProfile?>> =
        directoryRepo.observeMyStudentProfile(studentUid).asUiState("students")

    val availableExamsState: StateFlow<UiState<List<Examination>>> =
        examRepo.observeStudentExaminations(studentUid).asUiState("examinations")

    val myAttemptsState: StateFlow<UiState<List<ExamAttempt>>> =
        examRepo.observeStudentAttempts(studentUid).asUiState("exam_attempts")

    val myResultsState: StateFlow<UiState<List<ExamResult>>> =
        examRepo.observeStudentResults(studentUid).asUiState("exam_results")

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> =
        directoryRepo.observeNotifications(studentUid).asUiState("notifications")

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch { directoryRepo.markNotificationRead(notificationId) }
    }

    private fun <T> Flow<T>.asUiState(path: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                emit(UiState.Error(friendlyFirestoreMessage(fsErr ?: error, "Unable to load $path.")))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
}
