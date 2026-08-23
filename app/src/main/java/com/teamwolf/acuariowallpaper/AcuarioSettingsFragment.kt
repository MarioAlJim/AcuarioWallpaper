package com.teamwolf.acuariowallpaper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * Placeholder settings screen for the Acuario effect. Once the effect's configurable
 * knobs are designed, replace fragment_acuario_settings.xml's content with card
 * selectors/sliders and wire them up here — see SunnySettingsFragment in the
 * "wallpaper" reference project for the established pattern (card selection helpers,
 * ConfigManager read/write, live summaries).
 */
class AcuarioSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_acuario_settings, container, false)
    }
}
