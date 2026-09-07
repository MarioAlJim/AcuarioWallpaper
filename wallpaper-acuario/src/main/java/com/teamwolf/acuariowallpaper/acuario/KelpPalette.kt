package com.teamwolf.acuariowallpaper.acuario

/**
 * A kelp clump's blade/tip/base/highlight colors. Not user-selectable - each [Kelp] instance
 * picks one of [PALETTES] at random when it's created, loosely modeled on real marine plants so
 * the tank floor ends up with a plausible variety instead of arbitrary colors.
 *
 * [glowColor] is the bioluminescent tint kelp.frag pulses at the blade tips once it's dark
 * enough - see CoralPalette.glowColor's identical comment for why every kelp (and every other
 * piece of flora) shares the exact same time-driven flash-then-rest pulse.
 */
class KelpPalette(
    val bladeColor: FloatArray,
    val tipColor: FloatArray,
    val baseColor: FloatArray,
    val highlightColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Giant kelp: rich green blades, yellow-green tips catching the light. */
        private val GIANT_KELP = KelpPalette(
            bladeColor = floatArrayOf(0.08f, 0.42f, 0.18f),
            tipColor = floatArrayOf(0.35f, 0.65f, 0.15f),
            baseColor = floatArrayOf(0.05f, 0.28f, 0.12f),
            highlightColor = floatArrayOf(0.45f, 0.75f, 0.25f),
            glowColor = floatArrayOf(0.30f, 1.0f, 0.35f) // Neon green
        )

        /** Sargassum: warm olive-brown blades with golden tips. */
        private val SARGASSUM = KelpPalette(
            bladeColor = floatArrayOf(0.32f, 0.28f, 0.08f),
            tipColor = floatArrayOf(0.55f, 0.45f, 0.10f),
            baseColor = floatArrayOf(0.22f, 0.18f, 0.05f),
            highlightColor = floatArrayOf(0.65f, 0.55f, 0.15f),
            glowColor = floatArrayOf(1.0f, 0.75f, 0.10f) // Neon amber
        )

        /** Red algae: deep maroon blades, pink-red tips. */
        private val RED_ALGAE = KelpPalette(
            bladeColor = floatArrayOf(0.42f, 0.10f, 0.14f),
            tipColor = floatArrayOf(0.65f, 0.22f, 0.24f),
            baseColor = floatArrayOf(0.28f, 0.06f, 0.09f),
            highlightColor = floatArrayOf(0.80f, 0.35f, 0.32f),
            glowColor = floatArrayOf(1.0f, 0.15f, 0.30f) // Neon red-pink
        )

        /** Purple sea fern: vivid violet blades, pale lavender tips. */
        private val PURPLE_SEA_FERN = KelpPalette(
            bladeColor = floatArrayOf(0.32f, 0.12f, 0.45f),
            tipColor = floatArrayOf(0.60f, 0.40f, 0.75f),
            baseColor = floatArrayOf(0.20f, 0.07f, 0.30f),
            highlightColor = floatArrayOf(0.72f, 0.55f, 0.85f),
            glowColor = floatArrayOf(0.65f, 0.20f, 1.0f) // Neon violet
        )

        /** Eelgrass: bright grassy-green blades, pale yellow-green tips. */
        private val EELGRASS = KelpPalette(
            bladeColor = floatArrayOf(0.18f, 0.50f, 0.22f),
            tipColor = floatArrayOf(0.55f, 0.78f, 0.30f),
            baseColor = floatArrayOf(0.10f, 0.34f, 0.14f),
            highlightColor = floatArrayOf(0.65f, 0.85f, 0.40f),
            glowColor = floatArrayOf(0.55f, 1.0f, 0.15f) // Neon chartreuse
        )

        val PALETTES: List<KelpPalette> = listOf(
            GIANT_KELP,
            SARGASSUM,
            RED_ALGAE,
            PURPLE_SEA_FERN,
            EELGRASS
        )
    }
}
