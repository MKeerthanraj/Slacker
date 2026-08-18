package com.slacker.app.data

import com.slacker.app.data.entities.SeverityConfigEntity
import com.slacker.app.data.entities.SupportCaseEntity
import com.slacker.app.data.entities.CaseStatus
import java.util.Calendar

data class SlaCheckpoint(
    val name: String,
    val dueAtEpochMillis: Long,
    val isDone: Boolean,
    val isOverdue: Boolean
)

object SlaCalculator {

    private const val HOUR_MILLIS = 3_600_000L

    /**
     * Returns every checkpoint for a case with its computed due timestamp,
     * given the matching severity config. RCA is only computed once the case
     * has actually moved to Done (that's its anchor).
     *
     * Weekends (Saturday & Sunday) don't count toward any SLA clock — the due
     * date calculation skips straight over them.
     */
    fun checkpointsFor(case: SupportCaseEntity, config: SeverityConfigEntity): List<SlaCheckpoint> {
        val now = System.currentTimeMillis()
        val created = case.createdAtEpochMillis

        val result = mutableListOf(
            build(
                "Initial Triage",
                addBusinessHours(created, config.initialTriageHours),
                case.triageDone || case.status != CaseStatus.NEW, now
            ),
            build(
                "Labs Review",
                addBusinessHours(created, config.labsReviewHours),
                case.labsReviewDone || case.status.ordinal >= CaseStatus.UNDER_DEVELOPMENT.ordinal || case.status == CaseStatus.DONE_NO_CODE_CHANGES, now
            ),
            build(
                "Final SLA",
                addBusinessHours(created, config.finalHours),
                case.finalDone || case.status == CaseStatus.RCA_COMPLETE || case.status == CaseStatus.DONE_NO_CODE_CHANGES, now
            )
        )

        // RCA only starts counting once the case has actually moved to Done
        case.movedToDoneAtEpochMillis?.let { doneAt ->
            result.add(
                build(
                    "RCA",
                    addBusinessHours(doneAt, config.rcaHours),
                    case.rcaDone, now
                )
            )
        }

        return result
    }

    /**
     * Adds `hours` of "business time" to `startMillis`, treating all of Saturday
     * and Sunday as non-counting. Walks day-by-day so partial days at the start
     * and end are handled correctly.
     *
     * A 0-hour SLA ("Immediately") is left exactly at the start time, weekend or
     * not — it means "the instant this is created," not "the next business hour."
     */
    fun addBusinessHours(startMillis: Long, hours: Double): Long {
        if (hours <= 0.0) return startMillis

        var remainingMillis = (hours * HOUR_MILLIS).toLong()
        var current = startMillis

        while (remainingMillis > 0) {
            val cal = Calendar.getInstance().apply { timeInMillis = current }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                // Jump straight to the start of the next day; no time consumed.
                current = startOfNextDay(cal)
                continue
            }

            val millisUntilMidnight = startOfNextDay(cal) - current
            val consumed = minOf(remainingMillis, millisUntilMidnight)
            current += consumed
            remainingMillis -= consumed
            // Loop re-checks: if we landed exactly at midnight with time left,
            // the next iteration evaluates whether that new day is a weekend day.
        }

        return current
    }

    private fun startOfNextDay(cal: Calendar): Long {
        val next = cal.clone() as Calendar
        next.add(Calendar.DAY_OF_YEAR, 1)
        next.set(Calendar.HOUR_OF_DAY, 0)
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
        return next.timeInMillis
    }

    private fun build(name: String, dueAt: Long, done: Boolean, now: Long) =
        SlaCheckpoint(
            name = name,
            dueAtEpochMillis = dueAt,
            isDone = done,
            isOverdue = !done && now > dueAt
        )

    /** The single "next thing due" for a case — used for board card badges & sorting. */
    fun nextCheckpoint(case: SupportCaseEntity, config: SeverityConfigEntity): SlaCheckpoint? =
        checkpointsFor(case, config)
            .filter { !it.isDone }
            .minByOrNull { it.dueAtEpochMillis }
}
