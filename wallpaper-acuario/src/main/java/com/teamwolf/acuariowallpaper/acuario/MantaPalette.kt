package com.teamwolf.acuariowallpaper.acuario

/**
 * A manta ray's body/wing-edge/tail/marking colors. Not user-selectable - each [Manta] instance
 * picks one of [PALETTES] at random when it's created, loosely modeled on real ray species so
 * the tank ends up with a plausible variety instead of arbitrary colors.
 */
class MantaPalette(
    val bodyColor: FloatArray,
    val wingColor: FloatArray,
    val tailColor: FloatArray,
    val markingColor: FloatArray
) {
    companion object {
        /** Reef manta ray: near-black body, pale shoulder chevron patches. */
        private val REEF_MANTA = MantaPalette(
            bodyColor = floatArrayOf(0.05f, 0.06f, 0.08f),
            wingColor = floatArrayOf(0.08f, 0.09f, 0.11f),
            tailColor = floatArrayOf(0.05f, 0.06f, 0.08f),
            markingColor = floatArrayOf(0.85f, 0.86f, 0.88f)
        )

        /** Oceanic manta ray: dark slate-blue body, smaller/dimmer shoulder patches. */
        private val OCEANIC_MANTA = MantaPalette(
            bodyColor = floatArrayOf(0.07f, 0.11f, 0.18f),
            wingColor = floatArrayOf(0.10f, 0.15f, 0.22f),
            tailColor = floatArrayOf(0.06f, 0.09f, 0.15f),
            markingColor = floatArrayOf(0.55f, 0.60f, 0.68f)
        )

        /** Spotted eagle ray: dark blue-grey body scattered with pale rings/spots. */
        private val SPOTTED_EAGLE_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.08f, 0.16f, 0.22f),
            wingColor = floatArrayOf(0.10f, 0.19f, 0.25f),
            tailColor = floatArrayOf(0.06f, 0.12f, 0.17f),
            markingColor = floatArrayOf(0.80f, 0.88f, 0.90f)
        )

        /** Cownose ray: warm golden-brown body, slightly darker wingtips. */
        private val COWNOSE_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.45f, 0.32f, 0.16f),
            wingColor = floatArrayOf(0.36f, 0.25f, 0.12f),
            tailColor = floatArrayOf(0.32f, 0.22f, 0.10f),
            markingColor = floatArrayOf(0.58f, 0.44f, 0.24f)
        )

        /** Devil ray (Mobula): deep blue-black body, crisp pale belly-edge trim. */
        private val DEVIL_RAY = MantaPalette(
            bodyColor = floatArrayOf(0.04f, 0.05f, 0.14f),
            wingColor = floatArrayOf(0.06f, 0.07f, 0.18f),
            tailColor = floatArrayOf(0.04f, 0.05f, 0.14f),
            markingColor = floatArrayOf(0.75f, 0.78f, 0.90f)
        )

        val PALETTES: List<MantaPalette> = listOf(
            REEF_MANTA,
            OCEANIC_MANTA,
            SPOTTED_EAGLE_RAY,
            COWNOSE_RAY,
            DEVIL_RAY
        )
    }
}
