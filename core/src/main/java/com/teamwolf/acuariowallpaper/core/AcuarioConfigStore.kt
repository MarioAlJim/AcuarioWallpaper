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
}
