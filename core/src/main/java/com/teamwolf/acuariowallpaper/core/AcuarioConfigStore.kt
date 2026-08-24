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
    private var cachedBubbleCount: Int = prefs.getInt(ConfigManager.KEY_BUBBLE_COUNT, ConfigManager.DEFAULT_BUBBLE_COUNT)

    fun getAcuarioTheme(): Int = cachedTheme
    fun setAcuarioTheme(theme: Int) {
        val coerced = theme.coerceIn(0, 1)
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

    // Range matches AcuarioRenderer's kMinAmbientBubbles/kMaxAmbientBubbles - keep both in sync.
    fun getBubbleCount(): Int = cachedBubbleCount
    fun setBubbleCount(count: Int) {
        val coerced = count.coerceIn(4, 45)
        cachedBubbleCount = coerced
        putInt(ConfigManager.KEY_BUBBLE_COUNT, coerced)
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
            ConfigManager.KEY_BUBBLE_COUNT -> {
                cachedBubbleCount = prefs.getInt(ConfigManager.KEY_BUBBLE_COUNT, ConfigManager.DEFAULT_BUBBLE_COUNT)
            }
        }
    }
}
