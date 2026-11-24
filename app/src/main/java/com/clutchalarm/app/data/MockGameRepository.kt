package com.clutchalarm.app.data

import android.content.res.AssetManager
import com.clutchalarm.app.domain.model.EventType
import com.clutchalarm.app.domain.model.GameEvent
import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameTimeline
import com.clutchalarm.app.domain.model.PlayDetails
import com.clutchalarm.app.domain.model.Player
import com.clutchalarm.app.domain.repository.GameRepository
import com.clutchalarm.app.domain.repository.GameListing
import com.clutchalarm.app.domain.repository.GameScenario
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MockGameRepository private constructor(
    private val ioDispatcher: CoroutineDispatcher,
    loadGames: () -> List<Pair<GameTimeline, GameScenario?>>,
) : GameRepository {
    constructor(readGameJson: () -> String) : this(Dispatchers.IO, loadGames = {
        listOf(decodeTimeline(readGameJson()) to null)
    })

    constructor(assets: AssetManager) : this(Dispatchers.IO, readAsset = { fileName ->
        assets.open("games/$fileName").bufferedReader(Charsets.UTF_8).use { it.readText() }
    })

    internal constructor(ioDispatcher: CoroutineDispatcher, readAsset: (String) -> String) :
        this(ioDispatcher, loadGames = {
            Json.decodeFromString<List<ScenarioEntry>>(readAsset("catalog.json")).map { entry ->
                val timeline = decodeTimeline(readAsset(entry.file))
                timeline to GameScenario(entry.name, entry.description)
            }
        })

    private val games by lazy {
        val entries = loadGames()
        entries.associateBy { (timeline, _) -> timeline.initialState.gameId }.also {
            require(it.size == entries.size) { "Duplicate game ID in the mock catalog" }
        }
    }

    override suspend fun listGames(): List<GameState> = listScenarios().map { it.state }

    override suspend fun listScenarios(): List<GameListing> =
        withContext(ioDispatcher) {
            games.values.map { (timeline, scenario) -> GameListing(timeline.initialState, scenario) }
        }

    override suspend fun getGame(gameId: String): GameTimeline? =
        withContext(ioDispatcher) { games[gameId]?.first }
}

@Serializable
private data class ScenarioEntry(val file: String, val name: String, val description: String)

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

private fun decodeTimeline(json: String): GameTimeline =
    if (kotlinx.serialization.json.Json.parseToJsonElement(json).let {
        it is kotlinx.serialization.json.JsonObject && "game_info" in it
    }) SnapshotGameAdapter.decode(json)
    else Json.decodeFromString<GameFile>(json).toTimeline()
