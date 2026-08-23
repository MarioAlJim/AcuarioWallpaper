package com.teamwolf.acuariowallpaper.core

import android.content.SharedPreferences

/**
 * Persisted settings for the Acuario effect (fish, bubbles, background, ...).
 *
 * Empty scaffold: add a `cachedInt`/`cachedBoolean` getter and a `coerceIn`-clamped
 * setter here per new knob, following the pattern in [CachedPrefStore], then expose
 * both through [ConfigProvider]/[ConfigManager].
 */
internal class AcuarioConfigStore(prefs: SharedPreferences) : CachedPrefStore(prefs)
