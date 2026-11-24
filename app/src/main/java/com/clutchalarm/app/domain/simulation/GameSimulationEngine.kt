package com.clutchalarm.app.domain.simulation

import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class PlaybackStatus { IDLE, PLAYING, PAUSED, COMPLETED }

enum class PlaybackSpeed(val multiplier: Int) {
    NORMAL(1), DOUBLE(2), FIVE_TIMES(5), TEN_TIMES(10),
}

data class PlaybackState(
    val gameState: GameState,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val elapsedMs: Long = 0,
    val lastEvent: GameEvent? = null,
    val processedEventCount: Int = 0,
    val speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
)

/** The caller owns [scope] and cancels it when this engine is no longer needed. */
class GameSimulationEngine(
    timeline: GameTimeline,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val ownerJob = requireNotNull(scope.coroutineContext[Job]) { "Scope must have a Job" }
    private val initialState = PlaybackState(timeline.initialState)
    private val timelineEvents = timeline.events.toList()
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(initialState)
    val state = mutableState.asStateFlow()

    private data class QueuedEvent(val session: Long, val event: GameEvent)

    private val session = MutableStateFlow(0L)
    private val eventQueue = Channel<QueuedEvent>(Channel.UNLIMITED)
    private val queuedEvents = eventQueue.receiveAsFlow()

    /** Ordered observation by one consumer. Use [collectEvents] for session-safe side effects. */
    val events = queuedEvents.filter { it.session == session.value }.map { it.event }

    /**
     * Consumes each current-session event in order. The callback runs under the same lock as
     * reset and play, so session changes cannot race a synchronous side effect. Keep it brief.
     * Use this or [events] as the single consumer of the engine's event queue.
     */
    suspend fun collectEvents(onEvent: suspend (GameEvent) -> Unit) {
        queuedEvents.collect { queued ->
            mutex.withLock {
                if (queued.session == session.value) onEvent(queued.event)
            }
        }
    }

    private var worker: Job? = null
    private var clock: PlaybackClock? = null

    init {
        ownerJob.invokeOnCompletion { eventQueue.close() }
    }

    /** Starts a new session at offset zero, even if playback was already active. */
    suspend fun play(onSessionStarted: () -> Unit = {}): Unit = mutex.withLock {
        ownerJob.ensureActive()
        invalidateSession()
        onSessionStarted()
        startWorker(initialState.copy(speed = state.value.speed))
    }

    suspend fun pause(): Unit = mutex.withLock {
        val current = state.value
        if (current.status != PlaybackStatus.PLAYING) return@withLock

        val nextOffset = timelineEvents[current.processedEventCount].offsetMs
        val position = checkNotNull(clock).positionMs(nextOffset)
        stopWorker()
        mutableState.value = current.copy(status = PlaybackStatus.PAUSED, elapsedMs = position)
    }

    suspend fun resume(): Unit = mutex.withLock {
        ownerJob.ensureActive()
        val current = state.value
        if (current.status == PlaybackStatus.PAUSED) startWorker(current)
    }

    suspend fun setSpeed(speed: PlaybackSpeed): Unit = mutex.withLock {
        ownerJob.ensureActive()
        val current = state.value
        if (current.speed == speed) return@withLock
        if (current.status != PlaybackStatus.PLAYING) {
            mutableState.value = current.copy(speed = speed)
            return@withLock
        }

        val position = checkNotNull(clock).positionMs(timelineEvents.last().offsetMs)
        val nextOffset = timelineEvents[current.processedEventCount].offsetMs
        stopWorker()
        // Keep the cursor and session. Only the clock and the worker's pending wait change.
        startWorker(current.copy(speed = speed, elapsedMs = minOf(position, nextOffset)), position)
    }

    suspend fun reset(onSessionReset: () -> Unit = {}): Unit = mutex.withLock {
        invalidateSession()
        mutableState.value = initialState
        onSessionReset()
    }

    private fun invalidateSession() {
        stopWorker()
        session.value++
        while (eventQueue.tryReceive().isSuccess) {
            // Drop events waiting for a consumer from the previous session.
        }
    }

    private fun stopWorker() {
        worker?.cancel()
        worker = null
        clock = null
    }

    private fun startWorker(snapshot: PlaybackState, startOffsetMs: Long = snapshot.elapsedMs) {
        if (timelineEvents.isEmpty()) {
            mutableState.value = snapshot.copy(status = PlaybackStatus.COMPLETED)
            return
        }
        val playbackClock = PlaybackClock(startOffsetMs, timeSource.markNow(), snapshot.speed)
        clock = playbackClock
        mutableState.value = snapshot.copy(status = PlaybackStatus.PLAYING)
        worker = scope.launch {
            while (true) {
                val waitMs = mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    advance(playbackClock)
                } ?: break
                if (waitMs > 0) delay(waitMs)
            }
        }
    }

    // Called under the mutex so a cancelled worker cannot commit after pause or reset.
    private fun advance(playbackClock: PlaybackClock): Long? {
        val current = state.value
        val event = timelineEvents[current.processedEventCount]
        val position = playbackClock.positionMs(event.offsetMs)
        val remainingMs = event.offsetMs - position
        if (remainingMs > 0) {
            mutableState.value = current.copy(elapsedMs = position)
            val realWaitMs = 1 + (remainingMs - 1) / current.speed.multiplier
            return minOf(realWaitMs, POSITION_UPDATE_INTERVAL_MS)
        }

        val count = current.processedEventCount + 1
        val completed = count == timelineEvents.size
        mutableState.value = PlaybackState(
            gameState = event.after,
            status = if (completed) PlaybackStatus.COMPLETED else PlaybackStatus.PLAYING,
            elapsedMs = event.offsetMs,
            lastEvent = event,
            processedEventCount = count,
            speed = current.speed,
        )
        check(eventQueue.trySend(QueuedEvent(session.value, event)).isSuccess)
        return if (completed) null else 0L
    }

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 50L
    }
}

private class PlaybackClock(
    private val startOffsetMs: Long,
    private val startedAt: TimeMark,
    private val speed: PlaybackSpeed,
) {
    fun positionMs(nextOffsetMs: Long): Long {
        if (startOffsetMs >= nextOffsetMs) return nextOffsetMs
        val elapsedMs = (startedAt.elapsedNow() * speed.multiplier).inWholeMilliseconds
        return startOffsetMs + elapsedMs.coerceIn(0, nextOffsetMs - startOffsetMs)
    }
}
