package com.teamwolf.acuariowallpaper.core

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for [SharedPreferences]-backed config stores.
 *
 * The wallpaper renderer polls config getters every frame (up to 60x/sec) on the
 * render thread. Reading straight from [SharedPreferences] on every call means
 * a synchronized map lookup per call, forever, for values that in practice only
 * change when the user touches the settings screen. This base class memoizes
 * each key's value in memory and only re-reads [prefs] after [invalidate] is
 * called for that key (wired up by [ConfigManager] via a
 * [SharedPreferences.OnSharedPreferenceChangeListener]), so repeated reads on
 * the render hot path become a plain map lookup instead of a prefs read.
 */
internal abstract class CachedPrefStore(protected val prefs: SharedPreferences) {
    private val cache = ConcurrentHashMap<String, Any>()

    protected fun cachedInt(key: String, default: Int): Int =
        cache.getOrPut(key) { prefs.getInt(key, default) } as Int

    protected fun cachedBoolean(key: String, default: Boolean): Boolean =
        cache.getOrPut(key) { prefs.getBoolean(key, default) } as Boolean

    protected fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        cache[key] = value
    }

    protected fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        cache[key] = value
    }

    /** Drops the cached value for [key], if any, so the next read re-fetches from [prefs]. */
    fun invalidate(key: String) {
        cache.remove(key)
    }
}
