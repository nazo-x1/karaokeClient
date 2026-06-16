package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

enum class KaraokeButtonVariant { Primary, Secondary }

/**
 * 同时支持触摸点击与 TV 遥控器焦点（不使用 tv-material Button，避免触摸无响应）。
 */
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
    val shape = RoundedCornerShape(KaraokeDimens.RadiusSm)
    val interactionSource = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(shape)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .background(
                color = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
                shape = shape,
            )
            .border(
                width = if (focused) KaraokeDimens.FocusBorder else KaraokeDimens.Border,
                color = if (focused) KaraokeColors.AccentPrimary else KaraokeColors.BorderSubtle,
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled, interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        KaraokeText(
            text = text,
            style = KaraokeTextStyle.List,
            color = if (enabled) KaraokeColors.TextPrimary else KaraokeColors.TextSecondary,
        )
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
