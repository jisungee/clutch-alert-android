package com.clutchalarm.app.integration

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameStatus
import com.clutchalarm.app.domain.model.InningHalf
import com.clutchalarm.app.domain.notification.NotificationCandidate
import com.clutchalarm.app.domain.notification.NotificationPreferences
import com.clutchalarm.app.domain.notification.NotificationPreferencesRepository
import com.clutchalarm.app.domain.notification.NotificationType
import com.clutchalarm.app.domain.notification.NotificationType.*
import com.clutchalarm.app.domain.simulation.PlaybackStatus
import com.clutchalarm.app.domain.simulation.PlaybackSpeed
import com.clutchalarm.app.notification.DeliveryStatus
import com.clutchalarm.app.notification.NotificationDispatcher
import com.clutchalarm.app.notification.ActiveGameNotification
import com.clutchalarm.app.notification.NotificationIds
import com.clutchalarm.app.notification.NotificationRetention
import com.clutchalarm.app.ui.GameDetailState
import com.clutchalarm.app.ui.GameListState
import com.clutchalarm.app.ui.GameViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockScenarioIntegrationTest {
    @Test
    fun comebackHomerToggleMatrixDispatchesAtMostOnePerPlay() = runTest {
        val three = listOf(HOME_RUN, RUN_SCORED, COMEBACK)
        for (mask in 0..7) {
            val enabled = three.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val settings = com.clutchalarm.app.domain.notification.InMemoryNotificationPreferencesRepository()
            settings.update { NotificationPreferences(NotificationType.entries.toSet() - enabled, onboardingCompleted = true) }
            withViewModel(preferencesRepository = settings) { viewModel, dispatches ->
                viewModel.selectGame("pdf-snapshot-test")
                runCurrent()
                viewModel.selectPreferredTeam("KIA")
                viewModel.play()
                step(30_000)
                val homer = dispatches.filter { it.eventId == "8" }
                assertEquals("mask=$mask", if (mask == 0) 0 else 1, homer.size)
                homer.singleOrNull()?.let {
                    assertTrue(it.body.contains("김도영"))
                    assertFalse(it.body.contains("최형우"))
                    assertEquals(setOf(HOME_RUN, RUN_SCORED, COMEBACK), it.matchingTypes)
                    val expectedType = when {
                        COMEBACK in enabled -> COMEBACK
                        HOME_RUN in enabled -> HOME_RUN
                        else -> RUN_SCORED
                    }
                    val expectedTitle = when {
                        COMEBACK in enabled && HOME_RUN in enabled -> "🚨 역전 홈런"
                        COMEBACK in enabled -> "🚨 역전"
                        else -> expectedType.title
                    }
                    assertEquals(expectedType, it.type)
                    assertEquals(expectedTitle, it.title)
                }
                assertEquals(dispatches.size, dispatches.map { it.eventId }.distinct().size)
                assertEquals(PlaybackStatus.COMPLETED, viewModel.loaded().playback.status)
            }
        }
        val settings = com.clutchalarm.app.domain.notification.InMemoryNotificationPreferencesRepository()
        settings.update { NotificationPreferences(emptySet(), onboardingCompleted = true) }
        withViewModel(preferencesRepository = settings) { viewModel, dispatches ->
            viewModel.selectGame("pdf-snapshot-test")
            runCurrent()
            viewModel.selectPreferredTeam("KIA")
            viewModel.play()
            step(30_000)
            assertEquals(1, dispatches.count { it.eventId == "8" })
            assertEquals(dispatches.size, dispatches.map { it.eventId }.distinct().size)
        }
    }

    @Test
    fun opponentOnBaseHasOneIndependentDefaultOffToggle() = runTest {
        withViewModel { viewModel, dispatches ->
            viewModel.disableAllNotifications()
            viewModel.saveSettings()
            runCurrent()
            viewModel.setNotificationEnabled(REACHED_BASE, true)
            viewModel.saveSettings()
            runCurrent()
            viewModel.selectGame("mock-001")
            runCurrent()
            viewModel.selectPreferredTeam("seoul")
            viewModel.play()
            step(3_001)
            assertFalse(dispatches.any { it.eventId == "play-2" })
            viewModel.setNotificationEnabled(REACHED_BASE, false)
            viewModel.saveSettings()
            runCurrent()
            viewModel.setNotificationEnabled(OPPONENT_ON_BASE, true)
            viewModel.saveSettings()
            runCurrent()
            viewModel.play()
            step(3_001)
            assertEquals(1, dispatches.count { it.eventId == "play-2" })
        }
    }

    @Test
    fun completeSourceGameAndAllLegacyGamesOpenAndFinish() = runTest {
        withViewModel { viewModel, dispatches ->
            val games = viewModel.games.value as GameListState.Loaded
            assertEquals(8, games.games.size)
            for (game in games.games) {
                viewModel.selectGame(game.gameId)
                runCurrent()
                assertTrue(viewModel.loaded().notificationsEnabled)
                viewModel.selectPreferredTeam(game.homeTeam.id)
                viewModel.play()
                step(60_001)
                assertEquals(game.gameId, PlaybackStatus.COMPLETED, viewModel.loaded().playback.status)
                if (game.gameId == "pdf-snapshot-test") {
                    assertEquals(GameStatus.FINISHED, viewModel.loaded().playback.gameState.status)
                    assertEquals(2, viewModel.loaded().playback.gameState.homeScore)
                    assertEquals(13, viewModel.loaded().playback.processedEventCount)
                    assertNull(viewModel.loaded().playback.gameState.balls)
                    assertTrue(dispatches.any { it.gameId == game.gameId && it.type == GAME_ENDED })
                }
                viewModel.reset()
                runCurrent()
                assertEquals(PlaybackStatus.IDLE, viewModel.loaded().playback.status)
                viewModel.closeGame()
            }
        }
    }

    @Test
    fun settingsReturnsToItsOriginAndAllDisabledDoesNotStopPlayback() = runTest {
        withViewModel { viewModel, dispatches ->
            viewModel.showNotificationSettings(true)
            assertTrue(viewModel.showNotificationSettings.value)
            assertNull(viewModel.detail.value)
            viewModel.disableAllNotifications()
            viewModel.saveSettings()
            runCurrent()
            assertEquals(0, viewModel.notificationSettings.value.preferences?.enabledCount)
            viewModel.showNotificationSettings(false)
            assertFalse(viewModel.showNotificationSettings.value)
            assertNull(viewModel.detail.value)

            viewModel.selectGame("mock-special")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.setPlaybackSpeed(PlaybackSpeed.TEN_TIMES)
            viewModel.play()
            step(100)
            viewModel.showNotificationSettings(true)
            step(1_400)
            assertTrue(viewModel.showNotificationSettings.value)
            viewModel.showNotificationSettings(false)
            assertFalse(viewModel.showNotificationSettings.value)
            val detail = viewModel.loaded()
            assertEquals("mock-special", detail.playback.gameState.gameId)
            assertEquals("busan", detail.preferredTeamId)
            assertEquals(PlaybackSpeed.TEN_TIMES, detail.playback.speed)
            assertEquals(PlaybackStatus.COMPLETED, detail.playback.status)
            assertEquals(5, detail.notifications.size)
            assertTrue(detail.notifications.all { it.status == DeliveryStatus.FILTERED })
            assertTrue(detail.sentNotifications.isEmpty())
            assertTrue(dispatches.isEmpty())

            viewModel.setNotificationEnabled(VIDEO_REVIEW, true)

            viewModel.saveSettings()
            runCurrent()
            assertEquals(1, viewModel.notificationSettings.value.preferences?.enabledCount)
            viewModel.enableAllNotifications()
            viewModel.saveSettings()
            runCurrent()
            assertEquals(16, viewModel.notificationSettings.value.preferences?.enabledCount)
            assertTrue(dispatches.isEmpty())
        }
    }

    @Test
    fun changesDuringPlaybackApplyToFutureEventsWithoutReissuingSkippedOnes() = runTest {
        withViewModel { viewModel, dispatches ->
            viewModel.selectGame("mock-special")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.play()
            step(0)
            assertEquals(listOf("review-start"), dispatches.map { it.eventId })

            viewModel.setNotificationEnabled(VIDEO_REVIEW, false)

            viewModel.saveSettings()
            viewModel.pause()
            step(10_000)
            viewModel.resume()
            step(6_000)
            assertEquals(listOf("review-start", "injury-suspected"), dispatches.map { it.eventId })
            val skipped = viewModel.loaded().notifications.single { it.candidate.eventId == "review-result" }
            assertEquals(DeliveryStatus.FILTERED, skipped.status)

            viewModel.setNotificationEnabled(VIDEO_REVIEW, true)

            viewModel.saveSettings()
            step(9_000)
            assertEquals(listOf("review-start", "injury-suspected", "medical-check",
                "injury-substitution"), dispatches.map { it.eventId })
            assertEquals(PlaybackStatus.COMPLETED, viewModel.loaded().playback.status)
        }
    }

    @Test
    fun combinedAlertsMatchAnyEnabledCodeAndSettingsFollowGameChanges() = runTest {
        withViewModel { viewModel, dispatches ->
            viewModel.setNotificationEnabled(HOME_RUN, false)
            viewModel.saveSettings()
            runCurrent()
            viewModel.selectGame("mock-close")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.play()
            step(14_000)
            val comeback = dispatches.single { it.eventId == "comeback" }
            assertEquals(COMEBACK, comeback.type)
            assertTrue("2점 홈런" in comeback.body)

            viewModel.setNotificationEnabled(COMEBACK, false)

            viewModel.saveSettings()
            runCurrent()
            viewModel.setNotificationEnabled(HOME_RUN, true)
            viewModel.saveSettings()
            runCurrent()
            viewModel.selectGame("mock-home-runs")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.play()
            step(3_000)
            assertEquals(1, dispatches.count { it.eventId == "two-run" })
            assertEquals(HOME_RUN, dispatches.single { it.eventId == "solo" }.type)
            assertEquals(DeliveryStatus.SENT,
                viewModel.loaded().notifications.single { it.candidate.eventId == "two-run" }.status)
        }
    }

    @Test
    fun failedSaveKeepsTheLastCommittedPreference() = runTest {
        val disabled = NotificationPreferences(setOf(VIDEO_REVIEW), onboardingCompleted = true)
        val repository = object : NotificationPreferencesRepository {
            override suspend fun load() = disabled
            override suspend fun update(
                transform: (NotificationPreferences) -> NotificationPreferences,
            ): NotificationPreferences = throw IOException("disk full")
        }
        withViewModel(preferencesRepository = repository) { viewModel, dispatches ->
            viewModel.setNotificationEnabled(VIDEO_REVIEW, true)
            viewModel.saveSettings()
            runCurrent()
            assertEquals(disabled, viewModel.notificationSettings.value.preferences)
            assertTrue(viewModel.notificationSettings.value.error)
            viewModel.selectGame("mock-special")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.play()
            step(0)
            assertTrue(dispatches.isEmpty())
            assertEquals(DeliveryStatus.FILTERED, viewModel.loaded().notifications.single().status)
        }
    }

    @Test
    fun tenTimesReplaysStillApplyTheTwelveNotificationLimit() = runTest {
        val retention = NotificationRetention()
        val ids = NotificationIds()
        val active = mutableListOf<ActiveGameNotification>()
        withViewModel(onDispatch = { candidate ->
            val now = testScheduler.currentTime
            val removals = requireNotNull(retention.makeRoom(active, candidate.important, now))
            for (address in removals) {
                active.removeAll { it.address == address }
                retention.cancelled(address, now)
            }
            val post = ActiveGameNotification(ids.next(), now, candidate.important)
            active.add(post)
            retention.submitted(post, now)
            assertTrue(active.size <= 12)
        }) { viewModel, dispatches ->
            viewModel.selectGame("mock-special")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.setPlaybackSpeed(PlaybackSpeed.TEN_TIMES)
            repeat(6) {
                viewModel.play()
                step(1_500)
                assertEquals(PlaybackStatus.COMPLETED, viewModel.loaded().playback.status)
                assertEquals(PlaybackSpeed.TEN_TIMES, viewModel.loaded().playback.speed)
            }
            assertEquals(30, dispatches.size)
            assertEquals(12, active.size)

            viewModel.setNotificationEnabled(VIDEO_REVIEW, false)

            viewModel.saveSettings()
            runCurrent()
            repeat(2) {
                viewModel.play()
                step(1_500)
            }
            assertEquals(36, dispatches.size)
            assertEquals(12, active.size)
        }
    }

    @Test
    fun acceleratedPlaybackKeepsAlertOrderAndNewGamesStartAtNormalSpeed() = runTest {
        withViewModel { viewModel, dispatches ->
            viewModel.selectGame("mock-special")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.play()
            step(100)
            assertEquals(listOf("review-start"), dispatches.map { it.eventId })
            viewModel.setPlaybackSpeed(PlaybackSpeed.TEN_TIMES)
            step(1_490)
            assertEquals(
                listOf("review-start", "review-result", "injury-suspected",
                    "medical-check", "injury-substitution"),
                dispatches.map { it.eventId },
            )
            assertEquals(PlaybackStatus.COMPLETED, viewModel.loaded().playback.status)
            viewModel.selectGame("mock-001")
            runCurrent()
            assertEquals(PlaybackSpeed.NORMAL, viewModel.loaded().playback.speed)
        }
    }

    @Test
    fun catalogAndAllSevenTimelinesAreConsistent() = runTest {
        val repository = repository()
        val listings = repository.listScenarios().filter { it.state.gameId != "pdf-snapshot-test" }
        assertEquals(
            listOf("mock-001", "mock-close", "mock-home-runs", "mock-big-inning",
                "mock-walkoff-hit", "mock-walkoff-homer", "mock-special"),
            listings.map { it.state.gameId },
        )
        assertTrue(listings.all {
            it.scenario?.name?.startsWith("모의 경기") == true && it.scenario.description.isNotBlank()
        })
        for (listing in listings) {
            val timeline = requireNotNull(repository.getGame(listing.state.gameId))
            assertEquals(listing.state, timeline.initialState)
            assertTrue(timeline.events.isNotEmpty())
            for (event in timeline.events) {
                val before = event.before
                val after = event.after
                val homeRuns = after.homeScore - before.homeScore
                val awayRuns = after.awayScore - before.awayScore
                val runs = homeRuns + awayRuns
                assertEquals(0, if (before.half == InningHalf.TOP) homeRuns else awayRuns)
                assertTrue(event.eventId, (event.details.runsBattedIn ?: 0) <= runs)
                if (event.eventType == EventType.HOME_RUN) {
                    assertEquals(1 + listOfNotNull(before.bases.first, before.bases.second, before.bases.third).size, runs)
                    assertEquals(null, after.bases.first)
                    assertEquals(null, after.bases.second)
                    assertEquals(null, after.bases.third)
                }
                if (event.details.walkOff) {
                    assertEquals(9, before.inning)
                    assertEquals(InningHalf.BOTTOM, before.half)
                    assertTrue(before.outs < 3 && before.homeScore <= before.awayScore)
                    assertTrue(after.homeScore > after.awayScore)
                    assertEquals(GameStatus.FINISHED, after.status)
                    assertEquals(timeline.events.last(), event)
                }
            }
        }
        assertNull(repository.getGame("unknown"))
    }

    @Test
    fun allFourteenTypesReachTheDispatcherWithExpectedMessages() = runTest {
        withViewModel { viewModel, dispatches ->
            val verified = mutableSetOf<NotificationType>()
            val games = viewModel.games.value as GameListState.Loaded
            assertEquals(8, games.games.size)
            assertEquals(8, games.scenarios.size)
            for ((scenario, cases) in expected.groupBy { it.gameId to it.team }) {
                val (gameId, team) = scenario
                val startIndex = dispatches.size
                viewModel.selectGame(gameId)
                // Same-game selection intentionally preserves state; explicitly start a new session below.
                runCurrent()
                viewModel.selectPreferredTeam(team)
                viewModel.play()
                step(30_000)
                val actual = dispatches.drop(startIndex)
                val detail = viewModel.loaded()
                assertEquals(PlaybackStatus.COMPLETED, detail.playback.status)
                assertEquals(actual, detail.notifications.filter { it.status == DeliveryStatus.SENT }.map { it.candidate })
                assertTrue(detail.notifications.filter { it.status != DeliveryStatus.SENT }.all { it.candidate.type == OPPONENT_ON_BASE })
                assertTrue(actual.all { it.gameId == gameId })
                assertEquals(actual.size, actual.map { it.eventId }.distinct().size)
                for (case in cases) {
                    val sent = actual.single { it.eventId == case.eventId }
                    assertEquals(case.type, sent.type)
                    assertEquals(case.title, sent.title)
                    assertEquals(case.body, sent.body)
                    verified.add(sent.type)
                    println("verified | $gameId | $team | ${sent.eventId} | ${sent.title} | ${sent.body}")
                }
                when (gameId) {
                    "mock-001" -> assertFalse(actual.any { it.eventId == "play-7" })
                    "mock-big-inning" -> assertEquals(1, actual.count { "빅이닝" in it.body })
                    "mock-special" -> {
                        val ids = actual.map { it.eventId }
                        assertEquals(listOf("review-start", "review-result", "injury-suspected",
                            "medical-check", "injury-substitution"), ids)
                        assertEquals("장도현", detail.playback.gameState.pitcher?.name)
                    }
                }
                if (gameId.startsWith("mock-walkoff")) assertEquals(GameStatus.FINISHED, detail.playback.gameState.status)
            }
            assertEquals(setOf(GAME_STARTED, OFFENSE_STARTED, OFFENSE_ENDED, DEFENSE_STARTED, DEFENSE_ENDED, REACHED_BASE, RUN_SCORED, TIED, COMEBACK, HOME_RUN, RUN_ALLOWED, VIDEO_REVIEW, INJURY, BIG_INNING), verified)
        }
    }

    @Test
    fun pauseResumeResetAndSwitchDoNotLeakDispatches() = runTest {
        withViewModel { viewModel, dispatches ->
            viewModel.selectGame("mock-close")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.play()
            step(1_000)
            viewModel.pause()
            step(12_000)
            assertEquals(listOf("half-start"), dispatches.map { it.eventId })
            viewModel.resume()
            step(2_000)
            assertEquals(listOf("half-start", "setup"), dispatches.map { it.eventId })

            viewModel.reset()
            step(12_000)
            assertTrue(viewModel.loaded().notifications.isEmpty())
            assertEquals(2, dispatches.size)
            viewModel.play()
            step(0)
            assertEquals(listOf("half-start", "setup", "half-start"), dispatches.map { it.eventId })

            viewModel.selectGame("mock-special")
            runCurrent()
            assertEquals(0, viewModel.loaded().playback.processedEventCount)
            assertNull(viewModel.loaded().preferredTeamId)
            viewModel.selectPreferredTeam("seoul")
            viewModel.play()
            step(30_000)
            assertEquals(listOf("review-start", "review-result", "injury-suspected",
                "medical-check", "injury-substitution"), dispatches.drop(3).map { it.eventId })
            assertTrue(dispatches.drop(3).all { it.gameId == "mock-special" })
        }
    }

    private fun TestScope.repository() = MockGameRepository(StandardTestDispatcher(testScheduler)) { file ->
        requireNotNull(javaClass.getResource("/games/$file")) { file }.readText(Charsets.UTF_8)
    }

    private suspend fun TestScope.withViewModel(
        onDispatch: (NotificationCandidate) -> Unit = {},
        preferencesRepository: NotificationPreferencesRepository =
            com.clutchalarm.app.domain.notification.InMemoryNotificationPreferencesRepository(),
        block: suspend (GameViewModel, List<NotificationCandidate>) -> Unit,
    ) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dispatches = mutableListOf<NotificationCandidate>()
        val viewModel = GameViewModel(repository(), testScheduler.timeSource,
            NotificationDispatcher {
                onDispatch(it)
                dispatches.add(it)
                DeliveryStatus.SENT
            }, preferencesRepository)
        try {
            runCurrent()
            block(viewModel, dispatches)
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.step(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun GameViewModel.loaded() = detail.value as GameDetailState.Loaded

    private data class Expected(
        val gameId: String,
        val eventId: String,
        val type: NotificationType,
        val body: String,
        val team: String = "busan",
        val title: String = type.title,
    )

    private val expected = listOf(
        Expected("mock-001", "play-1", GAME_STARTED, "1회초 · 부산 웨이브스 경기 시작! 첫 투구 · 0 : 0"),
        Expected("mock-001", "play-2", REACHED_BASE, "1회초 · 부산 웨이브스 김도윤 안타! 무사 주자 1루 · 0 : 0"),
        Expected("mock-001", "play-3", RUN_SCORED, "1회초 · 부산 웨이브스 이준서 2루타! 리드 · 1 : 0"),
        Expected("mock-001", "play-6", OFFENSE_ENDED, "1회초 · 부산 웨이브스 공격 종료 · 수비 전환 · 1 : 0"),
        Expected("mock-001", "play-6", DEFENSE_ENDED, "1회초 · 서울 팔콘스 수비 종료 · 공격 전환 · 0 : 1", "seoul"),
        Expected("mock-close", "half-start", OFFENSE_STARTED, "7회초 · 부산 웨이브스 공격 시작 · 2 : 3"),
        Expected("mock-close", "half-start", DEFENSE_STARTED, "7회초 · 서울 팔콘스 수비 시작 · 3 : 2", "seoul"),
        Expected("mock-close", "tie", TIED, "7회초 · 부산 웨이브스 이준서 적시타! 동점 · 3 : 3"),
        Expected("mock-close", "tie", RUN_ALLOWED, "7회초 · 부산 웨이브스 이준서 적시타! 동점 허용 · 3 : 3",
            "seoul", "⚠️ 동점 허용"),
        Expected("mock-close", "comeback", COMEBACK, "8회초 · 부산 웨이브스 정지훈 2점 홈런! 역전 · 5 : 4",
            title = "🚨 역전 홈런"),
        Expected("mock-close", "comeback", RUN_ALLOWED, "8회초 · 부산 웨이브스 정지훈 2점 홈런! 역전 허용 · 4 : 5",
            "seoul", "🚨 역전 허용"),
        Expected("mock-home-runs", "two-run", COMEBACK, "4회초 · 부산 웨이브스 김도윤 2점 홈런! 역전 · 2 : 1",
            title = "🚨 역전 홈런"),
        Expected("mock-home-runs", "solo", HOME_RUN, "4회초 · 부산 웨이브스 이준서 솔로 홈런! 1점 득점 · 부산 웨이브스 3득점 빅이닝 · 3 : 1"),
        Expected("mock-home-runs", "three-run", HOME_RUN,
            "4회초 · 부산 웨이브스 정지훈 3점 홈런! 3점 득점 · 6 : 1"),
        Expected("mock-home-runs", "grand-slam", HOME_RUN, "4회초 · 부산 웨이브스 장현우 만루 홈런! 4점 득점 · 10 : 1"),
        Expected("mock-big-inning", "two-more", BIG_INNING,
            "5회초 · 부산 웨이브스 박민재 2타점 적시타! 2점 득점 · 부산 웨이브스 3득점 빅이닝 · 5 : 1"),
        Expected("mock-big-inning", "fifth-run", RUN_SCORED, "5회초 · 부산 웨이브스 정지훈 적시타! 1점 득점 · 7 : 1"),
        Expected("mock-walkoff-hit", "walkoff-hit", RUN_SCORED, "9회말 · 서울 팔콘스 윤지호 적시타! 끝내기 승리 · 5 : 4",
            "seoul", "🚨 끝내기 승리"),
        Expected("mock-walkoff-homer", "walkoff-homer", COMEBACK,
            "9회말 · 서울 팔콘스 윤지호 2점 홈런! 끝내기 승리 · 5 : 4", "seoul", "🚨 끝내기 홈런"),
        Expected("mock-special", "review-start", VIDEO_REVIEW, "3회초 · 1루 세이프 여부 · 공식 비디오 판독 시작 · 1 : 1"),
        Expected("mock-special", "review-result", VIDEO_REVIEW, "3회초 · 비디오 판독 결과 · 세이프 판정으로 번복 · 1 : 1"),
        Expected("mock-special", "injury-suspected", INJURY, "3회초 · 서울 팔콘스 임건우 부상 의심 · 경기 중단 · 1 : 1"),
        Expected("mock-special", "injury-substitution", INJURY, "3회초 · 서울 팔콘스 임건우 부상으로 교체 · 1 : 1"),
    )
}
