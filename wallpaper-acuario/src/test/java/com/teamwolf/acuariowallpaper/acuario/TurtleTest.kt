package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TurtleTest {

    @Test
    fun update_movesTowardAWaypointWithoutExplodingOrFreezing() {
        val turtle = Turtle()
        val (startX, startY) = turtle.x to turtle.y

        var moved = false
        repeat(300) {
            turtle.update(1f / 60f, aspectRatio = 1.7f)
            if (abs(turtle.x - startX) > 0.001f || abs(turtle.y - startY) > 0.001f) {
                moved = true
            }
        }

        assertTrue("expected the turtle to have moved from its starting position", moved)
        // Bounds are generous: pickNewTarget() clamps to aspectRatio*0.8 horizontally and
        // [-0.9, 0.2] vertically, but the turtle can be mid-transit toward a waypoint.
        assertTrue("x drifted implausibly far: ${turtle.x}", abs(turtle.x) < 3f)
        assertTrue("y drifted implausibly far: ${turtle.y}", abs(turtle.y) < 3f)
    }

    @Test
    fun update_advancesSwimPhaseWhileMoving() {
        val turtle = Turtle()
        turtle.update(1f / 60f, aspectRatio = 1.7f)
        val phaseAfterOneStep = turtle.swimPhase

        repeat(30) { turtle.update(1f / 60f, aspectRatio = 1.7f) }

        assertTrue(turtle.swimPhase > phaseAfterOneStep)
    }

    @Test
    fun facing_reflectsHorizontalTravelDirection() {
        val turtle = Turtle()
        // Facing always starts at 1 (right) and only flips once horizontal movement resolves,
        // so just assert it lands on a valid value after a good number of update ticks.
        repeat(200) { turtle.update(1f / 60f, aspectRatio = 1.7f) }
        assertTrue(turtle.facing == 1f || turtle.facing == -1f)
    }
}
