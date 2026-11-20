package com.example.ui.admin

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.navigation.AdminRoutes
import com.example.navigation.NavTransitions
import com.example.navigation.navigateToTopLevelTab
import com.example.ui.components.AdminRoleSwitcherBar
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.QuestionAnalysisScreen
import com.example.ui.components.SuperSonicLogoBadge
import com.example.viewmodel.AdminViewModel

private data class AdminNavDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShell(
    viewModel: AdminViewModel,
    activeWorkspaceRole: UserRole,
    onSwitchWorkspaceRole: (UserRole) -> Unit,
    onSignOut: () -> Unit
) {
    val teachersState by viewModel.teachersState.collectAsStateWithLifecycle()
    val studentsState by viewModel.studentsState.collectAsStateWithLifecycle()
    val pdfPapersState by viewModel.pdfPapersState.collectAsStateWithLifecycle()
    val questionsState by viewModel.questionsState.collectAsStateWithLifecycle()
    val examsState by viewModel.examinationsState.collectAsStateWithLifecycle()
    val resultsState by viewModel.resultsState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val bundledSets by viewModel.bundledSets.collectAsStateWithLifecycle()
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()
    val bannerState by viewModel.bannerState.collectAsStateWithLifecycle()
    val studentsLimit by viewModel.studentsPageLimit.collectAsStateWithLifecycle()
    val questionsLimit by viewModel.questionsPageLimit.collectAsStateWithLifecycle()
    val resultsLimit by viewModel.resultsPageLimit.collectAsStateWithLifecycle()

    val teachers = (teachersState as? UiState.Success)?.data.orEmpty()
    val students = (studentsState as? UiState.Success)?.data.orEmpty()
    val pdfPapers = (pdfPapersState as? UiState.Success)?.data.orEmpty()
    val questions = (questionsState as? UiState.Success)?.data.orEmpty()
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val results = (resultsState as? UiState.Success)?.data.orEmpty()
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()

    val adminNavController = rememberNavController()
    val navBackStackEntry by adminNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDetailRoute = AdminRoutes.isDetailRoute(currentRoute)

    val destinations = remember {
        listOf(
            AdminNavDest(AdminRoutes.OVERVIEW, "Overview", Icons.Outlined.Dashboard, "admin_nav_overview"),
            AdminNavDest(AdminRoutes.PEOPLE, "People", Icons.Outlined.Groups, "admin_nav_people"),
            AdminNavDest(AdminRoutes.PAPERS_BANK, "Papers", Icons.Outlined.MenuBook, "admin_nav_papers"),
            AdminNavDest(AdminRoutes.EXAMS, "Exams", Icons.AutoMirrored.Outlined.Assignment, "admin_nav_exams"),
            AdminNavDest(AdminRoutes.RESULTS_REPORTS, "Reports", Icons.Outlined.Assessment, "admin_nav_reports")
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!isDetailRoute) {
                    Column {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SuperSonicLogoBadge(size = 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Super Sonic Edu", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Super Admin Console",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.testTag("admin_sign_out_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        AdminRoleSwitcherBar(
                            activeRole = activeWorkspaceRole,
                            onSelectRole = onSwitchWorkspaceRole
                        )
                    }
                }
            },
            bottomBar = {
                if (!useRail && !isDetailRoute) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { adminNavController.navigateToTopLevelTab(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isDetailRoute) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
            ) {
                if (useRail && !isDetailRoute) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        destinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { adminNavController.navigateToTopLevelTab(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (!isDetailRoute) {
                        FeedbackBanner(
                            message = bannerState.message,
                            error = bannerState.error,
                            onDismiss = viewModel::clearBanner
                        )
                    }

                    NavHost(
                        navController = adminNavController,
                        startDestination = AdminRoutes.OVERVIEW,
                        modifier = Modifier.weight(1f),
                        enterTransition = { NavTransitions.tabFadeIn },
                        exitTransition = { NavTransitions.tabFadeOut }
                    ) {
                        composable(AdminRoutes.OVERVIEW) {
                            AdminOverviewScreen(
                                students = students,
                                teachers = teachers,
                                exams = exams,
                                questions = questions,
                                results = results,
                                bundledSets = bundledSets,
                                includeDemoRecords = bannerState.includeDemoRecords,
                                busy = bannerState.busy,
                                onToggleDemoVisibility = viewModel::toggleDemoVisibility,
                                onSeedDemo = viewModel::seedDemoData,
                                onClearDemo = viewModel::clearDemoData,
                                onNavigateToPapers = { adminNavController.navigateToTopLevelTab(AdminRoutes.PAPERS_BANK) },
                                onNavigateToExams = { adminNavController.navigateToTopLevelTab(AdminRoutes.EXAMS) },
                                isLoading = studentsState is UiState.Loading || resultsState is UiState.Loading
                            )
                        }

                        composable(AdminRoutes.PEOPLE) {
                            AdminPeopleScreen(
                                students = students,
                                teachers = teachers,
                                exams = exams,
                                results = results,
                                includeDemoRecords = bannerState.includeDemoRecords,
                                onCreateStudent = viewModel::createStudent,
                                onUpdateStudent = viewModel::updateStudent,
                                onTransferStudent = viewModel::transferStudent,
                                onDeleteStudent = viewModel::deleteStudent,
                                onCreateTeacher = viewModel::createTeacher,
                                onUpdateTeacher = viewModel::updateTeacher,
                                onDeleteTeacher = viewModel::deleteTeacher,
                                isLoading = studentsState is UiState.Loading || teachersState is UiState.Loading,
                                canLoadMoreStudents = students.size >= studentsLimit,
                                onLoadMoreStudents = viewModel::loadMoreStudents
                            )
                        }

                        composable(AdminRoutes.PAPERS_BANK) {
                            AdminPapersBankScreen(
                                bundledSets = bundledSets,
                                pdfPapers = pdfPapers,
                                questions = questions,
                                extractionState = extractionState,
                                busy = bannerState.busy,
                                onImportBundledSet = viewModel::importBundledSet,
                                onExtractPdfUri = viewModel::extractPdfWithAi,
                                onUpdateExtractionMeta = viewModel::updateExtractionMeta,
                                onUpdateDraft = viewModel::updateDraftQuestion,
                                onRemoveDraft = viewModel::removeDraftQuestion,
                                onSaveReviewedDrafts = viewModel::saveReviewedExtractionToBank,
                                onDismissDrafts = viewModel::dismissExtractionDrafts,
                                onSaveManualQuestion = viewModel::saveManualQuestion,
                                onDeleteQuestion = viewModel::deleteQuestion,
                                isLoadingQuestions = questionsState is UiState.Loading,
                                canLoadMoreQuestions = questions.size >= questionsLimit,
                                onLoadMoreQuestions = viewModel::loadMoreQuestions
                            )
                        }

                        composable(AdminRoutes.EXAMS) {
                            AdminExamsScreen(
                                exams = exams,
                                questions = questions,
                                students = students,
                                teachers = teachers,
                                includeDemoRecords = bannerState.includeDemoRecords,
                                busy = bannerState.busy,
                                onCreateExam = viewModel::createExamination,
                                onUpdateExamStatus = viewModel::updateExamStatus,
                                isLoading = examsState is UiState.Loading,
                                canLoadMoreQuestions = questions.size >= questionsLimit,
                                onLoadMoreQuestions = viewModel::loadMoreQuestions
                            )
                        }

                        composable(AdminRoutes.RESULTS_REPORTS) {
                            AdminResultsReportsScreen(
                                results = results,
                                notifications = notifications,
                                auditLogs = auditLogs,
                                includeDemoRecords = bannerState.includeDemoRecords,
                                onOpenResultAnalysis = { res ->
                                    adminNavController.navigate(AdminRoutes.resultAnalysis(res.id))
                                },
                                isLoading = resultsState is UiState.Loading,
                                canLoadMoreResults = results.size >= resultsLimit,
                                onLoadMoreResults = viewModel::loadMoreResults
                            )
                        }

                        composable(
                            route = AdminRoutes.RESULT_ANALYSIS,
                            arguments = listOf(navArgument(AdminRoutes.ARG_RESULT_ID) { type = NavType.StringType }),
                            enterTransition = { NavTransitions.slideInForward(this) },
                            exitTransition = { NavTransitions.slideOutForward(this) },
                            popEnterTransition = { NavTransitions.slideInBackward(this) },
                            popExitTransition = { NavTransitions.slideOutBackward(this) }
                        ) { backStackEntry ->
                            val rawId = backStackEntry.arguments?.getString(AdminRoutes.ARG_RESULT_ID).orEmpty()
                            val resultId = Uri.decode(rawId)
                            val inspectingResult = remember(resultId, results) {
                                results.firstOrNull { it.id == resultId }
                            }
                            if (inspectingResult != null) {
                                QuestionAnalysisScreen(
                                    result = inspectingResult,
                                    canGradeSubjective = true,
                                    canControlVisibility = true,
                                    isStudentView = false,
                                    onBack = { adminNavController.popBackStack() },
                                    onGradeQuestion = { qId, marks ->
                                        viewModel.gradeSubjectiveAnswer(inspectingResult, qId, marks)
                                    },
                                    onUpdateVisibility = { vis, rev ->
                                        viewModel.updateResultVisibility(inspectingResult.id, vis, rev)
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Result not found or still syncing.", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = { adminNavController.popBackStack() }) {
                                            Text("Back to Reports")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
