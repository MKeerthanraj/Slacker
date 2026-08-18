package com.slacker.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per severity level. All SLA windows are stored in HOURS so
 * fractional-day rules ("immediately" = 0h) work cleanly.
 *
 * Anchors (what each SLA is measured from):
 *  - initialTriageHours, labsReviewHours, finalHours  -> anchored to case creation time
 *  - rcaHours                                         -> anchored to the time the case
 *                                                         status moved to DONE
 *
 * This table is fully editable from the Settings screen, and ships pre-seeded
 * with the scheme you gave me (Sev 1-5).
 */
@Entity(tableName = "severity_config")
data class SeverityConfigEntity(
    @PrimaryKey val severityLevel: Int,     // 1 = Sev1 ... 5 = Sev5
    val label: String,                      // "Sev 1", or rename e.g. "Critical"
    val initialTriageHours: Double,
    val labsReviewHours: Double,
    val finalHours: Double,
    val rcaHours: Double
)

/** Convenience seed data matching the scheme you provided. */
object DefaultSeverityConfigs {
    val seed = listOf(
        SeverityConfigEntity(1, "Sev 1", 0.0, 0.0, 24.0, 168.0),
        SeverityConfigEntity(2, "Sev 2", 24.0, 24.0, 48.0, 168.0),
        SeverityConfigEntity(3, "Sev 3", 48.0, 168.0, 240.0, 504.0),
        SeverityConfigEntity(4, "Sev 4", 48.0, 168.0, 2160.0, 672.0),
        SeverityConfigEntity(5, "Sev 5", 48.0, 168.0, 4320.0, 672.0)
    )
}
