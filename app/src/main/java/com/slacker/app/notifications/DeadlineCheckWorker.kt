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
 * free/manual-entry scale). Each (item, deadline, state) fires exactly once — the
 * sent log lives in the "slacker_notify_log" SharedPreferences.
 */
class DeadlineCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val now = System.currentTimeMillis()
        val notifyLog = applicationContext.getSharedPreferences("slacker_notify_log", Context.MODE_PRIVATE)

        fun notifyOnce(key: String, id: Int, title: String, text: String) {
            if (notifyLog.getBoolean(key, false)) return
            NotificationHelper.notify(applicationContext, id, title, text)
            notifyLog.edit().putBoolean(key, true).apply()
        }

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
                    notifyOnce(
                        key = "task_${task.id}_${due}_$label",
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
            val next = SlaCalculator.nextPending(case, config) ?: return@forEach
            val nextDue = next.dueAtEpochMillis ?: return@forEach

            val msUntilDue = nextDue - now
            val twoHoursMillis = 2 * 3_600_000L
            val isOverdue = msUntilDue < 0
            val isApproaching = !isOverdue && msUntilDue <= twoHoursMillis

            if (isOverdue || isApproaching) {
                val label = if (isOverdue) "SLA BREACHED" else "SLA due soon"
                notifyOnce(
                    key = "case_${case.id}_${next.name}_${nextDue}_$label",
                    id = "case_${case.id}_${next.name}".hashCode(),
                    title = "$label: ${case.title} (Sev ${case.severityLevel})",
                    text = "${next.name} SLA" + if (isOverdue) " has been missed." else " due within 2 hours."
                )
            }
        }

        return Result.success()
    }
}
