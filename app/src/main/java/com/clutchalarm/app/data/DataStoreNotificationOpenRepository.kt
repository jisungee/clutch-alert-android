package com.clutchalarm.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.clutchalarm.app.behavior.*
import com.clutchalarm.app.notification.DispatchSequenceScope
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clutchalarm.app.notification.NotificationDelivery
import com.clutchalarm.app.notification.NotificationOpenRepository

private val Context.notificationOpenStore by preferencesDataStore(name = "notification_open_receipts")

class DataStoreNotificationOpenRepository(private val store: DataStore<Preferences>,
    private val recorder: BehaviorEventRecorder = NoBehaviorEvents) : NotificationOpenRepository {
    constructor(context: Context, recorder: BehaviorEventRecorder = NoBehaviorEvents) : this(context.applicationContext.notificationOpenStore, recorder)

    override suspend fun markOpened(delivery: NotificationDelivery): Boolean {
        var first = false
        var event: BehaviorEvent? = null
        store.edit { values ->
            val seen = values[openedKey].orEmpty()
            first = delivery.postId !in seen
            if (first) {
                values[openedKey] = seen + delivery.postId
                if (delivery.sequenceScope == DispatchSequenceScope.INSTALLATION) {
                    if (delivery.sequence == 1L && values[firstDoneKey] != true) {
                        values[firstDoneKey] = true
                        event = BehaviorEvent("first_done", notificationId = delivery.postId,
                            notificationSequence = delivery.sequence, installationId = delivery.sequenceScopeId, gameId = delivery.gameId)
                    } else if (delivery.sequence >= 2L) {
                        event = BehaviorEvent("repeat_done", action = "notification_open", notificationId = delivery.postId,
                            notificationSequence = delivery.sequence, installationId = delivery.sequenceScopeId, gameId = delivery.gameId)
                    }
                }
            }
        }
        event?.let(recorder::record)
        return first
    }

    private companion object {
        val firstDoneKey = booleanPreferencesKey("first_done_recorded")
        val openedKey = stringSetPreferencesKey("opened_notification_post_ids")
    }
}
