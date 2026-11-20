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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.BankQuestion
import com.example.data.model.BundledQuestionSet
import com.example.data.model.ExamResult
import com.example.data.model.Examination
import com.example.data.model.StudentProfile
import com.example.data.model.TeacherProfile
import com.example.ui.components.BarChartCard
import com.example.ui.components.ChartBarEntry
import com.example.ui.components.DonutBreakdownCard
import com.example.ui.components.InstituteCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.LoadingSkeletonList
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TrendLineChartCard
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.BrandSurfaceTint
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusPurple
import com.example.ui.theme.StatusPurpleContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import java.util.Locale

@Composable
fun AdminOverviewScreen(
    students: List<StudentProfile>,
    teachers: List<TeacherProfile>,
    exams: List<Examination>,
    questions: List<BankQuestion>,
    results: List<ExamResult>,
    bundledSets: List<BundledQuestionSet>,
    includeDemoRecords: Boolean,
    busy: Boolean,
    onToggleDemoVisibility: (Boolean) -> Unit,
    onSeedDemo: () -> Unit,
    onClearDemo: () -> Unit,
    onNavigateToPapers: () -> Unit,
    onNavigateToExams: () -> Unit,
    isLoading: Boolean = false
) {
    if (isLoading && students.isEmpty() && teachers.isEmpty() && exams.isEmpty() && results.isEmpty()) {
        LoadingSkeletonList(cardCount = 5)
        return
    }

    val visibleStudents = remember(students, includeDemoRecords) {
        if (includeDemoRecords) students else students.filterNot { it.demoRecord }
    }
    val visibleTeachers = remember(teachers, includeDemoRecords) {
        if (includeDemoRecords) teachers else teachers.filterNot { it.demoRecord }
    }
    val visibleExams = remember(exams, includeDemoRecords) {
        if (includeDemoRecords) exams else exams.filterNot { it.demoRecord }
    }
    val visibleResults = remember(results, includeDemoRecords) {
        if (includeDemoRecords) results else results.filterNot { it.demoRecord }
    }

    val hasAnyDemo = remember(students, teachers, exams, results) {
        students.any { it.demoRecord } || teachers.any { it.demoRecord } ||
            exams.any { it.demoRecord } || results.any { it.demoRecord }
    }

    val avgPerformance = remember(visibleResults) {
        if (visibleResults.isNotEmpty()) visibleResults.map { it.percentage }.average() else 0.0
    }
    val passedCount = remember(visibleResults) { visibleResults.count { it.passed } }
    val failedCount = visibleResults.size - passedCount
    val passPercentage = if (visibleResults.isNotEmpty()) (passedCount * 100.0 / visibleResults.size) else 0.0
    val bundledTotalQuestions = remember(bundledSets) { bundledSets.sumOf { it.questionCount } }

    val subjectChartEntries = remember(visibleResults) {
        visibleResults
            .groupBy { it.subject.ifBlank { "General" } }
            .map { (subject, list) ->
                ChartBarEntry(
                    label = subject.replace("Computer Fundamentals", "Comp Fund").take(11),
                    value = list.map { it.percentage }.average().toFloat(),
                    maxValue = 100f,
                    valueSuffix = "%"
                )
            }
            .sortedByDescending { it.value }
    }

    val trendPoints = remember(visibleResults) {
        visibleResults
            .sortedBy { it.submittedAt?.seconds ?: 0L }
            .takeLast(20)
            .mapIndexed { idx, res ->
                "T${idx + 1}" to res.percentage.toFloat()
            }
    }

    val teacherLoadEntries = remember(visibleTeachers, visibleResults) {
        val resultsByTeacherId = visibleResults.filter { it.assignedTeacherId.isNotBlank() }.groupBy { it.assignedTeacherId }
        val resultsByTeacherUid = visibleResults.filter { it.assignedTeacherUid.isNotBlank() }.groupBy { it.assignedTeacherUid }
        visibleTeachers.mapNotNull { t ->
            val tResults = (resultsByTeacherId[t.id].orEmpty() +
                if (t.linkedUid.isNotBlank()) resultsByTeacherUid[t.linkedUid].orEmpty() else emptyList())
                .distinctBy { it.id }
            if (tResults.isEmpty()) null else ChartBarEntry(
                label = t.name.substringAfterLast(" ").take(10),
                value = tResults.map { it.percentage }.average().toFloat(),
                maxValue = 100f,
                valueSuffix = "%"
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            InstituteCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = BrandDarkBlue
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = "SUPER ADMIN CONSOLE",
                            textColor = BrandDarkBlue,
                            backgroundColor = Color(0xFFD7F0FD)
                        )
                        StatusPill(
                            text = "${bundledSets.size} PDF Sets • $bundledTotalQuestions Qs",
                            textColor = Color.White,
                            backgroundColor = Color(0x3370C5F0)
                        )
                    }
                    Text(
                        text = "Super Sonic Computers Education",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Learn Smart. Practice More. Achieve Excellence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD6F0FD)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onNavigateToPapers,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF70C5F0),
                                contentColor = Color(0xFF10375C)
                            ),
                            modifier = Modifier.testTag("overview_import_pdfs_button")
                        ) {
                            Icon(Icons.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF Question Bank")
                        }
                        OutlinedButton(
                            onClick = onNavigateToExams,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.testTag("overview_create_exam_button")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Manage Exams")
                        }
                    }
                }
            }
        }

        // KPI Grid (2 columns per row)
        item {
            SectionHeader(
                title = "Institute Overview",
                subtitle = if (includeDemoRecords && hasAnyDemo) "Showing Real + Demo Preview records" else "Live Institute Records"
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMetricCard(
                    title = "Total Students",
                    value = visibleStudents.size.toString(),
                    subtitle = "${visibleStudents.count { it.active }} active accounts",
                    icon = Icons.Outlined.Groups,
                    accentColor = BrandDarkBlue,
                    accentBg = BrandSurfaceTint,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Total Teachers",
                    value = visibleTeachers.size.toString(),
                    subtitle = "${visibleTeachers.count { it.active }} active faculty",
                    icon = Icons.Outlined.School,
                    accentColor = StatusInfo,
                    accentBg = StatusInfoContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMetricCard(
                    title = "Total Tests",
                    value = visibleExams.size.toString(),
                    subtitle = "${visibleExams.count { it.status == "PUBLISHED" }} published",
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    accentColor = StatusPurple,
                    accentBg = StatusPurpleContainer,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Question Bank",
                    value = questions.size.toString(),
                    subtitle = "+$bundledTotalQuestions ready in PDFs",
                    icon = Icons.Outlined.Quiz,
                    accentColor = StatusWarning,
                    accentBg = StatusWarningContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiMetricCard(
                    title = "Tests Completed",
                    value = visibleResults.size.toString(),
                    subtitle = "${visibleResults.count { it.pendingManualCount > 0 }} pending review",
                    icon = Icons.Outlined.CheckCircle,
                    accentColor = StatusSuccess,
                    accentBg = StatusSuccessContainer,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Avg Performance",
                    value = String.format(Locale.getDefault(), "%.1f%%", avgPerformance),
                    subtitle = String.format(Locale.getDefault(), "Pass rate %.1f%%", passPercentage),
                    icon = Icons.Outlined.TrendingUp,
                    accentColor = BrandDarkBlue,
                    accentBg = BrandSurfaceTint,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Analytics Charts
        item {
            DonutBreakdownCard(
                title = "Examination Pass Percentage",
                centerLabel = "Pass Rate",
                centerValue = String.format(Locale.getDefault(), "%.0f%%", passPercentage),
                segments = listOf(
                    Triple("Passed Students", passedCount, StatusSuccess),
                    Triple("Failed / Needs Help", failedCount, StatusError),
                    Triple("Published Exams", visibleExams.count { it.status == "PUBLISHED" }, StatusInfo)
                )
            )
        }

        item {
            BarChartCard(
                title = "Subject-Wise Performance",
                subtitle = "Average student percentage across computer subjects",
                entries = subjectChartEntries
            )
        }

        item {
            TrendLineChartCard(
                title = "Student Performance Progression",
                subtitle = "Chronological examination score trend across submissions",
                points = trendPoints
            )
        }

        if (teacherLoadEntries.isNotEmpty()) {
            item {
                BarChartCard(
                    title = "Teacher-Wise Student Average",
                    subtitle = "Average percentage achieved by each teacher's assigned students",
                    entries = teacherLoadEntries
                )
            }
        }

        // Demo Data Separation Card
        item {
            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Optional Demo Preview Workspace", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Demo records are strictly tagged (demoRecord=true) and kept separate from real institute accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Outlined.Percent, contentDescription = null, tint = BrandDarkBlue)
                    }
                    if (hasAnyDemo) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Include Demo Records in Views", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = includeDemoRecords,
                                onCheckedChange = onToggleDemoVisibility,
                                modifier = Modifier.testTag("toggle_demo_records_switch")
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onSeedDemo,
                            enabled = !busy,
                            modifier = Modifier.testTag("seed_demo_data_button")
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Load Demo Sample")
                        }
                        if (hasAnyDemo) {
                            OutlinedButton(
                                onClick = onClearDemo,
                                enabled = !busy,
                                modifier = Modifier.testTag("clear_demo_data_button")
                            ) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear Demo Data")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
