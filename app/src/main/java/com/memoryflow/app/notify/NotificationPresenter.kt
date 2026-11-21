package com.memoryflow.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.memoryflow.app.MainActivity
import com.memoryflow.app.R

/** Builds the interactive review notification (inline reply + hints). */
class NotificationPresenter(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return manager.areNotificationsEnabled()
    }

    fun showQuestion(
        deckName: String,
        question: String,
        hint: String?,
        hintsUsed: Int,
        sessionId: Long,
        feedbackLine: String? = null,
        feedbackPositive: Boolean = true,
        progressLine: String? = null
    ) {
        ensureChannel()
        val body = StringBuilder()
        feedbackLine?.let { body.append(it).append("\n\n") }
        body.append(question)
        hint?.takeIf { it.isNotBlank() }?.let { body.append("\n\n💡 ").append(it) }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_memory)
            .setContentTitle(context.getString(R.string.notif_title, deckName))
            .setContentText(question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.toString()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(openIntent(sessionId))
            .addAction(replyAction(sessionId))
            .addAction(hintAction(sessionId, hintsUsed))
            .addAction(dontKnowAction(sessionId))
            .addAction(openAction(sessionId))

        if (progressLine != null) builder.setSubText(progressLine)
        safeNotify(NOTIFICATION_ID, builder.build())
    }

    fun showDone(title: String, text: String) {
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_memory)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent(null))
        safeNotify(NOTIFICATION_ID, builder.build())
    }

    fun showTemporary(title: String, text: String) {
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_memory)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent(null))
        safeNotify(TEMP_NOTIFICATION_ID, builder.build())
    }

    /**
     * Posting a notification without POST_NOTIFICATIONS throws on some Android versions and is
     * silently dropped on others, so every path goes through this guard.
     */
    private fun safeNotify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (t: Throwable) {
            android.util.Log.w("MemoryFlow", "Notification could not be posted", t)
        }
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    // ---- Pending intents -------------------------------------------------

    private fun replyAction(sessionId: Long): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(context.getString(R.string.notif_reply_label))
            .build()
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_SESSION, sessionId)
        }
        // RemoteInput needs a MUTABLE PendingIntent so the system can attach the typed text.
        val pi = PendingIntent.getBroadcast(
            context,
            (sessionId * 10 + 1).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_memory, context.getString(R.string.notif_action_reply), pi)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun hintAction(sessionId: Long, hintsUsed: Int): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_HINT
            putExtra(NotificationActionReceiver.EXTRA_SESSION, sessionId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (sessionId * 10 + 2).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = context.getString(R.string.notif_action_hint, hintsUsed + 1)
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_memory, label, pi).build()
    }

    /** "I don't know": reveals the answer, fails the card and marks it weak. */
    private fun dontKnowAction(sessionId: Long): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DONT_KNOW
            putExtra(NotificationActionReceiver.EXTRA_SESSION, sessionId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (sessionId * 10 + 3).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_memory,
            context.getString(R.string.review_dont_know),
            pi
        ).build()
    }

    private fun openAction(sessionId: Long?): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_memory,
            context.getString(R.string.notif_action_open),
            openIntent(sessionId)
        ).build()
    }

    private fun openIntent(sessionId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_REVIEW, true)
            if (sessionId != null) putExtra(MainActivity.EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getActivity(
            context,
            7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "memory_flow_reviews"
        const val NOTIFICATION_ID = 4201
        const val TEMP_NOTIFICATION_ID = 4202
        const val KEY_TEXT_REPLY = "memoryflow_reply"
    }
}
