package com.clutchalarm.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.clutchalarm.app.notification.*
import java.util.UUID

internal val notificationInstallationKey = stringPreferencesKey("notification_installation_id")

class DataStoreNotificationPublisher(private val store: DataStore<Preferences>) : NotificationPublisher {
    constructor(context: Context) : this(context.applicationContext.notificationPreferencesStore)

    override suspend fun publish(fallback: NotificationDelivery, send: (NotificationDelivery) -> Unit): NotificationDelivery {
        var published = fallback
        store.edit { values ->
            if (values.notificationSettingsWereSaved()) {
                val installation = values[notificationInstallationKey] ?: UUID.randomUUID().toString()
                val sequence = Math.addExact(values[sequenceKey] ?: 0L, 1L)
                published = fallback.copy(sequence = sequence, sequenceScopeId = installation,
                    sequenceScope = DispatchSequenceScope.INSTALLATION)
                send(published) // failure aborts this edit; no success number consumed
                values[notificationInstallationKey] = installation
                values[sequenceKey] = sequence
            } else send(fallback) // pre-measurement posts remain explicitly PROCESS-scoped
        }
        return published
    }

    private companion object {
        val sequenceKey = longPreferencesKey("successful_notification_sequence")
    }
}
