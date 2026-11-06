package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Movement, animation phase, and depth state for an Axolotl wandering the aquarium floor.
 *
 * Axolotls are bottom-dwelling salamanders with external feathery gills. They spend most of
 * their time crawling along the tank bed ([y] around -0.88 to -0.72), but occasionally swim
 * up into mid-water before floating back down.
 *
 * [heading]: Facing angle in radians (0 = facing/moving right), smoothed toward target angle.
 * [facingScale]: Discrete left/right facing blend (+1 for right, -1 for left).
 * [pitchDegrees]: Body tilt angle in degrees, animated with a spring for natural body weight.
 * [swimPhase]: Drives body & paddle-tail undulation wave in axolotl.frag.
 * [gillPhase]: Drives rhythmic fluttering of the 6 external branch gills in axolotl.frag.
 * [legPhase]: Drives limb/paw crawling animation in axolotl.frag.
 * [depth]: 0 (near glass) to 1 (deep in tank) for depth ordering and parallax tinting.
 */
class Axolotl(customPalette: AxolotlPalette? = null) {
    val shinyType: Int
    val palette: AxolotlPalette

    init {
        shinyType = if (Random.nextFloat() < 0.10f) {
            if (Random.nextBoolean()) 1 else 2
        } else {
            0
        }
        palette = when (shinyType) {
            1 -> AxolotlPalette.SHINY_GOLD
            2 -> AxolotlPalette.SHINY_DIAMOND
            else -> customPalette ?: FaunaVariationManager.nextAxolotlPalette()
        }
    }

    var x = Random.nextFloat() * 1.2f - 0.6f
        private set
    var y = -0.68f + Random.nextFloat() * 0.06f
        private set

    var heading = 0f
        private set

    var swimPhase = Random.nextFloat() * TWO_PI
        private set

    var gillPhase = Random.nextFloat() * TWO_PI
        private set

    var legPhase = Random.nextFloat() * TWO_PI
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

    private var swimWaypointsRemaining = 0
    private var pauseTimer = 0f

    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val speed = 0.08f + Random.nextFloat() * 0.04f
    private var speedMultiplier = 1f
    private var targetSpeedMultiplier = 1f

    private var startleTimer = 0f
    private var isStartled = false

    var isSeekingRock = false
        private set

    var isRestingOnRock = false
        private set

    var hungerTimer = 65f
    var isStarving = false
        private set

    fun feed() {
        hungerTimer = 65f
        isStarving = false
    }

    fun isOutOfBounds(aspectRatio: Float): Boolean =
        kotlin.math.abs(x) > aspectRatio + 0.45f

    fun spawnFromEdge(aspectRatio: Float) {
        val fromLeft = Random.nextBoolean()
        x = if (fromLeft) -aspectRatio - 0.4f else aspectRatio + 0.4f
        y = -0.68f + Random.nextFloat() * 0.06f
        depth = Random.nextFloat()
        hungerTimer = 65f
        isStarving = false
        hasTarget = false
        pauseTimer = 0f
        isSeekingRock = false
        isRestingOnRock = false
        isStartled = false
    }

    fun update(deltaTime: Float, aspectRatio: Float, realisticMode: Boolean = false) {
        // Clamp deltaTime to prevent spring destabilization or massive phase jumps
        val dt = deltaTime.coerceIn(0f, 0.05f)

        if (realisticMode) {
            hungerTimer -= dt
            if (hungerTimer <= 0f && !isStarving) {
                isStarving = true
                targetX = if (x >= 0f) aspectRatio + 0.8f else -aspectRatio - 0.8f
                targetY = y
                hasTarget = true
                pauseTimer = 0f
                isSeekingRock = false
                isRestingOnRock = false
            }
        } else {
            hungerTimer = 65f
            isStarving = false
        }

        // Gills flutter continuously whether moving or stationary (wrapped to prevent float overflow)
        val gillSpeed = if (isStartled) 6.0f else 2.5f
        gillPhase = (gillPhase + dt * gillSpeed) % TWO_PI

        if (isStartled && !isSeekingRock) {
            startleTimer -= dt
            if (startleTimer <= 0f) {
                isStartled = false
                targetSpeedMultiplier = 1f
            }
        }

        if (pauseTimer > 0f) {
            pauseTimer -= dt
            // Pitch relaxes horizontally while resting/floating
            stepPitchSpring(0f, dt)

            // Gentle buoyancy drift when hovering in open water
            if (y > -0.60f) {
                y += sin(swimPhase * 0.5f) * 0.015f * dt
            }

            // Gentle idle drift for tail and legs (wrap periodically to prevent float precision loss)
            swimPhase = (swimPhase + dt * 1.2f) % FOUR_PI
            legPhase = (legPhase + dt * 0.8f) % TWO_PI

            if (pauseTimer <= 0f) {
                if (isRestingOnRock) {
                    isRestingOnRock = false
                }
                pickNewTarget(aspectRatio)
            }
            return
        }

        if (!hasTarget) {
            pickNewTarget(aspectRatio)
        }

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)

        if (dist < 0.04f) {
            if (isStarving) {
                return
            }
            if (isSeekingRock) {
                isSeekingRock = false
                isRestingOnRock = true
                isStartled = false
                targetSpeedMultiplier = 1f
                speedMultiplier = 1f
                pauseTimer = 2.0f + Random.nextFloat() * 0.6f
                return
            }
            if (!isStartled && Random.nextFloat() < 0.40f) {
                pauseTimer = 1.2f + Random.nextFloat() * 1.8f
            }
            pickNewTarget(aspectRatio)
            return
        }

        val speedLerp = 1f - exp(-kSpeedEaseRate * dt)
        speedMultiplier += (targetSpeedMultiplier - speedMultiplier) * speedLerp
        val effectiveSpeed = speed * (1f - (1f - kMinSpeedAtDepth) * depth) * speedMultiplier

        val safeDist = if (dist > 0.0001f) dist else 0.04f
        x += (dx / safeDist) * effectiveSpeed * dt
        y += (dy / safeDist) * effectiveSpeed * dt

        // Keep position safely bounded within the aquarium (unless exiting screen due to hunger)
        if (!isStarving) {
            val halfWidth = aspectRatio * 0.95f
            x = x.coerceIn(-halfWidth, halfWidth)
        }
        y = y.coerceIn(-0.72f, 0.68f)

        // Steer heading toward target angle with smooth turning arc
        val targetHeading = atan2(dy, dx)
        var angleDiff = targetHeading - heading
        while (angleDiff > PI) angleDiff -= TWO_PI
        while (angleDiff < -PI) angleDiff += TWO_PI
        val turnLerp = 1f - exp(-kTurnRate * dt)
        heading += angleDiff * turnLerp

        // Normalize heading to [-PI, PI] to prevent unbounded angle accumulation and while-loop stalling
        while (heading > PI) heading -= TWO_PI
        while (heading < -PI) heading += TWO_PI

        if (kotlin.math.abs(dx) > kFacingDeadzone) {
            facingSign = if (dx > 0f) 1f else -1f
        }
        val mirrorLerp = 1f - exp(-kMirrorRate * dt)
        mirrorBlend += (facingSign - mirrorBlend) * mirrorLerp

        // Pitch: flatten body horizontally when crawling along rock ledges (both current y and target on rocks)
        val isCrawlingOnRocks = y < -0.60f && targetY < -0.58f
        val desiredPitch = if (isCrawlingOnRocks) 0f else (dy / safeDist) * kMaxPitchDegrees
        stepPitchSpring(desiredPitch, dt)

        val depthLerp = 1f - exp(-kDepthEaseRate * dt)
        depth = (depth + (targetDepth - depth) * depthLerp).coerceIn(0f, 1f)

        // Swim and leg animation phases (periodically wrapped to maintain precision indefinitely)
        val movementFactor = effectiveSpeed * 8f
        swimPhase = (swimPhase + dt * (2.0f + movementFactor)) % FOUR_PI
        legPhase = (legPhase + dt * (4.0f + movementFactor * 2.0f)) % TWO_PI
    }

    /**
     * Triggered when touched: causes the axolotl to swim towards a rock on the tank seabed,
     * stay resting there for a couple of seconds, and then resume its normal wandering behavior.
     */
    fun touch(touchX: Float, touchY: Float, availableRocks: List<Rock> = emptyList()) {
        if (isStarving) return
        isSeekingRock = true
        isRestingOnRock = false
        isStartled = true
        startleTimer = 5f
        pauseTimer = 0f
        swimWaypointsRemaining = 0

        val awaySign = if (x >= touchX) 1f else -1f

        if (availableRocks.isNotEmpty()) {
            // Find rocks adequately distanced from touch point
            val safeRocks = availableRocks.filter { kotlin.math.abs(it.x - touchX) > 0.20f }
            val pool = if (safeRocks.isNotEmpty()) safeRocks else availableRocks

            // Prefer rocks at least slightly away from current x so the swim to the rock is clearly visible
            val farEnough = pool.filter { kotlin.math.abs(it.x - x) > 0.12f }
            val candidatePool = if (farEnough.isNotEmpty()) farEnough else pool

            val chosenRock = candidatePool.minByOrNull { rock ->
                val dx = rock.x - x
                val sameDirection = (dx * awaySign) > 0f
                val dirPenalty = if (sameDirection) 0f else 0.6f
                kotlin.math.abs(dx) + dirPenalty
            } ?: candidatePool.random()

            val depthScale = 1f - (1f - kMinScaleAtDepth) * chosenRock.depth
            targetX = chosenRock.x
            targetY = (kRockFloorY + chosenRock.scaleY * depthScale * 0.95f).coerceIn(-0.71f, -0.62f)
            targetDepth = chosenRock.depth
        } else {
            val fleeDist = 0.45f + Random.nextFloat() * 0.4f
            targetX = (x + awaySign * fleeDist).coerceIn(-1.1f, 1.1f)
            targetY = -0.68f + Random.nextFloat() * 0.05f
            targetDepth = Random.nextFloat()
        }

        hasTarget = true
        speedMultiplier = 2.0f
        targetSpeedMultiplier = 2.0f
    }

    fun facingScale(): Float = mirrorBlend

    internal fun pickNewTarget(aspectRatio: Float) {
        val halfWidth = aspectRatio * 0.85f
        targetX = Random.nextFloat() * (halfWidth * 2f) - halfWidth

        val onBottom = y < -0.58f

        if (onBottom) {
            if (swimWaypointsRemaining > 0) {
                swimWaypointsRemaining--
                targetY = if (Random.nextFloat() < 0.45f) {
                    0.15f + Random.nextFloat() * 0.45f // Upper water: [0.15, 0.60]
                } else {
                    -0.25f + Random.nextFloat() * 0.35f // Mid water: [-0.25, 0.10]
                }
            } else {
                val roll = Random.nextFloat()
                when {
                    roll < 0.40f -> {
                        // Crawl along rock surface peaks
                        targetY = -0.70f + Random.nextFloat() * 0.08f
                        swimWaypointsRemaining = 0
                    }
                    roll < 0.75f -> {
                        // Swim up into mid-water
                        targetY = -0.30f + Random.nextFloat() * 0.40f // [-0.30, 0.10]
                        swimWaypointsRemaining = Random.nextInt(1, 4)
                    }
                    else -> {
                        // Swim high up into upper tank / surface
                        targetY = 0.15f + Random.nextFloat() * 0.45f // [0.15, 0.60]
                        swimWaypointsRemaining = Random.nextInt(2, 5)
                    }
                }
            }
        } else {
            // Already swimming in open water
            if (swimWaypointsRemaining > 0) {
                swimWaypointsRemaining--
                targetY = if (Random.nextFloat() < 0.50f) {
                    0.10f + Random.nextFloat() * 0.50f // Upper water: [0.10, 0.60]
                } else {
                    -0.35f + Random.nextFloat() * 0.40f // Mid water: [-0.35, 0.05]
                }
            } else {
                // Completed excursion: glide back down to rocks/floor
                targetY = -0.70f + Random.nextFloat() * 0.08f
            }
        }

        targetDepth = Random.nextFloat()
        hasTarget = true

        if (!isStartled) {
            val dy = targetY - y
            targetSpeedMultiplier = when {
                dy > 0.25f -> 1.35f + Random.nextFloat() * 0.20f // swimming up
                dy < -0.25f -> 0.85f + Random.nextFloat() * 0.15f // gliding down
                else -> if (Random.nextFloat() < 0.25f) 1.25f else 1.0f
            }
        }
    }

    private fun stepPitchSpring(desiredPitch: Float, deltaTime: Float) {
        val springAccel = (desiredPitch - pitchDegrees) * kPitchSpringStiffness - pitchVelocity * kPitchSpringDamping
        pitchVelocity += springAccel * deltaTime
        pitchDegrees = (pitchDegrees + pitchVelocity * deltaTime).coerceIn(-kMaxPitchOvershoot, kMaxPitchOvershoot)
    }

    companion object {
        private const val PI = kotlin.math.PI.toFloat()
        private const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()
        private const val FOUR_PI = (kotlin.math.PI * 4.0).toFloat()

        private const val kTurnRate = 3.5f
        private const val kFacingDeadzone = 0.02f
        private const val kMirrorRate = 12f
        private const val kMaxPitchDegrees = 32f
        private const val kPitchSpringStiffness = 85f
        private const val kPitchSpringDamping = 8.5f
        private const val kMaxPitchOvershoot = 48f
        private const val kMinSpeedAtDepth = 0.45f
        private const val kRockFloorY = -1.05f
        private const val kMinScaleAtDepth = 0.40f
        private const val kDepthEaseRate = 1.5f
        private const val kSpeedEaseRate = 1.5f
    }
}
