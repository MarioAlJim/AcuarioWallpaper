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
    fun facingScale_staysInValidRange() {
        val turtle = Turtle()
        repeat(200) { turtle.update(1f / 60f, aspectRatio = 1.7f) }

        val scale = turtle.facingScale()
        assertTrue("facingScale() out of [-1, 1]: $scale", scale in -1f..1f)
    }

    @Test
    fun facingScale_reachesFullWidthInsteadOfStayingCompressed() {
        // A tall, narrow roaming box (portrait-like aspect ratio) makes the real travel angle
        // spend long stretches near vertical - facingScale() must not stay squashed for that
        // whole time (see Turtle.update's comment on why it's decoupled from cos(heading)).
        val turtle = Turtle()
        var maxAbsScale = 0f
        repeat(600) {
            turtle.update(1f / 60f, aspectRatio = 0.5f)
            maxAbsScale = maxOf(maxAbsScale, abs(turtle.facingScale()))
        }

        assertTrue("expected facingScale() to reach near full width at some point, max was $maxAbsScale", maxAbsScale > 0.9f)
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

    @Test
    fun pitchDegrees_staysWithinTheSafetyClampAndCanBothLiftAndDip() {
        // A tall, narrow roaming box means plenty of legs are mostly vertical, so both signs
        // of pitch (climbing and diving) should show up over enough updates. 1000 frames
        // (~16.7s) only covers 1-3 waypoint legs, which was occasionally too few for both signs
        // to show up by chance (flaky - a run or two of mostly-horizontal legs was enough to
        // fail it); 8000 (~133s) covers many more legs - likely including a breathing cycle or
        // two (30-120s oxygen interval), which only reinforces the "some upward pitch" bound
        // via the forced ascent to the surface.
        val turtle = Turtle()
        var maxPitch = Float.NEGATIVE_INFINITY
        var minPitch = Float.POSITIVE_INFINITY
        repeat(8000) {
            turtle.update(1f / 60f, aspectRatio = 0.5f)
            maxPitch = maxOf(maxPitch, turtle.pitchDegrees)
            minPitch = minOf(minPitch, turtle.pitchDegrees)
            assertTrue("pitchDegrees out of safety clamp: ${turtle.pitchDegrees}", abs(turtle.pitchDegrees) <= 40f)
        }

        assertTrue("expected some upward pitch (climbing), max was $maxPitch", maxPitch > 5f)
        assertTrue("expected some downward pitch (diving), min was $minPitch", minPitch < -5f)
    }

    @Test
    fun pitchDegrees_doesNotSnapInstantlyToTheDesiredAngle() {
        // Starting level (pitchDegrees == 0) with a target straight below should NOT put the
        // spring already at rest one frame later - it has mass/inertia, so a single 1/60s step
        // can only have nudged it a little.
        val turtle = Turtle()
        turtle.update(1f / 60f, aspectRatio = 1.7f)
        assertTrue(
            "expected only a small first-frame pitch change, got ${turtle.pitchDegrees}",
            abs(turtle.pitchDegrees) < 5f
        )
    }

    @Test
    fun depth_staysWithinZeroToOneAcrossManyWaypointChanges() {
        val turtle = Turtle()
        repeat(2000) {
            turtle.update(1f / 60f, aspectRatio = 1.7f)
            assertTrue("depth out of [0, 1]: ${turtle.depth}", turtle.depth in -0.001f..1.001f)
        }
    }

    @Test
    fun depth_variesOverTimeInsteadOfBeingFixed() {
        // Enough waypoint changes should eventually send depth toward both ends of its range -
        // it isn't just a fixed per-instance value. 3000 frames (~50 simulated seconds) only
        // covers a handful of independent targetDepth draws, which was occasionally too few to
        // reliably visit both ends by chance (flaky); 15000 (~250s) also guarantees at least
        // one breathing cycle (forcing depth toward the glass while surfacing - see
        // breathingCycle_surfacesLevelsOutPitchAndExhales), which only reinforces this.
        val turtle = Turtle()
        var maxDepth = 0f
        var minDepth = 1f
        repeat(15000) {
            turtle.update(1f / 60f, aspectRatio = 1.7f)
            maxDepth = maxOf(maxDepth, turtle.depth)
            minDepth = minOf(minDepth, turtle.depth)
        }

        assertTrue("expected depth to drift near the glass at some point, min was $minDepth", minDepth < 0.3f)
        assertTrue("expected depth to drift into the background at some point, max was $maxDepth", maxDepth > 0.7f)
    }

    @Test
    fun breathingCycle_surfacesLevelsOutPitchAndExhales() {
        val turtle = Turtle()
        val deltaTime = 1f / 60f
        var reachedSurface = false
        var levelPitchNearSurface = false
        var exhaleCount = 0

        // ~250 simulated seconds - comfortably past the worst-case 120s oxygen interval plus
        // travel/hold time, so at least one full breathing cycle should complete.
        repeat(15000) {
            turtle.update(deltaTime, aspectRatio = 1.7f)
            if (turtle.y > 0.75f) {
                reachedSurface = true
                if (abs(turtle.pitchDegrees) < 5f) levelPitchNearSurface = true
            }
            if (turtle.consumeExhaleEvent()) exhaleCount++
        }

        assertTrue("expected the turtle to surface at least once", reachedSurface)
        assertTrue("expected pitch to level out horizontal while holding at the surface", levelPitchNearSurface)
        assertTrue("expected at least one exhale event, got $exhaleCount", exhaleCount >= 1)
    }

    @Test
    fun consumeExhaleEvent_doesNotFireAgainImmediatelyAfterBeingConsumed() {
        val turtle = Turtle()
        val deltaTime = 1f / 60f
        var consumedOnce = false
        for (i in 0 until 15000) {
            turtle.update(deltaTime, aspectRatio = 1.7f)
            if (turtle.consumeExhaleEvent()) {
                consumedOnce = true
                // The very next read (same frame, no further update()) must be false - it's a
                // one-shot event, not a level that stays true.
                assertTrue(!turtle.consumeExhaleEvent())
                break
            }
        }
        assertTrue("expected to observe at least one exhale event", consumedOnce)
    }

    @Test
    fun powerStrokeEvent_firesRepeatedlyWhileSwimmingAndIsOneShot() {
        val turtle = Turtle()
        val deltaTime = 1f / 60f
        var strokeCount = 0
        // ~16.7s, comfortably below the 30s minimum oxygen interval, so this whole window stays
        // in normal swimming (no breathing-cycle interference); the flap rate
        // (2.2-3.4 rad/s / 2*PI ~= 0.35-0.54 Hz) means several strokes should land in it.
        repeat(1000) {
            turtle.update(deltaTime, aspectRatio = 1.7f)
            if (turtle.consumePowerStrokeEvent()) {
                strokeCount++
                // One-shot: consuming it again the same frame (no further update()) must be false.
                assertTrue(!turtle.consumePowerStrokeEvent())
            }
        }

        assertTrue("expected several power-stroke events while swimming, got $strokeCount", strokeCount >= 3)
    }

    @Test
    fun frontFlipperWorldPosition_staysNearTheTurtleWithTopAndBottomOnOppositeSides() {
        val turtle = Turtle()
        repeat(120) { turtle.update(1f / 60f, aspectRatio = 1.7f) }

        val turtleScale = 0.28f
        val (topX, topY) = turtle.frontFlipperWorldPosition(top = true, turtleScale)
        val (botX, botY) = turtle.frontFlipperWorldPosition(top = false, turtleScale)

        // Both anchors are a small local offset scaled by turtleScale, so they must stay close
        // to the turtle's own position, not drift off arbitrarily far.
        assertTrue(abs(topX - turtle.x) < turtleScale * 2f)
        assertTrue(abs(topY - turtle.y) < turtleScale * 2f)
        assertTrue(abs(botX - turtle.x) < turtleScale * 2f)
        assertTrue(abs(botY - turtle.y) < turtleScale * 2f)

        // The top and bottom flipper anchors' local Y always has opposite signs before
        // rotation, and pitch stays within its documented +-40 deg overshoot clamp, so rotating
        // by it can never swing them to the same side - this should hold for any pitch/facing.
        assertTrue(
            "expected top/bottom flipper anchors on opposite sides of the turtle, " +
                "topY-y=${topY - turtle.y}, botY-y=${botY - turtle.y}",
            (topY - turtle.y) * (botY - turtle.y) < 0f
        )
    }
}
