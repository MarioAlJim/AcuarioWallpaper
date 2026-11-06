package com.teamwolf.acuariowallpaper.acuario

/**
 * Color attributes defining a Starfish's visual style.
 *
 * Each [Starfish] instance picks one of [PALETTES] at random upon creation,
 * or gets assigned [SHINY_GOLD] / [SHINY_DIAMOND] based on its shiny roll.
 *
 * [bodyColor]: Main skin tone across arms and central disc.
 * [detailColor]: Spines, tubercles, and central ring nodules.
 * [suctionColor]: Suction tube feet pressed against the glass.
 * [glowColor]: Bioluminescent nighttime glow tint.
 */
class StarfishPalette(
    val bodyColor: FloatArray,
    val detailColor: FloatArray,
    val suctionColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Coral Orange: Classic reef sea star with vibrant warm coral body and cream suction pads. */
        val CORAL_ORANGE = StarfishPalette(
            bodyColor = floatArrayOf(0.95f, 0.42f, 0.18f),
            detailColor = floatArrayOf(1.0f, 0.72f, 0.35f),
            suctionColor = floatArrayOf(0.98f, 0.88f, 0.78f),
            glowColor = floatArrayOf(1.0f, 0.55f, 0.20f) // Warm amber-gold glow
        )

        /** Pacific Blue: Majestic Linckia laevigata with deep cobalt blue and cyan bioluminescence. */
        val PACIFIC_BLUE = StarfishPalette(
            bodyColor = floatArrayOf(0.12f, 0.38f, 0.88f),
            detailColor = floatArrayOf(0.35f, 0.70f, 1.0f),
            suctionColor = floatArrayOf(0.75f, 0.88f, 1.0f),
            glowColor = floatArrayOf(0.15f, 0.85f, 1.0f) // Electric cyan glow
        )

        /** Red-Knobbed: Cream / ivory base with brilliant scarlet-red spines (Protoreaster linckii). */
        val RED_KNOBBED = StarfishPalette(
            bodyColor = floatArrayOf(0.94f, 0.90f, 0.85f),
            detailColor = floatArrayOf(0.92f, 0.15f, 0.18f),
            suctionColor = floatArrayOf(0.98f, 0.85f, 0.80f),
            glowColor = floatArrayOf(1.0f, 0.25f, 0.35f) // Crimson neon glow
        )

        /** Sunburst Gold: Rich golden yellow ochre sea star with radiant highlights. */
        val SUNBURST_GOLD = StarfishPalette(
            bodyColor = floatArrayOf(0.95f, 0.75f, 0.15f),
            detailColor = floatArrayOf(1.0f, 0.92f, 0.45f),
            suctionColor = floatArrayOf(0.98f, 0.92f, 0.75f),
            glowColor = floatArrayOf(1.0f, 0.82f, 0.20f) // Golden glow
        )

        /** Purple Reef: Deep royal violet with lavender suction tube feet. */
        val PURPLE_REEF = StarfishPalette(
            bodyColor = floatArrayOf(0.55f, 0.22f, 0.68f),
            detailColor = floatArrayOf(0.82f, 0.45f, 0.95f),
            suctionColor = floatArrayOf(0.90f, 0.78f, 0.98f),
            glowColor = floatArrayOf(0.75f, 0.35f, 1.0f) // Neon purple glow
        )

        /** Shiny Gold: Ultra-rare golden sea star with radiant sparkles. */
        val SHINY_GOLD = StarfishPalette(
            bodyColor = floatArrayOf(0.98f, 0.84f, 0.25f),
            detailColor = floatArrayOf(1.0f, 0.96f, 0.65f),
            suctionColor = floatArrayOf(1.0f, 0.95f, 0.80f),
            glowColor = floatArrayOf(1.0f, 0.88f, 0.30f)
        )

        /** Shiny Diamond: Ultra-rare iridescent crystalline sea star. */
        val SHINY_DIAMOND = StarfishPalette(
            bodyColor = floatArrayOf(0.75f, 0.92f, 1.0f),
            detailColor = floatArrayOf(0.90f, 0.98f, 1.0f),
            suctionColor = floatArrayOf(0.95f, 0.98f, 1.0f),
            glowColor = floatArrayOf(0.60f, 0.95f, 1.0f)
        )

        val PALETTES: List<StarfishPalette> = listOf(
            CORAL_ORANGE,
            PACIFIC_BLUE,
            RED_KNOBBED,
            SUNBURST_GOLD,
            PURPLE_REEF
        )
    }
}
