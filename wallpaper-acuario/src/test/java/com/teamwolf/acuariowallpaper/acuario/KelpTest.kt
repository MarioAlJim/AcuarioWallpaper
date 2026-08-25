package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KelpTest {

    @Test
    fun placeAt_setsPositionAndDoesNotDriftOnUpdate() {
        val kelp = Kelp()
        kelp.placeAt(x = 0.42f, depth = 0.3f, heightScale = 1.1f)

        repeat(120) { kelp.update(1f / 60f) }

        assertEquals("kelp shouldn't drift horizontally like a wandering creature", 0.42f, kelp.x, 0.0001f)
        assertEquals("kelp's depth is fixed once placed", 0.3f, kelp.depth, 0.0001f)
        assertEquals("kelp's height is fixed once placed", 1.1f, kelp.heightScale, 0.0001f)
    }

    @Test
    fun update_advancesSwayPhaseOverTime() {
        val kelp = Kelp()
        val startPhase = kelp.swayPhase
        repeat(60) { kelp.update(1f / 60f) }

        assertTrue("expected swayPhase to advance while updating", kelp.swayPhase > startPhase)
    }
}
