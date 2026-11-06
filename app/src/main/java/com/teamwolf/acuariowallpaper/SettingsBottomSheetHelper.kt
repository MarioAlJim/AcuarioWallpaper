package com.teamwolf.acuariowallpaper

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.radiobutton.MaterialRadioButton

/**
 * Helper centralizado para desplegar Modal Bottom Sheets con estilo Material 3 / WhatsApp & Cash App.
 * Proporciona:
 * - Modal informativo y de ayuda contextual (Hero Badge circular, ítems con leading icon, CTA pill y botón de tutorial).
 * - Modal dinámico para selectores de opciones con Hero preview interactivo en tiempo real.
 * - Modal interactivo para selección y mezcla de color HSV con previsualización en vivo, presets y controles ergonómicos.
 */
object SettingsBottomSheetHelper {

    private data class HelpFeatureItem(
        val emoji: String?,
        val iconRes: Int?,
        val title: String,
        val description: String
    )

    /**
     * Crea y configura un BottomSheetDialog con esquinas superiores redondeadas a 28dp
     * y apertura en estado expandido completo.
     */
    fun createBottomSheetDialog(context: Context, layoutResId: Int): Pair<BottomSheetDialog, View> {
        val dialog = BottomSheetDialog(context, R.style.Theme_AcuarioWallpaper_BottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(layoutResId, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        return Pair(dialog, view)
    }

    /**
     * Muestra el modal informativo de ayuda estilo Material 3 / WhatsApp Android.
     */
    fun showHelpSheet(
        context: Context,
        onTutorialRequested: (() -> Unit)? = null
    ) {
        val (dialog, view) = createBottomSheetDialog(context, R.layout.bottom_sheet_help)

        val closeBtn = view.findViewById<ImageView>(R.id.btnHelpClose)
        val heroImage = view.findViewById<ImageView>(R.id.imageHelpHero)
        val titleView = view.findViewById<TextView>(R.id.textHelpTitle)
        val featuresContainer = view.findViewById<LinearLayout>(R.id.containerHelpFeatures)
        val dismissBtn = view.findViewById<MaterialButton>(R.id.btnHelpDismiss)
        val tutorialBtn = view.findViewById<MaterialButton>(R.id.btnHelpTutorial)

        val inflater = LayoutInflater.from(context)

        heroImage.setImageResource(R.drawable.ic_touch)
        titleView.setText(R.string.acuario_help_dialog_title)

        val features = listOf(
            HelpFeatureItem(
                "🐠",
                null,
                "Alimentar Peces",
                "Toca cualquier área libre de agua para soltar copos de alimento. Los peces nadarán rápidamente a comer."
            ),
            HelpFeatureItem(
                "🐢",
                null,
                "Susto de Tortuga",
                "Toca una tortuga para asustarla: acelerará su nado y esconderá momentáneamente su cabeza y aletas."
            ),
            HelpFeatureItem(
                "🌊",
                null,
                "Acrobacia de Mantarraya",
                "Toca una mantarraya para que ejecute una elegante vuelta completa (looping) de 360°."
            ),
            HelpFeatureItem(
                "🦈",
                null,
                "Ataque del Tiburón",
                "Toca al tiburón para provocar una embestida a alta velocidad abriendo sus feroces mandíbulas."
            ),
            HelpFeatureItem(
                "⚡",
                null,
                "Medusa Eléctrica",
                "Toca una medusa para electrificarla: brillará con destellos bioluminiscentes y chispas de energía."
            ),
            HelpFeatureItem(
                "🦎",
                null,
                "Ajolotes Curiosos",
                "Caminan sobre las rocas y el fondo marino; tócalos para que naden hacia otro escondite."
            ),
            HelpFeatureItem(
                "📱",
                null,
                "Parallax 3D con Giroscopio",
                "Inclina tu dispositivo en cualquier dirección para experimentar la profundidad submarina real."
            ),
            HelpFeatureItem(
                "🫧",
                null,
                "Tormenta de Burbujas",
                "Sacude el teléfono para activar una densa columna ascendente de burbujas de aire desde el lecho marino."
            ),
            HelpFeatureItem(
                "🍖",
                null,
                "Modo Realista (Ecosistema)",
                "Al activar el Modo Realista, si tocas al tiburón cazará presas en pantalla, y los animales deben ser alimentados para no marcharse."
            )
        )

        featuresContainer.removeAllViews()
        features.forEach { item ->
            val itemView = inflater.inflate(R.layout.item_help_feature, featuresContainer, false)
            val emojiView = itemView.findViewById<TextView>(R.id.textFeatureEmoji)
            val iconView = itemView.findViewById<ImageView>(R.id.imageFeatureIcon)
            if (item.emoji != null) {
                emojiView.visibility = View.VISIBLE
                emojiView.text = item.emoji
                iconView.visibility = View.GONE
            } else if (item.iconRes != null) {
                emojiView.visibility = View.GONE
                iconView.visibility = View.VISIBLE
                iconView.setImageResource(item.iconRes)
            }
            itemView.findViewById<TextView>(R.id.textFeatureTitle).text = item.title
            itemView.findViewById<TextView>(R.id.textFeatureDesc).text = item.description
            featuresContainer.addView(itemView)
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dismissBtn.setOnClickListener { dialog.dismiss() }

        if (onTutorialRequested != null) {
            tutorialBtn.visibility = View.VISIBLE
            tutorialBtn.setOnClickListener {
                dialog.dismiss()
                onTutorialRequested()
            }
        } else {
            tutorialBtn.visibility = View.GONE
        }

        dialog.show()
    }

    /**
     * Muestra un Modal Bottom Sheet dinámico para selección de opciones con vista previa interactiva.
     */
    fun showDynamicOptionPickerSheet(
        context: Context,
        title: String,
        subtitle: String?,
        options: Array<String>,
        selectedIndex: Int,
        previewFactory: ((optionIndex: Int) -> View?)? = null,
        onSelected: (Int) -> Unit
    ) {
        val (dialog, view) = createBottomSheetDialog(context, R.layout.bottom_sheet_dynamic_option_picker)

        val titleView = view.findViewById<TextView>(R.id.textOptionSheetTitle)
        val subtitleView = view.findViewById<TextView>(R.id.textOptionSheetSubtitle)
        val closeBtn = view.findViewById<ImageView>(R.id.btnOptionSheetClose)
        val applyBtn = view.findViewById<MaterialButton>(R.id.btnOptionSheetApply)
        val heroCard = view.findViewById<MaterialCardView>(R.id.cardOptionHeroPreview)
        val heroContainer = view.findViewById<FrameLayout>(R.id.frameOptionHeroContainer)
        val heroBadge = view.findViewById<TextView>(R.id.badgeOptionHero)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerOptionSheetList)

        titleView.text = title
        if (!subtitle.isNullOrBlank()) {
            subtitleView.text = subtitle
            subtitleView.visibility = View.VISIBLE
        } else {
            subtitleView.visibility = View.GONE
        }

        var activeIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))

        fun updateHero(index: Int) {
            heroBadge.text = options.getOrNull(index) ?: ""
            if (previewFactory != null) {
                heroCard.visibility = View.VISIBLE
                heroContainer.removeAllViews()
                previewFactory(index)?.let { previewView ->
                    (previewView.parent as? ViewGroup)?.removeView(previewView)
                    heroContainer.addView(
                        previewView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            } else {
                heroCard.visibility = View.GONE
            }
        }

        updateHero(activeIndex)

        class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardLeading: MaterialCardView = itemView.findViewById(R.id.cardOptionLeading)
            val frameLeading: FrameLayout = itemView.findViewById(R.id.frameOptionLeading)
            val textTitle: TextView = itemView.findViewById(R.id.textOptionTitle)
            val radioSelected: MaterialRadioButton = itemView.findViewById(R.id.radioOptionSelected)
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = object : RecyclerView.Adapter<OptionViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_option_choice, parent, false)
                return OptionViewHolder(v)
            }

            override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
                holder.textTitle.text = options[position]
                val isSelected = position == activeIndex
                holder.radioSelected.isChecked = isSelected

                if (previewFactory != null) {
                    holder.cardLeading.visibility = View.VISIBLE
                    holder.frameLeading.removeAllViews()
                    previewFactory(position)?.let { preview ->
                        (preview.parent as? ViewGroup)?.removeView(preview)
                        holder.frameLeading.addView(
                            preview,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                } else {
                    holder.cardLeading.visibility = View.GONE
                }

                holder.itemView.setOnClickListener {
                    if (activeIndex != position) {
                        val prev = activeIndex
                        activeIndex = position
                        notifyItemChanged(prev)
                        notifyItemChanged(activeIndex)
                        updateHero(activeIndex)
                        onSelected(activeIndex)
                    }
                }
            }

            override fun getItemCount(): Int = options.size
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        applyBtn.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /**
     * Muestra el modal interactivo de selección de color HSV estilo Cash App / Material 3.
     */
    fun showColorMixerSheet(
        context: Context,
        initialColorHex: Int,
        presets: List<Int>? = null,
        onColorSelected: (Int) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        fun Int.px() = Math.round(this * density)

        val (dialog, view) = createBottomSheetDialog(context, R.layout.bottom_sheet_color_mixer)

        val preview = view.findViewById<View>(R.id.colorMixerPreview)
        val hexBadge = view.findViewById<TextView>(R.id.textColorHexBadge)
        val presetsContainer = view.findViewById<LinearLayout>(R.id.colorMixerPresets)
        val hueSeekBar = view.findViewById<SeekBar>(R.id.colorMixerHue)
        val satSeekBar = view.findViewById<SeekBar>(R.id.colorMixerSaturation)
        val valSeekBar = view.findViewById<SeekBar>(R.id.colorMixerBrightness)
        val textHueValue = view.findViewById<TextView>(R.id.textHueValue)
        val textSatValue = view.findViewById<TextView>(R.id.textSatValue)
        val textValValue = view.findViewById<TextView>(R.id.textValValue)
        val closeBtn = view.findViewById<ImageView>(R.id.btnMixerClose)
        val cancelBtn = view.findViewById<MaterialButton>(R.id.btnMixerCancel)
        val applyBtn = view.findViewById<MaterialButton>(R.id.btnMixerApply)

        fun currentColorHex(): Int {
            val hsv = floatArrayOf(
                hueSeekBar.progress.toFloat(),
                satSeekBar.progress / 100f,
                valSeekBar.progress / 100f
            )
            return Color.HSVToColor(hsv) and 0xFFFFFF
        }

        fun updateUI() {
            val hex = currentColorHex()
            val colorWithAlpha = hex or 0xFF000000.toInt()

            (preview.background as? GradientDrawable
                ?: GradientDrawable().also { preview.background = it })
                .apply {
                    cornerRadius = 14f * density
                    setColor(colorWithAlpha)
                }

            hexBadge.text = String.format("#%06X", hex)

            textHueValue.text = "${hueSeekBar.progress}°"
            textSatValue.text = "${satSeekBar.progress}%"
            textValValue.text = "${valSeekBar.progress}%"
        }

        fun setHsv(hsv: FloatArray) {
            hueSeekBar.progress = hsv[0].toInt().coerceIn(0, 360)
            satSeekBar.progress = (hsv[1] * 100).toInt().coerceIn(0, 100)
            valSeekBar.progress = (hsv[2] * 100).toInt().coerceIn(0, 100)
            updateUI()
        }

        val initialHsv = FloatArray(3)
        Color.colorToHSV(initialColorHex or 0xFF000000.toInt(), initialHsv)
        setHsv(initialHsv)

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateUI()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        hueSeekBar.setOnSeekBarChangeListener(seekListener)
        satSeekBar.setOnSeekBarChangeListener(seekListener)
        valSeekBar.setOnSeekBarChangeListener(seekListener)

        val defaultPresets = presets ?: listOf(
            0x1A7066, 0x0D578C, 0xB34D66, 0x260D40, 0x0DA699,
            0x00ACC1, 0x00E5FF, 0x0288D1, 0x1565C0, 0x004D40,
            0x00796B, 0x3F51B5, 0x4A148C, 0x1A237E, 0x031821
        )

        presetsContainer.removeAllViews()
        defaultPresets.forEach { colorIntRaw ->
            val colorWithAlpha = colorIntRaw or 0xFF000000.toInt()
            val swatchCard = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(44.px(), 44.px()).apply {
                    marginEnd = 12.px()
                }
                radius = 12f * density
                cardElevation = 0f
                strokeWidth = (1.5f * density).toInt()
                strokeColor = ContextCompat.getColor(context, R.color.settings_stroke)
                setCardBackgroundColor(colorWithAlpha)
                isClickable = true
                isFocusable = true
                foreground = RippleDrawable(
                    ColorStateList.valueOf(0x33FFFFFF),
                    null,
                    null
                )
                setOnClickListener {
                    val presetHsv = FloatArray(3)
                    Color.colorToHSV(colorWithAlpha, presetHsv)
                    setHsv(presetHsv)
                }
            }
            presetsContainer.addView(swatchCard)
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        cancelBtn.setOnClickListener { dialog.dismiss() }
        applyBtn.setOnClickListener {
            dialog.dismiss()
            onColorSelected(currentColorHex())
        }

        dialog.show()
    }
}
