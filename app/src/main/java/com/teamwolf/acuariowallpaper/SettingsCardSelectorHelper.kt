package com.teamwolf.acuariowallpaper

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

/**
 * Shared builder for configuration selectors.
 * Replaces repetitive horizontal card carousels with a clean, interactive Material 3 row card
 * that triggers a dynamic Modal Bottom Sheet with live real-time preview feedback.
 */
object SettingsCardSelectorHelper {

    /**
     * Clears [container] and populates it with an interactive Material 3 row card displaying
     * the currently selected option, a mini-preview badge (if [previewFactory] is provided),
     * and a forward chevron. Tapping the card opens a dynamic [SettingsBottomSheetHelper] modal
     * with live interactive preview and reactive real-time updates.
     */
    fun populate(
        context: Context,
        container: LinearLayout,
        options: Array<String>,
        selectedIndex: Int,
        previewFactory: ((optionIndex: Int) -> View?)? = null,
        previewFrameBackgroundRes: Int = 0,
        title: String? = null,
        subtitle: String? = null,
        onSelected: (optionIndex: Int) -> Unit
    ) {
        container.removeAllViews()
        var currentSelected = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))

        val density = context.resources.displayMetrics.density
        fun Int.px() = Math.round(this * density)

        // Ensure HorizontalScrollView and container expand to 100% full width
        (container.parent as? HorizontalScrollView)?.isFillViewport = true
        container.orientation = LinearLayout.VERTICAL
        container.layoutParams = (container.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }

        val settingTitle = title ?: findSettingTitle(container) ?: "Seleccionar opción"
        val settingSubtitle = subtitle ?: "Selecciona una opción para previsualizar en tiempo real"

        val rowCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4.px(), 0, 4.px())
            }
            radius = 14.px().toFloat()
            strokeWidth = 1.px()
            strokeColor = ContextCompat.getColor(context, R.color.settings_stroke)
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.settings_card_bg_alt))
            isClickable = true
            isFocusable = true
        }

        val contentRow = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.px(), 12.px(), 14.px(), 12.px())
        }

        val hasPreview = previewFactory != null
        var previewContainer: FrameLayout? = null

        if (hasPreview) {
            previewContainer = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(42.px(), 42.px()).apply {
                    marginEnd = 12.px()
                }
                background = if (previewFrameBackgroundRes != 0) {
                    ContextCompat.getDrawable(context, previewFrameBackgroundRes)
                } else {
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 10 * density
                        setColor(ContextCompat.getColor(context, R.color.settings_card_bg_alt2))
                        setStroke(1.px(), ContextCompat.getColor(context, R.color.settings_stroke))
                    }
                }
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                clipToOutline = true
            }

            previewFactory?.invoke(currentSelected)?.let { preview ->
                (preview.parent as? ViewGroup)?.removeView(preview)
                previewContainer.addView(
                    preview,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
            contentRow.addView(previewContainer)
        }

        val textColumn = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        val valueTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = options.getOrNull(currentSelected) ?: ""
            setTextColor(ContextCompat.getColor(context, R.color.settings_text_primary))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val hintTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.px()
            }
            text = "Toca para cambiar"
            setTextColor(ContextCompat.getColor(context, R.color.settings_text_secondary))
            textSize = 12f
        }

        textColumn.addView(valueTextView)
        textColumn.addView(hintTextView)
        contentRow.addView(textColumn)

        val chevron = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(22.px(), 22.px())
            setImageResource(R.drawable.ic_arrow_forward)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.settings_text_secondary)
            )
        }
        contentRow.addView(chevron)

        rowCard.addView(contentRow)
        container.addView(rowCard)

        fun updateCardDisplay(index: Int) {
            currentSelected = index.coerceIn(0, (options.size - 1).coerceAtLeast(0))
            valueTextView.text = options.getOrNull(currentSelected) ?: ""
            if (hasPreview && previewContainer != null) {
                previewContainer.removeAllViews()
                previewFactory?.invoke(currentSelected)?.let { preview ->
                    (preview.parent as? ViewGroup)?.removeView(preview)
                    previewContainer.addView(
                        preview,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }

        rowCard.setOnClickListener {
            SettingsBottomSheetHelper.showDynamicOptionPickerSheet(
                context = context,
                title = settingTitle,
                subtitle = settingSubtitle,
                options = options,
                selectedIndex = currentSelected,
                previewFactory = previewFactory
            ) { selectedIdx ->
                updateCardDisplay(selectedIdx)
                onSelected(selectedIdx)
            }
        }
    }

    /**
     * Inspects the view hierarchy preceding [container] to discover the setting title label.
     */
    fun findSettingTitle(container: View): String? {
        var current: View = container
        while (current.parent is View) {
            val parent = current.parent as ViewGroup
            val idx = parent.indexOfChild(current)
            if (idx > 0) {
                val prev = parent.getChildAt(idx - 1)
                val title = extractTitleFromView(prev)
                if (!title.isNullOrBlank()) {
                    return title
                }
            }
            if (parent is HorizontalScrollView) {
                current = parent
            } else {
                break
            }
        }
        return null
    }

    private fun extractTitleFromView(view: View): String? {
        if (view is TextView && view.text.isNotBlank()) {
            return view.text.toString()
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val text = extractTitleFromView(child)
                if (!text.isNullOrBlank()) {
                    return text
                }
            }
        }
        return null
    }

    /** Index into [values] whose entry is numerically closest to [current] (ties favor the lower index). */
    fun closestValueIndex(values: IntArray, current: Int): Int =
        values.indices.minByOrNull { kotlin.math.abs(values[it] - current) } ?: 0
}
