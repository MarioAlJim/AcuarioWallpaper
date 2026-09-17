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

        assertTrue("axolotl should seek a rock when touched", axolotl.isSeekingRock)
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
    fun touch_swimsToRockThenRestsAndResumesNormalBehavior() {
        val axolotl = Axolotl()
        val rock = Rock()
        rock.placeAt(x = 0.5f, depth = 0.2f, scaleX = 0.6f, scaleY = 0.4f, shapeType = 0)

        // Axolotl is touched
        axolotl.touch(touchX = 0f, touchY = 0f, availableRocks = listOf(rock))

        assertTrue("Axolotl should be seeking rock", axolotl.isSeekingRock)
        assertTrue("Target should match rock X coordinate", axolotl.targetX == rock.x)
        assertTrue("Target Y should be on seabed rock level", axolotl.targetY <= -0.60f)

        // Simulate frames until reaching the rock
        var frames = 0
        while (axolotl.isSeekingRock && frames < 600) {
            axolotl.update(1f / 60f, aspectRatio = 1.0f)
            frames++
        }

        assertTrue("Axolotl should have reached the rock and be resting", axolotl.isRestingOnRock)
        assertFalse("Axolotl should no longer be seeking rock", axolotl.isSeekingRock)

        // Simulate resting period (a couple of seconds, e.g. ~3 seconds)
        val restFrames = (3.0f * 60).toInt()
        repeat(restFrames) {
            axolotl.update(1f / 60f, aspectRatio = 1.0f)
        }

        // After the rest period, normal behavior resumes
        assertFalse("Axolotl should no longer be resting on rock", axolotl.isRestingOnRock)
        assertFalse("Axolotl should not be seeking rock", axolotl.isSeekingRock)
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
