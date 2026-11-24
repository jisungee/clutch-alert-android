package com.clutchalarm.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clutchalarm.app.BuildConfig
import kotlinx.coroutines.CancellationException
import com.clutchalarm.app.MainActivity
import com.clutchalarm.app.R
import com.clutchalarm.app.domain.notification.NotificationCandidate
import com.clutchalarm.app.data.DataStoreNotificationPublisher
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidNotificationDispatcher(context: Context,
    private val publisher: NotificationPublisher = DataStoreNotificationPublisher(context)) : NotificationDispatcher {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val compat = NotificationManagerCompat.from(app)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, app.getString(R.string.game_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
            )
        }
    }

    fun availability(): NotificationAvailability = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
            NotificationAvailability.PERMISSION_REQUIRED
        !compat.areNotificationsEnabled() || channelDisabled() -> NotificationAvailability.DISABLED
        else -> NotificationAvailability.AVAILABLE
    }

    override fun dispatch(candidate: NotificationCandidate): DeliveryStatus = runBlocking { submit(candidate).status }

    override fun dispatchTracked(candidate: NotificationCandidate): NotificationSubmission = runBlocking { submit(candidate) }

    override suspend fun submit(candidate: NotificationCandidate): NotificationSubmission = withContext(Dispatchers.IO) {
        dispatchMutex.withLock {
            val cleanup = mutableSetOf<NotificationAddress>()
            var postedAddress: NotificationAddress? = null
            try {
                when (availability()) {
                    NotificationAvailability.PERMISSION_REQUIRED ->
                        return@withLock NotificationSubmission(DeliveryStatus.PERMISSION_DENIED)
                    NotificationAvailability.DISABLED -> return@withLock NotificationSubmission(DeliveryStatus.DISABLED)
                    NotificationAvailability.AVAILABLE -> Unit
                }
                val now = SystemClock.elapsedRealtime()
                val active = manager.activeNotifications.mapNotNull(::managedNotification)
                val removals = retention.makeRoom(active, candidate.important, now)
                    ?: return@withLock NotificationSubmission(DeliveryStatus.RETENTION_LIMIT)
                val address = ids.next()
                val fallback = NotificationDelivery.from(candidate.gameId, address)
                val delivery = publisher.publish(fallback) { delivery ->
                    val intent = Intent(app, MainActivity::class.java).apply {
                        action = ACTION_OPEN_GAME
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        NotificationEntryIntent.put(this, delivery)
                        data = Uri.Builder().scheme("clutchalarm").authority("game")
                            .appendPath(candidate.gameId)
                            .appendQueryParameter("post", delivery.postId).build()
                    }
                    val contentIntent = PendingIntent.getActivity(app, address.id, intent, PendingIntent.FLAG_IMMUTABLE)
                    val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_game_notification)
                        .setContentTitle(candidate.title)
                        .setContentText(candidate.body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(candidate.body))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true)
                        .addExtras(Bundle().apply {
                            putString(NotificationEntryIntent.POST_ID, delivery.postId)
                            putLong(NotificationEntryIntent.SEQUENCE, delivery.sequence)
                            putString(NotificationEntryIntent.SCOPE_ID, delivery.sequenceScopeId)
                            putString(NotificationEntryIntent.SCOPE, delivery.sequenceScope.name)
                            putBoolean(EXTRA_MANAGED, true)
                            putBoolean(EXTRA_IMPORTANT, candidate.important)
                        })
                        .build()
                    for (old in removals) {
                        manager.cancel(old.tag, old.id)
                        retention.cancelled(old, now)
                        cleanup.add(old)
                    }
                    manager.notify(address.tag, address.id, notification)
                    postedAddress = address
                }
                retention.submitted(ActiveGameNotification(address, System.currentTimeMillis(), candidate.important), now)
                if (BuildConfig.DEBUG) Log.d("ClutchNotificationPost", "post=${delivery.postId} sequence=${delivery.sequence} " +
                    "scope=${delivery.sequenceScope} installation=${delivery.sequenceScopeId} game=${delivery.gameId}")
                NotificationSubmission(DeliveryStatus.SENT, address, cleanup, delivery)
            } catch (error: CancellationException) {
                postedAddress?.let { manager.cancel(it.tag, it.id) }
                throw error
            } catch (error: IOException) {
                postedAddress?.let { manager.cancel(it.tag, it.id) }
                if (BuildConfig.DEBUG) Log.w("ClutchNotifications", "Success sequence could not be committed", error)
                NotificationSubmission(DeliveryStatus.ERROR, cleanupRequested = cleanup)
            } catch (error: SecurityException) {
                val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) DeliveryStatus.PERMISSION_DENIED else DeliveryStatus.ERROR
                NotificationSubmission(status, cleanupRequested = cleanup)
            } catch (error: RuntimeException) {
                if (BuildConfig.DEBUG) Log.w("ClutchNotifications", "Notification submission failed", error)
                NotificationSubmission(DeliveryStatus.ERROR, cleanupRequested = cleanup)
            }
        }
    }

    override suspend fun confirm(submission: NotificationSubmission): NotificationConfirmation? {
        if (submission.address == null && submission.cleanupRequested.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            confirmFromActiveList(submission) {
                try {
                    manager.activeNotifications.mapNotNullTo(mutableSetOf()) {
                        it.tag?.let { tag -> NotificationAddress(tag, it.id) }
                    }
                } catch (error: RuntimeException) {
                    if (BuildConfig.DEBUG) Log.w("ClutchNotifications", "Unable to inspect active notifications", error)
                    null
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun managedNotification(active: StatusBarNotification): ActiveGameNotification? {
        val notification = active.notification
        val tag = active.tag ?: return null
        if (active.packageName != app.packageName || !active.isClearable ||
            notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0
        ) return null
        val extras = notification.extras
        val managed = extras.getBoolean(EXTRA_MANAGED)
        val onGameChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.channelId == CHANNEL_ID || notification.channelId == LEGACY_CHANNEL_ID
        } else notification.icon == R.drawable.ic_game_notification
        if (!onGameChannel || (!managed && !legacyTag.matches(tag))) return null

        val important = if (managed) extras.getBoolean(EXTRA_IMPORTANT) else {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            "끝내기" in title || "역전" in title || "홈런" in title || "홈런!" in body
        }
        return ActiveGameNotification(NotificationAddress(tag, active.id), active.postTime, important)
    }

    private fun channelDisabled() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE

    companion object {
        const val CHANNEL_ID = "mock_game_events_heads_up"
        private const val LEGACY_CHANNEL_ID = "mock_game_events"
        const val ACTION_OPEN_GAME = "com.clutchalarm.app.OPEN_GAME"
        const val EXTRA_GAME_ID = "com.clutchalarm.app.GAME_ID"
        private const val EXTRA_MANAGED = "com.clutchalarm.app.MANAGED_NOTIFICATION"
        private const val EXTRA_IMPORTANT = "com.clutchalarm.app.IMPORTANT_NOTIFICATION"
        private val dispatchMutex = Mutex()
        private val ids = NotificationIds()
        private val retention = NotificationRetention()
        private val legacyTag = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
