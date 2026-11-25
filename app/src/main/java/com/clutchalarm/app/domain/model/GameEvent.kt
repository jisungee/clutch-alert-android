package com.clutchalarm.app.domain.model

import kotlinx.serialization.Serializable

enum class EventType {
    PITCHER_CHANGE, PITCHER_STAT, DEFENSE_HIGHLIGHT, BENCH_CLEARING,
    REACHED_BASE, OFFICIAL_REVIEW_RESULT, SNAPSHOT, GAME_STARTED, SINGLE, DOUBLE, TRIPLE, HOME_RUN, WALK, STRIKEOUT,
    FOUL, BATTED_OUT, RUN_SCORED, HALF_INNING_CHANGED, GAME_ENDED,
    FIRST_PITCH, HALF_INNING_STARTED, HIT_BY_PITCH, REACHED_ON_ERROR,
    DROPPED_THIRD_STRIKE_REACHED, FIELDERS_CHOICE_REACHED, STOLEN_BASE, WILD_PITCH,
    OFFICIAL_REVIEW_STARTED, OFFICIAL_REVIEW_UPHELD, OFFICIAL_REVIEW_OVERTURNED,
    BROADCAST_REPLAY, INJURY_SUSPECTED_STOPPAGE, INJURY_MEDICAL_CHECK, INJURY_SUBSTITUTION,
}

@Serializable
data class PlayDetails(
    val firstPitchConfirmed: Boolean = false,
    val walkOff: Boolean = false,
    val useSourceDescription: Boolean = false,
    val notificationDescription: String? = null,
    val confirmedReach: Boolean = false,
    val runsBattedIn: Int? = null,
    val reviewSubject: String? = null,
    val revisedCall: String? = null,
) {
    init {
        require(runsBattedIn == null || runsBattedIn in 0..4)
    }
}

data class GameEvent(
    val eventId: String,
    val gameId: String,
    val eventType: EventType,
    val teamId: String?,
    val players: List<Player>,
    val offsetMs: Long,
    val before: GameState,
    val after: GameState,
    val details: PlayDetails = PlayDetails(),
    val playActor: Player? = null,
    val sourceLabel: String? = null,
    val sourceSummary: String? = null,
    val sourceTime: String? = null,
) {
    init {
        require(eventId.isNotBlank())
        require(offsetMs >= 0) { "Offset must be nonnegative" }
        require(gameId == before.gameId && gameId == after.gameId) { "Event game ID mismatch" }
        require(before.homeTeam == after.homeTeam && before.awayTeam == after.awayTeam)
        require(teamId == null || teamId == before.homeTeam.id || teamId == before.awayTeam.id)
        require(eventType == EventType.OFFICIAL_REVIEW_OVERTURNED ||
            (after.homeScore >= before.homeScore && after.awayScore >= before.awayScore)) {
            "Scores cannot decrease"
        }
    }
}

data class GameTimeline(
    val initialState: GameState,
    val events: List<GameEvent>,
    val notificationsEnabled: Boolean = true,
) {
    init {
        require(events.map { it.eventId }.toSet().size == events.size) { "Duplicate event ID" }

        var previousState = initialState
        var previousOffset = 0L
        for (event in events) {
            require(event.before == previousState) { "State discontinuity at ${event.eventId}" }
            require(event.offsetMs >= previousOffset) { "Events must be in playback order" }
            previousState = event.after
            previousOffset = event.offsetMs
        }
    }
}
