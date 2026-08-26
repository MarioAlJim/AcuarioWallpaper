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
 *
 * Propulsion: once per flap cycle, right at the front flippers' downstroke (their peak
 * velocity - see [consumePowerStrokeEvent]'s comment), AcuarioRenderer spawns a couple of
 * small bubbles at [frontFlipperWorldPosition], emphasizing the effort of the stroke.
 *
 * Startle response: [touch] (called by AcuarioRenderer when the user taps directly on this
 * turtle) overrides everything else - even a breathing pause mid-surface/mid-hold - and drives
 * three phases via [startleState]: RETRACTING eases [retraction] from 0 to 1 while the turtle
 * coasts on whatever momentum it had (see [driftVX]/[driftVY]), HIDING then holds fully
 * retracted for [kHideDurationMin]-[kHideDurationMax] seconds, drifting further on that same
 * decaying momentum - both read as an inert shell floating passively, not a paused sprite -
 * and FLEEING picks a waypoint straight away from the touch point and darts toward it at
 * [kFleeSpeedMultiplier], unfolding back out of the shell as it goes, via the normal
 * waypoint-chasing code in [update] (reusing hasTarget/targetX/targetY exactly like a regular
 * wander leg). Landing back on a normal waypoint at the end of FLEEING resumes ordinary
 * wandering/breathing as if nothing happened. [retraction] itself eases back toward 0 on its
 * own regardless of state once the startle sequence is behind it, so turtle.frag (which reads
 * it as uRetraction) always shows a smooth pose whether the turtle is mid-flee or long done.
 */
class Turtle {
    /** Randomly assigned at construction and fixed for the turtle's lifetime - not user-selectable. */
    val shinyType: Int
    val palette: TurtlePalette

    init {
        shinyType = if (Random.nextFloat() < 0.01f) {
            if (Random.nextBoolean()) 1 else 2
        } else {
            0
        }
        palette = when (shinyType) {
            1 -> TurtlePalette.SHINY_GOLD
            2 -> TurtlePalette.SHINY_DIAMOND
            else -> TurtlePalette.PALETTES.random()
        }
    }

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
    // Occasionally bursts faster or slower than the baseline above during normal wandering (not
    // while surfacing/holding its breath) - see pickNewTarget()'s comment - eased toward its
    // target the same way depth is, so a burst reads as a deliberate acceleration/deceleration
    // rather than a snap.
    private var speedMultiplier = 1f
    private var targetSpeedMultiplier = 1f

    private enum class BreathPhase { SWIMMING, SURFACING, HOLDING_BREATH }
    private var breathPhase = BreathPhase.SWIMMING
    private var oxygenTimer = randomOxygenInterval()
    private var surfaceHoldTimer = 0f
    private var exhalePending = false
    private var powerStrokePending = false

    private enum class StartleState { NONE, RETRACTING, HIDING, FLEEING }
    private var startleState = StartleState.NONE
    private var startleTimer = 0f

    // Residual velocity the turtle keeps coasting on while withdrawn (RETRACTING/HIDING),
    // decaying toward 0 in updateStartled() - see [touch].
    private var driftVX = 0f
    private var driftVY = 0f

    // Where the touch that triggered the current startle landed, in world space - pickFleeTarget()
    // steers straight away from this point.
    private var touchOriginX = 0f
    private var touchOriginY = 0f

    /** 0 = normal swimming pose, 1 = fully withdrawn into the shell - see class doc and [touch]. */
    var retraction = 0f
        private set

    fun update(deltaTime: Float, aspectRatio: Float) {
        if (startleState == StartleState.RETRACTING || startleState == StartleState.HIDING) {
            updateStartled(deltaTime)
            return
        }

        // Relaxes back out of the shell on its own timescale once it's not actively withdrawing
        // above - covers unfolding mid-flee and guards against any leftover retraction if a new
        // breath cycle happens to land before it's fully back to 0.
        if (retraction > 0f) {
            val retractLerp = 1f - exp(-kRetractRate * deltaTime)
            retraction += (0f - retraction) * retractLerp
            if (retraction < 0.01f) retraction = 0f
        }

        if (breathPhase == BreathPhase.HOLDING_BREATH) {
            updateHoldingBreath(deltaTime)
            return
        }

        if (breathPhase == BreathPhase.SWIMMING && startleState == StartleState.NONE) {
            oxygenTimer -= deltaTime
            if (oxygenTimer <= 0f) {
                // Overrides whatever waypoint it was chasing - breathing takes priority.
                breathPhase = BreathPhase.SURFACING
                hasTarget = false
            }
        }

        if (!hasTarget) {
            when {
                startleState == StartleState.FLEEING -> pickFleeTarget(aspectRatio)
                breathPhase == BreathPhase.SURFACING -> pickSurfaceTarget()
                else -> pickNewTarget(aspectRatio)
            }
        }

        val dx = targetX - x
        val dy = targetY - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.05f) {
            when {
                breathPhase == BreathPhase.SURFACING -> {
                    // Arrived at the surface: pause and level out instead of immediately picking
                    // the next normal waypoint.
                    breathPhase = BreathPhase.HOLDING_BREATH
                    surfaceHoldTimer = kSurfaceHoldMin + Random.nextFloat() * (kSurfaceHoldMax - kSurfaceHoldMin)
                }
                startleState == StartleState.FLEEING -> {
                    // Reached safety - the scare is over, resume ordinary wandering.
                    startleState = StartleState.NONE
                    pickNewTarget(aspectRatio)
                }
                else -> pickNewTarget(aspectRatio)
            }
            return
        }

        // Parallax cue: turtles drifting deeper into the tank move more slowly than ones near
        // the glass, same as distant things crossing the screen slower in a real parallax
        // scene. Scales the whole travel speed (not just its horizontal share) since "lateral"
        // is by far the dominant component of most legs here anyway, and a uniform slowdown is
        // simpler than splitting it axis-by-axis for the same visual effect.
        val speedLerp = 1f - exp(-kSpeedEaseRate * deltaTime)
        speedMultiplier += (targetSpeedMultiplier - speedMultiplier) * speedLerp
        val effectiveSpeed = speed * (1f - (1f - kMinSpeedAtDepth) * depth) * speedMultiplier
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

        val previousSwimPhase = swimPhase
        swimPhase += deltaTime * (2.2f + speed * 4f) * speedMultiplier

        // Power stroke: turtle.frag animates the front flippers as sin(swimPhase)*0.14, so
        // their downstroke - the actual "push" against the water - peaks in velocity exactly
        // where that sine crosses zero going negative, i.e. at swimPhase == PI (mod 2*PI).
        // floor((phase - PI) / (2*PI)) is a step function that ticks up by exactly 1 every time
        // phase crosses one of those instants, so comparing it before/after this frame's
        // advance detects the crossing without any explicit modulo/wraparound bookkeeping.
        val strokesBefore = kotlin.math.floor((previousSwimPhase - PI) / TWO_PI)
        val strokesAfter = kotlin.math.floor((swimPhase - PI) / TWO_PI)
        if (strokesAfter > strokesBefore) {
            powerStrokePending = true
        }
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

    /**
     * RETRACTING/HIDING: no waypoint-chasing at all, just [retraction] easing toward 1 and the
     * turtle coasting on [driftVX]/[driftVY] (set once in [touch], decaying here toward 0) - an
     * inert shell floating passively rather than a puppet frozen in place. Pitch relaxes toward
     * level the same way it does while holding a breath. swimPhase is deliberately left untouched
     * (not advanced) so the flippers stop flapping mid-fold instead of continuing to cycle while
     * they shrink into the shell.
     */
    private fun updateStartled(deltaTime: Float) {
        x += driftVX * deltaTime
        y += driftVY * deltaTime - kInertSinkSpeed * deltaTime
        val dampLerp = 1f - exp(-kDriftDampRate * deltaTime)
        driftVX -= driftVX * dampLerp
        driftVY -= driftVY * dampLerp

        stepPitchSpring(desiredPitch = 0f, deltaTime)

        val retractLerp = 1f - exp(-kRetractRate * deltaTime)
        retraction += (1f - retraction) * retractLerp

        if (startleState == StartleState.RETRACTING) {
            if (retraction > 0.97f) {
                retraction = 1f
                startleState = StartleState.HIDING
                startleTimer = kHideDurationMin + Random.nextFloat() * (kHideDurationMax - kHideDurationMin)
            }
            return
        }

        // HIDING
        startleTimer -= deltaTime
        if (startleTimer <= 0f) {
            startleState = StartleState.FLEEING
            hasTarget = false
        }
    }

    /**
     * Straight away from wherever the turtle was touched: extends the touch-to-turtle vector
     * outward by a random flee distance and clamps it into the normal roaming bounds (falls back
     * to fleeing along the turtle's current heading if it was touched dead-on, i.e. that vector
     * is ~zero). Sets [Turtle.speedMultiplier] directly (not just its target) so the dart-off
     * reads as an immediate burst of speed rather than an ease-in.
     */
    private fun pickFleeTarget(aspectRatio: Float) {
        var dirX = x - touchOriginX
        var dirY = y - touchOriginY
        val len = sqrt(dirX * dirX + dirY * dirY)
        if (len < 0.001f) {
            dirX = kotlin.math.cos(heading)
            dirY = kotlin.math.sin(heading)
        } else {
            dirX /= len
            dirY /= len
        }
        val fleeDist = kFleeDistanceMin + Random.nextFloat() * (kFleeDistanceMax - kFleeDistanceMin)
        targetX = (x + dirX * fleeDist).coerceIn(-aspectRatio * 0.9f, aspectRatio * 0.9f)
        targetY = (y + dirY * fleeDist).coerceIn(-0.9f, 0.2f)
        hasTarget = true
        targetSpeedMultiplier = kFleeSpeedMultiplier
        speedMultiplier = kFleeSpeedMultiplier
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

    /**
     * True exactly once per flap cycle, right at the front flippers' downstroke (their peak
     * velocity through the water - see [update]'s comment) - callers (AcuarioRenderer) should
     * spawn a couple of small bubbles at [frontFlipperWorldPosition] when this returns true, to
     * emphasize the thrust. Consumes the event, so it won't fire again until the next stroke.
     */
    fun consumePowerStrokeEvent(): Boolean {
        if (!powerStrokePending) return false
        powerStrokePending = false
        return true
    }

    /**
     * Called by AcuarioRenderer when the user taps directly on this turtle, with the tap's own
     * world-space position. Kicks off the startle sequence described in the class doc - ignored
     * while already mid-reaction (startleState != NONE) so a flurry of taps doesn't restart it
     * over and over. Interrupts breathing outright, even mid-surface/mid-hold - being grabbed
     * takes priority over everything else the turtle was doing.
     */
    fun touch(touchX: Float, touchY: Float) {
        if (startleState != StartleState.NONE) return
        startleState = StartleState.RETRACTING
        touchOriginX = touchX
        touchOriginY = touchY
        // Keeps a bit of whatever momentum it had, decaying quickly in updateStartled() - reads
        // as gliding to an inert stop rather than snapping still.
        driftVX = kotlin.math.cos(heading) * speed * 0.5f
        driftVY = kotlin.math.sin(heading) * speed * 0.5f
        breathPhase = BreathPhase.SWIMMING
        hasTarget = false
    }

    /**
     * World-space position of the front-top ([top] = true) or front-bottom ([top] = false)
     * flipper's own local anchor - the same point turtle.frag centers
     * aFlipperTopFront/aFlipperBotFront on - transformed through the exact same
     * scale-then-pitch-rotate-then-mirror-then-translate chain AcuarioRenderer uses to draw
     * this turtle (see its drawTurtles() comment for why that order matters), so the
     * propulsion bubble trail spawns right where the flipper visually is regardless of the
     * turtle's current facing/pitch. [turtleScale] must be the same uniform scale
     * AcuarioRenderer draws this turtle at (its kTurtleScale).
     *
     * Writes the result into [out] (`out[0]` = x, `out[1]` = y) instead of returning a
     * `Pair<Float, Float>` - Kotlin boxes both Floats to build one, and this is called from
     * AcuarioRenderer's spawnPropulsionBubbles() on every qualifying power stroke, so a caller-
     * owned scratch array (the same pattern [AcuarioRenderer.mixColorInto] already uses for its
     * own per-frame color math) avoids that allocation entirely.
     */
    fun frontFlipperWorldPosition(top: Boolean, turtleScale: Float, out: FloatArray) {
        val frontFlap = kotlin.math.sin(swimPhase) * 0.14f
        val localX = 0.10f
        val localY = if (top) 0.46f + frontFlap else -0.46f - frontFlap

        val pitchRad = pitchDegrees * (PI / 180f)
        val cosP = kotlin.math.cos(pitchRad)
        val sinP = kotlin.math.sin(pitchRad)
        val rotatedX = localX * cosP - localY * sinP
        val rotatedY = localX * sinP + localY * cosP

        val mirroredX = rotatedX * facingScale()
        out[0] = x + turtleScale * mirroredX
        out[1] = y + turtleScale * rotatedY
    }

    private fun pickNewTarget(aspectRatio: Float) {
        // Roams the lower two-thirds of the water column - turtles cruise nearer the
        // bottom/mid-depth rather than right at the surface.
        targetX = Random.nextFloat() * (aspectRatio * 1.6f) - aspectRatio * 0.8f
        targetY = -0.9f + Random.nextFloat() * 1.1f
        hasTarget = true
        // A new "decision" to come closer or recede is made alongside every new waypoint.
        targetDepth = Random.nextFloat()

        // Occasionally (kSpeedBurstChance) commit to a faster or slower pace for this leg -
        // otherwise settle back to the normal cruising speed. Re-rolled at every waypoint change
        // (but not when surfacing to breathe - see pickSurfaceTarget()) so a burst is a
        // transient flourish rather than a permanent trait of this turtle.
        targetSpeedMultiplier = if (Random.nextFloat() < kSpeedBurstChance) {
            if (Random.nextBoolean()) {
                kFastSpeedMultiplierMin + Random.nextFloat() * (kFastSpeedMultiplierMax - kFastSpeedMultiplierMin)
            } else {
                kSlowSpeedMultiplierMin + Random.nextFloat() * (kSlowSpeedMultiplierMax - kSlowSpeedMultiplierMin)
            }
        } else {
            1f
        }
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

        // Chance, at each new waypoint, of committing to a speed burst for that leg instead of
        // cruising at the normal pace - see pickNewTarget()'s comment.
        const val kSpeedBurstChance = 0.3f
        const val kFastSpeedMultiplierMin = 1.35f
        const val kFastSpeedMultiplierMax = 1.7f
        const val kSlowSpeedMultiplierMin = 0.5f
        const val kSlowSpeedMultiplierMax = 0.75f
        // Chosen so speedMultiplier is ~95% of the way to a new burst/cruise target in about 2s
        // (1 - e^(-kSpeedEaseRate*2) ~= 0.95) - a deliberate ease, not a snap.
        const val kSpeedEaseRate = 1.5f

        // Startle response (see class doc). Chosen so retraction is ~95% of the way to its
        // target (0 or 1) in about 0.35s (1 - e^(-kRetractRate*0.35) ≈ 0.95) - quick enough to
        // read as a startled flinch, not a lazy fade.
        const val kRetractRate = 8.5f
        // Chosen so the residual drift velocity is ~95% decayed in about 1.5s
        // (1 - e^(-kDriftDampRate*1.5) ≈ 0.95) - a gliding-to-a-stop feel, not an instant halt.
        const val kDriftDampRate = 2f
        // Gentle passive sink while withdrawn, like a heavy, inert shell settling.
        const val kInertSinkSpeed = 0.02f
        const val kHideDurationMin = 2.5f
        const val kHideDurationMax = 4f
        // Well above even the fastest normal cruising burst (kFastSpeedMultiplierMax) - the
        // whole point is that fleeing reads as dramatically faster than any ordinary swim.
        const val kFleeSpeedMultiplier = 2.4f
        const val kFleeDistanceMin = 0.9f
        const val kFleeDistanceMax = 1.4f
    }
}
