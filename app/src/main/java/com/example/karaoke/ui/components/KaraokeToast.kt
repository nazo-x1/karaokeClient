package com.example.karaoke.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import com.example.karaoke.ui.theme.KaraokeColors
import kotlinx.coroutines.delay

@Composable
fun KaraokeToast(
    message: String?,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(message) {
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
        Surface(
            modifier = Modifier.padding(bottom = 48.dp),
            shape = RoundedCornerShape(8.dp),
            color = KaraokeColors.BgElevated,
        ) {
            KaraokeText(
                text = message,
                style = KaraokeTextStyle.Body,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}
