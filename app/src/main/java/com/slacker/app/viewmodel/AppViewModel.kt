package com.slacker.app.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slacker.app.data.AppDatabase
import com.slacker.app.data.SlaCalculator
import com.slacker.app.data.entities.*
import com.slacker.app.groq.QuickAddParser
import com.slacker.app.groq.QuickAddResult
import com.slacker.app.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository(AppDatabase.getInstance(application))
    private val prefs = application.getSharedPreferences("slacker_settings", 0)

    val tasks = repo.observeTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cases = repo.observeCases().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val severityConfigs = repo.observeSeverityConfigs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val productAlignments = mutableStateOf(
        prefs.getString("product_alignments", "Core Platform,Mobile App,API,Integrations")!!
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    )
    val collapsedSections = mutableStateOf(
        prefs.getStringSet("collapsed_sections", emptySet()) ?: emptySet()
    )

    init {
        // One-time reconcile pass so cases saved before SLA results were stored
        // get scored from their status history. Idempotent — after the first
        // pass reconcile returns the case unchanged and nothing is rewritten.
        viewModelScope.launch {
            val configs = repo.observeSeverityConfigs().first().associateBy { it.severityLevel }
            if (configs.isNotEmpty()) {
                repo.observeCases().first().forEach { case ->
                    configs[case.severityLevel]?.let { config ->
                        val reconciled = SlaCalculator.reconcile(case, config)
                        if (reconciled != case) repo.updateCase(reconciled)
                    }
                }
            }
        }
    }

    fun toggleSection(sectionKey: String) {
        val current = collapsedSections.value.toMutableSet()
        if (current.contains(sectionKey)) {
            current.remove(sectionKey)
        } else {
            current.add(sectionKey)
        }
        collapsedSections.value = current
        prefs.edit().putStringSet("collapsed_sections", current).apply()
    }

    fun updateTaskStatus(task: TaskEntity, status: TaskStatus) = viewModelScope.launch {
        val updated = task.copy(
            status = status,
            completedAtEpochMillis = if (status == TaskStatus.DONE) System.currentTimeMillis() else null
        )
        if (updated.id == 0L) repo.addTask(updated) else repo.updateTask(updated)
        // A repeating task spawns its next occurrence the first time it's completed
        if (status == TaskStatus.DONE && task.status != TaskStatus.DONE) {
            nextRepeatDue(task)?.let { nextDue ->
                repo.addTask(
                    task.copy(
                        id = 0,
                        status = TaskStatus.TODO,
                        dueAtEpochMillis = nextDue,
                        createdAtEpochMillis = System.currentTimeMillis(),
                        completedAtEpochMillis = null
                    )
                )
            }
        }
    }

    /**
     * Next due time for a repeating task, always in the future — completing a
     * task late skips the occurrences that were missed in between.
     */
    private fun nextRepeatDue(task: TaskEntity): Long? {
        val due = task.dueAtEpochMillis ?: return null
        val base = task.repeatOption.substringBefore(":")
        val detail = task.repeatOption.substringAfter(":", "")
        if (base != "Daily" && base != "Weekly" && base != "Monthly") return null

        val weekdays = mapOf(
            "Sunday" to Calendar.SUNDAY, "Monday" to Calendar.MONDAY,
            "Tuesday" to Calendar.TUESDAY, "Wednesday" to Calendar.WEDNESDAY,
            "Thursday" to Calendar.THURSDAY, "Friday" to Calendar.FRIDAY,
            "Saturday" to Calendar.SATURDAY
        )
        val cal = Calendar.getInstance().apply { timeInMillis = due }
        fun advance() {
            when (base) {
                "Daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "Weekly" -> {
                    val target = weekdays[detail] ?: cal.get(Calendar.DAY_OF_WEEK)
                    do cal.add(Calendar.DAY_OF_YEAR, 1) while (cal.get(Calendar.DAY_OF_WEEK) != target)
                }
                "Monthly" -> {
                    val dayOfMonth = detail.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
            }
        }
        val now = System.currentTimeMillis()
        do advance() while (cal.timeInMillis <= now)
        return cal.timeInMillis
    }

    fun updateCaseStatus(case: SupportCaseEntity, status: CaseStatus) = viewModelScope.launch {
        if (case.status == status) return@launch
        val stamp = "${status.name}:${System.currentTimeMillis()}"
        val history = listOf(case.statusHistory, stamp).filter { it.isNotBlank() }.joinToString("|")
        persistCase(case.copy(status = status, statusHistory = history))
    }

    fun saveTask(task: TaskEntity) = viewModelScope.launch {
        if (task.id == 0L) repo.addTask(task) else repo.updateTask(task)
    }

    fun saveCase(case: SupportCaseEntity) = viewModelScope.launch {
        var toSave = case
        // If the status changed (via the editor or on creation) without an explicit
        // history date for it, record the transition moment now — that timestamp is
        // what the SLA pass/breach evaluation is scored against.
        val statusChanged = if (case.id == 0L) case.status != CaseStatus.NEW
        else repo.getCaseById(case.id)?.status != case.status
        if (statusChanged && !SlaCalculator.parseHistory(case.statusHistory).containsKey(case.status)) {
            val stamp = "${case.status.name}:${System.currentTimeMillis()}"
            toSave = case.copy(
                statusHistory = listOf(case.statusHistory, stamp).filter { it.isNotBlank() }.joinToString("|")
            )
        }
        persistCase(toSave)
    }

    private suspend fun persistCase(case: SupportCaseEntity) {
        val config = repo.getSeverityConfig(case.severityLevel)
        val reconciled = config?.let { SlaCalculator.reconcile(case, it) } ?: case
        if (reconciled.id == 0L) repo.addCase(reconciled) else repo.updateCase(reconciled)
    }

    suspend fun quickAdd(text: String): QuickAddResult = QuickAddParser.parse(text)

    fun saveSeverityConfig(config: SeverityConfigEntity) = viewModelScope.launch {
        repo.updateSeverityConfig(config)
    }

    fun saveProductAlignments(values: List<String>) {
        val cleaned = values.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        productAlignments.value = cleaned
        prefs.edit().putString("product_alignments", cleaned.joinToString(",")).apply()
    }
}
