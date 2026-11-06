package com.teamwolf.acuariowallpaper.acuario

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * State & animation controller for a Sunken Treasure Chest resting on the seabed floor.
 *
 * Automatically opens periodically releasing a dense column of bubbles and golden light,
 * or responds instantly when touched by the user.
 */
class TreasureChest {
    var x = 0.55f // Relative to aspectRatio (* aspectRatio at draw time)
        private set
    var y = -0.76f // Seabed floor anchor
        private set
    var depth = 0.48f // Seabed shelf depth, respecting plant layer tiers (Escalón 1 - Medio)
        private set

    var palette: TreasureChestPalette = TreasureChestPalette.CLASSIC_PIRATE

    /** 0.0 = fully closed lid, 1.0 = fully open lid. Smoothly interpolated. */
    var lidOpen = 0f
        private set

    /** Glowing gold light pulse intensity inside the chest. */
    var glowIntensity = 0f
        private set

    /** Special beacon glow shown when ready to restore depleted fauna. */
    var beaconGlow = 0f

    /** Set to true when bubbles should be emitted this frame by AcuarioRenderer. */
    var shouldSpawnBubbles = false

    private var isOpen = false
    private var openTimer = 0f
    private var autoOpenTimer = 10f + Random.nextFloat() * 10f // First open after 10-20 sec
    private var bubbleSpawnTimer = 0f

    fun update(deltaTime: Float, aspectRatio: Float) {
        x = 0.55f

        if (!isOpen) {
            // Counting down to next auto-open
            autoOpenTimer -= deltaTime
            if (autoOpenTimer <= 0f) {
                triggerOpen()
            }

            // Smoothly close lid
            lidOpen = max(0f, lidOpen - deltaTime * 3.0f)
            glowIntensity = max(0f, glowIntensity - deltaTime * 2.5f)
        } else {
            // Chest is open
            openTimer -= deltaTime
            if (openTimer <= 0f) {
                isOpen = false
                autoOpenTimer = 14f + Random.nextFloat() * 16f
            }

            // Smoothly open lid
            lidOpen = min(1.0f, lidOpen + deltaTime * 4.0f)
            glowIntensity = min(1.0f, glowIntensity + deltaTime * 3.5f)

            // Emit bubbles while lid is open
            if (lidOpen > 0.3f) {
                bubbleSpawnTimer -= deltaTime
                if (bubbleSpawnTimer <= 0f) {
                    shouldSpawnBubbles = true
                    bubbleSpawnTimer = 0.12f // Continuous stream of bubbles every 120ms
                }
            }
        }
    }

    /** Triggers the chest to open and release treasure bubbles & light. */
    fun triggerOpen() {
        isOpen = true
        openTimer = 4.0f // Stay open for 4 seconds
        shouldSpawnBubbles = true
        bubbleSpawnTimer = 0.0f
    }

    /**
     * Responds to user touch. Returns true if touch was registered on the chest.
     */
    fun touch() {
        triggerOpen()
    }
}
