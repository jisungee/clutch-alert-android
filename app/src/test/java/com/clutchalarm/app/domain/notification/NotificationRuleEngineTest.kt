package com.clutchalarm.app.domain.notification

import com.clutchalarm.app.domain.model.Bases
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameStatus
import com.clutchalarm.app.domain.model.InningHalf
import com.clutchalarm.app.domain.model.PlayDetails
import com.clutchalarm.app.domain.model.Player
import com.clutchalarm.app.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRuleEngineTest {
    private val engine = NotificationRuleEngine()
    private val batter = Player("batter", "김타자")
    private val runner = Player("runner", "이주자")

    @Test
    fun confirmsGameStartOnFirstPitchAndSupportsBothHalfStarts() {
        val before = state().copy(inning = 1, status = GameStatus.NOT_STARTED)
        val after = before.copy(status = GameStatus.IN_PROGRESS)
        assertNull(engine.evaluate(play(EventType.GAME_STARTED, before, after), "away").candidate)
        checkMessage(NotificationType.GAME_STARTED, play(EventType.FIRST_PITCH, before, after),
            "첫 투구", "1회초", "0 : 0")
        val start = play(EventType.HALF_INNING_STARTED, after)
        checkMessage(NotificationType.OFFENSE_STARTED, start, "원정 공격 시작", "1회초")
        checkMessage(NotificationType.DEFENSE_STARTED, start, "홈 수비 시작", "1회초", preferred = "home")
    }

    @Test
    fun formatsReachTypesWithBatterOutsAndScoringPosition() {
        val before = state(away = 2, home = 3).copy(outs = 2, bases = Bases(second = runner))
        val after = before.copy(bases = Bases(first = batter, second = runner))
        for ((type, wording) in mapOf(
            EventType.SINGLE to "안타", EventType.WALK to "볼넷", EventType.HIT_BY_PITCH to "사구",
            EventType.REACHED_ON_ERROR to "실책", EventType.DROPPED_THIRD_STRIKE_REACHED to "낫아웃",
            EventType.FIELDERS_CHOICE_REACHED to "야수선택",
        )) {
            checkMessage(NotificationType.REACHED_BASE, play(type, before, after),
                "김타자", wording, "2사", "1·2루 득점권", "2 : 3")
        }
        val bases = Bases(batter, runner, Player("third", "정주자"))
        checkMessage(NotificationType.REACHED_BASE, play(EventType.WALK, before, after.copy(bases = bases)),
            "만루 찬스")
    }

    @Test
    fun stolenBaseAndWildPitchDoNotCountAsBatterReaching() {
        val before = state().copy(bases = Bases(first = runner))
        val after = before.copy(bases = Bases(second = runner))
        for (type in listOf(EventType.STOLEN_BASE, EventType.WILD_PITCH)) {
            assertNull(engine.evaluate(play(type, before, after), "away").candidate)
        }
        assertEquals(NotificationType.OPPONENT_ON_BASE, engine.evaluate(play(EventType.SINGLE, before, before.copy(bases = Bases(batter))), "home").candidate?.type)
        assertNull(engine.evaluate(play(EventType.SINGLE, before, after), "away").candidate)
    }

    @Test
    fun distinguishesScoringTieComebackAndTakingLead() {
        val examples = listOf(
            Triple(state(2, 1) to state(3, 1), NotificationType.RUN_SCORED, "1점 득점"),
            Triple(state(2, 3) to state(3, 3), NotificationType.TIED, "동점"),
            Triple(state(2, 3) to state(4, 3), NotificationType.COMEBACK, "역전"),
            Triple(state(3, 3) to state(4, 3), NotificationType.RUN_SCORED, "리드"),
        )
        for ((states, expected, wording) in examples) {
            val (before, after) = states
            val event = play(EventType.SINGLE, before, after, details = PlayDetails(runsBattedIn = 1))
            val candidate = checkMessage(expected, event,
                "김타자", "적시타", wording, "${after.awayScore} : ${after.homeScore}")
            if (wording == "리드") assertFalse(candidate.body.contains("역전"))
        }
    }

    @Test
    fun distinguishesOpponentRunsTieAndComebackInPreferredScoreOrder() {
        for ((before, after, wording) in listOf(
            Triple(state(1, 3), state(2, 3), "1실점 허용"),
            Triple(state(2, 3), state(3, 3), "동점 허용"),
            Triple(state(2, 3), state(4, 3), "역전 허용"),
        )) {
            val event = play(EventType.RUN_SCORED, before, after)
            checkMessage(NotificationType.RUN_ALLOWED, event, wording,
                "${after.homeScore} : ${after.awayScore}", preferred = "home")
        }
    }

    @Test
    fun formatsEveryHomeRunSizeAndCombinesComebackAndWalkOff() {
        for ((runs, label) in listOf(1 to "솔로", 2 to "2점", 3 to "3점", 4 to "만루")) {
            val event = play(EventType.HOME_RUN, state(5, 1), state(5 + runs, 1))
            checkMessage(NotificationType.HOME_RUN, event, "$label 홈런", "김타자", "${5 + runs} : 1")
        }
        val comeback = play(EventType.HOME_RUN, state(2, 3), state(4, 3))
        assertEquals("🚨 역전 홈런",
            checkMessage(NotificationType.COMEBACK, comeback, "2점 홈런", "역전", "4 : 3").title)
        checkMessage(NotificationType.RUN_ALLOWED, comeback, "2점 홈런", "역전 허용", "3 : 4", preferred = "home")

        val tied = state(4, 4).copy(inning = 9, half = InningHalf.BOTTOM)
        val final = tied.copy(homeScore = 5, status = GameStatus.FINISHED)
        val walkOff = play(EventType.HOME_RUN, tied, final, details = PlayDetails(walkOff = true))
        assertEquals("🚨 끝내기 홈런", checkMessage(NotificationType.HOME_RUN, walkOff,
            "9회말", "김타자", "솔로 홈런", "끝내기 승리", "5 : 4", preferred = "home").title)
        val unconfirmed = walkOff.copy(details = PlayDetails())
        assertFalse(checkMessage(NotificationType.HOME_RUN, unconfirmed, "리드",
            preferred = "home").body.contains("끝내기"))
    }

    @Test
    fun combinesTheThirdRunWithThePlayAndStartsCountingAgainNextInning() {
        val session = NotificationSession()
        val token = session.begin("game")
        val scores = listOf(0, 1, 3, 4, 5)
        val candidates = scores.zipWithNext().mapIndexed { index, (before, after) ->
            session.onEvent(token, play(EventType.SINGLE, state(before), state(after), "run-$index",
                PlayDetails(runsBattedIn = after - before)), "away")
        }
        val fourth = requireNotNull(candidates[1])
        assertEquals(NotificationType.BIG_INNING, fourth.type)
        assertContains(fourth.body, "김타자", "적시타", "5회초", "3득점 빅이닝", "3 : 0")
        assertEquals(NotificationType.RUN_SCORED, candidates[3]?.type)
        assertFalse(requireNotNull(candidates[3]).body.contains("빅이닝"))
        val next = state(5).copy(inning = 6)
        val later = requireNotNull(session.onEvent(token,
            play(EventType.HOME_RUN, next, next.copy(awayScore = 9), "later"), "away"))
        assertEquals(NotificationType.HOME_RUN, later.type)
        assertContains(later.body, "6회초", "4득점 빅이닝", "만루 홈런", "9 : 0")
    }

    @Test
    fun officialReviewAndInjuryEventsUseOnlyConfirmedDetails() {
        val state = state(2, 1)
        checkMessage(NotificationType.VIDEO_REVIEW,
            play(EventType.OFFICIAL_REVIEW_STARTED, state, details = PlayDetails(reviewSubject = "1루 세이프 여부")),
            "5회초", "1루 세이프 여부", "공식 비디오 판독 시작")
        checkMessage(NotificationType.VIDEO_REVIEW, play(EventType.OFFICIAL_REVIEW_UPHELD, state), "원심 유지")
        checkMessage(NotificationType.VIDEO_REVIEW,
            play(EventType.OFFICIAL_REVIEW_OVERTURNED, state, details = PlayDetails(revisedCall = "세이프")),
            "세이프", "번복")
        assertNull(engine.evaluate(play(EventType.BROADCAST_REPLAY, state), "away").candidate)
        for ((type, wording) in mapOf(
            EventType.INJURY_SUSPECTED_STOPPAGE to "부상 의심",
            EventType.INJURY_MEDICAL_CHECK to "의료진 확인 중",
            EventType.INJURY_SUBSTITUTION to "부상으로 교체",
        )) {
            checkMessage(NotificationType.INJURY, play(type, state), "원정", "김타자", wording, "2 : 1")
        }
    }

    @Test
    fun combinesThirdOutAndFollowingTransitionForEitherTeam() {
        val before = state(2, 1).copy(outs = 2)
        val thirdOut = play(EventType.STRIKEOUT, before, before.copy(outs = 3), "third")
        val switch = play(EventType.HALF_INNING_CHANGED, thirdOut.after,
            thirdOut.after.copy(half = InningHalf.BOTTOM, outs = 0), "switch")
        for ((preferred, type, wording) in listOf(
            Triple("away", NotificationType.OFFENSE_ENDED, "공격 종료 · 수비 전환"),
            Triple("home", NotificationType.DEFENSE_ENDED, "수비 종료 · 공격 전환"),
        )) {
            val decision = engine.evaluate(thirdOut, preferred)
            assertEquals(type, decision.candidate?.type)
            assertContains(requireNotNull(decision.candidate).body, wording, "5회초")
            val transition = engine.evaluate(switch, preferred, decision.nextContext)
            assertNull(transition.candidate)
            val firstPitchOfHalf = play(EventType.HALF_INNING_STARTED, switch.after, id = "start")
            assertNull(engine.evaluate(firstPitchOfHalf, preferred, transition.nextContext).candidate)
        }
        checkMessage(NotificationType.OFFENSE_STARTED, switch, "5회말", "홈 공격 시작", preferred = "home")
    }

    @Test
    fun deduplicatesByGameAndEventAndRejectsStaleSessionTokens() {
        val deduplicator = NotificationDeduplicator()
        assertTrue(deduplicator.markIfNew("one", "same"))
        assertFalse(deduplicator.markIfNew("one", "same"))
        assertTrue(deduplicator.markIfNew("two", "same"))
        val session = NotificationSession()
        val event = play(EventType.HOME_RUN, state(1), state(2))
        val first = session.begin("game")
        val original = requireNotNull(session.onEvent(first, event, "away"))
        assertNull(session.onEvent(first, event, "away"))
        val second = session.begin("game")
        assertNull(session.onEvent(first, event, "away"))
        assertEquals(original.id, session.onEvent(second, event, "away")?.id)
        session.end(second)
        assertNull(session.onEvent(second, event.copy(eventId = "next"), "away"))
    }

    @Test
    fun appliesChangedPreferenceToNewEventsAndPreservesEqualOffsetOrder() {
        val session = NotificationSession()
        val token = session.begin("game")
        val first = play(EventType.HOME_RUN, state(1, 0), state(2, 0), "first")
        val second = play(EventType.HOME_RUN, state(2, 0), state(3, 0), "second")
        assertEquals(first.offsetMs, second.offsetMs)
        val one = requireNotNull(session.onEvent(token, first, "away"))
        assertNull(session.onEvent(token, first, "home"))
        val two = requireNotNull(session.onEvent(token, second, "home"))
        assertEquals(listOf("first", "second"), listOf(one.eventId, two.eventId))
        assertEquals(NotificationType.HOME_RUN, one.type)
        assertEquals(NotificationType.RUN_ALLOWED, two.type)
        assertContains(one.body, "2 : 0")
        assertContains(two.body, "0 : 3")
    }

    private fun checkMessage(
        expected: NotificationType,
        event: GameEvent,
        vararg fragments: String,
        preferred: String = "away",
    ): NotificationCandidate {
        val result = requireNotNull(engine.evaluate(event, preferred).candidate)
        assertEquals(expected, result.type)
        assertEquals(event.eventId, result.eventId)
        assertEquals(event.gameId, result.gameId)
        assertTrue(result.id.isNotBlank())
        assertContains(result.body, *fragments)
        return result
    }

    private fun assertContains(body: String, vararg fragments: String) {
        fragments.forEach { assertTrue("Missing '$it' in '$body'", body.contains(it)) }
    }

    private fun state(away: Int = 0, home: Int = 0) = GameState(
        "game", Team("home", "홈"), Team("away", "원정"), home, away,
        5, InningHalf.TOP, 0, 0, 0, Bases(), batter, null, GameStatus.IN_PROGRESS,
    )

    private fun play(
        type: EventType,
        before: GameState,
        after: GameState = before,
        id: String = "event",
        details: PlayDetails = PlayDetails(),
    ) = GameEvent(
        id, before.gameId, type, if (before.half == InningHalf.TOP) "away" else "home",
        listOf(batter), 500, before, after, details,
    )
}
