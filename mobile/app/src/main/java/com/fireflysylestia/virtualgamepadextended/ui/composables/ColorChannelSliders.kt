package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fireflysylestia.virtualgamepadextended.R

/** One red, green or blue channel of a colour. */
private data class ColorChannel(
    @StringRes val labelRes: Int,
    val value: Float,
    val update: (Float) -> Color,
)

/**
 * A colour picker built from the three channels, with a swatch of the result.
 *
 * The three sliders are one setting between them, which is why a plain list of
 * numbers would not do: the swatch is what the choice has to be made against.
 */
@Composable
fun ColorChannelSliders(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val channels = listOf(
        ColorChannel(R.string.color_red, color.red) { Color(it, color.green, color.blue) },
        ColorChannel(R.string.color_green, color.green) { Color(color.red, it, color.blue) },
        ColorChannel(R.string.color_blue, color.blue) { Color(color.red, color.green, it) },
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }

        Surface(
            color = color,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(width = 96.dp, height = 28.dp),
        ) {}

        channels.forEach { channel ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(channel.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(56.dp),
                )
                Slider(
                    value = channel.value,
                    onValueChange = { onColorChange(channel.update(it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
