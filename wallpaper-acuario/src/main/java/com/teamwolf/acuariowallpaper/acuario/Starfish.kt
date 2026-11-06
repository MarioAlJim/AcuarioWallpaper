package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * State & animation controller for a Starfish (Asteroidea) adhering to the front glass of the tank.
 *
 * Physically suctioned to the inner surface of the front viewing glass:
 * - Constant depth = 0.0f (no depth parallax relative to device screen).
 * - Very slow, natural crawling movement across the glass pane.
 * - Reactive to touch: flexes arms, emits micro-bubbles, and slowly crawls away from the touch point.
 */
class Starfish(customPalette: StarfishPalette? = null) {

    val shinyType: Int
    val palette: StarfishPalette

    init {
        shinyType = if (Random.nextFloat() < 0.10f) {
            if (Random.nextBoolean()) 1 else 2
        } else {
            0
        }
        palette = when (shinyType) {
            1 -> StarfishPalette.SHINY_GOLD
            2 -> StarfishPalette.SHINY_DIAMOND
            else -> customPalette ?: StarfishPalette.PALETTES.random()
        }
    }

    // Position on the front glass (world coordinates in [-aspectRatio, aspectRatio] x [-1, 1])
    var x = -0.15f + Random.nextFloat() * 0.30f
        private set
    var y = -0.25f + Random.nextFloat() * 0.50f
        private set

    val depth = 0.0f // Clings directly to the front glass

    var rotationDegrees = Random.nextFloat() * 360f
        private set

    var crawlPhase = Random.nextFloat() * 6.28318f
        private set

    /** 0.0 = calm, 1.0 = startled/flexing touch response. Decays exponentially. */
    var touchReact = 0f
        private set

    private var targetX = x
    private var targetY = y
    private var isCrawling = false
    private var crawlTimer = 5f + Random.nextFloat() * 15f
    private val crawlSpeed = 0.026f // Slow realistic sea star pace

    private var pendingBubblePuff = false

    fun consumeBubblePuffEvent(): Boolean {
        val result = pendingBubblePuff
        pendingBubblePuff = false
        return result
    }

    fun setInitialPosition(startX: Float, startY: Float, startRotation: Float = Random.nextFloat() * 360f) {
        x = startX
        y = startY
        targetX = startX
        targetY = startY
        rotationDegrees = startRotation
    }

    /**
     * Clamps the starfish safely inside the viewport based on the real runtime [aspectRatio]
     * so that its arms (radius ~ 0.11f) never poke outside the screen edges.
     */
    fun ensureWithinBounds(aspectRatio: Float) {
        val maxMarginX = (aspectRatio - 0.12f).coerceIn(0.05f, (aspectRatio * 0.80f).coerceAtLeast(0.05f))
        x = x.coerceIn(-maxMarginX, maxMarginX)
        y = y.coerceIn(-0.75f, 0.75f)
        targetX = targetX.coerceIn(-maxMarginX, maxMarginX)
        targetY = targetY.coerceIn(-0.75f, 0.75f)
    }

    fun update(deltaTime: Float, aspectRatio: Float) {
        val dt = deltaTime.coerceAtMost(0.1f)

        // Always keep safely inside screen boundaries
        val maxMarginX = (aspectRatio - 0.12f).coerceIn(0.05f, (aspectRatio * 0.80f).coerceAtLeast(0.05f))
        x = x.coerceIn(-maxMarginX, maxMarginX)
        y = y.coerceIn(-0.75f, 0.75f)

        // Decay touch reaction
        if (touchReact > 0.001f) {
            touchReact *= exp(-2.5f * dt)
        } else {
            touchReact = 0f
        }

        // Timer to initiate new crawl path
        if (!isCrawling) {
            crawlTimer -= dt
            if (crawlTimer <= 0f) {
                pickNewTarget(aspectRatio)
            }
        } else {
            val dx = targetX - x
            val dy = targetY - y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < 0.04f) {
                // Arrived at destination
                isCrawling = false
                crawlTimer = 18f + Random.nextFloat() * 25f // Rest on the glass for 18-43s
            } else {
                // Steer slowly towards target
                val targetAngleRad = atan2(dy, dx)
                val targetAngleDeg = Math.toDegrees(targetAngleRad.toDouble()).toFloat()

                // Smooth rotation interpolation
                var angleDiff = targetAngleDeg - rotationDegrees
                while (angleDiff > 180f) angleDiff -= 360f
                while (angleDiff < -180f) angleDiff += 360f

                val maxTurnSpeed = 22f // degrees/sec
                val turnStep = angleDiff.coerceIn(-maxTurnSpeed * dt, maxTurnSpeed * dt)
                rotationDegrees = (rotationDegrees + turnStep) % 360f

                // Advance position along current heading
                val rad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
                val moveSpeed = if (touchReact > 0.1f) crawlSpeed * 1.8f else crawlSpeed
                x += cos(rad) * moveSpeed * dt
                y += sin(rad) * moveSpeed * dt

                // Keep safely within screen boundaries
                x = x.coerceIn(-maxMarginX, maxMarginX)
                y = y.coerceIn(-0.75f, 0.75f)

                // Advance crawl animation phase
                crawlPhase = (crawlPhase + dt * 2.8f) % 6.2831853f
            }
        }
    }

    private fun pickNewTarget(aspectRatio: Float) {
        val maxMarginX = (aspectRatio - 0.12f).coerceIn(0.05f, (aspectRatio * 0.80f).coerceAtLeast(0.05f))
        targetX = Random.nextFloat() * (maxMarginX * 2f) - maxMarginX
        targetY = -0.70f + Random.nextFloat() * 1.40f
        isCrawling = true
    }

    /**
     * Responds to user touch.
     */
    fun touch(touchX: Float, touchY: Float, aspectRatio: Float = 1.0f) {
        touchReact = 1.0f
        pendingBubblePuff = true

        // Steer away from the touch point
        val dx = x - touchX
        val dy = y - touchY
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)

        // Flee target
        val fleeDist = 0.25f + Random.nextFloat() * 0.20f
        val maxMarginX = (aspectRatio - 0.12f).coerceIn(0.05f, (aspectRatio * 0.80f).coerceAtLeast(0.05f))
        targetX = (x + (dx / dist) * fleeDist).coerceIn(-maxMarginX, maxMarginX)
        targetY = (y + (dy / dist) * fleeDist).coerceIn(-0.75f, 0.75f)
        isCrawling = true
    }
}
