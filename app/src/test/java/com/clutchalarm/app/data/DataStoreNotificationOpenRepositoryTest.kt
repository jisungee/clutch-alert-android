package com.clutchalarm.app.data

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import com.clutchalarm.app.notification.NotificationDelivery
import java.io.File
import kotlinx.coroutines.*
import okio.Path.Companion.toOkioPath
import okio.FileSystem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreNotificationOpenRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun postReadIsAtomicAndPersistsAcrossStoreReopening() = runBlocking {
        val first = NotificationDelivery("game", "process:1", 1, "process")
        val second = NotificationDelivery("game", "process:2", 2, "process")
        withStore { repository ->
            val results = coroutineScope { List(10) { async { repository.markOpened(first) } }.awaitAll() }
            assertEquals(1, results.count { it })
            assertTrue(repository.markOpened(second))
        }
        withStore { repository ->
            assertFalse(repository.markOpened(first))
            assertFalse(repository.markOpened(second))
            assertTrue(repository.markOpened(NotificationDelivery("game", "new-process:1", 1, "new-process")))
        }
    }

    private suspend fun withStore(block: suspend (DataStoreNotificationOpenRepository) -> Unit) {
        val job = Job()
        val store = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = {
                File(temporaryFolder.root, "opens.preferences_pb").toOkioPath()
            }), scope = CoroutineScope(Dispatchers.IO + job),
        )
        try { block(DataStoreNotificationOpenRepository(store)) }
        finally { job.cancelAndJoin() }
    }
}
