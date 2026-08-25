package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MantaTest {

    @Test
    fun update_movesTowardAWaypointWithoutExplodingOrFreezing() {
        val manta = Manta()
        val (startX, startY) = manta.x to manta.y

        var moved = false
        repeat(300) {
            manta.update(1f / 60f, aspectRatio = 1.7f)
            if (abs(manta.x - startX) > 0.001f || abs(manta.y - startY) > 0.001f) {
                moved = true
            }
        }

        assertTrue("expected the manta to have moved from its starting position", moved)
        assertTrue("x drifted implausibly far: ${manta.x}", abs(manta.x) < 3f)
        assertTrue("y drifted implausibly far: ${manta.y}", abs(manta.y) < 3f)
    }

    @Test
    fun update_advancesSwimPhaseWhileMoving() {
        val manta = Manta()
        val startPhase = manta.swimPhase
        manta.update(1f / 60f, aspectRatio = 1.7f)
        val phaseAfterOneStep = manta.swimPhase

        repeat(30) { manta.update(1f / 60f, aspectRatio = 1.7f) }

        assertTrue(manta.swimPhase > phaseAfterOneStep)
        assertTrue(phaseAfterOneStep > startPhase)
    }

    @Test
    fun facingScale_staysInValidRange() {
        val manta = Manta()
        repeat(200) { manta.update(1f / 60f, aspectRatio = 1.7f) }

        val scale = manta.facingScale()
        assertTrue("facingScale() out of [-1, 1]: $scale", scale in -1f..1f)
    }

    @Test
    fun facingScale_reachesFullWidthInsteadOfStayingCompressed() {
        val manta = Manta()
        var maxAbsScale = 0f
        repeat(1200) {
            manta.update(1f / 60f, aspectRatio = 0.5f)
            maxAbsScale = maxOf(maxAbsScale, abs(manta.facingScale()))
        }

        assertTrue("expected facingScale() to reach near full width at some point, max was $maxAbsScale", maxAbsScale > 0.9f)
    }

    @Test
    fun heading_easesTowardTargetInsteadOfSnapping() {
        val manta = Manta()
        manta.update(1f / 60f, aspectRatio = 1.7f)
        val headingAfterOneFrame = manta.heading

        // A single 1/60s step should only close a small fraction of the angular gap
        assertTrue(
            "expected a small first-frame turn, got $headingAfterOneFrame",
            abs(headingAfterOneFrame) < 0.3f
        )
    }

    @Test
    fun pitchDegrees_staysWithinTheSafetyClampAndCanBothLiftAndDip() {
        val manta = Manta()
        var maxPitch = Float.NEGATIVE_INFINITY
        var minPitch = Float.POSITIVE_INFINITY
        repeat(10000) {
            manta.update(1f / 60f, aspectRatio = 0.5f)
            maxPitch = maxOf(maxPitch, manta.pitchDegrees)
            minPitch = minOf(minPitch, manta.pitchDegrees)
            assertTrue("pitchDegrees out of safety clamp: ${manta.pitchDegrees}", abs(manta.pitchDegrees) <= 30f)
        }

        assertTrue("expected some upward pitch (climbing), max was $maxPitch", maxPitch > 3f)
        assertTrue("expected some downward pitch (diving), min was $minPitch", minPitch < -3f)
    }

    @Test
    fun pitchDegrees_doesNotSnapInstantlyToTheDesiredAngle() {
        val manta = Manta()
        manta.update(1f / 60f, aspectRatio = 1.7f)
        assertTrue(
            "expected only a small first-frame pitch change, got ${manta.pitchDegrees}",
            abs(manta.pitchDegrees) < 5f
        )
    }

    @Test
    fun depth_staysWithinZeroToOneAcrossManyWaypointChanges() {
        val manta = Manta()
        repeat(3000) {
            manta.update(1f / 60f, aspectRatio = 1.7f)
            assertTrue("depth out of [0, 1]: ${manta.depth}", manta.depth in -0.001f..1.001f)
        }
    }

    @Test
    fun depth_variesOverTimeInsteadOfBeingFixed() {
        val manta = Manta()
        var maxDepth = 0f
        var minDepth = 1f
        repeat(10000) {
            manta.update(1f / 60f, aspectRatio = 1.7f)
            maxDepth = maxOf(maxDepth, manta.depth)
            minDepth = minOf(minDepth, manta.depth)
        }

        assertTrue("expected depth to drift near the glass at some point, min was $minDepth", minDepth < 0.3f)
        assertTrue("expected depth to drift into the background at some point, max was $maxDepth", maxDepth > 0.7f)
    }
}
