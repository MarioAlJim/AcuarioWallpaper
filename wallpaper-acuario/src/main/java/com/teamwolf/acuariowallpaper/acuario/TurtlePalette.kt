package com.teamwolf.acuariowallpaper.acuario

/**
 * A turtle's shell/head/flipper/shell-pattern colors. Not user-selectable - each [Turtle]
 * instance picks one of [PALETTES] at random when it's created (see `Turtle.palette`), loosely
 * modeled on real species so the tank ends up with a plausible variety instead of arbitrary
 * colors.
 */
class TurtlePalette(
    val shellColor: FloatArray,
    val headColor: FloatArray,
    val flipperColor: FloatArray,
    val spotColor: FloatArray
) {
    companion object {
        /** Green sea turtle: olive-green shell, slightly lighter head/flippers. */
        private val GREEN_SEA_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.16f, 0.38f, 0.20f),
            headColor = floatArrayOf(0.24f, 0.48f, 0.26f),
            flipperColor = floatArrayOf(0.20f, 0.42f, 0.22f),
            spotColor = floatArrayOf(0.09f, 0.24f, 0.13f)
        )

        /** Loggerhead: reddish-brown shell. */
        private val LOGGERHEAD = TurtlePalette(
            shellColor = floatArrayOf(0.42f, 0.22f, 0.10f),
            headColor = floatArrayOf(0.52f, 0.30f, 0.14f),
            flipperColor = floatArrayOf(0.46f, 0.25f, 0.11f),
            spotColor = floatArrayOf(0.24f, 0.11f, 0.04f)
        )

        /** Hawksbill: warm amber/tortoiseshell tones. */
        private val HAWKSBILL = TurtlePalette(
            shellColor = floatArrayOf(0.46f, 0.34f, 0.08f),
            headColor = floatArrayOf(0.55f, 0.42f, 0.12f),
            flipperColor = floatArrayOf(0.48f, 0.36f, 0.09f),
            spotColor = floatArrayOf(0.20f, 0.13f, 0.03f)
        )

        /** Painted turtle: near-black olive shell with a warm reddish limb undertone. */
        private val PAINTED_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.14f, 0.16f, 0.10f),
            headColor = floatArrayOf(0.20f, 0.22f, 0.14f),
            flipperColor = floatArrayOf(0.30f, 0.10f, 0.08f),
            spotColor = floatArrayOf(0.05f, 0.06f, 0.03f)
        )

        /** Red-eared slider: dark green shell, a yellow-green stripe hint on the head. */
        private val RED_EARED_SLIDER = TurtlePalette(
            shellColor = floatArrayOf(0.10f, 0.28f, 0.14f),
            headColor = floatArrayOf(0.55f, 0.55f, 0.10f),
            flipperColor = floatArrayOf(0.14f, 0.32f, 0.16f),
            spotColor = floatArrayOf(0.06f, 0.16f, 0.08f)
        )

        val PALETTES: List<TurtlePalette> = listOf(
            GREEN_SEA_TURTLE,
            LOGGERHEAD,
            HAWKSBILL,
            PAINTED_TURTLE,
            RED_EARED_SLIDER
        )
    }
}
