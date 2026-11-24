package com.clutchalarm.app.data

import androidx.datastore.preferences.core.*

internal val notificationSettingsSavedKey = booleanPreferencesKey("notification_settings_saved")

/** Existing persisted notification settings prove an earlier save, but old post numbers are never converted. */
internal fun Preferences.notificationSettingsWereSaved(): Boolean = this[notificationSettingsSavedKey] ?: (
    this[booleanPreferencesKey("onboarding_completed")] == true ||
        contains(booleanPreferencesKey("notification_preferences_initialized")) ||
        contains(stringSetPreferencesKey("disabled_notification_types")) || contains(booleanPreferencesKey("hide_when_losing")))
