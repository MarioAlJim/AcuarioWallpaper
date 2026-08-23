package com.teamwolf.acuariowallpaper

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.teamwolf.acuariowallpaper.core.ConfigManager

/**
 * Settings screen for the Acuario effect. Currently wires the "Ambiente" (Acuario / Mar
 * abierto) theme selector - see fragment_acuario_settings.xml's comment for what to add next
 * as the effect grows more knobs.
 */
class AcuarioSettingsFragment : Fragment() {

    private lateinit var configManager: ConfigManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_acuario_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configManager = (requireActivity() as WallpaperSettingsActivity).configManager

        setupThemeCards(view)
    }

    private fun setupThemeCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerAcuarioTheme)
        val options = arrayOf(
            getString(R.string.acuario_theme_acuario),
            getString(R.string.acuario_theme_mar)
        )
        val previewEmoji = arrayOf("🐠", "🌊")

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getAcuarioTheme(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = previewEmoji[index]
                    textSize = 22f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setAcuarioTheme(selectedIndex)
        }
    }
}
