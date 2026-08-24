package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Movement + swim-animation state for the single turtle wandering around the tank.
 *
 * Picks a random waypoint within the swimmable bounds and steers toward it at a roughly
 * constant speed; once close enough (or if it never had one), it picks a new one - producing
 * a meandering patrol instead of a straight back-and-forth path. [swimPhase] accumulates
 * while moving and drives the flipper-flap animation in turtle.frag.
 *
 * [heading] is the turtle's current facing angle in radians (0 = facing/moving right), eased
 * toward the angle-to-target every frame instead of snapping to it - see [update]'s comment.
 */
class Turtle {
    var x = 0f
        private set
    var y = -0.3f
        private set

    /** Current facing angle in radians, smoothed each frame - see class doc. */
    var heading = 0f
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

        // Steer heading toward the target angle instead of snapping to it: each frame it
        // closes a fraction of the remaining angular gap (framerate-independent via the usual
        // 1 - e^(-rate*dt) smoothing curve), producing a natural turning arc instead of an
        // instant snap. The gap is wrapped into [-PI, PI] first so it always turns the short
        // way, never spins around the long way when the target is just behind it.
        val targetHeading = atan2(dy, dx)
        var angleDiff = targetHeading - heading
        while (angleDiff > PI) angleDiff -= TWO_PI
        while (angleDiff < -PI) angleDiff += TWO_PI
        val turnLerp = 1f - exp(-kTurnRate * deltaTime)
        heading += angleDiff * turnLerp

        swimPhase += deltaTime * (2.2f + speed * 4f)
    }

    /**
     * X-scale multiplier for the (always "facing right") sprite: +1 heading straight right,
     * -1 heading straight left, passing smoothly through 0 as [heading] crosses vertical - so
     * AcuarioRenderer applying this as a scale reads as the turtle turning in depth (thinning
     * to an edge-on silhouette, then re-expanding facing the other way) rather than an instant
     * left/right mirror pop.
     */
    fun facingScale(): Float = cos(heading)

    private fun pickNewTarget(aspectRatio: Float) {
        // Roams the lower two-thirds of the water column - turtles cruise nearer the
        // bottom/mid-depth rather than right at the surface.
        targetX = Random.nextFloat() * (aspectRatio * 1.6f) - aspectRatio * 0.8f
        targetY = -0.9f + Random.nextFloat() * 1.1f
        hasTarget = true
    }

    private companion object {
        const val PI = kotlin.math.PI.toFloat()
        const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()

        // Chosen so heading closes ~5% of the remaining angular gap per 1/60s frame, i.e.
        // 1 - e^(-kTurnRate/60) ≈ 0.05.
        const val kTurnRate = 3.08f
    }
}
