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
    val shinyType: Int
    val palette: MantaPalette

    init {
        shinyType = if (Random.nextFloat() < 0.10f) {
            if (Random.nextBoolean()) 1 else 2
        } else {
            0
        }
        palette = when (shinyType) {
            1 -> MantaPalette.SHINY_GOLD
            2 -> MantaPalette.SHINY_DIAMOND
            else -> MantaPalette.PALETTES.random()
        }
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

    var loopTimer = 0f
        private set
    private var loopStartPitch = 0f

    var loopOffsetX = 0f
        private set
    var loopOffsetY = 0f
        private set

    fun triggerLoop() {
        if (loopTimer <= 0f) {
            loopTimer = 1.5f
            loopStartPitch = pitchDegrees
        }
    }

    var depth = Random.nextFloat()
        private set
    private var targetDepth = depth

    private var targetX = 0f
    private var targetY = 0f
    private var hasTarget = false

    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val speed = 0.07f + Random.nextFloat() * 0.04f
    // Occasionally bursts faster or slower than the baseline above - see pickNewTarget()'s
    // comment - eased toward its target the same way depth is, so a burst reads as a deliberate
    // acceleration/deceleration rather than a snap.
    private var speedMultiplier = 1f
    private var targetSpeedMultiplier = 1f

    fun update(deltaTime: Float, aspectRatio: Float) {
        if (loopTimer > 0f) {
            loopTimer -= deltaTime
            if (loopTimer < 0f) {
                loopTimer = 0f
            }
        }

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

        // Depth parallax + occasional speed burst
        val speedLerp = 1f - exp(-kSpeedEaseRate * deltaTime)
        speedMultiplier += (targetSpeedMultiplier - speedMultiplier) * speedLerp
        val effectiveSpeed = speed * (1f - (1f - kMinSpeedAtDepth) * depth) * speedMultiplier
        val activeSpeed = if (loopTimer > 0f) effectiveSpeed * 2.5f else effectiveSpeed
        x += (dx / dist) * activeSpeed * deltaTime
        y += (dy / dist) * activeSpeed * deltaTime

        // Heading steering and mirror flip are paused during a loop
        if (loopTimer <= 0f) {
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
        }

        // Pitch loop or spring
        if (loopTimer <= 0f) {
            // Pitch spring - a gentler max angle than Fish/Turtle: mantas glide fairly level,
            // banking only a little even on a steep leg.
            val desiredPitch = (dy / dist) * kMaxPitchDegrees
            stepPitchSpring(desiredPitch, deltaTime)
        } else {
            // Loop pitch override (0 to 360 degrees)
            val loopProgress = (1.5f - loopTimer) / 1.5f
            pitchDegrees = loopStartPitch + loopProgress * 360f
            if (loopTimer <= 0f) {
                // Wrap pitchDegrees to [-180, 180] to keep the spring stable when it takes back over
                while (pitchDegrees > 180f) pitchDegrees -= 360f
                while (pitchDegrees < -180f) pitchDegrees += 360f
                pitchVelocity = 0f // reset velocity to prevent spring jerk
            }
        }

        // Depth drift
        val depthLerp = 1f - exp(-kDepthEaseRate * deltaTime)
        depth += (targetDepth - depth) * depthLerp

        // Swim phase drives manta.frag's slow, whole-body wing undulation - much gentler than a
        // fish's tail-wag frequency. Flaps 3.5x faster during loop.
        val phaseSpeed = (1.6f + speed * 2.5f) * speedMultiplier
        if (loopTimer > 0f) {
            swimPhase += deltaTime * phaseSpeed * 3.5f
        } else {
            swimPhase += deltaTime * phaseSpeed
        }

        // Loop circular path offsets
        if (loopTimer > 0f) {
            val loopProgress = (1.5f - loopTimer) / 1.5f
            val angle = loopProgress * TWO_PI
            val radius = 0.22f
            loopOffsetX = facingSign * kotlin.math.sin(angle) * radius
            loopOffsetY = (1f - kotlin.math.cos(angle)) * radius
        } else {
            loopOffsetX = 0f
            loopOffsetY = 0f
        }
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

        // Occasionally (kSpeedBurstChance) commit to a faster or slower pace for this leg -
        // otherwise settle back to the normal cruising speed. Re-rolled at every waypoint change
        // so a burst is a transient flourish rather than a permanent trait of this manta - kept
        // milder than Fish/Turtle's own ranges, since a manta darting around would clash with
        // its whole "big, majestic glider" character.
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

        // Chance, at each new waypoint, of committing to a speed burst for that leg instead of
        // cruising at the normal pace - see pickNewTarget()'s comment. Milder range than
        // Fish/Turtle's own - see there for why.
        const val kSpeedBurstChance = 0.3f
        const val kFastSpeedMultiplierMin = 1.25f
        const val kFastSpeedMultiplierMax = 1.5f
        const val kSlowSpeedMultiplierMin = 0.6f
        const val kSlowSpeedMultiplierMax = 0.8f
        // Chosen so speedMultiplier is ~95% of the way to a new burst/cruise target in about 2s
        // (1 - e^(-kSpeedEaseRate*2) ~= 0.95) - a deliberate ease, not a snap.
        const val kSpeedEaseRate = 1.5f
    }
}
