package com.teamwolf.acuariowallpaper.acuario

/**
 * Manages cyclic, non-repeating variations for aquatic fauna so that when creatures
 * disappear (due to starving or being hunted), new animals that appear or restore
 * will exhibit different species and color variations rather than identical repeats.
 */
object FaunaVariationManager {

    private val fishDeck = ArrayDeque<FishPalette>()
    private val recentlyDepartedFish = ArrayDeque<FishPalette>()

    private val turtleDeck = ArrayDeque<TurtlePalette>()
    private val recentlyDepartedTurtles = ArrayDeque<TurtlePalette>()

    private val mantaDeck = ArrayDeque<MantaPalette>()
    private val recentlyDepartedMantas = ArrayDeque<MantaPalette>()

    private val axolotlDeck = ArrayDeque<AxolotlPalette>()
    private val recentlyDepartedAxolotls = ArrayDeque<AxolotlPalette>()

    private val jellyfishDeck = ArrayDeque<JellyfishPalette>()
    private val recentlyDepartedJellyfishes = ArrayDeque<JellyfishPalette>()

    // --- FISH ---
    @Synchronized
    fun recordDepartedFish(palette: FishPalette) {
        if (palette != FishPalette.SHINY_GOLD && palette != FishPalette.SHINY_DIAMOND) {
            recentlyDepartedFish.addLast(palette)
            if (recentlyDepartedFish.size > 8) {
                recentlyDepartedFish.removeFirst()
            }
        }
    }

    @Synchronized
    fun nextFishPalette(): FishPalette {
        if (fishDeck.isEmpty()) {
            val shuffled = FishPalette.PALETTES.shuffled().toMutableList()
            shuffled.sortBy { if (recentlyDepartedFish.contains(it)) 1 else 0 }
            fishDeck.addAll(shuffled)
        }
        val it = fishDeck.iterator()
        while (it.hasNext()) {
            val candidate = it.next()
            if (!recentlyDepartedFish.contains(candidate)) {
                it.remove()
                return candidate
            }
        }
        return fishDeck.removeFirst()
    }

    // --- TURTLE ---
    @Synchronized
    fun recordDepartedTurtle(palette: TurtlePalette) {
        if (palette != TurtlePalette.SHINY_GOLD && palette != TurtlePalette.SHINY_DIAMOND) {
            recentlyDepartedTurtles.addLast(palette)
            if (recentlyDepartedTurtles.size > 4) {
                recentlyDepartedTurtles.removeFirst()
            }
        }
    }

    @Synchronized
    fun nextTurtlePalette(): TurtlePalette {
        if (turtleDeck.isEmpty()) {
            val shuffled = TurtlePalette.PALETTES.shuffled().toMutableList()
            shuffled.sortBy { if (recentlyDepartedTurtles.contains(it)) 1 else 0 }
            turtleDeck.addAll(shuffled)
        }
        val it = turtleDeck.iterator()
        while (it.hasNext()) {
            val candidate = it.next()
            if (!recentlyDepartedTurtles.contains(candidate)) {
                it.remove()
                return candidate
            }
        }
        return turtleDeck.removeFirst()
    }

    // --- MANTA ---
    @Synchronized
    fun recordDepartedManta(palette: MantaPalette) {
        if (palette != MantaPalette.SHINY_GOLD && palette != MantaPalette.SHINY_DIAMOND) {
            recentlyDepartedMantas.addLast(palette)
            if (recentlyDepartedMantas.size > 4) {
                recentlyDepartedMantas.removeFirst()
            }
        }
    }

    @Synchronized
    fun nextMantaPalette(): MantaPalette {
        if (mantaDeck.isEmpty()) {
            val shuffled = MantaPalette.PALETTES.shuffled().toMutableList()
            shuffled.sortBy { if (recentlyDepartedMantas.contains(it)) 1 else 0 }
            mantaDeck.addAll(shuffled)
        }
        val it = mantaDeck.iterator()
        while (it.hasNext()) {
            val candidate = it.next()
            if (!recentlyDepartedMantas.contains(candidate)) {
                it.remove()
                return candidate
            }
        }
        return mantaDeck.removeFirst()
    }

    // --- AXOLOTL ---
    @Synchronized
    fun recordDepartedAxolotl(palette: AxolotlPalette) {
        if (palette != AxolotlPalette.SHINY_GOLD && palette != AxolotlPalette.SHINY_DIAMOND) {
            recentlyDepartedAxolotls.addLast(palette)
            if (recentlyDepartedAxolotls.size > 4) {
                recentlyDepartedAxolotls.removeFirst()
            }
        }
    }

    @Synchronized
    fun nextAxolotlPalette(): AxolotlPalette {
        if (axolotlDeck.isEmpty()) {
            val shuffled = AxolotlPalette.PALETTES.shuffled().toMutableList()
            shuffled.sortBy { if (recentlyDepartedAxolotls.contains(it)) 1 else 0 }
            axolotlDeck.addAll(shuffled)
        }
        val it = axolotlDeck.iterator()
        while (it.hasNext()) {
            val candidate = it.next()
            if (!recentlyDepartedAxolotls.contains(candidate)) {
                it.remove()
                return candidate
            }
        }
        return axolotlDeck.removeFirst()
    }

    // --- JELLYFISH ---
    @Synchronized
    fun recordDepartedJellyfish(palette: JellyfishPalette) {
        if (palette != JellyfishPalette.SHINY_GOLD && palette != JellyfishPalette.SHINY_DIAMOND) {
            recentlyDepartedJellyfishes.addLast(palette)
            if (recentlyDepartedJellyfishes.size > 4) {
                recentlyDepartedJellyfishes.removeFirst()
            }
        }
    }

    @Synchronized
    fun nextJellyfishPalette(): JellyfishPalette {
        if (jellyfishDeck.isEmpty()) {
            val shuffled = JellyfishPalette.PALETTES.shuffled().toMutableList()
            shuffled.sortBy { if (recentlyDepartedJellyfishes.contains(it)) 1 else 0 }
            jellyfishDeck.addAll(shuffled)
        }
        val it = jellyfishDeck.iterator()
        while (it.hasNext()) {
            val candidate = it.next()
            if (!recentlyDepartedJellyfishes.contains(candidate)) {
                it.remove()
                return candidate
            }
        }
        return jellyfishDeck.removeFirst()
    }
}
