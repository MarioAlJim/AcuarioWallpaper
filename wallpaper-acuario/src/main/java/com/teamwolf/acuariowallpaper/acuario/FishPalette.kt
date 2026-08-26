package com.teamwolf.acuariowallpaper.acuario

/**
 * A fish's body/fin/tail/stripe colors. Not user-selectable - each [Fish]
 * instance picks one of [PALETTES] at random when it's created, loosely
 * modeled on real tropical species so the tank ends up with a plausible variety
 * instead of arbitrary colors.
 *
 * [glowColor] is the neon tint fish.frag adds to the stripe pattern once it's dark enough (see
 * its uGlowColor/nightGlow comment) - each species gets its own hue instead of every fish
 * glowing the same fixed color, spreading the tank's bioluminescence across a wide range of
 * colors rather than one repeated note.
 */
class FishPalette(
    val bodyColor: FloatArray,
    val finColor: FloatArray,
    val tailColor: FloatArray,
    val stripeColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Clownfish: Orange body, white stripes, slightly darker/black fin tips. */
        private val CLOWNFISH = FishPalette(
            bodyColor = floatArrayOf(0.95f, 0.45f, 0.05f),
            finColor = floatArrayOf(0.85f, 0.35f, 0.02f),
            tailColor = floatArrayOf(0.90f, 0.40f, 0.03f),
            stripeColor = floatArrayOf(0.95f, 0.95f, 0.95f),
            glowColor = floatArrayOf(0.0f, 1.0f, 0.85f) // Neon cyan
        )

        /** Blue Tang (Regal Tang): Royal blue body, yellow tail/fins, black markings/stripe. */
        private val BLUE_TANG = FishPalette(
            bodyColor = floatArrayOf(0.05f, 0.25f, 0.85f),
            finColor = floatArrayOf(0.95f, 0.85f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.85f, 0.05f),
            stripeColor = floatArrayOf(0.08f, 0.08f, 0.08f),
            glowColor = floatArrayOf(1.0f, 0.75f, 0.05f) // Neon amber/gold
        )

        /** Yellow Tang: Bright yellow body, slightly warmer fins. */
        private val YELLOW_TANG = FishPalette(
            bodyColor = floatArrayOf(0.95f, 0.85f, 0.02f),
            finColor = floatArrayOf(0.95f, 0.88f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.88f, 0.05f),
            stripeColor = floatArrayOf(0.90f, 0.82f, 0.02f),
            glowColor = floatArrayOf(0.65f, 0.15f, 1.0f) // Neon violet
        )

        /** Moorish Idol: Black & white stripes, yellow highlights. */
        private val MOORISH_IDOL = FishPalette(
            bodyColor = floatArrayOf(0.90f, 0.90f, 0.90f),
            finColor = floatArrayOf(0.10f, 0.10f, 0.10f),
            tailColor = floatArrayOf(0.90f, 0.80f, 0.05f),
            stripeColor = floatArrayOf(0.08f, 0.08f, 0.08f),
            glowColor = floatArrayOf(0.0f, 0.85f, 0.65f) // Neon teal
        )

        /** Royal Gramma: Vibrant purple front, yellow tail/rear. */
        private val ROYAL_GRAMMA = FishPalette(
            bodyColor = floatArrayOf(0.70f, 0.05f, 0.80f),
            finColor = floatArrayOf(0.95f, 0.80f, 0.02f),
            tailColor = floatArrayOf(0.95f, 0.80f, 0.02f),
            stripeColor = floatArrayOf(0.85f, 0.40f, 0.02f),
            glowColor = floatArrayOf(1.0f, 0.15f, 0.65f) // Neon hot pink
        )

        /** Queen Angelfish: blue-green body, vivid yellow tail/fin edges. */
        private val QUEEN_ANGELFISH = FishPalette(
            bodyColor = floatArrayOf(0.05f, 0.45f, 0.65f),
            finColor = floatArrayOf(0.95f, 0.75f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.80f, 0.10f),
            stripeColor = floatArrayOf(0.05f, 0.70f, 0.55f),
            glowColor = floatArrayOf(0.15f, 0.45f, 1.0f) // Neon electric blue
        )

        /** Stoplight Parrotfish: teal-green body, coral-orange highlights. */
        private val PARROTFISH = FishPalette(
            bodyColor = floatArrayOf(0.02f, 0.55f, 0.45f),
            finColor = floatArrayOf(0.95f, 0.45f, 0.20f),
            tailColor = floatArrayOf(0.90f, 0.35f, 0.15f),
            stripeColor = floatArrayOf(0.95f, 0.70f, 0.10f),
            glowColor = floatArrayOf(0.55f, 1.0f, 0.15f) // Neon lime
        )

        /** Copperband Butterflyfish: pale body, bold orange vertical bands. */
        private val BUTTERFLYFISH = FishPalette(
            bodyColor = floatArrayOf(0.92f, 0.90f, 0.82f),
            finColor = floatArrayOf(0.90f, 0.55f, 0.10f),
            tailColor = floatArrayOf(0.92f, 0.90f, 0.82f),
            stripeColor = floatArrayOf(0.85f, 0.45f, 0.05f),
            glowColor = floatArrayOf(1.0f, 0.30f, 0.20f) // Neon coral-red
        )

        /** Lionfish: creamy body, deep maroon-red banding. */
        private val LIONFISH = FishPalette(
            bodyColor = floatArrayOf(0.90f, 0.80f, 0.60f),
            finColor = floatArrayOf(0.55f, 0.10f, 0.08f),
            tailColor = floatArrayOf(0.60f, 0.15f, 0.10f),
            stripeColor = floatArrayOf(0.45f, 0.06f, 0.05f),
            glowColor = floatArrayOf(1.0f, 0.10f, 0.20f) // Neon crimson
        )

        /** Mandarinfish: teal body, swirling orange psychedelic markings. */
        private val MANDARINFISH = FishPalette(
            bodyColor = floatArrayOf(0.05f, 0.55f, 0.60f),
            finColor = floatArrayOf(0.95f, 0.60f, 0.05f),
            tailColor = floatArrayOf(0.10f, 0.65f, 0.70f),
            stripeColor = floatArrayOf(0.85f, 0.15f, 0.55f),
            glowColor = floatArrayOf(0.05f, 0.90f, 0.75f) // Neon turquoise
        )

        /** Coral Beauty (dwarf angelfish): deep magenta-orange body, blue-tinted fin edges. */
        private val CORAL_BEAUTY = FishPalette(
            bodyColor = floatArrayOf(0.80f, 0.25f, 0.15f),
            finColor = floatArrayOf(0.15f, 0.25f, 0.75f),
            tailColor = floatArrayOf(0.85f, 0.30f, 0.10f),
            stripeColor = floatArrayOf(0.20f, 0.30f, 0.80f),
            glowColor = floatArrayOf(0.35f, 0.15f, 0.95f) // Neon indigo
        )

        /** Foxface Rabbitfish: bright yellow body, dark brown/black masked face pattern. */
        private val FOXFACE_RABBITFISH = FishPalette(
            bodyColor = floatArrayOf(0.95f, 0.90f, 0.10f),
            finColor = floatArrayOf(0.90f, 0.85f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.90f, 0.10f),
            stripeColor = floatArrayOf(0.20f, 0.10f, 0.05f),
            glowColor = floatArrayOf(1.0f, 0.90f, 0.10f) // Neon golden yellow
        )

        /** Powder Blue Tang: pale sky-blue body, black head mask, bright yellow dorsal fin. */
        private val POWDER_BLUE_TANG = FishPalette(
            bodyColor = floatArrayOf(0.35f, 0.65f, 0.90f),
            finColor = floatArrayOf(0.95f, 0.85f, 0.10f),
            tailColor = floatArrayOf(0.95f, 0.95f, 0.95f),
            stripeColor = floatArrayOf(0.08f, 0.10f, 0.15f),
            glowColor = floatArrayOf(0.20f, 0.70f, 1.0f) // Neon sky-blue
        )

        /** Picasso Triggerfish: sandy body, bold black/blue/orange geometric markings. */
        private val PICASSO_TRIGGERFISH = FishPalette(
            bodyColor = floatArrayOf(0.90f, 0.85f, 0.65f),
            finColor = floatArrayOf(0.10f, 0.10f, 0.10f),
            tailColor = floatArrayOf(0.90f, 0.55f, 0.10f),
            stripeColor = floatArrayOf(0.10f, 0.35f, 0.75f),
            glowColor = floatArrayOf(0.10f, 1.0f, 0.45f) // Neon emerald
        )

        /** Neon Tetra: electric-blue body with a vivid red rear half, silvery belly stripe. */
        private val NEON_TETRA = FishPalette(
            bodyColor = floatArrayOf(0.05f, 0.35f, 0.95f),
            finColor = floatArrayOf(0.90f, 0.10f, 0.10f),
            tailColor = floatArrayOf(0.85f, 0.10f, 0.10f),
            stripeColor = floatArrayOf(0.85f, 0.90f, 0.95f),
            glowColor = floatArrayOf(0.10f, 0.60f, 1.0f) // Neon electric blue
        )

        /** Red Discus: deep red-maroon body with darker banding. */
        private val DISCUS = FishPalette(
            bodyColor = floatArrayOf(0.75f, 0.10f, 0.12f),
            finColor = floatArrayOf(0.55f, 0.05f, 0.08f),
            tailColor = floatArrayOf(0.60f, 0.08f, 0.10f),
            stripeColor = floatArrayOf(0.30f, 0.02f, 0.03f),
            glowColor = floatArrayOf(1.0f, 0.05f, 0.30f) // Neon red-pink
        )

        /** Green Chromis: silvery-green iridescent schooling body. */
        private val GREEN_CHROMIS = FishPalette(
            bodyColor = floatArrayOf(0.55f, 0.75f, 0.65f),
            finColor = floatArrayOf(0.60f, 0.80f, 0.70f),
            tailColor = floatArrayOf(0.50f, 0.72f, 0.62f),
            stripeColor = floatArrayOf(0.80f, 0.95f, 0.85f),
            glowColor = floatArrayOf(0.30f, 1.0f, 0.55f) // Neon mint green
        )

        /** Achilles Tang: near-black body, bold orange/white tail patch. */
        private val ACHILLES_TANG = FishPalette(
            bodyColor = floatArrayOf(0.08f, 0.08f, 0.10f),
            finColor = floatArrayOf(0.90f, 0.35f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.55f, 0.05f),
            stripeColor = floatArrayOf(0.90f, 0.90f, 0.90f),
            glowColor = floatArrayOf(1.0f, 0.50f, 0.0f) // Neon orange
        )

        /** Pink Skunk Clownfish: pale peach-pink body, crisp white stripe. */
        private val PINK_SKUNK_CLOWNFISH = FishPalette(
            bodyColor = floatArrayOf(0.95f, 0.75f, 0.70f),
            finColor = floatArrayOf(0.90f, 0.55f, 0.50f),
            tailColor = floatArrayOf(0.95f, 0.70f, 0.65f),
            stripeColor = floatArrayOf(0.98f, 0.98f, 0.98f),
            glowColor = floatArrayOf(1.0f, 0.40f, 0.85f) // Neon pink
        )

        /** Midnight Angelfish: deep indigo-black body with bright blue rings. */
        private val MIDNIGHT_ANGELFISH = FishPalette(
            bodyColor = floatArrayOf(0.05f, 0.05f, 0.20f),
            finColor = floatArrayOf(0.10f, 0.10f, 0.35f),
            tailColor = floatArrayOf(0.08f, 0.08f, 0.28f),
            stripeColor = floatArrayOf(0.20f, 0.30f, 0.90f),
            glowColor = floatArrayOf(0.40f, 0.10f, 1.0f) // Neon deep violet
        )

        val PALETTES: List<FishPalette> = listOf(
            CLOWNFISH,
            BLUE_TANG,
            YELLOW_TANG,
            MOORISH_IDOL,
            ROYAL_GRAMMA,
            QUEEN_ANGELFISH,
            PARROTFISH,
            BUTTERFLYFISH,
            LIONFISH,
            MANDARINFISH,
            CORAL_BEAUTY,
            FOXFACE_RABBITFISH,
            POWDER_BLUE_TANG,
            PICASSO_TRIGGERFISH,
            NEON_TETRA,
            DISCUS,
            GREEN_CHROMIS,
            ACHILLES_TANG,
            PINK_SKUNK_CLOWNFISH,
            MIDNIGHT_ANGELFISH
        )
    }
}
