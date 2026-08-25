package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeaGrassTest {

    @Test
    fun placeAt_setsPositionAndDoesNotDriftOnUpdate() {
        val seaGrass = SeaGrass()
        seaGrass.placeAt(x = 0.15f, depth = 0.2f, heightScale = 0.9f)

        repeat(120) { seaGrass.update(1f / 60f) }

        assertEquals("sea grass shouldn't drift horizontally like a wandering creature", 0.15f, seaGrass.x, 0.0001f)
        assertEquals("sea grass' depth is fixed once placed", 0.2f, seaGrass.depth, 0.0001f)
        assertEquals("sea grass' height is fixed once placed", 0.9f, seaGrass.heightScale, 0.0001f)
    }

    @Test
    fun update_advancesSwayPhaseOverTime() {
        val seaGrass = SeaGrass()
        val startPhase = seaGrass.swayPhase
        repeat(60) { seaGrass.update(1f / 60f) }

        assertTrue("expected swayPhase to advance while updating", seaGrass.swayPhase > startPhase)
    }
}
