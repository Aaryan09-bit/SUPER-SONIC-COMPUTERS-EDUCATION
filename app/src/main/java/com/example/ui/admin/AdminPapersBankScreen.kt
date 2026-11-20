package com.example.ui.admin

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BankQuestion
import com.example.data.model.BundledQuestionSet
import com.example.data.model.DifficultyLevel
import com.example.data.model.ExtractedDraftQuestion
import com.example.data.model.PdfPaper
import com.example.data.model.QuestionType
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.InstituteCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.BrandSurfaceTint
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningContainer
import com.example.viewmodel.PdfExtractionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPapersBankScreen(
    bundledSets: List<BundledQuestionSet>,
    pdfPapers: List<PdfPaper>,
    questions: List<BankQuestion>,
    extractionState: PdfExtractionUiState,
    busy: Boolean,
    onImportBundledSet: (BundledQuestionSet) -> Unit,
    onExtractPdfUri: (Uri, String) -> Unit,
    onUpdateExtractionMeta: (String, String, String, String) -> Unit,
    onUpdateDraft: (ExtractedDraftQuestion) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onSaveReviewedDrafts: () -> Unit,
    onDismissDrafts: () -> Unit,
    onSaveManualQuestion: (
        String?, String, QuestionType, List<String>, List<String>, String,
        String, String, String, DifficultyLevel, Double, Boolean
    ) -> Unit,
    onDeleteQuestion: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var previewingSet by remember { mutableStateOf<BundledQuestionSet?>(null) }
    var editingQuestion by remember { mutableStateOf<BankQuestion?>(null) }
    var showAddQuestion by remember { mutableStateOf(false) }

    val importedPaperIds = remember(pdfPapers) { pdfPapers.map { it.id }.toSet() }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var fileName = "Question_Paper.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) {
                    fileName = cursor.getString(idx) ?: fileName
                }
            }
            onExtractPdfUri(uri, fileName)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("42 PDF Sets") },
                modifier = Modifier.testTag("papers_bundled_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Upload PDF AI") },
                modifier = Modifier.testTag("papers_upload_ai_tab")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Bank (${questions.size})") },
                modifier = Modifier.testTag("papers_bank_tab")
            )
        }

        when (selectedTab) {
            0 -> {
                val filteredSets = bundledSets.filter {
                    searchQuery.isBlank() ||
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.primarySubject.contains(searchQuery, ignoreCase = true) ||
                        it.rangeLabel.contains(searchQuery, ignoreCase = true)
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter 42 uploaded PDF sets (1,237 MCQs)...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSets, key = { it.setId }) { set ->
                        val isImported = importedPaperIds.contains("paper_${set.setId}")
                        InstituteCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(set.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${set.sourceFileName} • ${set.questionCount} MCQs • ${set.primarySubject}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(
                                        text = if (isImported) "Imported" else set.rangeLabel,
                                        textColor = if (isImported) StatusSuccess else BrandDarkBlue,
                                        backgroundColor = if (isImported) StatusSuccessContainer else BrandSurfaceTint
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { previewingSet = set }) {
                                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Preview Qs")
                                    }
                                    Button(
                                        onClick = { onImportBundledSet(set) },
                                        enabled = !busy,
                                        modifier = Modifier.testTag("import_set_${set.setId}")
                                    ) {
                                        Icon(Icons.Outlined.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isImported) "Re-Import to Bank" else "Import to Bank")
                                    }
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
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = BrandDarkBlue)
                                    Text("AI Question Paper PDF Extractor", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "Upload any PDF question paper from your device. AI extracts MCQs, Multi-Select, True/False, Fill-in-Blanks, and Short-Answer questions. If no answer key is present in the PDF, you can review and set official answers manually before saving.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                    enabled = !extractionState.extracting && !busy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("upload_pdf_ai_button")
                                ) {
                                    if (extractionState.extracting) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Scanning PDF Pages with AI...")
                                    } else {
                                        Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Select Question Paper PDF")
                                    }
                                }
                                extractionState.error?.let { err ->
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = StatusError)
                                }
                            }
                        }
                    }

                    if (extractionState.drafts.isNotEmpty()) {
                        item {
                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Review Extracted Questions (${extractionState.drafts.size})", style = MaterialTheme.typography.titleSmall)
                                        StatusPill(
                                            text = if (extractionState.answerKeyFoundInPdf) "Answer Key Detected" else "Manual Key Needed",
                                            textColor = if (extractionState.answerKeyFoundInPdf) StatusSuccess else StatusWarning,
                                            backgroundColor = if (extractionState.answerKeyFoundInPdf) StatusSuccessContainer else StatusWarningContainer
                                        )
                                    }
                                    if (!extractionState.answerKeyFoundInPdf) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(18.dp))
                                            Text(
                                                "No answer key was detected in this PDF. Enter or approve the official answer for each question below.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StatusWarning
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = extractionState.paperTitle,
                                        onValueChange = { onUpdateExtractionMeta(it, extractionState.subject, extractionState.chapter, extractionState.topic) },
                                        label = { Text("Paper Title") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = extractionState.subject,
                                            onValueChange = { onUpdateExtractionMeta(extractionState.paperTitle, it, extractionState.chapter, extractionState.topic) },
                                            label = { Text("Subject") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = extractionState.chapter,
                                            onValueChange = { onUpdateExtractionMeta(extractionState.paperTitle, extractionState.subject, it, extractionState.topic) },
                                            label = { Text("Chapter") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = onSaveReviewedDrafts,
                                            enabled = !busy,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("save_extracted_questions_button")
                                        ) {
                                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Save All to Bank")
                                        }
                                        OutlinedButton(onClick = onDismissDrafts) { Text("Discard") }
                                    }
                                }
                            }
                        }

                        itemsIndexed(extractionState.drafts, key = { _, d -> d.tempId }) { idx, draft ->
                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Q${idx + 1} • ${draft.questionType.label}", style = MaterialTheme.typography.labelLarge, color = BrandDarkBlue)
                                        IconButton(onClick = { onRemoveDraft(draft.tempId) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Remove Draft", tint = StatusError)
                                        }
                                    }
                                    OutlinedTextField(
                                        value = draft.questionText,
                                        onValueChange = { onUpdateDraft(draft.copy(questionText = it)) },
                                        label = { Text("Question Statement") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (draft.options.isNotEmpty()) {
                                        val letters = listOf("A", "B", "C", "D", "E", "F")
                                        draft.options.forEachIndexed { oIdx, opt ->
                                            Text(
                                                "(${letters.getOrElse(oIdx) { "${oIdx + 1}" }}) $opt",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = draft.correctAnswer,
                                            onValueChange = { onUpdateDraft(draft.copy(correctAnswer = it, approved = it.isNotBlank())) },
                                            label = { Text("Official Answer Key (e.g. A, A,C, True)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Checkbox(
                                                checked = draft.approved,
                                                onCheckedChange = { onUpdateDraft(draft.copy(approved = it)) }
                                            )
                                            Text("Approved", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                val filteredBank = questions.filter {
                    searchQuery.isBlank() ||
                        it.questionText.contains(searchQuery, ignoreCase = true) ||
                        it.subject.contains(searchQuery, ignoreCase = true) ||
                        it.chapter.contains(searchQuery, ignoreCase = true)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search question bank...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showAddQuestion = true },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("add_manual_question_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("New Q")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredBank.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.Quiz,
                                title = "Question Bank Empty",
                                subtitle = "Import any of the 42 pre-loaded PDF sets, upload a PDF via AI OCR, or create questions manually."
                            )
                        }
                    } else {
                        items(filteredBank.take(120), key = { it.id }) { q ->
                            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            StatusPill(q.subject, BrandDarkBlue, BrandSurfaceTint)
                                            StatusPill(q.typeEnum().name, StatusInfo, StatusInfoContainer)
                                        }
                                        StatusPill(
                                            text = if (q.answerApproved && q.correctAnswers.isNotEmpty()) "Key: ${q.correctAnswers.joinToString(",")}" else "Key Missing",
                                            textColor = if (q.answerApproved && q.correctAnswers.isNotEmpty()) StatusSuccess else StatusWarning,
                                            backgroundColor = if (q.answerApproved && q.correctAnswers.isNotEmpty()) StatusSuccessContainer else StatusWarningContainer
                                        )
                                    }
                                    Text(q.questionText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    if (q.options.isNotEmpty()) {
                                        val letters = listOf("A", "B", "C", "D", "E", "F")
                                        Text(
                                            q.options.mapIndexed { i, o -> "(${letters.getOrElse(i) { "${i + 1}" }}) $o" }.joinToString("  •  "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${q.difficultyEnum().label} • ${q.marks} marks • ${q.chapter.ifBlank { "General" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row {
                                            IconButton(onClick = { editingQuestion = q }) {
                                                Icon(Icons.Outlined.Edit, contentDescription = "Edit Question")
                                            }
                                            IconButton(onClick = { onDeleteQuestion(q.id) }) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete Question", tint = StatusError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    previewingSet?.let { set ->
        AlertDialog(
            onDismissRequest = { previewingSet = null },
            title = { Text("${set.title} (${set.questionCount} Qs)") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(set.questions) { q ->
                        Column {
                            Text("Q${q.number}. ${q.text}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            val letters = listOf("A", "B", "C", "D")
                            q.options.forEachIndexed { i, o ->
                                Text("  (${letters.getOrElse(i) { "${i + 1}" }}) $o", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "  Answer Key: ${q.correctOption.ifBlank { "Missing — set manually after import" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (q.correctOption.isNotBlank()) StatusSuccess else StatusWarning
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onImportBundledSet(set)
                    previewingSet = null
                }) { Text("Import Set") }
            },
            dismissButton = { TextButton(onClick = { previewingSet = null }) { Text("Close") } }
        )
    }

    if (showAddQuestion || editingQuestion != null) {
        ManualQuestionDialog(
            existing = editingQuestion,
            onDismiss = { showAddQuestion = false; editingQuestion = null },
            onSave = { text, type, opts, ans, exp, subj, chap, topic, diff, marks, approved ->
                onSaveManualQuestion(
                    editingQuestion?.id, text, type, opts, ans, exp, subj, chap, topic, diff, marks, approved
                )
                showAddQuestion = false
                editingQuestion = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualQuestionDialog(
    existing: BankQuestion?,
    onDismiss: () -> Unit,
    onSave: (String, QuestionType, List<String>, List<String>, String, String, String, String, DifficultyLevel, Double, Boolean) -> Unit
) {
    var text by remember { mutableStateOf(existing?.questionText.orEmpty()) }
    var type by remember { mutableStateOf(existing?.typeEnum() ?: QuestionType.MCQ) }
    var typeMenu by remember { mutableStateOf(false) }
    var optA by remember { mutableStateOf(existing?.options?.getOrNull(0).orEmpty()) }
    var optB by remember { mutableStateOf(existing?.options?.getOrNull(1).orEmpty()) }
    var optC by remember { mutableStateOf(existing?.options?.getOrNull(2).orEmpty()) }
    var optD by remember { mutableStateOf(existing?.options?.getOrNull(3).orEmpty()) }
    var answerRaw by remember { mutableStateOf(existing?.correctAnswers?.joinToString(",").orEmpty()) }
    var subject by remember { mutableStateOf(existing?.subject ?: "MS Word") }
    var chapter by remember { mutableStateOf(existing?.chapter ?: "General") }
    var topic by remember { mutableStateOf(existing?.topic ?: "Core") }
    var explanation by remember { mutableStateOf(existing?.explanation.orEmpty()) }
    var difficulty by remember { mutableStateOf(existing?.difficultyEnum() ?: DifficultyLevel.MEDIUM) }
    var marksStr by remember { mutableStateOf((existing?.marks ?: 2.0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Question to Bank" else "Edit Question & Answer Key") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(expanded = typeMenu, onExpandedChange = { typeMenu = it }) {
                    OutlinedTextField(
                        value = type.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Question Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        QuestionType.entries.forEach { qt ->
                            DropdownMenuItem(text = { Text(qt.label) }, onClick = { type = qt; typeMenu = false })
                        }
                    }
                }
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Question Text *") })
                if (type == QuestionType.MCQ || type == QuestionType.MULTI_SELECT) {
                    OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Option A") }, singleLine = true)
                    OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Option B") }, singleLine = true)
                    OutlinedTextField(value = optC, onValueChange = { optC = it }, label = { Text("Option C") }, singleLine = true)
                    OutlinedTextField(value = optD, onValueChange = { optD = it }, label = { Text("Option D") }, singleLine = true)
                }
                OutlinedTextField(
                    value = answerRaw,
                    onValueChange = { answerRaw = it },
                    label = {
                        Text(
                            when (type) {
                                QuestionType.MCQ -> "Correct Option (A / B / C / D)"
                                QuestionType.MULTI_SELECT -> "Correct Options (e.g. A,C)"
                                QuestionType.TRUE_FALSE -> "Correct Answer (True / False)"
                                QuestionType.FILL_BLANK -> "Accepted Answer (use | for variants)"
                                QuestionType.SHORT_ANSWER -> "Reference Answer / Rubric"
                            }
                        )
                    },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = marksStr, onValueChange = { marksStr = it }, label = { Text("Marks") }, singleLine = true, modifier = Modifier.weight(0.6f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = chapter, onValueChange = { chapter = it }, label = { Text("Chapter") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = explanation, onValueChange = { explanation = it }, label = { Text("Explanation (optional)") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opts = when (type) {
                        QuestionType.TRUE_FALSE -> listOf("True", "False")
                        QuestionType.MCQ, QuestionType.MULTI_SELECT -> listOf(optA, optB, optC, optD).filter { it.isNotBlank() }
                        else -> emptyList()
                    }
                    val answers = answerRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(
                        text, type, opts, answers, explanation, subject, chapter, topic,
                        difficulty, marksStr.toDoubleOrNull() ?: 2.0, answers.isNotEmpty()
                    )
                },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
