package com.clutchalarm.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.clutchalarm.app.data.DataStoreNotificationPreferencesRepository
import com.clutchalarm.app.notification.AndroidNotificationDispatcher
import com.clutchalarm.app.notification.NotificationAvailability
import com.clutchalarm.app.ui.GameApp
import com.clutchalarm.app.ui.GameViewModel

class MainActivity : ComponentActivity() {
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
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameId = if (savedInstanceState == null) notificationGameId(intent)
            else savedInstanceState.getString(STATE_GAME_ID)
        gameId?.let(gameViewModel::selectGame)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GameApp(gameViewModel, availability, ::requestNotificationPermission, ::openNotificationSettings)
        }
    }

    override fun onResume() {
        super.onResume()
        availability = dispatcher.availability()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationGameId(intent)?.let(gameViewModel::selectGame)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_GAME_ID, gameViewModel.currentGameId)
        super.onSaveInstanceState(outState)
    }

    private fun notificationGameId(intent: Intent): String? =
        if (intent.action == AndroidNotificationDispatcher.ACTION_OPEN_GAME) {
            intent.getStringExtra(AndroidNotificationDispatcher.EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }
        } else null

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
        const val STATE_GAME_ID = "selected_game_id"
    }
}
