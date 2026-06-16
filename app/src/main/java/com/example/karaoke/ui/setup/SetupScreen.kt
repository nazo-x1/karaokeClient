package com.example.karaoke.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens
import com.example.karaoke.ui.theme.KaraokeTypography

@Composable
fun SetupScreen(
    initialUrl: String,
    connecting: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KaraokeColors.BgPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(KaraokeDimens.SetupCardWidth)
                .background(KaraokeColors.BgElevated, RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "连接 KTV 服务器",
                color = KaraokeColors.TextPrimary,
                fontSize = KaraokeTypography.Title,
            )
            Text(
                text = "请输入局域网服务器地址，例如 http://192.168.1.20:15233",
                color = KaraokeColors.TextSecondary,
                fontSize = KaraokeTypography.Body,
            )
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KaraokeColors.BgSecondary, RoundedCornerShape(8.dp))
                    .border(1.dp, KaraokeColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(
                    color = KaraokeColors.TextPrimary,
                    fontSize = KaraokeTypography.List,
                ),
                cursorBrush = SolidColor(KaraokeColors.AccentPrimary),
                singleLine = true,
            )
            if (error != null) {
                Text(text = error, color = KaraokeColors.Error, fontSize = KaraokeTypography.Body)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onConnect(url) },
                enabled = !connecting,
                colors = ButtonDefaults.buttonColors(containerColor = KaraokeColors.AccentPrimary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        color = KaraokeColors.TextPrimary,
                        modifier = Modifier.height(20.dp),
                    )
                } else {
                    Text("连接", fontSize = KaraokeTypography.List)
                }
            }
            Text(
                text = "◀ 后退退出应用",
                color = KaraokeColors.TextSecondary,
                fontSize = KaraokeTypography.Hint,
            )
        }
    }
}
