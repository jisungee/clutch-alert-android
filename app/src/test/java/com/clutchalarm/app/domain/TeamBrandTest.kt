package com.clutchalarm.app.domain

import com.clutchalarm.app.domain.model.KboTeams
import com.clutchalarm.app.domain.model.TeamBrands
import org.junit.Assert.*
import org.junit.Test

class TeamBrandTest {
    @Test fun allTenClubColorsMatchTheSuppliedContract() {
        assertEquals(KboTeams.all.map { it.id }.toSet(), TeamBrands.all.map { it.teamId }.toSet())
        assertEquals(listOf(0x570514, 0x074CA1, 0, 0xC40037, 0xEA0029, 0x1A1748, 0xCE0E2D,
            0x041E42, 0x315288, 0xFC4E00), TeamBrands.all.map { it.brandColor })
        assertNull(TeamBrands.find("mock-away"))
        assertNull(TeamBrands.find(null))
    }
    @Test fun allTextAndControlPairsMeetNormalAndLargeTextContrast() {
        for (team in TeamBrands.all) {
            assertTrue(team.teamId, TeamBrands.contrast(team.primaryColor, team.onPrimaryColor) >= 4.5)
            assertTrue(team.teamId, TeamBrands.contrast(team.primaryContainer, team.onPrimaryContainer) >= 4.5)
            for (surface in listOf(0xFFFFFF, 0xF7F8FA, 0xF0F1F5, team.primaryContainer)) {
                // Normal-text threshold also exceeds the 3:1 large-text target.
                assertTrue(team.teamId, TeamBrands.contrast(team.primaryColor, surface) >= 4.5)
                assertTrue(team.teamId, TeamBrands.contrast(0x172238, surface) >= 4.5)
                assertTrue(team.teamId, TeamBrands.contrast(0x555E70, surface) >= 4.5)
            }
            assertNotEquals(team.brandColor, team.primaryColor)
        }
    }
}
