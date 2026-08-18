package com.slacker.app.data.dao

import androidx.room.*
import com.slacker.app.data.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // Adding a comment to force re-generation of the DAO implementation
    @Query("SELECT * FROM tasks ORDER BY dueAtEpochMillis ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}
