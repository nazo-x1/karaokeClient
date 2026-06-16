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
import com.example.karaoke.ui.components.KaraokeButton
import com.example.karaoke.ui.components.KaraokeCard
import com.example.karaoke.ui.components.KaraokeLinearProgress
import com.example.karaoke.ui.components.KaraokeOverlay
import com.example.karaoke.ui.components.KaraokeScreen
import com.example.karaoke.ui.components.KaraokeText
import com.example.karaoke.ui.components.KaraokeTextStyle
import com.example.karaoke.ui.drawer.DrawerPanel
import com.example.karaoke.ui.theme.KaraokeColors

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
    KaraokeScreen {
        AndroidView(
            factory = {
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
                IdleOverlay()
        }

        val playing = state.playbackState is PlaybackState.Playing ||
            (state.queue.isNotEmpty() && state.queue.first().isPlaying())
        if (playing && state.overlayVisible && !state.drawerOpen) {
            PlayingTopBar(state)
        }

        if (!state.drawerOpen && state.queue.isNotEmpty() && !playing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                KaraokeText(
                    text = "MENU 打开菜单",
                    style = KaraokeTextStyle.Hint,
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
private fun IdleOverlay() {
    KaraokeOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KaraokeText(text = "🎤", style = KaraokeTextStyle.Title)
            Spacer(modifier = Modifier.height(12.dp))
            KaraokeText(text = "等待点歌", style = KaraokeTextStyle.Title)
            Spacer(modifier = Modifier.height(8.dp))
            KaraokeText(text = "按 MENU 打开点歌菜单", style = KaraokeTextStyle.Hint)
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
        KaraokeText(
            text = current,
            style = KaraokeTextStyle.Title,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (next != null) {
            KaraokeText(text = "下一首：$next", style = KaraokeTextStyle.Hint)
        }
        KaraokeText(
            text = if (state.connectionOk) "●" else "○",
            style = KaraokeTextStyle.Body,
            color = if (state.connectionOk) KaraokeColors.Connected else KaraokeColors.Error,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun PrepareOverlay(progress: PrepareProgress?) {
    if (progress == null) return
    val pct = progress.status?.progress?.toFloat()?.div(100f)
    KaraokeOverlay {
        KaraokeCard {
            KaraokeText(text = "准备中", style = KaraokeTextStyle.Title)
            Spacer(modifier = Modifier.height(8.dp))
            KaraokeText(text = progress.displayName, style = KaraokeTextStyle.Hint)
            Spacer(modifier = Modifier.height(16.dp))
            if (pct != null) {
                KaraokeLinearProgress(progress = pct)
            }
            Spacer(modifier = Modifier.height(8.dp))
            KaraokeText(
                text = progress.status?.message ?: "正在准备播放资源…",
                style = KaraokeTextStyle.Hint,
            )
        }
    }
}

@Composable
private fun ConnectionErrorOverlay(onOpenSettings: () -> Unit) {
    KaraokeOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KaraokeText(text = "无法连接服务器", style = KaraokeTextStyle.Title)
            Spacer(modifier = Modifier.height(16.dp))
            KaraokeButton(text = "打开设置", onClick = onOpenSettings)
        }
    }
}
