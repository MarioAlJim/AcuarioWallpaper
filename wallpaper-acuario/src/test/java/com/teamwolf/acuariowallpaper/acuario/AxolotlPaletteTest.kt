package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AxolotlPaletteTest {

    @Test
    fun palettes_hasMultipleOptionsAndEveryColorIsValidRgb() {
        assertTrue("expected multiple palettes for real variety", AxolotlPalette.PALETTES.size >= 4)

        val allPalettes = AxolotlPalette.PALETTES + listOf(
            AxolotlPalette.SHINY_GOLD,
            AxolotlPalette.SHINY_DIAMOND
        )

        for (palette in allPalettes) {
            for (color in listOf(
                palette.bodyColor,
                palette.gillColor,
                palette.bellyColor,
                palette.spotColor,
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
    fun axolotl_getsValidPaletteAssigned() {
        val axolotl = Axolotl()
        val validPalettes = AxolotlPalette.PALETTES + listOf(
            AxolotlPalette.SHINY_GOLD,
            AxolotlPalette.SHINY_DIAMOND
        )
        assertTrue(validPalettes.any { it === axolotl.palette })
    }
}
