package com.teamwolf.acuariowallpaper.acuario

import kotlin.random.Random

/**
 * A single anchored branching coral (fan/staghorn-style) growing from the tank floor.
 *
 * Same fixed-position shape as [Kelp]/[Anemone]/[SeaGrass] (see [Kelp]'s class doc for why
 * plants are laid out differently from the wandering creatures), but barely animated: real hard/
 * soft corals are far stiffer than a waving anemone or a swaying blade of grass, so
 * coral.frag's branch bend amplitude is deliberately tiny - just enough of a "living" wobble to
 * not read as a completely static prop.
 */
class Coral {
    val palette: CoralPalette = CoralPalette.PALETTES.random()

    var x = 0f
        private set
    var depth = 0f
        private set
    var scale = 1f
        private set

    var swayPhase = Random.nextFloat() * TWO_PI
        private set

    private val swaySpeed = 0.25f + Random.nextFloat() * 0.15f

    /** Assigns this coral's fixed position/size - see [AcuarioRenderer]'s plant layout. */
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
