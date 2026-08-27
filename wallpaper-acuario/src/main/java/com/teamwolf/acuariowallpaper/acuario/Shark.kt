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
        resetTimer()
    }

    fun update(deltaTime: Float, aspectRatio: Float) {
        if (presenceMode == 0) {
            active = false
            return
        }

        // Prevent update jumps on massive frames spikes
        val dt = deltaTime.coerceAtMost(0.1f)

        if (presenceMode == 2) {
            // Always active: wanders gracefully using steering
            active = true
            swimPhase += dt * 3.5f
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

            // Smooth gradual steering (max 0.8 rad/s -> slow, wide turns)
            val maxTurnRate = 0.8f
            val turnStep = angleDiff.coerceIn(-maxTurnRate * dt, maxTurnRate * dt)
            heading += turnStep

            // Move forward in current heading direction
            val speed = baseSpeed * (1f - (1f - kMinSpeedAtDepth) * depth)
            x += cos(heading) * speed * dt
            y += sin(heading) * speed * dt

            // Smooth depth change
            depth += (targetDepth - depth) * (1f - exp(-0.6f * dt))

            // Smooth face flipping (flip when heading crosses vertical axis)
            facingSign = if (cos(heading) >= 0f) 1f else -1f
            mirrorBlend += (facingSign - mirrorBlend) * (1f - exp(-6.0f * dt))

            // Smooth pitch matching the heading direction (adjusted for mirror flipping)
            val desiredPitch = facingSign * sin(heading) * 18f
            pitchDegrees += (desiredPitch - pitchDegrees) * (1f - exp(-3.0f * dt))
        } else {
            // Random pass-by behavior (Sometimes)
            if (active) {
                swimPhase += dt * 4.0f
                val dirX = cos(heading)
                val speed = 0.20f * (1f - (1f - kMinSpeedAtDepth) * depth) // swims slightly faster on pass-by
                x += dirX * speed * dt

                // Float slightly up/down in a slow wave
                y += sin(timeAccumulator) * 0.05f * dt
                timeAccumulator += dt

                // Face the direction of travel
                facingSign = if (dirX > 0f) 1f else -1f
                mirrorBlend += (facingSign - mirrorBlend) * (1f - exp(-6.0f * dt))
                
                // Pitch matches the slope of the vertical wave
                val pitchSlope = 0.05f * cos(timeAccumulator) / speed
                pitchDegrees = facingSign * pitchSlope * 18f

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
