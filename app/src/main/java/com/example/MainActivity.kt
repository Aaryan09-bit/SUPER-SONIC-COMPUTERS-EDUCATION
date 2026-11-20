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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.core.configureAppCheck
import com.example.data.model.UserRole
import com.example.data.repository.BundledSetsRepository
import com.example.data.repository.InstituteDirectoryRepository
import com.example.data.repository.InstituteExamRepository
import com.example.data.repository.PdfAiExtractorRepository
import com.example.navigation.AppRoutes
import com.example.navigation.NavTransitions
import com.example.ui.admin.AdminShell
import com.example.ui.auth.AdminLoginScreen
import com.example.ui.auth.PortalSelectorScreen
import com.example.ui.auth.StudentLoginScreen
import com.example.ui.auth.TeacherLoginScreen
import com.example.ui.components.SuperSonicLogoBadge
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
    val rootNavController = rememberNavController()
    val currentBackStackEntry by rootNavController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Reactively drive transitions between Auth Graph (Portal Selector / Role Logins)
    // and Auth-Gated Role Dashboards (Admin / Teacher / Student) via NavController.
    LaunchedEffect(
        user?.uid,
        authState.loading,
        authState.userAccount != null,
        authState.activeWorkspaceRole
    ) {
        val current = rootNavController.currentDestination?.route
        when {
            user == null -> {
                // If user signed out while inside a dashboard or loading route, clear backstack to Portal Selector
                if (!AppRoutes.isAuthRoute(current)) {
                    rootNavController.navigate(AppRoutes.PORTAL_SELECTOR) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            authState.loading && authState.userAccount == null -> {
                if (current != AppRoutes.SESSION_LOADING) {
                    rootNavController.navigate(AppRoutes.SESSION_LOADING) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            else -> {
                val targetDashboard = AppRoutes.dashboardRouteFor(authState.activeWorkspaceRole)
                if (current != targetDashboard) {
                    rootNavController.navigate(targetDashboard) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    fun navigateToRoleLogin(targetRole: UserRole, replaceCurrentLogin: Boolean = false) {
        authViewModel.selectPortalTab(targetRole)
        val targetRoute = AppRoutes.loginRouteFor(targetRole)
        if (currentRoute == targetRoute) return
        rootNavController.navigate(targetRoute) {
            if (replaceCurrentLogin) {
                popUpTo(AppRoutes.PORTAL_SELECTOR) { inclusive = false }
            }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = rootNavController,
        startDestination = AppRoutes.AUTH_GRAPH,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { NavTransitions.slideInForward(this) },
        exitTransition = { NavTransitions.slideOutForward(this) },
        popEnterTransition = { NavTransitions.slideInBackward(this) },
        popExitTransition = { NavTransitions.slideOutBackward(this) }
    ) {
        // 1. Nested Authentication Navigation Graph (Portal Selector -> Dedicated Role Logins)
        navigation(
            startDestination = AppRoutes.PORTAL_SELECTOR,
            route = AppRoutes.AUTH_GRAPH
        ) {
            composable(AppRoutes.PORTAL_SELECTOR) {
                PortalSelectorScreen(
                    uiState = authState,
                    onNavigateToLogin = { role -> navigateToRoleLogin(role, replaceCurrentLogin = false) },
                    onGoogleIdToken = authViewModel::signInWithGoogleToken,
                    onClearError = authViewModel::clearMessages
                )
            }

            composable(AppRoutes.ADMIN_LOGIN) {
                AdminLoginScreen(
                    uiState = authState,
                    onGoogleIdToken = authViewModel::signInWithGoogleToken,
                    onAuthError = authViewModel::reportAuthError,
                    onClearError = authViewModel::clearMessages,
                    onBack = { rootNavController.popBackStack() },
                    onSwitchRoleLogin = { target -> navigateToRoleLogin(target, replaceCurrentLogin = true) }
                )
            }

            composable(AppRoutes.TEACHER_LOGIN) {
                TeacherLoginScreen(
                    uiState = authState,
                    onUpdatePendingCredentials = authViewModel::updatePendingPortalCredentials,
                    onGoogleIdToken = authViewModel::signInWithGoogleToken,
                    onAuthError = authViewModel::reportAuthError,
                    onClearError = authViewModel::clearMessages,
                    onBack = { rootNavController.popBackStack() },
                    onSwitchRoleLogin = { target -> navigateToRoleLogin(target, replaceCurrentLogin = true) }
                )
            }

            composable(AppRoutes.STUDENT_LOGIN) {
                StudentLoginScreen(
                    uiState = authState,
                    onUpdatePendingCredentials = authViewModel::updatePendingPortalCredentials,
                    onGoogleIdToken = authViewModel::signInWithGoogleToken,
                    onAuthError = authViewModel::reportAuthError,
                    onClearError = authViewModel::clearMessages,
                    onBack = { rootNavController.popBackStack() },
                    onSwitchRoleLogin = { target -> navigateToRoleLogin(target, replaceCurrentLogin = true) }
                )
            }
        }

        // 2. Post-Auth Hydration Transition Route
        composable(
            route = AppRoutes.SESSION_LOADING,
            enterTransition = { NavTransitions.tabFadeIn },
            exitTransition = { NavTransitions.tabFadeOut }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SuperSonicLogoBadge(size = 64.dp)
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.loading_workspace),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 3. Auth-Gated Admin Dashboard Destination (hosts its own internal Admin NavHost)
        composable(
            route = AppRoutes.ADMIN_DASHBOARD,
            enterTransition = { NavTransitions.tabFadeIn },
            exitTransition = { NavTransitions.tabFadeOut }
        ) {
            // Strict auth gate: never instantiate Firestore ViewModels while unauthenticated
            val signedInUser = authState.firebaseUser ?: return@composable
            key(signedInUser.uid) {
                val adminVm: AdminViewModel = viewModel(
                    key = "admin_${signedInUser.uid}",
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
        }

        // 4. Auth-Gated Teacher Dashboard Destination (hosts its own internal Teacher NavHost)
        composable(
            route = AppRoutes.TEACHER_DASHBOARD,
            enterTransition = { NavTransitions.tabFadeIn },
            exitTransition = { NavTransitions.tabFadeOut }
        ) {
            val signedInUser = authState.firebaseUser ?: return@composable
            val isAdminAccount = authState.isBootstrappedAdmin || authState.userAccount?.roleEnum() == UserRole.ADMIN
            key(signedInUser.uid, isAdminAccount) {
                val teacherVm: TeacherViewModel = viewModel(
                    key = "teacher_${signedInUser.uid}_$isAdminAccount",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                            val dirRepo = InstituteDirectoryRepository(db)
                            val examRepo = InstituteExamRepository(db, dirRepo)
                            TeacherViewModel(
                                directoryRepo = dirRepo,
                                examRepo = examRepo,
                                teacherUid = signedInUser.uid,
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
        }

        // 5. Auth-Gated Student Dashboard Destination (hosts its own internal Student NavHost)
        composable(
            route = AppRoutes.STUDENT_DASHBOARD,
            enterTransition = { NavTransitions.tabFadeIn },
            exitTransition = { NavTransitions.tabFadeOut }
        ) {
            val signedInUser = authState.firebaseUser ?: return@composable
            val isAdminAccount = authState.isBootstrappedAdmin || authState.userAccount?.roleEnum() == UserRole.ADMIN
            key(signedInUser.uid) {
                val studentVm: StudentViewModel = viewModel(
                    key = "student_${signedInUser.uid}",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                            val dirRepo = InstituteDirectoryRepository(db)
                            val examRepo = InstituteExamRepository(db, dirRepo)
                            StudentViewModel(
                                directoryRepo = dirRepo,
                                examRepo = examRepo,
                                studentUid = signedInUser.uid
                            )
                        }
                    }
                )
                val examSessionVm: ExamSessionViewModel = viewModel(
                    key = "exam_session_${signedInUser.uid}",
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
