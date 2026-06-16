package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.karaoke.ui.UiToast
import com.example.karaoke.ui.theme.DesignTokens
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens
import com.example.karaoke.ui.theme.wdp
import kotlinx.coroutines.delay

@Composable
fun KaraokeToast(
    message: UiToast?,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(message?.token) {
        if (message != null) {
            delay(2500)
            onDismiss()
        }
    }
    if (message == null) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = wdp(DesignTokens.TOAST_BOTTOM))
                .background(KaraokeColors.BgElevated, RoundedCornerShape(KaraokeDimens.RadiusSm))
                .padding(horizontal = KaraokeDimens.SpaceLg, vertical = KaraokeDimens.SpaceSm),
        ) {
            KaraokeText(text = message.text, style = KaraokeTextStyle.Body)
        }
    }
}
