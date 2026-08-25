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

        const val KEY_ACUARIO_THEME = "acuario_theme"
        const val DEFAULT_ACUARIO_THEME = 0

        const val KEY_TURTLE_COUNT = "turtle_count"
        const val DEFAULT_TURTLE_COUNT = 1

        const val KEY_FISH_COUNT = "fish_count"
        const val DEFAULT_FISH_COUNT = 3

        const val KEY_MANTA_COUNT = "manta_count"
        const val DEFAULT_MANTA_COUNT = 1

        const val KEY_BUBBLE_COUNT = "bubble_count"
        const val DEFAULT_BUBBLE_COUNT = 28

        // Add more KEY_/DEFAULT_ constants here as settings are introduced, following the
        // pattern used by the "wallpaper" reference project's ConfigManager. Every new visual
        // effect should get its own knob here (count/density and/or an enable toggle at
        // minimum) rather than shipping with hardcoded constants only.
    }

    override fun getAcuarioTheme(): Int = acuario.getAcuarioTheme()
    fun setAcuarioTheme(theme: Int) = acuario.setAcuarioTheme(theme)

    override fun getTurtleCount(): Int = acuario.getTurtleCount()
    fun setTurtleCount(count: Int) = acuario.setTurtleCount(count)

    override fun getFishCount(): Int = acuario.getFishCount()
    fun setFishCount(count: Int) = acuario.setFishCount(count)

    override fun getMantaCount(): Int = acuario.getMantaCount()
    fun setMantaCount(count: Int) = acuario.setMantaCount(count)

    override fun getBubbleCount(): Int = acuario.getBubbleCount()
    fun setBubbleCount(count: Int) = acuario.setBubbleCount(count)
}
