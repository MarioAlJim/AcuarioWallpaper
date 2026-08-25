package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test

class AnemonePaletteTest {

    @Test
    fun palettes_hasMoreThanOneOptionAndEveryColorIsValidRgb() {
        assertTrue("expected multiple palettes for real variety", AnemonePalette.PALETTES.size >= 3)

        for (palette in AnemonePalette.PALETTES) {
            for (color in listOf(palette.footColor, palette.tentacleColor, palette.tipColor, palette.highlightColor)) {
                assertTrue("expected an RGB triplet, got size ${color.size}", color.size == 3)
                for (component in color) {
                    assertTrue("color component out of [0, 1]: $component", component in 0f..1f)
                }
            }
        }
    }

    @Test
    fun anemone_getsARandomlyAssignedPaletteFromTheSharedList() {
        val anemone = Anemone()
        assertTrue(AnemonePalette.PALETTES.any { it === anemone.palette })
    }
}
