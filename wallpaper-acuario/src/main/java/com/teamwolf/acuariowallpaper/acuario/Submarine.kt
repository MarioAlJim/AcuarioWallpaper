package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.sin
import kotlin.random.Random

/**
 * Movement + animation state for a submarine that travels through the background
 * of the tank occasionally.
 */
class Submarine {
    var x = -10f
        private set
    var y = 0.25f
        private set
    // Feeds AcuarioRenderer's shared depth-of-field formula (see drawSubmarine()'s comment):
    // both the size shrink (kMinScaleAtDepth) and the tint-toward-deepColor (kMaxDepthTint) scale
    // directly with this value. It used to sit at 0.95 ("very deep, drawn at the back of the
    // tank"), but that wasn't just a mild background cue - at 0.95 the tint alone blends the hull
    // ~71% of the way to deepColor, which is nearly the same color the background gradient
    // already fades to at this submarine's own screen height, so on top of being shrunk to ~13%
    // of its full scale, the sub was rendering almost perfectly camouflaged against its own
    // backdrop - never actually visible despite spawning and crossing the screen correctly. Actual
    // back-of-tank layering doesn't need this at all - drawSubmarine() already runs before every
    // other creature/plant draw call, so it's always painted underneath them regardless of this
    // value. 0.4 keeps a modest "background, not front-and-center" size/tint cue while staying
    // clearly visible (a warm yellow hull, not a grey smudge) in every theme and at night.
    var depth = 0.4f
        private set
    var active = false
        private set

    private var timeActive = 0f
    private var timeUntilNextSpawn = 2f + Random.nextFloat() * 3f // Spawns initially after 2-5 seconds

    var propellerPhase = 0f
        private set

    fun update(deltaTime: Float, aspectRatio: Float) {
        if (!active) {
            timeUntilNextSpawn -= deltaTime
            if (timeUntilNextSpawn <= 0f) {
                active = true
                timeActive = 0f
                // Spawn off-screen to the right, facing left
                x = aspectRatio + 0.5f
                y = 0.1f + Random.nextFloat() * 0.3f
            }
        } else {
            timeActive += deltaTime
            // Horizontal speed: slow movement (takes ~35 seconds to cross the screen)
            val speed = 0.08f
            x -= speed * deltaTime

            // Gentle diving/rising wave motion
            propellerPhase += deltaTime * 25f
            y = 0.15f + sin(timeActive * 0.4f) * 0.07f

            // Check if it has fully exited the left edge of the screen
            if (x < -aspectRatio - 0.5f) {
                active = false
                // Reset spawn timer (appear again in 15-30 seconds)
                timeUntilNextSpawn = 15f + Random.nextFloat() * 15f
            }
        }
    }
}
