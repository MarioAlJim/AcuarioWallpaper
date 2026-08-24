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
 * abierto) theme selector, the turtle count (0-5) selector, and the ambient bubble count
 * (Poco/Medio/Alto/Muy alto) selector - see fragment_acuario_settings.xml's comment for what to
 * add next as the effect grows more knobs. Every new effect should get its own selector here,
 * mirroring one of these three.
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
        setupFishCountCards(view)
        setupBubbleCountCards(view)
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

    private fun setupFishCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerFishCount)
        val options = (0..8).map { it.toString() }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getFishCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "🐠"
                    textSize = 22f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setFishCount(selectedIndex)
        }
    }

    // Unlike theme/turtle count, the card's index isn't the stored value directly - each tier
    // maps to an actual bubble count (kept in sync with AcuarioRenderer's
    // kMinAmbientBubbles/kMaxAmbientBubbles range).
    private fun setupBubbleCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerBubbleCount)
        val options = arrayOf(
            getString(R.string.bubble_density_poco),
            getString(R.string.bubble_density_medio),
            getString(R.string.bubble_density_alto),
            getString(R.string.bubble_density_muy_alto)
        )
        val values = intArrayOf(8, 18, 28, 42)
        val initialIndex = SettingsCardSelectorHelper.closestValueIndex(values, configManager.getBubbleCount())

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            initialIndex,
            previewFactory = {
                TextView(requireContext()).apply {
                    text = "🫧"
                    textSize = 22f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setBubbleCount(values[selectedIndex])
        }
    }
}
