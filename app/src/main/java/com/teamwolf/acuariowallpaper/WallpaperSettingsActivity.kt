package com.teamwolf.acuariowallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.teamwolf.acuariowallpaper.core.ConfigManager

class WallpaperSettingsActivity : AppCompatActivity() {

    lateinit var configManager: ConfigManager
        private set

    private lateinit var themePreference: SettingsThemePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate()/setContentView() so the chosen theme applies
        // to this Activity's very first layout pass.
        themePreference = SettingsThemePreference(this)
        AppCompatDelegate.setDefaultNightMode(nightModeFor(themePreference.getThemeMode()))

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configManager = ConfigManager(this)

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_fragment_container, AcuarioSettingsFragment())
            .commit()

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

    fun showHelpDialog() {
        val helpText = android.text.Html.fromHtml(
            "<p><b>• Alimentar Peces:</b> Toca el agua vacía (donde no haya otros animales) para soltar alimento. Los peces nadarán a comer y darán un giro feliz.</p>" +
            "<p><b>• Susto de Tortuga:</b> Toca una tortuga para asustarla; nadará más rápido y encogerá su cabeza/aletas en el caparazón.</p>" +
            "<p><b>• Loop de Mantarraya:</b> Toca una mantarraya para que realice una pirueta acrobática de 360° en el aire con un aleteo rápido.</p>" +
            "<p><b>• Tiburón Agresivo:</b> Toca al tiburón para que realice una embestida rápida y abra sus mandíbulas en un ataque feroz.</p>" +
            "<p><b>• Medusa Eléctrica:</b> Toca una medusa para electrificarla. Brillará con luz bioluminiscente y emitirá rayos y chispas eléctricas.</p>" +
            "<p><b>• Parallax 3D (Giroscopio):</b> Inclina tu dispositivo hacia los lados o arriba/abajo para percibir la profundidad tridimensional del acuario.</p>" +
            "<p><b>• Tormenta de Burbujas:</b> Sacude tu dispositivo para desatar una intensa ráfaga de burbujas desde el fondo.</p>",
            android.text.Html.FROM_HTML_MODE_LEGACY
        )

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Ayuda e Interacciones")
            .setMessage(helpText)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun nightModeFor(mode: Int): Int = when (mode) {
        SettingsThemePreference.MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        SettingsThemePreference.MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
