package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.domain.model.KboTeams
import com.clutchalarm.app.domain.notification.*
import com.clutchalarm.app.domain.notification.NotificationType.*
import com.clutchalarm.app.notification.DeliveryStatus
import com.clutchalarm.app.notification.NotificationDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsFlowTest {
    private class Store(var saved: NotificationPreferences) : NotificationPreferencesRepository {
        var fail = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load() = saved
        override suspend fun update(transform: (NotificationPreferences) -> NotificationPreferences): NotificationPreferences {
            gate?.await()
            if (fail) throw IOException("disk full")
            return transform(saved).also { saved = it; writes++ }
        }
    }

    @Test fun firstSaveCompletesSetupAndRestartEntersHomeWithTheSameValues() = runTest {
        val store = Store(NotificationPreferences())
        withModel(store) { model, _ ->
            assertEquals(SettingsPage.TEAM, model.notificationSettings.value.editor?.page)
            assertTrue(model.showNotificationSettings.value)
            model.chooseTeam("SSG")
            model.teamNext()
            assertEquals(SettingsPage.CATEGORIES, model.notificationSettings.value.editor?.page)
            model.disableAllNotifications()
            model.setNotificationEnabled(HOME_RUN, true)
            assertFalse(store.saved.onboardingCompleted)
            assertEquals(0, store.writes)
            model.saveSettings()
            assertFalse(store.saved.onboardingCompleted)
            runCurrent()
            assertTrue(store.saved.onboardingCompleted)
            assertEquals("SSG", store.saved.preferredTeamId)
            assertEquals(1, store.saved.enabledCount)
            assertFalse(model.showNotificationSettings.value)
            assertEquals(MainScreen.HOME, model.mainScreen.value)
        }
        withModel(store) { model, _ ->
            assertNull(model.notificationSettings.value.editor)
            assertFalse(model.showNotificationSettings.value)
            assertEquals(MainScreen.HOME, model.mainScreen.value)
            assertEquals(store.saved, model.notificationSettings.value.preferences)
        }
    }

    @Test fun abandoningFirstFlowDoesNotPersistPartialSetup() = runTest {
        val store = Store(NotificationPreferences())
        withModel(store) { model, _ ->
            model.chooseTeam("KIA")
            model.teamNext()
            model.disableAllNotifications()
            model.navigateBack()
            assertEquals(SettingsPage.TEAM, model.notificationSettings.value.editor?.page)
            assertFalse(store.saved.onboardingCompleted)
            assertNull(store.saved.preferredTeamId)
            assertEquals(0, store.writes)
        }
        withModel(store) { model, _ ->
            assertEquals(SettingsPage.TEAM, model.notificationSettings.value.editor?.page)
            assertNull(model.notificationSettings.value.editor?.draft?.preferredTeamId)
            assertTrue(model.notificationSettings.value.editor!!.draft.isEnabled(HOME_RUN))
        }
    }

    @Test fun pendingSaveKeepsCommittedValuesAndIgnoresDuplicateSaveAndBack() = runTest {
        val original = NotificationPreferences(preferredTeamId = "KIA", onboardingCompleted = true)
        val store = Store(original)
        withModel(store) { model, _ ->
            model.showNotificationSettings(true)
            model.disableAllNotifications()
            store.gate = CompletableDeferred()
            model.saveSettings()
            runCurrent()
            assertTrue(model.notificationSettings.value.busy)
            assertEquals(original, model.notificationSettings.value.preferences)
            model.saveSettings()
            model.navigateBack()
            assertTrue(model.showNotificationSettings.value)
            model.enableAllNotifications() // disabled while committing
            assertEquals(0, model.notificationSettings.value.editor!!.draft.enabledCount)
            store.gate!!.complete(Unit)
            runCurrent()
            assertEquals(1, store.writes)
            assertEquals(0, model.notificationSettings.value.preferences?.enabledCount)
            assertFalse(model.showNotificationSettings.value)
        }
    }

    @Test fun exitingWithoutSaveKeepsTeamTogglesAndCompletedState() = runTest {
        val original = NotificationPreferences(preferredTeamId = "KIA", onboardingCompleted = true)
        val store = Store(original)
        withModel(store) { model, _ ->
            model.showNotificationSettings(true)
            model.disableAllNotifications()
            model.setHideWhenLosing(true)
            model.editTeamInSettings()
            model.chooseTeam("SSG")
            model.teamNext()
            assertEquals(original, model.notificationSettings.value.preferences)
            model.navigateBack()
            assertFalse(model.showNotificationSettings.value)
            assertEquals(original, store.saved)
            model.showNotificationSettings(true)
            assertEquals(original, model.notificationSettings.value.editor?.draft)
            model.showNotificationSettings(false)
            model.showTeamSelection()
            model.chooseTeam("LG")
            model.navigateBack()
            assertEquals(original, store.saved)
            assertEquals(0, store.writes)
        }
    }

    @Test fun failedFirstSaveRetainsDraftAndDoesNotCompleteUntilRetrySucceeds() = runTest {
        val store = Store(NotificationPreferences())
        withModel(store) { model, _ ->
            model.chooseTeam("KIA")
            model.teamNext()
            model.setCategoryEnabled(NotificationCategory.FLOW, false)
            val draft = model.notificationSettings.value.editor!!.draft
            store.fail = true
            model.saveSettings()
            runCurrent()
            assertTrue(model.notificationSettings.value.error)
            assertEquals(draft, model.notificationSettings.value.editor?.draft)
            assertFalse(store.saved.onboardingCompleted)
            assertNull(store.saved.preferredTeamId)
            assertTrue(model.showNotificationSettings.value)
            store.fail = false
            model.saveSettings()
            runCurrent()
            assertTrue(store.saved.onboardingCompleted)
            assertEquals("KIA", store.saved.preferredTeamId)
            assertEquals(draft.disabledTypes, store.saved.disabledTypes)
        }
    }

    @Test fun resavingChangesActualDeliveryOnlyAfterSaveAndDoesNotReplayPastEvents() = runTest {
        val store = Store(NotificationPreferences(onboardingCompleted = true))
        withModel(store) { model, sent ->
            model.selectGame("mock-special")
            runCurrent()
            model.selectPreferredTeam("busan")
            model.showNotificationSettings(true)
            model.setNotificationEnabled(VIDEO_REVIEW, false)
            model.play()
            runCurrent()
            assertEquals(listOf("review-start"), sent.map { it.eventId }) // draft is not active
            model.saveSettings()
            runCurrent()
            assertFalse(store.saved.isEnabled(VIDEO_REVIEW))
            advanceTimeBy(6_001)
            runCurrent()
            assertFalse(sent.any { it.eventId == "review-result" })
            model.showNotificationSettings(true)
            model.setNotificationEnabled(VIDEO_REVIEW, true)
            model.saveSettings()
            runCurrent()
            assertTrue(store.saved.isEnabled(VIDEO_REVIEW))
            assertFalse(sent.any { it.eventId == "review-result" })
        }
    }

    @Test fun storedTeamIsUsedGloballyAndNeverOverwrittenByUnrelatedMockGames() = runTest {
        val store = Store(NotificationPreferences(preferredTeamId = "KIA", onboardingCompleted = true))
        withModel(store) { model, _ ->
            model.selectGame("pdf-snapshot-test")
            runCurrent()
            assertEquals("KIA", (model.detail.value as GameDetailState.Loaded).preferredTeamId)
            model.selectGame("mock-001")
            runCurrent()
            assertEquals("KIA", store.saved.preferredTeamId)
            model.selectPreferredTeam("busan") // explicit replay-only perspective
            assertEquals("KIA", model.notificationSettings.value.preferences?.preferredTeamId)
            model.showTeamSelection()
            model.chooseTeam("LG")
            model.saveSettings()
            runCurrent()
            assertEquals("LG", store.saved.preferredTeamId)
            assertEquals("LG", (model.detail.value as GameDetailState.Loaded).preferredTeamId)
        }
    }

    @Test fun categoryMappingCoversExactlySixteenTogglesAndTenTeams() {
        val types = NotificationCategory.entries.flatMap { it.types }
        assertEquals(configurableNotificationTypes.toSet(), types.toSet())
        assertEquals(16, types.size)
        assertEquals(16, types.distinct().size)
        assertEquals(3, NotificationCategory.entries.count { it.types.isEmpty() })
        assertEquals(10, KboTeams.all.map { it.id }.distinct().size)
    }

    @Test fun notificationWaitsForFirstSaveThenOpensTheRequestedDetail() = runTest {
        val store = Store(NotificationPreferences())
        withModel(store) { model, _ ->
            model.openNotificationGame("mock-close")
            assertNull(model.detail.value)
            model.chooseTeam("SSG")
            model.teamNext()
            model.saveSettings()
            runCurrent()
            assertEquals("mock-close", model.currentGameId)
            assertFalse(model.showNotificationSettings.value)
            model.navigateBack()
            assertEquals(MainScreen.HOME, model.mainScreen.value)
        }
    }

    private suspend fun TestScope.withModel(store: NotificationPreferencesRepository,
        block: suspend TestScope.(GameViewModel, List<NotificationCandidate>) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = MockGameRepository(StandardTestDispatcher(testScheduler)) { file ->
            requireNotNull(javaClass.getResource("/games/$file")).readText(Charsets.UTF_8)
        }
        val sent = mutableListOf<NotificationCandidate>()
        val model = GameViewModel(repository, testScheduler.timeSource,
            NotificationDispatcher { sent.add(it); DeliveryStatus.SENT }, store)
        try { runCurrent(); block(model, sent) }
        finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
