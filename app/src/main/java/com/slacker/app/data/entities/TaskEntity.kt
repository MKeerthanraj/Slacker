package com.slacker.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus {
    TODO, IN_PROGRESS, BLOCKED, DONE
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueAtEpochMillis: Long?,          // nullable: some tasks may have no hard deadline
    val assignee: String = "",
    val notes: String = "",
    val repeatOption: String = "None",
    val status: TaskStatus = TaskStatus.TODO,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val completedAtEpochMillis: Long? = null,
    val remindBeforeMinutes: Int = 60      // default: notify 1 hour before due
)
