package com.slacker.app.data.dao

import androidx.room.*
import com.slacker.app.data.entities.SupportCaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupportCaseDao {
    @Query("SELECT * FROM support_cases ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<SupportCaseEntity>>

    @Query("SELECT * FROM support_cases WHERE id = :id")
    suspend fun getById(id: Long): SupportCaseEntity?

    @Insert
    suspend fun insert(supportCase: SupportCaseEntity): Long

    @Update
    suspend fun update(supportCase: SupportCaseEntity)

    @Delete
    suspend fun delete(supportCase: SupportCaseEntity)
}
