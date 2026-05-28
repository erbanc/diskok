package com.diskok.game

import android.graphics.Color

/**
 * A single minimal, pastel color theme. Every level picks a theme so the game
 * stays pure and geometric while still feeling fresh as you progress.
 */
data class Theme(
    val background: Int,
    val ink: Int,        // text + thin outlines
    val ball: Int,
    val target: Int,
    val obstacle: Int,
    val bouncer: Int,
    val aim: Int,
)

private fun c(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

object Palette {
    val themes: List<Theme> = listOf(
        // Coral on cream
        Theme(c(247, 244, 239), c(60, 62, 72), c(247, 161, 153),
            c(150, 206, 188), c(206, 211, 229), c(245, 200, 142), c(150, 130, 205)),
        // Sky & lilac
        Theme(c(238, 243, 247), c(64, 70, 84), c(151, 197, 233),
            c(238, 178, 191), c(206, 213, 224), c(176, 220, 178), c(206, 150, 120)),
        // Mint & blush
        Theme(c(241, 246, 242), c(58, 68, 64), c(244, 184, 196),
            c(158, 214, 200), c(210, 219, 214), c(170, 160, 220), c(110, 180, 168)),
        // Sand & sage
        Theme(c(247, 243, 235), c(72, 68, 58), c(240, 197, 142),
            c(176, 204, 160), c(220, 213, 199), c(238, 168, 160), c(140, 178, 138)),
        // Dusk
        Theme(c(238, 236, 244), c(68, 64, 82), c(198, 178, 230),
            c(244, 196, 168), c(212, 210, 224), c(160, 206, 224), c(214, 140, 178)),
    )

    fun theme(levelIndex: Int): Theme = themes[levelIndex.mod(themes.size)]
}
