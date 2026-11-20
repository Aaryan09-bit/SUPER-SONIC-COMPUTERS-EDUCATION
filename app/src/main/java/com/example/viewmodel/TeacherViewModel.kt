package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.OperationType
import com.example.core.friendlyFirestoreMessage
import com.example.core.handleFirestoreError
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.NotificationItem
import com.example.data.model.StudentProfile
import com.example.data.model.TeacherProfile
import com.example.data.model.UiState
import com.example.data.repository.InstituteDirectoryRepository
import com.example.data.repository.InstituteExamRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeacherActionBanner(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class TeacherViewModel(
    private val directoryRepo: InstituteDirectoryRepository,
    private val examRepo: InstituteExamRepository,
    private val teacherUid: String,
    private val isAdminPreview: Boolean
) : ViewModel() {

    val teacherProfileState: StateFlow<UiState<TeacherProfile?>> =
        directoryRepo.observeMyTeacherProfile(teacherUid).asUiState("teachers")

    // Strictly scoped to assigned students for real teachers; shows all students only in Admin preview
    val assignedStudentsState: StateFlow<UiState<List<StudentProfile>>> =
        (if (isAdminPreview) directoryRepo.observeAllStudents() else directoryRepo.observeTeacherStudents(teacherUid))
            .asUiState("students")

    val examinationsState: StateFlow<UiState<List<Examination>>> =
        (if (isAdminPreview) examRepo.observeAllExaminationsAdmin() else examRepo.observeTeacherExaminations(teacherUid))
            .asUiState("examinations")

    val studentResultsState: StateFlow<UiState<List<ExamResult>>> =
        (if (isAdminPreview) examRepo.observeAllResultsAdmin() else examRepo.observeTeacherResults(teacherUid))
            .asUiState("exam_results")

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> =
        directoryRepo.observeNotifications(teacherUid).asUiState("notifications")

    private val _banner = MutableStateFlow(TeacherActionBanner())
    val banner: StateFlow<TeacherActionBanner> = _banner.asStateFlow()

    fun assignExamToStudents(exam: Examination, students: List<StudentProfile>, daysUntilDeadline: Int) {
        if (students.isEmpty()) return
        viewModelScope.launch {
            _banner.update { it.copy(busy = true, error = null, message = null) }
            val deadline = System.currentTimeMillis() + daysUntilDeadline.coerceIn(1, 90) * 86_400_000L
            examRepo.assignExaminationToStudents(exam, students, deadline).fold(
                onSuccess = {
                    _banner.update {
                        it.copy(busy = false, message = "Assigned '${exam.title}' to ${students.size} student(s)")
                    }
                },
                onFailure = { err ->
                    _banner.update {
                        it.copy(busy = false, error = friendlyFirestoreMessage(err, "Unable to assign exam."))
                    }
                }
            )
        }
    }

    fun gradeSubjectiveAnswer(result: ExamResult, questionId: String, awardedMarks: Double) {
        viewModelScope.launch {
            _banner.update { it.copy(busy = true, error = null, message = null) }
            examRepo.gradeSubjectiveQuestion(result, questionId, awardedMarks).fold(
                onSuccess = {
                    _banner.update { it.copy(busy = false, message = "Graded subjective answer and updated marks.") }
                },
                onFailure = { err ->
                    _banner.update {
                        it.copy(busy = false, error = friendlyFirestoreMessage(err, "Only pending results can be graded."))
                    }
                }
            )
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch { directoryRepo.markNotificationRead(notificationId) }
    }

    fun clearBanner() {
        _banner.update { it.copy(message = null, error = null) }
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
