package com.clutchalarm.app.domain.model

/** Approved home-only gradients; independent of official artwork and control colors. */
data class HomeGradient(val teamId: String, val draftTop: Int, val bottom: Int) {
    val top: Int get() {
        var color = draftTop
        // Include the actual translucent white guide surface, with rounding headroom.
        while (TeamBrands.contrast(0xFFFFFF, guideBackground(color)) < 4.6) {
            color = TeamBrands.darken(color, .98)
        }
        return color
    }

    fun backgroundAt(fraction: Double): Int {
        val t = fraction.coerceIn(0.0, 1.0)
        fun channel(shift: Int) = ((top shr shift and 255) * (1 - t) + (bottom shr shift and 255) * t).toInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun guideBackground(background: Int): Int {
        fun channel(shift: Int) = ((background shr shift and 255) * (1 - GUIDE_ALPHA) + 255 * GUIDE_ALPHA).toInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    companion object { const val GUIDE_ALPHA = .12 }
}

object HomeGradients {
    val all = listOf(
        HomeGradient("HANWHA", 0xC04B1D, 0x763019),
        HomeGradient("SAMSUNG", 0x1556A0, 0x082D60),
        HomeGradient("KIA", 0xCE1936, 0x800D25),
        HomeGradient("KT", 0x3A3A3A, 0x141414),
        HomeGradient("KIWOOM", 0x831E38, 0x430B19),
        HomeGradient("LG", 0xBD1646, 0x710B2C),
        HomeGradient("DOOSAN", 0x363365, 0x15132F),
        HomeGradient("SSG", 0xC31936, 0x790E23),
        HomeGradient("LOTTE", 0x203E65, 0x071A34),
        HomeGradient("NC", 0x365B91, 0x1B3459),
    )
    fun find(teamId: String?): HomeGradient? = all.firstOrNull { it.teamId == teamId }
}
