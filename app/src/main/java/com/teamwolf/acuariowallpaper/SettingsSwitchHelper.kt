package com.teamwolf.acuariowallpaper

import android.content.res.ColorStateList
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

/**
 * Helper para filas de configuración con switch o tarjeta interactiva de encendido/apagado.
 */
object SettingsSwitchHelper {

    fun bind(
        row: View,
        switch: CompoundButton? = null,
        isChecked: Boolean,
        statusView: TextView? = null,
        iconView: ImageView? = null,
        iconCard: MaterialCardView? = null,
        activeColor: Int? = null,
        onCheckedChanged: (Boolean) -> Unit
    ) {
        if (switch is SwitchCompat) {
            switch.showText = false
            switch.textOn = ""
            switch.textOff = ""
        }
        switch?.setOnCheckedChangeListener(null)
        switch?.isChecked = isChecked

        var currentState = isChecked

        fun updateVisuals(checked: Boolean) {
            val resolvedActiveColor = activeColor ?: ContextCompat.getColor(row.context, R.color.settings_accent)
            val mutedColor = ContextCompat.getColor(row.context, R.color.settings_text_muted)
            val activeChipBg = ContextCompat.getColor(row.context, R.color.settings_chip_bg_active)
            val inactiveCardBg = ContextCompat.getColor(row.context, R.color.settings_card_bg_alt)
            val defaultStrokeColor = ContextCompat.getColor(row.context, R.color.settings_stroke)

            statusView?.apply {
                text = context.getString(
                    if (checked) R.string.touch_interaction_enabled else R.string.touch_interaction_disabled
                )
                setTextColor(if (checked) resolvedActiveColor else mutedColor)
            }

            iconView?.apply {
                val color = if (checked) resolvedActiveColor else mutedColor
                imageTintList = ColorStateList.valueOf(color)
                alpha = if (checked) 1.0f else 0.45f
            }

            iconCard?.apply {
                if (checked) {
                    setCardBackgroundColor(activeChipBg)
                    strokeColor = resolvedActiveColor
                } else {
                    setCardBackgroundColor(inactiveCardBg)
                    strokeColor = defaultStrokeColor
                }
            }
        }

        updateVisuals(currentState)

        val toggle = {
            currentState = !currentState
            switch?.isChecked = currentState
            updateVisuals(currentState)
            onCheckedChanged(currentState)
        }

        row.setOnClickListener { toggle() }

        switch?.setOnClickListener {
            currentState = switch.isChecked
            updateVisuals(currentState)
            onCheckedChanged(currentState)
        }
    }

    fun setLocked(row: View, switch: CompoundButton? = null, locked: Boolean) {
        row.isEnabled = !locked
        switch?.isEnabled = !locked
        row.alpha = if (locked) 0.38f else 1.0f
    }
}
