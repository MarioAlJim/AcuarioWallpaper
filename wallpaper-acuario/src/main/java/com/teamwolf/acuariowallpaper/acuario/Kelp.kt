package com.teamwolf.acuariowallpaper.acuario

import kotlin.random.Random

/**
 * A single anchored kelp clump growing from the tank floor.
 *
 * Unlike [Fish]/[Turtle]/[Manta], plants don't wander: [x]/[depth]/[heightScale] are fixed for
 * the clump's whole lifetime, assigned once by [AcuarioRenderer] when it lays out the vegetation
 * (see its plant-layout comment for why the whole layout is rebuilt from scratch on any density/
 * aspect-ratio change instead of incrementally adjusted like the wandering creatures are).
 * [swayPhase] is the only thing that advances over time, driving kelp.frag's blade-bending
 * animation - each blade bends more near its tip than its base, like a real frond swaying in a
 * gentle current.
 */
class Kelp {
    val palette: KelpPalette = KelpPalette.PALETTES.random()

    var x = 0f
        private set
    var depth = 0f
        private set
    var heightScale = 1f
        private set

    var swayPhase = Random.nextFloat() * TWO_PI
        private set

    private val swaySpeed = 0.35f + Random.nextFloat() * 0.25f

    /** Assigns this clump's fixed position/size - see [AcuarioRenderer]'s plant layout. */
    fun placeAt(x: Float, depth: Float, heightScale: Float) {
        this.x = x
        this.depth = depth
        this.heightScale = heightScale
    }

    fun update(deltaTime: Float) {
        swayPhase += deltaTime * swaySpeed
    }

    private companion object {
        const val TWO_PI = (kotlin.math.PI * 2.0).toFloat()
    }
}
