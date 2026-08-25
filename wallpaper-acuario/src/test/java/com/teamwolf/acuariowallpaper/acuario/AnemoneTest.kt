package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnemoneTest {

    @Test
    fun placeAt_setsPositionAndDoesNotDriftOnUpdate() {
        val anemone = Anemone()
        anemone.placeAt(x = -0.7f, depth = 0.6f, scale = 0.9f)

        repeat(120) { anemone.update(1f / 60f) }

        assertEquals("anemones shouldn't drift horizontally like a wandering creature", -0.7f, anemone.x, 0.0001f)
        assertEquals("anemone's depth is fixed once placed", 0.6f, anemone.depth, 0.0001f)
        assertEquals("anemone's scale is fixed once placed", 0.9f, anemone.scale, 0.0001f)
    }

    @Test
    fun update_advancesSwayPhaseOverTime() {
        val anemone = Anemone()
        val startPhase = anemone.swayPhase
        repeat(60) { anemone.update(1f / 60f) }

        assertTrue("expected swayPhase to advance while updating", anemone.swayPhase > startPhase)
    }
}
