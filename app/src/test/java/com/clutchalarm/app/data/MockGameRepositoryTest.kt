package com.clutchalarm.app.data

import com.clutchalarm.app.domain.model.Bases
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameStatus
import com.clutchalarm.app.domain.model.GameTimeline
import com.clutchalarm.app.domain.model.InningHalf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MockGameRepositoryTest {
    private fun fixture() =
        requireNotNull(javaClass.getResource("/games/mock-game.json")).readText(Charsets.UTF_8)

    private fun timeline(json: String = fixture()): GameTimeline = runBlocking {
        requireNotNull(MockGameRepository { json }.getGame("mock-001"))
    }

    @Test
    fun listsAndFindsThePackagedGame() = runBlocking {
        val repository = MockGameRepository { fixture() }
        val game = requireNotNull(repository.getGame("mock-001"))
        assertEquals(listOf(game.initialState), repository.listGames())
        assertEquals(GameStatus.NOT_STARTED, game.initialState.status)
        assertNull(repository.getGame("missing"))
    }

    @Test
    fun sampleFollowsAConsistentFirstInning() {
        val game = timeline()
        val (start, single, double, firstOut, secondOut) = game.events
        val (thirdOut, change, tie, lead) = game.events.drop(5)

        assertEquals(EventType.GAME_STARTED, start.eventType)
        assertEquals(GameStatus.IN_PROGRESS, start.after.status)
        assertEquals(EventType.SINGLE, single.eventType)
        assertEquals(single.before.batter, single.after.bases.first)
        assertEquals(EventType.DOUBLE, double.eventType)
        assertEquals(single.after.bases.first, double.players[1])
        assertEquals(double.before.batter, double.after.bases.second)
        assertNull(double.after.bases.first)
        assertEquals(1, double.after.awayScore - double.before.awayScore)

        for ((index, out) in listOf(firstOut, secondOut, thirdOut).withIndex()) {
            assertEquals(EventType.BATTED_OUT, out.eventType)
            assertEquals(index + 1, out.after.outs)
            assertEquals(double.after.bases, out.after.bases)
            assertEquals(double.after.awayScore, out.after.awayScore)
        }

        assertEquals(EventType.HALF_INNING_CHANGED, change.eventType)
        assertEquals(InningHalf.TOP, change.before.half)
        assertEquals(InningHalf.BOTTOM, change.after.half)
        assertEquals(change.before.inning, change.after.inning)
        assertEquals(listOf(0, 0, 0), with(change.after) { listOf(balls, strikes, outs) })
        assertEquals(Bases(), change.after.bases)

        for (homer in listOf(tie, lead)) {
            assertEquals(EventType.HOME_RUN, homer.eventType)
            assertEquals(Bases(), homer.before.bases)
            assertEquals(Bases(), homer.after.bases)
            assertEquals(1, homer.after.homeScore - homer.before.homeScore)
            assertEquals(1, homer.after.awayScore)
            assertEquals(0, homer.after.outs)
        }
        assertEquals(tie.after.homeScore, tie.after.awayScore)
        assertEquals(2, lead.after.homeScore)
        assertEquals(GameStatus.IN_PROGRESS, lead.after.status)
    }

    @Test
    fun rejectsDuplicateIdsAndInvalidPlaybackOrder() {
        val json = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            timeline(json.replace("\"play-2\"", "\"play-1\""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            timeline(json.replace("\"offsetMs\": 6000", "\"offsetMs\": 1000"))
        }
    }

    @Test
    fun rejectsInvalidGameReferencesAndDecreasingScores() {
        val json = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            timeline(json.replaceFirst("\"gameId\": \"mock-001\"", "\"gameId\": \"unknown\""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            timeline(json.replaceFirst("\"awayScore\": 1", "\"awayScore\": 2"))
        }
    }

    @Test
    fun rejectsInvalidScoresAndCountsWhenParsing() {
        val json = fixture()
        for ((field, invalid) in mapOf(
            "homeScore" to -1, "awayScore" to -1, "balls" to 4,
            "strikes" to 3, "outs" to 4, "inning" to 0,
        )) {
            val original = if (field == "inning") 1 else 0
            assertThrows(field, IllegalArgumentException::class.java) {
                timeline(json.replaceFirst("\"$field\": $original", "\"$field\": $invalid"))
            }
        }
        assertThrows(SerializationException::class.java) { timeline("{") }
    }
}
