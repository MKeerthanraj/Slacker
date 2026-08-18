package com.slacker.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CaseStatus {
    NEW,
    UNDER_INITIAL_REVIEW,
    ON_HOLD,
    READY_FOR_DEVELOPMENT,
    PENDING_OUTSIDE_LABS,
    UNDER_DEVELOPMENT,
    READY_FOR_QA,
    IN_TEST,
    READY_FOR_DEMO,
    DONE_READY_TO_DEPLOY,
    CLOSED,
    RCA_IN_PROGRESS,
    RCA_IN_REVIEW,
    RCA_COMPLETE,
    DONE_NO_CODE_CHANGES
}

enum class CaseCriticality {
    CRITICAL, MAJOR, NORMAL, LOW
}

@Entity(tableName = "support_cases")
data class SupportCaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseNumber: String = "",           // your own ticket/case ID, optional
    val title: String,
    val description: String = "",
    val productAlignment: String = "",
    val criticality: CaseCriticality = CaseCriticality.NORMAL,
    val assignee: String = "",
    val notes: String = "",
    val severityLevel: Int,                // FK -> SeverityConfigEntity.severityLevel
    val status: CaseStatus = CaseStatus.NEW,

    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val movedToDoneAtEpochMillis: Long? = null,   // sets the RCA SLA anchor when filled

    // Whether each checkpoint has actually been completed (stops further reminders once true)
    val triageDone: Boolean = false,
    val labsReviewDone: Boolean = false,
    val finalDone: Boolean = false,
    val rcaDone: Boolean = false,
    val statusHistory: String = "",

    // Stored SLA outcomes — written the moment the case crosses each stage's
    // gate status (see SlaCalculator.reconcile). Empty string = not scored yet.
    val triageResult: String = "",          // "", "PASSED" or "BREACHED"
    val triageEvaluatedAtEpochMillis: Long? = null,
    val labsResult: String = "",
    val labsEvaluatedAtEpochMillis: Long? = null,
    val finalResult: String = "",
    val finalEvaluatedAtEpochMillis: Long? = null,
    val rcaResult: String = "",
    val rcaEvaluatedAtEpochMillis: Long? = null
)
