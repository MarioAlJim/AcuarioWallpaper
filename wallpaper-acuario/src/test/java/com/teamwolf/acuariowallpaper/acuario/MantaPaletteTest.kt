package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test

class MantaPaletteTest {

    @Test
    fun palettes_hasMoreThanOneOptionAndEveryColorIsValidRgb() {
        assertTrue("expected multiple palettes for real variety", MantaPalette.PALETTES.size >= 3)

        for (palette in MantaPalette.PALETTES) {
            for (color in listOf(palette.bodyColor, palette.wingColor, palette.tailColor, palette.markingColor)) {
                assertTrue("expected an RGB triplet, got size ${color.size}", color.size == 3)
                for (component in color) {
                    assertTrue("color component out of [0, 1]: $component", component in 0f..1f)
                }
            }
        }
    }

    @Test
    fun manta_getsARandomlyAssignedPaletteFromTheSharedList() {
        val manta = Manta()
        assertTrue(MantaPalette.PALETTES.any { it === manta.palette })
    }
}
