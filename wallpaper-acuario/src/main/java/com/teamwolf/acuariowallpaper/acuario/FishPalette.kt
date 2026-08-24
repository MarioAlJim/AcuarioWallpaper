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

        val PALETTES: List<FishPalette> = listOf(
            CLOWNFISH,
            BLUE_TANG,
            YELLOW_TANG,
            MOORISH_IDOL,
            ROYAL_GRAMMA
        )
    }
}
