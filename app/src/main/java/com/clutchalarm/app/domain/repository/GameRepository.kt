package com.clutchalarm.app.domain.repository

import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameTimeline

data class GameListing(
    val state: GameState,
    val scenario: GameScenario? = null,
)

data class GameScenario(
    val name: String, val description: String,
    val date: String? = null, val durationMs: Long = 0,
    val preparedForTeamId: String? = null,
)

interface GameRepository {
    suspend fun listGames(): List<GameState>

    suspend fun listScenarios(): List<GameListing> = listGames().map { GameListing(it) }

    /** Returns null if the requested game does not exist. */
    suspend fun getGame(gameId: String): GameTimeline?
}
