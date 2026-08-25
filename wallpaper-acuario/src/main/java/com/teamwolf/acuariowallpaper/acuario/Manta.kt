package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Movement + swim-animation state for manta rays gliding around the tank.
 *
 * Structurally the same waypoint-wander/steering/pitch-spring/mirror machinery as [Fish] and
 * [Turtle] (see their class docs for the shared mechanics), but tuned to read as a much bigger,
 * slower, more majestic glider: a lower [kTurnRate] so it sweeps through wide, lazy arcs instead
 * of darting, a slower [speed], and [swimPhase] advancing gently to drive manta.frag's
 * whole-body wing undulation (rather than a fish's sharp tail wag). Like [Fish] and unlike
 * [Turtle], mantas are gill-breathing elasmobranchs with no need to surface for air.
 */
class Manta {
    val palette: MantaPalette = MantaPalette.PALETTES.random()

    var x = Random.nextFloat() * 1.2f - 0.6f
        private set
    var y = -0.9f + Random.nextFloat() * 1.5f
        private set

    var heading = 0f
        private set

    var swimPhase = Random.nextFloat() * TWO_PI
        private set

    var pitchDegrees = 0f
        private set
    private var pitchVelocity = 0f

    var depth = Random.nextFloat()
        private set
    private var targetDepth = depth

    private var targetX = 0f
    private var targetY = 0f
    private var hasTarget = false

    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val speed = 0.07f + Random.nextFloat() * 0.04f

    fun update(deltaTime: Float, aspectRatio: Float) {
        if (!hasTarget) {
            pickNewTarget(aspectRatio)
        }

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.05f) {
            pickNewTarget(aspectRatio)
            return
        }

        // Depth parallax
        val effectiveSpeed = speed * (1f - (1f - kMinSpeedAtDepth) * depth)
        x += (dx / dist) * effectiveSpeed * deltaTime
        y += (dy / dist) * effectiveSpeed * deltaTime

        // Heading steering - deliberately slow (kTurnRate well below Fish/Turtle's) so a manta
        // banks through a wide, lazy arc toward each new waypoint instead of turning briskly.
        val targetHeading = atan2(dy, dx)
        var angleDiff = targetHeading - heading
        while (angleDiff > PI) angleDiff -= TWO_PI
        while (angleDiff < -PI) angleDiff += TWO_PI
        val turnLerp = 1f - exp(-kTurnRate * deltaTime)
        heading += angleDiff * turnLerp

        // Mirror flip
        if (kotlin.math.abs(dx) > kFacingDeadzone) {
            facingSign = if (dx > 0f) 1f else -1f
        }
        val mirrorLerp = 1f - exp(-kMirrorRate * deltaTime)
        mirrorBlend += (facingSign - mirrorBlend) * mirrorLerp

        // Pitch spring - a gentler max angle than Fish/Turtle: mantas glide fairly level,
        // banking only a little even on a steep leg.
        val desiredPitch = (dy / dist) * kMaxPitchDegrees
        stepPitchSpring(desiredPitch, deltaTime)

        // Depth drift
        val depthLerp = 1f - exp(-kDepthEaseRate * deltaTime)
        depth += (targetDepth - depth) * depthLerp

        // Swim phase drives manta.frag's slow, whole-body wing undulation - much gentler than a
        // fish's tail-wag frequency.
        swimPhase += deltaTime * (1.6f + speed * 2.5f)
    }

    private fun stepPitchSpring(desiredPitch: Float, deltaTime: Float) {
        val springAccel = (desiredPitch - pitchDegrees) * kPitchSpringStiffness - pitchVelocity * kPitchSpringDamping
        pitchVelocity += springAccel * deltaTime
        pitchDegrees = (pitchDegrees + pitchVelocity * deltaTime).coerceIn(-kMaxPitchOvershoot, kMaxPitchOvershoot)
    }

    fun facingScale(): Float = mirrorBlend

    private fun pickNewTarget(aspectRatio: Float) {
        // Roams the entire vertical range, same as Fish - unlike turtles, mantas don't stick to
        // the lower/mid water column.
        targetX = Random.nextFloat() * (aspectRatio * 1.6f) - aspectRatio * 0.8f
        targetY = -0.85f + Random.nextFloat() * 1.65f
        hasTarget = true
        targetDepth = Random.nextFloat()
    }

    companion object {
        const val PI = kotlin.math.PI.toFloat()
        const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()

        // Wide, lazy turning arcs - well below Fish's 4.5 and Turtle's 3.08.
        const val kTurnRate = 1.8f

        const val kFacingDeadzone = 0.02f

        const val kMirrorRate = 12f

        // Mantas glide close to level, banking only gently.
        const val kMaxPitchDegrees = 14f

        const val kPitchSpringStiffness = 70f
        const val kPitchSpringDamping = 11f
        const val kMaxPitchOvershoot = 30f

        const val kMinSpeedAtDepth = 0.45f
        const val kDepthEaseRate = 1.0f
    }
}
