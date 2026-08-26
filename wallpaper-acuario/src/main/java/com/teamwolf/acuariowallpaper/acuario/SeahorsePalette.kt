package com.teamwolf.acuariowallpaper.acuario

/**
 * A seahorse's body/fin/pattern colors. Not user-selectable - each [Seahorse] instance picks one
 * of [PALETTES] at random when it's created, loosely modeled on real seahorse species so the
 * tank ends up with a plausible variety instead of arbitrary colors.
 *
 * [glowColor] is the neon tint seahorse.frag adds to the body's bony-plate rings once it's dark
 * enough (see its uGlowColor/nightGlow comment) - each species gets its own hue, same convention
 * as [FishPalette]/[MantaPalette]/[TurtlePalette].
 */
class SeahorsePalette(
    val bodyColor: FloatArray,
    val finColor: FloatArray,
    val snoutColor: FloatArray,
    val patternColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Common (spiny) seahorse: mottled yellow-brown body. */
        private val COMMON_SEAHORSE = SeahorsePalette(
            bodyColor = floatArrayOf(0.55f, 0.40f, 0.18f),
            finColor = floatArrayOf(0.62f, 0.48f, 0.24f),
            snoutColor = floatArrayOf(0.48f, 0.34f, 0.15f),
            patternColor = floatArrayOf(0.35f, 0.24f, 0.10f),
            glowColor = floatArrayOf(1.0f, 0.75f, 0.15f) // Neon amber
        )

        /** Pacific seahorse: deep olive-green body. */
        private val PACIFIC_SEAHORSE = SeahorsePalette(
            bodyColor = floatArrayOf(0.20f, 0.32f, 0.14f),
            finColor = floatArrayOf(0.26f, 0.40f, 0.18f),
            snoutColor = floatArrayOf(0.16f, 0.26f, 0.11f),
            patternColor = floatArrayOf(0.10f, 0.18f, 0.08f),
            glowColor = floatArrayOf(0.30f, 1.0f, 0.20f) // Neon lime
        )

        /** Thorny seahorse: vivid orange-red body with spiny bumps. */
        private val THORNY_SEAHORSE = SeahorsePalette(
            bodyColor = floatArrayOf(0.75f, 0.24f, 0.08f),
            finColor = floatArrayOf(0.85f, 0.32f, 0.10f),
            snoutColor = floatArrayOf(0.65f, 0.20f, 0.06f),
            patternColor = floatArrayOf(0.45f, 0.12f, 0.04f),
            glowColor = floatArrayOf(1.0f, 0.25f, 0.10f) // Neon orange-red
        )

        /** Dwarf seahorse: pale cream body with fine speckling. */
        private val DWARF_SEAHORSE = SeahorsePalette(
            bodyColor = floatArrayOf(0.85f, 0.80f, 0.65f),
            finColor = floatArrayOf(0.90f, 0.86f, 0.72f),
            snoutColor = floatArrayOf(0.78f, 0.72f, 0.56f),
            patternColor = floatArrayOf(0.55f, 0.48f, 0.35f),
            glowColor = floatArrayOf(1.0f, 0.95f, 0.60f) // Neon pale gold
        )

        /** Longsnout seahorse: purple-violet body. */
        private val LONGSNOUT_SEAHORSE = SeahorsePalette(
            bodyColor = floatArrayOf(0.35f, 0.15f, 0.42f),
            finColor = floatArrayOf(0.44f, 0.20f, 0.52f),
            snoutColor = floatArrayOf(0.28f, 0.12f, 0.34f),
            patternColor = floatArrayOf(0.18f, 0.06f, 0.24f),
            glowColor = floatArrayOf(0.75f, 0.20f, 1.0f) // Neon violet
        )

        /** Tiger tail seahorse: black body with bold yellow-orange banding. */
        private val TIGER_TAIL_SEAHORSE = SeahorsePalette(
            bodyColor = floatArrayOf(0.08f, 0.08f, 0.09f),
            finColor = floatArrayOf(0.14f, 0.12f, 0.10f),
            snoutColor = floatArrayOf(0.06f, 0.06f, 0.07f),
            patternColor = floatArrayOf(0.95f, 0.65f, 0.10f),
            glowColor = floatArrayOf(1.0f, 0.60f, 0.05f) // Neon tiger-orange
        )

        /** Sunset seahorse: warm coral-pink body. */
        private val SUNSET_SEAHORSE = SeahorsePalette(
            bodyColor = floatArrayOf(0.90f, 0.42f, 0.38f),
            finColor = floatArrayOf(0.95f, 0.55f, 0.48f),
            snoutColor = floatArrayOf(0.80f, 0.35f, 0.32f),
            patternColor = floatArrayOf(0.60f, 0.22f, 0.20f),
            glowColor = floatArrayOf(1.0f, 0.35f, 0.45f) // Neon coral-pink
        )

        /** Deep blue seahorse: rich cobalt body. */
        private val DEEP_BLUE_SEAHORSE = SeahorsePalette(
            bodyColor = floatArrayOf(0.08f, 0.18f, 0.48f),
            finColor = floatArrayOf(0.12f, 0.24f, 0.58f),
            snoutColor = floatArrayOf(0.06f, 0.14f, 0.40f),
            patternColor = floatArrayOf(0.04f, 0.10f, 0.28f),
            glowColor = floatArrayOf(0.15f, 0.55f, 1.0f) // Neon electric blue
        )

        /** Shiny Gold: majestic gold body with bright golden accents. */
        val SHINY_GOLD = SeahorsePalette(
            bodyColor = floatArrayOf(0.85f, 0.65f, 0.05f),
            finColor = floatArrayOf(0.95f, 0.75f, 0.10f),
            snoutColor = floatArrayOf(0.80f, 0.60f, 0.04f),
            patternColor = floatArrayOf(1.0f, 0.85f, 0.20f),
            glowColor = floatArrayOf(1.0f, 0.75f, 0.0f)
        )

        /** Shiny Diamond: ice blue/diamond color with shimmering diamond white markings. */
        val SHINY_DIAMOND = SeahorsePalette(
            bodyColor = floatArrayOf(0.70f, 0.90f, 0.98f),
            finColor = floatArrayOf(0.80f, 0.95f, 1.0f),
            snoutColor = floatArrayOf(0.65f, 0.88f, 0.97f),
            patternColor = floatArrayOf(0.95f, 1.0f, 1.0f),
            glowColor = floatArrayOf(0.50f, 0.90f, 1.0f)
        )

        val PALETTES: List<SeahorsePalette> = listOf(
            COMMON_SEAHORSE,
            PACIFIC_SEAHORSE,
            THORNY_SEAHORSE,
            DWARF_SEAHORSE,
            LONGSNOUT_SEAHORSE,
            TIGER_TAIL_SEAHORSE,
            SUNSET_SEAHORSE,
            DEEP_BLUE_SEAHORSE
        )
    }
}
