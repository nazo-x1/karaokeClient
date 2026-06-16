package com.example.karaoke.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.karaoke.playback.PlaybackState
import com.example.karaoke.playback.PrepareProgress
import com.example.karaoke.ui.components.KaraokeButton
import com.example.karaoke.ui.components.KaraokeLinearProgress
import com.example.karaoke.ui.components.KaraokePlayerCanvas
import com.example.karaoke.ui.components.KaraokePlayerOverlay
import com.example.karaoke.ui.components.KaraokeText
import com.example.karaoke.ui.components.KaraokeTextStyle
import com.example.karaoke.ui.drawer.DrawerPanel
import com.example.karaoke.ui.navigation.QueueAction
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    playerView: PlayerView,
    onToggleDrawer: (Boolean) -> Unit,
    onPlayerTap: () -> Unit,
    onOpenSettings: () -> Unit,
    onTabSelected: (com.example.karaoke.ui.navigation.DrawerTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onEnqueue: (Int, String) -> Unit,
    onRefreshRandom: () -> Unit,
    onSettingsUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSaveReconnect: () -> Unit,
    onQueueRowTapped: (Int) -> Unit,
    onQueueActionTapped: (QueueAction) -> Unit,
) {
    val touchEnabled = !state.drawerOpen &&
        state.prepareProgress == null &&
        !(state.connectionError && !state.drawerOpen)

    KaraokePlayerCanvas {
        AndroidView(
            factory = {
                (playerView.parent as? ViewGroup)?.removeView(playerView)
                playerView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                playerView.useController = false
                playerView.setKeepContentOnPlayerReset(true)
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
                playerView
            },
            update = { view ->
                if (view.player == null && playerView.player != null) {
                    view.player = playerView.player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (touchEnabled) {
            val tapInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = tapInteraction,
                        onClick = onPlayerTap,
                    ),
            )
        }

        when {
            state.connectionError && !state.drawerOpen -> ConnectionErrorOverlay(onOpenSettings)
            state.prepareProgress != null -> PrepareOverlay(state.prepareProgress)
            state.playbackState is PlaybackState.Idle && state.queue.isEmpty() ->
                IdleOverlay(onOpenMenu = { onToggleDrawer(true) })
        }

        val playing = state.playbackState is PlaybackState.Playing ||
            (state.queue.isNotEmpty() && state.queue.first().isPlaying())
        if (playing && state.overlayVisible && !state.drawerOpen) {
            PlayingTopBar(state)
        }

        if (!state.drawerOpen) {
            KaraokeText(
                text = "MENU 菜单",
                style = KaraokeTextStyle.Hint,
                color = KaraokeColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(KaraokeDimens.SpaceLg)
                    .clickable { onToggleDrawer(true) }
                    .padding(horizontal = KaraokeDimens.SpaceSm, vertical = KaraokeDimens.SpaceXs),
            )
        }

        if (state.drawerOpen) {
            DrawerPanel(
                state = state,
                onTabSelected = onTabSelected,
                onDismiss = { onToggleDrawer(false) },
                onQueryChange = onQueryChange,
                onLoadMore = onLoadMore,
                onEnqueue = onEnqueue,
                onRefreshRandom = onRefreshRandom,
                onSettingsUrlChange = onSettingsUrlChange,
                onTestConnection = onTestConnection,
                onSaveReconnect = onSaveReconnect,
                onQueueRowTapped = onQueueRowTapped,
                onQueueActionTapped = onQueueActionTapped,
            )
        }
    }
}

@Composable
private fun IdleOverlay(onOpenMenu: () -> Unit) {
    KaraokePlayerOverlay(
        modifier = Modifier.clickable(onClick = onOpenMenu),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KaraokeText(text = "等待点歌", style = KaraokeTextStyle.Title)
            Spacer(modifier = Modifier.height(KaraokeDimens.SpaceXs))
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
            .padding(horizontal = KaraokeDimens.SpaceLg, vertical = KaraokeDimens.SpaceMd),
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
            modifier = Modifier.padding(start = KaraokeDimens.SpaceSm),
        )
    }
}

@Composable
private fun PrepareOverlay(progress: PrepareProgress?) {
    if (progress == null) return
    val pct = progress.status?.progress?.toFloat()?.div(100f)
    KaraokePlayerOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KaraokeText(text = "正在准备播放资源", style = KaraokeTextStyle.Title)
            Spacer(modifier = Modifier.height(KaraokeDimens.SpaceXs))
            KaraokeText(text = progress.displayName, style = KaraokeTextStyle.Hint)
            Spacer(modifier = Modifier.height(KaraokeDimens.SpaceMd))
            if (pct != null) {
                KaraokeLinearProgress(
                    progress = pct,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
            Spacer(modifier = Modifier.height(KaraokeDimens.SpaceXs))
            KaraokeText(
                text = progress.status?.message ?: "正在准备播放资源…",
                style = KaraokeTextStyle.Hint,
            )
        }
    }
}

@Composable
private fun ConnectionErrorOverlay(onOpenSettings: () -> Unit) {
    KaraokePlayerOverlay {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KaraokeText(text = "无法连接服务器", style = KaraokeTextStyle.Title)
            Spacer(modifier = Modifier.height(KaraokeDimens.SpaceXs))
            KaraokeText(text = "请检查地址与网络，按 MENU 打开设置", style = KaraokeTextStyle.Hint)
            Spacer(modifier = Modifier.height(KaraokeDimens.SpaceMd))
            KaraokeButton(text = "打开设置", onClick = onOpenSettings)
        }
    }
}
