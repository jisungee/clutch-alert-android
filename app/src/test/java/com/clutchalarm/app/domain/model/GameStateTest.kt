package com.clutchalarm.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GameStateTest {
    @Test
    fun rejectsSameRunnerOnMultipleBases() {
        val runner = Player("p1", "김도윤")
        assertThrows(IllegalArgumentException::class.java) {
            Bases(first = runner, second = runner)
        }
    }

    @Test
    fun acceptsAnEndOfHalfInningSnapshotWithThreeOuts() {
        val state = GameState(
            gameId = "game-1",
            homeTeam = Team("home", "서울 팔콘스"),
            awayTeam = Team("away", "부산 웨이브스"),
            homeScore = 0,
            awayScore = 1,
            inning = 1,
            half = InningHalf.TOP,
            balls = 0,
            strikes = 0,
            outs = 3,
            bases = Bases(),
            batter = null,
            pitcher = Player("p2", "임건우"),
            status = GameStatus.IN_PROGRESS,
        )
        assertEquals(3, state.outs)
        assertThrows(IllegalArgumentException::class.java) { state.copy(outs = 4) }
    }
}
