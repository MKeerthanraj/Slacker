package com.slacker.app.data.dao

import androidx.room.*
import com.slacker.app.data.entities.SeverityConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeverityConfigDao {
    @Query("SELECT * FROM severity_config ORDER BY severityLevel ASC")
    fun observeAll(): Flow<List<SeverityConfigEntity>>

    @Query("SELECT * FROM severity_config WHERE severityLevel = :level")
    suspend fun getByLevel(level: Int): SeverityConfigEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(configs: List<SeverityConfigEntity>)

    @Update
    suspend fun update(config: SeverityConfigEntity)
}
