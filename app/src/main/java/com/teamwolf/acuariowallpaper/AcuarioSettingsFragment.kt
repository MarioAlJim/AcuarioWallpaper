package com.teamwolf.acuariowallpaper

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.teamwolf.acuariowallpaper.core.ConfigManager

/**
 * Settings screen fragment for the Acuario effect, styled according to Material 3
 * and aligned with the Sol y Luna design language.
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

        // 1. Setup Accordion Group
        SettingsAccordionHelper.setupAccordionGroup(
            view,
            SettingsAccordionHelper.AccordionSpec(
                R.id.headerAcuarioEnvironment,
                R.id.contentAcuarioEnvironment,
                R.id.dividerAcuarioEnvironment,
                R.id.arrowAcuarioEnvironment
            ),
            SettingsAccordionHelper.AccordionSpec(
                R.id.headerAcuarioFauna,
                R.id.contentAcuarioFauna,
                R.id.dividerAcuarioFauna,
                R.id.arrowAcuarioFauna
            ),
            SettingsAccordionHelper.AccordionSpec(
                R.id.headerAcuarioLandscape,
                R.id.contentAcuarioLandscape,
                R.id.dividerAcuarioLandscape,
                R.id.arrowAcuarioLandscape
            )
        )

        // Expand first accordion by default for immediate discoverability
        view.findViewById<View>(R.id.contentAcuarioEnvironment)?.visibility = View.VISIBLE
        view.findViewById<View>(R.id.dividerAcuarioEnvironment)?.visibility = View.VISIBLE
        view.findViewById<ImageView>(R.id.arrowAcuarioEnvironment)?.rotation = 180f

        // 2. Setup All Selectors
        setupThemeCards(view)
        setupCustomColorSelectors(view)
        setupDayNightCycleCards(view)
        setupBioluminescenceSwitch(view)

        setupRealisticModeSwitch(view)
        setupFishCountCards(view)
        setupTurtleCountCards(view)
        setupMantaCountCards(view)
        setupJellyfishCountCards(view)
        setupAxolotlCountCards(view)
        setupStarfishCountCards(view)
        setupSharkPresenceCards(view)
        setupSharkColorCards(view)

        setupPlantDensityCards(view)
        setupBubbleCountCards(view)
        setupSubmarineColorCards(view)
        setupChestColorCards(view)

        updateSummaries(view)
    }

    private fun updateSummaries(parent: View) {
        val summaryEnvironment = parent.findViewById<TextView>(R.id.summaryAcuarioEnvironment)
        val summaryFauna = parent.findViewById<TextView>(R.id.summaryAcuarioFauna)
        val summaryLandscape = parent.findViewById<TextView>(R.id.summaryAcuarioLandscape)

        val accentColor = String.format(
            "#%06X",
            0xFFFFFF and ContextCompat.getColor(requireContext(), R.color.settings_accent_text)
        )

        // Environment summary
        if (summaryEnvironment != null) {
            val themeNames = arrayOf(
                getString(R.string.acuario_theme_turquesa),
                getString(R.string.acuario_theme_azul_profundo),
                getString(R.string.acuario_theme_atardecer),
                getString(R.string.acuario_theme_abisal),
                getString(R.string.acuario_theme_arrecife),
                getString(R.string.acuario_theme_personalizado)
            )
            val themeName = themeNames.getOrElse(configManager.getAcuarioTheme()) { themeNames[0] }

            val cycleNames = arrayOf(
                getString(R.string.day_night_cycle_rapido),
                getString(R.string.day_night_cycle_normal),
                getString(R.string.day_night_cycle_lento),
                getString(R.string.day_night_cycle_real)
            )
            val cycleValues = intArrayOf(60, 180, 600, -1)
            val cycleIndex = SettingsCardSelectorHelper.closestValueIndex(cycleValues, configManager.getDayNightCycleDuration())
            val cycleText = cycleNames.getOrElse(cycleIndex) { cycleNames[1] }

            val bioStatus = if (configManager.getBioluminescenceEnabled()) {
                getString(R.string.touch_interaction_enabled)
            } else {
                getString(R.string.touch_interaction_disabled)
            }

            val textHtml = "<font color='$accentColor'><b>$themeName</b></font> • Ciclo: $cycleText • Brillo: $bioStatus"
            summaryEnvironment.text = Html.fromHtml(textHtml, Html.FROM_HTML_MODE_LEGACY)
        }

        // Fauna summary
        if (summaryFauna != null) {
            val fish = configManager.getFishCount()
            val turtles = configManager.getTurtleCount()
            val mantas = configManager.getMantaCount()
            val jelly = configManager.getJellyfishCount()
            val starfish = configManager.getStarfishCount()
            val sharkState = when (configManager.getSharkPresence()) {
                0 -> "Inactivo"
                1 -> "Aleatorio"
                else -> "Activo"
            }
            val realisticText = if (configManager.getRealisticModeEnabled()) " • <font color='$accentColor'><b>Ecosistema Realista</b></font>" else ""

            val textHtml = "Peces: <font color='$accentColor'><b>$fish</b></font> • Tortugas: <b>$turtles</b> • Mantas: <b>$mantas</b> • Estrellas: <b>$starfish</b> • Tiburón: <b>$sharkState</b>$realisticText"
            summaryFauna.text = Html.fromHtml(textHtml, Html.FROM_HTML_MODE_LEGACY)
        }

        // Landscape summary
        if (summaryLandscape != null) {
            val plantOptions = arrayOf(
                getString(R.string.plant_density_poca),
                getString(R.string.plant_density_media),
                getString(R.string.plant_density_alta),
                getString(R.string.plant_density_muy_alta)
            )
            val plantValues = intArrayOf(2, 8, 14, 22)
            val plantIdx = SettingsCardSelectorHelper.closestValueIndex(plantValues, configManager.getPlantDensity())
            val plantText = plantOptions.getOrElse(plantIdx) { plantOptions[1] }

            val bubbleOptions = arrayOf(
                getString(R.string.bubble_density_poco),
                getString(R.string.bubble_density_medio),
                getString(R.string.bubble_density_alto),
                getString(R.string.bubble_density_muy_alto)
            )
            val bubbleValues = intArrayOf(8, 18, 28, 42)
            val bubbleIdx = SettingsCardSelectorHelper.closestValueIndex(bubbleValues, configManager.getBubbleCount())
            val bubbleText = bubbleOptions.getOrElse(bubbleIdx) { bubbleOptions[1] }

            val subColors = arrayOf(
                getString(R.string.submarine_color_amarillo),
                getString(R.string.submarine_color_rojo),
                getString(R.string.submarine_color_azul),
                getString(R.string.submarine_color_verde),
                getString(R.string.submarine_color_rosa),
                getString(R.string.submarine_color_naranja)
            )
            val subText = subColors.getOrElse(configManager.getSubmarineColor()) { subColors[0] }

            val textHtml = "Plantas: <font color='$accentColor'><b>$plantText</b></font> • Burbujas: <b>$bubbleText</b> • Submarino: <b>$subText</b>"
            summaryLandscape.text = Html.fromHtml(textHtml, Html.FROM_HTML_MODE_LEGACY)
        }
    }

    private fun setupThemeCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerAcuarioTheme)
        val options = arrayOf(
            getString(R.string.acuario_theme_turquesa),
            getString(R.string.acuario_theme_azul_profundo),
            getString(R.string.acuario_theme_atardecer),
            getString(R.string.acuario_theme_abisal),
            getString(R.string.acuario_theme_arrecife),
            getString(R.string.acuario_theme_personalizado)
        )

        val shallowColors = intArrayOf(
            0xFF1A7066.toInt(), // Turquesa
            0xFF0D578C.toInt(), // Azul Profundo
            0xFFB34D66.toInt(), // Atardecer Violeta
            0xFF260D40.toInt(), // Fosa Abisal
            0xFF0DA699.toInt(), // Arrecife Coral
            configManager.getCustomShallowColor() // Personalizado
        )
        val deepColors = intArrayOf(
            0xFF031821.toInt(),
            0xFF020B21.toInt(),
            0xFF140A26.toInt(),
            0xFF02020A.toInt(),
            0xFF05142E.toInt(),
            configManager.getCustomDeepColor() // Personalizado
        )

        val layoutCustomColors = parent.findViewById<LinearLayout>(R.id.layoutCustomColors)

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
                        cornerRadius = 8f * resources.displayMetrics.density
                    }
                }
            }
        ) { selectedIndex ->
            configManager.setAcuarioTheme(selectedIndex)
            layoutCustomColors.visibility = if (selectedIndex == 5) View.VISIBLE else View.GONE
            updateSummaries(parent)
        }
    }

    private fun setupCustomColorSelectors(parent: View) {
        val layoutCustomColors = parent.findViewById<LinearLayout>(R.id.layoutCustomColors)
        layoutCustomColors.visibility = if (configManager.getAcuarioTheme() == 5) View.VISIBLE else View.GONE

        // 1. Color Superior (Superficie)
        val containerShallow = parent.findViewById<LinearLayout>(R.id.containerCustomShallowColor)
        val shallowColors = intArrayOf(
            0xFF0DA699.toInt(), // Turquoise
            0xFF0D578C.toInt(), // Blue
            0xFFB34D66.toInt(), // Pink/Coral
            0xFF260D40.toInt(), // Deep Purple
            0xFF1A7066.toInt(), // Teal
            0xFFE2583E.toInt(), // Sunset Red
            0xFF2ECC71.toInt(), // Emerald Green
            0xFF9B59B6.toInt()  // Violet
        )
        val shallowOptions = arrayOf(
            "Turquesa", "Azul", "Coral", "Púrpura", "Cian", "Naranja", "Verde", "Violeta"
        )
        val initialShallowIndex = shallowColors.indexOf(configManager.getCustomShallowColor()).coerceAtLeast(0)

        SettingsCardSelectorHelper.populate(
            requireContext(),
            containerShallow,
            shallowOptions,
            initialShallowIndex,
            title = getString(R.string.settings_shallow_color),
            subtitle = "Toca para abrir el mezclador de color avanzado",
            previewFactory = { index ->
                View(requireContext()).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(shallowColors[index])
                    }
                }
            }
        ) { selectedIndex ->
            // Open HSV color mixer with selected preset
            SettingsBottomSheetHelper.showColorMixerSheet(
                requireContext(),
                shallowColors[selectedIndex]
            ) { customColor ->
                configManager.setCustomShallowColor(customColor)
                setupThemeCards(parent)
                setupCustomColorSelectors(parent)
                updateSummaries(parent)
            }
        }

        // 2. Color Inferior (Fondo)
        val containerDeep = parent.findViewById<LinearLayout>(R.id.containerCustomDeepColor)
        val deepColors = intArrayOf(
            0xFF031821.toInt(), // Dark Turquoise
            0xFF020B21.toInt(), // Dark Blue
            0xFF140A26.toInt(), // Dark Purple
            0xFF02020A.toInt(), // Pitch Black
            0xFF05142E.toInt(), // Ocean Deep
            0xFF121212.toInt(), // Midnight Grey
            0xFF0A2F2D.toInt(), // Deep Teal
            0xFF1A1B35.toInt()  // Dark Indigo
        )
        val deepOptions = arrayOf(
            "Marina", "Marino", "Índigo", "Negro", "Abisal", "Gris", "Teal Oscuro", "Sombra"
        )
        val initialDeepIndex = deepColors.indexOf(configManager.getCustomDeepColor()).coerceAtLeast(0)

        SettingsCardSelectorHelper.populate(
            requireContext(),
            containerDeep,
            deepOptions,
            initialDeepIndex,
            title = getString(R.string.settings_deep_color),
            subtitle = "Toca para abrir el mezclador de color avanzado",
            previewFactory = { index ->
                View(requireContext()).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(deepColors[index])
                    }
                }
            }
        ) { selectedIndex ->
            SettingsBottomSheetHelper.showColorMixerSheet(
                requireContext(),
                deepColors[selectedIndex]
            ) { customColor ->
                configManager.setCustomDeepColor(customColor)
                setupThemeCards(parent)
                setupCustomColorSelectors(parent)
                updateSummaries(parent)
            }
        }
    }

    private fun setupTurtleCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerTurtleCount)
        val options = (0..5).map { "$it tortugas" }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getTurtleCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "🐢"
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setTurtleCount(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupFishCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerFishCount)
        val options = (0..8).map { "$it peces" }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getFishCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "🐠"
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setFishCount(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupMantaCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerMantaCount)
        val options = (0..4).map { "$it mantas" }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getMantaCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "🌊"
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setMantaCount(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupJellyfishCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerJellyfishCount)
        val options = (0..6).map { "$it medusas" }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getJellyfishCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "🪼"
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setJellyfishCount(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupAxolotlCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerAxolotlCount)
        val options = (0..4).map { "$it ajolotes" }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getAxolotlCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "🦎"
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setAxolotlCount(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupStarfishCountCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerStarfishCount) ?: return
        val options = (0..3).map { if (it == 1) "1 estrella" else "$it estrellas" }.toTypedArray()

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getStarfishCount(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = if (index == 0) "🚫" else "⭐"
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setStarfishCount(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupPlantDensityCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerPlantDensity)
        val options = arrayOf(
            getString(R.string.plant_density_poca),
            getString(R.string.plant_density_media),
            getString(R.string.plant_density_alta),
            getString(R.string.plant_density_muy_alta)
        )
        val values = intArrayOf(2, 8, 14, 22)
        val initialIndex = SettingsCardSelectorHelper.closestValueIndex(values, configManager.getPlantDensity())

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            initialIndex,
            previewFactory = {
                TextView(requireContext()).apply {
                    text = "🌿"
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setPlantDensity(values[selectedIndex])
            updateSummaries(parent)
        }
    }

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
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setBubbleCount(values[selectedIndex])
            updateSummaries(parent)
        }
    }

    private fun setupDayNightCycleCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerDayNightCycle)
        val options = arrayOf(
            getString(R.string.day_night_cycle_rapido),
            getString(R.string.day_night_cycle_normal),
            getString(R.string.day_night_cycle_lento),
            getString(R.string.day_night_cycle_real)
        )
        val values = intArrayOf(60, 180, 600, -1)
        val initialIndex = SettingsCardSelectorHelper.closestValueIndex(values, configManager.getDayNightCycleDuration())

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            initialIndex,
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = when (index) {
                        0 -> "⚡"
                        1 -> "⏰"
                        2 -> "🐢"
                        else -> "🌍"
                    }
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setDayNightCycleDuration(values[selectedIndex])
            updateSummaries(parent)
        }
    }

    private fun setupSubmarineColorCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerSubmarineColor)
        val options = arrayOf(
            getString(R.string.submarine_color_amarillo),
            getString(R.string.submarine_color_rojo),
            getString(R.string.submarine_color_azul),
            getString(R.string.submarine_color_verde),
            getString(R.string.submarine_color_rosa),
            getString(R.string.submarine_color_naranja)
        )
        val colors = intArrayOf(
            0xFFF2CC1A.toInt(),
            0xFFD9261A.toInt(),
            0xFF2980B9.toInt(),
            0xFF2ECC71.toInt(),
            0xFFE91E63.toInt(),
            0xFFE67E22.toInt()
        )

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getSubmarineColor(),
            previewFactory = { index ->
                View(requireContext()).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(colors[index])
                    }
                }
            }
        ) { selectedIndex ->
            configManager.setSubmarineColor(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupChestColorCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerChestColor) ?: return
        val options = arrayOf(
            getString(R.string.chest_color_pirate),
            getString(R.string.chest_color_royal),
            getString(R.string.chest_color_ancient)
        )
        val colors = intArrayOf(
            0xFF8B4513.toInt(),
            0xFF3B1E08.toInt(),
            0xFF2E5A44.toInt()
        )

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getChestColor(),
            previewFactory = { index ->
                View(requireContext()).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(colors[index])
                    }
                }
            }
        ) { selectedIndex ->
            configManager.setChestColor(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupSharkPresenceCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerSharkPresence)
        val options = arrayOf(
            getString(R.string.shark_presence_disabled),
            getString(R.string.shark_presence_sometimes),
            getString(R.string.shark_presence_always)
        )

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getSharkPresence(),
            previewFactory = { index ->
                TextView(requireContext()).apply {
                    text = when (index) {
                        0 -> "🚫"
                        1 -> "🦈"
                        else -> "🌊"
                    }
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
            }
        ) { selectedIndex ->
            configManager.setSharkPresence(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupSharkColorCards(parent: View) {
        val container = parent.findViewById<LinearLayout>(R.id.containerSharkColor)
        val options = arrayOf(
            getString(R.string.shark_color_gray),
            getString(R.string.shark_color_blue),
            getString(R.string.shark_color_white),
            getString(R.string.shark_color_gold)
        )
        val colors = intArrayOf(
            0xFF737B85.toInt(),
            0xFF38598C.toInt(),
            0xFFEBE0E0.toInt(),
            0xFFE6B840.toInt()
        )

        SettingsCardSelectorHelper.populate(
            requireContext(),
            container,
            options,
            configManager.getSharkColor(),
            previewFactory = { index ->
                View(requireContext()).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(colors[index])
                    }
                }
            }
        ) { selectedIndex ->
            configManager.setSharkColor(selectedIndex)
            updateSummaries(parent)
        }
    }

    private fun setupBioluminescenceSwitch(parent: View) {
        val row = parent.findViewById<View>(R.id.rowBioluminescence) ?: return
        val switch = parent.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBioluminescence)
        val statusView = parent.findViewById<TextView>(R.id.statusBioluminescence)
        val iconView = parent.findViewById<ImageView>(R.id.iconBioluminescence)
        val cardIcon = parent.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardIconBioluminescence)
        val activeColor = 0xFF00E5FF.toInt()

        SettingsSwitchHelper.bind(
            row = row,
            switch = switch,
            isChecked = configManager.getBioluminescenceEnabled(),
            statusView = statusView,
            iconView = iconView,
            iconCard = cardIcon,
            activeColor = activeColor
        ) { isChecked ->
            configManager.setBioluminescenceEnabled(isChecked)
            updateSummaries(parent)
        }
    }

    private fun setupRealisticModeSwitch(parent: View) {
        val row = parent.findViewById<View>(R.id.rowRealisticMode) ?: return
        val switch = parent.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchRealisticMode)
        val statusView = parent.findViewById<TextView>(R.id.statusRealisticMode)
        val iconView = parent.findViewById<ImageView>(R.id.iconRealisticMode)
        val cardIcon = parent.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardIconRealisticMode)
        val activeColor = 0xFFFF5722.toInt()

        SettingsSwitchHelper.bind(
            row = row,
            switch = switch,
            isChecked = configManager.getRealisticModeEnabled(),
            statusView = statusView,
            iconView = iconView,
            iconCard = cardIcon,
            activeColor = activeColor
        ) { isChecked ->
            configManager.setRealisticModeEnabled(isChecked)
            updateSummaries(parent)
        }
    }
}
