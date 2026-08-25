package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoralTest {

    @Test
    fun placeAt_setsPositionAndDoesNotDriftOnUpdate() {
        val coral = Coral()
        coral.placeAt(x = -0.25f, depth = 0.55f, scale = 1.05f)

        repeat(120) { coral.update(1f / 60f) }

        assertEquals("coral shouldn't drift horizontally like a wandering creature", -0.25f, coral.x, 0.0001f)
        assertEquals("coral's depth is fixed once placed", 0.55f, coral.depth, 0.0001f)
        assertEquals("coral's scale is fixed once placed", 1.05f, coral.scale, 0.0001f)
    }

    @Test
    fun update_advancesSwayPhaseOverTime() {
        val coral = Coral()
        val startPhase = coral.swayPhase
        repeat(60) { coral.update(1f / 60f) }

        assertTrue("expected swayPhase to advance while updating", coral.swayPhase > startPhase)
    }
}
