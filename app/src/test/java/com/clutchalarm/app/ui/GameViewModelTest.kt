package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameTimeline
import com.clutchalarm.app.domain.repository.GameRepository
import com.clutchalarm.app.domain.simulation.PlaybackStatus
import com.clutchalarm.app.notification.DeliveryStatus
import com.clutchalarm.app.notification.NotificationDispatcher
import com.clutchalarm.app.notification.NotificationAddress
import com.clutchalarm.app.notification.NotificationConfirmation
import com.clutchalarm.app.notification.NotificationSubmission
import com.clutchalarm.app.notification.PublicationStatus
import com.clutchalarm.app.domain.notification.NotificationCandidate
import com.clutchalarm.app.domain.notification.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    @Test
    fun loadsRealFixtureAndUpdatesScreenThroughPlaybackControls() = runTest {
        val repository = MockGameRepository {
            requireNotNull(javaClass.getResource("/games/mock-game.json")).readText(Charsets.UTF_8)
        }
        val timeline = requireNotNull(repository.getGame("mock-001"))
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = GameViewModel(object : GameRepository {
            override suspend fun listGames() = listOf(timeline.initialState)
            override suspend fun getGame(gameId: String) =
                timeline.takeIf { it.initialState.gameId == gameId }
        }, testScheduler.timeSource)
        try {
            runCurrent()
            assertEquals(GameListState.Loaded(listOf(timeline.initialState)), fixture.games.value)
            fixture.selectGame(timeline.initialState.gameId)
            runCurrent()
            assertEquals(timeline.initialState, fixture.loaded().playback.gameState)

            fixture.play()
            runCurrent()
            assertEquals(EventType.GAME_STARTED, fixture.loaded().latestEvent?.eventType)
            advanceTimeBy(1_000)
            fixture.pause()
            runCurrent()
            assertEquals(PlaybackStatus.PAUSED, fixture.loaded().playback.status)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(1, fixture.loaded().playback.processedEventCount)
            fixture.resume()
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(timeline.events[1].after, fixture.loaded().playback.gameState)
            assertEquals(EventType.SINGLE, fixture.loaded().latestEvent?.eventType)

            fixture.reset()
            runCurrent()
            assertEquals(PlaybackStatus.IDLE, fixture.loaded().playback.status)
            assertEquals(timeline.initialState, fixture.loaded().playback.gameState)
            assertNull(fixture.loaded().latestEvent)
            advanceTimeBy(5_000)
            runCurrent()
            assertNull(fixture.loaded().latestEvent)

            fixture.play()
            runCurrent()
            advanceTimeBy(timeline.events.last().offsetMs)
            runCurrent()
            assertEquals(PlaybackStatus.COMPLETED, fixture.loaded().playback.status)
            assertEquals(timeline.events.last(), fixture.loaded().latestEvent)

            fixture.play()
            runCurrent()
            fixture.closeGame()
            advanceTimeBy(30_000)
            runCurrent()
            assertNull(fixture.detail.value)
            fixture.selectGame(timeline.initialState.gameId)
            runCurrent()
            assertEquals(timeline.initialState, fixture.loaded().playback.gameState)
            assertNull(fixture.loaded().latestEvent)
        } finally {
            fixture.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dispatchesOrderedCandidatesAndKeepsPermissionFailuresSeparateFromPlayback() = runTest {
        val source = MockGameRepository {
            requireNotNull(javaClass.getResource("/games/mock-game.json")).readText(Charsets.UTF_8)
        }
        val original = requireNotNull(source.getGame("mock-001"))
        val timeline = original.copy(events = original.events.mapIndexed { index, event ->
            if (index == 1) event.copy(offsetMs = 0) else event
        })
        val dispatched = mutableListOf<NotificationCandidate>()
        var delivery = DeliveryStatus.PERMISSION_DENIED
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = GameViewModel(object : GameRepository {
            override suspend fun listGames() = listOf(timeline.initialState)
            override suspend fun getGame(gameId: String) = timeline.takeIf { it.initialState.gameId == gameId }
        }, testScheduler.timeSource, NotificationDispatcher { candidate ->
            dispatched.add(candidate)
            delivery
        })
        try {
            viewModel.selectGame("mock-001")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.play()
            runCurrent()
            assertEquals(listOf("play-1", "play-2"), dispatched.map { it.eventId })
            assertEquals(listOf(NotificationType.GAME_STARTED, NotificationType.REACHED_BASE), dispatched.map { it.type })
            assertEquals(List(2) { DeliveryStatus.PERMISSION_DENIED },
                viewModel.loaded().notifications.map { it.status })

            viewModel.pause()
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(2, dispatched.size)
            delivery = DeliveryStatus.SENT
            viewModel.selectPreferredTeam("seoul")
            viewModel.resume()
            runCurrent()
            advanceTimeBy(6_000)
            runCurrent()
            assertEquals(listOf("play-1", "play-2", "play-3"), dispatched.map { it.eventId })
            assertEquals(NotificationType.RUN_ALLOWED, dispatched.last().type)
            assertEquals(DeliveryStatus.SENT, viewModel.loaded().notifications.last().status)

            viewModel.selectGame("mock-001") // An alert tap on the current game preserves playback.
            assertEquals(3, viewModel.loaded().playback.processedEventCount)
            viewModel.reset()
            runCurrent()
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals("seoul", viewModel.loaded().preferredTeamId)
            assertEquals(emptyList<Any>(), viewModel.loaded().notifications)
            assertEquals(3, dispatched.size)
            viewModel.play()
            runCurrent()
            assertEquals(listOf("play-1", "play-2", "play-3", "play-1"), dispatched.map { it.eventId })
            assertEquals(1, viewModel.loaded().notifications.size)
            viewModel.closeGame()
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(4, dispatched.size)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun recordsConfirmationAndCleanupAcrossPlaybackSessions() = runTest {
        val scheduler = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(scheduler)
        val repository = MockGameRepository(scheduler) { file ->
            requireNotNull(javaClass.getResource("/games/$file")).readText(Charsets.UTF_8)
        }
        val first = NotificationAddress("test", 1)
        val second = NotificationAddress("test", 2)
        var attempts = 0
        val viewModel = GameViewModel(repository, testScheduler.timeSource, object : NotificationDispatcher {
            override fun dispatch(candidate: NotificationCandidate) = DeliveryStatus.SENT
            override fun dispatchTracked(candidate: NotificationCandidate): NotificationSubmission =
                if (++attempts == 1) NotificationSubmission(DeliveryStatus.SENT, first)
                else NotificationSubmission(DeliveryStatus.SENT, second, setOf(first))

            override suspend fun confirm(submission: NotificationSubmission): NotificationConfirmation {
                delay(100)
                return NotificationConfirmation(submission.address == first, submission.cleanupRequested)
            }
        })
        try {
            viewModel.selectGame("mock-001")
            runCurrent()
            viewModel.selectPreferredTeam("busan")
            viewModel.play()
            runCurrent()
            val pending = viewModel.loaded().notificationHistory.single()
            assertEquals(DeliveryStatus.SENT, pending.status)
            assertEquals(PublicationStatus.CHECKING, pending.publication)
            advanceTimeBy(100)
            runCurrent()
            assertEquals(PublicationStatus.CONFIRMED, viewModel.loaded().notificationHistory.single().publication)

            viewModel.reset()
            runCurrent()
            assertEquals(1, viewModel.loaded().notificationHistory.size)
            assertEquals(0, viewModel.loaded().notifications.size)
            viewModel.play()
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            val (original, replay) = viewModel.loaded().notificationHistory
            assertEquals(PublicationStatus.CONFIRMED, original.publication)
            assertEquals(true, original.cleaned)
            assertEquals(PublicationStatus.UNCONFIRMED, replay.publication)
            assertEquals(1, replay.cleanupCount)
            assertEquals(replay, viewModel.loaded().notifications.single())
            assertEquals(2, attempts)
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reportsListAndDetailLoadingFailures() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = GameViewModel(object : GameRepository {
            override suspend fun listGames(): List<GameState> = error("Broken fixture")
            override suspend fun getGame(gameId: String): GameTimeline? = null
        })
        try {
            runCurrent()
            assertEquals(GameListState.Error, viewModel.games.value)
            viewModel.selectGame("missing")
            runCurrent()
            assertEquals(GameDetailState.Error, viewModel.detail.value)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun GameViewModel.loaded() = detail.value as GameDetailState.Loaded
}
