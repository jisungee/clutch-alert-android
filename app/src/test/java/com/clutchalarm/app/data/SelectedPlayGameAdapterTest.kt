package com.clutchalarm.app.data

import com.clutchalarm.app.domain.model.*
import com.clutchalarm.app.domain.notification.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SelectedPlayGameAdapterTest {
    private fun source() = requireNotNull(javaClass.getResource("/games/HANWHA_2026-03-28.json")).readText(Charsets.UTF_8)
    private fun timeline() = SelectedPlayGameAdapter.decode(source())

    @Test fun sparseStatesAndUnknownsArePreservedWithoutInventingPlays() {
        val timeline = timeline()
        val records = Json.parseToJsonElement(source()).jsonObject.getValue("events").jsonArray
        assertEquals(49, timeline.events.size)
        assertEquals(GameStatus.NOT_STARTED, timeline.initialState.status)
        assertEquals((0..48).map { it * 5000L }, timeline.events.map { it.offsetMs })
        timeline.events.forEachIndexed { index, event ->
            val record = records[index].jsonObject
            assertEquals(record.getValue("score").jsonObject.getValue("home").jsonPrimitive.int, event.after.homeScore)
            assertEquals(record.getValue("score").jsonObject.getValue("away").jsonPrimitive.int, event.after.awayScore)
            assertEquals(record.getValue("outs").jsonPrimitive.intOrNull, event.after.outs)
            assertNull(event.after.balls)
            assertNull(event.after.strikes)
            assertEquals(if (index == 0) timeline.initialState else timeline.events[index - 1].after, event.before)
        }
        val aggregate = timeline.events.single { it.eventId == "20" }
        assertNull(aggregate.playActor)
        assertNull(aggregate.after.outs)
        assertFalse(aggregate.after.bases.known)
        assertNull(aggregate.after.batter)
        assertEquals("강백호", timeline.events.single { it.eventId == "7" }.playActor?.name)
        assertEquals(9, timeline.events.last().after.awayScore)
        assertEquals(10, timeline.events.last().after.homeScore)
        assertEquals(GameStatus.FINISHED, timeline.events.last().after.status)
        assertTrue(timeline.events.single { it.eventId == "48" }.details.walkOff)
        assertEquals(GameStatus.FINISHED, timeline.events.single { it.eventId == "48" }.after.status)
    }

    private fun notifications(settings: NotificationPreferences): List<NotificationCandidate> {
        val engine = NotificationRuleEngine()
        var context = NotificationContext.Empty
        return timeline().events.mapNotNull {
            val decision = engine.evaluate(it, "HANWHA", context)
            context = decision.nextContext
            decision.candidate?.let(settings::select)
        }
    }

    @Test fun aggregateScoresReviewAndWalkoffUseExistingSubscriptionsOncePerRecord() {
        val all = notifications(NotificationPreferences(disabledTypes = emptySet()))
        assertEquals(all.size, all.map { it.eventId }.distinct().size)
        val aggregate = all.single { it.eventId == "20" }
        assertTrue(NotificationType.RUN_ALLOWED in aggregate.matchingTypes)
        assertTrue(NotificationType.BIG_INNING in aggregate.matchingTypes)
        assertTrue(aggregate.body.contains("공식 이닝 합계"))
        assertFalse(aggregate.body.contains("타점"))
        assertTrue(all.single { it.eventId == "7" }.body.contains("폭투로 페라자 득점"))
        assertFalse(all.single { it.eventId == "7" }.body.contains("강백호!"))
        // Raw metadata says "역전" but the confirmed score is 7:7. Score wins.
        val tie = all.single { it.eventId == "35" }
        assertEquals(NotificationType.HOME_RUN, tie.type)
        assertTrue(tie.body.contains("동점"))
        assertFalse(tie.body.contains("역전"))
        assertEquals(3, all.count { it.type == NotificationType.VIDEO_REVIEW })
        assertTrue(all.single { it.eventId == "48" }.body.contains("끝내기"))
        val onlyBig = notifications(NotificationPreferences(disabledTypes = NotificationType.entries.toSet() - NotificationType.BIG_INNING))
        assertEquals(listOf("20", "35", "48"), onlyBig.map { it.eventId })
        all.forEach { println("HANWHA_ALL | ${it.eventId} | ${it.type} | ${it.title} | ${it.body}") }
    }

    @Test fun observedLosingStreakStaysHiddenThroughBothTiesAndResumesAtWalkoff() {
        val hidden = notifications(NotificationPreferences(disabledTypes = emptySet(), hideWhenLosing = true))
        assertFalse(hidden.any { it.eventId.toInt() in 20..47 })
        assertEquals(listOf("48", "49"), hidden.filter { it.eventId.toInt() >= 20 }.map { it.eventId })
        val scoreOnly = notifications(NotificationPreferences(
            disabledTypes = NotificationType.entries.toSet() - NotificationType.RUN_SCORED, hideWhenLosing = true,
        ))
        assertEquals(NotificationType.RUN_SCORED, scoreOnly.single { it.eventId == "48" }.type)
        assertFalse(scoreOnly.any { it.eventId == "35" || it.eventId == "46" })
    }

    @Test fun rejectsNegativeDelayAndScoreDecreaseWithoutConfirmedReversal() {
        assertThrows(IllegalArgumentException::class.java) {
            SelectedPlayGameAdapter.decode(source().replaceFirst("\"delay_sec\": 5", "\"delay_sec\": -5"))
        }
        val root = Json.parseToJsonElement(source()).jsonObject
        val events = root.getValue("events").jsonArray.toMutableList()
        val review = events[46].jsonObject
        val score = JsonObject(mapOf("away" to JsonPrimitive(9), "home" to JsonPrimitive(8)))
        events[46] = JsonObject(review + ("score" to score))
        assertThrows(IllegalArgumentException::class.java) {
            SelectedPlayGameAdapter.decode(JsonObject(root + ("events" to JsonArray(events))).toString())
        }
        val details = review.getValue("details").jsonObject
        events[46] = JsonObject(review + ("score" to score) + ("details" to JsonObject(details + ("review_result" to JsonPrimitive("세이프→아웃")))))
        assertEquals(EventType.OFFICIAL_REVIEW_OVERTURNED,
            SelectedPlayGameAdapter.decode(JsonObject(root + ("events" to JsonArray(events))).toString()).events[46].eventType)
    }

    @Test fun catalogAddsOneGameAndKeepsAllEightExistingScenariosLoadable() = runBlocking {
        val repository = MockGameRepository(kotlinx.coroutines.Dispatchers.Unconfined, "test-catalog.json") { file ->
            requireNotNull(javaClass.getResource("/games/$file")).readText(Charsets.UTF_8)
        }
        val games = repository.listScenarios()
        assertEquals(18, games.size)
        games.forEach { assertTrue(requireNotNull(repository.getGame(it.state.gameId)).events.isNotEmpty()) }
    }
}
