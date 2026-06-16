package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

@Composable
fun KaraokeFocusableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(KaraokeDimens.RadiusSm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) KaraokeColors.BgHover else KaraokeColors.BgElevated,
                shape,
            )
            .border(
                width = if (selected) KaraokeDimens.FocusBorderStrong else KaraokeDimens.Border,
                color = if (selected) KaraokeColors.AccentPrimary else KaraokeColors.BorderSubtle,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(KaraokeDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
