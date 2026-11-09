package com.clutchalarm.app.domain.model

enum class EventType {
    GAME_STARTED, SINGLE, DOUBLE, TRIPLE, HOME_RUN, WALK, STRIKEOUT,
    FOUL, BATTED_OUT, RUN_SCORED, HALF_INNING_CHANGED, GAME_ENDED,
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
) {
    init {
        require(eventId.isNotBlank())
        require(offsetMs >= 0) { "Offset must be nonnegative" }
        require(gameId == before.gameId && gameId == after.gameId) { "Event game ID mismatch" }
        require(before.homeTeam == after.homeTeam && before.awayTeam == after.awayTeam)
        require(teamId == null || teamId == before.homeTeam.id || teamId == before.awayTeam.id)
        require(after.homeScore >= before.homeScore && after.awayScore >= before.awayScore) {
            "Scores cannot decrease"
        }
    }
}

data class GameTimeline(val initialState: GameState, val events: List<GameEvent>) {
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
