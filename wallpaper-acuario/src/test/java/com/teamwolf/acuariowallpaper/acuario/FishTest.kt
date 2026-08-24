package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FishTest {

    @Test
    fun update_movesTowardAWaypointWithoutExplodingOrFreezing() {
        val fish = Fish()
        val (startX, startY) = fish.x to fish.y

        var moved = false
        repeat(300) {
            fish.update(1f / 60f, aspectRatio = 1.7f)
            if (abs(fish.x - startX) > 0.001f || abs(fish.y - startY) > 0.001f) {
                moved = true
            }
        }

        assertTrue("expected the fish to have moved from its starting position", moved)
        assertTrue("x drifted implausibly far: ${fish.x}", abs(fish.x) < 3f)
        assertTrue("y drifted implausibly far: ${fish.y}", abs(fish.y) < 3f)
    }

    @Test
    fun update_advancesSwimPhaseWhileMoving() {
        val fish = Fish()
        val startPhase = fish.swimPhase
        fish.update(1f / 60f, aspectRatio = 1.7f)
        val phaseAfterOneStep = fish.swimPhase

        repeat(30) { fish.update(1f / 60f, aspectRatio = 1.7f) }

        assertTrue(fish.swimPhase > phaseAfterOneStep)
        assertTrue(phaseAfterOneStep > startPhase)
    }

    @Test
    fun facingScale_staysInValidRange() {
        val fish = Fish()
        repeat(200) { fish.update(1f / 60f, aspectRatio = 1.7f) }

        val scale = fish.facingScale()
        assertTrue("facingScale() out of [-1, 1]: $scale", scale in -1f..1f)
    }

    @Test
    fun facingScale_reachesFullWidthInsteadOfStayingCompressed() {
        val fish = Fish()
        var maxAbsScale = 0f
        repeat(600) {
            fish.update(1f / 60f, aspectRatio = 0.5f)
            maxAbsScale = maxOf(maxAbsScale, abs(fish.facingScale()))
        }

        assertTrue("expected facingScale() to reach near full width at some point, max was $maxAbsScale", maxAbsScale > 0.9f)
    }

    @Test
    fun heading_easesTowardTargetInsteadOfSnapping() {
        val fish = Fish()
        fish.update(1f / 60f, aspectRatio = 1.7f)
        val headingAfterOneFrame = fish.heading

        // A single 1/60s step should only close a small fraction of the angular gap
        assertTrue(
            "expected a small first-frame turn, got $headingAfterOneFrame",
            abs(headingAfterOneFrame) < 0.3f
        )
    }

    @Test
    fun pitchDegrees_staysWithinTheSafetyClampAndCanBothLiftAndDip() {
        val fish = Fish()
        var maxPitch = Float.NEGATIVE_INFINITY
        var minPitch = Float.POSITIVE_INFINITY
        repeat(8000) {
            fish.update(1f / 60f, aspectRatio = 0.5f)
            maxPitch = maxOf(maxPitch, fish.pitchDegrees)
            minPitch = minOf(minPitch, fish.pitchDegrees)
            assertTrue("pitchDegrees out of safety clamp: ${fish.pitchDegrees}", abs(fish.pitchDegrees) <= 45f)
        }

        assertTrue("expected some upward pitch (climbing), max was $maxPitch", maxPitch > 5f)
        assertTrue("expected some downward pitch (diving), min was $minPitch", minPitch < -5f)
    }

    @Test
    fun pitchDegrees_doesNotSnapInstantlyToTheDesiredAngle() {
        val fish = Fish()
        fish.update(1f / 60f, aspectRatio = 1.7f)
        assertTrue(
            "expected only a small first-frame pitch change, got ${fish.pitchDegrees}",
            abs(fish.pitchDegrees) < 5f
        )
    }

    @Test
    fun depth_staysWithinZeroToOneAcrossManyWaypointChanges() {
        val fish = Fish()
        repeat(2000) {
            fish.update(1f / 60f, aspectRatio = 1.7f)
            assertTrue("depth out of [0, 1]: ${fish.depth}", fish.depth in -0.001f..1.001f)
        }
    }

    @Test
    fun depth_variesOverTimeInsteadOfBeingFixed() {
        val fish = Fish()
        var maxDepth = 0f
        var minDepth = 1f
        repeat(8000) {
            fish.update(1f / 60f, aspectRatio = 1.7f)
            maxDepth = maxOf(maxDepth, fish.depth)
            minDepth = minOf(minDepth, fish.depth)
        }

        assertTrue("expected depth to drift near the glass at some point, min was $minDepth", minDepth < 0.3f)
        assertTrue("expected depth to drift into the background at some point, max was $maxDepth", maxDepth > 0.7f)
    }
}
