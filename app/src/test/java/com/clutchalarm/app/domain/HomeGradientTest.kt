package com.clutchalarm.app.domain

import com.clutchalarm.app.domain.model.HomeGradients
import com.clutchalarm.app.domain.model.TeamBrands
import com.clutchalarm.app.domain.model.KboTeams
import org.junit.Assert.*
import org.junit.Test

class HomeGradientTest {
    @Test fun everyClubHasExactlyOneApprovedGradient() {
        assertEquals(KboTeams.all.map { it.id }.toSet(), HomeGradients.all.map { it.teamId }.toSet())
        assertEquals(10, HomeGradients.all.size)
        assertNull(HomeGradients.find(null))
        assertNull(HomeGradients.find("mock-away"))
    }

    @Test fun approvedFourClubColorsAndGuideOpacityRemainUnchanged() {
        for ((id, top, bottom) in listOf(
            Triple("HANWHA", 0xB04319, 0x763019), Triple("SAMSUNG", 0x1556A0, 0x082D60),
            Triple("KIA", 0xCE1936, 0x800D25), Triple("KT", 0x3A3A3A, 0x141414),
        )) {
            assertEquals(id, top, HomeGradients.find(id)!!.top)
            assertEquals(id, bottom, HomeGradients.find(id)!!.bottom)
        }
        assertEquals(.12, com.clutchalarm.app.domain.model.HomeGradient.GUIDE_ALPHA, 0.0)
    }

    @Test fun whiteTextOnGradientAndCompositedGuideMeetsNormalTextContrast() {
        for (palette in HomeGradients.all) for (step in 0..255) {
            val background = palette.backgroundAt(step / 255.0)
            assertTrue(palette.teamId, TeamBrands.contrast(0xFFFFFF, background) >= 4.5)
            assertTrue(palette.teamId, TeamBrands.contrast(0xFFFFFF, palette.guideBackground(background)) >= 4.5)
        }
    }
}
