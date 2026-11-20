package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.configureAppCheck
import com.example.data.model.UserRole
import com.example.data.repository.BundledSetsRepository
import com.example.data.repository.InstituteDirectoryRepository
import com.example.data.repository.InstituteExamRepository
import com.example.data.repository.PdfAiExtractorRepository
import com.example.ui.admin.AdminShell
import com.example.ui.auth.AuthScreen
import com.example.ui.student.StudentShell
import com.example.ui.teacher.TeacherShell
import com.example.ui.theme.SuperSonicTheme
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ExamSessionViewModel
import com.example.viewmodel.StudentViewModel
import com.example.viewmodel.TeacherViewModel
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent BEFORE UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            SuperSonicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SuperSonicAppRoot()
                }
            }
        }
    }
}

@Composable
private fun SuperSonicAppRoot() {
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AuthViewModel(InstituteDirectoryRepository(db))
            }
        }
    )

    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val user = authState.firebaseUser

    if (user == null) {
        AuthScreen(
            uiState = authState,
            onSelectPortalTab = authViewModel::selectPortalTab,
            onGoogleIdToken = authViewModel::signInWithGoogleToken,
            onAuthError = authViewModel::reportAuthError,
            onClearError = authViewModel::clearMessages
        )
    } else if (authState.loading && authState.userAccount == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading Super Sonic Computers Education...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        // Auth-gated & UID-keyed workspace composition prevents stale listener leaks across accounts
        key(user.uid, authState.activeWorkspaceRole) {
            val isAdminAccount = authState.isBootstrappedAdmin || authState.userAccount?.roleEnum() == UserRole.ADMIN
            when (authState.activeWorkspaceRole) {
                UserRole.ADMIN -> {
                    val adminVm: AdminViewModel = viewModel(
                        key = "admin_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                val dirRepo = InstituteDirectoryRepository(db)
                                val examRepo = InstituteExamRepository(db, dirRepo)
                                AdminViewModel(
                                    directoryRepo = dirRepo,
                                    examRepo = examRepo,
                                    bundledSetsRepo = BundledSetsRepository(app),
                                    pdfAiRepo = PdfAiExtractorRepository(app)
                                )
                            }
                        }
                    )
                    AdminShell(
                        viewModel = adminVm,
                        activeWorkspaceRole = authState.activeWorkspaceRole,
                        onSwitchWorkspaceRole = authViewModel::switchAdminPreviewRole,
                        onSignOut = authViewModel::signOut
                    )
                }

                UserRole.TEACHER -> {
                    val teacherVm: TeacherViewModel = viewModel(
                        key = "teacher_${user.uid}_$isAdminAccount",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                val dirRepo = InstituteDirectoryRepository(db)
                                val examRepo = InstituteExamRepository(db, dirRepo)
                                TeacherViewModel(
                                    directoryRepo = dirRepo,
                                    examRepo = examRepo,
                                    teacherUid = user.uid,
                                    isAdminPreview = isAdminAccount
                                )
                            }
                        }
                    )
                    TeacherShell(
                        viewModel = teacherVm,
                        isAdminPreview = isAdminAccount,
                        activeWorkspaceRole = authState.activeWorkspaceRole,
                        onSwitchWorkspaceRole = authViewModel::switchAdminPreviewRole,
                        onSignOut = authViewModel::signOut
                    )
                }

                UserRole.STUDENT -> {
                    val studentVm: StudentViewModel = viewModel(
                        key = "student_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                val dirRepo = InstituteDirectoryRepository(db)
                                val examRepo = InstituteExamRepository(db, dirRepo)
                                StudentViewModel(
                                    directoryRepo = dirRepo,
                                    examRepo = examRepo,
                                    studentUid = user.uid
                                )
                            }
                        }
                    )
                    val examSessionVm: ExamSessionViewModel = viewModel(
                        key = "exam_session_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                val dirRepo = InstituteDirectoryRepository(db)
                                ExamSessionViewModel(InstituteExamRepository(db, dirRepo))
                            }
                        }
                    )
                    StudentShell(
                        studentViewModel = studentVm,
                        examSessionViewModel = examSessionVm,
                        userAccount = authState.userAccount,
                        isAdminPreview = isAdminAccount,
                        activeWorkspaceRole = authState.activeWorkspaceRole,
                        authMessage = authState.statusMessage,
                        authError = authState.errorMessage,
                        claimingCode = authState.claimingCode,
                        onClaimPortalCode = authViewModel::claimPortalCode,
                        onClearAuthMessages = authViewModel::clearMessages,
                        onSwitchWorkspaceRole = authViewModel::switchAdminPreviewRole,
                        onSignOut = authViewModel::signOut
                    )
                }
            }
        }
    }
}
