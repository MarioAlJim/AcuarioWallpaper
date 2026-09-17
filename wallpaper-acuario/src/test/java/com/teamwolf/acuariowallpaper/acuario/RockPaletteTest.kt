package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RockPaletteTest {

    @Test
    fun testPalettesListIsNotEmpty() {
        assertTrue(RockPalette.PALETTES.isNotEmpty())
        assertEquals(5, RockPalette.PALETTES.size)
    }

    @Test
    fun testPaletteColorDimensions() {
        for (palette in RockPalette.PALETTES) {
            assertNotNull(palette.baseColor)
            assertEquals(3, palette.baseColor.size)

            assertNotNull(palette.highlightColor)
            assertEquals(3, palette.highlightColor.size)

            assertNotNull(palette.shadowColor)
            assertEquals(3, palette.shadowColor.size)

            assertNotNull(palette.spotColor)
            assertEquals(3, palette.spotColor.size)

            assertNotNull(palette.glowColor)
            assertEquals(3, palette.glowColor.size)
        }
    }
}
