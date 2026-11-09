package com.clutchalarm.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.repository.GameRepository
import com.clutchalarm.app.domain.simulation.GameSimulationEngine
import com.clutchalarm.app.domain.simulation.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

sealed interface GameListState {
    data object Loading : GameListState
    data object Error : GameListState
    data class Loaded(val games: List<GameState>) : GameListState
}

sealed interface GameDetailState {
    data object Loading : GameDetailState
    data object Error : GameDetailState
    data class Loaded(
        val playback: PlaybackState,
        val latestEvent: GameEvent? = null,
    ) : GameDetailState
}

class GameViewModel(
    private val repository: GameRepository,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {
    private val mutableGames = MutableStateFlow<GameListState>(GameListState.Loading)
    val games = mutableGames.asStateFlow()

    // Null means the list is the active screen.
    private val mutableDetail = MutableStateFlow<GameDetailState?>(null)
    val detail = mutableDetail.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var engine: GameSimulationEngine? = null

    init {
        loadGames()
    }

    fun loadGames() {
        listJob?.cancel()
        mutableGames.value = GameListState.Loading
        listJob = viewModelScope.launch {
            val result = try {
                GameListState.Loaded(repository.listGames())
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
        closeGame()
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
                mutableDetail.value = GameDetailState.Loaded(simulation.state.value)

                launch {
                    simulation.state.collect { playback ->
                        updateDetail(simulation) { it.copy(playback = playback) }
                    }
                }
                simulation.collectEvents { event ->
                    updateDetail(simulation) {
                        it.copy(playback = simulation.state.value, latestEvent = event)
                    }
                }
            }
        }
    }

    fun closeGame() {
        engine = null
        detailJob?.cancel()
        detailJob = null
        mutableDetail.value = null
    }

    fun play() = control { simulation ->
        updateDetail(simulation) { it.copy(latestEvent = null) }
        simulation.play()
    }

    fun pause() = control { it.pause() }

    fun resume() = control { it.resume() }

    fun reset() = control { simulation ->
        simulation.reset()
        updateDetail(simulation) { GameDetailState.Loaded(simulation.state.value) }
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
