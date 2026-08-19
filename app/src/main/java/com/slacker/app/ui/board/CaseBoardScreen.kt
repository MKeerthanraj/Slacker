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
import androidx.compose.material3.MenuAnchorType
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
import com.slacker.app.data.SlaCalculator
import com.slacker.app.data.entities.CaseStatus
import com.slacker.app.data.entities.SeverityConfigEntity
import com.slacker.app.data.entities.SupportCaseEntity
import com.slacker.app.ui.CaseEditorDialog
import com.slacker.app.ui.caseStatusStyle
import com.slacker.app.ui.criticalityStyle
import com.slacker.app.ui.formatDate
import com.slacker.app.ui.getDueCountdown
import com.slacker.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch

private val statuses = CaseStatus.entries

@Composable
fun CaseBoardScreen(viewModel: AppViewModel) {
    val cases by viewModel.cases.collectAsState()
    val configs by viewModel.severityConfigs.collectAsState()
    val configByLevel = remember(configs) { configs.associateBy { it.severityLevel } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SupportCaseEntity?>(null) }
    val defaultSeverity = configs.firstOrNull()?.severityLevel ?: 3
    var selectedJump by remember { mutableStateOf(statuses.first()) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Support Cases", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { editing = SupportCaseEntity(title = "", severityLevel = defaultSeverity) }) { Text("Add Case") }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusJumpMenu(
                value = selectedJump,
                statuses = statuses,
                countFor = { status -> cases.count { it.status == status } },
                labelFor = { status -> caseStatusStyle(status).let { "${it.emoji} ${it.label}" } },
                onSelect = { status ->
                    selectedJump = status
                    scope.launch {
                        var targetIndex = 0
                        for (s in statuses) {
                            if (s == status) break
                            targetIndex++
                            if (!viewModel.collapsedSections.value.contains("case_${s.name}")) {
                                val count = cases.count { it.status == s }
                                targetIndex += if (count == 0) 1 else count
                            }
                        }
                        listState.animateScrollToItem(targetIndex)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text("${cases.size} total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            statuses.forEach { status ->
                val sectionKey = "case_${status.name}"
                val isCollapsed = viewModel.collapsedSections.value.contains(sectionKey)
                val sectionCases = cases.filter { it.status == status }.sortedBy { case ->
                    configByLevel[case.severityLevel]?.let { SlaCalculator.nextPending(case, it)?.dueAtEpochMillis }
                        ?: Long.MAX_VALUE
                }

                item(key = status.name) {
                    val style = caseStatusStyle(status)
                    StatusSectionHeader(
                        label = style.label,
                        emoji = style.emoji,
                        color = style.color,
                        count = sectionCases.size,
                        isCollapsed = isCollapsed,
                        onToggle = { viewModel.toggleSection(sectionKey) }
                    )
                }
                if (!isCollapsed) {
                    if (sectionCases.isEmpty()) {
                        item(key = "${status.name}-empty") { EmptyStatusCard() }
                    } else {
                        items(sectionCases, key = { it.id }) { supportCase ->
                            CaseCard(
                                case = supportCase,
                                config = configByLevel[supportCase.severityLevel],
                                onOpen = { editing = supportCase },
                                onStatus = { viewModel.updateCaseStatus(supportCase, it) }
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { supportCase ->
        CaseEditorDialog(
            initial = supportCase,
            productOptions = viewModel.productAlignments.value,
            severityConfigs = configs,
            config = configByLevel[supportCase.severityLevel],
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveCase(it)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusJumpMenu(
    value: CaseStatus,
    statuses: List<CaseStatus>,
    countFor: (CaseStatus) -> Int,
    labelFor: (CaseStatus) -> String,
    onSelect: (CaseStatus) -> Unit,
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
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
private fun CaseCard(case: SupportCaseEntity, config: SeverityConfigEntity?, onOpen: () -> Unit, onStatus: (CaseStatus) -> Unit) {
    val next = config?.let { SlaCalculator.nextPending(case, it) }
    val displayDue = next?.dueAtEpochMillis
    val overdue = displayDue != null && displayDue < System.currentTimeMillis()
    val statusStyle = caseStatusStyle(case.status)
    val criticality = criticalityStyle(case.criticality)
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
                    Text(case.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(statusStyle.emoji)
                }
                if (case.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(case.description, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(config?.label ?: "Sev ${case.severityLevel}") })
                    AssistChip(onClick = {}, label = { Text("${criticality.emoji} ${criticality.label}") })
                    AssistChip(onClick = { menu = true }, label = { Text(statusStyle.label) })
                }
                if (next != null && displayDue != null) {
                    val due = displayDue
                    val countdown = getDueCountdown(due)
                    val dateText = "${next.name} SLA " + (if (overdue) "breached — was due " else "due ") + formatDate(due)
                    val text = if (countdown != null) "$dateText ($countdown)" else dateText
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overdue || countdown != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                if (case.productAlignment.isNotBlank() || case.assignee.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            case.productAlignment.takeIf { it.isNotBlank() }?.let { "Product: $it" },
                            case.assignee.takeIf { it.isNotBlank() }?.let { "Assignee: $it" }
                        ).joinToString("  |  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            statuses.forEach { status ->
                val nextStyle = caseStatusStyle(status)
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