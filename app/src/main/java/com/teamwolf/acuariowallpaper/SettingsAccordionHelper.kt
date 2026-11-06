package com.teamwolf.acuariowallpaper

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.widget.NestedScrollView

/**
 * Shared UI helpers for the settings screen fragments.
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
                    scrollToHeaderTop(header, root)
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

    /**
     * Scrolls the nearest vertical ScrollView / NestedScrollView so that the expanded section's card/header
     * aligns right at the top of the viewport.
     */
    private fun scrollToHeaderTop(header: View, root: View) {
        val targetCard = (header.parent as? com.google.android.material.card.MaterialCardView) ?: header

        var current: View? = targetCard.parent as? View
        var scrollView: View? = null
        while (current != null) {
            if (current is NestedScrollView || current is android.widget.ScrollView) {
                scrollView = current
                break
            }
            current = current.parent as? View
        }
        if (scrollView == null) {
            scrollView = (root as? NestedScrollView)
                ?: (root as? android.widget.ScrollView)
                ?: root.rootView.findViewById(R.id.settingsScrollView)
        }

        val scrollContainer = scrollView ?: return

        scrollContainer.postDelayed({
            val rect = android.graphics.Rect()
            targetCard.getDrawingRect(rect)
            (scrollContainer as ViewGroup).offsetDescendantRectToMyCoords(targetCard, rect)

            val targetY = (scrollContainer.scrollY + rect.top).coerceAtLeast(0)
            if (scrollContainer is NestedScrollView) {
                scrollContainer.smoothScrollTo(0, targetY)
            } else if (scrollContainer is android.widget.ScrollView) {
                scrollContainer.smoothScrollTo(0, targetY)
            }
        }, 280)
    }
}
