package com.teamwolf.acuariowallpaper

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks whether the first-run onboarding tutorial (see [TutorialOverlay]) has already been
 * shown, so it only runs automatically once. The user can always replay it manually from the
 * "Ver tutorial" button in the help dialog regardless of this flag.
 */
class TutorialPreference(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeenTutorial(): Boolean = prefs.getBoolean(KEY_SEEN, false)

    fun setSeenTutorial(seen: Boolean) {
        prefs.edit().putBoolean(KEY_SEEN, seen).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_ui_prefs"
        private const val KEY_SEEN = "onboarding_tutorial_seen"
    }
}
