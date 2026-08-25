package com.teamwolf.acuariowallpaper.acuario

/**
 * A branching coral's base/branch/polyp/highlight colors. Not user-selectable - each [Coral]
 * instance picks one of [PALETTES] at random when it's created, loosely modeled on real coral
 * species so the tank floor ends up with a plausible variety instead of arbitrary colors.
 */
class CoralPalette(
    val baseColor: FloatArray,
    val branchColor: FloatArray,
    val polypColor: FloatArray,
    val highlightColor: FloatArray
) {
    companion object {
        /** Purple sea fan (gorgonian): deep violet branches, pale lavender polyp tips. */
        private val PURPLE_SEA_FAN = CoralPalette(
            baseColor = floatArrayOf(0.28f, 0.10f, 0.38f),
            branchColor = floatArrayOf(0.42f, 0.18f, 0.55f),
            polypColor = floatArrayOf(0.75f, 0.55f, 0.85f),
            highlightColor = floatArrayOf(0.88f, 0.75f, 0.95f)
        )

        /** Staghorn coral: warm orange-brown branches, pale cream polyp tips. */
        private val STAGHORN_CORAL = CoralPalette(
            baseColor = floatArrayOf(0.45f, 0.24f, 0.08f),
            branchColor = floatArrayOf(0.62f, 0.36f, 0.14f),
            polypColor = floatArrayOf(0.90f, 0.72f, 0.45f),
            highlightColor = floatArrayOf(0.98f, 0.85f, 0.60f)
        )

        /** Elkhorn coral: tan-yellow branches, pale sandy polyp tips. */
        private val ELKHORN_CORAL = CoralPalette(
            baseColor = floatArrayOf(0.42f, 0.36f, 0.14f),
            branchColor = floatArrayOf(0.60f, 0.52f, 0.22f),
            polypColor = floatArrayOf(0.85f, 0.78f, 0.55f),
            highlightColor = floatArrayOf(0.95f, 0.90f, 0.70f)
        )

        /** Soft coral (Dendronephthya): vivid pink branches, bright orange polyp tips. */
        private val SOFT_CORAL = CoralPalette(
            baseColor = floatArrayOf(0.55f, 0.12f, 0.30f),
            branchColor = floatArrayOf(0.85f, 0.25f, 0.45f),
            polypColor = floatArrayOf(0.98f, 0.55f, 0.15f),
            highlightColor = floatArrayOf(1.00f, 0.80f, 0.55f)
        )

        val PALETTES: List<CoralPalette> = listOf(
            PURPLE_SEA_FAN,
            STAGHORN_CORAL,
            ELKHORN_CORAL,
            SOFT_CORAL
        )
    }
}
