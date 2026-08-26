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
 *
 * [speedMultiplier] occasionally bursts faster or slower than [speed]'s own baseline pace - see
 * [pickNewTarget]'s comment - eased toward its target the same way [depth] is, so a burst reads
 * as a deliberate acceleration/deceleration rather than a snap.
 */
class Fish {
    val shinyType: Int
    val palette: FishPalette
    val fishType: Int

    init {
        shinyType = if (Random.nextFloat() < 0.01f) {
            if (Random.nextBoolean()) 1 else 2
        } else {
            0
        }
        val normalPalette = FishPalette.PALETTES.random()
        palette = when (shinyType) {
            1 -> FishPalette.SHINY_GOLD
            2 -> FishPalette.SHINY_DIAMOND
            else -> normalPalette
        }
        fishType = normalPalette.fishType
    }

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

    var targetX = 0f
        private set
    var targetY = 0f
        private set
    private var hasTarget = false

    var isTargetingFood = false
    var isSpinning = false
        private set
    var spinAngle = 0f
        private set
    private var spinProgress = 0f
    private val spinDuration = 0.6f

    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val speed = 0.14f + Random.nextFloat() * 0.08f
    private var speedMultiplier = 1f
    private var targetSpeedMultiplier = 1f

    fun setTarget(tx: Float, ty: Float) {
        targetX = tx
        targetY = ty
        hasTarget = true
    }

    fun startSpin() {
        isSpinning = true
        spinProgress = 0f
        spinAngle = 0f
    }

    fun stopTargetingFood(aspectRatio: Float) {
        isTargetingFood = false
        pickNewTarget(aspectRatio)
    }


    fun update(deltaTime: Float, aspectRatio: Float) {
        if (isSpinning) {
            spinProgress += deltaTime / spinDuration
            spinAngle = spinProgress * 360f
            if (spinProgress >= 1f) {
                isSpinning = false
                spinAngle = 0f
                pickNewTarget(aspectRatio)
            }
        }

        if (!hasTarget) {
            pickNewTarget(aspectRatio)
        }

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.05f && !isTargetingFood) {
            pickNewTarget(aspectRatio)
            return
        }

        // Depth parallax + occasional speed burst
        if (isTargetingFood) {
            targetSpeedMultiplier = 1.8f
        }
        val speedLerp = 1f - exp(-kSpeedEaseRate * deltaTime)
        speedMultiplier += (targetSpeedMultiplier - speedMultiplier) * speedLerp
        val effectiveSpeed = speed * (1f - (1f - kMinSpeedAtDepth) * depth) * speedMultiplier
        
        if (dist > 0.001f) {
            x += (dx / dist) * effectiveSpeed * deltaTime
            y += (dy / dist) * effectiveSpeed * deltaTime
        }

        // Heading steering
        val targetHeading = if (dist > 0.001f) atan2(dy, dx) else heading
        var angleDiff = targetHeading - heading
        while (angleDiff > PI) angleDiff -= TWO_PI
        while (angleDiff < -PI) angleDiff += TWO_PI
        val turnRate = if (isTargetingFood) 9.0f else kTurnRate
        val turnLerp = 1f - exp(-turnRate * deltaTime)
        heading += angleDiff * turnLerp

        // Mirror flip
        if (kotlin.math.abs(dx) > kFacingDeadzone) {
            facingSign = if (dx > 0f) 1f else -1f
        }
        val mirrorLerp = 1f - exp(-kMirrorRate * deltaTime)
        mirrorBlend += (facingSign - mirrorBlend) * mirrorLerp

        // Pitch spring
        val desiredPitch = if (dist > 0.001f) (dy / dist) * kMaxPitchDegrees else 0f
        stepPitchSpring(desiredPitch, deltaTime)

        // Depth drift
        if (isTargetingFood) {
            targetDepth = 0f
        }
        val depthLerp = 1f - exp(-kDepthEaseRate * deltaTime)
        depth += (targetDepth - depth) * depthLerp

        // Swim phase (tail-wag frequency scales slightly with travel speed, including bursts)
        swimPhase += deltaTime * (6.0f + speed * 12f) * speedMultiplier
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

        // Occasionally (kSpeedBurstChance) commit to a faster or slower pace for this leg -
        // otherwise settle back to the normal cruising speed. Re-rolled at every waypoint change
        // so a burst is a transient flourish rather than a permanent trait of this fish.
        targetSpeedMultiplier = if (Random.nextFloat() < kSpeedBurstChance) {
            if (Random.nextBoolean()) {
                kFastSpeedMultiplierMin + Random.nextFloat() * (kFastSpeedMultiplierMax - kFastSpeedMultiplierMin)
            } else {
                kSlowSpeedMultiplierMin + Random.nextFloat() * (kSlowSpeedMultiplierMax - kSlowSpeedMultiplierMin)
            }
        } else {
            1f
        }
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

        // Chance, at each new waypoint, of committing to a speed burst for that leg instead of
        // cruising at the normal pace - see pickNewTarget()'s comment.
        const val kSpeedBurstChance = 0.3f
        const val kFastSpeedMultiplierMin = 1.4f
        const val kFastSpeedMultiplierMax = 1.9f
        const val kSlowSpeedMultiplierMin = 0.5f
        const val kSlowSpeedMultiplierMax = 0.75f
        // Chosen so speedMultiplier is ~95% of the way to a new burst/cruise target in about 2s
        // (1 - e^(-kSpeedEaseRate*2) ~= 0.95) - a deliberate ease, not a snap.
        const val kSpeedEaseRate = 1.5f
    }
}
