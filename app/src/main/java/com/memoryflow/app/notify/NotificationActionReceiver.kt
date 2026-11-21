package com.memoryflow.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the inline reply and the hint action from the notification. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION, -1L)
        if (sessionId <= 0L) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val action = intent.action
        val reply = if (action == ACTION_REPLY) {
            RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(NotificationPresenter.KEY_TEXT_REPLY)
                ?.toString()
                .orEmpty()
        } else {
            null
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val flow = NotificationFlow(appContext)
                when (action) {
                    ACTION_REPLY -> flow.onReply(sessionId, reply.orEmpty())
                    ACTION_HINT -> flow.onHint(sessionId)
                    ACTION_DONT_KNOW -> flow.onDontKnow(sessionId)
                }
            } catch (t: Throwable) {
                android.util.Log.e("MemoryFlow", "Notification action failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.memoryflow.app.ACTION_REPLY"
        const val ACTION_HINT = "com.memoryflow.app.ACTION_HINT"
        const val ACTION_DONT_KNOW = "com.memoryflow.app.ACTION_DONT_KNOW"
        const val EXTRA_SESSION = "session_id"
    }
}
