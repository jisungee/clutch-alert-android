package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.domain.simulation.PlaybackStatus
import com.clutchalarm.app.domain.simulation.PlaybackSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameNavigationTest {
    @Test fun homeToSelectionToDetailBackUnwindsAndStopsPlayback() = runTest {
        withModel { model ->
            assertEquals(MainScreen.HOME, model.mainScreen.value)
            assertNull(model.detail.value)
            model.showGames()
            assertEquals(MainScreen.GAMES, model.mainScreen.value)
            model.selectGame("mock-001")
            runCurrent()
            model.play()
            runCurrent()
            model.setPlaybackSpeed(PlaybackSpeed.DOUBLE)
            runCurrent()
            model.showNotificationSettings(true)
            model.navigateBack()
            assertFalse(model.showNotificationSettings.value)
            assertEquals(PlaybackStatus.PLAYING, model.loaded().playback.status)
            assertEquals(PlaybackSpeed.DOUBLE, model.loaded().playback.speed)
            model.navigateBack()
            assertNull(model.detail.value)
            assertEquals(MainScreen.GAMES, model.mainScreen.value)
            advanceTimeBy(30_000)
            runCurrent()
            assertNull(model.detail.value)
            model.navigateBack()
            assertEquals(MainScreen.HOME, model.mainScreen.value)
            model.navigateBack()
            assertEquals(MainScreen.HOME, model.mainScreen.value)
        }
    }

    @Test fun notificationFromHomeOpensDetailAndSameGameKeepsPlayback() = runTest {
        withModel { model ->
            model.openNotificationGame("mock-close")
            runCurrent()
            assertEquals("mock-close", model.currentGameId)
            assertEquals(MainScreen.HOME, model.mainScreen.value)
            model.play()
            advanceTimeBy(1_000)
            runCurrent()
            model.pause()
            runCurrent()
            val playback = model.loaded().playback
            model.showNotificationSettings(true)
            model.openNotificationGame("mock-close")
            runCurrent()
            assertFalse(model.showNotificationSettings.value)
            assertEquals(playback, model.loaded().playback)
            model.openNotificationGame("mock-special")
            runCurrent()
            assertEquals("mock-special", model.currentGameId)
            assertEquals(PlaybackStatus.IDLE, model.loaded().playback.status)
            model.navigateBack()
            assertNull(model.detail.value)
            assertEquals(MainScreen.HOME, model.mainScreen.value)
        }
    }

    @Test fun notificationOverSelectionReturnsThereAndRestoredRouteKeepsOrigin() = runTest {
        withModel { model ->
            model.showGames()
            model.openNotificationGame("pdf-snapshot-test")
            runCurrent()
            model.navigateBack()
            assertNull(model.detail.value)
            assertEquals(MainScreen.GAMES, model.mainScreen.value)
            // Activity restores this saved main screen before restoring a selected game.
            model.restoreMainScreen(MainScreen.GAMES)
            model.selectGame("mock-home-runs")
            runCurrent()
            model.navigateBack()
            assertEquals(MainScreen.GAMES, model.mainScreen.value)
            model.navigateBack()
            model.showNotificationSettings(true)
            model.navigateBack()
            assertEquals(MainScreen.HOME, model.mainScreen.value)
            assertFalse(model.showNotificationSettings.value)
        }
    }

    @Test fun unknownNotificationGameShowsErrorAndBackReturnsToOrigin() = runTest {
        withModel { model ->
            model.showGames()
            model.openNotificationGame("missing")
            runCurrent()
            assertEquals(GameDetailState.Error, model.detail.value)
            model.navigateBack()
            assertNull(model.detail.value)
            assertEquals(MainScreen.GAMES, model.mainScreen.value)
        }
    }

    private suspend fun TestScope.withModel(block: suspend TestScope.(GameViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = MockGameRepository(StandardTestDispatcher(testScheduler), "test-catalog.json") { file ->
            requireNotNull(javaClass.getResource("/games/$file")).readText(Charsets.UTF_8)
        }
        val model = GameViewModel(repository, testScheduler.timeSource)
        try {
            runCurrent()
            block(model)
        } finally {
            model.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun GameViewModel.loaded() = detail.value as GameDetailState.Loaded
}
