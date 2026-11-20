package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExamResult
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.ui.components.AdminRoleSwitcherBar
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.QuestionAnalysisScreen
import com.example.ui.components.SuperSonicLogoBadge
import com.example.viewmodel.AdminViewModel

private data class AdminNavDest(val label: String, val icon: ImageVector, val tag: String)

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

    val teachers = (teachersState as? UiState.Success)?.data.orEmpty()
    val students = (studentsState as? UiState.Success)?.data.orEmpty()
    val pdfPapers = (pdfPapersState as? UiState.Success)?.data.orEmpty()
    val questions = (questionsState as? UiState.Success)?.data.orEmpty()
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val results = (resultsState as? UiState.Success)?.data.orEmpty()
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()

    var selectedNav by remember { mutableIntStateOf(0) }
    var inspectingResultId by remember { mutableStateOf<String?>(null) }

    val inspectingResult: ExamResult? = remember(inspectingResultId, results) {
        inspectingResultId?.let { id -> results.firstOrNull { it.id == id } }
    }

    if (inspectingResult != null) {
        BackHandler { inspectingResultId = null }
        QuestionAnalysisScreen(
            result = inspectingResult,
            canGradeSubjective = true,
            canControlVisibility = true,
            isStudentView = false,
            onBack = { inspectingResultId = null },
            onGradeQuestion = { qId, marks -> viewModel.gradeSubjectiveAnswer(inspectingResult, qId, marks) },
            onUpdateVisibility = { vis, rev -> viewModel.updateResultVisibility(inspectingResult.id, vis, rev) }
        )
        return
    }

    val destinations = remember {
        listOf(
            AdminNavDest("Overview", Icons.Outlined.Dashboard, "admin_nav_overview"),
            AdminNavDest("People", Icons.Outlined.Groups, "admin_nav_people"),
            AdminNavDest("Papers", Icons.Outlined.MenuBook, "admin_nav_papers"),
            AdminNavDest("Exams", Icons.AutoMirrored.Outlined.Assignment, "admin_nav_exams"),
            AdminNavDest("Reports", Icons.Outlined.Assessment, "admin_nav_reports")
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SuperSonicLogoBadge(size = 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Super Sonic Edu", style = MaterialTheme.typography.titleMedium)
                                    Text("Super Admin Console", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEachIndexed { idx, dest ->
                            NavigationBarItem(
                                selected = selectedNav == idx,
                                onClick = { selectedNav = idx },
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
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        destinations.forEachIndexed { idx, dest ->
                            NavigationRailItem(
                                selected = selectedNav == idx,
                                onClick = { selectedNav = idx },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    FeedbackBanner(
                        message = bannerState.message,
                        error = bannerState.error,
                        onDismiss = viewModel::clearBanner
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedNav) {
                            0 -> AdminOverviewScreen(
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
                                onNavigateToPapers = { selectedNav = 2 },
                                onNavigateToExams = { selectedNav = 3 }
                            )
                            1 -> AdminPeopleScreen(
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
                                onDeleteTeacher = viewModel::deleteTeacher
                            )
                            2 -> AdminPapersBankScreen(
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
                                onDeleteQuestion = viewModel::deleteQuestion
                            )
                            3 -> AdminExamsScreen(
                                exams = exams,
                                questions = questions,
                                students = students,
                                teachers = teachers,
                                includeDemoRecords = bannerState.includeDemoRecords,
                                busy = bannerState.busy,
                                onCreateExam = viewModel::createExamination,
                                onUpdateExamStatus = viewModel::updateExamStatus
                            )
                            else -> AdminResultsReportsScreen(
                                results = results,
                                notifications = notifications,
                                auditLogs = auditLogs,
                                includeDemoRecords = bannerState.includeDemoRecords,
                                onOpenResultAnalysis = { res -> inspectingResultId = res.id }
                            )
                        }
                    }
                }
            }
        }
    }
}
