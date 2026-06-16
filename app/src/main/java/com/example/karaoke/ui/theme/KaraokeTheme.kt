package com.example.karaoke.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val KaraokeColorScheme = darkColorScheme(
    primary = KaraokeColors.AccentPrimary,
    onPrimary = KaraokeColors.TextPrimary,
    background = KaraokeColors.BgPrimary,
    onBackground = KaraokeColors.TextPrimary,
    surface = KaraokeColors.BgSecondary,
    onSurface = KaraokeColors.TextPrimary,
    error = KaraokeColors.Error,
)

@Composable
fun KaraokeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KaraokeColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KaraokeColors.BgPrimary),
        ) {
            content()
        }
    }
}
