package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test

class CoralPaletteTest {

    @Test
    fun palettes_hasMoreThanOneOptionAndEveryColorIsValidRgb() {
        assertTrue("expected multiple palettes for real variety", CoralPalette.PALETTES.size >= 3)

        for (palette in CoralPalette.PALETTES) {
            for (color in listOf(palette.baseColor, palette.branchColor, palette.polypColor, palette.highlightColor)) {
                assertTrue("expected an RGB triplet, got size ${color.size}", color.size == 3)
                for (component in color) {
                    assertTrue("color component out of [0, 1]: $component", component in 0f..1f)
                }
            }
        }
    }

    @Test
    fun coral_getsARandomlyAssignedPaletteFromTheSharedList() {
        val coral = Coral()
        assertTrue(CoralPalette.PALETTES.any { it === coral.palette })
    }
}
