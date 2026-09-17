package com.teamwolf.acuariowallpaper.acuario

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
}
