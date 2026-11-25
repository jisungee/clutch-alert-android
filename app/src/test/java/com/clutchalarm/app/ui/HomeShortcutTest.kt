package com.clutchalarm.app.ui

import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.behavior.*
import com.clutchalarm.app.data.MockGameRepository
import com.clutchalarm.app.domain.notification.*
import com.clutchalarm.app.domain.simulation.PlaybackStatus
import com.clutchalarm.app.notification.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeShortcutTest {
    private val targets = mapOf(
        "DOOSAN" to Triple("20260329_NC_DOOSAN_01", "2026-03-29", "NC"),
        "HANWHA" to Triple("20260328_HANWHA_KIWOOM_01", "2026-03-28", "KIWOOM"),
        "KIA" to Triple("20260405_KIA_NC_01", "2026-04-05", "NC"),
        "KIWOOM" to Triple("20260401_SSG_KIWOOM_01", "2026-04-01", "SSG"),
        "KT" to Triple("20260328_LG_KT_01", "2026-03-28", "LG"),
        "LG" to Triple("20260401_LG_KIA_01", "2026-04-01", "KIA"),
        "LOTTE" to Triple("20260328_SAMSUNG_LOTTE_01", "2026-03-28", "SAMSUNG"),
        "NC" to Triple("20260328_NC_DOOSAN_01", "2026-03-28", "DOOSAN"),
        "SAMSUNG" to Triple("20260401_SAMSUNG_DOOSAN_01", "2026-04-01", "DOOSAN"),
        "SSG" to Triple("20260328_SSG_KIA_01", "2026-03-28", "KIA"),
    )
    private fun settings(team: String? = "KIWOOM") = InMemoryNotificationPreferencesRepository(
        NotificationPreferences(preferredTeamId = team, onboardingCompleted = true, notificationSettingsSaved = true),
    )
    private fun TestScope.model(settings: NotificationPreferencesRepository, events: MutableList<BehaviorEvent>,
        posts: MutableList<NotificationCandidate>, opened: MutableList<String> = mutableListOf()): GameViewModel {
        val repo = MockGameRepository(StandardTestDispatcher(testScheduler)) { file ->
            check(file != "test-catalog.json")
            requireNotNull(javaClass.getResource("/games/$file")).readText(Charsets.UTF_8)
        }
        val seen = mutableSetOf<String>()
        return GameViewModel(repo, testScheduler.timeSource,
            NotificationDispatcher { posts.add(it); DeliveryStatus.SENT }, settings,
            notificationOpens = NotificationOpenRepository { opened.add(it.postId); seen.add(it.postId) },
            behaviorEvents = BehaviorEventRecorder { events.add(it) })
    }
    private fun GameViewModel.shortcut() = (games.value as GameListState.Loaded).homeShortcut(notificationSettings.value.preferences?.preferredTeamId)
    private fun GameViewModel.loaded() = detail.value as GameDetailState.Loaded

    @Test fun allTenSavedTeamsOpenTheCorrectIdleDetailWithoutPublishingOrReadingNotifications() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<BehaviorEvent>()
        val posts = mutableListOf<NotificationCandidate>()
        val opened = mutableListOf<String>()
        val model = model(settings(), events, posts, opened)
        try {
            runCurrent()
            assertEquals(10, (model.games.value as GameListState.Loaded).games.size)
            for ((teamId, expected) in targets) {
                model.showTeamSelection(); model.chooseTeam(teamId); model.saveSettings(); runCurrent()
                val shortcut = requireNotNull(model.shortcut())
                assertEquals(expected.first, shortcut.gameId)
                assertEquals(expected.second, shortcut.date)
                assertEquals(expected.third, shortcut.opponent.id)
                model.openRecommendedGame(); runCurrent()
                assertEquals(expected.first, model.currentGameId)
                assertEquals(teamId, model.loaded().preferredTeamId)
                assertEquals(PlaybackStatus.IDLE, model.loaded().playback.status)
                assertEquals(0, model.loaded().playback.processedEventCount)
                assertEquals(DetailEntrySource.LIST, model.detailEntry.value!!.source)
                val entry = model.detailEntry.value!!
                model.confirmDetailVisible(entry.gameId, entry.requestId); runCurrent()
                assertEquals(NotificationReadStatus.NOT_APPLICABLE, model.detailEntry.value!!.readStatus)
                advanceTimeBy(250_000); runCurrent()
                assertEquals(PlaybackStatus.IDLE, model.loaded().playback.status)
                model.navigateBack()
                assertEquals(MainScreen.HOME, model.mainScreen.value)
                assertNull(model.detail.value)
            }
            assertTrue(posts.isEmpty()); assertTrue(opened.isEmpty()); assertTrue(events.isEmpty())
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun unsavedTeamKeepsTheOldShortcutSavedTeamUpdatesImmediatelyAndRestores() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = settings()
        val events = mutableListOf<BehaviorEvent>()
        val posts = mutableListOf<NotificationCandidate>()
        var model = model(saved, events, posts)
        try {
            runCurrent()
            model.showTeamSelection(); model.chooseTeam("HANWHA")
            assertEquals(targets.getValue("KIWOOM").first, model.shortcut()!!.gameId)
            model.navigateBack()
            assertEquals("KIWOOM", saved.load().preferredTeamId)
            model.showTeamSelection(); model.chooseTeam("HANWHA"); model.saveSettings(); runCurrent()
            assertEquals(targets.getValue("HANWHA").first, model.shortcut()!!.gameId)
            model.openRecommendedGame(); runCurrent(); model.navigateBack()
            assertEquals(MainScreen.HOME, model.mainScreen.value)
            model.viewModelScope.cancel(); runCurrent()
            model = model(saved, events, posts); runCurrent()
            assertEquals(targets.getValue("HANWHA").first, model.shortcut()!!.gameId)
            model.openRecommendedGame(); runCurrent()
            assertEquals(targets.getValue("HANWHA").first, model.currentGameId)
            assertEquals(PlaybackStatus.IDLE, model.loaded().playback.status)
            model.navigateBack(); model.showGames()
            assertEquals(MainScreen.GAMES, model.mainScreen.value)
            assertEquals(10, (model.games.value as GameListState.Loaded).games.size)
            assertTrue(events.isEmpty()); assertTrue(posts.isEmpty())
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun explicitPlaybackAndNotificationReturnKeepTheirExistingBehavior() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<BehaviorEvent>()
        val posts = mutableListOf<NotificationCandidate>()
        val opened = mutableListOf<String>()
        val model = model(settings(), events, posts, opened)
        try {
            runCurrent(); model.openRecommendedGame(); runCurrent()
            assertTrue(posts.isEmpty())
            model.play(); advanceTimeBy(5_001); runCurrent()
            assertEquals(PlaybackStatus.PLAYING, model.loaded().playback.status)
            assertTrue(posts.isNotEmpty())
            model.pause(); runCurrent()
            val state = model.loaded().playback
            val delivery = NotificationDelivery(model.currentGameId!!, "qa:1", 1, "qa")
            model.openNotification(delivery); runCurrent()
            assertEquals(state, model.loaded().playback)
            val entry = model.detailEntry.value!!
            assertTrue(opened.isEmpty())
            model.confirmDetailVisible(entry.gameId, entry.requestId); runCurrent()
            assertEquals(NotificationReadStatus.NEW, model.detailEntry.value!!.readStatus)
            model.openNotification(delivery); runCurrent()
            val duplicate = model.detailEntry.value!!
            model.confirmDetailVisible(duplicate.gameId, duplicate.requestId); runCurrent()
            assertEquals(NotificationReadStatus.DUPLICATE, model.detailEntry.value!!.readStatus)
            assertEquals(state, model.loaded().playback)
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }

    @Test fun noSavedTeamOrMissingRecommendationDoesNotOpenOrPlayAnotherTeamGame() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<BehaviorEvent>()
        val posts = mutableListOf<NotificationCandidate>()
        val model = model(settings(null), events, posts)
        try {
            model.openRecommendedGame()
            assertNull(model.detail.value)
            runCurrent()
            assertNull(model.shortcut())
            model.openRecommendedGame(); runCurrent()
            assertNull(model.detail.value)
            assertTrue(events.isEmpty()); assertTrue(posts.isEmpty())
        } finally { model.viewModelScope.cancel(); runCurrent(); Dispatchers.resetMain() }
    }
}
