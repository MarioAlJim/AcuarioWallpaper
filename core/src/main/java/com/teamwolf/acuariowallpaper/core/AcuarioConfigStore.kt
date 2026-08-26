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
    private var cachedPlantDensity: Int = prefs.getInt(ConfigManager.KEY_PLANT_DENSITY, ConfigManager.DEFAULT_PLANT_DENSITY)

    @Volatile
    private var cachedBubbleCount: Int = prefs.getInt(ConfigManager.KEY_BUBBLE_COUNT, ConfigManager.DEFAULT_BUBBLE_COUNT)

    @Volatile
    private var cachedDayNightCycle: Int = prefs.getInt(ConfigManager.KEY_DAY_NIGHT_CYCLE, ConfigManager.DEFAULT_DAY_NIGHT_CYCLE)

    fun getAcuarioTheme(): Int = cachedTheme
    fun setAcuarioTheme(theme: Int) {
        val coerced = theme.coerceIn(0, 4)
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
            ConfigManager.KEY_PLANT_DENSITY -> {
                cachedPlantDensity = prefs.getInt(ConfigManager.KEY_PLANT_DENSITY, ConfigManager.DEFAULT_PLANT_DENSITY)
            }
            ConfigManager.KEY_BUBBLE_COUNT -> {
                cachedBubbleCount = prefs.getInt(ConfigManager.KEY_BUBBLE_COUNT, ConfigManager.DEFAULT_BUBBLE_COUNT)
            }
            ConfigManager.KEY_DAY_NIGHT_CYCLE -> {
                cachedDayNightCycle = prefs.getInt(ConfigManager.KEY_DAY_NIGHT_CYCLE, ConfigManager.DEFAULT_DAY_NIGHT_CYCLE)
            }
        }
    }
}
