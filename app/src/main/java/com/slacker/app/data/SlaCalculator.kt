package com.slacker.app.data

import com.slacker.app.data.entities.CaseStatus
import com.slacker.app.data.entities.SeverityConfigEntity
import com.slacker.app.data.entities.SupportCaseEntity
import java.util.Calendar

const val SLA_PASSED = "PASSED"
const val SLA_BREACHED = "BREACHED"

/**
 * One of the four SLA stages of a support case, with everything the UI needs:
 * the computed due time, the stored pass/breach result (empty until the case
 * crosses the stage's gate status), and whether the gate has already been
 * passed (so legacy stages with no recorded timing aren't shown as "next").
 */
data class SlaStageState(
    val name: String,
    val dueAtEpochMillis: Long?,        // null while the anchor is unknown (RCA before Done)
    val result: String,                 // "", SLA_PASSED or SLA_BREACHED
    val evaluatedAtEpochMillis: Long?,
    val gateReached: Boolean
) {
    /** True when this stage's clock is still running — this is what the board card shows. */
    val isPending: Boolean get() = result.isEmpty() && !gateReached && dueAtEpochMillis != null
}

object SlaCalculator {

    private const val HOUR_MILLIS = 3_600_000L

    /**
     * Scores every SLA stage whose gate the case has crossed, using the saved
     * status history for the transition times, and stores the pass/breach
     * outcome on the case. Deterministic and idempotent: it can safely re-run
     * on every save (and once at startup to backfill pre-existing cases).
     *
     * Stage gates — reaching one of these statuses stops that SLA clock:
     *  - Initial Triage: Under Initial Review
     *  - Labs Review:    On Hold, Ready for Development or Pending Outside Labs
     *  - Final:          Done Ready to Deploy (also anchors the RCA clock)
     *  - RCA:            RCA Complete
     * Done No Code Changes settles every still-open stage at the moment of the
     * move. A later gate also settles any earlier stage that was skipped over.
     */
    fun reconcile(case: SupportCaseEntity, config: SeverityConfigEntity): SupportCaseEntity {
        val history = parseHistory(case.statusHistory)
        val noCodeAt = history[CaseStatus.DONE_NO_CODE_CHANGES]
        val doneAt = history[CaseStatus.DONE_READY_TO_DEPLOY]
            ?: case.movedToDoneAtEpochMillis
            ?: noCodeAt
        val labsAt = listOfNotNull(
            history[CaseStatus.ON_HOLD],
            history[CaseStatus.READY_FOR_DEVELOPMENT],
            history[CaseStatus.PENDING_OUTSIDE_LABS]
        ).minOrNull() ?: doneAt
        val triageAt = history[CaseStatus.UNDER_INITIAL_REVIEW] ?: labsAt
        val rcaAt = history[CaseStatus.RCA_COMPLETE] ?: noCodeAt

        val created = case.createdAtEpochMillis
        val triageDue = addBusinessHours(created, config.initialTriageHours)
        val labsDue = addBusinessHours(created, config.labsReviewHours)
        val finalDue = addBusinessHours(created, config.finalHours)
        val rcaDue = doneAt?.let { addBusinessHours(it, config.rcaHours) }

        fun eval(at: Long?, due: Long?, keepResult: String, keepAt: Long?): Pair<String, Long?> =
            if (at != null && due != null) {
                (if (at <= due) SLA_PASSED else SLA_BREACHED) to at
            } else {
                keepResult to keepAt
            }

        val (triageResult, triageEvalAt) = eval(triageAt, triageDue, case.triageResult, case.triageEvaluatedAtEpochMillis)
        val (labsResult, labsEvalAt) = eval(labsAt, labsDue, case.labsResult, case.labsEvaluatedAtEpochMillis)
        val (finalResult, finalEvalAt) = eval(doneAt, finalDue, case.finalResult, case.finalEvaluatedAtEpochMillis)
        val (rcaResult, rcaEvalAt) = eval(rcaAt, rcaDue, case.rcaResult, case.rcaEvaluatedAtEpochMillis)

        return case.copy(
            // History is the authority: an edited Done date re-anchors the RCA clock
            movedToDoneAtEpochMillis = doneAt ?: case.movedToDoneAtEpochMillis,
            triageResult = triageResult, triageEvaluatedAtEpochMillis = triageEvalAt,
            labsResult = labsResult, labsEvaluatedAtEpochMillis = labsEvalAt,
            finalResult = finalResult, finalEvaluatedAtEpochMillis = finalEvalAt,
            rcaResult = rcaResult, rcaEvaluatedAtEpochMillis = rcaEvalAt,
            triageDone = case.triageDone || triageResult.isNotEmpty(),
            labsReviewDone = case.labsReviewDone || labsResult.isNotEmpty(),
            finalDone = case.finalDone || finalResult.isNotEmpty(),
            rcaDone = case.rcaDone || rcaResult.isNotEmpty()
        )
    }

    /** All four stages in order — this backs the SLA history view. */
    fun stageStates(case: SupportCaseEntity, config: SeverityConfigEntity): List<SlaStageState> {
        val created = case.createdAtEpochMillis
        val ordinal = case.status.ordinal
        val settledEverything = case.status == CaseStatus.DONE_NO_CODE_CHANGES
        val rcaDue = case.movedToDoneAtEpochMillis?.let { addBusinessHours(it, config.rcaHours) }
        return listOf(
            SlaStageState(
                "Initial Triage", addBusinessHours(created, config.initialTriageHours),
                case.triageResult, case.triageEvaluatedAtEpochMillis,
                gateReached = case.status != CaseStatus.NEW
            ),
            SlaStageState(
                "Labs Review", addBusinessHours(created, config.labsReviewHours),
                case.labsResult, case.labsEvaluatedAtEpochMillis,
                gateReached = ordinal >= CaseStatus.ON_HOLD.ordinal
            ),
            SlaStageState(
                "Final", addBusinessHours(created, config.finalHours),
                case.finalResult, case.finalEvaluatedAtEpochMillis,
                gateReached = ordinal >= CaseStatus.DONE_READY_TO_DEPLOY.ordinal
            ),
            SlaStageState(
                "RCA", rcaDue,
                case.rcaResult, case.rcaEvaluatedAtEpochMillis,
                gateReached = case.status == CaseStatus.RCA_COMPLETE || settledEverything
            )
        )
    }

    /** The single stage whose clock is currently running — shown on the board card. */
    fun nextPending(case: SupportCaseEntity, config: SeverityConfigEntity): SlaStageState? =
        stageStates(case, config).firstOrNull { it.isPending }

    /** "STATUS:millis|STATUS:millis" -> earliest recorded time per status. */
    fun parseHistory(statusHistory: String): Map<CaseStatus, Long> =
        statusHistory.split("|")
            .mapNotNull { entry ->
                val status = runCatching { CaseStatus.valueOf(entry.substringBefore(":")) }.getOrNull()
                    ?: return@mapNotNull null
                val ts = entry.substringAfter(":", "").toLongOrNull() ?: return@mapNotNull null
                status to ts
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.min() }

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

        // A deadline landing exactly on a weekend boundary (midnight into
        // Saturday) rolls to Monday 00:00 — the weekend never counts.
        while (true) {
            val cal = Calendar.getInstance().apply { timeInMillis = current }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) break
            current = startOfNextDay(cal)
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
}
