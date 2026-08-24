package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test

class FishPaletteTest {

    @Test
    fun palettes_hasMoreThanOneOptionAndEveryColorIsValidRgb() {
        assertTrue("expected multiple palettes for real variety", FishPalette.PALETTES.size >= 3)

        for (palette in FishPalette.PALETTES) {
            for (color in listOf(palette.bodyColor, palette.finColor, palette.tailColor, palette.stripeColor)) {
                assertTrue("expected an RGB triplet, got size ${color.size}", color.size == 3)
                for (component in color) {
                    assertTrue("color component out of [0, 1]: $component", component in 0f..1f)
                }
            }
        }
    }

    @Test
    fun fish_getsARandomlyAssignedPaletteFromTheSharedList() {
        val fish = Fish()
        assertTrue(FishPalette.PALETTES.any { it === fish.palette })
    }
}
