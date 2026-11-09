package com.clutchalarm.app.data

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import com.clutchalarm.app.domain.notification.NotificationPreferences
import com.clutchalarm.app.domain.notification.NotificationType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreNotificationPreferencesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun emptyStoreEnablesAllFourteenTypes() = runBlocking {
        withRepository { repository ->
            val preferences = repository.load()
            assertEquals(14, NotificationType.entries.size)
            assertTrue(NotificationType.entries.all(preferences::isEnabled))
        }
    }

    @Test
    fun allDisabledSurvivesReopeningAndOneTypeCanBeReenabled() = runBlocking {
        withRepository { repository ->
            val saved = repository.update { NotificationPreferences(NotificationType.entries.toSet()) }
            assertEquals(0, saved.enabledCount)
        }
        withRepository { repository ->
            assertEquals(0, repository.load().enabledCount)
            val saved = repository.update { it.withEnabled(NotificationType.TIED, true) }
            assertEquals(1, saved.enabledCount)
            assertTrue(saved.isEnabled(NotificationType.TIED))
        }
    }

    @Test
    fun savedTogglesSurviveReopeningAndCanBeEnabledAgain() = runBlocking {
        withRepository { repository ->
            repository.update { it.withEnabled(NotificationType.HOME_RUN, false) }
            repository.update { it.withEnabled(NotificationType.INJURY, false) }
        }
        withRepository { repository ->
            val loaded = repository.load()
            assertFalse(loaded.isEnabled(NotificationType.HOME_RUN))
            assertFalse(loaded.isEnabled(NotificationType.INJURY))
            assertTrue(loaded.isEnabled(NotificationType.COMEBACK))
            val updated = repository.update { it.withEnabled(NotificationType.HOME_RUN, true) }
            assertTrue(updated.isEnabled(NotificationType.HOME_RUN))
            assertFalse(updated.isEnabled(NotificationType.INJURY))
            repository.update { NotificationPreferences() }
        }
        withRepository { repository ->
            assertTrue(NotificationType.entries.all(repository.load()::isEnabled))
        }
    }

    private suspend fun withRepository(
        block: suspend (DataStoreNotificationPreferencesRepository) -> Unit,
    ) {
        val job = Job()
        // The JVM test runs on Windows, where File.renameTo cannot replace an existing file.
        val store = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = {
                File(temporaryFolder.root, "notification_preferences.preferences_pb").toOkioPath()
            }),
            scope = CoroutineScope(Dispatchers.IO + job),
        )
        try {
            block(DataStoreNotificationPreferencesRepository(store))
        } finally {
            job.cancelAndJoin()
        }
    }
}
