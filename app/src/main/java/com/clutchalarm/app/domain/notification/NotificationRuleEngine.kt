package com.clutchalarm.app.domain.notification

import com.clutchalarm.app.domain.model.Bases
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameStatus
import com.clutchalarm.app.domain.model.InningHalf

enum class NotificationType(val title: String) {
    OPPONENT_ON_BASE("상대 팀 출루"),
    GAME_ENDED("경기 종료"),
    PITCHER_CHANGE("투수 교체"),
    PITCHER_STAT("투수 기록"),
    DEFENSE_HIGHLIGHT("호수비"),
    BENCH_CLEARING("벤치클리어링"),
    GAME_STARTED("⚾ 경기 시작"),
    OFFENSE_STARTED("⚾ 공격 시작"),
    OFFENSE_ENDED("⚾ 공격 종료"),
    DEFENSE_STARTED("🛡️ 수비 시작"),
    DEFENSE_ENDED("🛡️ 수비 종료"),
    REACHED_BASE("🏃 출루"),
    RUN_SCORED("🎉 득점"),
    TIED("🔥 동점"),
    COMEBACK("🚨 역전·동점"),
    HOME_RUN("💥 홈런"),
    RUN_ALLOWED("⚠️ 실점"),
    VIDEO_REVIEW("📺 비디오 판독"),
    INJURY("🚑 부상"),
    BIG_INNING("🔥 빅이닝"),
}

data class NotificationCandidate(
    val id: String,
    val gameId: String,
    val eventId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val matchingTypes: Set<NotificationType> = setOf(type),
    val hiddenUntilLead: Boolean = false,
    val recoveredLead: Boolean = false,
    val important: Boolean = type == NotificationType.COMEBACK || type == NotificationType.HOME_RUN,
)

data class NotificationDecision(
    val candidate: NotificationCandidate?,
    val nextContext: NotificationContext,
)

class NotificationContext internal constructor(
    internal val trailingTeams: Set<String> = emptySet(),
    internal val scoringHalf: HalfInning? = null,
    internal val runsInHalf: Long = 0,
    internal val bigInningAnnounced: Boolean = false,
    internal val announcedStart: HalfInning? = null,
) {
    companion object {
        val Empty = NotificationContext()
    }
}

internal data class HalfInning(val gameId: String, val number: Int, val half: InningHalf) {
    val label get() = "${number}회${if (half == InningHalf.TOP) "초" else "말"}"

    fun next() = if (half == InningHalf.TOP) {
        copy(half = InningHalf.BOTTOM)
    } else {
        copy(number = number + 1, half = InningHalf.TOP)
    }
}

private fun GameState.halfInning() = HalfInning(gameId, inning, half)

private fun GameState.battingTeam() = if (half == InningHalf.TOP) awayTeam else homeTeam

/** Evaluates one confirmed play. Feed [NotificationDecision.nextContext] into the next play. */
class NotificationRuleEngine {
    fun evaluate(
        event: GameEvent,
        preferredTeamId: String,
        context: NotificationContext = NotificationContext.Empty,
    ): NotificationDecision {
        if (event.eventType == EventType.SNAPSHOT) return NotificationDecision(null, context)
        val before = event.before
        val after = event.after
        val trailingBefore = context.trailingTeams.toMutableSet().apply {
            if (before.homeScore < before.awayScore) add(before.homeTeam.id)
            if (before.awayScore < before.homeScore) add(before.awayTeam.id)
        }
        val trailingAfter = trailingBefore.toMutableSet().apply {
            when {
                after.homeScore > after.awayScore -> { remove(after.homeTeam.id); add(after.awayTeam.id) }
                after.awayScore > after.homeScore -> { remove(after.awayTeam.id); add(after.homeTeam.id) }
                // A tie keeps the previous losing streak, including at game end.
            }
        }
        val recoveredLead = preferredTeamId in trailingBefore && preferredTeamId !in trailingAfter
        val half = before.halfInning()
        val preferred = listOf(before.homeTeam, before.awayTeam).find { it.id == preferredTeamId }
        val homeRuns = after.homeScore - before.homeScore
        val awayRuns = after.awayScore - before.awayScore
        val scoringTeam = when {
            event.eventType in independentEventTypes -> null
            homeRuns > 0 && awayRuns == 0 -> before.homeTeam
            awayRuns > 0 && homeRuns == 0 -> before.awayTeam
            else -> null
        }
        val runs = if (scoringTeam == null) 0 else maxOf(homeRuns, awayRuns)
        val previousRuns = if (context.scoringHalf == half) context.runsInHalf else 0
        val correction = if (event.eventType == EventType.OFFICIAL_REVIEW_OVERTURNED) {
            if (before.half == InningHalf.TOP) awayRuns else homeRuns
        } else 0
        val halfRuns = (previousRuns + if (scoringTeam == before.battingTeam()) runs else correction).coerceAtLeast(0)
        val previouslyAnnounced = context.scoringHalf == half && context.bigInningAnnounced
        val bigInning = scoringTeam != null && !previouslyAnnounced && previousRuns < 3 && halfRuns >= 3

        val ended = event.eventType !in independentEventTypes &&
            before.outs != null && before.outs < 3 && after.outs == 3 && half == after.halfInning()
        val starts = after.status == GameStatus.IN_PROGRESS &&
            (event.eventType == EventType.HALF_INNING_STARTED ||
                (event.eventType == EventType.HALF_INNING_CHANGED && half != after.halfInning()))
        val transitionAnnounced = ended && after.status == GameStatus.IN_PROGRESS
        val nextContext = NotificationContext(
            trailingTeams = trailingAfter,
            scoringHalf = half,
            runsInHalf = halfRuns,
            bigInningAnnounced = previouslyAnnounced || bigInning,
            announcedStart = when {
                preferred == null -> context.announcedStart
                transitionAnnounced -> half.next()
                starts -> after.halfInning()
                else -> context.announcedStart
            },
        )
        if (preferred == null) return NotificationDecision(null, nextContext)

        val preference = ScorePerspective(preferredTeamId == before.homeTeam.id, event)
        val batting = before.battingTeam()
        val preferredBatting = batting.id == preferredTeamId
        val ending = when {
            !ended -> null
            preferredBatting -> if (transitionAnnounced) "공격 종료 · 수비 전환" else "공격 종료"
            else -> if (transitionAnnounced) "수비 종료 · 공격 전환" else "수비 종료"
        }
        val decision = when (event.eventType) {
            EventType.GAME_ENDED -> Message(NotificationType.GAME_ENDED, "${preferred.name} 경기 종료")
            EventType.PITCHER_CHANGE -> Message(NotificationType.PITCHER_CHANGE, event.sourceSummary ?: "투수 교체")
            EventType.PITCHER_STAT -> Message(NotificationType.PITCHER_STAT, event.sourceSummary ?: "투수 기록")
            EventType.DEFENSE_HIGHLIGHT -> Message(NotificationType.DEFENSE_HIGHLIGHT, event.sourceSummary ?: "호수비")
            EventType.BENCH_CLEARING -> Message(NotificationType.BENCH_CLEARING, event.sourceSummary ?: "벤치클리어링")
            EventType.FIRST_PITCH -> Message(NotificationType.GAME_STARTED, "${preferred.name} 경기 시작! 첫 투구")
            EventType.GAME_STARTED -> if (event.details.firstPitchConfirmed &&
                before.status == GameStatus.NOT_STARTED && after.status == GameStatus.IN_PROGRESS
            ) Message(NotificationType.GAME_STARTED, "${preferred.name} 경기 시작! 첫 투구") else null
            EventType.OFFICIAL_REVIEW_RESULT -> Message(NotificationType.VIDEO_REVIEW, event.sourceSummary ?: "비디오 판독 결과 미상")
            EventType.OFFICIAL_REVIEW_STARTED -> Message(
                NotificationType.VIDEO_REVIEW,
                listOfNotNull(event.details.reviewSubject?.takeIf { it.isNotBlank() }, "공식 비디오 판독 시작")
                    .joinToString(" · "),
            )
            EventType.OFFICIAL_REVIEW_UPHELD -> Message(NotificationType.VIDEO_REVIEW, "비디오 판독 결과 · 원심 유지")
            EventType.OFFICIAL_REVIEW_OVERTURNED -> Message(
                NotificationType.VIDEO_REVIEW,
                event.details.revisedCall?.takeIf { it.isNotBlank() }
                    ?.let { "비디오 판독 결과 · $it 판정으로 번복" } ?: "비디오 판독 결과 · 판정 번복",
            )
            EventType.INJURY_SUSPECTED_STOPPAGE -> injury(event, "부상 의심 · 경기 중단")
            EventType.INJURY_MEDICAL_CHECK -> injury(event, "부상 여부 의료진 확인 중")
            EventType.INJURY_SUBSTITUTION -> injury(event, "부상으로 교체")
            EventType.BROADCAST_REPLAY -> null
            else -> when {
                scoringTeam != null -> scoreMessage(event, scoringTeam.name, runs, preference, bigInning, recoveredLead)
                preferredBatting && (event.teamId == null || event.teamId == preferredTeamId) &&
                    event.eventType.reachLabel() != null && !ended &&
                    (event.details.confirmedReach || after.bases.canConfirmReach((event.playActor ?: before.batter)?.id)) -> Message(
                        NotificationType.REACHED_BASE,
                        "${actor(preferred.name, (event.playActor ?: before.batter)?.name)} ${event.eventType.reachLabel()}! " +
                            "${after.outs.outLabel()} ${after.bases.runnerLabel()}",
                    )
                !preferredBatting && event.eventType.reachLabel() != null && !ended &&
                    (event.details.confirmedReach || after.bases.canConfirmReach((event.playActor ?: before.batter)?.id)) -> Message(
                        NotificationType.OPPONENT_ON_BASE,
                        "${actor(batting.name, (event.playActor ?: before.batter)?.name)} ${event.eventType.reachLabel()}! " +
                            "${after.outs.outLabel()} ${after.bases.runnerLabel()}",
                    )
                ending != null -> Message(
                    if (preferredBatting) NotificationType.OFFENSE_ENDED else NotificationType.DEFENSE_ENDED,
                    "${preferred.name} $ending",
                )
                starts && after.halfInning() != context.announcedStart -> {
                    val offense = after.battingTeam().id == preferredTeamId
                    Message(
                        if (offense) NotificationType.OFFENSE_STARTED else NotificationType.DEFENSE_STARTED,
                        "${preferred.name} ${if (offense) "공격 시작" else "수비 시작"}",
                    )
                }
                else -> null
            }
        }

        val candidate = decision?.let { message ->
            val body = listOfNotNull(
                if (starts) after.halfInning().label else half.label,
                if (event.details.useSourceDescription && event.sourceSummary != null)
                    if (runs > 0) "${event.details.notificationDescription ?: event.sourceSummary} · ${message.text}" else event.sourceSummary
                else message.text,
                if (bigInning) "${scoringTeam?.name} ${halfRuns}득점 빅이닝" else null,
                ending?.takeIf { runs > 0 },
                preference.score,
            ).joinToString(" · ")
            NotificationCandidate(
                id = listOf(event.gameId, event.eventId, preferredTeamId, message.type.name)
                    .joinToString("") { "${it.length}:$it" },
                gameId = event.gameId,
                eventId = event.eventId,
                type = message.type,
                title = message.title,
                body = body,
                hiddenUntilLead = preferredTeamId in trailingAfter,
                recoveredLead = recoveredLead && scoringTeam?.id == preferredTeamId,
                matchingTypes = buildSet {
                    add(if (message.type == NotificationType.TIED) NotificationType.COMEBACK else message.type)
                    if (scoringTeam != null) {
                        add(if (scoringTeam.id == preferredTeamId) NotificationType.RUN_SCORED else NotificationType.RUN_ALLOWED)
                        if (event.eventType == EventType.HOME_RUN) add(NotificationType.HOME_RUN)
                        val beforeDiff = preference.beforePreferred - preference.beforeOpponent
                        val afterDiff = preference.afterPreferred - preference.afterOpponent
                        if (recoveredLead || (beforeDiff < 0 && afterDiff >= 0) || (beforeDiff > 0 && afterDiff <= 0))
                            add(NotificationType.COMEBACK)
                        if (bigInning) add(NotificationType.BIG_INNING)
                    }
                },
                important = message.important,
            )
        }
        return NotificationDecision(candidate, nextContext)
    }

    private fun scoreMessage(
        event: GameEvent,
        scoringTeamName: String,
        runs: Int,
        score: ScorePerspective,
        bigInning: Boolean,
        recoveredLead: Boolean,
    ): Message {
        val preferredScored = score.afterPreferred > score.beforePreferred
        val homer = event.eventType == EventType.HOME_RUN && runs in 1..4
        val walkOff = event.details.walkOff && event.before.half == InningHalf.BOTTOM &&
            event.after.status == GameStatus.FINISHED && event.after.homeScore > event.after.awayScore &&
            event.after.homeScore > event.before.homeScore
        val comeback = recoveredLead || (score.beforePreferred < score.beforeOpponent && score.afterPreferred > score.afterOpponent)
        val leadAllowed = score.beforePreferred > score.beforeOpponent && score.afterPreferred < score.afterOpponent
        val tied = score.afterPreferred == score.afterOpponent
        val hit = when {
            homer -> when (runs) {
                1 -> "솔로 홈런"
                4 -> "만루 홈런"
                else -> "${runs}점 홈런"
            }
            event.eventType in hitTypes && (event.details.runsBattedIn ?: 0) > 0 -> {
                val rbi = checkNotNull(event.details.runsBattedIn)
                if (rbi == 1) "적시타" else "${rbi}타점 적시타"
            }
            event.eventType in hitTypes -> event.eventType.reachLabel()
            else -> null
        }
        val subject = actor(scoringTeamName, if (event.details.useSourceDescription) null else event.playActor?.name ?: event.before.batter?.name?.takeIf { hit != null })
        val play = listOfNotNull(subject, hit).joinToString(" ")
        val leadTaken = score.beforePreferred == score.beforeOpponent && score.afterPreferred > score.afterOpponent
        val result = when {
            walkOff && preferredScored -> "끝내기 승리"
            walkOff -> "끝내기 실점"
            comeback -> "역전"
            leadAllowed -> "역전 허용"
            tied && preferredScored -> "동점"
            tied -> "동점 허용"
            leadTaken -> "리드"
            preferredScored -> "${runs}점 득점"
            else -> "${runs}실점 허용"
        }
        val type = when {
            !preferredScored -> NotificationType.RUN_ALLOWED
            comeback -> NotificationType.COMEBACK
            homer -> NotificationType.HOME_RUN
            tied -> NotificationType.TIED
            bigInning && !walkOff && !leadTaken -> NotificationType.BIG_INNING
            else -> NotificationType.RUN_SCORED
        }
        val title = when {
            walkOff && homer -> if (preferredScored) "🚨 끝내기 홈런" else "🚨 끝내기 홈런 허용"
            walkOff -> if (preferredScored) "🚨 끝내기 승리" else "🚨 끝내기 실점"
            comeback && homer -> "🚨 역전 홈런"
            leadAllowed -> "🚨 역전 허용"
            tied && !preferredScored -> "⚠️ 동점 허용"
            else -> type.title
        }
        return Message(type, "$play! $result", title, important = walkOff || comeback || leadAllowed || homer)
    }

    private fun injury(event: GameEvent, situation: String): Message {
        val team = listOf(event.before.homeTeam, event.before.awayTeam).find { it.id == event.teamId }
        val players = event.players.joinToString(", ") { it.name }.ifEmpty { null }
        return Message(NotificationType.INJURY, listOfNotNull(team?.name, players, situation).joinToString(" "))
    }

    private data class Message(
        val type: NotificationType,
        val text: String,
        val title: String = type.title,
        val important: Boolean = false,
    )

    private class ScorePerspective(home: Boolean, event: GameEvent) {
        val beforePreferred = if (home) event.before.homeScore else event.before.awayScore
        val beforeOpponent = if (home) event.before.awayScore else event.before.homeScore
        val afterPreferred = if (home) event.after.homeScore else event.after.awayScore
        val afterOpponent = if (home) event.after.awayScore else event.after.homeScore
        val score = "$afterPreferred : $afterOpponent"
    }

    private companion object {
        val hitTypes = setOf(EventType.SINGLE, EventType.DOUBLE, EventType.TRIPLE)
        val independentEventTypes = setOf(
            EventType.OFFICIAL_REVIEW_RESULT, EventType.OFFICIAL_REVIEW_STARTED, EventType.OFFICIAL_REVIEW_UPHELD,
            EventType.OFFICIAL_REVIEW_OVERTURNED, EventType.BROADCAST_REPLAY,
            EventType.INJURY_SUSPECTED_STOPPAGE, EventType.INJURY_MEDICAL_CHECK,
            EventType.INJURY_SUBSTITUTION,
        )
    }
}

private fun actor(team: String, player: String?) = listOfNotNull(team, player).joinToString(" ")

private fun EventType.reachLabel() = when (this) {
    EventType.REACHED_BASE -> "출루"
    EventType.SINGLE -> "안타"
    EventType.DOUBLE -> "2루타"
    EventType.TRIPLE -> "3루타"
    EventType.WALK -> "볼넷 출루"
    EventType.HIT_BY_PITCH -> "사구 출루"
    EventType.REACHED_ON_ERROR -> "상대 실책으로 출루"
    EventType.DROPPED_THIRD_STRIKE_REACHED -> "낫아웃 출루"
    EventType.FIELDERS_CHOICE_REACHED -> "야수선택 출루"
    else -> null
}

private fun Int?.outLabel() = if (this == null) "아웃 미상" else if (this == 0) "무사" else "${this}사"

private fun Bases.canConfirmReach(batterId: String?): Boolean =
    known && listOfNotNull(first, second, third).any { batterId == null || it.id == batterId }

private fun Bases.runnerLabel(): String {
    if (!known) return "주자 미상"
    val occupied = listOfNotNull(
        1.takeIf { first != null }, 2.takeIf { second != null }, 3.takeIf { third != null },
    )
    return when {
        occupied.isEmpty() -> "주자 없음"
        occupied.size == 3 -> "만루 찬스"
        second != null || third != null -> "${occupied.joinToString("·")}루 득점권"
        else -> "주자 1루"
    }
}
