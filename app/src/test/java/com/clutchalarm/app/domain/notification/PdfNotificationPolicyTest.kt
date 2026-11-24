package com.clutchalarm.app.domain.notification

import com.clutchalarm.app.domain.model.*
import com.clutchalarm.app.domain.notification.NotificationType.*
import org.junit.Assert.*
import org.junit.Test

class PdfNotificationPolicyTest {
    private val batter = Player("actor", "방금친선수")
    private fun state(own: Int, opponent: Int = 2) = GameState(
        "policy", Team("home", "상대"), Team("away", "우리"), opponent, own,
        1, InningHalf.TOP, null, null, 0, Bases(), Player("next", "다음타자"), null, GameStatus.IN_PROGRESS,
    )
    private fun play(id: String, before: GameState, after: GameState, type: EventType = EventType.SINGLE) =
        GameEvent(id, "policy", type, "away", listOf(batter), 0, before, after, playActor = batter)
    private fun settings(vararg types: NotificationType) = NotificationPreferences(
        NotificationType.entries.toSet() - types.toSet(), hideWhenLosing = true,
    )

    @Test fun tiedStaysHiddenAndLeadingPlayResumesWithScoreToggleOnly() {
        val preferences = settings(RUN_SCORED, REACHED_BASE)
        val session = NotificationSession()
        val token = session.begin("policy")
        val tied = state(2)
        val sequence = listOf(
            play("tie", state(0), tied),
            play("reach", tied, tied.copy(bases = Bases(first = batter))),
            play("ahead", tied.copy(bases = Bases(first = batter)), state(3)),
        )
        val candidates = sequence.map { session.onEvent(token, it, "away")!! }
        assertTrue(candidates[0].hiddenUntilLead)
        assertTrue(candidates[1].hiddenUntilLead)
        assertFalse(candidates[2].hiddenUntilLead)
        assertTrue(candidates[2].recoveredLead)
        assertEquals(COMEBACK, candidates[2].type) // 2:2 -> 3:2 is a comeback after a losing streak.
        val sent = candidates.mapNotNull(preferences::select)
        assertEquals(listOf("ahead"), sent.map { it.eventId })
        assertEquals(RUN_SCORED, sent.single().type)
        assertEquals(RUN_SCORED.title, sent.single().title)
        assertNull(session.onEvent(token, sequence[0], "away"))
    }

    @Test fun resumingHomerUsesEnabledTitleAndTypeEvenWhenComebackIsOff() {
        val combinations = listOf(
            Triple(setOf(HOME_RUN), HOME_RUN, HOME_RUN.title),
            Triple(setOf(RUN_SCORED), RUN_SCORED, RUN_SCORED.title),
            Triple(setOf(COMEBACK), COMEBACK, "🚨 역전"),
            Triple(setOf(HOME_RUN, RUN_SCORED, COMEBACK), COMEBACK, "🚨 역전 홈런"),
        )
        for ((enabled, type, title) in combinations) {
            val session = NotificationSession()
            val token = session.begin("policy")
            val preferences = settings(*enabled.toTypedArray())
            val tie = session.onEvent(token, play("tie", state(0), state(2)), "away")!!
            assertNull(preferences.select(tie))
            val event = play("homer", state(2), state(3), EventType.HOME_RUN)
            val candidate = session.onEvent(token, event, "away")!!
            val selected = preferences.select(candidate)!!
            assertFalse(selected.hiddenUntilLead)
            assertEquals(type, selected.type)
            assertEquals(title, selected.title)
            assertTrue(selected.body.contains("방금친선수"))
            assertFalse(selected.body.contains("다음타자"))
            // Hiding was cleared by the score, independently of the comeback toggle.
            val next = play("later", state(3), state(4), EventType.HOME_RUN)
            assertFalse(session.onEvent(token, next, "away")!!.hiddenUntilLead)
        }
    }

    @Test fun losingOrTiedGameEndIsHiddenButFreshTiedGameIsNot() {
        for (finalScore in listOf(0, 2)) {
            val session = NotificationSession()
            val token = session.begin("policy")
            val prefs = settings(GAME_ENDED)
            val out = play("out", state(0), state(finalScore), EventType.BATTED_OUT)
            session.onEvent(token, out, "away") // even a play with no notification must update the streak
            val end = play("end", state(finalScore), state(finalScore).copy(status = GameStatus.FINISHED), EventType.GAME_ENDED)
            val candidate = session.onEvent(token, end, "away")!!
            assertTrue(candidate.hiddenUntilLead)
            assertNull(prefs.select(candidate))
            assertNotNull(prefs.copy(hideWhenLosing = false).select(candidate))
        }
        val fresh = NotificationSession()
        val token = fresh.begin("policy")
        val end = play("end", state(0, 0), state(0, 0).copy(status = GameStatus.FINISHED), EventType.GAME_ENDED)
        assertNotNull(settings(GAME_ENDED).select(fresh.onEvent(token, end, "away")!!))
    }

    @Test fun reviewCorrectionDoesNotGenerateRunsOrRepeatBigInning() {
        val session = NotificationSession()
        val token = session.begin("policy")
        val one = play("one", state(0, 0), state(1, 0))
        val two = play("two", state(1, 0), state(2, 0))
        val three = play("three", state(2, 0), state(3, 0))
        assertFalse(session.onEvent(token, one, "away")!!.matchingTypes.contains(BIG_INNING))
        assertFalse(session.onEvent(token, two, "away")!!.matchingTypes.contains(BIG_INNING))
        assertTrue(session.onEvent(token, three, "away")!!.matchingTypes.contains(BIG_INNING))
        val correction = play("review", state(3, 0), state(2, 0), EventType.OFFICIAL_REVIEW_OVERTURNED)
        val corrected = session.onEvent(token, correction, "away")!!
        assertEquals(setOf(VIDEO_REVIEW), corrected.matchingTypes)
        val repeated = play("again", state(2, 0), state(3, 0))
        assertFalse(session.onEvent(token, repeated, "away")!!.matchingTypes.contains(BIG_INNING))
        val next = play("next", state(3, 0).copy(inning = 2), state(6, 0).copy(inning = 2))
        assertTrue(session.onEvent(token, next, "away")!!.matchingTypes.contains(BIG_INNING))
        assertThrows(IllegalArgumentException::class.java) {
            play("bad", state(3, 0), state(2, 0))
        }
    }

    @Test fun defaultOpponentToggleIsIndependentAndInningAndLeadAliasesShareToggles() {
        val preferences = NotificationPreferences()
        assertFalse(preferences.isEnabled(OPPONENT_ON_BASE))
        assertTrue(preferences.isEnabled(REACHED_BASE))
        assertEquals(16, configurableNotificationTypes.size)
        val changed = preferences.withEnabled(OPPONENT_ON_BASE, true).withEnabled(REACHED_BASE, false)
        assertTrue(changed.isEnabled(OPPONENT_ON_BASE))
        assertFalse(changed.isEnabled(REACHED_BASE))
        assertFalse(changed.withEnabled(COMEBACK, false).isEnabled(TIED))
        assertFalse(changed.withEnabled(OFFENSE_STARTED, false).isEnabled(DEFENSE_ENDED))
    }
}
