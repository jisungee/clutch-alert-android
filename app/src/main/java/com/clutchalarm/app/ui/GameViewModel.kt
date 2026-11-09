package com.clutchalarm.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
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

sealed interface GameListState {
    data object Loading : GameListState
    data object Error : GameListState
    data class Loaded(
        val games: List<GameState>,
        val scenarios: Map<String, GameScenario> = emptyMap(),
    ) : GameListState
}

sealed interface GameDetailState {
    data object Loading : GameDetailState
    data object Error : GameDetailState
    data class Loaded(
        val playback: PlaybackState,
        val latestEvent: GameEvent? = null,
        val preferredTeamId: String? = null,
        val notifications: List<NotificationRecord> = emptyList(),
        val notificationHistory: List<NotificationRecord> = emptyList(),
    ) : GameDetailState
}

data class NotificationSettingsState(
    val preferences: NotificationPreferences? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
)

class GameViewModel(
    private val repository: GameRepository,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val dispatcher: NotificationDispatcher = NotificationDispatcher { DeliveryStatus.DISABLED },
    private val preferencesRepository: NotificationPreferencesRepository = InMemoryNotificationPreferencesRepository(),
) : ViewModel() {
    private val mutableGames = MutableStateFlow<GameListState>(GameListState.Loading)
    val games = mutableGames.asStateFlow()

    // Null means the list is the active screen.
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
    private val notificationHistory = mutableMapOf<String, List<NotificationRecord>>()
    private var nextNotificationRecordId = 0L
    var currentGameId: String? = null
        private set

    init {
        loadNotificationPreferences()
        loadGames()
    }

    fun loadNotificationPreferences() {
        changeNotificationPreferences { preferencesRepository.load() }
    }

    fun setNotificationEnabled(type: NotificationType, enabled: Boolean) {
        changeNotificationPreferences {
            preferencesRepository.update { it.withEnabled(type, enabled) }
        }
    }

    fun enableAllNotifications() {
        changeNotificationPreferences {
            preferencesRepository.update { NotificationPreferences() }
        }
    }

    fun disableAllNotifications() {
        changeNotificationPreferences {
            preferencesRepository.update { NotificationPreferences(NotificationType.entries.toSet()) }
        }
    }

    fun showNotificationSettings(show: Boolean) {
        mutableShowNotificationSettings.value = show
    }

    private fun changeNotificationPreferences(
        operation: suspend () -> NotificationPreferences,
    ) {
        val previous = notificationSettings.value
        if (previous.busy) return
        mutableNotificationSettings.value = previous.copy(busy = true, error = false)
        viewModelScope.launch {
            mutableNotificationSettings.value = try {
                NotificationSettingsState(preferences = operation())
            } catch (_: IOException) {
                previous.copy(busy = false, error = true)
            }
        }
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

    fun selectGame(gameId: String) {
        showNotificationSettings(false)
        if (currentGameId == gameId && mutableDetail.value != GameDetailState.Error) return
        closeGame()
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
                mutableDetail.value = GameDetailState.Loaded(
                    simulation.state.value,
                    notificationHistory = notificationHistory[gameId].orEmpty(),
                )

                launch {
                    simulation.state.collect { playback ->
                        updateDetail(simulation) { it.copy(playback = playback) }
                    }
                }
                simulation.collectEvents { event ->
                    updateDetail(simulation) {
                        val candidate = notificationToken?.let { token ->
                            notificationSession.onEvent(token, event, it.preferredTeamId.orEmpty())
                        }
                        val records = if (candidate == null) it.notifications else
                            it.notifications + dispatch(candidate)
                        it.copy(
                            playback = simulation.state.value,
                            latestEvent = event,
                            notifications = records,
                            notificationHistory = notificationHistory[gameId].orEmpty(),
                        )
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
                it.copy(latestEvent = null, notifications = emptyList())
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
                it.copy(playback = simulation.state.value, latestEvent = null, notifications = emptyList())
            }
        }
    }

    private fun endNotifications() {
        notificationToken?.let(notificationSession::end)
        notificationToken = null
    }

    private fun dispatch(candidate: NotificationCandidate): NotificationRecord {
        val preferences = notificationSettings.value.preferences
        val submission = when {
            preferences == null -> NotificationSubmission(DeliveryStatus.PREFERENCES_UNAVAILABLE)
            !preferences.isEnabled(candidate.type) -> NotificationSubmission(DeliveryStatus.FILTERED)
            else -> dispatcher.dispatchTracked(candidate)
        }
        val record = NotificationRecord(
            candidate, submission.status, submission.address,
            when {
                submission.address != null -> PublicationStatus.CHECKING
                submission.status == DeliveryStatus.SENT -> PublicationStatus.UNCONFIRMED
                else -> PublicationStatus.NOT_REQUESTED
            },
            recordId = ++nextNotificationRecordId,
        )
        notificationHistory[candidate.gameId] = notificationHistory[candidate.gameId].orEmpty() + record
        if (submission.address != null || submission.cleanupRequested.isNotEmpty()) {
            viewModelScope.launch {
                yield()
                val confirmation = dispatcher.confirm(submission)
                fun update(item: NotificationRecord): NotificationRecord {
                    val current = item.recordId == record.recordId
                    val cleaned = item.cleaned || item.address in confirmation?.cleaned.orEmpty()
                    if (!current && cleaned == item.cleaned) return item
                    return item.copy(
                        publication = if (!current || submission.address == null) item.publication
                        else if (confirmation?.published == true) PublicationStatus.CONFIRMED
                        else PublicationStatus.UNCONFIRMED,
                        cleaned = cleaned,
                        cleanupCount = if (current) confirmation?.cleaned?.size ?: 0 else item.cleanupCount,
                    )
                }
                for ((gameId, items) in notificationHistory) {
                    notificationHistory[gameId] = items.map(::update)
                }
                val simulation = engine ?: return@launch
                updateDetail(simulation) {
                    it.copy(
                        notifications = it.notifications.map(::update),
                        notificationHistory = notificationHistory[it.playback.gameState.gameId].orEmpty(),
                    )
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
