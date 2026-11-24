package com.clutchalarm.app.data

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    fun defaultStoreLeavesOnlyOpponentOnBaseOff() = runBlocking {
        withRepository { repository ->
            val preferences = repository.load()
            assertEquals(20, NotificationType.entries.size)
            assertFalse(preferences.isEnabled(NotificationType.OPPONENT_ON_BASE))
            assertTrue(NotificationType.entries.filter { it != NotificationType.OPPONENT_ON_BASE }.all(preferences::isEnabled))
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
            assertFalse(repository.load().isEnabled(NotificationType.OPPONENT_ON_BASE))
        }
    }

    @Test
    fun losingFilterAndIndependentOpponentToggleSurviveReopening() = runBlocking {
        withRepository { repository ->
            repository.update { it.copy(hideWhenLosing = true).withEnabled(NotificationType.OPPONENT_ON_BASE, true) }
            repository.update { it.withEnabled(NotificationType.REACHED_BASE, false) }
        }
        withRepository { repository ->
            val preferences = repository.load()
            assertTrue(preferences.hideWhenLosing)
            assertTrue(preferences.isEnabled(NotificationType.OPPONENT_ON_BASE))
            assertFalse(preferences.isEnabled(NotificationType.REACHED_BASE))
        }
    }

    @Test
    fun freshStoreRequiresSetupAndFirstSaveSurvivesReopeningAtomically() = runBlocking {
        withRepository { repository ->
            assertFalse(repository.load().onboardingCompleted)
            assertEquals(null, repository.load().preferredTeamId)
            repository.update { it.copy(preferredTeamId = "SSG", onboardingCompleted = true,
                disabledTypes = setOf(NotificationType.HOME_RUN), hideWhenLosing = true) }
        }
        withRepository { repository ->
            val saved = repository.load()
            assertTrue(saved.onboardingCompleted)
            assertEquals("SSG", saved.preferredTeamId)
            assertFalse(saved.isEnabled(NotificationType.HOME_RUN))
            assertTrue(saved.hideWhenLosing)
        }
    }

    @Test
    fun legacyUserKeepsDisabledTypesAndHideOptionWithoutRepeatingSetup() = runBlocking {
        withRepository(seed = { store ->
            store.edit {
                it[stringSetPreferencesKey("disabled_notification_types")] = setOf("HOME_RUN", "VIDEO_REVIEW")
                it[booleanPreferencesKey("hide_when_losing")] = true
                it[booleanPreferencesKey("notification_preferences_initialized")] = true
            }
        }) { repository ->
            val old = repository.load()
            assertTrue(old.onboardingCompleted)
            assertEquals(null, old.preferredTeamId)
            assertFalse(old.isEnabled(NotificationType.HOME_RUN))
            assertFalse(old.isEnabled(NotificationType.VIDEO_REVIEW))
            assertTrue(old.hideWhenLosing)
            repository.update { it.copy(preferredTeamId = "KIA") }
        }
        withRepository { repository ->
            val saved = repository.load()
            assertEquals("KIA", saved.preferredTeamId)
            assertFalse(saved.isEnabled(NotificationType.HOME_RUN))
            assertTrue(saved.hideWhenLosing)
            assertTrue(saved.onboardingCompleted)
        }
    }

    private suspend fun withRepository(
        seed: (suspend (DataStore<Preferences>) -> Unit)? = null,
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
            seed?.invoke(store)
            block(DataStoreNotificationPreferencesRepository(store))
        } finally {
            job.cancelAndJoin()
        }
    }
}
