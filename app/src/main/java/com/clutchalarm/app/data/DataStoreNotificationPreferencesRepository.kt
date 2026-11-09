package com.clutchalarm.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clutchalarm.app.domain.notification.NotificationPreferences
import com.clutchalarm.app.domain.notification.NotificationPreferencesRepository
import com.clutchalarm.app.domain.notification.NotificationType
import kotlinx.coroutines.flow.first

private val Context.notificationPreferencesStore by preferencesDataStore(name = "notification_preferences")

class DataStoreNotificationPreferencesRepository(
    private val store: DataStore<Preferences>,
) : NotificationPreferencesRepository {
    constructor(context: Context) : this(context.applicationContext.notificationPreferencesStore)

    override suspend fun load(): NotificationPreferences = store.data.first().toNotificationPreferences()

    override suspend fun update(
        transform: (NotificationPreferences) -> NotificationPreferences,
    ): NotificationPreferences = store.edit { values ->
        val updated = transform(values.toNotificationPreferences())
        if (updated.disabledTypes.isEmpty()) {
            values.remove(disabledTypesKey)
        } else {
            values[disabledTypesKey] = updated.disabledTypes.mapTo(mutableSetOf()) { it.name }
        }
    }.toNotificationPreferences()

    private fun Preferences.toNotificationPreferences(): NotificationPreferences {
        val disabledNames = this[disabledTypesKey].orEmpty()
        return NotificationPreferences(NotificationType.entries.filterTo(mutableSetOf()) { it.name in disabledNames })
    }

    private companion object {
        val disabledTypesKey = stringSetPreferencesKey("disabled_notification_types")
    }
}
