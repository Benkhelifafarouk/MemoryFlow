package com.memoryflow.app.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/** All user preferences. Backed by SharedPreferences so they survive app restarts and updates. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("memoryflow_settings", Context.MODE_PRIVATE)

    // ---- Master switches -------------------------------------------------
    var learningMode: Boolean
        get() = prefs.getBoolean(KEY_LEARNING_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_LEARNING_MODE, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var scheduledEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULED, true)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULED, value).apply()

    var randomEnabled: Boolean
        get() = prefs.getBoolean(KEY_RANDOM, false)
        set(value) = prefs.edit().putBoolean(KEY_RANDOM, value).apply()

    // ---- Limits ----------------------------------------------------------
    var dailyNotificationLimit: Int
        get() = prefs.getInt(KEY_DAILY_NOTIF_LIMIT, 12)
        set(value) = prefs.edit().putInt(KEY_DAILY_NOTIF_LIMIT, value.coerceIn(1, 100)).apply()

    var maxQuestionsPerDay: Int
        get() = prefs.getInt(KEY_MAX_QUESTIONS, 30)
        set(value) = prefs.edit().putInt(KEY_MAX_QUESTIONS, value.coerceIn(1, 300)).apply()

    var newPerDayGlobal: Int
        get() = prefs.getInt(KEY_NEW_PER_DAY, 10)
        set(value) = prefs.edit().putInt(KEY_NEW_PER_DAY, value.coerceIn(0, 200)).apply()

    // ---- Time windows ----------------------------------------------------
    var quietStartHour: Int
        get() = prefs.getInt(KEY_QUIET_START, 22)
        set(value) = prefs.edit().putInt(KEY_QUIET_START, value.coerceIn(0, 23)).apply()

    var quietEndHour: Int
        get() = prefs.getInt(KEY_QUIET_END, 7)
        set(value) = prefs.edit().putInt(KEY_QUIET_END, value.coerceIn(0, 23)).apply()

    var windowStartHour: Int
        get() = prefs.getInt(KEY_WINDOW_START, 8)
        set(value) = prefs.edit().putInt(KEY_WINDOW_START, value.coerceIn(0, 23)).apply()

    var windowEndHour: Int
        get() = prefs.getInt(KEY_WINDOW_END, 21)
        set(value) = prefs.edit().putInt(KEY_WINDOW_END, value.coerceIn(0, 23)).apply()

    // ---- Learning --------------------------------------------------------
    var desiredRetention: Float
        get() = prefs.getFloat(KEY_RETENTION, 0.90f)
        set(value) = prefs.edit().putFloat(KEY_RETENTION, value.coerceIn(0.70f, 0.97f)).apply()

    /** 0 = calm, 1 = normal, 2 = intense */
    var frequency: Int
        get() = prefs.getInt(KEY_FREQUENCY, 1)
        set(value) = prefs.edit().putInt(KEY_FREQUENCY, value.coerceIn(0, 2)).apply()

    // ---- Appearance / language ------------------------------------------
    var themeMode: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ar") ?: "ar"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    // ---- Bookkeeping -----------------------------------------------------
    var lastNotificationAt: Long
        get() = prefs.getLong(KEY_LAST_NOTIF, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_NOTIF, value).apply()

    var lastQueueBuildAt: Long
        get() = prefs.getLong(KEY_LAST_QUEUE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_QUEUE, value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    fun hourIn(now: Calendar = Calendar.getInstance()): Int = now.get(Calendar.HOUR_OF_DAY)

    /** True when [now] falls inside quiet hours (supports windows crossing midnight). */
    fun isQuietNow(now: Calendar = Calendar.getInstance()): Boolean = hourWithin(now, quietStartHour, quietEndHour)

    /** True when [now] falls inside the allowed notification window. */
    fun isInWindowNow(now: Calendar = Calendar.getInstance()): Boolean =
        hourWithin(now, windowStartHour, windowEndHour)

    private fun hourWithin(now: Calendar, start: Int, end: Int): Boolean {
        val h = now.get(Calendar.HOUR_OF_DAY)
        if (start == end) return false
        return if (start < end) h in start until end else h >= start || h < end
    }

    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        learningMode = learningMode,
        notificationsEnabled = notificationsEnabled,
        scheduledEnabled = scheduledEnabled,
        randomEnabled = randomEnabled,
        dailyNotificationLimit = dailyNotificationLimit,
        maxQuestionsPerDay = maxQuestionsPerDay,
        newPerDayGlobal = newPerDayGlobal,
        quietStartHour = quietStartHour,
        quietEndHour = quietEndHour,
        windowStartHour = windowStartHour,
        windowEndHour = windowEndHour,
        desiredRetention = desiredRetention,
        frequency = frequency,
        themeMode = themeMode,
        language = language,
        soundEnabled = soundEnabled
    )

    companion object {
        private const val KEY_LEARNING_MODE = "learning_mode"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_SCHEDULED = "scheduled_enabled"
        private const val KEY_RANDOM = "random_enabled"
        private const val KEY_DAILY_NOTIF_LIMIT = "daily_notification_limit"
        private const val KEY_MAX_QUESTIONS = "max_questions_per_day"
        private const val KEY_NEW_PER_DAY = "new_per_day_global"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        private const val KEY_WINDOW_START = "window_start"
        private const val KEY_WINDOW_END = "window_end"
        private const val KEY_RETENTION = "desired_retention"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_LAST_NOTIF = "last_notification_at"
        private const val KEY_LAST_QUEUE = "last_queue_at"
        private const val KEY_ONBOARDING = "onboarding_done"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}

data class SettingsSnapshot(
    val learningMode: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val scheduledEnabled: Boolean = true,
    val randomEnabled: Boolean = false,
    val dailyNotificationLimit: Int = 12,
    val maxQuestionsPerDay: Int = 30,
    val newPerDayGlobal: Int = 10,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val windowStartHour: Int = 8,
    val windowEndHour: Int = 21,
    val desiredRetention: Float = 0.90f,
    val frequency: Int = 1,
    val themeMode: String = SettingsStore.THEME_SYSTEM,
    val language: String = "ar",
    val soundEnabled: Boolean = false
)
