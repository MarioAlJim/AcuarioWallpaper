package com.teamwolf.acuariowallpaper.acuario

import kotlin.random.Random

/**
 * A single anchored sea anemone growing from the tank floor.
 *
 * Same fixed-position/animated-sway shape as [Kelp] (see its class doc for why plants are laid
 * out differently from the wandering creatures) - only [swayPhase] advances over time, driving
 * anemone.frag's per-tentacle waving animation, where each of its several tentacles curves with
 * its own phase offset for a "living coral" look instead of moving in lockstep.
 */
class Anemone {
    val palette: AnemonePalette = AnemonePalette.PALETTES.random()

    var x = 0f
        private set
    var depth = 0f
        private set
    var scale = 1f
        private set

    var swayPhase = Random.nextFloat() * TWO_PI
        private set

    private val swaySpeed = 0.6f + Random.nextFloat() * 0.35f

    /** Assigns this anemone's fixed position/size - see [AcuarioRenderer]'s plant layout. */
    fun placeAt(x: Float, depth: Float, scale: Float) {
        this.x = x
        this.depth = depth
        this.scale = scale
    }

    fun update(deltaTime: Float) {
        swayPhase += deltaTime * swaySpeed
    }

    private companion object {
        const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()
    }
}
