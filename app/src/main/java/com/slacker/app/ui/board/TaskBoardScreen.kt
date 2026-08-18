package com.slacker.app.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slacker.app.data.entities.TaskEntity
import com.slacker.app.data.entities.TaskStatus
import com.slacker.app.ui.TaskEditorDialog
import com.slacker.app.ui.formatDate
import com.slacker.app.ui.getDueCountdown
import com.slacker.app.ui.taskStatusStyle
import com.slacker.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch

private val statuses = listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.DONE)

@Composable
fun TaskBoardScreen(viewModel: AppViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var selectedJump by remember { mutableStateOf(statuses.first()) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { editing = TaskEntity(title = "", dueAtEpochMillis = null) }) { Text("Add Task") }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusJumpMenu(
                value = selectedJump,
                statuses = statuses,
                countFor = { status -> tasks.count { it.status == status } },
                labelFor = { status -> taskStatusStyle(status).let { "${it.emoji} ${it.label}" } },
                onSelect = { status ->
                    selectedJump = status
                    scope.launch {
                        var targetIndex = 0
                        for (s in statuses) {
                            if (s == status) break
                            targetIndex++
                            if (!viewModel.collapsedSections.value.contains("task_${s.name}")) {
                                val count = tasks.count { it.status == s }
                                targetIndex += if (count == 0) 1 else count
                            }
                        }
                        listState.animateScrollToItem(targetIndex)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("${tasks.size} total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            statuses.forEach { status ->
                val sectionKey = "task_${status.name}"
                val isCollapsed = viewModel.collapsedSections.value.contains(sectionKey)
                val sectionTasks = tasks.filter { it.status == status }.sortedBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }

                item(key = status.name) {
                    val style = taskStatusStyle(status)
                    StatusSectionHeader(
                        label = style.label,
                        emoji = style.emoji,
                        color = style.color,
                        count = sectionTasks.size,
                        isCollapsed = isCollapsed,
                        onToggle = { viewModel.toggleSection(sectionKey) }
                    )
                }
                if (!isCollapsed) {
                    if (sectionTasks.isEmpty()) {
                        item(key = "${status.name}-empty") { EmptyStatusCard() }
                    } else {
                        items(sectionTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                onOpen = { editing = task },
                                onStatus = { viewModel.updateTaskStatus(task, it) }
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { task ->
        TaskEditorDialog(
            initial = task,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveTask(it)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusJumpMenu(
    value: TaskStatus,
    statuses: List<TaskStatus>,
    countFor: (TaskStatus) -> Int,
    labelFor: (TaskStatus) -> String,
    onSelect: (TaskStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = "${labelFor(value)} (${countFor(value)})",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Jump to status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            statuses.forEach { status ->
                DropdownMenuItem(
                    text = { Text("${labelFor(status)} (${countFor(status)})") },
                    onClick = {
                        expanded = false
                        onSelect(status)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusSectionHeader(
    label: String,
    emoji: String,
    color: Color,
    count: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable { onToggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(28.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text("$emoji $label", fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.weight(1f))
        Text(if (isCollapsed) "▶" else "▼", modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outline)
        Text("$count", color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun EmptyStatusCard() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Text("Nothing here", modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(task: TaskEntity, onOpen: () -> Unit, onStatus: (TaskStatus) -> Unit) {
    val now = System.currentTimeMillis()
    val overdue = task.dueAtEpochMillis != null && task.dueAtEpochMillis < now && task.status != TaskStatus.DONE
    val style = taskStatusStyle(task.status)
    var menu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
            colors = CardDefaults.cardColors(
                containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(style.emoji)
                }
                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(task.description, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { menu = true }, label = { Text(style.label) })
                    task.dueAtEpochMillis?.let { due ->
                        val countdown = getDueCountdown(due)
                        val dateText = (if (overdue) "Overdue " else "Due ") + formatDate(due)
                        val text = if (countdown != null) "$dateText ($countdown)" else dateText
                        Text(
                            text,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (overdue || countdown != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (task.assignee.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Assignee: ${task.assignee}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            statuses.forEach { status ->
                val nextStyle = taskStatusStyle(status)
                DropdownMenuItem(
                    text = { Text("${nextStyle.emoji} ${nextStyle.label}") },
                    onClick = {
                        menu = false
                        onStatus(status)
                    }
                )
            }
        }
    }
}
