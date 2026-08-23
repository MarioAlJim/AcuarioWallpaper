package com.teamwolf.acuariowallpaper.core

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(context: Context) : ConfigProvider {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val acuario = AcuarioConfigStore(prefs)

    // Each store memoizes its reads (see CachedPrefStore) so the render thread doesn't
    // hit SharedPreferences on every frame. Settings can be changed from a different
    // ConfigManager instance than the one the wallpaper engine is polling (the settings
    // screen and the wallpaper service each hold their own), so we listen for changes on
    // the underlying prefs object (shared per-process by Android) and drop the stale
    // cached value wherever it lives. A strong reference to the listener is kept here
    // because SharedPreferences only holds a weak reference to registered listeners.
    private val prefsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null) {
            acuario.invalidate(key)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
    }

    companion object {
        const val PREFS_NAME = "acuario_wallpaper_prefs"

        // Add KEY_/DEFAULT_ constants here as settings are introduced, following the
        // pattern used by the "wallpaper" reference project's ConfigManager.
    }

    // Delegate to `acuario` here as ConfigProvider grows getters/setters.
}
