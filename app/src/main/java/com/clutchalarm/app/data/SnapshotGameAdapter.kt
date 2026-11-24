package com.clutchalarm.app.data

import com.clutchalarm.app.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Complete ordered plays: initial_state is explicit and every record is a post-play state. */
internal object SnapshotGameAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): GameTimeline {
        val source = json.decodeFromString<SourceGame>(text)
        val info = source.game_info
        require(source.events.isNotEmpty()) { "Snapshot game must contain events" }
        fun player(team: String, name: String?) = name?.let { Player("$team:$it", it) }
        fun state(event: SourceEvent) : GameState {
            val batting = if (event.inning.half == InningHalf.TOP) info.away_team else info.home_team
            return GameState(
                gameId = info.game_id, homeTeam = Team(info.home_team, info.home_team),
                awayTeam = Team(info.away_team, info.away_team),
                homeScore = event.score.home, awayScore = event.score.away,
                inning = event.inning.number, half = event.inning.half,
                balls = event.balls, strikes = event.strikes, outs = event.outs,
                bases = Bases(player(batting, event.bases.first), player(batting, event.bases.second),
                    player(batting, event.bases.third)),
                batter = event.current_batter?.let { player(it.team, it.name) },
                pitcher = event.current_pitcher?.let { player(it.team, it.name) },
                status = event.status ?: if (event.event_type == "GAME_END") GameStatus.FINISHED
                    else GameStatus.IN_PROGRESS,
            )
        }
        val initial = state(source.initial_state)
        var previous = initial
        var offset = 0L
        val events = source.events.map { event ->
            require(event.delay_sec >= 0) { "Negative delay" }
            offset = Math.addExact(offset, Math.multiplyExact(event.delay_sec, 1000L))
            val after = state(event)
            val type = when (event.event_type) {
                "GAME_START" -> EventType.GAME_STARTED
                "GAME_END" -> EventType.GAME_ENDED
                "INNING_CHANGE" -> EventType.HALF_INNING_CHANGED
                "ON_BASE", "OPPONENT_ON_BASE" -> when (event.details.reach_type) {
                    "SINGLE" -> EventType.SINGLE
                    "DOUBLE" -> EventType.DOUBLE
                    "TRIPLE" -> EventType.TRIPLE
                    "WALK" -> EventType.WALK
                    "HIT_BY_PITCH" -> EventType.HIT_BY_PITCH
                    "ERROR" -> EventType.REACHED_ON_ERROR
                    "DROPPED_THIRD_STRIKE" -> EventType.DROPPED_THIRD_STRIKE_REACHED
                    "FIELDERS_CHOICE" -> EventType.FIELDERS_CHOICE_REACHED
                    else -> error("ON_BASE requires structured reach_type")
                }
                "SCORE", "CONCEDE", "LEAD_CHANGE", "BIG_INNING" -> EventType.RUN_SCORED
                "VIDEO_REVIEW" -> when (event.details.review_result) {
                    "STARTED" -> EventType.OFFICIAL_REVIEW_STARTED
                    "UPHELD" -> EventType.OFFICIAL_REVIEW_UPHELD
                    "OVERTURNED" -> EventType.OFFICIAL_REVIEW_OVERTURNED
                    else -> error("VIDEO_REVIEW requires structured review_result")
                }
                "INJURY" -> when (event.details.injury_stage) {
                    "SUSPECTED" -> EventType.INJURY_SUSPECTED_STOPPAGE
                    "MEDICAL_CHECK" -> EventType.INJURY_MEDICAL_CHECK
                    "SUBSTITUTION" -> EventType.INJURY_SUBSTITUTION
                    else -> error("INJURY requires structured injury_stage")
                }
                else -> EventType.valueOf(event.event_type)
            }
            val actor = event.play_actor?.let { player(it.team, it.name) }
            if (type in setOf(EventType.SINGLE, EventType.DOUBLE, EventType.TRIPLE, EventType.WALK,
                    EventType.HIT_BY_PITCH, EventType.HOME_RUN, EventType.RUN_SCORED, EventType.BATTED_OUT,
                    EventType.REACHED_ON_ERROR, EventType.DROPPED_THIRD_STRIKE_REACHED, EventType.FIELDERS_CHOICE_REACHED)) {
                requireNotNull(actor) { "Batting play requires play_actor" }
                require(event.play_actor?.team == event.actor_team) { "Actor team mismatch" }
            }
            GameEvent(
                eventId = event.event_id.toString(), gameId = info.game_id,
                eventType = type,
                teamId = event.actor_team.takeUnless { it == "ALL" },
                players = listOfNotNull(actor), offsetMs = offset,
                before = previous, after = after,
                playActor = actor,
                details = PlayDetails(
                    firstPitchConfirmed = event.event_type == "GAME_START",
                    runsBattedIn = event.details.runs_batted_in,
                    reviewSubject = event.details.review_target,
                    revisedCall = event.details.revised_call,
                    walkOff = event.details.walk_off,
                ),
                sourceLabel = "원본 이벤트 · ${event.event_type}",
                sourceSummary = event.details.summary,
            ).also { previous = after }
        }
        return GameTimeline(initial, events)
    }
}

@Serializable
private data class SourceGame(val game_info: SourceInfo, val events: List<SourceEvent>, val initial_state: SourceEvent)
@Serializable
private data class SourceInfo(val game_id: String, val away_team: String, val home_team: String)
@Serializable
private data class SourceInning(val number: Int, val half: InningHalf)
@Serializable
private data class SourceScore(val away: Int, val home: Int)
@Serializable
private data class SourceBases(val first: String? = null, val second: String? = null, val third: String? = null)
@Serializable
private data class SourcePlayer(val team: String, val name: String)
@Serializable
private data class SourceDetails(
    val summary: String? = null, val reach_type: String? = null,
    val review_result: String? = null, val review_target: String? = null, val revised_call: String? = null,
    val injury_stage: String? = null, val runs_batted_in: Int? = null, val walk_off: Boolean = false,
)
@Serializable
private data class SourceEvent(
    val event_id: Int = 0, val delay_sec: Long = 0, val event_type: String = "STATE", val actor_team: String = "ALL",
    val inning: SourceInning, val score: SourceScore, val outs: Int, val bases: SourceBases,
    val play_actor: SourcePlayer? = null,
    val current_batter: SourcePlayer? = null, val current_pitcher: SourcePlayer? = null,
    val balls: Int? = null, val strikes: Int? = null, val status: GameStatus? = null,
    val details: SourceDetails = SourceDetails(),
)
