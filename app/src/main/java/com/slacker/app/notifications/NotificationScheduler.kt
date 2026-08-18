package com.slacker.app.notifications

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_NAME = "sla_tracker_deadline_check"
    private const val CHECK_INTERVAL_MINUTES = 15L

    /** 15 minutes is the shortest interval Android's WorkManager allows for periodic work. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DeadlineCheckWorker>(CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
