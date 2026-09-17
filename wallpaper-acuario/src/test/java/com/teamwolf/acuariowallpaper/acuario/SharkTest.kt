package com.teamwolf.acuariowallpaper.acuario
 
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharkTest {

    private fun advanceTime(shark: Shark, seconds: Float) {
        val dt = 1f / 60f
        val steps = (seconds / dt).toInt()
        repeat(steps) {
            shark.update(dt, aspectRatio = 1.0f)
        }
    }

    @Test
    fun touch_triggersBiteAnimationWithExtendedHold() {
        val shark = Shark()
        shark.active = true

        assertEquals(0f, shark.biteProgress, 0.001f)

        // Trigger touch interaction
        shark.touch()

        // After 0.4 seconds, mouth should be fully snapped open
        advanceTime(shark, 0.4f)
        assertTrue("biteProgress should be open", shark.biteProgress > 0.95f)

        // After 1.5 seconds total, it should still be holding teeth open
        advanceTime(shark, 1.1f)
        assertEquals("biteProgress should remain held open during display", 1.0f, shark.biteProgress, 0.001f)

        // After 2.0 seconds total, teeth should still be prominently displayed
        advanceTime(shark, 0.5f)
        assertEquals("biteProgress should still be open at 2.0s", 1.0f, shark.biteProgress, 0.001f)

        // After 3.2 seconds total, animation should complete and mouth closes
        advanceTime(shark, 1.2f)
        assertEquals("biteProgress should be back to 0", 0f, shark.biteProgress, 0.001f)
    }

    @Test
    fun touch_resetsWhileBiting() {
        val shark = Shark()
        shark.active = true

        shark.touch()
        advanceTime(shark, 2.5f) // nearing the end of bite
        assertTrue("biteProgress closing", shark.biteProgress < 1.0f)

        // Tap again while biting to restart display
        shark.touch()
        advanceTime(shark, 0.4f)
        assertEquals("re-touch should restart and hold open", 1.0f, shark.biteProgress, 0.001f)
    }
}
