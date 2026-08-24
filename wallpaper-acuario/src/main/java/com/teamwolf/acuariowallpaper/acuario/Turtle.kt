package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.atan2
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

    // Discrete left/right side (+1/-1) the sprite should mirror to, and a fast-easing blend
    // toward it - see facingScale()'s doc for why this is kept separate from `heading`.
    private var facingSign = 1f
    private var mirrorBlend = 1f

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

        // Which side the sprite mirrors to is driven only by the discrete horizontal
        // direction (with a dead zone so near-vertical travel - dx close to 0 - can't make it
        // flicker), not by the full travel angle: the roaming box is taller than it is wide on
        // a portrait screen, so `heading` spends long stretches near +-90 deg just cruising up
        // or down, and tying the mirror to it (e.g. via cos(heading)) left the turtle looking
        // squashed for most of that time instead of just during an actual side change.
        if (kotlin.math.abs(dx) > kFacingDeadzone) {
            facingSign = if (dx > 0f) 1f else -1f
        }
        // mirrorBlend eases toward facingSign on its own quick, fixed timescale, independent
        // of how long the real heading lingers near vertical - so a side change always reads
        // as a brief turn-in-depth flourish (thin -> re-expand) instead of a lingering squash.
        val mirrorLerp = 1f - exp(-kMirrorRate * deltaTime)
        mirrorBlend += (facingSign - mirrorBlend) * mirrorLerp

        swimPhase += deltaTime * (2.2f + speed * 4f)
    }

    /**
     * X-scale multiplier for the (always "facing right") sprite: settles at +1/-1 facing
     * right/left, passing through 0 only briefly while [facingSign] just flipped - see
     * [update]'s comment for why this isn't simply cos(heading).
     */
    fun facingScale(): Float = mirrorBlend

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

        const val kFacingDeadzone = 0.02f

        // Chosen so the mirror flip is ~95% done in about 0.25s (1 - e^(-kMirrorRate*0.25) ≈
        // 0.95) - fast enough to read as a snappy turn rather than a slow fade.
        const val kMirrorRate = 12f
    }
}
