package com.teamwolf.acuariowallpaper.acuario

import kotlin.random.Random

/**
 * A floor rock / substrate boulder resting on the aquarium bed.
 *
 * Placed in a continuous row behind/around flora along the screen width to form a natural
 * stone seabed where bottom-dwelling creatures (like Axolotls) swim and crawl.
 */
class Rock {
    val palette: RockPalette = RockPalette.PALETTES.random()

    var x = 0f
        private set

    var depth = 0f
        private set

    var scaleX = 0.4f
        private set

    var scaleY = 0.25f
        private set

    var shapeType = 0
        private set

    var rotationDegrees = 0f
        private set

    fun placeAt(x: Float, depth: Float, scaleX: Float, scaleY: Float, shapeType: Int, rotationDegrees: Float = 0f) {
        this.x = x
        this.depth = depth
        this.scaleX = scaleX
        this.scaleY = scaleY
        this.shapeType = shapeType
        this.rotationDegrees = rotationDegrees
    }
}
