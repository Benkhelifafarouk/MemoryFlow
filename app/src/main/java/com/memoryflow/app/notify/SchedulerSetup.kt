package com.memoryflow.app.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.memoryflow.app.data.Repository
import java.util.concurrent.TimeUnit

/** Registers the background scheduler. Called on app start, after boot and after an update. */
object SchedulerSetup {

    const val WORK_NAME = "memoryflow_review_loop"

    fun ensure(context: Context) {
        val repo = Repository.get(context)
        if (!repo.settings.notificationsEnabled || !repo.settings.learningMode) {
            cancel(context)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<ReviewWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Used by the "send a test question now" button. */
    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReviewWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "memoryflow_review_once",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
