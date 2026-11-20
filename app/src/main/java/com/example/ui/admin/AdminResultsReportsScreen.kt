package com.example.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.EvaluationEngine
import com.example.core.ReportExportManager
import com.example.data.model.AuditLog
import com.example.data.model.ExamResult
import com.example.data.model.GradingStatus
import com.example.data.model.NotificationItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InstituteCard
import com.example.ui.components.LoadingSkeletonList
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.rememberDebouncedText
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminResultsReportsScreen(
    results: List<ExamResult>,
    notifications: List<NotificationItem>,
    auditLogs: List<AuditLog>,
    includeDemoRecords: Boolean,
    onOpenResultAnalysis: (ExamResult) -> Unit,
    isLoading: Boolean = false,
    canLoadMoreResults: Boolean = false,
    onLoadMoreResults: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val debouncedQuery = rememberDebouncedText(searchQuery)
    var filterStatus by remember { mutableStateOf("ALL") } // ALL, PASS, FAIL, PENDING
    var exportStatus by remember { mutableStateOf<String?>(null) }

    val baseResults = remember(results, includeDemoRecords) {
        if (includeDemoRecords) results else results.filterNot { it.demoRecord }
    }
    val filteredResults = remember(baseResults, debouncedQuery, filterStatus) {
        baseResults.filter { r ->
            val matchesQuery = debouncedQuery.isBlank() ||
                r.studentName.contains(debouncedQuery, ignoreCase = true) ||
                r.studentCode.contains(debouncedQuery, ignoreCase = true) ||
                r.examTitle.contains(debouncedQuery, ignoreCase = true) ||
                r.subject.contains(debouncedQuery, ignoreCase = true) ||
                r.batch.contains(debouncedQuery, ignoreCase = true) ||
                r.assignedTeacherName.contains(debouncedQuery, ignoreCase = true)
            val matchesFilter = when (filterStatus) {
                "PASS" -> r.passed
                "FAIL" -> !r.passed
                "PENDING" -> r.gradingStatusEnum() == GradingStatus.PENDING_REVIEW
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    val timeFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Results (${baseResults.size})") },
                modifier = Modifier.testTag("results_tab_monitor")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Reports & Export") },
                modifier = Modifier.testTag("results_tab_export")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Audit & Alerts") },
                modifier = Modifier.testTag("results_tab_audit")
            )
        }

        when (selectedTab) {
            0 -> {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter by student, teacher, batch, subject, exam...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All", "PASS" to "Passed", "FAIL" to "Failed", "PENDING" to "Pending Review").forEach { (k, label) ->
                            FilterChip(selected = filterStatus == k, onClick = { filterStatus = k }, label = { Text(label) })
                        }
                    }
                }
                if (isLoading && results.isEmpty()) {
                    LoadingSkeletonList(cardCount = 4)
                } else LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredResults.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.Assessment,
                                title = "No Matching Results",
                                subtitle = "Student submissions will appear here immediately after auto-evaluation."
                            )
                        }
                    } else {
                        items(filteredResults, key = { it.id }) { res ->
                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${res.studentName} ${if (res.studentCode.isNotBlank()) "(${res.studentCode})" else ""}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${res.examTitle} • ${res.subject}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        StatusPill(
                                            text = if (res.passed) "PASS" else "FAIL",
                                            textColor = if (res.passed) StatusSuccess else StatusError,
                                            backgroundColor = if (res.passed) StatusSuccessContainer else StatusErrorContainer
                                        )
                                    }
                                    Text(
                                        text = String.format(
                                            Locale.getDefault(),
                                            "Marks: %.1f/%.1f (%.1f%%)  •  ✓%d  ✗%d  ○%d  •  %s",
                                            res.obtainedMarks, res.totalMarks, res.percentage,
                                            res.correctCount, res.wrongCount, res.unattemptedCount,
                                            EvaluationEngine.formatDuration(res.timeTakenSeconds)
                                        ),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                        color = BrandDarkBlue
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Batch: ${res.batch.ifBlank { "General" }} • Teacher: ${res.assignedTeacherName.ifBlank { "—" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = { onOpenResultAnalysis(res) },
                                            modifier = Modifier.testTag("view_analysis_${res.id}")
                                        ) {
                                            Text(if (res.pendingManualCount > 0) "Grade & Review" else "Question Analysis")
                                        }
                                    }
                                }
                            }
                        }
                        if (canLoadMoreResults) {
                            item {
                                OutlinedButton(
                                    onClick = onLoadMoreResults,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("load_more_results_button")
                                ) {
                                    Text("Load More Results")
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Institute Performance Reports & Export", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Export currently filtered results (${filteredResults.size} records) as a branded Super Sonic Computers Education PDF report or spreadsheet-ready CSV/Excel file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                ReportExportManager.exportResultsPdf(
                                                    context = context,
                                                    reportTitle = "Comprehensive Institute Performance Report",
                                                    subtitle = "Filter: $filterStatus • Search: ${searchQuery.ifBlank { "All Records" }}",
                                                    results = filteredResults
                                                ).fold(
                                                    onSuccess = { file ->
                                                        exportStatus = "PDF Report generated (${file.name})"
                                                        ReportExportManager.shareExportedFile(context, file, "application/pdf")
                                                    },
                                                    onFailure = { exportStatus = "PDF export failed: ${it.localizedMessage}" }
                                                )
                                            }
                                        },
                                        enabled = filteredResults.isNotEmpty(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("admin_export_pdf_button")
                                    ) {
                                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Export PDF")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                ReportExportManager.exportResultsCsv(context, filteredResults).fold(
                                                    onSuccess = { file ->
                                                        exportStatus = "CSV/Excel Report generated (${file.name})"
                                                        ReportExportManager.shareExportedFile(context, file, "text/csv")
                                                    },
                                                    onFailure = { exportStatus = "CSV export failed: ${it.localizedMessage}" }
                                                )
                                            }
                                        },
                                        enabled = filteredResults.isNotEmpty(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("admin_export_csv_button")
                                    ) {
                                        Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Export CSV")
                                    }
                                }
                                exportStatus?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
                                }
                            }
                        }
                    }

                    // Batch-wise Summary Breakdown
                    item { SectionHeader(title = "Batch-Wise Performance Summary") }
                    val byBatch = baseResults.groupBy { it.batch.ifBlank { "General" } }.entries.toList()
                    if (byBatch.isEmpty()) {
                        item {
                            Text("No batch results recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(byBatch, key = { it.key }) { (batchName, list) ->
                            val avg = list.map { it.percentage }.average()
                            val passRate = list.count { it.passed } * 100.0 / list.size
                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(batchName, style = MaterialTheme.typography.titleSmall)
                                        Text("${list.size} submissions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        String.format(Locale.getDefault(), "Avg %.1f%% • Pass %.0f%%", avg, passRate),
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                        color = BrandDarkBlue
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { SectionHeader(title = "Recent Notifications (${notifications.size})") }
                    if (notifications.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.Notifications,
                                title = "No Notifications Yet",
                                subtitle = "Exam assignments and student submissions automatically create alerts."
                            )
                        }
                    } else {
                        items(notifications.take(15), key = { it.id }) { n ->
                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(n.title, style = MaterialTheme.typography.titleSmall)
                                    Text(n.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    item { SectionHeader(title = "Admin Security Audit Log (${auditLogs.size})") }
                    if (auditLogs.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.History,
                                title = "Audit Trail Ready",
                                subtitle = "Administrative creations, imports, and status updates are logged here."
                            )
                        }
                    } else {
                        items(auditLogs.take(25), key = { it.id }) { log ->
                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        StatusPill(log.action, BrandDarkBlue, StatusWarningContainer)
                                        Text(
                                            log.createdAt?.toDate()?.let { timeFmt.format(it) } ?: "Just now",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(log.details, style = MaterialTheme.typography.bodySmall)
                                    Text("${log.actorEmail} (${log.actorRole})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
