package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.ui.Alignment
import com.fireflysylestia.virtualgamepadextended.data.ButtonAnchor

/**
 * Where a control sits inside the inset box before its offset is applied.
 *
 * This is the Compose half of `GamepadGeometry`: that module computes the same
 * placement for the customisation screen's tap targets, so the two must stay in
 * step.
 */
fun ButtonAnchor.toAlignment(): Alignment = when (this) {
    ButtonAnchor.TOP_LEFT -> Alignment.TopStart
    ButtonAnchor.TOP_CENTER -> Alignment.TopCenter
    ButtonAnchor.TOP_RIGHT -> Alignment.TopEnd
    ButtonAnchor.CENTER_LEFT -> Alignment.CenterStart
    ButtonAnchor.CENTER -> Alignment.Center
    ButtonAnchor.CENTER_RIGHT -> Alignment.CenterEnd
    ButtonAnchor.BOTTOM_LEFT -> Alignment.BottomStart
    ButtonAnchor.BOTTOM_CENTER -> Alignment.BottomCenter
    ButtonAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
}
