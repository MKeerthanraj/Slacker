package com.slacker.app.ui.quickadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slacker.app.data.entities.CaseCriticality
import com.slacker.app.data.entities.CaseStatus
import com.slacker.app.data.entities.SupportCaseEntity
import com.slacker.app.data.entities.TaskEntity
import com.slacker.app.groq.QuickAddResult
import com.slacker.app.ui.CaseEditorDialog
import com.slacker.app.ui.TaskEditorDialog
import com.slacker.app.ui.formatDate
import com.slacker.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch

private data class ChatLine(val text: String, val fromUser: Boolean)

@Composable
fun QuickAddScreen(viewModel: AppViewModel) {
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var draftTask by remember { mutableStateOf<TaskEntity?>(null) }
    var draftCase by remember { mutableStateOf<SupportCaseEntity?>(null) }
    var editingTask by remember { mutableStateOf(false) }
    var editingCase by remember { mutableStateOf(false) }
    var draftPrompt by remember { mutableStateOf("") }
    val chatLines = remember { mutableStateListOf<ChatLine>() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Quick Add", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Use AI to draft an item, or add manually from the Tasks and Cases tabs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chatLines.size) { index ->
                val line = chatLines[index]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (line.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(line.text, modifier = Modifier.padding(10.dp))
                    }
                }
            }
            item {
                draftTask?.let {
                    DraftTaskPill(
                        task = it,
                        onEdit = { editingTask = true },
                        onConfirm = {
                            viewModel.saveTask(it)
                            draftTask = null
                            chatLines.add(ChatLine("Task created. Add another?", fromUser = false))
                        }
                    )
                }
                draftCase?.let {
                    DraftCasePill(
                        supportCase = it,
                        onEdit = { editingCase = true },
                        onConfirm = {
                            viewModel.saveCase(it)
                            draftCase = null
                            chatLines.add(ChatLine("Support case created. Add another?", fromUser = false))
                        }
                    )
                }
            }
            if (loading) {
                item { CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Describe a task/case or add missing draft data...") },
                singleLine = false,
                maxLines = 4
            )
            Button(
                enabled = input.isNotBlank() && !loading,
                onClick = {
                    val text = input
                    input = ""
                    chatLines.add(ChatLine(text, fromUser = true))
                    loading = true
                    scope.launch {
                        val currentTask = draftTask
                        val currentCase = draftCase
                        if (currentTask != null) {
                            draftPrompt = "$draftPrompt. $text"
                            when (val result = viewModel.quickAdd(draftPrompt)) {
                                is QuickAddResult.TaskParsed -> draftTask = currentTask.copy(
                                    title = result.title.ifBlank { currentTask.title },
                                    description = result.description.ifBlank { currentTask.description },
                                    dueAtEpochMillis = result.dueAtEpochMillis ?: currentTask.dueAtEpochMillis,
                                    assignee = result.assignee.ifBlank { currentTask.assignee },
                                    notes = result.notes.ifBlank { currentTask.notes },
                                    repeatOption = result.repeatOption.ifBlank { currentTask.repeatOption }
                                )
                                else -> chatLines.add(ChatLine("I couldn't map that automatically. Tap the draft pill to place it manually.", false))
                            }
                        } else if (currentCase != null) {
                            draftPrompt = "$draftPrompt. $text"
                            when (val result = viewModel.quickAdd(draftPrompt)) {
                                is QuickAddResult.CaseParsed -> draftCase = currentCase.copy(
                                    title = result.title.ifBlank { currentCase.title },
                                    description = result.description.ifBlank { currentCase.description },
                                    severityLevel = result.severityLevel,
                                    productAlignment = result.productAlignment.ifBlank { currentCase.productAlignment },
                                    criticality = runCatching { CaseCriticality.valueOf(result.criticality.uppercase()) }.getOrDefault(currentCase.criticality),
                                    assignee = result.assignee.ifBlank { currentCase.assignee },
                                    notes = result.notes.ifBlank { currentCase.notes }
                                )
                                else -> chatLines.add(ChatLine("I couldn't map that automatically. Tap the draft pill to place it manually.", false))
                            }
                        } else {
                            draftPrompt = text
                            when (val result = viewModel.quickAdd(text)) {
                                is QuickAddResult.TaskParsed -> draftTask = TaskEntity(
                                    title = result.title,
                                    description = result.description,
                                    dueAtEpochMillis = result.dueAtEpochMillis,
                                    assignee = result.assignee,
                                    notes = result.notes,
                                    repeatOption = result.repeatOption
                                )
                                is QuickAddResult.CaseParsed -> draftCase = SupportCaseEntity(
                                    title = result.title,
                                    description = result.description,
                                    severityLevel = result.severityLevel,
                                    productAlignment = result.productAlignment,
                                    criticality = runCatching { CaseCriticality.valueOf(result.criticality.uppercase()) }.getOrDefault(CaseCriticality.NORMAL),
                                    assignee = result.assignee,
                                    notes = result.notes
                                )
                                is QuickAddResult.NeedsClarification -> chatLines.add(ChatLine(result.question, false))
                                is QuickAddResult.Error -> chatLines.add(ChatLine(result.message, false))
                            }
                        }
                        loading = false
                    }
                }
            ) { Text("Send") }
        }
    }

    if (editingTask) {
        draftTask?.let {
            TaskEditorDialog(it, onDismiss = { editingTask = false }, onSave = { saved ->
                draftTask = saved
                editingTask = false
            })
        }
    }
    if (editingCase) {
        draftCase?.let {
            CaseEditorDialog(it, productOptions = viewModel.productAlignments.value, onDismiss = { editingCase = false }, onSave = { saved ->
                draftCase = saved
                editingCase = false
            })
        }
    }
}

@Composable
private fun DraftTaskPill(task: TaskEntity, onEdit: () -> Unit, onConfirm: () -> Unit) {
    val missing = listOfNotNull(
        "due date".takeIf { task.dueAtEpochMillis == null },
        "assignee".takeIf { task.assignee.isBlank() },
        "description".takeIf { task.description.isBlank() }
    )
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI task draft: ${task.title}", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onEdit, label = { Text(task.dueAtEpochMillis?.let { "Due ${formatDate(it)}" } ?: "Missing due") })
                AssistChip(onClick = onEdit, label = { Text(task.assignee.ifBlank { "Missing assignee" }) })
            }
            if (missing.isNotEmpty()) Text("Missing: ${missing.joinToString()}", style = MaterialTheme.typography.labelSmall)
            Button(onClick = onConfirm) { Text("Confirm Task") }
        }
    }
}

@Composable
private fun DraftCasePill(supportCase: SupportCaseEntity, onEdit: () -> Unit, onConfirm: () -> Unit) {
    val missing = listOfNotNull(
        "product alignment".takeIf { supportCase.productAlignment.isBlank() },
        "created date".takeIf { supportCase.createdAtEpochMillis == 0L },
        "status history".takeIf { supportCase.status.ordinal > CaseStatus.UNDER_INITIAL_REVIEW.ordinal && supportCase.statusHistory.isBlank() },
        "assignee".takeIf { supportCase.assignee.isBlank() }
    )
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AI case draft: ${supportCase.title}", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onEdit, label = { Text(supportCase.criticality.name.lowercase().replaceFirstChar { it.titlecase() }) })
                AssistChip(onClick = onEdit, label = { Text(supportCase.productAlignment.ifBlank { "Missing product" }) })
            }
            if (missing.isNotEmpty()) Text("Missing: ${missing.joinToString()}", style = MaterialTheme.typography.labelSmall)
            Button(onClick = onConfirm) { Text("Confirm Case") }
        }
    }
}
