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
 * and smooth mirror flipping. Supports predatory hunting in realistic mode.
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

    // Predatory hunting state
    var isHunting = false
        private set
    var huntTargetX = 0f
    var huntTargetY = 0f
    var huntTargetDepth = 0.5f
    var huntTargetObject: Any? = null
        private set
    private var huntDuration = 0f

    private var targetX = 0f
    private var targetY = 0f
    private var targetDepth = 0.5f
    private var hasTarget = false

    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val baseSpeed = 0.14f // slow, majestic cruising speed

    // Presence & life-cycle
    var canSpawn = true
    var isRetiring = false
        private set

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
        isHunting = false
        huntTargetObject = null
        resetTimer()
    }

    fun touch() {
        if (!active) return
        isBiting = true
        biteTimer = 0f
    }

    fun startHunt(targetX: Float, targetY: Float, targetDepth: Float, prey: Any) {
        if (!active) return
        isHunting = true
        huntDuration = 0f
        huntTargetX = targetX
        huntTargetY = targetY
        huntTargetDepth = targetDepth
        huntTargetObject = prey
        isBiting = true
        biteTimer = 0f
    }

    fun updateHuntTarget(targetX: Float, targetY: Float, targetDepth: Float) {
        if (isHunting) {
            huntTargetX = targetX
            huntTargetY = targetY
            huntTargetDepth = targetDepth
        }
    }

    /**
     * Calculates the exact world position of the shark's snout/mouth based on current
     * position, facing scale, pitch rotation, and depth scale.
     */
    fun getHeadWorldPosition(depthScale: Float): Pair<Float, Float> {
        val headLocalX = 0.46f * depthScale
        val headLocalY = -0.04f * depthScale
        val pitchRad = Math.toRadians(pitchDegrees.toDouble()).toFloat()
        val cosP = cos(pitchRad)
        val sinP = sin(pitchRad)
        // Local rotation first
        val rotX = headLocalX * cosP - headLocalY * sinP
        val rotY = headLocalX * sinP + headLocalY * cosP
        // Apply facing sign flip and body world position
        val headX = x + rotX * facingSign
        val headY = y + rotY
        return Pair(headX, headY)
    }

    fun retire() {
        if (!active) return
        isRetiring = true
        isHunting = false
        huntTargetObject = null
    }

    fun finishHunt(preyCaught: Boolean) {
        isHunting = false
        huntTargetObject = null
        huntDuration = 0f
        if (preyCaught) {
            // Snaps jaw and glides satisfied
            isBiting = true
            biteTimer = 1.0f
            lungeMultiplier = 1.25f
        }
    }

    fun update(deltaTime: Float, aspectRatio: Float) {
        if (presenceMode == 0 || (!canSpawn && !active)) {
            active = false
            return
        }

        val dt = deltaTime.coerceAtMost(0.1f)

        // Handle retreating off-screen when fauna is depleted
        if (isRetiring) {
            val speed = 0.22f * (1f - (1f - kMinSpeedAtDepth) * depth)
            val dirX = if (facingSign >= 0f) 1f else -1f
            x += dirX * speed * dt
            swimPhase = (swimPhase + dt * 3.2f) % TWO_PI
            mirrorBlend += (facingSign - mirrorBlend) * (1f - exp(-6.0f * dt))
            if (kotlin.math.abs(x) > aspectRatio + 2.0f) {
                active = false
                isRetiring = false
                resetTimer()
            }
            return
        }

        // Handle hunting timeout
        if (isHunting) {
            huntDuration += dt
            if (huntDuration > 6.0f) {
                finishHunt(false)
            }
        }

        // Handle bite and lunge interaction
        if (isHunting) {
            lungeMultiplier = 1.75f
            // Wide open jaws while chasing down prey
            biteProgress = (0.75f + 0.25f * sin(huntDuration * 9f)).coerceIn(0.55f, 1.0f)
        } else if (isBiting) {
            biteTimer += dt

            lungeMultiplier = when {
                biteTimer < 0.6f -> 1.0f + 0.6f * (1.0f - biteTimer / 0.6f)
                biteTimer < 2.0f -> 1.25f
                biteTimer < 2.8f -> 1.0f + 0.25f * (1.0f - (biteTimer - 2.0f) / 0.8f)
                else -> 1.0f
            }

            biteProgress = when {
                biteTimer < 0.35f -> {
                    val t = biteTimer / 0.35f
                    t * (2.0f - t)
                }
                biteTimer < 1.8f -> 1.0f
                biteTimer < 2.6f -> {
                    val t = (biteTimer - 1.8f) / 0.8f
                    1.0f - t * t
                }
                else -> 0f
            }

            if (biteTimer >= 2.6f) {
                isBiting = false
                biteProgress = 0f
                lungeMultiplier = 1f
            }
        }

        // Active hunting overrides normal wander behavior
        if (isHunting) {
            val swimSpeedBoost = 3.2f
            swimPhase = (swimPhase + dt * (3.0f + swimSpeedBoost)) % TWO_PI

            // Steering towards prey from body center (x, y) prevents erratic chattering
            val dx = huntTargetX - x
            val dy = huntTargetY - y
            val distToTarget = sqrt(dx * dx + dy * dy)

            val targetHeading = atan2(dy, dx)
            var angleDiff = targetHeading - heading
            while (angleDiff > PI) angleDiff -= TWO_PI
            while (angleDiff < -PI) angleDiff += TWO_PI

            // Smooth hydrodynamic turn rate
            val maxTurnRate = 3.0f
            val turnStep = angleDiff.coerceIn(-maxTurnRate * dt, maxTurnRate * dt)
            heading += turnStep

            val speed = baseSpeed * (1f - (1f - kMinSpeedAtDepth) * depth) * lungeMultiplier
            x += cos(heading) * speed * dt
            y = (y + sin(heading) * speed * dt).coerceIn(-0.65f, 0.75f)

            depth += (huntTargetDepth - depth) * (1f - exp(-1.5f * dt))

            // Only flip facing sign with hysteresis and when not right on top of target
            if (distToTarget > 0.22f) {
                if (facingSign > 0f && cos(heading) < -0.25f) {
                    facingSign = -1f
                } else if (facingSign < 0f && cos(heading) > 0.25f) {
                    facingSign = 1f
                }
            }
            mirrorBlend += (facingSign - mirrorBlend) * (1f - exp(-6.0f * dt))

            // Smooth pitch oriented toward target movement
            val targetPitch = (sin(heading) * 22f).coerceIn(-28f, 28f)
            pitchDegrees += (targetPitch - pitchDegrees) * (1f - exp(-6.0f * dt))
            return
        }

        if (presenceMode == 2) {
            if (!canSpawn) {
                active = false
                return
            }
            // Always active: wanders gracefully using steering
            active = true
            val swimSpeedBase = 2.8f
            val swimSpeedBoost = if (isBiting) 3.2f * biteProgress else 0f
            swimPhase = (swimPhase + dt * (swimSpeedBase + swimSpeedBoost)) % TWO_PI

            if (!hasTarget) {
                pickNewTarget(aspectRatio)
            }

            val dx = targetX - x
            val dy = targetY - y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < 0.25f) {
                pickNewTarget(aspectRatio)
                return
            }

            val targetHeading = atan2(dy, dx)
            var angleDiff = targetHeading - heading
            while (angleDiff > PI) angleDiff -= TWO_PI
            while (angleDiff < -PI) angleDiff += TWO_PI

            val maxTurnRate = if (isBiting) 2.5f else 0.8f
            val turnStep = angleDiff.coerceIn(-maxTurnRate * dt, maxTurnRate * dt)
            heading += turnStep

            val speed = baseSpeed * (1f - (1f - kMinSpeedAtDepth) * depth) * lungeMultiplier
            x += cos(heading) * speed * dt
            y = (y + sin(heading) * speed * dt).coerceIn(-0.65f, 0.75f)

            depth += (targetDepth - depth) * (1f - exp(-0.6f * dt))

            facingSign = if (cos(heading) >= 0f) 1f else -1f
            mirrorBlend += (facingSign - mirrorBlend) * (1f - exp(-6.0f * dt))

            val biteBodySway = if (isBiting) sin(biteTimer * 8.0f) * 4.0f * biteProgress else 0f
            val desiredPitch = sin(heading) * 18f + biteBodySway
            pitchDegrees += (desiredPitch - pitchDegrees) * (1f - exp(-4.0f * dt))
        } else {
            // Random pass-by behavior (Sometimes)
            if (active) {
                val swimSpeedBase = 3.0f
                val swimSpeedBoost = if (isBiting) 3.5f * biteProgress else 0f
                swimPhase = (swimPhase + dt * (swimSpeedBase + swimSpeedBoost)) % TWO_PI

                val dirX = cos(heading)
                val speed = 0.20f * (1f - (1f - kMinSpeedAtDepth) * depth) * lungeMultiplier
                x += dirX * speed * dt

                y = (y + sin(timeAccumulator) * 0.05f * dt).coerceIn(-0.65f, 0.75f)
                timeAccumulator = (timeAccumulator + dt) % TWO_PI

                facingSign = if (dirX > 0f) 1f else -1f
                mirrorBlend += (facingSign - mirrorBlend) * (1f - exp(-6.0f * dt))

                val pitchSlope = 0.05f * sin(timeAccumulator) / speed
                val biteBodySway = if (isBiting) sin(biteTimer * 8.0f) * 4.0f * biteProgress else 0f
                val desiredPitch = pitchSlope * 18f + biteBodySway
                pitchDegrees += (desiredPitch - pitchDegrees) * (1f - exp(-4.0f * dt))

                if (dirX > 0 && x > aspectRatio + 1.8f) {
                    active = false
                    resetTimer()
                } else if (dirX < 0 && x < -aspectRatio - 1.8f) {
                    active = false
                    resetTimer()
                }
            } else {
                if (!canSpawn) {
                    spawnTimer = 0f
                } else {
                    spawnTimer += dt
                    if (spawnTimer >= nextSpawnInterval) {
                        spawn(aspectRatio)
                    }
                }
            }
        }
    }

    private var timeAccumulator = Random.nextFloat() * TWO_PI

    private fun pickNewTarget(aspectRatio: Float) {
        targetX = Random.nextFloat() * (aspectRatio * 1.6f) - aspectRatio * 0.8f
        targetY = -0.55f + Random.nextFloat() * 1.15f
        targetDepth = 0.3f + Random.nextFloat() * 0.4f
        hasTarget = true
    }

    fun facingScale(): Float = mirrorBlend

    companion object {
        private const val PI = kotlin.math.PI.toFloat()
        private const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()
        private const val kMinSpeedAtDepth = 0.5f
        private const val kMinScaleAtDepth = 0.40f
    }
}
