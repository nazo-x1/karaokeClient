package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

@Composable
fun KaraokeActionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    secondary: Boolean = false,
) {
    val bg = when {
        selected -> KaraokeColors.AccentPrimary
        secondary -> KaraokeColors.BgSecondary
        else -> KaraokeColors.BgHover
    }
    val fg = when {
        selected -> KaraokeColors.TextPrimary
        secondary -> KaraokeColors.TextSecondary
        else -> KaraokeColors.TextPrimary
    }
    KaraokeText(
        text = label,
        style = KaraokeTextStyle.Body,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(KaraokeDimens.RadiusSm))
            .border(
                width = if (selected) KaraokeDimens.FocusBorderStrong else KaraokeDimens.Border,
                color = if (selected) KaraokeColors.AccentPrimary else KaraokeColors.BorderSubtle,
                shape = RoundedCornerShape(KaraokeDimens.RadiusSm),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = KaraokeDimens.SpaceMd, vertical = KaraokeDimens.SpaceXs),
    )
}

/** 阻止点击穿透到下层（如侧边栏遮罩）。 */
@Composable
fun Modifier.consumeClicks(): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(
        indication = null,
        interactionSource = interaction,
        onClick = {},
    )
}
