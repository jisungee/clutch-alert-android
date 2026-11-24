package com.clutchalarm.app.domain.notification

data class NotificationPreferences(
    val disabledTypes: Set<NotificationType> = setOf(NotificationType.OPPONENT_ON_BASE),
    val hideWhenLosing: Boolean = false,
    val preferredTeamId: String? = null,
    val onboardingCompleted: Boolean = false,
    val notificationSettingsSaved: Boolean = false,
) {
    val enabledCount: Int get() = configurableNotificationTypes.count(::isEnabled)

    fun isEnabled(type: NotificationType): Boolean = type.toggleType() !in disabledTypes

    fun allows(candidate: NotificationCandidate, event: com.clutchalarm.app.domain.model.GameEvent, teamId: String?): Boolean {
        if (teamId != event.after.homeTeam.id && teamId != event.after.awayTeam.id) return false
        return select(candidate) != null
    }

    /** Streak state is updated by the rule engine for every play, even when all toggles are OFF. */
    fun select(candidate: NotificationCandidate): NotificationCandidate? {
        if (hideWhenLosing && candidate.hiddenUntilLead) return null
        val enabled = candidate.matchingTypes.filter(::isEnabled).toSet()
        if (enabled.isEmpty()) return null
        if (!candidate.recoveredLead) return candidate
        val homer = NotificationType.HOME_RUN in enabled
        val comeback = NotificationType.COMEBACK in enabled
        val type = when {
            comeback -> NotificationType.COMEBACK
            homer -> NotificationType.HOME_RUN
            NotificationType.RUN_SCORED in enabled -> NotificationType.RUN_SCORED
            else -> enabled.first()
        }
        val title = when {
            comeback && homer -> if (candidate.title.contains("끝내기")) candidate.title else "🚨 역전 홈런"
            comeback -> "🚨 역전"
            else -> type.title
        }
        return candidate.copy(type = type, title = title)
    }

    fun withEnabled(type: NotificationType, enabled: Boolean): NotificationPreferences {
        val toggle = type.toggleType()
        return copy(disabledTypes = if (enabled) disabledTypes.filterNot { it.toggleType() == toggle }.toSet() else disabledTypes + toggle)
    }
}

interface NotificationPreferencesRepository {
    suspend fun load(): NotificationPreferences
    suspend fun update(transform: (NotificationPreferences) -> NotificationPreferences): NotificationPreferences
}

internal class InMemoryNotificationPreferencesRepository(
    initial: NotificationPreferences = NotificationPreferences(onboardingCompleted = true),
) : NotificationPreferencesRepository {
    private var preferences = initial

    override suspend fun load(): NotificationPreferences = preferences

    override suspend fun update(
        transform: (NotificationPreferences) -> NotificationPreferences,
    ): NotificationPreferences {
        preferences = transform(preferences)
        return preferences
    }
}

fun NotificationType.toggleType(): NotificationType = when (this) {
    NotificationType.TIED -> NotificationType.COMEBACK
    NotificationType.OFFENSE_ENDED, NotificationType.DEFENSE_STARTED, NotificationType.DEFENSE_ENDED -> NotificationType.OFFENSE_STARTED
    else -> this
}

val configurableNotificationTypes = NotificationType.entries.filter { it.toggleType() == it }
