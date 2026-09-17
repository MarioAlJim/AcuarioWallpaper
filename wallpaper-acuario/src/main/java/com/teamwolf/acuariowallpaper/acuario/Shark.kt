package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Movement + swim-animation state for the Shark.
 * Uses steering behavior to ensure graceful, curved turns (no abrupt sliding)
 * and smooth mirror flipping.
 */
class Shark {
    var active = false
    var x = 0f
    var y = 0f
    var depth = 0.5f
    var heading = 0f
    var swimPhase = 0f
    var pitchDegrees = 0f

    // Interaction state: Lunge and Bite
    var biteProgress = 0f // 0 to 1 for mouth opening animation
        private set
    private var isBiting = false
    private var biteTimer = 0f
    private var lungeMultiplier = 1.0f

    private var targetX = 0f
    private var targetY = 0f
    private var targetDepth = 0.5f
    private var hasTarget = false

    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val baseSpeed = 0.14f // slow, majestic cruising speed

    // 0 = Disabled, 1 = Sometimes (Random), 2 = Always
    var presenceMode = 1
    var spawnTimer = 0f
    private var nextSpawnInterval = 30f + Random.nextFloat() * 45f

    // We can support 4 colors: Gray, Blue, White/Albino, Golden
    var colorMode = 0

    init {
        resetTimer()
    }

    private fun resetTimer() {
        spawnTimer = 0f
        nextSpawnInterval = 35f + Random.nextFloat() * 40f // swims by every 35 to 75 seconds
    }

    fun spawn(aspectRatio: Float) {
        val spawnLeft = Random.nextBoolean()
        x = if (spawnLeft) -aspectRatio - 1.5f else aspectRatio + 1.5f
        heading = if (spawnLeft) 0f else PI
        y = -0.5f + Random.nextFloat() * 0.8f
        depth = 0.3f + Random.nextFloat() * 0.4f // swims in the mid-background
        swimPhase = Random.nextFloat() * TWO_PI
        facingSign = if (spawnLeft) 1f else -1f
        mirrorBlend = facingSign
        pitchDegrees = 0f
        active = true
        isBiting = false
        biteProgress = 0f
        lungeMultiplier = 1f
        resetTimer()
    }

    fun touch() {
        if (!active) return
        isBiting = true
        biteTimer = 0f
    }

    fun update(deltaTime: Float, aspectRatio: Float) {
        if (presenceMode == 0) {
            active = false
            return
        }

        // Prevent update jumps on massive frames spikes
        val dt = deltaTime.coerceAtMost(0.1f)

        // Handle bite and lunge interaction: extended duration showing bared teeth
        if (isBiting) {
            biteTimer += dt

            // Forward surge / lunge multiplier during the bite display
            lungeMultiplier = when {
                biteTimer < 0.8f -> 1.0f + 2.2f * (1.0f - biteTimer / 0.8f) // explosive initial lunge
                biteTimer < 2.2f -> 1.35f // sustained predatory speed during the hold
                biteTimer < 3.0f -> 1.0f + 0.35f * (1.0f - (biteTimer - 2.2f) / 0.8f) // settling back to cruising speed
                else -> 1.0f
            }

            // Bite progress: snap open -> hold showing teeth -> smooth close
            biteProgress = when {
                biteTimer < 0.35f -> {
                    // Quick menacing snap open (0.0 to 1.0)
                    val t = biteTimer / 0.35f
                    t * (2.0f - t) // easeOut quad
                }
                biteTimer < 2.2f -> {
                    // Hold open displaying razor-sharp teeth with sustained predatory focus
                    1.0f
                }
                biteTimer < 3.0f -> {
                    // Smooth, deliberate jaw closure back to normal
                    val t = (biteTimer - 2.2f) / 0.8f
                    1.0f - t * t // easeIn quad
                }
                else -> 0f
            }

            if (biteTimer >= 3.0f) {
                isBiting = false
                biteProgress = 0f
                lungeMultiplier = 1f
            }
        }

        if (presenceMode == 2) {
            // Always active: wanders gracefully using steering
            active = true
            // Swim phase speeds up dramatically during lunge for a "thrashing" tail
            val swimSpeedBase = 3.5f
            val swimSpeedBoost = if (isBiting) 8.0f * biteProgress else 0f
            swimPhase = (swimPhase + dt * (swimSpeedBase + swimSpeedBoost)) % TWO_PI
            
            if (!hasTarget) {
                pickNewTarget(aspectRatio)
            }

            val dx = targetX - x
            val dy = targetY - y
            val dist = sqrt(dx * dx + dy * dy)
            
            // If close to target, pick a new one
            if (dist < 0.25f) {
                pickNewTarget(aspectRatio)
                return
            }

            // Calculate target heading
            val targetHeading = atan2(dy, dx)
            var angleDiff = targetHeading - heading
            while (angleDiff > PI) angleDiff -= TWO_PI
            while (angleDiff < -PI) angleDiff += TWO_PI

            // More aggressive steering during attack
            val maxTurnRate = if (isBiting) 2.5f else 0.8f
            val turnStep = angleDiff.coerceIn(-maxTurnRate * dt, maxTurnRate * dt)
            heading += turnStep

            // Move forward in current heading direction
            val speed = baseSpeed * (1f - (1f - kMinSpeedAtDepth) * depth) * lungeMultiplier
            x += cos(heading) * speed * dt
            y += sin(heading) * speed * dt

            // Smooth depth change
            depth += (targetDepth - depth) * (1f - exp(-0.6f * dt))

            // Smooth face flipping (flip when heading crosses vertical axis)
            facingSign = if (cos(heading) >= 0f) 1f else -1f
            mirrorBlend += (facingSign - mirrorBlend) * (1f - exp(-6.0f * dt))

            // Jitter pitch slightly during bite for "struggle" effect
            val jitter = if (isBiting) (Random.nextFloat() - 0.5f) * 15f * biteProgress else 0f
            // Nose lifts whenever heading points upward (sin(heading) > 0), regardless of which
            // way it's facing horizontally: drawShark() rotates by pitchDegrees BEFORE applying
            // the mirror flip (facingScale(), X-only) in the model matrix, so that rotation
            // always happens in the sprite's canonical "facing right" frame and the later mirror
            // can never undo the vertical lift it added - same contract as Fish/Turtle/Manta's
            // dy/dist-based pitch. Multiplying by facingSign here was a bug: it inverted the sign
            // of the pitch whenever cos(heading) < 0 (heading left), pitching the nose DOWN while
            // actually swimming up-and-left - i.e. reading as swimming tail-first.
            val desiredPitch = sin(heading) * 18f + jitter
            pitchDegrees += (desiredPitch - pitchDegrees) * (1f - exp(-4.0f * dt))
        } else {
            // Random pass-by behavior (Sometimes)
            if (active) {
                val swimSpeedBase = 4.0f
                val swimSpeedBoost = if (isBiting) 9.0f * biteProgress else 0f
                swimPhase = (swimPhase + dt * (swimSpeedBase + swimSpeedBoost)) % TWO_PI
                
                val dirX = cos(heading)
                // swims slightly faster on pass-by
                val speed = 0.20f * (1f - (1f - kMinSpeedAtDepth) * depth) * lungeMultiplier
                x += dirX * speed * dt

                // Float slightly up/down in a slow wave
                y += sin(timeAccumulator) * 0.05f * dt
                timeAccumulator = (timeAccumulator + dt) % TWO_PI

                // Face the direction of travel
                facingSign = if (dirX > 0f) 1f else -1f
                mirrorBlend += (facingSign - mirrorBlend) * (1f - exp(-6.0f * dt))
                
                // Pitch matches the vertical wave's own instantaneous velocity + jitter during
                // attack. y's update above is driven by sin(timeAccumulator), so that same
                // sin() - not cos() - is its rate of change and the correct thing to pitch
                // toward; using cos() here left pitch a quarter-cycle out of phase with the
                // actual up/down motion (near-level exactly when rising/sinking fastest, and
                // maximally tilted exactly when the wave is momentarily flat), reading as an
                // uncorrelated writhe rather than a gentle bob. Not scaled by facingSign either -
                // nose-up should mean "actually moving up" regardless of which horizontal
                // direction the pass-by is headed (see the identical fix in the "Always active"
                // branch above); drawShark()'s mirror flip alone already handles left/right,
                // applied after this rotation in the model matrix.
                val pitchSlope = 0.05f * sin(timeAccumulator) / speed
                val jitter = if (isBiting) (Random.nextFloat() - 0.5f) * 20f * biteProgress else 0f
                pitchDegrees = pitchSlope * 18f + jitter

                // Went off-screen -> become inactive
                if (dirX > 0 && x > aspectRatio + 1.8f) {
                    active = false
                    resetTimer()
                } else if (dirX < 0 && x < -aspectRatio - 1.8f) {
                    active = false
                    resetTimer()
                }
            } else {
                // Inactive, waiting for next spawn
                spawnTimer += dt
                if (spawnTimer >= nextSpawnInterval) {
                    spawn(aspectRatio)
                }
            }
        }
    }

    private var timeAccumulator = Random.nextFloat() * TWO_PI

    private fun pickNewTarget(aspectRatio: Float) {
        targetX = Random.nextFloat() * (aspectRatio * 1.6f) - aspectRatio * 0.8f
        targetY = -0.6f + Random.nextFloat() * 1.2f
        targetDepth = 0.3f + Random.nextFloat() * 0.4f
        hasTarget = true
    }

    fun facingScale(): Float = mirrorBlend

    companion object {
        private const val PI = kotlin.math.PI.toFloat()
        private const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()
        private const val kMinSpeedAtDepth = 0.5f
    }
}
