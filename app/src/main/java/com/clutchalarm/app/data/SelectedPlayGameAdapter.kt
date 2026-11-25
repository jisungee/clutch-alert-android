package com.clutchalarm.app.data

import com.clutchalarm.app.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Sparse observations, not an invented pitch-by-pitch reconstruction. */
internal object SelectedPlayGameAdapter {
    const val COVERAGE = "selected_plays_plus_official_half_inning_totals"
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): GameTimeline {
        val source = json.decodeFromString<SelectedGame>(text)
        val info = source.game_info
        require(info.coverage == COVERAGE)
        require(source.events.isNotEmpty())
        val first = source.events.first()
        require(first.event_type == "GAME_START" && first.inning.number == 1 && first.inning.half == InningHalf.TOP)
        require(first.score.away == 0 && first.score.home == 0)
        fun team(id: String) = KboTeams.find(id) ?: Team(id, id)
        fun player(value: SelectedPlayer?): Player? = value?.let {
            if (it.team.isNullOrBlank() || it.name.isNullOrBlank()) null else Player("${it.team}:${it.name}", it.name)
        }
        fun state(record: SelectedEvent, status: GameStatus): GameState {
            val batting = if (record.inning.half == InningHalf.TOP) info.away_team else info.home_team
            val known = record.details.bases_state_known
            fun runner(name: String?) = if (known && !name.isNullOrBlank()) Player("$batting:$name", name) else null
            return GameState(
                gameId = info.game_id, homeTeam = team(info.home_team), awayTeam = team(info.away_team),
                homeScore = record.score.home, awayScore = record.score.away,
                inning = record.inning.number, half = record.inning.half,
                balls = record.balls, strikes = record.strikes, outs = record.outs,
                bases = Bases(runner(record.bases.first), runner(record.bases.second), runner(record.bases.third), known),
                batter = player(record.current_batter), pitcher = player(record.current_pitcher),
                status = status, batterIsLastPlayActor = true,
            )
        }
        val initial = state(first, GameStatus.NOT_STARTED).copy(
            balls = null, strikes = null, outs = null, bases = Bases(known = false), batter = null, pitcher = null,
        )
        var previous = initial
        var offset = 0L
        val events = source.events.mapIndexed { index, record ->
            require(record.delay_sec >= 0) { "Negative delay at ${record.event_id}" }
            offset = Math.addExact(offset, Math.multiplyExact(record.delay_sec, 1000L))
            val summary = record.details.summary
            val next = source.events.getOrNull(index + 1)
            // Narrative identifies the play; the matching final record confirms the game result.
            // Neither the narrative nor sub_types rewrites the supplied score, outs or runners.
            val walkOff = record.event_type in setOf("SCORE", "HOME_RUN") && summary?.contains("끝내기") == true &&
                record.inning.number >= 9 && record.inning.half == InningHalf.BOTTOM &&
                record.score.home > previous.homeScore && record.score.home > record.score.away &&
                next?.event_type == "GAME_END" && next.score == record.score && next.inning == record.inning &&
                info.final_score == record.score
            val status = if (record.event_type == "GAME_END" || walkOff) GameStatus.FINISHED else GameStatus.IN_PROGRESS
            val after = state(record, status)
            val type = when (record.event_type) {
                "GAME_START" -> EventType.GAME_STARTED
                "GAME_END" -> EventType.GAME_ENDED
                "INNING_CHANGE" -> EventType.HALF_INNING_CHANGED
                "SCORE" -> EventType.RUN_SCORED
                "ON_BASE", "OPPONENT_ON_BASE" -> reachType(record.details.hit_type)
                "VIDEO_REVIEW" -> reviewType(record.details.review_result)
                "HOME_RUN" -> EventType.HOME_RUN
                "PITCHER_CHANGE" -> EventType.PITCHER_CHANGE
                "PITCHER_STAT" -> EventType.PITCHER_STAT
                "DEFENSE_HIGHLIGHT" -> EventType.DEFENSE_HIGHLIGHT
                "BENCH_CLEARING" -> EventType.BENCH_CLEARING
                else -> error("Unsupported selected event ${record.event_type}")
            }
            require(record.details.record_granularity != "half_inning_remaining" || type == EventType.RUN_SCORED)
            if (type == EventType.RUN_SCORED || type == EventType.HOME_RUN) {
                require(previous.inning == after.inning && previous.half == after.half) { "Scoring half is ambiguous" }
            }
            val playActor = if (record.details.record_granularity == "half_inning_remaining") null else player(record.current_batter)
            GameEvent(
                eventId = record.event_id.toString(), gameId = info.game_id, eventType = type,
                teamId = record.actor_team.takeUnless { it == "ALL" }, players = listOfNotNull(playActor),
                offsetMs = offset, before = previous, after = after, playActor = playActor,
                details = PlayDetails(
                    firstPitchConfirmed = type == EventType.GAME_STARTED, walkOff = walkOff,
                    useSourceDescription = true,
                    // A supplied "역전" label cannot override a confirmed tied scoreboard.
                    notificationDescription = if (after.homeScore == after.awayScore) summary?.replace("역전 ", "") else summary,
                    confirmedReach = record.event_type in setOf("ON_BASE", "OPPONENT_ON_BASE"),
                    reviewSubject = record.details.review_target, revisedCall = record.details.review_result,
                ),
                sourceLabel = if (record.details.record_granularity == "half_inning_remaining") "이닝 득점 합계 보정" else "선별 경기 기록",
                sourceSummary = summary, sourceTime = record.timestamp,
            ).also { previous = after }
        }
        require(events.last().eventType == EventType.GAME_ENDED)
        require(previous.homeScore == info.final_score.home && previous.awayScore == info.final_score.away)
        return GameTimeline(initial, events)
    }

    private fun reachType(description: String?): EventType = when (description) {
        "볼넷" -> EventType.WALK
        "몸에 맞는 공" -> EventType.HIT_BY_PITCH
        "1루수 실책" -> EventType.REACHED_ON_ERROR
        else -> when {
            description?.contains("2루타") == true -> EventType.DOUBLE
            description?.contains("3루타") == true -> EventType.TRIPLE
            description?.contains("실책") == true -> EventType.REACHED_BASE // Mixed causes stay unspecialized.
            description?.endsWith("안타") == true -> EventType.SINGLE
            else -> EventType.REACHED_BASE
        }
    }

    private fun reviewType(result: String?): EventType = when {
        result in setOf("세이프 유지", "아웃 유지", "스윙 유지", "노스윙 유지") -> EventType.OFFICIAL_REVIEW_UPHELD
        result in setOf("아웃→세이프", "세이프→아웃") -> EventType.OFFICIAL_REVIEW_OVERTURNED
        else -> EventType.OFFICIAL_REVIEW_RESULT // No inferred state or reversal for unrecognized prose.
    }
}

@Serializable private data class SelectedGame(val game_info: SelectedInfo, val events: List<SelectedEvent>)
@Serializable private data class SelectedInfo(
    val game_id: String, val away_team: String, val home_team: String, val coverage: String, val final_score: SelectedScore,
)
@Serializable private data class SelectedScore(val away: Int, val home: Int)
@Serializable private data class SelectedInning(val number: Int, val half: InningHalf)
@Serializable private data class SelectedBases(val first: String? = null, val second: String? = null, val third: String? = null)
@Serializable private data class SelectedPlayer(val team: String? = null, val name: String? = null)
@Serializable private data class SelectedDetails(
    val summary: String? = null, val record_granularity: String? = null,
    val hit_type: String? = null, val review_result: String? = null, val review_target: String? = null,
    val bases_state_known: Boolean = false,
)
@Serializable private data class SelectedEvent(
    val event_id: Int, val delay_sec: Long, val event_type: String, val actor_team: String,
    val inning: SelectedInning, val score: SelectedScore, val outs: Int? = null,
    val bases: SelectedBases = SelectedBases(), val current_batter: SelectedPlayer? = null,
    val current_pitcher: SelectedPlayer? = null, val balls: Int? = null, val strikes: Int? = null,
    val timestamp: String? = null, val details: SelectedDetails = SelectedDetails(),
)
