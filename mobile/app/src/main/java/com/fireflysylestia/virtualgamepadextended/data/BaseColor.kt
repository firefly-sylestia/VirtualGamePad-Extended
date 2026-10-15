package com.fireflysylestia.virtualgamepadextended.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.fireflysylestia.virtualgamepadextended.R
import com.fireflysylestia.virtualgamepadextended.ui.theme.GlossyBlue
import com.fireflysylestia.virtualgamepadextended.ui.theme.GlossyGreen
import com.fireflysylestia.virtualgamepadextended.ui.theme.GlossyRed
import com.fireflysylestia.virtualgamepadextended.ui.theme.NeonBlue
import com.fireflysylestia.virtualgamepadextended.ui.theme.NeonGreen
import com.fireflysylestia.virtualgamepadextended.ui.theme.NeonRed

enum class BaseColor(@StringRes val nameRes: Int) {
    RED(R.string.color_red),
    GREEN(R.string.color_green),
    BLUE(R.string.color_blue);

    companion object {
        fun fromInt(i: Int): BaseColor {
            return when (i) {
                0 -> RED
                1 -> GREEN
                2 -> BLUE
                else -> throw IllegalArgumentException("Invalid BaseColor value")
            }
        }
    }
}

/**
 * Returns a color from a given base color name depending on the current color scheme.
 */
fun getColorFromBaseColor(baseColor: BaseColor, isDarkMode: Boolean): Color {
    return when (baseColor) {
        BaseColor.RED -> if (isDarkMode) NeonRed else GlossyRed
        BaseColor.GREEN -> if (isDarkMode) NeonGreen else GlossyGreen
        BaseColor.BLUE -> if (isDarkMode) NeonBlue else GlossyBlue
    }
}
