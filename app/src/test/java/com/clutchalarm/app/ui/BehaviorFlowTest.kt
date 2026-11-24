package com.clutchalarm.app.ui

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.*
import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.behavior.*
import com.clutchalarm.app.data.*
import com.clutchalarm.app.domain.notification.*
import com.clutchalarm.app.notification.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class BehaviorFlowTest {
    @get:Rule val folder = org.junit.rules.TemporaryFolder()

    @Test fun acceptanceFlowHasOneAppOpenOneFirstTwoRepeatAndOneDrop() = runTest {
        val job = Job()
        fun store(name: String) = PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM,
            PreferencesSerializer, producePath = { File(folder.root, "$name.preferences_pb").toOkioPath() }),
            scope = CoroutineScope(Dispatchers.IO + job))
        val preferences = store("preferences")
        val receiptStore = store("receipts")
        val events = mutableListOf<BehaviorEvent>()
        val recorder = BehaviorEventRecorder { synchronized(events) { events.add(it) } }
        val process = ProcessAppOpen(recorder)
        process.onScreenVisible()
        try {
            withModel(DataStoreNotificationPreferencesRepository(preferences),
                DataStoreNotificationOpenRepository(receiptStore, recorder), recorder) { model ->
                model.chooseTeam("SSG")
                model.teamNext()
                model.saveSettings()
                model.notificationSettings.first { !it.busy }
                assertTrue(events.none { it.name == "first_done" })
                val publisher = DataStoreNotificationPublisher(preferences)
                val one = publisher.publish(NotificationDelivery("mock-001", "process:1", 1, "process")) {}
                val two = publisher.publish(NotificationDelivery("mock-001", "process:2", 2, "process")) {}
                for (delivery in listOf(one, two)) {
                    model.openNotification(delivery)
                    model.detail.first { it is GameDetailState.Loaded }
                    val entry = model.detailEntry.value!!
                    model.confirmDetailVisible(entry.gameId, entry.requestId)
                    model.detailEntry.first { it?.readStatus == NotificationReadStatus.NEW }
                }
                model.showNotificationSettings(true)
                model.saveSettings()
                model.notificationSettings.first { !it.busy }
                model.showNotificationSettings(true)
                model.openCategory(NotificationCategory.FLOW)
                model.setNotificationEnabled(NotificationType.HOME_RUN, false)
                model.editorBack() // details to categories is not a drop
                assertTrue(events.none { it.name == "drop" })
                model.editorBack()
                model.onSettingsFlowAbandoned()
                val count = events.size
                model.selectGame("mock-001")
                runCurrent()
                val direct = model.detailEntry.value!!
                model.confirmDetailVisible(direct.gameId, direct.requestId)
                runCurrent()
                repeat(3) { process.onScreenVisible() }
                assertEquals(count, events.size)
            }
        } finally { job.cancelAndJoin() }
        assertEquals(1, events.count { it.name == "app_open" })
        assertEquals(1, events.count { it.name == "first_done" })
        assertEquals(2, events.count { it.name == "repeat_done" })
        assertEquals(setOf("notification_open", "settings_resave"), events.filter { it.name == "repeat_done" }.map { it.action }.toSet())
        assertEquals(1, events.count { it.name == "drop" })
    }

    @Test fun untouchedFlowSuccessfulSaveAndTeamOnlyChangeAreNotDropOrSettingsResave() = runTest {
        val events = mutableListOf<BehaviorEvent>()
        val repo = InMemoryNotificationPreferencesRepository(NotificationPreferences(onboardingCompleted = true, notificationSettingsSaved = true))
        withModel(repo, InMemoryNotificationOpenRepository(), BehaviorEventRecorder { events.add(it) }) { model ->
            model.showNotificationSettings(true)
            model.editorBack()
            assertTrue(events.isEmpty())
            model.showTeamSelection()
            model.chooseTeam("KIA")
            model.saveSettings()
            runCurrent()
            assertTrue(events.isEmpty())
            model.showNotificationSettings(true)
            model.disableAllNotifications()
            model.enableAllNotifications()
            model.saveSettings()
            runCurrent()
            model.onSettingsFlowAbandoned()
            assertEquals(listOf("repeat_done"), events.map { it.name })
            assertEquals("settings_resave", events.single().action)
        }
    }

    @Test fun failedSaveDoesNotEmitResaveAndLeavingTouchedFlowEmitsOnlyOneDrop() = runTest {
        val events = mutableListOf<BehaviorEvent>()
        val repo = object : NotificationPreferencesRepository {
            override suspend fun load() = NotificationPreferences(onboardingCompleted = true, notificationSettingsSaved = true)
            override suspend fun update(transform: (NotificationPreferences) -> NotificationPreferences): NotificationPreferences = throw IOException("full")
        }
        withModel(repo, InMemoryNotificationOpenRepository(), BehaviorEventRecorder { events.add(it) }) { model ->
            model.showNotificationSettings(true)
            model.disableAllNotifications()
            model.saveSettings()
            runCurrent()
            assertTrue(model.notificationSettings.value.error)
            assertTrue(events.isEmpty())
            model.editorBack()
            model.editorBack()
            model.onSettingsFlowAbandoned()
            assertEquals(listOf("drop"), events.map { it.name })
        }
    }

    @Test fun myPageShowsOnlyCommittedTeamAndRetainsOriginAfterSaveCancelAndReload() = runTest {
        val events = mutableListOf<BehaviorEvent>()
        val recorder = BehaviorEventRecorder { events.add(it) }
        val repo = InMemoryNotificationPreferencesRepository(NotificationPreferences(
            preferredTeamId = "SSG", onboardingCompleted = true, notificationSettingsSaved = true))
        withModel(repo, InMemoryNotificationOpenRepository(), recorder) { model ->
            model.showMyPage()
            model.showTeamSelection()
            model.chooseTeam("KIA")
            assertEquals("SSG", model.notificationSettings.value.preferences?.preferredTeamId)
            model.navigateBack()
            assertEquals(MainScreen.MY_PAGE, model.mainScreen.value)
            assertEquals("SSG", model.notificationSettings.value.preferences?.preferredTeamId)
            model.showTeamSelection()
            model.chooseTeam("KIA")
            model.saveSettings()
            runCurrent()
            assertFalse(model.showNotificationSettings.value)
            assertEquals(MainScreen.MY_PAGE, model.mainScreen.value)
            assertEquals("KIA", model.notificationSettings.value.preferences?.preferredTeamId)
            assertEquals("KIA", repo.load().preferredTeamId)
            // Team-only save is neither notification resave nor a touched-toggle drop.
            assertTrue(events.isEmpty())
        }
        withModel(repo, InMemoryNotificationOpenRepository(), recorder) { model ->
            model.restoreMainScreen(MainScreen.MY_PAGE)
            assertEquals("KIA", model.notificationSettings.value.preferences?.preferredTeamId)
            model.openNotificationGame("mock-001")
            runCurrent()
            model.navigateBack()
            assertEquals(MainScreen.MY_PAGE, model.mainScreen.value)
            model.navigateBack()
            assertEquals(MainScreen.HOME, model.mainScreen.value)
        }
    }

    @Test fun myPageSettingsKeepResaveAndDropRulesAcrossCategoryNavigation() = runTest {
        val events = mutableListOf<BehaviorEvent>()
        val repo = InMemoryNotificationPreferencesRepository(NotificationPreferences(
            preferredTeamId = "SSG", onboardingCompleted = true, notificationSettingsSaved = true))
        withModel(repo, InMemoryNotificationOpenRepository(), BehaviorEventRecorder { events.add(it) }) { model ->
            model.showMyPage()
            model.showNotificationSettings(true)
            model.navigateBack()
            assertTrue(events.isEmpty())
            val saved = model.notificationSettings.value.preferences!!
            model.showNotificationSettings(true)
            model.openCategory(NotificationCategory.FLOW)
            model.setNotificationEnabled(NotificationType.HOME_RUN, false)
            model.navigateBack()
            assertTrue(events.isEmpty())
            model.navigateBack()
            assertEquals(MainScreen.MY_PAGE, model.mainScreen.value)
            assertEquals(saved, model.notificationSettings.value.preferences)
            assertEquals(saved, repo.load())
            assertEquals(listOf("drop"), events.map { it.name })
            model.showNotificationSettings(true)
            model.disableAllNotifications()
            model.saveSettings()
            runCurrent()
            assertFalse(model.showNotificationSettings.value)
            assertEquals(MainScreen.MY_PAGE, model.mainScreen.value)
            assertEquals(0, model.notificationSettings.value.preferences?.enabledCount)
            model.onSettingsFlowAbandoned()
            assertEquals(listOf("drop", "repeat_done"), events.map { it.name })
            assertEquals("settings_resave", events.last().action)
        }
    }

    private suspend fun TestScope.withModel(preferences: NotificationPreferencesRepository, opens: NotificationOpenRepository,
        recorder: BehaviorEventRecorder, block: suspend TestScope.(GameViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = MockGameRepository(StandardTestDispatcher(testScheduler)) { file ->
            requireNotNull(javaClass.getResource("/games/$file")).readText(Charsets.UTF_8)
        }
        val model = GameViewModel(repo, testScheduler.timeSource, preferencesRepository = preferences,
            notificationOpens = opens, behaviorEvents = recorder)
        try {
            model.notificationSettings.first { !it.busy && it.preferences != null }
            block(model)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
