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

    @Volatile
    private var cachedTheme: Int = prefs.getInt(ConfigManager.KEY_ACUARIO_THEME, ConfigManager.DEFAULT_ACUARIO_THEME)

    @Volatile
    private var cachedTurtleCount: Int = prefs.getInt(ConfigManager.KEY_TURTLE_COUNT, ConfigManager.DEFAULT_TURTLE_COUNT)

    @Volatile
    private var cachedFishCount: Int = prefs.getInt(ConfigManager.KEY_FISH_COUNT, ConfigManager.DEFAULT_FISH_COUNT)

    @Volatile
    private var cachedMantaCount: Int = prefs.getInt(ConfigManager.KEY_MANTA_COUNT, ConfigManager.DEFAULT_MANTA_COUNT)

    @Volatile
    private var cachedJellyfishCount: Int = prefs.getInt(ConfigManager.KEY_JELLYFISH_COUNT, ConfigManager.DEFAULT_JELLYFISH_COUNT)

    @Volatile
    private var cachedPlantDensity: Int = prefs.getInt(ConfigManager.KEY_PLANT_DENSITY, ConfigManager.DEFAULT_PLANT_DENSITY)

    @Volatile
    private var cachedBubbleCount: Int = prefs.getInt(ConfigManager.KEY_BUBBLE_COUNT, ConfigManager.DEFAULT_BUBBLE_COUNT)

    @Volatile
    private var cachedDayNightCycle: Int = prefs.getInt(ConfigManager.KEY_DAY_NIGHT_CYCLE, ConfigManager.DEFAULT_DAY_NIGHT_CYCLE)

    @Volatile
    private var cachedCustomShallowColor: Int = prefs.getInt(ConfigManager.KEY_CUSTOM_SHALLOW_COLOR, ConfigManager.DEFAULT_CUSTOM_SHALLOW_COLOR)

    @Volatile
    private var cachedCustomDeepColor: Int = prefs.getInt(ConfigManager.KEY_CUSTOM_DEEP_COLOR, ConfigManager.DEFAULT_CUSTOM_DEEP_COLOR)

    @Volatile
    private var cachedSubmarineColor: Int = prefs.getInt(ConfigManager.KEY_SUBMARINE_COLOR, ConfigManager.DEFAULT_SUBMARINE_COLOR)

    fun getAcuarioTheme(): Int = cachedTheme
    fun setAcuarioTheme(theme: Int) {
        val coerced = theme.coerceIn(0, 5)
        cachedTheme = coerced
        putInt(ConfigManager.KEY_ACUARIO_THEME, coerced)
    }

    fun getTurtleCount(): Int = cachedTurtleCount
    fun setTurtleCount(count: Int) {
        val coerced = count.coerceIn(0, 5)
        cachedTurtleCount = coerced
        putInt(ConfigManager.KEY_TURTLE_COUNT, coerced)
    }

    fun getFishCount(): Int = cachedFishCount
    fun setFishCount(count: Int) {
        val coerced = count.coerceIn(0, 8)
        cachedFishCount = coerced
        putInt(ConfigManager.KEY_FISH_COUNT, coerced)
    }

    fun getMantaCount(): Int = cachedMantaCount
    fun setMantaCount(count: Int) {
        val coerced = count.coerceIn(0, 4)
        cachedMantaCount = coerced
        putInt(ConfigManager.KEY_MANTA_COUNT, coerced)
    }


    // Range matches AcuarioRenderer's kMaxJellyfish - keep both in sync.
    fun getJellyfishCount(): Int = cachedJellyfishCount
    fun setJellyfishCount(count: Int) {
        val coerced = count.coerceIn(0, 6)
        cachedJellyfishCount = coerced
        putInt(ConfigManager.KEY_JELLYFISH_COUNT, coerced)
    }

    // Range matches AcuarioRenderer's kMaxPlantDensity - keep both in sync.
    fun getPlantDensity(): Int = cachedPlantDensity
    fun setPlantDensity(count: Int) {
        val coerced = count.coerceIn(0, 24)
        cachedPlantDensity = coerced
        putInt(ConfigManager.KEY_PLANT_DENSITY, coerced)
    }

    // Range matches AcuarioRenderer's kMinAmbientBubbles/kMaxAmbientBubbles - keep both in sync.
    fun getBubbleCount(): Int = cachedBubbleCount
    fun setBubbleCount(count: Int) {
        val coerced = count.coerceIn(4, 45)
        cachedBubbleCount = coerced
        putInt(ConfigManager.KEY_BUBBLE_COUNT, coerced)
    }

    fun getDayNightCycleDuration(): Int = cachedDayNightCycle
    fun setDayNightCycleDuration(duration: Int) {
        val coerced = if (duration == 60 || duration == 180 || duration == 600 || duration == -1) duration else 180
        cachedDayNightCycle = coerced
        putInt(ConfigManager.KEY_DAY_NIGHT_CYCLE, coerced)
    }

    fun getCustomShallowColor(): Int = cachedCustomShallowColor
    fun setCustomShallowColor(color: Int) {
        cachedCustomShallowColor = color
        putInt(ConfigManager.KEY_CUSTOM_SHALLOW_COLOR, color)
    }

    fun getCustomDeepColor(): Int = cachedCustomDeepColor
    fun setCustomDeepColor(color: Int) {
        cachedCustomDeepColor = color
        putInt(ConfigManager.KEY_CUSTOM_DEEP_COLOR, color)
    }

    fun getSubmarineColor(): Int = cachedSubmarineColor
    fun setSubmarineColor(color: Int) {
        val coerced = color.coerceIn(0, 5)
        cachedSubmarineColor = coerced
        putInt(ConfigManager.KEY_SUBMARINE_COLOR, coerced)
    }

    override fun invalidate(key: String) {
        super.invalidate(key)
        when (key) {
            ConfigManager.KEY_ACUARIO_THEME -> {
                cachedTheme = prefs.getInt(ConfigManager.KEY_ACUARIO_THEME, ConfigManager.DEFAULT_ACUARIO_THEME)
            }
            ConfigManager.KEY_TURTLE_COUNT -> {
                cachedTurtleCount = prefs.getInt(ConfigManager.KEY_TURTLE_COUNT, ConfigManager.DEFAULT_TURTLE_COUNT)
            }
            ConfigManager.KEY_FISH_COUNT -> {
                cachedFishCount = prefs.getInt(ConfigManager.KEY_FISH_COUNT, ConfigManager.DEFAULT_FISH_COUNT)
            }
            ConfigManager.KEY_MANTA_COUNT -> {
                cachedMantaCount = prefs.getInt(ConfigManager.KEY_MANTA_COUNT, ConfigManager.DEFAULT_MANTA_COUNT)
            }
            ConfigManager.KEY_JELLYFISH_COUNT -> {
                cachedJellyfishCount = prefs.getInt(ConfigManager.KEY_JELLYFISH_COUNT, ConfigManager.DEFAULT_JELLYFISH_COUNT)
            }
            ConfigManager.KEY_PLANT_DENSITY -> {
                cachedPlantDensity = prefs.getInt(ConfigManager.KEY_PLANT_DENSITY, ConfigManager.DEFAULT_PLANT_DENSITY)
            }
            ConfigManager.KEY_BUBBLE_COUNT -> {
                cachedBubbleCount = prefs.getInt(ConfigManager.KEY_BUBBLE_COUNT, ConfigManager.DEFAULT_BUBBLE_COUNT)
            }
            ConfigManager.KEY_DAY_NIGHT_CYCLE -> {
                cachedDayNightCycle = prefs.getInt(ConfigManager.KEY_DAY_NIGHT_CYCLE, ConfigManager.DEFAULT_DAY_NIGHT_CYCLE)
            }
            ConfigManager.KEY_CUSTOM_SHALLOW_COLOR -> {
                cachedCustomShallowColor = prefs.getInt(ConfigManager.KEY_CUSTOM_SHALLOW_COLOR, ConfigManager.DEFAULT_CUSTOM_SHALLOW_COLOR)
            }
            ConfigManager.KEY_CUSTOM_DEEP_COLOR -> {
                cachedCustomDeepColor = prefs.getInt(ConfigManager.KEY_CUSTOM_DEEP_COLOR, ConfigManager.DEFAULT_CUSTOM_DEEP_COLOR)
            }
            ConfigManager.KEY_SUBMARINE_COLOR -> {
                cachedSubmarineColor = prefs.getInt(ConfigManager.KEY_SUBMARINE_COLOR, ConfigManager.DEFAULT_SUBMARINE_COLOR)
            }
        }
    }
}
