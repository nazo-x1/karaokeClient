package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

enum class KaraokeButtonVariant { Primary, Secondary }

@Composable
fun KaraokeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: KaraokeButtonVariant = KaraokeButtonVariant.Primary,
) {
    val containerColor = when (variant) {
        KaraokeButtonVariant.Primary -> KaraokeColors.AccentPrimary
        KaraokeButtonVariant.Secondary -> KaraokeColors.BgElevated
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.colors(containerColor = containerColor),
    ) {
        KaraokeText(text = text, style = KaraokeTextStyle.List)
    }
}

@Composable
fun KaraokeLoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    KaraokeButton(
        text = if (loading) "请稍候…" else text,
        onClick = onClick,
        enabled = !loading,
        modifier = modifier.height(KaraokeDimens.ButtonHeight),
    )
}
