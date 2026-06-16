package com.example.karaoke.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.karaoke.playback.PlaybackState
import com.example.karaoke.playback.PrepareProgress
import com.example.karaoke.ui.drawer.DrawerPanel
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeTypography

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    playerView: PlayerView,
    onToggleDrawer: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onTabSelected: (com.example.karaoke.ui.navigation.DrawerTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onEnqueue: (Int) -> Unit,
    onSettingsUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSaveReconnect: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(KaraokeColors.BgPrimary)) {
        AndroidView(
            factory = { ctx ->
                (playerView.parent as? ViewGroup)?.removeView(playerView)
                playerView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                playerView.useController = false
                playerView.setKeepContentOnPlayerReset(true)
                playerView
            },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            state.connectionError && !state.drawerOpen -> ConnectionErrorOverlay(onOpenSettings)
            state.prepareProgress != null -> PrepareOverlay(state.prepareProgress)
            state.playbackState is PlaybackState.Idle && state.queue.isEmpty() ->
                IdleOverlay(onMenu = { onToggleDrawer(true) })
        }

        val playing = state.playbackState is PlaybackState.Playing ||
            (state.queue.isNotEmpty() && state.queue.first().isPlaying())
        if (playing && state.overlayVisible && !state.drawerOpen) {
            PlayingTopBar(state)
        }

        if (!state.drawerOpen && state.queue.isNotEmpty() && !playing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                Text(
                    text = "MENU 打开菜单",
                    color = KaraokeColors.TextSecondary.copy(alpha = 0.7f),
                    fontSize = KaraokeTypography.Hint,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        if (state.drawerOpen) {
            DrawerPanel(
                state = state,
                onTabSelected = onTabSelected,
                onDismiss = { onToggleDrawer(false) },
                onQueryChange = onQueryChange,
                onLoadMore = onLoadMore,
                onEnqueue = onEnqueue,
                onSettingsUrlChange = onSettingsUrlChange,
                onTestConnection = onTestConnection,
                onSaveReconnect = onSaveReconnect,
            )
        }
    }
}

@Composable
private fun IdleOverlay(onMenu: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KaraokeColors.OverlayDim),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎤", fontSize = KaraokeTypography.Title)
            Spacer(modifier = Modifier.height(12.dp))
            Text("等待点歌", color = KaraokeColors.TextPrimary, fontSize = KaraokeTypography.Title)
            Spacer(modifier = Modifier.height(8.dp))
            Text("按 MENU 打开点歌菜单", color = KaraokeColors.TextSecondary, fontSize = KaraokeTypography.Body)
        }
    }
}

@Composable
private fun PlayingTopBar(state: PlayerUiState) {
    val current = state.queue.firstOrNull()?.name ?: ""
    val next = state.queue.getOrNull(1)?.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KaraokeColors.OverlayDim)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = current,
            color = KaraokeColors.TextPrimary,
            fontSize = KaraokeTypography.Title,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (next != null) {
            Text(
                text = "下一首：$next",
                color = KaraokeColors.TextSecondary,
                fontSize = KaraokeTypography.Body,
            )
        }
        Text(
            text = if (state.connectionOk) "●" else "○",
            color = if (state.connectionOk) KaraokeColors.Connected else KaraokeColors.Error,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun PrepareOverlay(progress: PrepareProgress?) {
    if (progress == null) return
    val pct = progress.status?.progress?.toFloat()?.div(100f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KaraokeColors.OverlayDim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(KaraokeColors.BgElevated, RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("准备中", color = KaraokeColors.TextPrimary, fontSize = KaraokeTypography.Title)
            Spacer(modifier = Modifier.height(8.dp))
            Text(progress.displayName, color = KaraokeColors.TextSecondary, fontSize = KaraokeTypography.Body)
            Spacer(modifier = Modifier.height(16.dp))
            if (pct != null) {
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = KaraokeColors.AccentPrimary,
                )
            }
            Text(
                text = progress.status?.message ?: "正在准备播放资源…",
                color = KaraokeColors.TextSecondary,
                fontSize = KaraokeTypography.Hint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ConnectionErrorOverlay(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KaraokeColors.OverlayDim),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("无法连接服务器", color = KaraokeColors.TextPrimary, fontSize = KaraokeTypography.Title)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = KaraokeColors.AccentPrimary),
            ) {
                Text("打开设置")
            }
        }
    }
}
