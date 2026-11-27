package com.clutchalarm.app

import com.clutchalarm.app.behavior.BehaviorEventRecorder
import com.clutchalarm.app.behavior.DebugBehaviorEvents
import com.clutchalarm.app.behavior.DurableBehaviorEvents
import com.clutchalarm.app.auth.AuthenticationProvider
import com.clutchalarm.app.auth.AndroidSessionStore
import com.clutchalarm.app.ui.LoginViewModel
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.app.NotificationManagerCompat
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.data.DataStoreNotificationOpenRepository
import com.clutchalarm.app.notification.NotificationEntryIntent
import com.clutchalarm.app.notification.NotificationReadStatus
import com.clutchalarm.app.data.DataStoreNotificationPreferencesRepository
import com.clutchalarm.app.notification.AndroidNotificationDispatcher
import com.clutchalarm.app.notification.NotificationAvailability
import com.clutchalarm.app.ui.GameApp
import com.clutchalarm.app.ui.GameViewModel
import com.clutchalarm.app.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val behaviorRecorder: BehaviorEventRecorder by lazy {
        if (BuildConfig.QA_DIAGNOSTICS || BuildConfig.API_BASE_URL.isBlank()) DebugBehaviorEvents
        else DurableBehaviorEvents.get(applicationContext)
    }
    private val dispatcher by lazy { AndroidNotificationDispatcher(applicationContext) }
    private var availability by mutableStateOf(NotificationAvailability.DISABLED)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { availability = dispatcher.availability() }

    private val gameViewModel by viewModels<GameViewModel> {
        viewModelFactory {
            initializer {
                GameViewModel(
                    MockGameRepository(application.assets),
                    dispatcher = dispatcher,
                    preferencesRepository = DataStoreNotificationPreferencesRepository(applicationContext),
                    notificationOpens = DataStoreNotificationOpenRepository(applicationContext, behaviorRecorder),
                    behaviorEvents = behaviorRecorder,
                    onDetailEntry = { entry ->
                        if (BuildConfig.DEBUG) Log.i("ClutchNotificationOpen", "source=${entry.source} game=${entry.gameId} " +
                            "post=${entry.delivery?.postId ?: "none"} sequence=${entry.delivery?.sequence ?: 0} " +
                            "scope=${entry.delivery?.sequenceScope} newRead=${entry.readStatus == NotificationReadStatus.NEW} " +
                            "status=${entry.readStatus}")
                    },
                )
            }
        }
    }

    private val loginViewModel by viewModels<LoginViewModel> {
        viewModelFactory {
            initializer {
                LoginViewModel(AuthenticationProvider.create(AndroidSessionStore(applicationContext)), AuthenticationProvider.qaLoginAvailable,
                    gameViewModel::onLoginCompleted, gameViewModel::onSessionUnavailable, gameViewModel::onLoggedOut,
                    sessionEvent = { if (BuildConfig.DEBUG) Log.i("ClutchSession", it) })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (behaviorRecorder as? DurableBehaviorEvents)?.scheduleRecovery()
        if (savedInstanceState == null) {
            handleNotificationIntent(intent)
        } else {
            MainScreen.entries.firstOrNull { it.name == savedInstanceState.getString(STATE_MAIN_SCREEN) }
                ?.let(gameViewModel::restoreMainScreen)
            if (gameViewModel.detailEntry.value == null) {
                val restored = NotificationEntryIntent.restore(savedInstanceState)
                val incoming = NotificationEntryIntent.read(intent)
                val entry = if (incoming != null && incoming.delivery?.postId != restored?.delivery?.postId) incoming
                    else restored ?: incoming
                if (entry != null) gameViewModel.restoreDetailEntry(entry)
                else savedInstanceState.getString(STATE_GAME_ID)?.let(gameViewModel::selectGame)
            }
            // Saved entry state, not the old launch Intent, owns subsequent recreation.
            setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GameApp(gameViewModel, loginViewModel, availability, ::requestNotificationPermission, ::openNotificationSettings,
                (if (behaviorRecorder is DurableBehaviorEvents) (behaviorRecorder as DurableBehaviorEvents).appOpen
                    else DebugBehaviorEvents.appOpen)::onScreenVisible)
        }
    }

    override fun onResume() {
        super.onResume()
        availability = dispatcher.availability()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onDestroy() {
        if (isFinishing) gameViewModel.onSettingsFlowAbandoned()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        gameViewModel.detailEntry.value?.let { NotificationEntryIntent.save(outState, it) }
        outState.putString(STATE_MAIN_SCREEN, gameViewModel.mainScreen.value.name)
        outState.putString(STATE_GAME_ID, gameViewModel.currentGameId)
        super.onSaveInstanceState(outState)
    }

    private fun handleNotificationIntent(incoming: Intent) {
        val entry = NotificationEntryIntent.read(incoming) ?: return
        entry.delivery?.let(gameViewModel::openNotification) ?: gameViewModel.openNotificationGame(entry.gameId)
        setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
    }

    private fun requestNotificationPermission() {
        availability = dispatcher.availability()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            availability == NotificationAvailability.PERMISSION_REQUIRED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openNotificationSettings() {
        val settings = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                NotificationManagerCompat.from(this).areNotificationsEnabled() ->
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidNotificationDispatcher.CHANNEL_ID)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        }
        startActivity(settings)
    }

    private companion object {
        const val STATE_MAIN_SCREEN = "main_screen"
        const val STATE_GAME_ID = "selected_game_id"
    }
}
