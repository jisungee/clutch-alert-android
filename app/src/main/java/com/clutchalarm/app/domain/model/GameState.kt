package com.clutchalarm.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Team(val id: String, val name: String) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
    }
}

@Serializable
data class Player(val id: String, val name: String) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
    }
}

enum class InningHalf { TOP, BOTTOM }

enum class GameStatus { NOT_STARTED, IN_PROGRESS, FINISHED }

@Serializable
data class Bases(
    val first: Player? = null,
    val second: Player? = null,
    val third: Player? = null,
) {
    init {
        val occupied = listOfNotNull(first?.id, second?.id, third?.id)
        require(occupied.size == occupied.toSet().size) { "A runner cannot occupy multiple bases" }
    }
}

@Serializable
data class GameState(
    val gameId: String,
    val homeTeam: Team,
    val awayTeam: Team,
    val homeScore: Int,
    val awayScore: Int,
    val inning: Int,
    val half: InningHalf,
    val balls: Int?,
    val strikes: Int?,
    val outs: Int,
    val bases: Bases,
    val batter: Player?,
    val pitcher: Player?,
    val status: GameStatus,
) {
    init {
        require(gameId.isNotBlank())
        require(homeTeam.id != awayTeam.id) { "Home and away teams must differ" }
        require(homeScore >= 0 && awayScore >= 0) { "Scores cannot be negative" }
        require(inning >= 1) { "Inning must be positive" }
        require((balls == null || balls in 0..3) && (strikes == null || strikes in 0..2) && outs in 0..3) { "Invalid count" }
    }
}
