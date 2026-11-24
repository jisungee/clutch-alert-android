package com.clutchalarm.app.domain.model

import kotlin.math.pow

/** Exact supplied club colors; independent of selection drafts and Android. */
data class TeamBrand(val teamId: String, val brandColor: Int) {
    /** Muted club hue for controls; official artwork and brandColor stay untouched. */
    val primaryColor: Int get() {
        var color = mix(brandColor, 0x586170, .35)
        while (listOf(0xFFFFFF, 0xF7F8FA, 0xF0F1F5, primaryContainer).any { contrast(color, it) < 4.5 }) {
            color = darken(color, .96)
        }
        return color
    }
    val onPrimaryColor: Int get() = 0xFFFFFF
    val primaryContainer: Int get() = mix(brandColor, 0xFFFFFF, .94)
    val onPrimaryContainer: Int get() = primaryColor
}

private fun mix(a: Int, b: Int, amount: Double): Int {
    fun channel(shift: Int) = (((a shr shift and 255) * (1 - amount)) + ((b shr shift and 255) * amount)).toInt()
    return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

object TeamBrands {
    val all = listOf(
        TeamBrand("KIWOOM", 0x570514), TeamBrand("SAMSUNG", 0x074CA1), TeamBrand("KT", 0x000000),
        TeamBrand("LG", 0xC40037), TeamBrand("KIA", 0xEA0029), TeamBrand("DOOSAN", 0x1A1748),
        TeamBrand("SSG", 0xCE0E2D), TeamBrand("LOTTE", 0x041E42), TeamBrand("NC", 0x315288),
        TeamBrand("HANWHA", 0xFC4E00),
    )
    fun find(id: String?): TeamBrand? = all.firstOrNull { it.teamId == id }
    fun contrast(a: Int, b: Int): Double {
        fun luminance(rgb: Int): Double {
            fun channel(shift: Int): Double {
                val v = ((rgb shr shift) and 255) / 255.0
                return if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
    internal fun darken(rgb: Int, factor: Double): Int =
        (((rgb shr 16 and 255) * factor).toInt() shl 16) or
            (((rgb shr 8 and 255) * factor).toInt() shl 8) or ((rgb and 255) * factor).toInt()
}

private fun contrast(a: Int, b: Int) = TeamBrands.contrast(a, b)
private fun darken(rgb: Int, factor: Double) = TeamBrands.darken(rgb, factor)
