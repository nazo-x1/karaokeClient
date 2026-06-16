package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.karaoke.ui.theme.KaraokeColors

@Composable
fun KaraokeScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KaraokeColors.BgPrimary),
        content = { content() },
    )
}

@Composable
fun KaraokeOverlay(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KaraokeColors.OverlayDim),
        contentAlignment = contentAlignment,
        content = { content() },
    )
}

@Composable
fun KaraokeCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(KaraokeColors.BgElevated, RoundedCornerShape(12.dp))
            .border(1.dp, KaraokeColors.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(32.dp),
        content = content,
    )
}

@Composable
fun KaraokeHintBar(text: String, modifier: Modifier = Modifier) {
    KaraokeText(
        text = text,
        style = KaraokeTextStyle.Hint,
        modifier = modifier
            .fillMaxWidth()
            .background(KaraokeColors.BgElevated)
            .padding(12.dp),
    )
}

@Composable
fun KaraokeLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(KaraokeColors.BgSecondary, RoundedCornerShape(4.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .background(KaraokeColors.AccentPrimary, RoundedCornerShape(4.dp)),
        )
    }
}
