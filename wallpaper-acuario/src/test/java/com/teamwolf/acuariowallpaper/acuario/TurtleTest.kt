package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
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
    fun facingScale_staysInValidRangeAndTracksHeading() {
        val turtle = Turtle()
        repeat(200) { turtle.update(1f / 60f, aspectRatio = 1.7f) }

        val scale = turtle.facingScale()
        assertTrue("facingScale() out of [-1, 1]: $scale", scale in -1f..1f)
        assertEquals(kotlin.math.cos(turtle.heading), scale, 1e-5f)
    }

    @Test
    fun heading_easesTowardTargetInsteadOfSnapping() {
        val turtle = Turtle()
        turtle.update(1f / 60f, aspectRatio = 1.7f)
        val headingAfterOneFrame = turtle.heading

        // A single 1/60s step should only close a small fraction of the angular gap (~5%
        // per the class doc), never jump straight to the target angle.
        assertTrue(
            "expected a small first-frame turn, got $headingAfterOneFrame",
            abs(headingAfterOneFrame) < 0.3f
        )
    }
}
