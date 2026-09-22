package com.teamwolf.acuariowallpaper.acuario

/**
 * Color palette definition for the Sunken Treasure Chest.
 */
class TreasureChestPalette(
    val woodColor: FloatArray,
    val metalColor: FloatArray,
    val goldColor: FloatArray,
    val gemColor: FloatArray
) {
    companion object {
        val CLASSIC_PIRATE = TreasureChestPalette(
            woodColor = floatArrayOf(0.38f, 0.22f, 0.12f),  // Dark weathered oak
            metalColor = floatArrayOf(0.25f, 0.25f, 0.28f), // Iron / dark steel
            goldColor = floatArrayOf(0.98f, 0.78f, 0.15f),  // Bright piratical gold
            gemColor = floatArrayOf(0.90f, 0.15f, 0.20f)   // Crimson ruby
        )

        val ROYAL_VAULT = TreasureChestPalette(
            woodColor = floatArrayOf(0.20f, 0.12f, 0.08f),  // Deep mahogany
            metalColor = floatArrayOf(0.78f, 0.65f, 0.30f), // Polished brass / bronze
            goldColor = floatArrayOf(1.00f, 0.85f, 0.30f),  // Radiant gold
            gemColor = floatArrayOf(0.15f, 0.85f, 0.40f)   // Deep emerald
        )

        val ANCIENT_RELIC = TreasureChestPalette(
            woodColor = floatArrayOf(0.18f, 0.25f, 0.22f),  // Mossy algae wood
            metalColor = floatArrayOf(0.30f, 0.50f, 0.45f), // Aged verdigris copper
            goldColor = floatArrayOf(0.30f, 0.90f, 0.85f),  // Mystical cyan glow
            gemColor = floatArrayOf(0.40f, 0.60f, 1.00f)   // Sapphire glow
        )

        val PALETTES = listOf(CLASSIC_PIRATE, ROYAL_VAULT, ANCIENT_RELIC)
    }
}
