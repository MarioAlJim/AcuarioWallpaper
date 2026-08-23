package com.teamwolf.acuariowallpaper

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the user's preferred UI theme (Automatic / Light / Dark) for the settings screen.
 *
 * This is purely a presentation preference for [WallpaperSettingsActivity] and its fragments,
 * unrelated to the wallpaper rendering configuration stored via ConfigManager/ConfigProvider.
 */
class SettingsThemePreference(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(): Int {
        return prefs.getInt(KEY_THEME_MODE, MODE_AUTO)
    }

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_ui_prefs"
        private const val KEY_THEME_MODE = "theme_mode"

        const val MODE_AUTO = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
    }
}
