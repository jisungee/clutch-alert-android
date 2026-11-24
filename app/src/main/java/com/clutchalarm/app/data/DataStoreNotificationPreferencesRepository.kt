package com.clutchalarm.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clutchalarm.app.domain.model.KboTeams
import androidx.datastore.preferences.preferencesDataStore
import com.clutchalarm.app.domain.notification.NotificationPreferences
import com.clutchalarm.app.domain.notification.NotificationPreferencesRepository
import com.clutchalarm.app.domain.notification.NotificationType
import com.clutchalarm.app.domain.notification.toggleType
import kotlinx.coroutines.flow.first

internal val Context.notificationPreferencesStore by preferencesDataStore(name = "notification_preferences")

class DataStoreNotificationPreferencesRepository(
    private val store: DataStore<Preferences>,
) : NotificationPreferencesRepository {
    constructor(context: Context) : this(context.applicationContext.notificationPreferencesStore)

    override suspend fun load(): NotificationPreferences = store.data.first().toNotificationPreferences()

    override suspend fun update(
        transform: (NotificationPreferences) -> NotificationPreferences,
    ): NotificationPreferences = store.edit { values ->
        val previous = values.toNotificationPreferences()
        val transformed = transform(previous)
        val updated = transformed.copy(notificationSettingsSaved = previous.notificationSettingsSaved || transformed.notificationSettingsSaved)
        require(updated.preferredTeamId == null || KboTeams.find(updated.preferredTeamId) != null)
        values[notificationSavedKey] = updated.notificationSettingsSaved
        values[onboardingKey] = updated.onboardingCompleted
        updated.preferredTeamId?.let { values[teamKey] = it } ?: values.remove(teamKey)
        values[hideKey] = updated.hideWhenLosing
        values[initializedKey] = true
        if (updated.disabledTypes.isEmpty()) {
            values.remove(disabledTypesKey)
        } else {
            values[disabledTypesKey] = updated.disabledTypes.mapTo(mutableSetOf()) { it.name }
        }
    }.toNotificationPreferences()

    private fun Preferences.toNotificationPreferences(): NotificationPreferences {
        val disabledNames = this[disabledTypesKey].orEmpty() + if (this[initializedKey] == true) emptySet() else setOf("OPPONENT_ON_BASE")
        val disabled = NotificationType.entries.filter { it.name in disabledNames }.mapTo(mutableSetOf()) { it.toggleType() }
        val legacyUser = contains(initializedKey) || contains(disabledTypesKey) || contains(hideKey)
        return NotificationPreferences(disabled, this[hideKey] ?: false,
            preferredTeamId = KboTeams.find(this[teamKey])?.id,
            onboardingCompleted = this[onboardingKey] ?: legacyUser,
            notificationSettingsSaved = notificationSettingsWereSaved())
    }

    private companion object {
        val notificationSavedKey = notificationSettingsSavedKey
        val onboardingKey = booleanPreferencesKey("onboarding_completed")
        val teamKey = stringPreferencesKey("preferred_team_id")
        val initializedKey = booleanPreferencesKey("notification_preferences_initialized")
        val hideKey = booleanPreferencesKey("hide_when_losing")
        val disabledTypesKey = stringSetPreferencesKey("disabled_notification_types")
    }
}
