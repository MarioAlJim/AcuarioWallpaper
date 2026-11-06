package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarfishTest {

    @Test
    fun starfish_initializesOnFrontGlass() {
        val starfish = Starfish()
        assertEquals("starfish must adhere to the front glass at depth 0.0f", 0.0f, starfish.depth, 0.0001f)
        assertTrue("shinyType should be 0 (normal), 1 (gold), or 2 (diamond)", starfish.shinyType in 0..2)
        assertTrue("starfish x within default range", starfish.x in -1.5f..1.5f)
        assertTrue("starfish y within default range", starfish.y in -1.0f..1.0f)
        assertEquals("initial touch reaction should be 0", 0f, starfish.touchReact, 0.0001f)
    }

    @Test
    fun setInitialPosition_setsCoordinatesAndHeading() {
        val starfish = Starfish()
        starfish.setInitialPosition(0.25f, -0.40f, 45f)
        assertEquals(0.25f, starfish.x, 0.0001f)
        assertEquals(-0.40f, starfish.y, 0.0001f)
        assertEquals(45f, starfish.rotationDegrees, 0.0001f)
    }

    @Test
    fun touch_triggersStartleAndEmitsBubblePuff() {
        val starfish = Starfish()
        starfish.setInitialPosition(0f, 0f)

        assertFalse("no bubble puff pending initially", starfish.consumeBubblePuffEvent())

        starfish.touch(0.05f, 0.05f)

        assertEquals("touch reaction should max out", 1.0f, starfish.touchReact, 0.0001f)
        assertTrue("bubble puff event emitted on touch", starfish.consumeBubblePuffEvent())
        assertFalse("bubble puff event consumed and reset", starfish.consumeBubblePuffEvent())
    }

    @Test
    fun update_decaysTouchReaction() {
        val starfish = Starfish()
        starfish.touch(0f, 0f)
        val initialReact = starfish.touchReact

        // Advance several frames
        repeat(30) {
            starfish.update(1f / 60f, aspectRatio = 1.0f)
        }

        assertTrue("touch reaction should decay exponentially over time", starfish.touchReact < initialReact)
    }

    @Test
    fun ensureWithinBounds_clampsOffscreenCoordinatesSafely() {
        val starfish = Starfish()
        starfish.setInitialPosition(1.5f, 1.2f)
        starfish.ensureWithinBounds(0.45f)

        assertTrue("x must be pulled inside visible screen on phone aspect ratio", starfish.x in -0.35f..0.35f)
        assertTrue("y must be pulled inside visible screen", starfish.y in -0.75f..0.75f)
    }

    @Test
    fun update_staysWithinScreenBounds() {
        val starfish = Starfish()
        starfish.setInitialPosition(0.80f, 0.80f)
        starfish.touch(0.79f, 0.79f, aspectRatio = 0.5f)

        repeat(120) {
            starfish.update(1f / 30f, aspectRatio = 0.5f)
            assertTrue("x should stay clamped inside glass margins", starfish.x in -0.60f..0.60f)
            assertTrue("y should stay clamped inside glass margins", starfish.y in -0.85f..0.85f)
        }
    }
}
