package com.clutchalarm.app.domain.repository

import com.clutchalarm.app.domain.model.GameState
import com.clutchalarm.app.domain.model.GameTimeline

interface GameRepository {
    suspend fun listGames(): List<GameState>

    /** Returns null if the requested game does not exist. */
    suspend fun getGame(gameId: String): GameTimeline?
}
