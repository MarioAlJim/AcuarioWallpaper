package com.teamwolf.acuariowallpaper

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.teamwolf.acuariowallpaper.core.ConfigManager

/**
 * Settings screen for the Acuario effect. Currently wires the "Ambiente" (Acuario / Mar
 * abierto) theme selector, the turtle/fish/manta ray count selectors, the plant (vegetation)
 * density selector, and the ambient bubble count (Poco/Medio/Alto/Muy alto) selector - see
 * fragment_acuario_settings.xml's comment for what to add next as the effect grows more knobs.
 * Every new effect should get its own selector here, mirroring one of these.
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
        setupMantaCountCards(view)
        setupPlantDensityCards(view)
        setupBubbleCountCards(view)
    }

    private fun setupThemeCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerAcuarioTheme)
        val options = arrayOf(
            getString(R.string.acuario_theme_turquesa),
            getString(R.string.acuario_theme_azul_profundo),
            getString(R.string.acuario_theme_atardecer),
            getString(R.string.acuario_theme_abisal),
            getString(R.string.acuario_theme_arrecife)
        )

        val shallowColors = intArrayOf(
            0xFF1A7066.toInt(), // Turquesa
            0xFF0D578C.toInt(), // Azul Profundo
            0xFFB34D66.toInt(), // Atardecer Violeta
            0xFF260D40.toInt(), // Fosa Abisal
            0xFF0DA699.toInt()  // Arrecife Coral
        )
        val deepColors = intArrayOf(
            0xFF031821.toInt(),
            0xFF020B21.toInt(),
            0xFF140A26.toInt(),
            0xFF02020A.toInt(),
            0xFF05142E.toInt()
        )

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getAcuarioTheme(),
            previewFactory = { index ->
                View(requireContext()).apply {
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(shallowColors[index], deepColors[index])
                    ).apply {
                        cornerRadius = 6f * resources.displayMetrics.density
                    }
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

    // Index == manta count (0-4), same as turtle/fish count above. No dedicated manta ray emoji
    // exists in Unicode, so the UFO glyph stands in - it's the closest common emoji to a wide,
    // flat, wing-tipped silhouette.
    private fun setupMantaCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerMantaCount)
        val options = (0..4).map { it.toString() }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getMantaCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "🛸"
                    textSize = 22f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setMantaCount(selectedIndex)
        }
    }

    // Like bubble density below, each tier maps to an actual plant count (kept in sync with
    // AcuarioRenderer's kMaxPlantDensity) rather than storing the card index directly.
    private fun setupPlantDensityCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerPlantDensity)
        val options = arrayOf(
            getString(R.string.plant_density_poca),
            getString(R.string.plant_density_media),
            getString(R.string.plant_density_alta),
            getString(R.string.plant_density_muy_alta)
        )
        val values = intArrayOf(0, 6, 12, 20)
        val initialIndex = SettingsCardSelectorHelper.closestValueIndex(values, configManager.getPlantDensity())

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            initialIndex,
            previewFactory = {
                TextView(requireContext()).apply {
                    text = "🌿"
                    textSize = 22f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setPlantDensity(values[selectedIndex])
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
