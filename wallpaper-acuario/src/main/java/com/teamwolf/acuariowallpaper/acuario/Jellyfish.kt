package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pulse + drift state for jellyfish floating through the tank.
 *
 * Unlike every other creature here, a jellyfish doesn't steer toward waypoints at all - real
 * jellyfish are weak swimmers that mostly go wherever the current takes them. So instead of
 * [Fish]/[Turtle]/[Manta]'s waypoint-wander + heading-steering machinery, a [Jellyfish] just
 * pulses its bell rhythmically ([pulsePhase] drives both jellyfish.frag's bell contraction and a
 * matching upward [thrustStrength] here - each contraction shoves it up a little) while otherwise
 * sinking passively ([sinkSpeed]) and drifting sideways on a slow, wandering "current"
 * ([currentPhase]). There's no heading/mirror-flip: a bell is radially symmetric enough that
 * facing direction doesn't read, so jellyfish.frag draws the same silhouette regardless of
 * drift direction.
 */
class Jellyfish {
    val shinyType: Int
    val palette: JellyfishPalette

    init {
        shinyType = if (Random.nextFloat() < 0.10f) {
            if (Random.nextBoolean()) 1 else 2
        } else {
            0
        }
        palette = when (shinyType) {
            1 -> JellyfishPalette.SHINY_GOLD
            2 -> JellyfishPalette.SHINY_DIAMOND
            else -> JellyfishPalette.PALETTES.random()
        }
    }

    var x = Random.nextFloat() * 1.4f - 0.7f
        private set
    var y = -0.9f + Random.nextFloat() * 1.7f
        private set

    // Drives jellyfish.frag's bell contraction/expansion and trailing tentacle ripple.
    var pulsePhase = Random.nextFloat() * TWO_PI
        private set

    // A slow, independent phase driving gentle side-to-side drift, standing in for the tank's
    // current rather than any deliberate steering.
    private var currentPhase = Random.nextFloat() * TWO_PI
    private val currentSpeed = 0.12f + Random.nextFloat() * 0.10f
    private val currentAmplitude = 0.10f + Random.nextFloat() * 0.08f

    // 30% slower than the original 1.1-1.6 rad/s range - pulses fire less often.
    private val pulseSpeed = 0.77f + Random.nextFloat() * 0.35f

    // How much thrust each bell contraction produces vs. how fast it sinks between contractions -
    // both vary per instance so a tank full of jellyfish doesn't rise/fall in lockstep; some
    // instances drift net-upward over time, others net-downward, same as real jellyfish bobbing
    // through the water column. thrustStrength is trimmed by ~35% from the original 0.5-0.8 range
    // for a noticeably gentler kick per pulse - the duty cycle (contracting exactly half of every
    // pulse period, see "contracting" below) makes the average drift rate scale directly with
    // this value regardless of pulseSpeed, so this alone is what controls how forceful the
    // movement reads.
    private val thrustStrength = 0.32f + Random.nextFloat() * 0.20f
    private val sinkSpeed = 0.12f + Random.nextFloat() * 0.08f

    private var previousPulseSin = sin(pulsePhase)

    var depth = Random.nextFloat()
        private set
    private var targetDepth = depth
    private var depthRetargetTimer = 4f + Random.nextFloat() * 6f

    fun update(deltaTime: Float, aspectRatio: Float) {
        pulsePhase += deltaTime * pulseSpeed
        currentPhase += deltaTime * currentSpeed

        // Thrust fires on the bell's active contraction stroke - jellyfish.frag makes the bell
        // taller/narrower as sin(pulsePhase) rises toward +1 (see its bellScaleX/Y), so the rising
        // edge is the muscular squeeze that pushes water out and shoves the animal up; the falling
        // edge back toward -1 is the passive re-expansion, which produces no thrust. Approximated
        // by comparing sin() before/after advancing the phase this frame, rather than tracking an
        // explicit contraction/expansion state machine.
        val pulseSin = sin(pulsePhase)
        val contracting = pulseSin > previousPulseSin
        previousPulseSin = pulseSin
        val thrust = if (contracting) thrustStrength else 0f

        y += (thrust - sinkSpeed) * deltaTime
        x += sin(currentPhase) * currentAmplitude * deltaTime

        // Gently clamp back into the tank instead of drifting off past the edges forever - a
        // jellyfish has no steering to correct course on its own.
        val halfWidth = aspectRatio * 0.85f
        x = x.coerceIn(-halfWidth, halfWidth)
        y = y.coerceIn(-0.95f, 0.95f)

        depthRetargetTimer -= deltaTime
        if (depthRetargetTimer <= 0f) {
            targetDepth = Random.nextFloat()
            depthRetargetTimer = 4f + Random.nextFloat() * 6f
        }
        val depthLerp = 1f - exp(-kDepthEaseRate * deltaTime)
        depth += (targetDepth - depth) * depthLerp
    }

    companion object {
        const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()
        const val kDepthEaseRate = 0.6f
    }
}
