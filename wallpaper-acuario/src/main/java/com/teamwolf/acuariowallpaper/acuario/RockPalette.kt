package com.teamwolf.acuariowallpaper.acuario

/**
 * Palette definitions for floor rocks and substrate boulders.
 *
 * Each [Rock] selects a palette at random to create a natural, varied stone riverbed
 * across the bottom of the aquarium tank.
 *
 * [baseColor]: Main stone body tint.
 * [highlightColor]: Sunlit top edge and ridge highlight.
 * [shadowColor]: Deep crevice and base ambient shadow.
 * [spotColor]: Mineral specks and moss accents.
 * [glowColor]: Night bioluminescent moss tint (active when uDayNight < 1.0).
 */
class RockPalette(
    val baseColor: FloatArray,
    val highlightColor: FloatArray,
    val shadowColor: FloatArray,
    val spotColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** River Slate: Cool bluish-grey layered stone. */
        val RIVER_SLATE = RockPalette(
            baseColor = floatArrayOf(0.25f, 0.28f, 0.32f),
            highlightColor = floatArrayOf(0.48f, 0.52f, 0.58f),
            shadowColor = floatArrayOf(0.10f, 0.12f, 0.15f),
            spotColor = floatArrayOf(0.18f, 0.22f, 0.26f),
            glowColor = floatArrayOf(0.20f, 0.80f, 0.90f) // Cyan bioluminescent moss
        )

        /** Granite: Warm specked grey stone with pinkish mineral flecks. */
        val GRANITE = RockPalette(
            baseColor = floatArrayOf(0.35f, 0.33f, 0.32f),
            highlightColor = floatArrayOf(0.60f, 0.58f, 0.55f),
            shadowColor = floatArrayOf(0.14f, 0.13f, 0.12f),
            spotColor = floatArrayOf(0.45f, 0.35f, 0.32f),
            glowColor = floatArrayOf(0.90f, 0.70f, 0.30f) // Golden mineral glow
        )

        /** Volcanic Basalt: Dark charcoal stone with warm highlights. */
        val BASALT = RockPalette(
            baseColor = floatArrayOf(0.18f, 0.19f, 0.20f),
            highlightColor = floatArrayOf(0.38f, 0.40f, 0.42f),
            shadowColor = floatArrayOf(0.06f, 0.07f, 0.08f),
            spotColor = floatArrayOf(0.12f, 0.14f, 0.16f),
            glowColor = floatArrayOf(0.30f, 0.95f, 0.60f) // Emerald moss glow
        )

        /** Sandstone: Warm ochre/tan riverbed rock. */
        val SANDSTONE = RockPalette(
            baseColor = floatArrayOf(0.42f, 0.36f, 0.28f),
            highlightColor = floatArrayOf(0.65f, 0.58f, 0.46f),
            shadowColor = floatArrayOf(0.18f, 0.14f, 0.10f),
            spotColor = floatArrayOf(0.50f, 0.40f, 0.28f),
            glowColor = floatArrayOf(0.95f, 0.85f, 0.35f) // Warm amber glow
        )

        /** Mossy Boulder: Deep grey stone carpeted with aquatic moss. */
        val MOSSY_BOULDER = RockPalette(
            baseColor = floatArrayOf(0.22f, 0.32f, 0.24f),
            highlightColor = floatArrayOf(0.42f, 0.58f, 0.38f),
            shadowColor = floatArrayOf(0.08f, 0.14f, 0.09f),
            spotColor = floatArrayOf(0.15f, 0.40f, 0.18f),
            glowColor = floatArrayOf(0.40f, 1.00f, 0.30f) // Neon green moss
        )

        val PALETTES: List<RockPalette> = listOf(
            RIVER_SLATE,
            GRANITE,
            BASALT,
            SANDSTONE,
            MOSSY_BOULDER
        )
    }
}
