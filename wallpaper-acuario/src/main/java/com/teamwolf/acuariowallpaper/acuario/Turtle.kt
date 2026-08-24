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
 *
 * [pitchDegrees] is a "nose up/down" body tilt driven by the vertical component of travel
 * (ascending pitches the nose up, descending pitches it down), animated as a lightly
 * underdamped spring so it overshoots and settles with a little bounce instead of just
 * easing to a stop - like a body with real weight in the water.
 *
 * [depth] simulates the turtle drifting nearer the glass (0) or back into the tank (1) on a
 * flat 2D scene: a new target depth is chosen alongside every new waypoint (see
 * [pickNewTarget]) and eased toward slowly, like the x/y wander. AcuarioRenderer reads it to
 * shrink+tint distant turtles toward the water's deep color, and [update] itself uses it to
 * slow down travel speed - the parallax cue that "background" turtles drift more lazily than
 * ones "against the glass".
 *
 * Breathing cycle: every so often ([kOxygenIntervalMin]-[kOxygenIntervalMax] seconds) the
 * turtle overrides its normal wandering, surfaces, holds at the top leveling out to a
 * horizontal pitch, then dives back down - see the [BreathPhase] states below and
 * [consumeExhaleEvent] for the "exhale" bubble burst AcuarioRenderer spawns on the way back
 * down.
 */
class Turtle {
    /** Randomly assigned at construction and fixed for the turtle's lifetime - not user-selectable. */
    val palette: TurtlePalette = TurtlePalette.PALETTES.random()

    // Randomized (instead of a fixed spot) so multiple turtles don't all start stacked on top
    // of each other; aspectRatio isn't known yet at construction time, so this uses a
    // generic-enough range and the first update() call picks a proper waypoint right after.
    var x = Random.nextFloat() * 1.2f - 0.6f
        private set
    var y = -0.9f + Random.nextFloat() * 1.1f
        private set

    /** Current facing angle in radians, smoothed each frame - see class doc. */
    var heading = 0f
        private set

    var swimPhase = 0f
        private set

    /** "Nose up/down" body tilt in degrees - see class doc. */
    var pitchDegrees = 0f
        private set
    private var pitchVelocity = 0f

    /** 0 = near the glass, 1 = deep in the tank - see class doc. */
    var depth = Random.nextFloat()
        private set
    private var targetDepth = depth

    private var targetX = 0f
    private var targetY = 0f
    private var hasTarget = false

    // Discrete left/right side (+1/-1) the sprite should mirror to, and a fast-easing blend
    // toward it - see facingScale()'s doc for why this is kept separate from `heading`.
    private var facingSign = 1f
    private var mirrorBlend = 1f

    private val speed = 0.10f + Random.nextFloat() * 0.05f

    private enum class BreathPhase { SWIMMING, SURFACING, HOLDING_BREATH }
    private var breathPhase = BreathPhase.SWIMMING
    private var oxygenTimer = randomOxygenInterval()
    private var surfaceHoldTimer = 0f
    private var exhalePending = false

    fun update(deltaTime: Float, aspectRatio: Float) {
        if (breathPhase == BreathPhase.HOLDING_BREATH) {
            updateHoldingBreath(deltaTime)
            return
        }

        if (breathPhase == BreathPhase.SWIMMING) {
            oxygenTimer -= deltaTime
            if (oxygenTimer <= 0f) {
                // Overrides whatever waypoint it was chasing - breathing takes priority.
                breathPhase = BreathPhase.SURFACING
                hasTarget = false
            }
        }

        if (!hasTarget) {
            if (breathPhase == BreathPhase.SURFACING) pickSurfaceTarget() else pickNewTarget(aspectRatio)
        }

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.05f) {
            if (breathPhase == BreathPhase.SURFACING) {
                // Arrived at the surface: pause and level out instead of immediately picking
                // the next normal waypoint.
                breathPhase = BreathPhase.HOLDING_BREATH
                surfaceHoldTimer = kSurfaceHoldMin + Random.nextFloat() * (kSurfaceHoldMax - kSurfaceHoldMin)
            } else {
                pickNewTarget(aspectRatio)
            }
            return
        }

        // Parallax cue: turtles drifting deeper into the tank move more slowly than ones near
        // the glass, same as distant things crossing the screen slower in a real parallax
        // scene. Scales the whole travel speed (not just its horizontal share) since "lateral"
        // is by far the dominant component of most legs here anyway, and a uniform slowdown is
        // simpler than splitting it axis-by-axis for the same visual effect.
        val effectiveSpeed = speed * (1f - (1f - kMinSpeedAtDepth) * depth)
        x += (dx / dist) * effectiveSpeed * deltaTime
        y += (dy / dist) * effectiveSpeed * deltaTime

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

        // Pitch: nose precedes vertical movement, based exclusively on dy's share of the
        // travel direction (dy/dist, i.e. how much of this leg is "straight up/down" vs
        // "sideways") mapped straight to +-kMaxPitchDegrees.
        val desiredPitch = (dy / dist) * kMaxPitchDegrees
        stepPitchSpring(desiredPitch, deltaTime)

        // Depth drifts slowly toward targetDepth (picked alongside each new x/y waypoint) -
        // deliberately much slower than the mirror/pitch eases above, so "coming closer" or
        // "receding" reads as a gradual drift rather than a snap.
        val depthLerp = 1f - exp(-kDepthEaseRate * deltaTime)
        depth += (targetDepth - depth) * depthLerp

        swimPhase += deltaTime * (2.2f + speed * 4f)
    }

    /**
     * While holding at the surface: no travel, just level the body out to a horizontal pitch
     * (via the same spring as normal swimming, so it settles with the same natural bounce)
     * and count down the hold. Flippers/heading/mirror/depth are left untouched - it's resting,
     * not swimming.
     */
    private fun updateHoldingBreath(deltaTime: Float) {
        stepPitchSpring(desiredPitch = 0f, deltaTime)

        surfaceHoldTimer -= deltaTime
        if (surfaceHoldTimer <= 0f) {
            breathPhase = BreathPhase.SWIMMING
            oxygenTimer = randomOxygenInterval()
            hasTarget = false
            // Consumed by AcuarioRenderer to spawn a couple of big "exhale" bubbles right as
            // the turtle dives back down.
            exhalePending = true
        }
    }

    private fun stepPitchSpring(desiredPitch: Float, deltaTime: Float) {
        // Driven as a lightly underdamped spring (not a plain ease) so it doesn't just glide
        // to a stop: it overshoots the target pitch and settles with a small bounce/wobble,
        // reading as a body with real weight and momentum in the water rather than a puppet
        // snapping straight to the "correct" angle.
        val springAccel = (desiredPitch - pitchDegrees) * kPitchSpringStiffness - pitchVelocity * kPitchSpringDamping
        pitchVelocity += springAccel * deltaTime
        pitchDegrees = (pitchDegrees + pitchVelocity * deltaTime).coerceIn(-kMaxPitchOvershoot, kMaxPitchOvershoot)
    }

    /**
     * X-scale multiplier for the (always "facing right") sprite: settles at +1/-1 facing
     * right/left, passing through 0 only briefly while [facingSign] just flipped - see
     * [update]'s comment for why this isn't simply cos(heading).
     */
    fun facingScale(): Float = mirrorBlend

    /**
     * True exactly once, right as the turtle finishes a surface breathing pause and starts
     * diving back down - callers (AcuarioRenderer) should spawn a couple of big bubbles at
     * (x, y) when this returns true. Consumes the event, so it won't fire again until the next
     * breathing cycle completes.
     */
    fun consumeExhaleEvent(): Boolean {
        if (!exhalePending) return false
        exhalePending = false
        return true
    }

    private fun pickNewTarget(aspectRatio: Float) {
        // Roams the lower two-thirds of the water column - turtles cruise nearer the
        // bottom/mid-depth rather than right at the surface.
        targetX = Random.nextFloat() * (aspectRatio * 1.6f) - aspectRatio * 0.8f
        targetY = -0.9f + Random.nextFloat() * 1.1f
        hasTarget = true
        // A new "decision" to come closer or recede is made alongside every new waypoint.
        targetDepth = Random.nextFloat()
    }

    /** Straight up to near the top of the screen to breathe - see class doc. */
    private fun pickSurfaceTarget() {
        targetX = x
        targetY = kSurfaceY
        targetDepth = kSurfaceDepth
        hasTarget = true
    }

    private fun randomOxygenInterval(): Float =
        kOxygenIntervalMin + Random.nextFloat() * (kOxygenIntervalMax - kOxygenIntervalMin)

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

        const val kMaxPitchDegrees = 20f

        // Damping ratio zeta = kPitchSpringDamping / (2*sqrt(kPitchSpringStiffness)) ≈ 0.47 -
        // underdamped on purpose, for a visible but controlled single overshoot/bounce rather
        // than a dead-stop ease or a wobbling oscillation.
        const val kPitchSpringStiffness = 90f
        const val kPitchSpringDamping = 9f

        // Safety clamp: with zeta ≈ 0.47 the spring overshoots kMaxPitchDegrees by roughly
        // 18%, so this just guards against a much larger transient in some edge case.
        const val kMaxPitchOvershoot = 40f

        // Speed at full depth (1.0) is this fraction of speed at the glass (0.0).
        const val kMinSpeedAtDepth = 0.45f

        // Chosen so depth is ~95% of the way to a new targetDepth in about 2s
        // (1 - e^(-kDepthEaseRate*2) ≈ 0.95) - a deliberate, gradual drift rather than a snap.
        const val kDepthEaseRate = 1.5f

        // Randomized per-turtle (and re-rolled after every breath) so multiple turtles don't
        // all surface in lockstep.
        const val kOxygenIntervalMin = 30f
        const val kOxygenIntervalMax = 120f

        // Near the top edge but low enough that the turtle's own scale never clips offscreen.
        const val kSurfaceY = 0.85f
        // Near the glass while breathing, for a clearer view of it.
        const val kSurfaceDepth = 0.1f

        const val kSurfaceHoldMin = 2f
        const val kSurfaceHoldMax = 3.5f
    }
}
