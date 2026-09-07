package com.teamwolf.acuariowallpaper.acuario

/**
 * A sea anemone's foot/tentacle/tentacle-tip/highlight colors. Not user-selectable - each
 * [Anemone] instance picks one of [PALETTES] at random when it's created, loosely modeled on
 * real species so the tank floor ends up with a plausible variety instead of arbitrary colors.
 *
 * [glowColor] is the bioluminescent tint anemone.frag pulses across the tentacles once it's dark
 * enough - see CoralPalette.glowColor's identical comment for why every anemone (and coral)
 * shares the exact same time-driven pulse instead of each blinking on its own rhythm.
 */
class AnemonePalette(
    val footColor: FloatArray,
    val tentacleColor: FloatArray,
    val tipColor: FloatArray,
    val highlightColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Magnificent sea anemone: deep pink foot, pale rosy tentacles with pink tips. */
        private val MAGNIFICENT_ANEMONE = AnemonePalette(
            footColor = floatArrayOf(0.55f, 0.10f, 0.30f),
            tentacleColor = floatArrayOf(0.85f, 0.55f, 0.65f),
            tipColor = floatArrayOf(0.95f, 0.70f, 0.75f),
            highlightColor = floatArrayOf(1.00f, 0.85f, 0.85f),
            glowColor = floatArrayOf(1.0f, 0.35f, 0.60f) // Neon rose
        )

        /** Bubble-tip (clownfish host) anemone: green body, warm pink-tipped tentacles. */
        private val BUBBLE_TIP_ANEMONE = AnemonePalette(
            footColor = floatArrayOf(0.10f, 0.35f, 0.20f),
            tentacleColor = floatArrayOf(0.30f, 0.60f, 0.35f),
            tipColor = floatArrayOf(0.90f, 0.45f, 0.50f),
            highlightColor = floatArrayOf(0.55f, 0.85f, 0.55f),
            glowColor = floatArrayOf(0.30f, 1.0f, 0.45f) // Neon green
        )

        /** Purple-base anemone: violet foot, pale lilac tentacles. */
        private val PURPLE_BASE_ANEMONE = AnemonePalette(
            footColor = floatArrayOf(0.35f, 0.12f, 0.48f),
            tentacleColor = floatArrayOf(0.70f, 0.55f, 0.85f),
            tipColor = floatArrayOf(0.88f, 0.78f, 0.95f),
            highlightColor = floatArrayOf(0.95f, 0.90f, 1.00f),
            glowColor = floatArrayOf(0.70f, 0.30f, 1.0f) // Neon violet
        )

        /** Orange carpet anemone: warm orange foot and tentacles, pale cream tips. */
        private val ORANGE_CARPET_ANEMONE = AnemonePalette(
            footColor = floatArrayOf(0.75f, 0.32f, 0.06f),
            tentacleColor = floatArrayOf(0.90f, 0.55f, 0.15f),
            tipColor = floatArrayOf(0.98f, 0.85f, 0.55f),
            highlightColor = floatArrayOf(1.00f, 0.92f, 0.70f),
            glowColor = floatArrayOf(1.0f, 0.60f, 0.10f) // Neon amber
        )

        val PALETTES: List<AnemonePalette> = listOf(
            MAGNIFICENT_ANEMONE,
            BUBBLE_TIP_ANEMONE,
            PURPLE_BASE_ANEMONE,
            ORANGE_CARPET_ANEMONE
        )
    }
}
