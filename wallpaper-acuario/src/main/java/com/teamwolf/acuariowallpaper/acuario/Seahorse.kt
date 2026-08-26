package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Movement + fin-flutter state for seahorses drifting near the kelp beds.
 *
 * Unlike [Fish]/[Turtle]/[Manta], a seahorse doesn't swim head-first through the water: it hangs
 * upright and porpoises along in slow, mostly-vertical/diagonal bobs ([bobPhase] layers a gentle
 * sinusoidal rise-and-fall on top of the waypoint-wander drift below) with [kTurnRate] set well
 * below even [Manta]'s already-lazy turn rate. Its tiny pectoral/dorsal fins have to flutter at a
 * high frequency just to hold position, so [finPhase] advances on its own fast, constant cadence
 * (see [kFinFlutterSpeed]) rather than scaling with travel speed the way [swimPhase] does on the
 * other creatures. Every so often a seahorse latches onto a kelp frond and holds perfectly still
 * for a while ([isGripping]) before drifting off toward a new waypoint again.
 */
class Seahorse {
    val shinyType: Int
    val palette: SeahorsePalette

    init {
        shinyType = if (Random.nextFloat() < 0.01f) {
            if (Random.nextBoolean()) 1 else 2
        } else {
            0
        }
        palette = when (shinyType) {
            1 -> SeahorsePalette.SHINY_GOLD
            2 -> SeahorsePalette.SHINY_DIAMOND
            else -> SeahorsePalette.PALETTES.random()
        }
    }

    var x = Random.nextFloat() * 1.2f - 0.6f
        private set
    var y = -0.7f + Random.nextFloat() * 1.2f
        private set

    var heading = 0f
        private set

    // Drives seahorse.frag's fin-flutter animation - a near-constant high-frequency vibration,
    // independent of how fast (or whether) the body is actually drifting.
    var finPhase = Random.nextFloat() * TWO_PI
        private set

    // Slow up/down bob riding on top of the wander drift - a seahorse porpoises gently rather
    // than gliding flat through the water.
    private var bobPhase = Random.nextFloat() * TWO_PI

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

    private val speed = 0.018f + Random.nextFloat() * 0.012f

    /** True while gripping a kelp frond and holding still - see the class doc. */
    var isGripping = false
        private set
    private var gripTimeRemaining = 0f
    private var wanderTimeRemaining = kMinWanderSeconds + Random.nextFloat() * (kMaxWanderSeconds - kMinWanderSeconds)

    fun update(deltaTime: Float, aspectRatio: Float) {
        finPhase += deltaTime * kFinFlutterSpeed
        bobPhase += deltaTime * kBobSpeed

        if (isGripping) {
            gripTimeRemaining -= deltaTime
            if (gripTimeRemaining <= 0f) {
                isGripping = false
                hasTarget = false
                wanderTimeRemaining = kMinWanderSeconds + Random.nextFloat() * (kMaxWanderSeconds - kMinWanderSeconds)
            }
            // Still bob gently in place while gripping rather than freezing dead.
            y += sin(bobPhase) * kBobAmplitude * deltaTime
            val depthLerp = 1f - exp(-kDepthEaseRate * deltaTime)
            depth += (targetDepth - depth) * depthLerp
            return
        }

        if (!hasTarget) {
            pickNewTarget(aspectRatio)
        }

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)
        wanderTimeRemaining -= deltaTime
        if (dist < 0.04f || wanderTimeRemaining <= 0f) {
            if (Random.nextFloat() < kGripChance) {
                isGripping = true
                gripTimeRemaining = kMinGripSeconds + Random.nextFloat() * (kMaxGripSeconds - kMinGripSeconds)
                return
            }
            pickNewTarget(aspectRatio)
            return
        }

        x += (dx / dist) * speed * deltaTime
        y += (dy / dist) * speed * deltaTime + sin(bobPhase) * kBobAmplitude * deltaTime

        // Heading steering - even slower than Manta's already-lazy sweep, since a seahorse barely
        // steers at all, it just drifts toward its next waypoint.
        val targetHeading = atan2(dy, dx)
        var angleDiff = targetHeading - heading
        while (angleDiff > PI) angleDiff -= TWO_PI
        while (angleDiff < -PI) angleDiff += TWO_PI
        val turnLerp = 1f - exp(-kTurnRate * deltaTime)
        heading += angleDiff * turnLerp

        // Mirror flip
        if (abs(dx) > kFacingDeadzone) {
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
    }

    private fun stepPitchSpring(desiredPitch: Float, deltaTime: Float) {
        val springAccel = (desiredPitch - pitchDegrees) * kPitchSpringStiffness - pitchVelocity * kPitchSpringDamping
        pitchVelocity += springAccel * deltaTime
        pitchDegrees = (pitchDegrees + pitchVelocity * deltaTime).coerceIn(-kMaxPitchOvershoot, kMaxPitchOvershoot)
    }

    fun facingScale(): Float = mirrorBlend

    private fun pickNewTarget(aspectRatio: Float) {
        targetX = Random.nextFloat() * (aspectRatio * 1.4f) - aspectRatio * 0.7f
        // Seahorses stick to the middle/lower water column near the kelp beds, not the whole
        // vertical range fish/mantas roam.
        targetY = -0.85f + Random.nextFloat() * 1.1f
        hasTarget = true
        targetDepth = Random.nextFloat()
        wanderTimeRemaining = kMinWanderSeconds + Random.nextFloat() * (kMaxWanderSeconds - kMinWanderSeconds)
    }

    companion object {
        const val PI = kotlin.math.PI.toFloat()
        const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()

        // Far below Manta's already-lazy 1.8 - a seahorse barely steers, it just drifts.
        const val kTurnRate = 1.0f

        const val kFacingDeadzone = 0.015f
        const val kMirrorRate = 8f

        const val kMaxPitchDegrees = 10f
        const val kPitchSpringStiffness = 55f
        const val kPitchSpringDamping = 10f
        const val kMaxPitchOvershoot = 22f

        const val kDepthEaseRate = 0.8f

        const val kBobSpeed = 1.4f
        const val kBobAmplitude = 0.05f

        // Fin flutter is a near-constant high-frequency vibration, independent of travel speed -
        // see the class doc.
        const val kFinFlutterSpeed = 14f

        const val kMinWanderSeconds = 6f
        const val kMaxWanderSeconds = 14f

        // Chance, at the end of each wander leg, of latching onto a kelp frond instead of picking
        // a new waypoint immediately.
        const val kGripChance = 0.45f
        const val kMinGripSeconds = 4f
        const val kMaxGripSeconds = 10f
    }
}
