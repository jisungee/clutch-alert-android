package com.clutchalarm.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.*
import com.clutchalarm.app.behavior.*
import com.clutchalarm.app.notification.*
import java.io.File
import kotlinx.coroutines.*
import okio.Path.Companion.toOkioPath
import okio.FileSystem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BehaviorPersistenceTest {
    @get:Rule val folder = org.junit.rules.TemporaryFolder()
    private fun fallback(id: Int, game: String = "game", process: String = "process") =
        NotificationDelivery(game, "$process:$id", id.toLong(), process)

    @Test fun successSequenceStartsAfterSettingsSaveAndContinuesAcrossReopeningAndGames() = runBlocking {
        var installation = ""
        withStore("settings") { store ->
            val prefs = DataStoreNotificationPreferencesRepository(store)
            val publisher = DataStoreNotificationPublisher(store)
            assertEquals(DispatchSequenceScope.PROCESS, publisher.publish(fallback(5)) {}.sequenceScope)
            prefs.update { it.copy(notificationSettingsSaved = true) }
            val first = publisher.publish(fallback(6)) {}
            assertEquals(1L, first.sequence)
            assertEquals(DispatchSequenceScope.INSTALLATION, first.sequenceScope)
            installation = first.sequenceScopeId
            assertThrows(SecurityException::class.java) { runBlocking { publisher.publish(fallback(7)) { throw SecurityException() } } }
            val second = publisher.publish(fallback(8, "different-game")) {}
            assertEquals(2L, second.sequence)
            prefs.update { it.copy(notificationSettingsSaved = false) }
            assertTrue(prefs.load().notificationSettingsSaved)
        }
        withStore("settings") { store ->
            val publisher = DataStoreNotificationPublisher(store)
            val next = publisher.publish(fallback(1, process = "new-process")) {}
            assertEquals(3L, next.sequence)
            assertEquals(installation, next.sequenceScopeId)
        }
    }

    @Test fun concurrentSuccessfulPostsHaveOneInstallationAndNoLostNumbers() = runBlocking {
        withStore("settings") { store ->
            DataStoreNotificationPreferencesRepository(store).update { it.copy(notificationSettingsSaved = true) }
            val publisher = DataStoreNotificationPublisher(store)
            val results = coroutineScope { (1..10).map { id -> async { publisher.publish(fallback(id)) {} } }.awaitAll() }
            assertEquals((1L..10L).toSet(), results.map { it.sequence }.toSet())
            assertEquals(1, results.map { it.sequenceScopeId }.distinct().size)
        }
    }

    @Test fun secondOpenedFirstDoesNotConsumeFirstDoneAndProcessOneIsNotInstallationOne() = runBlocking {
        val events = mutableListOf<BehaviorEvent>()
        val recorder = BehaviorEventRecorder { events.add(it) }
        fun install(id: Long, post: String = "new:$id") = NotificationDelivery("game", post, id, "installation", DispatchSequenceScope.INSTALLATION)
        withStore("opens") { store ->
            val opens = DataStoreNotificationOpenRepository(store, recorder)
            opens.markOpened(fallback(1)) // legacy PROCESS 1 must never mean first_done
            assertTrue(events.isEmpty())
            assertTrue(opens.markOpened(install(2)))
            assertEquals("repeat_done", events.single().name)
            assertTrue(opens.markOpened(install(1)))
            assertEquals("first_done", events.last().name)
            assertFalse(opens.markOpened(install(2)))
            assertFalse(opens.markOpened(install(1)))
        }
        withStore("opens") { store ->
            val opens = DataStoreNotificationOpenRepository(store, recorder)
            assertFalse(opens.markOpened(install(1)))
            opens.markOpened(install(1, "another-id")) // first_done is installation-once, not post-once
        }
        assertEquals(1, events.count { it.name == "first_done" })
        assertEquals(1, events.count { it.name == "repeat_done" })
        assertEquals("notification_open", events.first().action)
    }

    private suspend fun withStore(name: String, block: suspend (DataStore<Preferences>) -> Unit) {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer,
            producePath = { File(folder.root, "$name.preferences_pb").toOkioPath() }), scope = CoroutineScope(Dispatchers.IO + job))
        try { block(store) } finally { job.cancelAndJoin() }
    }
}
