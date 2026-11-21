package com.memoryflow.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memoryflow.app.data.SettingsStore
import com.memoryflow.app.notify.NotificationPresenter
import com.memoryflow.app.notify.SchedulerSetup
import com.memoryflow.app.ui.AppRoot
import com.memoryflow.app.ui.AppViewModel
import com.memoryflow.app.ui.theme.MemoryFlowTheme
import com.memoryflow.app.util.LocaleHelper

class MainActivity : ComponentActivity() {

    private val reviewSignal = mutableIntStateOf(0)
    private var openReviewOnStart = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        val language = SettingsStore(newBase).language
        super.attachBaseContext(LocaleHelper.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openReviewOnStart = intent?.getBooleanExtra(EXTRA_OPEN_REVIEW, false) == true

        NotificationPresenter(this).ensureChannel()
        SchedulerSetup.ensure(this)
        askForNotifications()

        setContent {
            val vm: AppViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            MemoryFlowTheme(themeMode = state.settings.themeMode) {
                AppRoot(
                    vm = vm,
                    state = state,
                    openReviewOnStart = openReviewOnStart,
                    reviewSignal = reviewSignal.intValue,
                    onLanguageChanged = { recreate() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_REVIEW, false)) {
            reviewSignal.intValue = reviewSignal.intValue + 1
        }
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_REVIEW = "open_review"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
