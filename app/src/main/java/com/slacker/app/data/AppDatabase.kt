package com.slacker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.slacker.app.data.entities.CaseCriticality
import com.slacker.app.data.entities.CaseStatus
import com.slacker.app.data.dao.SeverityConfigDao
import com.slacker.app.data.dao.SupportCaseDao
import com.slacker.app.data.dao.TaskDao
import com.slacker.app.data.entities.DefaultSeverityConfigs
import com.slacker.app.data.entities.SeverityConfigEntity
import com.slacker.app.data.entities.SupportCaseEntity
import com.slacker.app.data.entities.TaskEntity
import com.slacker.app.data.entities.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppConverters {
    @TypeConverter
    fun fromCaseCriticality(value: CaseCriticality): String = value.name

    @TypeConverter
    fun toCaseCriticality(value: String): CaseCriticality =
        runCatching { CaseCriticality.valueOf(value) }.getOrDefault(CaseCriticality.NORMAL)

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus =
        runCatching { TaskStatus.valueOf(value) }.getOrDefault(TaskStatus.TODO)

    @TypeConverter
    fun fromCaseStatus(value: CaseStatus): String = value.name

    @TypeConverter
    fun toCaseStatus(value: String): CaseStatus = when (value) {
        "TRIAGED" -> CaseStatus.UNDER_INITIAL_REVIEW
        "IN_REVIEW" -> CaseStatus.UNDER_DEVELOPMENT
        "RESOLVED" -> CaseStatus.DONE_READY_TO_DEPLOY
        "DONE" -> CaseStatus.RCA_COMPLETE
        else -> runCatching { CaseStatus.valueOf(value) }.getOrDefault(CaseStatus.NEW)
    }
}

@Database(
    entities = [TaskEntity::class, SupportCaseEntity::class, SeverityConfigEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun supportCaseDao(): SupportCaseDao
    abstract fun severityConfigDao(): SeverityConfigDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN assignee TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatOption TEXT NOT NULL DEFAULT 'None'")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN productAlignment TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN criticality TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN nextStatusDueAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN assignee TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE support_cases ADD COLUMN statusHistory TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE support_cases SET status = 'UNDER_INITIAL_REVIEW' WHERE status = 'TRIAGED'")
                db.execSQL("UPDATE support_cases SET status = 'UNDER_DEVELOPMENT' WHERE status = 'IN_REVIEW'")
                db.execSQL("UPDATE support_cases SET status = 'DONE_READY_TO_DEPLOY' WHERE status = 'RESOLVED'")
                db.execSQL("UPDATE support_cases SET status = 'RCA_COMPLETE' WHERE status = 'DONE'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive only — existing rows keep all their data and get
                // scored from statusHistory by SlaCalculator.reconcile at startup.
                db.execSQL("ALTER TABLE support_cases ADD COLUMN triageResult TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN triageEvaluatedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN labsResult TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN labsEvaluatedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN finalResult TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN finalEvaluatedAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN rcaResult TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE support_cases ADD COLUMN rcaEvaluatedAtEpochMillis INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sla_tracker.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed the default Sev1-5 SLA scheme on first launch
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.severityConfigDao()?.insertAll(DefaultSeverityConfigs.seed)
                        }
                    }
                }).build().also { INSTANCE = it }
            }
    }
}
