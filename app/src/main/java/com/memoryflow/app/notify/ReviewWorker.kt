package com.memoryflow.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memoryflow.app.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs periodically (15 min, the WorkManager minimum) and decides whether a question should
 * interrupt the user right now. All limits and quiet hours are enforced inside NotificationFlow.
 */
class ReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repo = Repository.get(applicationContext)
            val now = System.currentTimeMillis()
            val pending = repo.pendingSession()
            if (pending != null) {
                if (now - pending.createdAt < PENDING_TTL_MS) {
                    // A question is still waiting for the user; never stack a second one.
                    return@withContext Result.success()
                }
                repo.updateSession(pending.copy(answered = true, answeredAt = now))
                repo.closeSessions()
            }
            NotificationFlow(applicationContext).postProactive(now)
            Result.success()
        } catch (t: Throwable) {
            android.util.Log.e("MemoryFlow", "Review worker failed", t)
            Result.retry()
        }
    }

    companion object {
        const val PENDING_TTL_MS = 45 * 60 * 1000L
    }
}
