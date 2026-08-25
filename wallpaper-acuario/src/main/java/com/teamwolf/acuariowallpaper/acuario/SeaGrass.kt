package com.teamwolf.acuariowallpaper.acuario

import kotlin.random.Random

/**
 * A single anchored patch of sea grass growing from the tank floor.
 *
 * Same fixed-position/animated-sway shape as [Kelp] (see its class doc for why plants are laid
 * out differently from the wandering creatures), but shorter and swaying faster - sea grass
 * blades are short and limber rather than tall and languid like kelp, so they respond more
 * quickly and skittishly to the current in seagrass.frag's animation.
 */
class SeaGrass {
    val palette: SeaGrassPalette = SeaGrassPalette.PALETTES.random()

    var x = 0f
        private set
    var depth = 0f
        private set
    var heightScale = 1f
        private set

    var swayPhase = Random.nextFloat() * TWO_PI
        private set

    private val swaySpeed = 0.9f + Random.nextFloat() * 0.5f

    /** Assigns this patch's fixed position/size - see [AcuarioRenderer]'s plant layout. */
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
