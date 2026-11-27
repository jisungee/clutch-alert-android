package com.clutchalarm.app.ui

import com.clutchalarm.app.behavior.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.Team
import com.clutchalarm.app.domain.model.KboTeams
import com.clutchalarm.app.domain.notification.NotificationSession
import com.clutchalarm.app.domain.notification.NotificationCandidate
import com.clutchalarm.app.domain.notification.InMemoryNotificationPreferencesRepository
import com.clutchalarm.app.domain.notification.NotificationPreferences
import com.clutchalarm.app.domain.notification.NotificationPreferencesRepository
import com.clutchalarm.app.domain.notification.NotificationType
import com.clutchalarm.app.domain.repository.GameRepository
import com.clutchalarm.app.domain.repository.GameScenario
import com.clutchalarm.app.domain.simulation.GameSimulationEngine
import com.clutchalarm.app.domain.simulation.PlaybackState
import com.clutchalarm.app.domain.simulation.PlaybackSpeed
import com.clutchalarm.app.notification.DeliveryStatus
import com.clutchalarm.app.notification.NotificationDispatcher
import com.clutchalarm.app.notification.NotificationRecord
import com.clutchalarm.app.notification.NotificationSubmission
import com.clutchalarm.app.notification.PublicationStatus
import com.clutchalarm.app.notification.GameDetailEntry
import com.clutchalarm.app.notification.DetailEntrySource
import com.clutchalarm.app.notification.NotificationReadStatus
import com.clutchalarm.app.notification.NotificationDelivery
import com.clutchalarm.app.notification.NotificationOpenRepository
import com.clutchalarm.app.notification.InMemoryNotificationOpenRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.IOException
import kotlin.time.TimeSource

enum class MainScreen { HOME, GAMES, MY_PAGE }

sealed interface GameListState {
    data object Loading : GameListState
    data object Error : GameListState
    data class Loaded(
        val games: List<GameState>,
        val scenarios: Map<String, GameScenario> = emptyMap(),
    ) : GameListState {
        val replayGames get() = games
        fun recommendedGames(teamId: String?) = replayGames.filter {
            teamId != null && scenarios[it.gameId]?.preparedForTeamId == teamId
        }
        fun homeShortcut(teamId: String?): HomeGameShortcut? {
            val game = recommendedGames(teamId).firstOrNull() ?: return null
            if (teamId != game.awayTeam.id && teamId != game.homeTeam.id) return null
            val opponent = if (teamId == game.homeTeam.id) game.awayTeam else game.homeTeam
            return HomeGameShortcut(game.gameId, scenarios[game.gameId]?.date, opponent)
        }
    }
}

data class HomeGameShortcut(val gameId: String, val date: String?, val opponent: Team)

sealed interface GameDetailState {
    data object Loading : GameDetailState
    data object Error : GameDetailState
    data class Loaded(
        val playback: PlaybackState,
        val latestEvent: GameEvent? = null,
        val preferredTeamId: String? = null,
        val notifications: List<NotificationRecord> = emptyList(),
        val notificationRound: Long = 0,
        val notificationsEnabled: Boolean = true,
    ) : GameDetailState {
        val sentNotifications: List<NotificationRecord>
            get() = notifications.asReversed().filter { it.status == DeliveryStatus.SENT }
    }
}

data class NotificationSettingsState(
    val preferences: NotificationPreferences? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
    val editor: SettingsEditor? = null,
)

class GameViewModel(
    private val repository: GameRepository,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val dispatcher: NotificationDispatcher = NotificationDispatcher { DeliveryStatus.DISABLED },
    private val preferencesRepository: NotificationPreferencesRepository = InMemoryNotificationPreferencesRepository(),
    private val notificationOpens: NotificationOpenRepository = InMemoryNotificationOpenRepository(),
    private val onDetailEntry: (GameDetailEntry) -> Unit = {},
    private val behaviorEvents: BehaviorEventRecorder = NoBehaviorEvents,
) : ViewModel() {
    private val mutableGames = MutableStateFlow<GameListState>(GameListState.Loading)
    val games = mutableGames.asStateFlow()

    private val mutableMainScreen = MutableStateFlow(MainScreen.HOME)
    val mainScreen = mutableMainScreen.asStateFlow()

    fun showGames() {
        closeGame()
        mutableMainScreen.value = MainScreen.GAMES
    }

    fun showHome() {
        closeGame()
        mutableMainScreen.value = MainScreen.HOME
    }

    fun showMyPage() {
        closeGame()
        mutableMainScreen.value = MainScreen.MY_PAGE
    }

    fun restoreMainScreen(screen: MainScreen) {
        mutableMainScreen.value = screen
    }

    fun onLoginCompleted() {
        if (pendingNotificationEntry != null) {
            if (notificationSettings.value.preferences?.onboardingCompleted == true) openPendingNotification()
            return
        }
        val entry = detailEntry.value
        if (entry?.source == DetailEntrySource.NOTIFICATION && entry.readStatus in setOf(
                NotificationReadStatus.PENDING, NotificationReadStatus.UNCONFIRMED, NotificationReadStatus.LEGACY)) return
        showHome()
    }

    fun onSessionUnavailable() {
        val entry = pendingNotificationEntry ?: detailEntry.value
        val waiting = entry?.takeIf { it.source == DetailEntrySource.NOTIFICATION && it.readStatus in setOf(
            NotificationReadStatus.PENDING, NotificationReadStatus.UNCONFIRMED, NotificationReadStatus.LEGACY) }
        onLoggedOut()
        pendingNotificationEntry = waiting
        if (waiting != null) mutableDetailEntry.value = waiting
    }

    fun onLoggedOut() {
        pendingNotificationEntry = null
        closeGame()
        mutableMainScreen.value = MainScreen.HOME
    }

    private val mutableDetailEntry = MutableStateFlow<GameDetailEntry?>(null)
    val detailEntry = mutableDetailEntry.asStateFlow()
    private var pendingNotificationEntry: GameDetailEntry? = null
    private var entrySerial = 0L
    private val confirmingEntries = mutableSetOf<Long>()
    private val confirmedEntries = mutableSetOf<Long>()

    fun openNotificationGame(gameId: String) = openEntry(GameDetailEntry(
        gameId, DetailEntrySource.NOTIFICATION, readStatus = NotificationReadStatus.LEGACY))

    fun openNotification(delivery: NotificationDelivery) = openEntry(GameDetailEntry(
        delivery.gameId, DetailEntrySource.NOTIFICATION, delivery))

    fun restoreDetailEntry(entry: GameDetailEntry) {
        if (mutableDetailEntry.value == null) openEntry(entry)
    }

    private fun openEntry(entry: GameDetailEntry) {
        val request = entry.copy(requestId = ++entrySerial)
        if (notificationSettings.value.preferences?.onboardingCompleted != true && entry.source == DetailEntrySource.NOTIFICATION) {
            pendingNotificationEntry = request
            mutableDetailEntry.value = request
        } else loadGame(request)
    }

    /** Called only while the loaded detail is composed and its lifecycle is RESUMED. */
    fun confirmDetailVisible(gameId: String, requestId: Long) {
        val entry = detailEntry.value ?: return
        if (entry.gameId != gameId || entry.requestId != requestId || currentGameId != gameId ||
            detail.value !is GameDetailState.Loaded || showNotificationSettings.value ||
            requestId in confirmingEntries || requestId in confirmedEntries) return
        if (entry.readStatus == NotificationReadStatus.RESTORED) {
            confirmedEntries.add(requestId)
            return
        }
        confirmingEntries.add(requestId)
        viewModelScope.launch {
            try {
                val status = if (entry.delivery == null) entry.readStatus else {
                    if (notificationOpens.markOpened(entry.delivery)) NotificationReadStatus.NEW else NotificationReadStatus.DUPLICATE
                }
                confirmedEntries.add(requestId)
                val confirmed = entry.copy(readStatus = status)
                if (mutableDetailEntry.value?.requestId == requestId) mutableDetailEntry.value = confirmed
                onDetailEntry(confirmed)
            } catch (_: IOException) {
                if (mutableDetailEntry.value?.requestId == requestId) {
                    mutableDetailEntry.value = entry.copy(readStatus = NotificationReadStatus.UNCONFIRMED)
                }
            } finally {
                confirmingEntries.remove(requestId)
            }
        }
    }

    fun navigateBack() {
        when {
            showNotificationSettings.value -> editorBack()
            detail.value != null -> closeGame()
            mainScreen.value != MainScreen.HOME -> showHome()
        }
    }

    // Detail overlays the home or game selection screen until it is closed.
    private val mutableDetail = MutableStateFlow<GameDetailState?>(null)
    val detail = mutableDetail.asStateFlow()
    private val mutableNotificationSettings = MutableStateFlow(NotificationSettingsState())
    val notificationSettings = mutableNotificationSettings.asStateFlow()
    private val mutableShowNotificationSettings = MutableStateFlow(false)
    val showNotificationSettings = mutableShowNotificationSettings.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var engine: GameSimulationEngine? = null
    private val notificationSession = NotificationSession()
    private var notificationToken: NotificationSession.Token? = null
    var currentGameId: String? = null
        private set

    init {
        loadNotificationPreferences()
        loadGames()
    }

    fun loadNotificationPreferences() {
        if (notificationSettings.value.busy) return
        mutableNotificationSettings.value = notificationSettings.value.copy(busy = true, error = false)
        viewModelScope.launch {
            try {
                val saved = preferencesRepository.load()
                val editor = if (saved.onboardingCompleted) null else SettingsEditor(saved, SettingsPage.TEAM, onboarding = true)
                mutableNotificationSettings.value = NotificationSettingsState(preferences = saved, editor = editor)
                mutableShowNotificationSettings.value = editor != null
                (detail.value as? GameDetailState.Loaded)?.let {
                    mutableDetail.value = it.copy(preferredTeamId = saved.preferredTeamId)
                }
                if (saved.onboardingCompleted) openPendingNotification()
            } catch (_: IOException) {
                mutableNotificationSettings.value = notificationSettings.value.copy(busy = false, error = true)
            }
        }
    }

    fun showNotificationSettings(show: Boolean) {
        val state = notificationSettings.value
        if (state.busy) return
        if (!show) {
            if (state.editor?.onboarding == true) return
            recordDrop(state.editor)
            mutableNotificationSettings.value = state.copy(editor = null, error = false)
            mutableShowNotificationSettings.value = false
        } else if (state.editor == null) {
            val saved = state.preferences ?: return
            mutableNotificationSettings.value = state.copy(editor = SettingsEditor(saved), error = false)
            mutableShowNotificationSettings.value = true
        }
    }

    fun showTeamSelection() {
        val state = notificationSettings.value
        val saved = state.preferences ?: return
        if (state.busy) return
        recordDrop(state.editor)
        mutableNotificationSettings.value = state.copy(editor = SettingsEditor(saved, SettingsPage.TEAM, teamOnly = true), error = false)
        mutableShowNotificationSettings.value = true
    }

    private fun edit(transform: (SettingsEditor) -> SettingsEditor) {
        if (notificationSettings.value.busy) return
        if (notificationSettings.value.editor == null) showNotificationSettings(true)
        val state = notificationSettings.value
        val editor = state.editor ?: return
        mutableNotificationSettings.value = state.copy(editor = transform(editor), error = false)
    }

    fun chooseTeam(teamId: String) {
        if (KboTeams.find(teamId) != null) edit { it.copy(draft = it.draft.copy(preferredTeamId = teamId)) }
    }

    fun teamNext() = edit {
        if (KboTeams.find(it.draft.preferredTeamId) != null) it.copy(page = SettingsPage.CATEGORIES) else it
    }

    fun editTeamInSettings() = edit { it.copy(page = SettingsPage.TEAM) }
    fun openCategory(category: NotificationCategory) = edit {
        if (category.types.isEmpty()) it else it.copy(page = SettingsPage.DETAILS, category = category)
    }

    fun setNotificationEnabled(type: NotificationType, enabled: Boolean) = edit {
        it.copy(draft = it.draft.withEnabled(type, enabled), notificationTouched = true)
    }
    fun setHideWhenLosing(enabled: Boolean) = edit { it.copy(draft = it.draft.copy(hideWhenLosing = enabled), notificationTouched = true) }
    fun enableAllNotifications() = edit { it.copy(draft = it.draft.copy(disabledTypes = emptySet()), notificationTouched = true) }
    fun disableAllNotifications() = edit { it.copy(draft = it.draft.copy(disabledTypes = NotificationType.entries.toSet()), notificationTouched = true) }
    fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) = edit { editor ->
        editor.copy(draft = category.types.fold(editor.draft) { draft, type -> draft.withEnabled(type, enabled) },
            notificationTouched = editor.notificationTouched || category.types.isNotEmpty())
    }

    private val droppedFlows = mutableSetOf<String>()
    private fun recordDrop(editor: SettingsEditor?) {
        if (editor != null && editor.notificationTouched && droppedFlows.add(editor.flowId)) {
            behaviorEvents.record(BehaviorEvent("drop", flowId = editor.flowId))
        }
    }

    fun onSettingsFlowAbandoned() {
        if (!notificationSettings.value.busy) recordDrop(notificationSettings.value.editor)
    }

    fun editorBack() {
        val state = notificationSettings.value
        if (state.busy) return
        val editor = state.editor ?: return
        when {
            editor.page == SettingsPage.DETAILS -> edit { it.copy(page = SettingsPage.CATEGORIES) }
            editor.onboarding && editor.page == SettingsPage.CATEGORIES -> edit { it.copy(page = SettingsPage.TEAM) }
            editor.page == SettingsPage.TEAM && !editor.teamOnly && !editor.onboarding -> edit { it.copy(page = SettingsPage.CATEGORIES) }
            !editor.onboarding -> showNotificationSettings(false)
        }
    }

    fun saveSettings() {
        val state = notificationSettings.value
        val editor = state.editor ?: return
        if (state.busy || (editor.onboarding || editor.teamOnly) && KboTeams.find(editor.draft.preferredTeamId) == null) return
        mutableNotificationSettings.value = state.copy(busy = true, error = false)
        viewModelScope.launch {
            try {
                val resaveEvent = if (!editor.teamOnly && state.preferences?.notificationSettingsSaved == true)
                    BehaviorEvent("repeat_done", action = "settings_resave", flowId = editor.flowId) else null
                val saved = preferencesRepository.updateWithEvent(behaviorEvents, resaveEvent) {
                    editor.draft.copy(onboardingCompleted = it.onboardingCompleted || editor.onboarding,
                        notificationSettingsSaved = it.notificationSettingsSaved || !editor.teamOnly)
                }
                mutableNotificationSettings.value = NotificationSettingsState(preferences = saved)
                mutableShowNotificationSettings.value = false
                if (state.preferences?.preferredTeamId != saved.preferredTeamId) {
                    (detail.value as? GameDetailState.Loaded)?.let {
                        mutableDetail.value = it.copy(preferredTeamId = saved.preferredTeamId)
                    }
                }
                if (editor.onboarding) showHome()
                openPendingNotification()
            } catch (_: IOException) {
                mutableNotificationSettings.value = state.copy(busy = false, error = true)
            }
        }
    }

    private fun openPendingNotification() {
        val entry = pendingNotificationEntry ?: return
        pendingNotificationEntry = null
        loadGame(entry)
    }

    fun loadGames() {
        listJob?.cancel()
        mutableGames.value = GameListState.Loading
        listJob = viewModelScope.launch {
            val result = try {
                val listings = repository.listScenarios()
                GameListState.Loaded(
                    listings.map { it.state },
                    listings.mapNotNull { listing ->
                        listing.scenario?.let { listing.state.gameId to it }
                    }.toMap(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                GameListState.Error
            }
            ensureActive()
            mutableGames.value = result
        }
    }

    fun openRecommendedGame() {
        val teamId = notificationSettings.value.preferences?.preferredTeamId
        val shortcut = (games.value as? GameListState.Loaded)?.homeShortcut(teamId) ?: return
        selectGame(shortcut.gameId)
    }

    fun selectGame(gameId: String) = openEntry(GameDetailEntry(gameId, DetailEntrySource.LIST))

    private fun loadGame(entry: GameDetailEntry) {
        val gameId = entry.gameId
        showNotificationSettings(false)
        if (currentGameId == gameId && mutableDetail.value != GameDetailState.Error) {
            mutableDetailEntry.value = entry
            return
        }
        closeGame()
        mutableDetailEntry.value = entry
        currentGameId = gameId
        mutableDetail.value = GameDetailState.Loading
        detailJob = viewModelScope.launch {
            val timeline = try {
                repository.getGame(gameId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            ensureActive()
            if (timeline == null) {
                mutableDetail.value = GameDetailState.Error
                return@launch
            }

            // Cancelling the detail job cancels its engine and both collectors.
            coroutineScope {
                val simulation = GameSimulationEngine(timeline, this, timeSource)
                engine = simulation
                mutableDetail.value = GameDetailState.Loaded(simulation.state.value,
                    preferredTeamId = notificationSettings.value.preferences?.preferredTeamId,
                    notificationsEnabled = timeline.notificationsEnabled)

                launch {
                    simulation.state.collect { playback ->
                        updateDetail(simulation) { it.copy(playback = playback) }
                    }
                }
                simulation.collectEvents { event ->
                    val activeDetail = detail.value as? GameDetailState.Loaded
                    if (engine === simulation && activeDetail != null) {
                        val token = notificationToken
                        val candidate = token?.takeIf { timeline.notificationsEnabled }?.let { activeToken ->
                            notificationSession.onEvent(activeToken, event, activeDetail.preferredTeamId.orEmpty())
                        }
                        val record = if (candidate != null) dispatch(candidate, event, simulation, token) else null
                        updateDetail(simulation) {
                            it.copy(playback = simulation.state.value, latestEvent = event,
                                notifications = if (record == null) it.notifications else it.notifications + record)
                        }
                    }
                }
            }
        }
    }

    fun closeGame() {
        showNotificationSettings(false)
        engine = null
        currentGameId = null
        endNotifications()
        detailJob?.cancel()
        detailJob = null
        mutableDetail.value = null
        mutableDetailEntry.value = null
    }

    fun selectPreferredTeam(teamId: String) {
        val simulation = engine ?: return
        updateDetail(simulation) {
            val game = it.playback.gameState
            if (teamId == game.homeTeam.id || teamId == game.awayTeam.id) it.copy(preferredTeamId = teamId)
            else it
        }
    }

    fun play() = control { simulation ->
        simulation.play {
            updateDetail(simulation) {
                notificationToken = notificationSession.begin(it.playback.gameState.gameId)
                it.copy(latestEvent = null, notifications = emptyList(), notificationRound = it.notificationRound + 1)
            }
        }
    }

    fun pause() = control { it.pause() }

    fun resume() = control { it.resume() }

    fun setPlaybackSpeed(speed: PlaybackSpeed) = control { it.setSpeed(speed) }

    fun reset() = control { simulation ->
        simulation.reset {
            updateDetail(simulation) {
                endNotifications()
                it.copy(
                    playback = simulation.state.value, latestEvent = null, notifications = emptyList(),
                    notificationRound = it.notificationRound + 1,
                )
            }
        }
    }

    private fun endNotifications() {
        notificationToken?.let(notificationSession::end)
        notificationToken = null
    }

    private suspend fun dispatch(
        candidate: NotificationCandidate,
        event: GameEvent,
        simulation: GameSimulationEngine,
        token: NotificationSession.Token,
    ): NotificationRecord {
        val preferences = notificationSettings.value.preferences
        val selected = preferences?.select(candidate)
        val submission = when {
            preferences == null -> NotificationSubmission(DeliveryStatus.PREFERENCES_UNAVAILABLE)
            selected == null -> NotificationSubmission(DeliveryStatus.FILTERED)
            else -> dispatcher.submit(selected)
        }
        val record = NotificationRecord(
            candidate = selected ?: candidate,
            delivery = submission.delivery,
            status = submission.status,
            gameState = event.after,
            address = submission.address,
            publication = when {
                submission.address != null -> PublicationStatus.CHECKING
                submission.status == DeliveryStatus.SENT -> PublicationStatus.UNCONFIRMED
                else -> PublicationStatus.NOT_REQUESTED
            },
            cleanupRequestedCount = submission.cleanupRequested.size,
        )
        if (submission.address != null || submission.cleanupRequested.isNotEmpty()) {
            viewModelScope.launch {
                yield()
                val confirmation = dispatcher.confirm(submission)
                if (notificationToken !== token) return@launch
                fun update(item: NotificationRecord): NotificationRecord {
                    val current = item.candidate.id == candidate.id
                    val cleaned = item.cleaned || item.address in confirmation?.cleaned.orEmpty()
                    if (!current && cleaned == item.cleaned) return item
                    return item.copy(
                        publication = if (!current || submission.address == null) item.publication
                        else if (confirmation?.published == true) PublicationStatus.CONFIRMED
                        else PublicationStatus.UNCONFIRMED,
                        cleaned = cleaned,
                        cleanupCount = if (current) confirmation?.cleaned?.size ?: 0 else item.cleanupCount,
                        cleanupChecked = item.cleanupChecked || (current && submission.cleanupRequested.isNotEmpty()),
                    )
                }
                updateDetail(simulation) {
                    it.copy(notifications = it.notifications.map(::update))
                }
            }
        }
        return record
    }

    private fun control(action: suspend (GameSimulationEngine) -> Unit) {
        val simulation = engine ?: return
        viewModelScope.launch {
            if (engine === simulation) action(simulation)
        }
    }

    private fun updateDetail(
        simulation: GameSimulationEngine,
        update: (GameDetailState.Loaded) -> GameDetailState.Loaded,
    ) {
        val current = mutableDetail.value
        if (engine === simulation && current is GameDetailState.Loaded) {
            mutableDetail.value = update(current)
        }
    }
}
