package com.clutchalarm.app.data

import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameStatus
import com.clutchalarm.app.domain.notification.NotificationRuleEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SnapshotGameAdapterTest {
    private fun source() = requireNotNull(javaClass.getResource("/games/pdf-snapshot-test.json")).readText(Charsets.UTF_8)

    @Test fun explicitInitialAndPostPlayStatesAreContinuousAndActorsAreSeparate() = runBlocking {
        val timeline = requireNotNull(MockGameRepository { source() }.getGame("pdf-snapshot-test"))
        assertTrue(timeline.notificationsEnabled)
        assertEquals(GameStatus.NOT_STARTED, timeline.initialState.status)
        assertEquals((0..12).map { it * 1000L }, timeline.events.map { it.offsetMs })
        assertEquals(timeline.initialState, timeline.events.first().before)
        timeline.events.zipWithNext().forEach { (a, b) -> assertEquals(a.after, b.before) }
        val reach = timeline.events.single { it.eventType == EventType.SINGLE }
        assertEquals("박찬호", reach.playActor?.name)
        assertEquals("김도영", reach.after.batter?.name)
        assertEquals(reach.playActor, reach.after.bases.first)
        assertNull(reach.after.balls)
        assertNull(reach.after.strikes)
        assertTrue(NotificationRuleEngine().evaluate(reach, "KIA").candidate!!.body.contains("박찬호"))
        val review = timeline.events.single { it.eventType == EventType.OFFICIAL_REVIEW_OVERTURNED }
        assertEquals(3, review.before.homeScore)
        assertEquals(2, review.after.homeScore)
        assertEquals(1, review.after.outs)
        assertNull(review.after.bases.first)
        assertEquals(GameStatus.FINISHED, timeline.events.last().after.status)
    }

    @Test fun rejectsNegativeAndOverflowingDelays() {
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotGameAdapter.decode(source().replaceFirst("\"delay_sec\": 1", "\"delay_sec\": -1"))
        }
        assertThrows(ArithmeticException::class.java) {
            SnapshotGameAdapter.decode(source().replaceFirst("\"delay_sec\": 1", "\"delay_sec\": 9223372036854775807"))
        }
    }
}
