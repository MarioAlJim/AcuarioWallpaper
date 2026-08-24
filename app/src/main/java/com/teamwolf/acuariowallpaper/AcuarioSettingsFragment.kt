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
 * abierto) theme selector and the turtle count (0-5) selector - see
 * fragment_acuario_settings.xml's comment for what to add next as the effect grows more knobs.
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
        setupTurtleCountCards(view)
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

    // Index == turtle count (0-5), so no separate value-mapping array is needed - the card's
    // position in the row is the setting's value.
    private fun setupTurtleCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerTurtleCount)
        val options = (0..5).map { it.toString() }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getTurtleCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "🐢"
                    textSize = 22f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setTurtleCount(selectedIndex)
        }
    }
}
