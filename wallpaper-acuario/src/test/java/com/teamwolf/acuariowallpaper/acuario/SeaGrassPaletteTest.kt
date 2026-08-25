package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test

class SeaGrassPaletteTest {

    @Test
    fun palettes_hasMoreThanOneOptionAndEveryColorIsValidRgb() {
        assertTrue("expected multiple palettes for real variety", SeaGrassPalette.PALETTES.size >= 3)

        for (palette in SeaGrassPalette.PALETTES) {
            for (color in listOf(palette.bladeColor, palette.tipColor, palette.baseColor, palette.highlightColor)) {
                assertTrue("expected an RGB triplet, got size ${color.size}", color.size == 3)
                for (component in color) {
                    assertTrue("color component out of [0, 1]: $component", component in 0f..1f)
                }
            }
        }
    }

    @Test
    fun seaGrass_getsARandomlyAssignedPaletteFromTheSharedList() {
        val seaGrass = SeaGrass()
        assertTrue(SeaGrassPalette.PALETTES.any { it === seaGrass.palette })
    }
}
