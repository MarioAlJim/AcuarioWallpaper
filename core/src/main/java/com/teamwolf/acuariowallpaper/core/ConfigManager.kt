package com.teamwolf.acuariowallpaper.core

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(private val context: Context) : ConfigProvider {
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
        } else {
            acuario.invalidateAll()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
    }

    fun reload() {
        acuario.invalidateAll()
    }

    fun notifySettingsChanged(key: String? = null) {
        acuario.invalidateAll()
        try {
            val intent = android.content.Intent(ACTION_SETTINGS_CHANGED).apply {
                setPackage(context.packageName)
                if (key != null) putExtra("key", key)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val PREFS_NAME = "acuario_wallpaper_prefs"
        const val ACTION_SETTINGS_CHANGED = "com.teamwolf.acuariowallpaper.ACTION_SETTINGS_CHANGED"

        const val KEY_ACUARIO_THEME = "acuario_theme"
        const val DEFAULT_ACUARIO_THEME = 0

        const val KEY_TURTLE_COUNT = "turtle_count"
        const val DEFAULT_TURTLE_COUNT = 1

        const val KEY_FISH_COUNT = "fish_count"
        const val DEFAULT_FISH_COUNT = 3

        const val KEY_MANTA_COUNT = "manta_count"
        const val DEFAULT_MANTA_COUNT = 1


        const val KEY_JELLYFISH_COUNT = "jellyfish_count"
        const val DEFAULT_JELLYFISH_COUNT = 2

        const val KEY_AXOLOTL_COUNT = "axolotl_count"
        const val DEFAULT_AXOLOTL_COUNT = 2

        const val KEY_SHARK_PRESENCE = "shark_presence"
        const val DEFAULT_SHARK_PRESENCE = 1 // 1 = Sometimes (Random)

        const val KEY_SHARK_COLOR = "shark_color"
        const val DEFAULT_SHARK_COLOR = 0 // 0 = Gray


        const val KEY_PLANT_DENSITY = "plant_density"
        const val DEFAULT_PLANT_DENSITY = 6 // "Media" tier - see AcuarioSettingsFragment's values array

        const val KEY_BUBBLE_COUNT = "bubble_count"
        const val DEFAULT_BUBBLE_COUNT = 28

        const val KEY_DAY_NIGHT_CYCLE = "day_night_cycle"
        const val DEFAULT_DAY_NIGHT_CYCLE = 180 // Default is 3 minutes (180s)

        const val KEY_CUSTOM_SHALLOW_COLOR = "custom_shallow_color"
        const val DEFAULT_CUSTOM_SHALLOW_COLOR = 0xFF0D578C.toInt()

        const val KEY_CUSTOM_DEEP_COLOR = "custom_deep_color"
        const val DEFAULT_CUSTOM_DEEP_COLOR = 0xFF020B21.toInt()

        const val KEY_SUBMARINE_COLOR = "submarine_color"
        const val DEFAULT_SUBMARINE_COLOR = 0

        const val KEY_GYRO_ENABLED = "gyro_enabled"
        const val DEFAULT_GYRO_ENABLED = true
    }

    override fun getAcuarioTheme(): Int = acuario.getAcuarioTheme()
    fun setAcuarioTheme(theme: Int) {
        acuario.setAcuarioTheme(theme)
        notifySettingsChanged(KEY_ACUARIO_THEME)
    }

    override fun getTurtleCount(): Int = acuario.getTurtleCount()
    fun setTurtleCount(count: Int) {
        acuario.setTurtleCount(count)
        notifySettingsChanged(KEY_TURTLE_COUNT)
    }

    override fun getFishCount(): Int = acuario.getFishCount()
    fun setFishCount(count: Int) {
        acuario.setFishCount(count)
        notifySettingsChanged(KEY_FISH_COUNT)
    }

    override fun getMantaCount(): Int = acuario.getMantaCount()
    fun setMantaCount(count: Int) {
        acuario.setMantaCount(count)
        notifySettingsChanged(KEY_MANTA_COUNT)
    }

    override fun getJellyfishCount(): Int = acuario.getJellyfishCount()
    fun setJellyfishCount(count: Int) {
        acuario.setJellyfishCount(count)
        notifySettingsChanged(KEY_JELLYFISH_COUNT)
    }

    override fun getAxolotlCount(): Int = acuario.getAxolotlCount()
    fun setAxolotlCount(count: Int) {
        acuario.setAxolotlCount(count)
        notifySettingsChanged(KEY_AXOLOTL_COUNT)
    }

    override fun getSharkPresence(): Int = acuario.getSharkPresence()
    fun setSharkPresence(presence: Int) {
        acuario.setSharkPresence(presence)
        notifySettingsChanged(KEY_SHARK_PRESENCE)
    }

    override fun getSharkColor(): Int = acuario.getSharkColor()
    fun setSharkColor(color: Int) {
        acuario.setSharkColor(color)
        notifySettingsChanged(KEY_SHARK_COLOR)
    }

    override fun getPlantDensity(): Int = acuario.getPlantDensity()
    fun setPlantDensity(count: Int) {
        acuario.setPlantDensity(count)
        notifySettingsChanged(KEY_PLANT_DENSITY)
    }

    override fun getBubbleCount(): Int = acuario.getBubbleCount()
    fun setBubbleCount(count: Int) {
        acuario.setBubbleCount(count)
        notifySettingsChanged(KEY_BUBBLE_COUNT)
    }

    override fun getDayNightCycleDuration(): Int = acuario.getDayNightCycleDuration()
    fun setDayNightCycleDuration(duration: Int) {
        acuario.setDayNightCycleDuration(duration)
        notifySettingsChanged(KEY_DAY_NIGHT_CYCLE)
    }

    override fun getCustomShallowColor(): Int = acuario.getCustomShallowColor()
    fun setCustomShallowColor(color: Int) {
        acuario.setCustomShallowColor(color)
        notifySettingsChanged(KEY_CUSTOM_SHALLOW_COLOR)
    }

    override fun getCustomDeepColor(): Int = acuario.getCustomDeepColor()
    fun setCustomDeepColor(color: Int) {
        acuario.setCustomDeepColor(color)
        notifySettingsChanged(KEY_CUSTOM_DEEP_COLOR)
    }

    override fun getSubmarineColor(): Int = acuario.getSubmarineColor()
    fun setSubmarineColor(color: Int) {
        acuario.setSubmarineColor(color)
        notifySettingsChanged(KEY_SUBMARINE_COLOR)
    }

    override fun isGyroEnabled(): Boolean = acuario.isGyroEnabled()
    fun setGyroEnabled(enabled: Boolean) {
        acuario.setGyroEnabled(enabled)
        notifySettingsChanged(KEY_GYRO_ENABLED)
    }
}
