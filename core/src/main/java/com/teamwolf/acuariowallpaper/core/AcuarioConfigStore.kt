package com.teamwolf.acuariowallpaper.core

import android.content.SharedPreferences

/**
 * Persisted settings for the Acuario effect (fish, bubbles, background, ...).
 *
 * Add a `cachedInt`/`cachedBoolean` getter and a `coerceIn`-clamped setter here per new
 * knob, following the pattern in [CachedPrefStore], then expose both through
 * [ConfigProvider]/[ConfigManager].
 */
internal class AcuarioConfigStore(prefs: SharedPreferences) : CachedPrefStore(prefs) {

    fun getAcuarioTheme(): Int = cachedInt(ConfigManager.KEY_ACUARIO_THEME, ConfigManager.DEFAULT_ACUARIO_THEME)
    fun setAcuarioTheme(theme: Int) = putInt(ConfigManager.KEY_ACUARIO_THEME, theme.coerceIn(0, 5))

    fun getTurtleCount(): Int = cachedInt(ConfigManager.KEY_TURTLE_COUNT, ConfigManager.DEFAULT_TURTLE_COUNT)
    fun setTurtleCount(count: Int) = putInt(ConfigManager.KEY_TURTLE_COUNT, count.coerceIn(0, 5))

    fun getFishCount(): Int = cachedInt(ConfigManager.KEY_FISH_COUNT, ConfigManager.DEFAULT_FISH_COUNT)
    fun setFishCount(count: Int) = putInt(ConfigManager.KEY_FISH_COUNT, count.coerceIn(0, 8))

    fun getMantaCount(): Int = cachedInt(ConfigManager.KEY_MANTA_COUNT, ConfigManager.DEFAULT_MANTA_COUNT)
    fun setMantaCount(count: Int) = putInt(ConfigManager.KEY_MANTA_COUNT, count.coerceIn(0, 4))

    fun getJellyfishCount(): Int = cachedInt(ConfigManager.KEY_JELLYFISH_COUNT, ConfigManager.DEFAULT_JELLYFISH_COUNT)
    fun setJellyfishCount(count: Int) = putInt(ConfigManager.KEY_JELLYFISH_COUNT, count.coerceIn(0, 6))

    fun getAxolotlCount(): Int = cachedInt(ConfigManager.KEY_AXOLOTL_COUNT, ConfigManager.DEFAULT_AXOLOTL_COUNT)
    fun setAxolotlCount(count: Int) = putInt(ConfigManager.KEY_AXOLOTL_COUNT, count.coerceIn(0, 4))

    fun getStarfishCount(): Int = cachedInt(ConfigManager.KEY_STARFISH_COUNT, ConfigManager.DEFAULT_STARFISH_COUNT)
    fun setStarfishCount(count: Int) = putInt(ConfigManager.KEY_STARFISH_COUNT, count.coerceIn(0, 3))

    fun getSharkPresence(): Int = cachedInt(ConfigManager.KEY_SHARK_PRESENCE, ConfigManager.DEFAULT_SHARK_PRESENCE)
    fun setSharkPresence(presence: Int) = putInt(ConfigManager.KEY_SHARK_PRESENCE, presence.coerceIn(0, 2))

    fun getSharkColor(): Int = cachedInt(ConfigManager.KEY_SHARK_COLOR, ConfigManager.DEFAULT_SHARK_COLOR)
    fun setSharkColor(color: Int) = putInt(ConfigManager.KEY_SHARK_COLOR, color.coerceIn(0, 3))

    fun getPlantDensity(): Int = cachedInt(ConfigManager.KEY_PLANT_DENSITY, ConfigManager.DEFAULT_PLANT_DENSITY)
    fun setPlantDensity(count: Int) = putInt(ConfigManager.KEY_PLANT_DENSITY, count.coerceIn(0, 28))

    fun getBubbleCount(): Int = cachedInt(ConfigManager.KEY_BUBBLE_COUNT, ConfigManager.DEFAULT_BUBBLE_COUNT)
    fun setBubbleCount(count: Int) = putInt(ConfigManager.KEY_BUBBLE_COUNT, count.coerceIn(4, 45))

    fun getDayNightCycleDuration(): Int = cachedInt(ConfigManager.KEY_DAY_NIGHT_CYCLE, ConfigManager.DEFAULT_DAY_NIGHT_CYCLE)
    fun setDayNightCycleDuration(duration: Int) {
        val coerced = if (duration == 60 || duration == 180 || duration == 600 || duration == -1) duration else 180
        putInt(ConfigManager.KEY_DAY_NIGHT_CYCLE, coerced)
    }

    fun getCustomShallowColor(): Int = cachedInt(ConfigManager.KEY_CUSTOM_SHALLOW_COLOR, ConfigManager.DEFAULT_CUSTOM_SHALLOW_COLOR)
    fun setCustomShallowColor(color: Int) = putInt(ConfigManager.KEY_CUSTOM_SHALLOW_COLOR, color)

    fun getCustomDeepColor(): Int = cachedInt(ConfigManager.KEY_CUSTOM_DEEP_COLOR, ConfigManager.DEFAULT_CUSTOM_DEEP_COLOR)
    fun setCustomDeepColor(color: Int) = putInt(ConfigManager.KEY_CUSTOM_DEEP_COLOR, color)

    fun getSubmarineColor(): Int = cachedInt(ConfigManager.KEY_SUBMARINE_COLOR, ConfigManager.DEFAULT_SUBMARINE_COLOR)
    fun setSubmarineColor(color: Int) = putInt(ConfigManager.KEY_SUBMARINE_COLOR, color.coerceIn(0, 5))

    fun getChestColor(): Int = cachedInt(ConfigManager.KEY_CHEST_COLOR, ConfigManager.DEFAULT_CHEST_COLOR)
    fun setChestColor(color: Int) = putInt(ConfigManager.KEY_CHEST_COLOR, color.coerceIn(0, 2))

    fun getBioluminescenceEnabled(): Boolean = cachedBoolean(ConfigManager.KEY_BIOLUMINESCENCE_ENABLED, ConfigManager.DEFAULT_BIOLUMINESCENCE_ENABLED)
    fun setBioluminescenceEnabled(enabled: Boolean) = putBoolean(ConfigManager.KEY_BIOLUMINESCENCE_ENABLED, enabled)

    fun getRealisticModeEnabled(): Boolean = cachedBoolean(ConfigManager.KEY_REALISTIC_MODE, ConfigManager.DEFAULT_REALISTIC_MODE)
    fun setRealisticModeEnabled(enabled: Boolean) = putBoolean(ConfigManager.KEY_REALISTIC_MODE, enabled)
}
