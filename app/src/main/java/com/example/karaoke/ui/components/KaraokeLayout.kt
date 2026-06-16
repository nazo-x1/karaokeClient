package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.example.karaoke.ui.theme.DesignTokens
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens
import com.example.karaoke.ui.theme.wdp

@Composable
fun KaraokeScreen(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KaraokeColors.BgPrimary),
        contentAlignment = contentAlignment,
        content = content,
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
            .background(KaraokeColors.BgElevated, RoundedCornerShape(KaraokeDimens.RadiusMd))
            .border(KaraokeDimens.Border, KaraokeColors.BorderSubtle, RoundedCornerShape(KaraokeDimens.RadiusMd))
            .padding(KaraokeDimens.SpaceXl),
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
            .padding(KaraokeDimens.SpaceSm),
    )
}

@Composable
fun KaraokeLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val trackHeight = wdp(DesignTokens.PROGRESS_HEIGHT)
    val trackRadius = wdp(DesignTokens.RADIUS_SM - 8f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .background(KaraokeColors.BgSecondary, RoundedCornerShape(trackRadius)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(trackHeight)
                .background(KaraokeColors.AccentPrimary, RoundedCornerShape(trackRadius)),
        )
    }
}
