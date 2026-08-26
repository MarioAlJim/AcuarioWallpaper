package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test

class TurtlePaletteTest {

    @Test
    fun palettes_hasMoreThanOneOptionAndEveryColorIsValidRgb() {
        assertTrue("expected multiple palettes for real variety", TurtlePalette.PALETTES.size >= 3)

        for (palette in TurtlePalette.PALETTES) {
            for (color in listOf(palette.shellColor, palette.headColor, palette.flipperColor, palette.spotColor, palette.glowColor)) {
                assertTrue("expected an RGB triplet, got size ${color.size}", color.size == 3)
                for (component in color) {
                    assertTrue("color component out of [0, 1]: $component", component in 0f..1f)
                }
            }
        }
    }

    @Test
    fun turtle_getsARandomlyAssignedPaletteFromTheSharedList() {
        val turtle = Turtle()
        assertTrue(TurtlePalette.PALETTES.any { it === turtle.palette })
    }
}
