package com.clutchalarm.app.domain.notification

data class NotificationPreferences(val disabledTypes: Set<NotificationType> = emptySet()) {
    val enabledCount: Int get() = NotificationType.entries.size - disabledTypes.size

    fun isEnabled(type: NotificationType): Boolean = type !in disabledTypes

    fun withEnabled(type: NotificationType, enabled: Boolean): NotificationPreferences =
        copy(disabledTypes = if (enabled) disabledTypes - type else disabledTypes + type)
}

interface NotificationPreferencesRepository {
    suspend fun load(): NotificationPreferences
    suspend fun update(transform: (NotificationPreferences) -> NotificationPreferences): NotificationPreferences
}

internal class InMemoryNotificationPreferencesRepository : NotificationPreferencesRepository {
    private var preferences = NotificationPreferences()

    override suspend fun load(): NotificationPreferences = preferences

    override suspend fun update(
        transform: (NotificationPreferences) -> NotificationPreferences,
    ): NotificationPreferences {
        preferences = transform(preferences)
        return preferences
    }
}
