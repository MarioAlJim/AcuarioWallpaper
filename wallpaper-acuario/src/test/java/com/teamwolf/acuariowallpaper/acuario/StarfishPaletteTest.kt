package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarfishPaletteTest {

    @Test
    fun palettes_hasMultipleOptionsAndEveryColorIsValidRgb() {
        assertTrue("expected multiple palettes for natural variety", StarfishPalette.PALETTES.size >= 5)

        val allPalettes = StarfishPalette.PALETTES + listOf(
            StarfishPalette.SHINY_GOLD,
            StarfishPalette.SHINY_DIAMOND
        )

        for (palette in allPalettes) {
            for (color in listOf(
                palette.bodyColor,
                palette.detailColor,
                palette.suctionColor,
                palette.glowColor
            )) {
                assertEquals("expected an RGB triplet", 3, color.size)
                for (component in color) {
                    assertTrue("color component out of [0, 1]: $component", component in 0f..1f)
                }
            }
        }
    }

    @Test
    fun starfish_getsValidPaletteAssigned() {
        val starfish = Starfish()
        val validPalettes = StarfishPalette.PALETTES + listOf(
            StarfishPalette.SHINY_GOLD,
            StarfishPalette.SHINY_DIAMOND
        )
        assertTrue(validPalettes.any { it === starfish.palette })
    }
}
