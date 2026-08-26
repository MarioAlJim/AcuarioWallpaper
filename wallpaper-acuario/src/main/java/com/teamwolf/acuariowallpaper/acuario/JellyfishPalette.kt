package com.teamwolf.acuariowallpaper.acuario

/**
 * A jellyfish's bell/margin/tentacle colors. Not user-selectable - each [Jellyfish] instance
 * picks one of [PALETTES] at random when it's created, loosely modeled on real jellyfish species
 * so the tank ends up with a plausible variety instead of arbitrary colors.
 *
 * [glowColor] is the bioluminescent tint jellyfish.frag ramps up along the bell margin and
 * tentacles once it's dark enough (see its uGlowColor/nightGlow comment) - each species gets its
 * own hue, same convention as [FishPalette]/[MantaPalette]/[SeahorsePalette].
 */
class JellyfishPalette(
    val bellColor: FloatArray,
    val marginColor: FloatArray,
    val tentacleColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Moon jelly: pale translucent white-blue bell with a faint four-leaf marking. */
        private val MOON_JELLY = JellyfishPalette(
            bellColor = floatArrayOf(0.75f, 0.82f, 0.88f),
            marginColor = floatArrayOf(0.85f, 0.90f, 0.95f),
            tentacleColor = floatArrayOf(0.70f, 0.78f, 0.85f),
            glowColor = floatArrayOf(0.55f, 0.85f, 1.0f) // Neon ice-blue
        )

        /** Blue blubber jelly: soft milky-blue bell. */
        private val BLUE_BLUBBER = JellyfishPalette(
            bellColor = floatArrayOf(0.25f, 0.45f, 0.70f),
            marginColor = floatArrayOf(0.35f, 0.55f, 0.80f),
            tentacleColor = floatArrayOf(0.20f, 0.38f, 0.62f),
            glowColor = floatArrayOf(0.20f, 0.60f, 1.0f) // Neon electric blue
        )

        /** Sea nettle: warm golden-brown bell with long trailing oral arms. */
        private val SEA_NETTLE = JellyfishPalette(
            bellColor = floatArrayOf(0.62f, 0.42f, 0.16f),
            marginColor = floatArrayOf(0.75f, 0.52f, 0.20f),
            tentacleColor = floatArrayOf(0.55f, 0.30f, 0.10f),
            glowColor = floatArrayOf(1.0f, 0.65f, 0.10f) // Neon amber
        )

        /** Crystal jelly: nearly clear bell with a faint green-blue bioluminescent ring. */
        private val CRYSTAL_JELLY = JellyfishPalette(
            bellColor = floatArrayOf(0.82f, 0.90f, 0.88f),
            marginColor = floatArrayOf(0.88f, 0.95f, 0.92f),
            tentacleColor = floatArrayOf(0.75f, 0.85f, 0.82f),
            glowColor = floatArrayOf(0.30f, 1.0f, 0.65f) // Neon aqua-green
        )

        /** Flower hat jelly: translucent bell with vivid pink-tipped tentacle bands. */
        private val FLOWER_HAT_JELLY = JellyfishPalette(
            bellColor = floatArrayOf(0.55f, 0.50f, 0.45f),
            marginColor = floatArrayOf(0.70f, 0.62f, 0.55f),
            tentacleColor = floatArrayOf(0.90f, 0.20f, 0.55f),
            glowColor = floatArrayOf(1.0f, 0.20f, 0.65f) // Neon pink
        )

        /** Purple-striped jelly: pale bell with deep purple radial stripes. */
        private val PURPLE_STRIPED_JELLY = JellyfishPalette(
            bellColor = floatArrayOf(0.68f, 0.62f, 0.72f),
            marginColor = floatArrayOf(0.78f, 0.72f, 0.82f),
            tentacleColor = floatArrayOf(0.40f, 0.15f, 0.50f),
            glowColor = floatArrayOf(0.70f, 0.25f, 1.0f) // Neon violet
        )

        /** Shiny Gold: majestic gold bell with bright golden tentacles. */
        val SHINY_GOLD = JellyfishPalette(
            bellColor = floatArrayOf(0.85f, 0.65f, 0.05f),
            marginColor = floatArrayOf(0.95f, 0.75f, 0.10f),
            tentacleColor = floatArrayOf(0.80f, 0.60f, 0.04f),
            glowColor = floatArrayOf(1.0f, 0.75f, 0.0f)
        )

        /** Shiny Diamond: ice blue/diamond bell with shimmering white tentacles. */
        val SHINY_DIAMOND = JellyfishPalette(
            bellColor = floatArrayOf(0.75f, 0.92f, 0.98f),
            marginColor = floatArrayOf(0.85f, 0.96f, 1.0f),
            tentacleColor = floatArrayOf(0.70f, 0.90f, 0.97f),
            glowColor = floatArrayOf(0.50f, 0.90f, 1.0f)
        )

        val PALETTES: List<JellyfishPalette> = listOf(
            MOON_JELLY,
            BLUE_BLUBBER,
            SEA_NETTLE,
            CRYSTAL_JELLY,
            FLOWER_HAT_JELLY,
            PURPLE_STRIPED_JELLY
        )
    }
}
