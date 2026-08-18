package com.slacker.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.slacker.app.data.AppDatabase
import com.slacker.app.data.SlaCalculator
import com.slacker.app.data.entities.TaskStatus
import kotlinx.coroutines.flow.first

/**
 * Runs periodically (see NotificationScheduler) and checks:
 *  - Tasks: fires when now is within `remindBeforeMinutes` of the due time (and not done).
 *  - Support cases: fires when a checkpoint is within 2 hours of its SLA deadline, or overdue.
 *
 * NOTE: this is a simple polling approach (good enough for a personal tracker on the
 * free/manual-entry scale). It may re-notify on subsequent runs while an item stays
 * overdue — dedup by tracking "last notified" timestamps is a natural next improvement.
 */
class DeadlineCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val now = System.currentTimeMillis()

        // ---- Tasks ----
        val tasks = db.taskDao().observeAll().first()
        tasks.filter { it.status != TaskStatus.DONE && it.dueAtEpochMillis != null }
            .forEach { task ->
                val remindWindowMillis = task.remindBeforeMinutes * 60_000L
                val due = task.dueAtEpochMillis!!
                val isOverdue = now > due
                val isApproaching = !isOverdue && (due - now) <= remindWindowMillis

                if (isOverdue || isApproaching) {
                    val label = if (isOverdue) "OVERDUE" else "Due soon"
                    NotificationHelper.notify(
                        applicationContext,
                        id = "task_${task.id}".hashCode(),
                        title = "$label: ${task.title}",
                        text = if (isOverdue) "This task passed its deadline." else "Due within ${task.remindBeforeMinutes} minutes."
                    )
                }
            }

        // ---- Support case SLA checkpoints ----
        val cases = db.supportCaseDao().observeAll().first()
        val severityConfigs = db.severityConfigDao().observeAll().first().associateBy { it.severityLevel }

        cases.forEach { case ->
            val config = severityConfigs[case.severityLevel] ?: return@forEach
            val next = SlaCalculator.nextCheckpoint(case, config) ?: return@forEach

            val msUntilDue = next.dueAtEpochMillis - now
            val twoHoursMillis = 2 * 3_600_000L
            val isOverdue = msUntilDue < 0
            val isApproaching = !isOverdue && msUntilDue <= twoHoursMillis

            if (isOverdue || isApproaching) {
                val label = if (isOverdue) "SLA BREACHED" else "SLA due soon"
                NotificationHelper.notify(
                    applicationContext,
                    id = "case_${case.id}_${next.name}".hashCode(),
                    title = "$label: ${case.title} (Sev ${case.severityLevel})",
                    text = "${next.name} checkpoint" + if (isOverdue) " has been missed." else " due within 2 hours."
                )
            }
        }

        return Result.success()
    }
}
