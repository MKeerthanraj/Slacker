package com.slacker.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slacker.app.data.entities.SeverityConfigEntity
import com.slacker.app.viewmodel.AppViewModel

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val configs by viewModel.severityConfigs.collectAsState()
    var productsText by remember(viewModel.productAlignments.value) {
        mutableStateOf(viewModel.productAlignments.value.joinToString(", "))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("SLA Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Edit the hours for each SLA checkpoint per severity. All values are in hours " +
                "(24 = 1 day, 168 = 1 week). \"Immediate\" = 0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Product Alignment", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = productsText,
                    onValueChange = { productsText = it },
                    label = { Text("Products") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Button(onClick = { viewModel.saveProductAlignments(productsText.split(",")) }) {
                    Text("Save Products")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(configs, key = { it.severityLevel }) { config ->
                SeverityConfigCard(config = config, onSave = { viewModel.saveSeverityConfig(it) })
            }
        }
    }
}

@Composable
private fun SeverityConfigCard(config: SeverityConfigEntity, onSave: (SeverityConfigEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var label by remember(config) { mutableStateOf(config.label) }
    var triage by remember(config) { mutableStateOf(config.initialTriageHours.toString()) }
    var labs by remember(config) { mutableStateOf(config.labsReviewHours.toString()) }
    var final by remember(config) { mutableStateOf(config.finalHours.toString()) }
    var rca by remember(config) { mutableStateOf(config.rcaHours.toString()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(label, fontWeight = FontWeight.Bold)
                    Text(
                        "Triage ${triage}h  |  Labs ${labs}h  |  Final ${final}h  |  RCA ${rca}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                HourField("Initial Triage SLA (hrs)", triage) { triage = it }
                Spacer(Modifier.height(8.dp))
                HourField("Labs Review SLA (hrs)", labs) { labs = it }
                Spacer(Modifier.height(8.dp))
                HourField("Final SLA (hrs)", final) { final = it }
                Spacer(Modifier.height(8.dp))
                HourField("RCA SLA (hrs, from Done)", rca) { rca = it }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onSave(
                            config.copy(
                                label = label,
                                initialTriageHours = triage.toDoubleOrNull() ?: config.initialTriageHours,
                                labsReviewHours = labs.toDoubleOrNull() ?: config.labsReviewHours,
                                finalHours = final.toDoubleOrNull() ?: config.finalHours,
                                rcaHours = rca.toDoubleOrNull() ?: config.rcaHours
                            )
                        )
                    }
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun HourField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
