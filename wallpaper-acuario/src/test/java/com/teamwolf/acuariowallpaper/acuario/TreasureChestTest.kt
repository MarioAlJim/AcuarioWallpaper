package com.teamwolf.acuariowallpaper.acuario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasureChestTest {

    @Test
    fun treasureChest_initialStateIsClosed() {
        val chest = TreasureChest()
        assertEquals(0f, chest.lidOpen, 0.001f)
        assertEquals(0f, chest.glowIntensity, 0.001f)
    }

    @Test
    fun triggerOpen_opensLidAndEmitsBubbles() {
        val chest = TreasureChest()
        chest.triggerOpen()

        assertTrue(chest.shouldSpawnBubbles)

        // Update several frames
        repeat(10) {
            chest.update(1f / 60f, 1.7f)
        }

        assertTrue("Expected lid to open, got ${chest.lidOpen}", chest.lidOpen > 0.3f)
        assertTrue("Expected glow to increase, got ${chest.glowIntensity}", chest.glowIntensity > 0.3f)
    }

    @Test
    fun touch_triggersChestOpen() {
        val chest = TreasureChest()
        chest.touch()

        assertTrue(chest.shouldSpawnBubbles)
        chest.update(1f / 60f, 1.7f)
        assertTrue(chest.lidOpen > 0f)
    }
}
