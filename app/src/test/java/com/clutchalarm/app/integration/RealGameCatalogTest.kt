package com.clutchalarm.app.integration

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.behavior.BehaviorEvent
import com.clutchalarm.app.behavior.BehaviorEventRecorder
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.domain.model.*
import com.clutchalarm.app.domain.notification.*
import com.clutchalarm.app.domain.simulation.PlaybackStatus
import com.clutchalarm.app.notification.*
import com.clutchalarm.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class RealGameCatalogTest {
    private data class Expected(val file: String, val events: Int, val away: Int, val home: Int, val sent: Int)
    private val expected = listOf(
        Expected("DOOSAN_2026-03-29.json", 39, 9, 6, 36),
        Expected("HANWHA_2026-03-28.json", 49, 9, 10, 48),
        Expected("KIA_2026-04-05.json", 29, 0, 3, 27),
        Expected("KIWOOM_2026-04-01.json", 34, 11, 2, 31),
        Expected("KT_2026-03-28.json", 38, 11, 7, 35),
        Expected("LG_2026-04-01.json", 31, 2, 7, 29),
        Expected("LOTTE_2026-03-28.json", 38, 6, 3, 35),
        Expected("NC_2026-03-28.json", 35, 0, 6, 33),
        Expected("SAMSUNG_2026-04-01.json", 36, 3, 13, 35),
        Expected("SSG_2026-03-28.json", 45, 6, 7, 43),
    )
    private fun bytes(file: String) = requireNotNull(javaClass.getResource("/games/$file")).readBytes()
    private fun info(file: String) = Json.parseToJsonElement(bytes(file).toString(Charsets.UTF_8)).jsonObject.getValue("game_info").jsonObject
    private fun repository(dispatcher: kotlinx.coroutines.CoroutineDispatcher, includeTestFixtures: Boolean = false) =
        MockGameRepository(dispatcher, if (includeTestFixtures) "test-catalog.json" else "catalog.json") { file ->
            if (!includeTestFixtures) assertFalse("Production must never read QA assets", file.startsWith("mock-") || file == "qa-catalog.json" || file == "pdf-snapshot-test.json")
            bytes(file).toString(Charsets.UTF_8)
        }

    @Test fun everyAppUsesTenAndTestOnlyFixturesPreserveAllOriginalBytes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val production = repository(dispatcher).listScenarios()
        assertEquals(10, production.size)
        assertTrue(production.all { it.scenario!!.preparedForTeamId != null })
        assertEquals(18, repository(dispatcher, includeTestFixtures = true).listScenarios().size)
        val hashes = Json.parseToJsonElement(requireNotNull(javaClass.getResource("/real-game-hashes.json")).readText()).jsonObject
        expected.forEach { fixture ->
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes(fixture.file)).joinToString("") { "%02x".format(it) }
            assertEquals(fixture.file, hashes.getValue(fixture.file).jsonPrimitive.content, actual)
        }
    }

    @Test fun recommendationsDateDurationAndInitialStateNeverRevealFinalScores() = runTest {
        val listings = repository(StandardTestDispatcher(testScheduler)).listScenarios()
        val state = GameListState.Loaded(listings.map { it.state }, listings.associate { it.state.gameId to it.scenario!! })
        for (team in KboTeams.all) {
            val recommended = state.recommendedGames(team.id).single()
            assertTrue(team.id in listOf(recommended.awayTeam.id, recommended.homeTeam.id))
            assertEquals(team.id, state.scenarios.getValue(recommended.gameId).preparedForTeamId)
            assertEquals(10, state.replayGames.size)
        }
        assertTrue(state.recommendedGames(null).isEmpty())
        expected.forEach { fixture ->
            val source = info(fixture.file)
            val listing = listings.single { it.state.gameId == source.getValue("game_id").jsonPrimitive.content }
            assertEquals(source.getValue("date").jsonPrimitive.content, listing.scenario!!.date)
            assertEquals((fixture.events - 1) * 5000L, listing.scenario.durationMs)
            val fixture = expected.single { info(it.file).getValue("game_id").jsonPrimitive.content == listing.state.gameId }
            assertEquals(info(fixture.file).getValue("date").jsonPrimitive.content, listing.scenario.date)
            assertEquals(fixture.file.substringBefore('_'), listing.scenario.preparedForTeamId)
            assertEquals(0, listing.state.awayScore)
            assertEquals(0, listing.state.homeScore)
            assertFalse(listing.scenario.description.contains("승리") || listing.scenario.description.contains("끝내기"))
        }
        assertEquals("약 4분 0초", replayDurationLabel(240_000))
    }

    @Test fun all374EventsFinishThroughTheExistingViewModelWithSavedTeamsAndExpectedNotifications() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var totalEvents = 0
        var totalSent = 0
        try {
            for (fixture in expected) {
                val teamId = fixture.file.substringBefore('_')
                val source = info(fixture.file)
                val gameId = source.getValue("game_id").jsonPrimitive.content
                val settings = InMemoryNotificationPreferencesRepository(NotificationPreferences(
                    preferredTeamId = teamId, onboardingCompleted = true, notificationSettingsSaved = true,
                ))
                val sent = mutableListOf<NotificationCandidate>()
                val behavior = mutableListOf<BehaviorEvent>()
                val repo = repository(StandardTestDispatcher(testScheduler))
                val model = GameViewModel(repo, testScheduler.timeSource,
                    NotificationDispatcher { sent.add(it); DeliveryStatus.SENT }, settings,
                    behaviorEvents = BehaviorEventRecorder { behavior.add(it) })
                try {
                    runCurrent()
                    model.showGames()
                    model.selectGame(gameId)
                    runCurrent()
                    val loaded = model.detail.value as GameDetailState.Loaded
                    assertEquals(teamId, loaded.preferredTeamId) // No temporary selection needed.
                    model.play()
                    advanceTimeBy(240_001)
                    runCurrent()
                    val detail = model.detail.value as GameDetailState.Loaded
                    assertEquals(PlaybackStatus.COMPLETED, detail.playback.status)
                    assertEquals(fixture.events, detail.playback.processedEventCount)
                    assertEquals(fixture.away, detail.playback.gameState.awayScore)
                    assertEquals(fixture.home, detail.playback.gameState.homeScore)
                    assertEquals(GameStatus.FINISHED, detail.playback.gameState.status)
                    assertEquals(fixture.sent, sent.size)
                    assertEquals(sent.size, sent.map { it.eventId }.toSet().size)
                    assertEquals(teamId, settings.load().preferredTeamId)
                    assertTrue(behavior.isEmpty()) // Direct list entry and playback are not notification opens.
                    val timeline = requireNotNull(repo.getGame(gameId))
                    emitReport(fixture, teamId, timeline, sent)
                    totalEvents += detail.playback.processedEventCount
                    totalSent += sent.size
                } finally {
                    model.viewModelScope.cancel()
                    runCurrent()
                }
            }
        } finally { Dispatchers.resetMain() }
        assertEquals(374, totalEvents)
        assertEquals(352, totalSent)
    }

    @Test fun allSubscriptionsDedupAndLosingHistoryRemainValidForBothTeamsOfAllGames() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler))
        var allOnCount = 0
        expected.forEach { fixture ->
            val timeline = requireNotNull(repo.getGame(info(fixture.file).getValue("game_id").jsonPrimitive.content))
            for (teamId in listOf(timeline.initialState.awayTeam.id, timeline.initialState.homeTeam.id)) {
                for (hidden in listOf(false, true)) {
                    val session = NotificationSession()
                    val token = session.begin(timeline.initialState.gameId)
                    var wasBehind = false
                    val bigHalves = mutableSetOf<Pair<Int, InningHalf>>()
                    timeline.events.forEach { event ->
                        val before = event.before
                        val after = event.after
                        val home = teamId == after.homeTeam.id
                        if ((if (home) before.homeScore - before.awayScore else before.awayScore - before.homeScore) < 0) wasBehind = true
                        val diff = if (home) after.homeScore - after.awayScore else after.awayScore - after.homeScore
                        if (diff < 0) wasBehind = true else if (diff > 0) wasBehind = false
                        val candidate = session.onEvent(token, event, teamId)
                        assertNull(session.onEvent(token, event, teamId))
                        val settings = NotificationPreferences(disabledTypes = emptySet(), hideWhenLosing = hidden)
                        if (!hidden && teamId == fixture.file.substringBefore('_') && candidate == null) {
                            // Third-out alerts already announced the next half. Do not send that start twice.
                            val mergedStarts = mapOf("DOOSAN" to "9", "KIA" to "6", "KIWOOM" to "16", "LG" to "4", "LOTTE" to "22", "SAMSUNG" to "4")
                            assertEquals(mergedStarts[teamId], event.eventId)
                            assertEquals(3, event.before.outs)
                            assertEquals(EventType.HALF_INNING_CHANGED, event.eventType)
                        }
                        val sent = candidate?.let(settings::select)
                        if (hidden && wasBehind) assertNull(sent)
                        if (!hidden && teamId == fixture.file.substringBefore('_') && sent != null) allOnCount++
                        if (candidate != null) {
                            if (NotificationType.BIG_INNING in candidate.matchingTypes) assertTrue(bigHalves.add(before.inning to before.half))
                            for (toggle in configurableNotificationTypes) {
                                val only = NotificationPreferences(NotificationType.entries.toSet().filterNot { it.toggleType() == toggle }.toSet())
                                assertEquals(candidate.matchingTypes.any { it.toggleType() == toggle }, only.select(candidate) != null)
                            }
                        }
                    }
                }
            }
        }
        assertEquals(368, allOnCount)
    }

    @Test fun bothIncorrectComebackDescriptionsProduceTiedHomerNotifications() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler))
        for ((file, eventId) in listOf("DOOSAN_2026-03-29.json" to "31", "HANWHA_2026-03-28.json" to "35")) {
            val timeline = requireNotNull(repo.getGame(info(file).getValue("game_id").jsonPrimitive.content))
            val session = NotificationSession()
            val token = session.begin(timeline.initialState.gameId)
            timeline.events.forEach { event ->
                val candidate = session.onEvent(token, event, file.substringBefore('_'))
                if (event.eventId == eventId) {
                    assertTrue(event.sourceSummary!!.contains("역전"))
                    assertEquals(event.after.homeScore, event.after.awayScore)
                    val alert = requireNotNull(candidate)
                    assertFalse(alert.title.contains("역전"))
                    assertFalse(alert.body.contains("역전"))
                    assertTrue(alert.body.contains("동점"))
                    assertEquals(NotificationType.HOME_RUN, alert.type)
                }
            }
        }
    }

    @Test fun everyObservedPostStateAndUnknownIsPreservedAcrossAll374Records() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler))
        var checked = 0
        var aggregates = 0
        for (fixture in expected) {
            val raw = Json.parseToJsonElement(bytes(fixture.file).toString(Charsets.UTF_8)).jsonObject
            val timeline = requireNotNull(repo.getGame(raw.getValue("game_info").jsonObject.getValue("game_id").jsonPrimitive.content))
            var offset = 0L
            raw.getValue("events").jsonArray.forEachIndexed { index, element ->
                val record = element.jsonObject
                val event = timeline.events[index]
                val after = event.after
                offset += record.getValue("delay_sec").jsonPrimitive.long * 1000
                assertEquals(offset, event.offsetMs)
                assertEquals(if (index == 0) timeline.initialState else timeline.events[index - 1].after, event.before)
                assertEquals(record.getValue("score").jsonObject.getValue("away").jsonPrimitive.int, after.awayScore)
                assertEquals(record.getValue("score").jsonObject.getValue("home").jsonPrimitive.int, after.homeScore)
                assertEquals(record.getValue("outs").jsonPrimitive.intOrNull, after.outs)
                assertNull(after.balls); assertNull(after.strikes)
                val details = record.getValue("details").jsonObject
                assertEquals(details.getValue("summary").jsonPrimitive.content, event.sourceSummary)
                assertEquals(record.getValue("timestamp").jsonPrimitive.contentOrNull, event.sourceTime)
                val known = details.getValue("bases_state_known").jsonPrimitive.boolean
                assertEquals(known, after.bases.known)
                for ((key, player) in listOf("first" to after.bases.first, "second" to after.bases.second, "third" to after.bases.third)) {
                    assertEquals(if (known) record.getValue("bases").jsonObject.getValue(key).jsonPrimitive.contentOrNull else null, player?.name)
                }
                assertEquals(record.getValue("current_batter").jsonObject.getValue("name").jsonPrimitive.contentOrNull, after.batter?.name)
                if (details.getValue("record_granularity").jsonPrimitive.content == "half_inning_remaining") {
                    aggregates++
                    assertNull(event.playActor)
                    assertNull(event.details.runsBattedIn)
                    assertEquals(EventType.RUN_SCORED, event.eventType)
                }
                checked++
            }
        }
        assertEquals(374, checked)
        assertEquals(40, aggregates)
    }

    private fun emitReport(fixture: Expected, teamId: String, timeline: GameTimeline, sent: List<NotificationCandidate>) {
        val report = buildJsonObject {
            put("file", fixture.file); put("team", teamId); put("events", fixture.events)
            put("awayScore", fixture.away); put("homeScore", fixture.home)
            put("durationMs", timeline.events.last().offsetMs); put("sent", sent.size)
            put("types", buildJsonObject { sent.groupingBy { it.type.name }.eachCount().toSortedMap().forEach { (k, v) -> put(k, v) } })
            put("subscriptions", buildJsonObject {
                configurableNotificationTypes.forEach { toggle -> put(toggle.name, sent.count { candidate -> candidate.matchingTypes.any { it.toggleType() == toggle } }) }
            })
            put("notifications", buildJsonArray { sent.forEach { c -> add(buildJsonObject {
                put("eventId", c.eventId); put("type", c.type.name); put("title", c.title); put("body", c.body)
            }) } })
        }
        // Gradle on Windows may replace emoji in stdout; ASCII escapes preserve the exact JSON.
        println("REAL_GAME_REPORT " + report.toString().map { if (it.code > 127) "\\u%04x".format(it.code) else it.toString() }.joinToString(""))
    }
}
