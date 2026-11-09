package com.clutchalarm.app.domain.simulation

import com.clutchalarm.app.domain.model.Bases
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameStatus
import com.clutchalarm.app.domain.model.GameTimeline
import com.clutchalarm.app.domain.model.InningHalf
import com.clutchalarm.app.domain.model.Team
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameSimulationEngineTest {
    @Test
    fun appliesAfterStatesAtAbsoluteOffsets() = runTest {
        val timeline = timeline(100, 350, 500)
        val engine = engine(timeline)
        val received = collectEvents(engine)
        engine.play()

        step(99)
        assertEquals(0, engine.state.value.processedEventCount)
        step(1)
        assertEquals(timeline.events[0].after, engine.state.value.gameState)
        assertEquals(timeline.events[0], engine.state.value.lastEvent)
        step(249)
        assertEquals(1, received.size)
        step(1)
        assertEquals(timeline.events[1].after, engine.state.value.gameState)
        step(150)
        assertEquals(timeline.events, received)
    }

    @Test
    fun pauseFreezesClockAndResumeWaitsOnlyForTheRemainder() = runTest {
        val engine = engine(timeline(100, 300))
        val received = collectEvents(engine)
        engine.play()
        step(173)
        assertEquals(150L, engine.state.value.elapsedMs)
        engine.pause()
        val paused = engine.state.value
        assertEquals(PlaybackStatus.PAUSED, paused.status)
        assertEquals(173L, paused.elapsedMs)

        step(1000)
        assertEquals(paused, engine.state.value)
        assertEquals(1, received.size)

        engine.resume()
        step(126)
        assertEquals(1, received.size)
        step(1)
        assertEquals(2, received.size)
        assertEquals(300L, engine.state.value.elapsedMs)
    }

    @Test
    fun resetRestoresInitialStateAndCancelsPendingWork() = runTest {
        val timeline = timeline(100, 300)
        val engine = engine(timeline)
        val received = collectEvents(engine)
        engine.play()
        step(150)

        engine.reset()
        assertEquals(PlaybackState(timeline.initialState), engine.state.value)
        step(1000)
        assertEquals(PlaybackState(timeline.initialState), engine.state.value)
        assertEquals(timeline.events.take(1), received)

        engine.play()
        step(99)
        assertEquals(0, engine.state.value.processedEventCount)
        step(201)
        assertEquals(timeline.events.take(1) + timeline.events, received)
    }

    @Test
    fun finalEventCompletesPlaybackEvenWhenBaseballGameIsStillInProgress() = runTest {
        val timeline = timeline(100, 200)
        val engine = engine(timeline)
        engine.play()
        step(199)
        assertEquals(PlaybackStatus.PLAYING, engine.state.value.status)
        step(1)

        assertEquals(
            PlaybackState(timeline.events.last().after, PlaybackStatus.COMPLETED, 200,
                timeline.events.last(), 2),
            engine.state.value,
        )
    }

    @Test
    fun repeatedPauseAndResumeDoNotDuplicateEvents() = runTest {
        val timeline = timeline(0, 100, 200)
        val engine = engine(timeline)
        val received = collectEvents(engine)
        engine.play()
        step(50)
        repeat(2) { engine.pause() }
        repeat(2) { engine.resume() }
        step(500)
        engine.resume()
        step(500)
        assertEquals(timeline.events, received)
        assertEquals(3, engine.state.value.processedEventCount)
    }

    @Test
    fun queuesAllEventsWithTheSameOffsetInFileOrderForALateCollector() = runTest {
        val timeline = timeline(0, 100, 100, 100)
        val engine = engine(timeline)
        engine.play()
        step(100)

        assertEquals(PlaybackStatus.COMPLETED, engine.state.value.status)
        assertEquals(timeline.events, engine.events.take(timeline.events.size).toList())
    }

    @Test
    fun playDuringPlaybackStartsANewSessionWithoutAnOldWorker() = runTest {
        val timeline = timeline(100, 300)
        val engine = engine(timeline)
        val received = collectEvents(engine)
        engine.play()
        step(50)
        engine.play()
        step(50)
        assertEquals(emptyList<GameEvent>(), received)
        step(250)
        assertEquals(timeline.events, received)
    }

    @Test
    fun cancellingTheOwnerStopsPlaybackAndClosesEventStream() = runTest {
        val owner = Job(coroutineContext[Job])
        val timeline = timeline(100, 300)
        val engine = GameSimulationEngine(timeline, CoroutineScope(coroutineContext + owner),
            testScheduler.timeSource)
        val received = mutableListOf<GameEvent>()
        val collector = launch { engine.events.toList(received) }
        engine.play()
        step(150)
        owner.cancel()
        step(500)

        assertEquals(timeline.events.take(1), received)
        assertFalse(collector.isActive)
    }

    @Test
    fun emptyTimelineCompletesImmediately() = runTest {
        val timeline = timeline()
        val engine = engine(timeline)
        engine.play()
        assertEquals(PlaybackState(timeline.initialState, PlaybackStatus.COMPLETED), engine.state.value)
    }

    @Test
    fun resetAndReplayDiscardBufferedEventsFromPreviousSessions() = runTest {
        val timeline = timeline(0, 0, 100)
        val engine = engine(timeline)
        engine.play()
        step(0)
        engine.reset()

        val received = mutableListOf<GameEvent>()
        val collector = backgroundScope.launch { engine.collectEvents { received.add(it) } }
        step(0)
        assertEquals(emptyList<GameEvent>(), received)
        collector.cancel()

        engine.play()
        step(0)
        engine.play()
        backgroundScope.launch { engine.collectEvents { received.add(it) } }
        step(100)
        assertEquals(timeline.events, received)
    }

    private fun TestScope.engine(timeline: GameTimeline) =
        GameSimulationEngine(timeline, this, testScheduler.timeSource)

    private fun TestScope.collectEvents(engine: GameSimulationEngine): List<GameEvent> {
        val received = mutableListOf<GameEvent>()
        backgroundScope.launch { engine.events.toList(received) }
        return received
    }

    private fun TestScope.step(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun timeline(vararg offsets: Long): GameTimeline {
        val initial = GameState(
            gameId = "simulation-test",
            homeTeam = Team("home", "Home"),
            awayTeam = Team("away", "Away"),
            homeScore = 0,
            awayScore = 0,
            inning = 1,
            half = InningHalf.TOP,
            balls = 0,
            strikes = 0,
            outs = 0,
            bases = Bases(),
            batter = null,
            pitcher = null,
            status = GameStatus.IN_PROGRESS,
        )
        var before = initial
        val events = offsets.mapIndexed { index, offset ->
            val after = before.copy(awayScore = index + 1)
            GameEvent("play-$index", initial.gameId, EventType.HOME_RUN, initial.awayTeam.id,
                emptyList(), offset, before, after).also { before = after }
        }
        return GameTimeline(initial, events)
    }
}
