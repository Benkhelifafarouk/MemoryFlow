package com.memoryflow.app

import android.app.Application
import com.memoryflow.app.data.SettingsStore
import com.memoryflow.app.notify.NotificationPresenter
import com.memoryflow.app.notify.SchedulerSetup
import com.memoryflow.app.util.LocaleHelper

class MemoryFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val context = LocaleHelper.wrap(this, SettingsStore(this).language)
        NotificationPresenter(context).ensureChannel()
        SchedulerSetup.ensure(this)
    }
}
