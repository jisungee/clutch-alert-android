package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameTimeline
import com.clutchalarm.app.domain.repository.GameRepository
import com.clutchalarm.app.domain.simulation.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
