package com.teamwolf.acuariowallpaper.acuario

/**
 * A manta ray's body/wing-edge/tail/marking colors. Not user-selectable - each [Manta] instance
 * picks one of [PALETTES] at random when it's created, loosely modeled on real ray species so
 * the tank ends up with a plausible variety instead of arbitrary colors.
 *
 * [glowColor] is the neon tint manta.frag adds to the shoulder markings once it's dark enough
 * (see its uGlowColor/nightGlow comment) - each species gets its own hue instead of every ray
 * glowing the same fixed color, spreading the tank's bioluminescence across a wide range of
 * colors rather than one repeated note.
 */
class MantaPalette(
    val bodyColor: FloatArray,
    val wingColor: FloatArray,
    val tailColor: FloatArray,
    val markingColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Reef manta ray: near-black body, pale shoulder chevron patches. */
        private val REEF_MANTA = MantaPalette(
            bodyColor = floatArrayOf(0.05f, 0.06f, 0.08f),
            wingColor = floatArrayOf(0.08f, 0.09f, 0.11f),
            tailColor = floatArrayOf(0.05f, 0.06f, 0.08f),
            markingColor = floatArrayOf(0.85f, 0.86f, 0.88f),
            glowColor = floatArrayOf(0.65f, 0.25f, 1.0f) // Neon violet
        )

        /** Oceanic manta ray: dark slate-blue body, smaller/dimmer shoulder patches. */
        private val OCEANIC_MANTA = MantaPalette(
            bodyColor = floatArrayOf(0.07f, 0.11f, 0.18f),
            wingColor = floatArrayOf(0.10f, 0.15f, 0.22f),
            tailColor = floatArrayOf(0.06f, 0.09f, 0.15f),
            markingColor = floatArrayOf(0.55f, 0.60f, 0.68f),
            glowColor = floatArrayOf(0.10f, 0.35f, 1.0f) // Neon deep blue
        )

        /** Spotted eagle ray: dark blue-grey body scattered with pale rings/spots. */
        private val SPOTTED_EAGLE_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.08f, 0.16f, 0.22f),
            wingColor = floatArrayOf(0.10f, 0.19f, 0.25f),
            tailColor = floatArrayOf(0.06f, 0.12f, 0.17f),
            markingColor = floatArrayOf(0.80f, 0.88f, 0.90f),
            glowColor = floatArrayOf(0.0f, 0.95f, 0.90f) // Neon cyan
        )

        /** Cownose ray: warm golden-brown body, slightly darker wingtips. */
        private val COWNOSE_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.45f, 0.32f, 0.16f),
            wingColor = floatArrayOf(0.36f, 0.25f, 0.12f),
            tailColor = floatArrayOf(0.32f, 0.22f, 0.10f),
            markingColor = floatArrayOf(0.58f, 0.44f, 0.24f),
            glowColor = floatArrayOf(1.0f, 0.65f, 0.10f) // Neon amber
        )

        /** Devil ray (Mobula): deep blue-black body, crisp pale belly-edge trim. */
        private val DEVIL_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.04f, 0.05f, 0.14f),
            wingColor = floatArrayOf(0.06f, 0.07f, 0.18f),
            tailColor = floatArrayOf(0.04f, 0.05f, 0.14f),
            markingColor = floatArrayOf(0.75f, 0.78f, 0.90f),
            glowColor = floatArrayOf(0.95f, 0.10f, 0.75f) // Neon magenta
        )

        /** Giant (pelagic) manta ray: warm charcoal body, broad soft-grey shoulder patches. */
        private val GIANT_MANTA = MantaPalette(
            bodyColor = floatArrayOf(0.10f, 0.10f, 0.11f),
            wingColor = floatArrayOf(0.14f, 0.14f, 0.15f),
            tailColor = floatArrayOf(0.09f, 0.09f, 0.10f),
            markingColor = floatArrayOf(0.65f, 0.64f, 0.62f),
            glowColor = floatArrayOf(0.55f, 0.80f, 1.0f) // Neon ice-blue
        )

        /** Blue-spotted ribbontail ray: warm tan body scattered with vivid electric-blue spots. */
        private val BLUE_SPOTTED_RIBBONTAIL = MantaPalette(
            bodyColor = floatArrayOf(0.60f, 0.42f, 0.20f),
            wingColor = floatArrayOf(0.52f, 0.36f, 0.16f),
            tailColor = floatArrayOf(0.48f, 0.32f, 0.14f),
            markingColor = floatArrayOf(0.10f, 0.35f, 0.90f),
            glowColor = floatArrayOf(0.10f, 0.55f, 1.0f) // Neon electric blue
        )

        /** Southern stingray: smooth dark olive-brown body, pale undertone edges. */
        private val SOUTHERN_STINGRAY = MantaPalette(
            bodyColor = floatArrayOf(0.20f, 0.20f, 0.14f),
            wingColor = floatArrayOf(0.26f, 0.25f, 0.17f),
            tailColor = floatArrayOf(0.16f, 0.16f, 0.11f),
            markingColor = floatArrayOf(0.55f, 0.52f, 0.40f),
            glowColor = floatArrayOf(0.60f, 1.0f, 0.20f) // Neon lime
        )

        /** Bat ray: dark brown body, coppery-tan wing undertones. */
        private val BAT_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.24f, 0.15f, 0.09f),
            wingColor = floatArrayOf(0.34f, 0.22f, 0.12f),
            tailColor = floatArrayOf(0.18f, 0.11f, 0.06f),
            markingColor = floatArrayOf(0.48f, 0.34f, 0.18f),
            glowColor = floatArrayOf(1.0f, 0.40f, 0.15f) // Neon coral-orange
        )

        /** Butterfly ray: pale sandy-grey body with a fine dark mottled diamond pattern. */
        private val BUTTERFLY_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.55f, 0.52f, 0.46f),
            wingColor = floatArrayOf(0.48f, 0.45f, 0.40f),
            tailColor = floatArrayOf(0.40f, 0.38f, 0.33f),
            markingColor = floatArrayOf(0.22f, 0.20f, 0.16f),
            glowColor = floatArrayOf(0.05f, 0.85f, 0.55f) // Neon teal-green
        )

        /** Albino manta ray: pale near-white body, faint ghostly markings. */
        private val ALBINO_MANTA = MantaPalette(
            bodyColor = floatArrayOf(0.85f, 0.85f, 0.88f),
            wingColor = floatArrayOf(0.80f, 0.80f, 0.85f),
            tailColor = floatArrayOf(0.75f, 0.75f, 0.80f),
            markingColor = floatArrayOf(0.95f, 0.95f, 0.98f),
            glowColor = floatArrayOf(0.70f, 0.90f, 1.0f) // Neon pale ice-cyan
        )

        /** Cobalt Ray: rich saturated cobalt-blue body, pale sky-blue markings. */
        private val COBALT_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.05f, 0.15f, 0.45f),
            wingColor = floatArrayOf(0.08f, 0.20f, 0.55f),
            tailColor = floatArrayOf(0.04f, 0.12f, 0.38f),
            markingColor = floatArrayOf(0.60f, 0.75f, 1.0f),
            glowColor = floatArrayOf(0.20f, 0.60f, 1.0f) // Neon electric blue
        )

        /** Sunburst Ray: warm golden-yellow body, pale gold shoulder patches. */
        private val SUNBURST_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.65f, 0.50f, 0.10f),
            wingColor = floatArrayOf(0.55f, 0.42f, 0.08f),
            tailColor = floatArrayOf(0.45f, 0.34f, 0.06f),
            markingColor = floatArrayOf(0.90f, 0.80f, 0.30f),
            glowColor = floatArrayOf(1.0f, 0.85f, 0.10f) // Neon gold
        )

        /** Crimson Ray: deep red-maroon body, dusty rose markings. */
        private val CRIMSON_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.35f, 0.08f, 0.08f),
            wingColor = floatArrayOf(0.28f, 0.06f, 0.06f),
            tailColor = floatArrayOf(0.22f, 0.05f, 0.05f),
            markingColor = floatArrayOf(0.60f, 0.15f, 0.12f),
            glowColor = floatArrayOf(1.0f, 0.10f, 0.15f) // Neon crimson
        )

        /** Shiny Gold: majestic gold body with bright golden accents. */
        val SHINY_GOLD = MantaPalette(
            bodyColor = floatArrayOf(0.85f, 0.65f, 0.05f),
            wingColor = floatArrayOf(0.95f, 0.75f, 0.10f),
            tailColor = floatArrayOf(0.85f, 0.65f, 0.05f),
            markingColor = floatArrayOf(1.0f, 0.85f, 0.20f),
            glowColor = floatArrayOf(1.0f, 0.75f, 0.0f)
        )

        /** Shiny Diamond: ice blue/diamond color with shimmering diamond white markings. */
        val SHINY_DIAMOND = MantaPalette(
            bodyColor = floatArrayOf(0.70f, 0.90f, 0.98f),
            wingColor = floatArrayOf(0.80f, 0.95f, 1.0f),
            tailColor = floatArrayOf(0.70f, 0.90f, 0.98f),
            markingColor = floatArrayOf(0.95f, 1.0f, 1.0f),
            glowColor = floatArrayOf(0.50f, 0.90f, 1.0f)
        )

        val PALETTES: List<MantaPalette> = listOf(
            REEF_MANTA,
            OCEANIC_MANTA,
            SPOTTED_EAGLE_RAY,
            COWNOSE_RAY,
            DEVIL_RAY,
            GIANT_MANTA,
            BLUE_SPOTTED_RIBBONTAIL,
            SOUTHERN_STINGRAY,
            BAT_RAY,
            BUTTERFLY_RAY,
            ALBINO_MANTA,
            COBALT_RAY,
            SUNBURST_RAY,
            CRIMSON_RAY
        )
    }
}
