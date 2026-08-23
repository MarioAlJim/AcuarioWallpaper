package com.teamwolf.acuariowallpaper

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.RelativeLayout

/**
 * Shared UI helpers for the settings screen fragments, ported as-is from the "wallpaper"
 * reference project so future settings sections (once the Acuario effect grows more knobs)
 * can reuse the same accordion/card-scroll behavior instead of duplicating it.
 */
object SettingsAccordionHelper {

    /** Identifies the views that make up a single top-level accordion section. */
    data class AccordionSpec(val headerId: Int, val contentId: Int, val dividerId: Int, val arrowId: Int)

    private class AccordionEntry(val content: View, val divider: View?, val arrow: ImageView)

    /**
     * Wires up a group of top-level accordions living under [root]. Tapping a header
     * expands/collapses its own content and, when expanding, automatically collapses any other
     * accordion in the same group that was open (non-exclusive accordions become exclusive).
     */
    fun setupAccordionGroup(root: View, vararg specs: AccordionSpec) {
        val entries = specs.map { spec ->
            AccordionEntry(
                content = root.findViewById(spec.contentId),
                divider = root.findViewById(spec.dividerId),
                arrow = root.findViewById(spec.arrowId)
            )
        }

        specs.forEachIndexed { index, spec ->
            val header = root.findViewById<RelativeLayout>(spec.headerId)
            val entry = entries[index]
            header.setOnClickListener {
                val isVisible = entry.content.visibility == View.VISIBLE
                if (isVisible) {
                    setExpanded(entry, false)
                } else {
                    entries.forEach { other ->
                        if (other !== entry && other.content.visibility == View.VISIBLE) {
                            setExpanded(other, false)
                        }
                    }
                    setExpanded(entry, true)
                }
            }
        }
    }

    private fun setExpanded(entry: AccordionEntry, expand: Boolean) {
        val parentViewGroup = entry.content.parent as? ViewGroup
        if (parentViewGroup != null) {
            TransitionManager.beginDelayedTransition(parentViewGroup, AutoTransition().apply { duration = 250 })
        }
        entry.content.visibility = if (expand) View.VISIBLE else View.GONE
        entry.divider?.visibility = if (expand) View.VISIBLE else View.GONE
        entry.arrow.animate().rotation(if (expand) 180f else 0f).setDuration(250).start()
    }

    /** Centers [target] (a currently-selected chip/card) within [scrollView]'s viewport. */
    fun scrollToSelected(scrollView: HorizontalScrollView, target: View) {
        scrollView.post {
            scrollView.smoothScrollTo(
                (target.left - scrollView.width / 2 + target.width / 2).coerceAtLeast(0),
                0
            )
        }
    }
}
