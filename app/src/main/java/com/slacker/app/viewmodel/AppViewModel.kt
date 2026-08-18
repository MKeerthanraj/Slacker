package com.slacker.app.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slacker.app.data.AppDatabase
import com.slacker.app.data.entities.*
import com.slacker.app.groq.QuickAddParser
import com.slacker.app.groq.QuickAddResult
import com.slacker.app.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        saveTask(
            task.copy(
                status = status,
                completedAtEpochMillis = if (status == TaskStatus.DONE) System.currentTimeMillis() else null
            )
        )
    }

    fun updateCaseStatus(case: SupportCaseEntity, status: CaseStatus) = viewModelScope.launch {
        val stamp = "${status.name}:${System.currentTimeMillis()}"
        val history = listOf(case.statusHistory, stamp).filter { it.isNotBlank() }.joinToString("|")
        saveCase(
            case.copy(
                status = status,
                // This is the RCA SLA anchor - set the instant the case first reaches Done
                movedToDoneAtEpochMillis = if ((status == CaseStatus.DONE_READY_TO_DEPLOY || status == CaseStatus.DONE_NO_CODE_CHANGES) && case.movedToDoneAtEpochMillis == null)
                    System.currentTimeMillis() else case.movedToDoneAtEpochMillis,
                statusHistory = history
            )
        )
    }

    fun saveTask(task: TaskEntity) = viewModelScope.launch {
        if (task.id == 0L) repo.addTask(task) else repo.updateTask(task)
    }

    fun saveCase(case: SupportCaseEntity) = viewModelScope.launch {
        if (case.id == 0L) repo.addCase(case) else repo.updateCase(case)
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
