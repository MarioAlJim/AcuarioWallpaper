package com.teamwolf.acuariowallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.card.MaterialCardView
import com.teamwolf.acuariowallpaper.core.ConfigManager

class WallpaperSettingsActivity : AppCompatActivity() {

    lateinit var configManager: ConfigManager
        private set

    private lateinit var themePreference: SettingsThemePreference
    private lateinit var tutorialPreference: TutorialPreference
    private val tutorialOverlay: TutorialOverlay by lazy { TutorialOverlay(this) }

    private lateinit var previewController: LivePreviewController
    private lateinit var previewSummary: TextView
    private lateinit var settingsPrefs: SharedPreferences

    // Refreshes the preview card's summary whenever any setting changes. Kept as a field because
    // SharedPreferences only holds a weak reference to registered listeners.
    private val previewSummaryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updatePreviewSummary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themePreference = SettingsThemePreference(this)
        AppCompatDelegate.setDefaultNightMode(nightModeFor(themePreference.getThemeMode()))

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Edge-to-Edge handling
        val rootContent = findViewById<View>(R.id.settingsRootContent)
        val buttonContainer = findViewById<View>(R.id.buttonContainer)
        val basePaddingTopPx = rootContent.paddingTop
        val basePaddingBottomPx = buttonContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = basePaddingTopPx + bars.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(buttonContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = basePaddingBottomPx + bars.bottom)
            insets
        }

        supportActionBar?.hide()

        configManager = ConfigManager(this)
        tutorialPreference = TutorialPreference(this)

        setupThemeModeControl()
        setupLivePreview()

        findViewById<View>(R.id.buttonHeaderHelp)?.setOnClickListener {
            showHelpDialog()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_fragment_container, AcuarioSettingsFragment())
                .commit()
        }

        title = getString(R.string.settings_title)

        if (!tutorialPreference.hasSeenTutorial()) {
            tutorialPreference.setSeenTutorial(true)
            supportFragmentManager.executePendingTransactions()
            findViewById<View>(R.id.settings_fragment_container).post {
                startTutorial()
            }
        }

        val buttonApply = findViewById<Button>(R.id.buttonApplyWallpaper)
        buttonApply.setOnClickListener {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@WallpaperSettingsActivity, AcuarioWallpaperService::class.java)
                )
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        if (::settingsPrefs.isInitialized) {
            settingsPrefs.unregisterOnSharedPreferenceChangeListener(previewSummaryListener)
        }
        super.onDestroy()
    }

    private fun setupLivePreview() {
        previewController = LivePreviewController(findViewById<SurfaceView>(R.id.previewSurface), configManager, this)
        previewSummary = findViewById(R.id.previewSummary)
        findViewById<View>(R.id.buttonPreviewBubbles).setOnClickListener {
            previewController.triggerBubbleStorm()
        }
        settingsPrefs = getSharedPreferences(ConfigManager.PREFS_NAME, MODE_PRIVATE)
        settingsPrefs.registerOnSharedPreferenceChangeListener(previewSummaryListener)
        updatePreviewSummary()
    }

    /** Two short lines under the preview title: water theme, then creature count + shark mode. */
    private fun updatePreviewSummary() {
        val themeNames = intArrayOf(
            R.string.acuario_theme_turquesa,
            R.string.acuario_theme_azul_profundo,
            R.string.acuario_theme_atardecer,
            R.string.acuario_theme_abisal,
            R.string.acuario_theme_arrecife,
            R.string.acuario_theme_personalizado
        )
        val theme = getString(themeNames.getOrElse(configManager.getAcuarioTheme()) { themeNames[0] })
        val creatures = configManager.getFishCount() + configManager.getTurtleCount() +
            configManager.getMantaCount() + configManager.getJellyfishCount() +
            configManager.getAxolotlCount() + configManager.getStarfishCount()
        val shark = getString(
            when (configManager.getSharkPresence()) {
                0 -> R.string.preview_shark_off
                1 -> R.string.preview_shark_random
                else -> R.string.preview_shark_always
            }
        )
        previewSummary.text = "$theme\n${getString(R.string.preview_summary_creatures, creatures)} · $shark"
    }

    fun showHelpDialog() {
        SettingsBottomSheetHelper.showHelpSheet(this) {
            startTutorial()
        }
    }

    private fun setupThemeModeControl() {
        val cards = listOf(
            findViewById<MaterialCardView>(R.id.cardThemeAuto),
            findViewById<MaterialCardView>(R.id.cardThemeLight),
            findViewById<MaterialCardView>(R.id.cardThemeDark)
        )
        val texts = listOf(
            findViewById<TextView>(R.id.textThemeAuto),
            findViewById<TextView>(R.id.textThemeLight),
            findViewById<TextView>(R.id.textThemeDark)
        )

        val activeStroke = ContextCompat.getColor(this, R.color.settings_accent)
        val inactiveStroke = ContextCompat.getColor(this, R.color.settings_chip_bg_inactive)
        val activeBg = ContextCompat.getColor(this, R.color.settings_chip_bg_active)
        val inactiveBg = ContextCompat.getColor(this, R.color.settings_card_bg_alt)
        val activeText = ContextCompat.getColor(this, R.color.settings_accent_text)
        val inactiveText = ContextCompat.getColor(this, R.color.settings_text_muted)
        val strokeWidthPx = resources.displayMetrics.density

        fun updateSelection(mode: Int) {
            cards.forEachIndexed { index, card ->
                val isSelected = index == mode
                card.strokeColor = if (isSelected) activeStroke else inactiveStroke
                card.strokeWidth = ((if (isSelected) 2 else 1) * strokeWidthPx).toInt()
                card.setCardBackgroundColor(if (isSelected) activeBg else inactiveBg)
                texts[index].setTextColor(if (isSelected) activeText else inactiveText)
            }
        }

        updateSelection(themePreference.getThemeMode())

        cards.forEachIndexed { index, card ->
            card.setOnClickListener {
                if (themePreference.getThemeMode() == index) return@setOnClickListener
                themePreference.setThemeMode(index)
                AppCompatDelegate.setDefaultNightMode(nightModeFor(index))
                recreate()
            }
        }
    }

    private fun nightModeFor(mode: Int): Int = when (mode) {
        SettingsThemePreference.MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        SettingsThemePreference.MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun startTutorial() {
        val steps = listOf(
            TutorialStep(
                R.id.layoutThemeMode,
                "Tema visual",
                "Alterna entre modo Claro, Oscuro o Automático según el sistema para la pantalla de ajustes."
            ),
            TutorialStep(
                R.id.cardLivePreview,
                "Vista previa en vivo",
                "Mira tu acuario en tiempo real mientras ajustas las opciones. Tócalo para interactuar o desliza para recorrerlo."
            ),
            TutorialStep(
                R.id.headerAcuarioEnvironment,
                "Entorno e Iluminación",
                "Personaliza el ambiente marino, degradados de color del agua y velocidad del ciclo día/noche."
            ),
            TutorialStep(
                R.id.headerAcuarioFauna,
                "Fauna Marina",
                "Controla la población de peces, tortugas, mantarrayas, medusas, ajolotes y la presencia del tiburón."
            ),
            TutorialStep(
                R.id.headerAcuarioLandscape,
                "Paisaje y Elementos",
                "Ajusta la densidad de plantas, burbujas y personaliza los colores del submarino y cofre del tesoro."
            ),
            TutorialStep(
                R.id.buttonApplyWallpaper,
                "Aplicar Fondo",
                "Establece el acuario interactivo 3D como tu fondo de pantalla o pantalla de bloqueo."
            )
        )
        tutorialOverlay.start(steps)
    }
}
