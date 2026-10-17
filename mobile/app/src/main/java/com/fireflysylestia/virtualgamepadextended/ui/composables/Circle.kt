package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Circle(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    colour: Color = Color.White,
    content: @Composable (BoxScope.() -> Unit)
) {
    Box(
        // The stick rings and knobs are drawn with this, so the skin's corner
        // radius reaches them here: a large radius is the circle they have always
        // been, and a small one squares the sticks off for blockier skins.
        modifier = modifier
            .clip(shape = RoundedCornerShape(LocalGamepadSkin.current.cornerRadius))
            .background(Color.Transparent),
        contentAlignment = contentAlignment
    ) {
        Box(
            Modifier
                .background(colour)
                .matchParentSize(),
            contentAlignment = contentAlignment
        ) {
            content()
        }
    }
}

@Preview(showBackground = false)
@Composable
fun CirclePreview() {
    Circle(
        modifier = Modifier
            .size(16.dp),
        contentAlignment = Alignment.Center
    ) {}
}
