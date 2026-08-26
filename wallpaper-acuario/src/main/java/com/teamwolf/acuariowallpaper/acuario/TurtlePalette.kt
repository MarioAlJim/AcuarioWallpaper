package com.teamwolf.acuariowallpaper.acuario

/**
 * A turtle's shell/head/flipper/shell-pattern colors. Not user-selectable - each [Turtle]
 * instance picks one of [PALETTES] at random when it's created (see `Turtle.palette`), loosely
 * modeled on real species so the tank ends up with a plausible variety instead of arbitrary
 * colors.
 *
 * [glowColor] is the neon tint turtle.frag adds to the shell grooves once it's dark enough (see
 * its uGlowColor/nightGlow comment) - each species gets its own hue instead of every turtle
 * glowing the same fixed color, spreading the tank's bioluminescence across a wide range of
 * colors rather than one repeated note.
 */
class TurtlePalette(
    val shellColor: FloatArray,
    val headColor: FloatArray,
    val flipperColor: FloatArray,
    val spotColor: FloatArray,
    val glowColor: FloatArray
) {
    companion object {
        /** Green sea turtle: olive-green shell, slightly lighter head/flippers. */
        private val GREEN_SEA_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.16f, 0.38f, 0.20f),
            headColor = floatArrayOf(0.24f, 0.48f, 0.26f),
            flipperColor = floatArrayOf(0.20f, 0.42f, 0.22f),
            spotColor = floatArrayOf(0.09f, 0.24f, 0.13f),
            glowColor = floatArrayOf(0.0f, 1.0f, 0.5f) // Neon emerald
        )

        /** Loggerhead: reddish-brown shell. */
        private val LOGGERHEAD = TurtlePalette(
            shellColor = floatArrayOf(0.42f, 0.22f, 0.10f),
            headColor = floatArrayOf(0.52f, 0.30f, 0.14f),
            flipperColor = floatArrayOf(0.46f, 0.25f, 0.11f),
            spotColor = floatArrayOf(0.24f, 0.11f, 0.04f),
            glowColor = floatArrayOf(1.0f, 0.70f, 0.05f) // Neon amber gold
        )

        /** Hawksbill: warm amber/tortoiseshell tones. */
        private val HAWKSBILL = TurtlePalette(
            shellColor = floatArrayOf(0.46f, 0.34f, 0.08f),
            headColor = floatArrayOf(0.55f, 0.42f, 0.12f),
            flipperColor = floatArrayOf(0.48f, 0.36f, 0.09f),
            spotColor = floatArrayOf(0.20f, 0.13f, 0.03f),
            glowColor = floatArrayOf(1.0f, 0.45f, 0.10f) // Neon warm orange
        )

        /** Painted turtle: near-black olive shell with a warm reddish limb undertone. */
        private val PAINTED_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.14f, 0.16f, 0.10f),
            headColor = floatArrayOf(0.20f, 0.22f, 0.14f),
            flipperColor = floatArrayOf(0.30f, 0.10f, 0.08f),
            spotColor = floatArrayOf(0.05f, 0.06f, 0.03f),
            glowColor = floatArrayOf(0.95f, 0.10f, 0.20f) // Neon crimson
        )

        /** Red-eared slider: dark green shell, a yellow-green stripe hint on the head. */
        private val RED_EARED_SLIDER = TurtlePalette(
            shellColor = floatArrayOf(0.10f, 0.28f, 0.14f),
            headColor = floatArrayOf(0.55f, 0.55f, 0.10f),
            flipperColor = floatArrayOf(0.14f, 0.32f, 0.16f),
            spotColor = floatArrayOf(0.06f, 0.16f, 0.08f),
            glowColor = floatArrayOf(0.70f, 1.0f, 0.10f) // Neon chartreuse
        )

        /** Leatherback: near-black, rubbery skin with faint pale speckling. */
        private val LEATHERBACK = TurtlePalette(
            shellColor = floatArrayOf(0.08f, 0.09f, 0.11f),
            headColor = floatArrayOf(0.14f, 0.15f, 0.17f),
            flipperColor = floatArrayOf(0.10f, 0.11f, 0.13f),
            spotColor = floatArrayOf(0.75f, 0.75f, 0.72f),
            glowColor = floatArrayOf(0.55f, 0.80f, 1.0f) // Neon ice-blue
        )

        /** Kemp's Ridley: pale grey-olive shell, lighter head. */
        private val KEMPS_RIDLEY = TurtlePalette(
            shellColor = floatArrayOf(0.36f, 0.40f, 0.28f),
            headColor = floatArrayOf(0.46f, 0.50f, 0.34f),
            flipperColor = floatArrayOf(0.40f, 0.44f, 0.30f),
            spotColor = floatArrayOf(0.20f, 0.22f, 0.14f),
            glowColor = floatArrayOf(0.0f, 0.90f, 0.85f) // Neon cyan
        )

        /** Olive Ridley: warm olive-grey shell, slightly warmer head undertone. */
        private val OLIVE_RIDLEY = TurtlePalette(
            shellColor = floatArrayOf(0.30f, 0.34f, 0.16f),
            headColor = floatArrayOf(0.40f, 0.44f, 0.20f),
            flipperColor = floatArrayOf(0.34f, 0.38f, 0.18f),
            spotColor = floatArrayOf(0.16f, 0.18f, 0.08f),
            glowColor = floatArrayOf(0.55f, 0.20f, 1.0f) // Neon violet
        )

        /** Flatback: uniform pale sage-green shell and skin. */
        private val FLATBACK = TurtlePalette(
            shellColor = floatArrayOf(0.42f, 0.50f, 0.38f),
            headColor = floatArrayOf(0.50f, 0.58f, 0.44f),
            flipperColor = floatArrayOf(0.46f, 0.54f, 0.40f),
            spotColor = floatArrayOf(0.26f, 0.32f, 0.22f),
            glowColor = floatArrayOf(0.05f, 0.85f, 0.65f) // Neon teal
        )

        /** African Helmeted Turtle: dark brown shell, yellowish head/throat tones. */
        private val AFRICAN_HELMETED_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.24f, 0.16f, 0.09f),
            headColor = floatArrayOf(0.52f, 0.48f, 0.18f),
            flipperColor = floatArrayOf(0.28f, 0.19f, 0.10f),
            spotColor = floatArrayOf(0.14f, 0.09f, 0.05f),
            glowColor = floatArrayOf(1.0f, 0.15f, 0.65f) // Neon hot pink
        )

        /** Map Turtle: grey-green shell with a fine yellow head-stripe undertone. */
        private val MAP_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.22f, 0.30f, 0.28f),
            headColor = floatArrayOf(0.40f, 0.46f, 0.16f),
            flipperColor = floatArrayOf(0.26f, 0.34f, 0.30f),
            spotColor = floatArrayOf(0.10f, 0.16f, 0.14f),
            glowColor = floatArrayOf(0.10f, 0.45f, 1.0f) // Neon electric blue
        )

        /** Albino turtle: pale cream shell and skin, a rare leucistic variant. */
        private val ALBINO_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.75f, 0.70f, 0.55f),
            headColor = floatArrayOf(0.80f, 0.76f, 0.62f),
            flipperColor = floatArrayOf(0.78f, 0.73f, 0.58f),
            spotColor = floatArrayOf(0.55f, 0.50f, 0.38f),
            glowColor = floatArrayOf(1.0f, 0.95f, 0.70f) // Neon warm gold
        )

        /** Black Sea Turtle: near-black shell with a subtle cool sheen. */
        private val BLACK_SEA_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.06f, 0.08f, 0.10f),
            headColor = floatArrayOf(0.10f, 0.12f, 0.15f),
            flipperColor = floatArrayOf(0.08f, 0.10f, 0.13f),
            spotColor = floatArrayOf(0.20f, 0.25f, 0.35f),
            glowColor = floatArrayOf(0.20f, 0.60f, 1.0f) // Neon electric blue
        )

        /** Sapphire Turtle: a stylized deep-blue shell, standing out among the earthier tones. */
        private val SAPPHIRE_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.08f, 0.20f, 0.45f),
            headColor = floatArrayOf(0.14f, 0.28f, 0.55f),
            flipperColor = floatArrayOf(0.10f, 0.24f, 0.50f),
            spotColor = floatArrayOf(0.05f, 0.12f, 0.30f),
            glowColor = floatArrayOf(0.10f, 0.50f, 1.0f) // Neon sapphire blue
        )

        /** Coral Turtle: a stylized warm coral-pink shell. */
        private val CORAL_TURTLE = TurtlePalette(
            shellColor = floatArrayOf(0.55f, 0.22f, 0.20f),
            headColor = floatArrayOf(0.65f, 0.30f, 0.26f),
            flipperColor = floatArrayOf(0.58f, 0.25f, 0.22f),
            spotColor = floatArrayOf(0.32f, 0.12f, 0.10f),
            glowColor = floatArrayOf(1.0f, 0.30f, 0.45f) // Neon coral-pink
        )

        val PALETTES: List<TurtlePalette> = listOf(
            GREEN_SEA_TURTLE,
            LOGGERHEAD,
            HAWKSBILL,
            PAINTED_TURTLE,
            RED_EARED_SLIDER,
            LEATHERBACK,
            KEMPS_RIDLEY,
            OLIVE_RIDLEY,
            FLATBACK,
            AFRICAN_HELMETED_TURTLE,
            MAP_TURTLE,
            ALBINO_TURTLE,
            BLACK_SEA_TURTLE,
            SAPPHIRE_TURTLE,
            CORAL_TURTLE
        )
    }
}
