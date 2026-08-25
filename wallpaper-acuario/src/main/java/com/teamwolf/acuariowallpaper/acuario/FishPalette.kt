package com.teamwolf.acuariowallpaper.acuario

/**
 * A fish's body/fin/tail/stripe colors. Not user-selectable - each [Fish]
 * instance picks one of [PALETTES] at random when it's created, loosely
 * modeled on real tropical species so the tank ends up with a plausible variety
 * instead of arbitrary colors.
 */
class FishPalette(
    val bodyColor: FloatArray,
    val finColor: FloatArray,
    val tailColor: FloatArray,
    val stripeColor: FloatArray
) {
    companion object {
        /** Clownfish: Orange body, white stripes, slightly darker/black fin tips. */
        private val CLOWNFISH = FishPalette(
            bodyColor = floatArrayOf(0.95f, 0.45f, 0.05f),
            finColor = floatArrayOf(0.85f, 0.35f, 0.02f),
            tailColor = floatArrayOf(0.90f, 0.40f, 0.03f),
            stripeColor = floatArrayOf(0.95f, 0.95f, 0.95f)
        )

        /** Blue Tang (Regal Tang): Royal blue body, yellow tail/fins, black markings/stripe. */
        private val BLUE_TANG = FishPalette(
            bodyColor = floatArrayOf(0.05f, 0.25f, 0.85f),
            finColor = floatArrayOf(0.95f, 0.85f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.85f, 0.05f),
            stripeColor = floatArrayOf(0.08f, 0.08f, 0.08f)
        )

        /** Yellow Tang: Bright yellow body, slightly warmer fins. */
        private val YELLOW_TANG = FishPalette(
            bodyColor = floatArrayOf(0.95f, 0.85f, 0.02f),
            finColor = floatArrayOf(0.95f, 0.88f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.88f, 0.05f),
            stripeColor = floatArrayOf(0.90f, 0.82f, 0.02f)
        )

        /** Moorish Idol: Black & white stripes, yellow highlights. */
        private val MOORISH_IDOL = FishPalette(
            bodyColor = floatArrayOf(0.90f, 0.90f, 0.90f),
            finColor = floatArrayOf(0.10f, 0.10f, 0.10f),
            tailColor = floatArrayOf(0.90f, 0.80f, 0.05f),
            stripeColor = floatArrayOf(0.08f, 0.08f, 0.08f)
        )

        /** Royal Gramma: Vibrant purple front, yellow tail/rear. */
        private val ROYAL_GRAMMA = FishPalette(
            bodyColor = floatArrayOf(0.70f, 0.05f, 0.80f),
            finColor = floatArrayOf(0.95f, 0.80f, 0.02f),
            tailColor = floatArrayOf(0.95f, 0.80f, 0.02f),
            stripeColor = floatArrayOf(0.85f, 0.40f, 0.02f)
        )

        /** Queen Angelfish: blue-green body, vivid yellow tail/fin edges. */
        private val QUEEN_ANGELFISH = FishPalette(
            bodyColor = floatArrayOf(0.05f, 0.45f, 0.65f),
            finColor = floatArrayOf(0.95f, 0.75f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.80f, 0.10f),
            stripeColor = floatArrayOf(0.05f, 0.70f, 0.55f)
        )

        /** Stoplight Parrotfish: teal-green body, coral-orange highlights. */
        private val PARROTFISH = FishPalette(
            bodyColor = floatArrayOf(0.02f, 0.55f, 0.45f),
            finColor = floatArrayOf(0.95f, 0.45f, 0.20f),
            tailColor = floatArrayOf(0.90f, 0.35f, 0.15f),
            stripeColor = floatArrayOf(0.95f, 0.70f, 0.10f)
        )

        /** Copperband Butterflyfish: pale body, bold orange vertical bands. */
        private val BUTTERFLYFISH = FishPalette(
            bodyColor = floatArrayOf(0.92f, 0.90f, 0.82f),
            finColor = floatArrayOf(0.90f, 0.55f, 0.10f),
            tailColor = floatArrayOf(0.92f, 0.90f, 0.82f),
            stripeColor = floatArrayOf(0.85f, 0.45f, 0.05f)
        )

        /** Lionfish: creamy body, deep maroon-red banding. */
        private val LIONFISH = FishPalette(
            bodyColor = floatArrayOf(0.90f, 0.80f, 0.60f),
            finColor = floatArrayOf(0.55f, 0.10f, 0.08f),
            tailColor = floatArrayOf(0.60f, 0.15f, 0.10f),
            stripeColor = floatArrayOf(0.45f, 0.06f, 0.05f)
        )

        /** Mandarinfish: teal body, swirling orange psychedelic markings. */
        private val MANDARINFISH = FishPalette(
            bodyColor = floatArrayOf(0.05f, 0.55f, 0.60f),
            finColor = floatArrayOf(0.95f, 0.60f, 0.05f),
            tailColor = floatArrayOf(0.10f, 0.65f, 0.70f),
            stripeColor = floatArrayOf(0.85f, 0.15f, 0.55f)
        )

        /** Coral Beauty (dwarf angelfish): deep magenta-orange body, blue-tinted fin edges. */
        private val CORAL_BEAUTY = FishPalette(
            bodyColor = floatArrayOf(0.80f, 0.25f, 0.15f),
            finColor = floatArrayOf(0.15f, 0.25f, 0.75f),
            tailColor = floatArrayOf(0.85f, 0.30f, 0.10f),
            stripeColor = floatArrayOf(0.20f, 0.30f, 0.80f)
        )

        /** Foxface Rabbitfish: bright yellow body, dark brown/black masked face pattern. */
        private val FOXFACE_RABBITFISH = FishPalette(
            bodyColor = floatArrayOf(0.95f, 0.90f, 0.10f),
            finColor = floatArrayOf(0.90f, 0.85f, 0.05f),
            tailColor = floatArrayOf(0.95f, 0.90f, 0.10f),
            stripeColor = floatArrayOf(0.20f, 0.10f, 0.05f)
        )

        /** Powder Blue Tang: pale sky-blue body, black head mask, bright yellow dorsal fin. */
        private val POWDER_BLUE_TANG = FishPalette(
            bodyColor = floatArrayOf(0.35f, 0.65f, 0.90f),
            finColor = floatArrayOf(0.95f, 0.85f, 0.10f),
            tailColor = floatArrayOf(0.95f, 0.95f, 0.95f),
            stripeColor = floatArrayOf(0.08f, 0.10f, 0.15f)
        )

        /** Picasso Triggerfish: sandy body, bold black/blue/orange geometric markings. */
        private val PICASSO_TRIGGERFISH = FishPalette(
            bodyColor = floatArrayOf(0.90f, 0.85f, 0.65f),
            finColor = floatArrayOf(0.10f, 0.10f, 0.10f),
            tailColor = floatArrayOf(0.90f, 0.55f, 0.10f),
            stripeColor = floatArrayOf(0.10f, 0.35f, 0.75f)
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
            PICASSO_TRIGGERFISH
        )
    }
}
