package com.memoryflow.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the scheduler after a reboot or an app update (WorkManager alone is not enough). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                try {
                    NotificationPresenter(context).ensureChannel()
                    SchedulerSetup.ensure(context)
                } catch (t: Throwable) {
                    android.util.Log.e("MemoryFlow", "Boot re-arm failed", t)
                }
            }
        }
    }
}
