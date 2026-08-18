package com.slacker.app.repository

import com.slacker.app.data.AppDatabase
import com.slacker.app.data.entities.SeverityConfigEntity
import com.slacker.app.data.entities.SupportCaseEntity
import com.slacker.app.data.entities.TaskEntity

class AppRepository(private val db: AppDatabase) {

    // Tasks
    fun observeTasks() = db.taskDao().observeAll()
    suspend fun addTask(task: TaskEntity) = db.taskDao().insert(task)
    suspend fun updateTask(task: TaskEntity) = db.taskDao().update(task)
    suspend fun deleteTask(task: TaskEntity) = db.taskDao().delete(task)

    // Support cases
    fun observeCases() = db.supportCaseDao().observeAll()
    suspend fun addCase(case: SupportCaseEntity) = db.supportCaseDao().insert(case)
    suspend fun updateCase(case: SupportCaseEntity) = db.supportCaseDao().update(case)
    suspend fun deleteCase(case: SupportCaseEntity) = db.supportCaseDao().delete(case)
    suspend fun getCaseById(id: Long) = db.supportCaseDao().getById(id)

    // Severity config
    fun observeSeverityConfigs() = db.severityConfigDao().observeAll()
    suspend fun getSeverityConfig(level: Int) = db.severityConfigDao().getByLevel(level)
    suspend fun updateSeverityConfig(config: SeverityConfigEntity) = db.severityConfigDao().update(config)
}
