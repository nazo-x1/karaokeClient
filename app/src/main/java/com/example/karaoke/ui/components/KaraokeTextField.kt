package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeTypography

@Composable
fun KaraokeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(KaraokeColors.BgElevated, RoundedCornerShape(8.dp))
            .border(1.dp, KaraokeColors.BorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        textStyle = TextStyle(
            color = KaraokeColors.TextPrimary,
            fontSize = KaraokeTypography.List,
        ),
        cursorBrush = SolidColor(KaraokeColors.AccentPrimary),
        singleLine = singleLine,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    KaraokeText(text = placeholder, style = KaraokeTextStyle.Hint)
                }
                inner()
            }
        },
    )
}
