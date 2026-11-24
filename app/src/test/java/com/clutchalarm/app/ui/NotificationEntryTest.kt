package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.notification.*
import com.clutchalarm.app.domain.simulation.PlaybackSpeed
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationEntryTest {
    private fun delivery(sequence: Long) = NotificationDelivery("mock-001", "test-process:$sequence", sequence, "test-process")

    @Test fun twoPostsSameGameAreDifferentButRepeatedPostAndRotationAreNotNewReads() = runTest {
        val receipts = mutableListOf<GameDetailEntry>()
        withModel(InMemoryNotificationOpenRepository(), receipts) { model ->
            model.openNotification(delivery(1))
            runCurrent()
            assertTrue(receipts.isEmpty()) // loaded is not yet a visible screen
            model.confirmVisible()
            runCurrent()
            assertEquals(NotificationReadStatus.NEW, receipts.last().readStatus)
            model.confirmVisible() // LifecycleResumeEffect on rotation
            runCurrent()
            assertEquals(1, receipts.size)
            model.openNotification(delivery(2))
            runCurrent()
            model.confirmVisible()
            runCurrent()
            assertEquals(NotificationReadStatus.NEW, receipts.last().readStatus)
            assertNotEquals(receipts[0].delivery?.postId, receipts[1].delivery?.postId)
            model.openNotification(delivery(1))
            model.confirmVisible()
            runCurrent()
            assertEquals(NotificationReadStatus.DUPLICATE, receipts.last().readStatus)
            assertEquals(2, receipts.count { it.readStatus == NotificationReadStatus.NEW })
        }
    }

    @Test fun notificationKeepsSameGamePlaybackAndListEntryIsNotANotificationRead() = runTest {
        val receipts = mutableListOf<GameDetailEntry>()
        withModel(InMemoryNotificationOpenRepository(), receipts) { model ->
            model.showGames()
            model.selectGame("mock-001")
            runCurrent()
            model.confirmVisible()
            runCurrent()
            assertEquals(DetailEntrySource.LIST, receipts.single().source)
            assertEquals(NotificationReadStatus.NOT_APPLICABLE, receipts.single().readStatus)
            model.play()
            runCurrent()
            model.setPlaybackSpeed(PlaybackSpeed.DOUBLE)
            advanceTimeBy(1_000)
            runCurrent()
            model.pause()
            runCurrent()
            val before = (model.detail.value as GameDetailState.Loaded).playback
            model.openNotification(delivery(1))
            model.confirmVisible()
            runCurrent()
            assertEquals(before, (model.detail.value as GameDetailState.Loaded).playback)
            assertEquals(NotificationReadStatus.NEW, receipts.last().readStatus)
            model.navigateBack()
            assertEquals(MainScreen.GAMES, model.mainScreen.value)
        }
    }

    @Test fun missingGameAndSettingsCoverDoNotRecordReads() = runTest {
        val receipts = mutableListOf<GameDetailEntry>()
        withModel(InMemoryNotificationOpenRepository(), receipts) { model ->
            val invalid = NotificationDelivery("missing", "test-process:1", 1, "test-process")
            model.openNotification(invalid)
            runCurrent()
            model.confirmVisible()
            assertEquals(GameDetailState.Error, model.detail.value)
            model.openNotification(delivery(2))
            runCurrent()
            model.showNotificationSettings(true)
            model.confirmVisible()
            runCurrent()
            assertTrue(receipts.isEmpty())
            model.navigateBack()
            model.confirmVisible()
            runCurrent()
            assertEquals(1, receipts.size)
        }
    }

    @Test fun processRestartAndRestoredEntryCannotCreateAnotherRead() = runTest {
        val store = InMemoryNotificationOpenRepository()
        val receipts = mutableListOf<GameDetailEntry>()
        withModel(store, receipts) { model ->
            model.openNotification(delivery(1))
            runCurrent()
            model.confirmVisible()
            runCurrent()
        }
        withModel(store, receipts) { model ->
            model.openNotification(delivery(1))
            runCurrent()
            model.confirmVisible()
            runCurrent()
            assertEquals(NotificationReadStatus.DUPLICATE, receipts.last().readStatus)
        }
        withModel(store, receipts) { model ->
            model.restoreDetailEntry(GameDetailEntry("mock-001", DetailEntrySource.NOTIFICATION, delivery(1), NotificationReadStatus.RESTORED))
            runCurrent()
            val count = receipts.size
            model.confirmVisible()
            runCurrent()
            assertEquals(count, receipts.size)
        }
        assertEquals(1, receipts.count { it.readStatus == NotificationReadStatus.NEW })
    }

    @Test fun failedReceiptWriteIsUnconfirmedAndCanRetryWithoutClaimingANewRead() = runTest {
        var fail = true
        val store = InMemoryNotificationOpenRepository()
        val repository = NotificationOpenRepository { if (fail) throw IOException("disk full") else store.markOpened(it) }
        val receipts = mutableListOf<GameDetailEntry>()
        withModel(repository, receipts) { model ->
            model.openNotification(delivery(1))
            runCurrent()
            model.confirmVisible()
            runCurrent()
            assertEquals(NotificationReadStatus.UNCONFIRMED, model.detailEntry.value?.readStatus)
            assertTrue(receipts.isEmpty())
            fail = false
            model.confirmVisible()
            runCurrent()
            assertEquals(NotificationReadStatus.NEW, receipts.single().readStatus)
        }
    }

    private fun GameViewModel.confirmVisible() {
        val entry = detailEntry.value!!
        confirmDetailVisible(entry.gameId, entry.requestId)
    }

    private suspend fun TestScope.withModel(store: NotificationOpenRepository, receipts: MutableList<GameDetailEntry>,
        block: suspend TestScope.(GameViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = MockGameRepository(StandardTestDispatcher(testScheduler)) { file ->
            requireNotNull(javaClass.getResource("/games/$file")).readText(Charsets.UTF_8)
        }
        val model = GameViewModel(repository, testScheduler.timeSource,
            notificationOpens = store, onDetailEntry = { receipts.add(it) })
        try { runCurrent(); block(model) }
        finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
