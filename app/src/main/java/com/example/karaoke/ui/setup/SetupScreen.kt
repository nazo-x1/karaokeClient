package com.example.karaoke.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.karaoke.ui.components.KaraokeCard
import com.example.karaoke.ui.components.KaraokeHintBar
import com.example.karaoke.ui.components.KaraokeLoadingButton
import com.example.karaoke.ui.components.KaraokeScreen
import com.example.karaoke.ui.components.KaraokeText
import com.example.karaoke.ui.components.KaraokeTextField
import com.example.karaoke.ui.components.KaraokeTextStyle
import com.example.karaoke.ui.theme.KaraokeDimens

@Composable
fun SetupScreen(
    initialUrl: String,
    connecting: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }

    KaraokeScreen {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.width(KaraokeDimens.SetupCardWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KaraokeCard {
                    KaraokeText(text = "连接 KTV 服务器", style = KaraokeTextStyle.Title)
                    Spacer(modifier = Modifier.height(8.dp))
                    KaraokeText(
                        text = "请输入局域网服务器地址，例如 http://192.168.1.20:15233",
                        style = KaraokeTextStyle.Hint,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    KaraokeTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = "http://192.168.x.x:15233",
                    )
                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        KaraokeText(text = error, style = KaraokeTextStyle.Error)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    KaraokeLoadingButton(
                        text = "连接",
                        loading = connecting,
                        onClick = { onConnect(url) },
                    )
                }
                KaraokeHintBar("◀ 后退退出应用")
            }
        }
    }
}
