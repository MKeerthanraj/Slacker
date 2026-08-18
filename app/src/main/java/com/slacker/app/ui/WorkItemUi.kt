package com.slacker.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slacker.app.data.entities.CaseCriticality
import com.slacker.app.data.entities.CaseStatus
import com.slacker.app.data.entities.SupportCaseEntity
import com.slacker.app.data.entities.TaskEntity
import com.slacker.app.data.entities.TaskStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class JiraStatusStyle(val label: String, val emoji: String, val color: Color)

fun taskStatusStyle(status: TaskStatus) = when (status) {
    TaskStatus.TODO -> JiraStatusStyle("To Do", "📋", Color(0xFF42526E))
    TaskStatus.IN_PROGRESS -> JiraStatusStyle("In Progress", "🔵", Color(0xFF0052CC))
    TaskStatus.BLOCKED -> JiraStatusStyle("Blocked", "⛔", Color(0xFFDE350B))
    TaskStatus.DONE -> JiraStatusStyle("Done", "✅", Color(0xFF00875A))
}

fun caseStatusStyle(status: CaseStatus) = when (status) {
    CaseStatus.NEW -> JiraStatusStyle("New", "🆕", Color(0xFF42526E))
    CaseStatus.UNDER_INITIAL_REVIEW -> JiraStatusStyle("Under Initial Review", "🧭", Color(0xFF6554C0))
    CaseStatus.ON_HOLD -> JiraStatusStyle("On Hold", "⏸", Color(0xFFFF8B00))
    CaseStatus.READY_FOR_DEVELOPMENT -> JiraStatusStyle("Ready for Development", "📦", Color(0xFF0052CC))
    CaseStatus.PENDING_OUTSIDE_LABS -> JiraStatusStyle("Pending Outside Labs", "🧪", Color(0xFF00A3BF))
    CaseStatus.UNDER_DEVELOPMENT -> JiraStatusStyle("Under Development", "🛠", Color(0xFF0052CC))
    CaseStatus.READY_FOR_QA -> JiraStatusStyle("Ready for QA", "✅", Color(0xFF00875A))
    CaseStatus.IN_TEST -> JiraStatusStyle("In Test", "🔎", Color(0xFF00A3BF))
    CaseStatus.READY_FOR_DEMO -> JiraStatusStyle("Ready for Demo", "🎬", Color(0xFF6554C0))
    CaseStatus.DONE_READY_TO_DEPLOY -> JiraStatusStyle("Done Ready to Deploy", "🚀", Color(0xFF00875A))
    CaseStatus.CLOSED -> JiraStatusStyle("Closed", "🔒", Color(0xFF42526E))
    CaseStatus.RCA_IN_PROGRESS -> JiraStatusStyle("RCA In Progress", "🧩", Color(0xFF0052CC))
    CaseStatus.RCA_IN_REVIEW -> JiraStatusStyle("RCA In Review", "📋", Color(0xFF6554C0))
    CaseStatus.RCA_COMPLETE -> JiraStatusStyle("RCA Complete", "🏁", Color(0xFF36B37E))
    CaseStatus.DONE_NO_CODE_CHANGES -> JiraStatusStyle("Done No Code Changes", "☑️", Color(0xFF00875A))
}

fun criticalityStyle(criticality: CaseCriticality) = when (criticality) {
    CaseCriticality.CRITICAL -> JiraStatusStyle("Critical", "🔥", Color(0xFFDE350B))
    CaseCriticality.MAJOR -> JiraStatusStyle("Major", "⚠️", Color(0xFFFF8B00))
    CaseCriticality.NORMAL -> JiraStatusStyle("Normal", "🟡", Color(0xFFFFAB00))
    CaseCriticality.LOW -> JiraStatusStyle("Low", "🟢", Color(0xFF36B37E))
}

fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault()).format(Date(epochMillis))

fun getDueCountdown(dueAtMillis: Long): String? {
    val now = System.currentTimeMillis()
    val diff = dueAtMillis - now
    if (diff <= 0) return null
    val days = diff / (24 * 3600_000L)
    if (days >= 7) return null
    if (days >= 1) return "${days}d left"
    val hours = diff / 3600_000L
    return if (hours > 0) "${hours}h left" else "${(diff % 3600_000L) / 60_000L}m left"
}

fun formatDateInput(epochMillis: Long?): String =
    epochMillis?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) } ?: ""

fun parseDateInput(text: String): Long? =
    listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd").firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale.getDefault()).parse(text.trim())?.time }.getOrNull()
    }

fun parseEndOfDayInput(text: String): Long? {
    val parsed = parseDateInput(text) ?: return null
    return Calendar.getInstance().apply {
        timeInMillis = parsed
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun repeatLabel(base: String, weeklyDay: String, monthlyDate: String): String = when (base) {
    "Weekly" -> "Weekly:$weeklyDay"
    "Monthly" -> "Monthly:$monthlyDate"
    else -> base
}

private fun repeatBase(value: String): String = value.substringBefore(":").ifBlank { "None" }

private fun repeatDetail(value: String): String = value.substringAfter(":", "")

private fun encodeStatusHistory(history: Map<CaseStatus, String>): String =
    history.mapNotNull { (status, value) ->
        parseDateInput(value)?.let { "${status.name}:$it" }
    }.joinToString("|")

private fun decodeStatusHistory(value: String): Map<CaseStatus, String> =
    value.split("|")
        .mapNotNull { entry ->
            val statusName = entry.substringBefore(":", "")
            val millis = entry.substringAfter(":", "").toLongOrNull() ?: return@mapNotNull null
            val status = runCatching { CaseStatus.valueOf(statusName) }.getOrNull() ?: return@mapNotNull null
            status to formatDateInput(millis)
        }
        .toMap()

private fun requiredHistoryStatuses(current: CaseStatus): List<CaseStatus> =
    CaseStatus.entries.takeWhile { it != current }.filter { it != CaseStatus.NEW }

@Composable
fun TaskEditorDialog(
    initial: TaskEntity,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial.title) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var due by remember(initial) { mutableStateOf(formatDateInput(initial.dueAtEpochMillis)) }
    var assignee by remember(initial) { mutableStateOf(initial.assignee) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }
    var repeat by remember(initial) { mutableStateOf(repeatBase(initial.repeatOption)) }
    var weeklyDay by remember(initial) { mutableStateOf(repeatDetail(initial.repeatOption).ifBlank { "Monday" }) }
    var monthlyDate by remember(initial) { mutableStateOf(repeatDetail(initial.repeatOption).ifBlank { "1" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add Task" else "Edit Task") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                DateTimeField("Due date", due, { due = it })
                OutlinedTextField(assignee, { assignee = it }, label = { Text("Assignee") }, modifier = Modifier.fillMaxWidth())
                SelectField("Repeat", repeat, listOf("None", "Daily", "Weekly", "Monthly")) { repeat = it }
                if (repeat == "Weekly") {
                    SelectField("Day of week", weeklyDay, listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")) { weeklyDay = it }
                }
                if (repeat == "Monthly") {
                    SelectField("Date of month", monthlyDate, (1..31).map { it.toString() }) { monthlyDate = it }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(initial.copy(title = title.trim(), description = description, dueAtEpochMillis = parseDateInput(due), assignee = assignee, notes = notes, repeatOption = repeatLabel(repeat, weeklyDay, monthlyDate)))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CaseEditorDialog(
    initial: SupportCaseEntity,
    productOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (SupportCaseEntity) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial.title) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var product by remember(initial) { mutableStateOf(initial.productAlignment) }
    var criticality by remember(initial) { mutableStateOf(initial.criticality) }
    var status by remember(initial) { mutableStateOf(initial.status) }
    var created by remember(initial) { mutableStateOf(formatDateInput(initial.createdAtEpochMillis)) }
    var assignee by remember(initial) { mutableStateOf(initial.assignee) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }
    val historyDates = remember(initial) { mutableStateMapOf<CaseStatus, String>().apply { putAll(decodeStatusHistory(initial.statusHistory)) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add Support Case" else "Edit Support Case") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                SelectField("Product alignment", product.ifBlank { productOptions.firstOrNull().orEmpty() }, productOptions.ifEmpty { listOf("General") }) { product = it }
                SelectField("Criticality", criticality.name.lowercase().replaceFirstChar { it.titlecase() }, CaseCriticality.entries.map { it.name.lowercase().replaceFirstChar { c -> c.titlecase() } }) {
                    criticality = CaseCriticality.valueOf(it.uppercase())
                }
                SelectField("Current status", caseStatusStyle(status).label, CaseStatus.entries.map { caseStatusStyle(it).label }) { selected ->
                    status = CaseStatus.entries.first { caseStatusStyle(it).label == selected }
                }
                DateTimeField("Created date", created, { created = it })
                val historyStatuses = requiredHistoryStatuses(status)
                if (historyStatuses.isNotEmpty()) {
                    Text("Known status history", style = MaterialTheme.typography.titleSmall)
                    historyStatuses.forEach { historyStatus ->
                        DateTimeField(
                            caseStatusStyle(historyStatus).label,
                            historyDates[historyStatus].orEmpty(),
                            { historyDates[historyStatus] = it }
                        )
                    }
                    Text("Use Pick for any dates you know. Unknown dates can stay blank.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                OutlinedTextField(assignee, { assignee = it }, label = { Text("Assignee") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(initial.copy(title = title.trim(), description = description, productAlignment = product.ifBlank { productOptions.firstOrNull().orEmpty() }, criticality = criticality, status = status, createdAtEpochMillis = parseDateInput(created) ?: initial.createdAtEpochMillis, assignee = assignee, notes = notes, statusHistory = encodeStatusHistory(historyDates)))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DateTimeField(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = parseDateInput(value) ?: System.currentTimeMillis() }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val picked = Calendar.getInstance().apply {
                                    set(year, month, day, hour, minute, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onChange(formatDateInput(picked.timeInMillis))
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) { Text("Pick") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = {
                    expanded = false
                    onChange(it)
                })
            }
        }
    }
}
