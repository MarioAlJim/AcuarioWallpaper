package com.teamwolf.acuariowallpaper.acuario

/**
 * Color attributes defining an Axolotl's visual style.
 *
 * Each [Axolotl] instance picks one of [PALETTES] at random upon creation,
 * or gets assigned [SHINY_GOLD] / [SHINY_DIAMOND] based on its shiny roll.
 *
 * [bodyColor]: Main skin tone (head, body, tail, limbs).
 * [gillColor]: External feathery branch gills hue.
 * [bellyColor]: Underside / highlight tone.
 * [spotColor]: Speckles / mottling spots across skin.
 * [glowColor]: Bioluminescent nighttime glow tint.
 */
class AxolotlPalette(
    val bodyColor: FloatArray,
    val gillColor: FloatArray,
    val bellyColor: FloatArray,
    val spotColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Classic Leucistic: Pink body with vivid magenta external gills and pale pink belly. */
        val LEUCISTIC = AxolotlPalette(
            bodyColor = floatArrayOf(0.96f, 0.78f, 0.82f),
            gillColor = floatArrayOf(0.95f, 0.22f, 0.48f),
            bellyColor = floatArrayOf(0.98f, 0.88f, 0.90f),
            spotColor = floatArrayOf(0.90f, 0.65f, 0.72f),
            glowColor = floatArrayOf(1.0f, 0.35f, 0.65f) // Neon pink
        )

        /** Golden Albino: Warm golden yellow skin with vivid red-gold gills. */
        val GOLDEN_ALBINO = AxolotlPalette(
            bodyColor = floatArrayOf(0.95f, 0.78f, 0.25f),
            gillColor = floatArrayOf(0.98f, 0.35f, 0.15f),
            bellyColor = floatArrayOf(0.98f, 0.88f, 0.45f),
            spotColor = floatArrayOf(0.90f, 0.65f, 0.10f),
            glowColor = floatArrayOf(1.0f, 0.75f, 0.15f) // Neon amber
        )

        /** Wild Type: Dark olive green / dark grey body with crimson gills and tan belly. */
        val WILD_TYPE = AxolotlPalette(
            bodyColor = floatArrayOf(0.28f, 0.35f, 0.26f),
            gillColor = floatArrayOf(0.68f, 0.12f, 0.18f),
            bellyColor = floatArrayOf(0.42f, 0.48f, 0.38f),
            spotColor = floatArrayOf(0.15f, 0.20f, 0.14f),
            glowColor = floatArrayOf(0.20f, 0.95f, 0.70f) // Neon emerald/cyan
        )

        /** White Albino: Pure pale peach white with vibrant coral pink gills. */
        val WHITE_ALBINO = AxolotlPalette(
            bodyColor = floatArrayOf(0.95f, 0.92f, 0.90f),
            gillColor = floatArrayOf(0.98f, 0.45f, 0.50f),
            bellyColor = floatArrayOf(0.98f, 0.96f, 0.94f),
            spotColor = floatArrayOf(0.90f, 0.82f, 0.80f),
            glowColor = floatArrayOf(0.40f, 0.90f, 1.0f) // Neon ice blue
        )

        /** Copper: Warm caramel brown skin with dark rust gills and golden undertones. */
        val COPPER = AxolotlPalette(
            bodyColor = floatArrayOf(0.72f, 0.48f, 0.28f),
            gillColor = floatArrayOf(0.58f, 0.20f, 0.12f),
            bellyColor = floatArrayOf(0.82f, 0.62f, 0.42f),
            spotColor = floatArrayOf(0.48f, 0.28f, 0.12f),
            glowColor = floatArrayOf(1.0f, 0.50f, 0.10f) // Neon warm orange
        )

        /** Lavender: Soft lavender skin with deep violet gills and pale purple belly. */
        val LAVENDER = AxolotlPalette(
            bodyColor = floatArrayOf(0.75f, 0.68f, 0.82f),
            gillColor = floatArrayOf(0.52f, 0.18f, 0.62f),
            bellyColor = floatArrayOf(0.85f, 0.80f, 0.90f),
            spotColor = floatArrayOf(0.45f, 0.35f, 0.55f),
            glowColor = floatArrayOf(0.75f, 0.30f, 1.0f) // Neon violet
        )

        /** Shiny Gold: Radiant metallic gold body, luminous golden gills, sparkling spots. */
        val SHINY_GOLD = AxolotlPalette(
            bodyColor = floatArrayOf(0.95f, 0.78f, 0.10f),
            gillColor = floatArrayOf(1.0f, 0.88f, 0.30f),
            bellyColor = floatArrayOf(0.98f, 0.90f, 0.45f),
            spotColor = floatArrayOf(1.0f, 0.95f, 0.60f),
            glowColor = floatArrayOf(1.0f, 0.85f, 0.0f) // Radiant gold
        )

        /** Shiny Diamond: Crystal ice blue / diamond body with shimmering azure gills. */
        val SHINY_DIAMOND = AxolotlPalette(
            bodyColor = floatArrayOf(0.78f, 0.92f, 0.98f),
            gillColor = floatArrayOf(0.35f, 0.75f, 0.98f),
            bellyColor = floatArrayOf(0.88f, 0.96f, 1.0f),
            spotColor = floatArrayOf(0.50f, 0.88f, 1.0f),
            glowColor = floatArrayOf(0.40f, 0.95f, 1.0f) // Radiant ice blue
        )

        val PALETTES: List<AxolotlPalette> = listOf(
            LEUCISTIC,
            GOLDEN_ALBINO,
            WILD_TYPE,
            WHITE_ALBINO,
            COPPER,
            LAVENDER
        )
    }
}
