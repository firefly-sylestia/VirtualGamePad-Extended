package com.fireflysylestia.virtualgamepadextended.data

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.fireflysylestia.virtualgamepadextended.ui.theme.VirtualGamePadExtendedTheme

// The width and height of the preview surface in dp
const val PreviewWidthDp = 960
const val PreviewHeightDp = 540

@Composable
fun PreviewBase(content: @Composable () -> Unit) {
    VirtualGamePadExtendedTheme(darkMode = defaultColorScheme, baseColor = defaultBaseColor) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}
