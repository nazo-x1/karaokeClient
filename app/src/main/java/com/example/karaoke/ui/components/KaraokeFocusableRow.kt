package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

@Composable
fun KaraokeFocusableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (focused) KaraokeColors.BgHover else KaraokeColors.BgElevated,
                RoundedCornerShape(KaraokeDimens.RadiusSm),
            )
            .border(
                width = if (focused) KaraokeDimens.FocusBorder else KaraokeDimens.Border,
                color = if (focused) KaraokeColors.AccentPrimary else KaraokeColors.BorderSubtle,
                shape = RoundedCornerShape(KaraokeDimens.RadiusSm),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(KaraokeDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
