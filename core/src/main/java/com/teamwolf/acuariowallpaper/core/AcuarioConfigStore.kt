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
    fun setAcuarioTheme(theme: Int) { putInt(ConfigManager.KEY_ACUARIO_THEME, theme.coerceIn(0, 1)) }

    fun getTurtleCount(): Int = cachedInt(ConfigManager.KEY_TURTLE_COUNT, ConfigManager.DEFAULT_TURTLE_COUNT)
    fun setTurtleCount(count: Int) { putInt(ConfigManager.KEY_TURTLE_COUNT, count.coerceIn(0, 5)) }
}
