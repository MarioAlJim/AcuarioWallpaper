package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Movement + swim-animation state for fish wandering around the tank.
 * Replicates the majority of Turtle's behaviors:
 * - Waypoint-based wandering
 * - Smooth heading steering
 * - Smooth 3D-depth scale/tint drift
 * - Smooth side mirroring
 * - Nose pitch underdamped spring system
 *
 * Designed to be slightly faster and more agile than turtles, and without the need
 * to surface for breathing.
 */
class Fish {
    val palette: FishPalette = FishPalette.PALETTES.random()

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
        private set
    private var targetY = 0f
        private set
    private var hasTarget = false

    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val speed = 0.14f + Random.nextFloat() * 0.08f

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

        // Heading steering
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

        // Pitch spring
        val desiredPitch = (dy / dist) * kMaxPitchDegrees
        stepPitchSpring(desiredPitch, deltaTime)

        // Depth drift
        val depthLerp = 1f - exp(-kDepthEaseRate * deltaTime)
        depth += (targetDepth - depth) * depthLerp

        // Swim phase (tail-wag frequency scales slightly with travel speed)
        swimPhase += deltaTime * (6.0f + speed * 12f)
    }

    private fun stepPitchSpring(desiredPitch: Float, deltaTime: Float) {
        val springAccel = (desiredPitch - pitchDegrees) * kPitchSpringStiffness - pitchVelocity * kPitchSpringDamping
        pitchVelocity += springAccel * deltaTime
        pitchDegrees = (pitchDegrees + pitchVelocity * deltaTime).coerceIn(-kMaxPitchOvershoot, kMaxPitchOvershoot)
    }

    fun facingScale(): Float = mirrorBlend

    private fun pickNewTarget(aspectRatio: Float) {
        // Roams the entire vertical range, unlike turtles who stay lower
        targetX = Random.nextFloat() * (aspectRatio * 1.6f) - aspectRatio * 0.8f
        targetY = -0.85f + Random.nextFloat() * 1.65f
        hasTarget = true
        targetDepth = Random.nextFloat()
    }

    companion object {
        const val PI = kotlin.math.PI.toFloat()
        const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()

        // Agile fish turn faster than turtles (turtles use 3.08f)
        const val kTurnRate = 4.5f

        const val kFacingDeadzone = 0.02f

        // Fast mirror flips (same as turtle)
        const val kMirrorRate = 12f

        // Fish tilt a bit more when swimming vertically
        const val kMaxPitchDegrees = 25f

        // Underdamped spring coefficients
        const val kPitchSpringStiffness = 110f
        const val kPitchSpringDamping = 10f
        const val kMaxPitchOvershoot = 45f

        const val kMinSpeedAtDepth = 0.45f
        const val kDepthEaseRate = 1.2f
    }
}
