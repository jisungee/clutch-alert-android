package com.clutchalarm.app.domain.notification

import com.clutchalarm.app.domain.model.GameEvent

/** In-memory event deduplication for one playback session. */
class NotificationDeduplicator {
    private val seen = mutableSetOf<Pair<String, String>>()

    fun markIfNew(gameId: String, eventId: String): Boolean = seen.add(gameId to eventId)
}

/** Owns the history of a single ordered event consumer. Candidates are not delivery receipts. */
class NotificationSession(private val rules: NotificationRuleEngine = NotificationRuleEngine()) {
    class Token internal constructor(internal val gameId: String)

    private class Active(val token: Token) {
        val deduplicator = NotificationDeduplicator()
        var context = NotificationContext.Empty
    }

    private var active: Active? = null

    @Synchronized
    fun begin(gameId: String): Token = Token(gameId).also { active = Active(it) }

    @Synchronized
    fun end(token: Token) {
        if (active?.token === token) active = null
    }

    /** Pass the token captured for the source session, never a replacement token. */
    @Synchronized
    fun onEvent(token: Token, event: GameEvent, preferredTeamId: String): NotificationCandidate? {
        val current = active ?: return null
        if (current.token !== token || token.gameId != event.gameId) return null
        if (!current.deduplicator.markIfNew(event.gameId, event.eventId)) return null

        val decision = rules.evaluate(event, preferredTeamId, current.context)
        current.context = decision.nextContext
        return decision.candidate
    }
}
