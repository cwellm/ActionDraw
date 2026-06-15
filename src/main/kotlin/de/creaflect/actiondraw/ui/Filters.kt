package de.creaflect.actiondraw.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/** Fully desaturated color filter for the black-and-white toggle. */
fun grayscaleFilter(): ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
