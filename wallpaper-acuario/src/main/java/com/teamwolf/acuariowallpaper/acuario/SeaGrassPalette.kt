package com.teamwolf.acuariowallpaper.acuario

/**
 * A sea grass patch's blade/tip/base/highlight colors. Not user-selectable - each [SeaGrass]
 * instance picks one of [PALETTES] at random when it's created, loosely modeled on real seagrass
 * species so the tank floor ends up with a plausible variety instead of arbitrary colors.
 */
class SeaGrassPalette(
    val bladeColor: FloatArray,
    val tipColor: FloatArray,
    val baseColor: FloatArray,
    val highlightColor: FloatArray
) {
    companion object {
        /** Turtle grass: broad, bright green blades. */
        private val TURTLE_GRASS = SeaGrassPalette(
            bladeColor = floatArrayOf(0.14f, 0.48f, 0.20f),
            tipColor = floatArrayOf(0.40f, 0.72f, 0.28f),
            baseColor = floatArrayOf(0.08f, 0.32f, 0.13f),
            highlightColor = floatArrayOf(0.55f, 0.82f, 0.38f)
        )

        /** Manatee grass: thin, dark olive-green blades. */
        private val MANATEE_GRASS = SeaGrassPalette(
            bladeColor = floatArrayOf(0.16f, 0.36f, 0.14f),
            tipColor = floatArrayOf(0.32f, 0.52f, 0.20f),
            baseColor = floatArrayOf(0.10f, 0.24f, 0.09f),
            highlightColor = floatArrayOf(0.45f, 0.62f, 0.28f)
        )

        /** Shoal grass: pale, almost yellow-green blades. */
        private val SHOAL_GRASS = SeaGrassPalette(
            bladeColor = floatArrayOf(0.42f, 0.60f, 0.20f),
            tipColor = floatArrayOf(0.62f, 0.80f, 0.32f),
            baseColor = floatArrayOf(0.28f, 0.42f, 0.12f),
            highlightColor = floatArrayOf(0.75f, 0.88f, 0.45f)
        )

        /** Widgeon grass: brownish-green blades with a warmer undertone. */
        private val WIDGEON_GRASS = SeaGrassPalette(
            bladeColor = floatArrayOf(0.30f, 0.36f, 0.14f),
            tipColor = floatArrayOf(0.48f, 0.52f, 0.20f),
            baseColor = floatArrayOf(0.20f, 0.24f, 0.08f),
            highlightColor = floatArrayOf(0.58f, 0.62f, 0.30f)
        )

        val PALETTES: List<SeaGrassPalette> = listOf(
            TURTLE_GRASS,
            MANATEE_GRASS,
            SHOAL_GRASS,
            WIDGEON_GRASS
        )
    }
}
