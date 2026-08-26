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
    var depth = 0.95f // Very deep, drawn at the back of the tank
        private set
    var active = false
        private set

    private var timeActive = 0f
    private var timeUntilNextSpawn = Random.nextFloat() * 40f + 20f // Spawns initially after 20-60s

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
                // Reset spawn timer (appear again in 90-150 seconds)
                timeUntilNextSpawn = 90f + Random.nextFloat() * 60f
            }
        }
    }
}
