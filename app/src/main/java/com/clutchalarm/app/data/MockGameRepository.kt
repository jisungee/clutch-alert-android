package com.clutchalarm.app.data

import android.content.res.AssetManager
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameTimeline
import com.clutchalarm.app.domain.model.PlayDetails
import com.clutchalarm.app.domain.model.Player
import com.clutchalarm.app.domain.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MockGameRepository(private val readGameJson: () -> String) : GameRepository {
    constructor(assets: AssetManager) : this({
        assets.open("games/mock-game.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    })

    private val game by lazy {
        Json.decodeFromString<GameFile>(readGameJson()).toTimeline()
    }

    override suspend fun listGames(): List<GameState> =
        withContext(Dispatchers.IO) { listOf(game.initialState) }

    override suspend fun getGame(gameId: String): GameTimeline? =
        withContext(Dispatchers.IO) { game.takeIf { it.initialState.gameId == gameId } }
}

@Serializable
private data class GameFile(val initialState: GameState, val events: List<PlayRecord>) {
    fun toTimeline(): GameTimeline {
        var previous = initialState
        val timelineEvents = events.map { record ->
            GameEvent(
                eventId = record.eventId,
                gameId = record.gameId,
                eventType = record.eventType,
                teamId = record.teamId,
                players = record.players,
                offsetMs = record.offsetMs,
                before = previous,
                after = record.after,
                details = record.details,
            ).also { previous = it.after }
        }
        return GameTimeline(initialState, timelineEvents)
    }
}

@Serializable
private data class PlayRecord(
    val eventId: String,
    val gameId: String,
    val eventType: EventType,
    val teamId: String?,
    val players: List<Player>,
    val offsetMs: Long,
    val after: GameState,
    val details: PlayDetails = PlayDetails(),
)
