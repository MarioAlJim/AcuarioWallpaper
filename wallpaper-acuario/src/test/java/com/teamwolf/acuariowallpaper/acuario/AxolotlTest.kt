package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AxolotlTest {

    @Test
    fun axolotl_initializesWithinBounds() {
        val axolotl = Axolotl()
        assertTrue("depth in [0, 1]", axolotl.depth in 0f..1f)
        assertTrue("shinyType in [0, 2]", axolotl.shinyType in 0..2)
        assertTrue("y near bottom/floor", axolotl.y in -0.95f..0.20f)
    }

    @Test
    fun update_advancesPhasesAndMovesPosition() {
        val axolotl = Axolotl()
        val initialX = axolotl.x
        val initialY = axolotl.y
        val initialGillPhase = axolotl.gillPhase
        val initialSwimPhase = axolotl.swimPhase

        // Advance 1 second
        repeat(60) {
            axolotl.update(1f / 60f, aspectRatio = 1.0f)
        }

        assertNotEquals("gillPhase should advance", initialGillPhase, axolotl.gillPhase)
        assertNotEquals("swimPhase should advance", initialSwimPhase, axolotl.swimPhase)
        assertTrue("x or y should change as axolotl wanders", axolotl.x != initialX || axolotl.y != initialY)
    }

    @Test
    fun touch_triggersStartleAndSpeedBurst() {
        val axolotl = Axolotl()

        axolotl.touch(0f, 0f)

        // Update frame
        axolotl.update(0.1f, aspectRatio = 1.0f)
        assertTrue("facing scale non-zero", axolotl.facingScale() != 0f)
    }

    @Test
    fun axolotl_canTargetUpperWater() {
        val axolotl = Axolotl()
        var upperWaterTargetChosen = false
        repeat(50) {
            axolotl.pickNewTarget(1.0f)
            if (axolotl.targetY > 0.0f) {
                upperWaterTargetChosen = true
            }
        }
        assertTrue("Axolotl should be able to choose targets in upper water (y > 0)", upperWaterTargetChosen)
    }

    @Test
    fun touch_canFleeUpward() {
        val axolotl = Axolotl()
        // Touch axolotl from below
        axolotl.touch(axolotl.x, axolotl.y - 0.5f)
        assertTrue("Target Y after touch from below should be higher", axolotl.targetY > axolotl.y)
    }

    @Test
    fun longTermSimulation_maintainsBoundedStateAndNoOverflow() {
        val axolotl = Axolotl()

        // Simulate 10,000 frames (approx 2.7 minutes at 60fps) with occasional spikes
        repeat(10_000) { i ->
            val dt = if (i % 500 == 0) 0.5f else 1f / 60f // test frame hitch robustness
            axolotl.update(dt, aspectRatio = 1.0f)

            if (i % 1000 == 0) {
                axolotl.touch(0f, -0.6f)
            }
        }

        // Verify no NaN or infinite coordinates
        assertFalse("x must not be NaN", axolotl.x.isNaN())
        assertFalse("y must not be NaN", axolotl.y.isNaN())
        assertFalse("pitchDegrees must not be NaN", axolotl.pitchDegrees.isNaN())

        // Verify position stays safely bounded within the tank
        assertTrue("x in bounds", axolotl.x in -1.5f..1.5f)
        assertTrue("y in bounds", axolotl.y in -0.75f..0.70f)
        assertTrue("depth in [0, 1]", axolotl.depth in 0f..1f)

        // Verify phases are wrapped and bounded (never overflowing)
        assertTrue("swimPhase is bounded", axolotl.swimPhase in 0f..13f)
        assertTrue("gillPhase is bounded", axolotl.gillPhase in 0f..7f)
        assertTrue("heading is normalized", axolotl.heading in -3.5f..3.5f)
        assertTrue("pitchDegrees is bounded", axolotl.pitchDegrees in -50f..50f)
    }
}
