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
class Axolotl {
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
            else -> AxolotlPalette.PALETTES.random()
        }
    }

    var x = Random.nextFloat() * 1.2f - 0.6f
        private set
    var y = -0.81f + Random.nextFloat() * 0.06f
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

    private var targetX = 0f
    private var targetY = 0f
    private var hasTarget = false

    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val speed = 0.08f + Random.nextFloat() * 0.04f
    private var speedMultiplier = 1f
    private var targetSpeedMultiplier = 1f

    private var startleTimer = 0f
    private var isStartled = false

    fun update(deltaTime: Float, aspectRatio: Float) {
        // Gills flutter continuously whether moving or stationary
        val gillSpeed = if (isStartled) 6.0f else 2.5f
        gillPhase += deltaTime * gillSpeed

        if (isStartled) {
            startleTimer -= deltaTime
            if (startleTimer <= 0f) {
                isStartled = false
                targetSpeedMultiplier = 1f
            }
        }

        if (!hasTarget) {
            pickNewTarget(aspectRatio)
        }

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)

        if (dist < 0.04f) {
            pickNewTarget(aspectRatio)
            return
        }

        val speedLerp = 1f - exp(-kSpeedEaseRate * deltaTime)
        speedMultiplier += (targetSpeedMultiplier - speedMultiplier) * speedLerp
        val effectiveSpeed = speed * (1f - (1f - kMinSpeedAtDepth) * depth) * speedMultiplier

        x += (dx / dist) * effectiveSpeed * deltaTime
        y += (dy / dist) * effectiveSpeed * deltaTime

        // Steer heading toward target angle with smooth turning arc
        val targetHeading = atan2(dy, dx)
        var angleDiff = targetHeading - heading
        while (angleDiff > PI) angleDiff -= TWO_PI
        while (angleDiff < -PI) angleDiff += TWO_PI
        val turnLerp = 1f - exp(-kTurnRate * deltaTime)
        heading += angleDiff * turnLerp

        if (kotlin.math.abs(dx) > kFacingDeadzone) {
            facingSign = if (dx > 0f) 1f else -1f
        }
        val mirrorLerp = 1f - exp(-kMirrorRate * deltaTime)
        mirrorBlend += (facingSign - mirrorBlend) * mirrorLerp

        // Pitch: flatten body horizontally when crawling along rock ledges (y < -0.72f)
        val isCrawlingOnRocks = y < -0.72f
        val desiredPitch = if (isCrawlingOnRocks) 0f else (dy / dist) * kMaxPitchDegrees
        stepPitchSpring(desiredPitch, deltaTime)

        val depthLerp = 1f - exp(-kDepthEaseRate * deltaTime)
        depth += (targetDepth - depth) * depthLerp

        // Swim and leg animation phases
        val movementFactor = effectiveSpeed * 8f
        swimPhase += deltaTime * (2.0f + movementFactor)
        legPhase += deltaTime * (4.0f + movementFactor * 2.0f)
    }

    /**
     * Triggered when touched: startles the axolotl into a quick gill flutter and swim dart.
     */
    fun touch(touchX: Float, touchY: Float) {
        isStartled = true
        startleTimer = 2.5f

        var dirX = x - touchX
        var dirY = y - touchY
        val len = sqrt(dirX * dirX + dirY * dirY)
        if (len < 0.001f) {
            dirX = cos(heading)
            dirY = sin(heading)
        } else {
            dirX /= len
            dirY /= len
        }

        val fleeDist = 0.6f + Random.nextFloat() * 0.5f
        targetX = x + dirX * fleeDist
        targetY = (y + dirY * fleeDist).coerceIn(-0.84f, 0.10f)
        hasTarget = true
        speedMultiplier = 2.2f
        targetSpeedMultiplier = 2.2f
    }

    fun facingScale(): Float = mirrorBlend

    private fun pickNewTarget(aspectRatio: Float) {
        val halfWidth = aspectRatio * 0.85f
        targetX = Random.nextFloat() * (halfWidth * 2f) - halfWidth

        // 85% chance to crawl/walk directly on top of rock surface peaks (y = -0.83f to -0.75f),
        // 15% chance to take a short hop/float into mid-water before landing back on the rocks.
        if (Random.nextFloat() < 0.85f) {
            targetY = -0.82f + Random.nextFloat() * 0.07f
        } else {
            targetY = -0.50f + Random.nextFloat() * 0.25f
        }

        targetDepth = Random.nextFloat()
        hasTarget = true

        if (!isStartled) {
            targetSpeedMultiplier = if (Random.nextFloat() < 0.3f) 1.4f else 1.0f
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

        private const val kTurnRate = 3.5f
        private const val kFacingDeadzone = 0.02f
        private const val kMirrorRate = 12f
        private const val kMaxPitchDegrees = 22f
        private const val kPitchSpringStiffness = 85f
        private const val kPitchSpringDamping = 8.5f
        private const val kMaxPitchOvershoot = 40f
        private const val kMinSpeedAtDepth = 0.45f
        private const val kDepthEaseRate = 1.5f
        private const val kSpeedEaseRate = 1.5f
    }
}
