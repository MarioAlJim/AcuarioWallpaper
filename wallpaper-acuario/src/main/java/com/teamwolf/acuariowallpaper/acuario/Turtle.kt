package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Movement + swim-animation state for the single turtle wandering around the tank.
 *
 * Picks a random waypoint within the swimmable bounds and steers toward it at a roughly
 * constant speed; once close enough (or if it never had one), it picks a new one - producing
 * a meandering patrol instead of a straight back-and-forth path. [swimPhase] accumulates
 * while moving and drives the flipper-flap animation in turtle.frag.
 */
class Turtle {
    var x = 0f
        private set
    var y = -0.3f
        private set

    /** -1 = facing/moving left, 1 = facing/moving right. Used to mirror the sprite. */
    var facing = 1f
        private set

    var swimPhase = 0f
        private set

    private var targetX = 0f
    private var targetY = 0f
    private var hasTarget = false

    private val speed = 0.10f + Random.nextFloat() * 0.05f

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

        x += (dx / dist) * speed * deltaTime
        y += (dy / dist) * speed * deltaTime

        if (kotlin.math.abs(dx) > 0.01f) {
            facing = if (dx >= 0f) 1f else -1f
        }

        swimPhase += deltaTime * (2.2f + speed * 4f)
    }

    private fun pickNewTarget(aspectRatio: Float) {
        // Roams the lower two-thirds of the water column - turtles cruise nearer the
        // bottom/mid-depth rather than right at the surface.
        targetX = Random.nextFloat() * (aspectRatio * 1.6f) - aspectRatio * 0.8f
        targetY = -0.9f + Random.nextFloat() * 1.1f
        hasTarget = true
    }
}
