package com.example.karaoke.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.Text
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeTypography

enum class KaraokeTextStyle {
    Title, List, Body, Hint, Error,
}

@Composable
fun KaraokeText(
    text: String,
    modifier: Modifier = Modifier,
    style: KaraokeTextStyle = KaraokeTextStyle.Body,
    color: Color? = null,
    fontWeight: FontWeight? = null,
) {
    val fontSize: TextUnit
    val defaultColor: Color
    when (style) {
        KaraokeTextStyle.Title -> {
            fontSize = KaraokeTypography.Title
            defaultColor = KaraokeColors.TextPrimary
        }
        KaraokeTextStyle.List -> {
            fontSize = KaraokeTypography.List
            defaultColor = KaraokeColors.TextPrimary
        }
        KaraokeTextStyle.Body -> {
            fontSize = KaraokeTypography.Body
            defaultColor = KaraokeColors.TextPrimary
        }
        KaraokeTextStyle.Hint -> {
            fontSize = KaraokeTypography.Hint
            defaultColor = KaraokeColors.TextSecondary
        }
        KaraokeTextStyle.Error -> {
            fontSize = KaraokeTypography.Body
            defaultColor = KaraokeColors.Error
        }
    }
    Text(
        text = text,
        modifier = modifier,
        color = color ?: defaultColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )
}
