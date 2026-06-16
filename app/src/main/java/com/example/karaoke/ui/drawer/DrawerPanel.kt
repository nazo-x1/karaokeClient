package com.example.karaoke.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import com.example.karaoke.data.remote.dto.QueueItem
import com.example.karaoke.data.remote.dto.SongItem
import com.example.karaoke.ui.components.KaraokeActionChip
import com.example.karaoke.ui.components.KaraokeButton
import com.example.karaoke.ui.components.KaraokeButtonVariant
import com.example.karaoke.ui.components.KaraokeFocusableRow
import com.example.karaoke.ui.components.KaraokeHintBar
import com.example.karaoke.ui.components.KaraokeText
import com.example.karaoke.ui.components.KaraokeTextField
import com.example.karaoke.ui.components.KaraokeTextStyle
import com.example.karaoke.ui.navigation.DrawerTab
import com.example.karaoke.ui.navigation.QueueAction
import com.example.karaoke.ui.navigation.QueueInteractionMode
import com.example.karaoke.ui.player.PlayerUiState
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

@Composable
fun DrawerPanel(
    state: PlayerUiState,
    onTabSelected: (DrawerTab) -> Unit,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onEnqueue: (Int) -> Unit,
    onSettingsUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSaveReconnect: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KaraokeColors.OverlayDim)
                .clickable { onDismiss() },
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.End,
        ) {
            Column(
                modifier = Modifier
                    .width(KaraokeDimens.DrawerWidth)
                    .fillMaxHeight()
                    .background(KaraokeColors.BgSecondary)
                    .border(width = 1.dp, color = KaraokeColors.BorderSubtle),
            ) {
                DrawerTabs(selected = state.drawerTab, onTabSelected = onTabSelected)
                Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                    when (state.drawerTab) {
                        DrawerTab.Library -> LibraryTabContent(
                            query = state.libraryQuery,
                            songs = state.librarySongs,
                            loading = state.libraryLoading,
                            hasMore = state.libraryHasMore,
                            onQueryChange = onQueryChange,
                            onLoadMore = onLoadMore,
                            onEnqueue = onEnqueue,
                        )
                        DrawerTab.Queue -> QueueTabContent(
                            queue = state.queue,
                            focusIndex = state.queueFocusIndex,
                            mode = state.queueMode,
                            action = state.queueAction,
                        )
                        DrawerTab.Settings -> SettingsTabContent(
                            url = state.settingsUrl,
                            testing = state.settingsTesting,
                            error = state.settingsError,
                            savedUrl = state.settingsUrl,
                            onUrlChange = onSettingsUrlChange,
                            onTest = onTestConnection,
                            onSave = onSaveReconnect,
                        )
                    }
                }
                DrawerHintBar(state)
            }
        }
    }
}

@Composable
private fun DrawerTabs(selected: DrawerTab, onTabSelected: (DrawerTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KaraokeColors.BgElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DrawerTab.entries.forEach { tab ->
            val isSelected = tab == selected
            KaraokeText(
                text = tab.label,
                style = KaraokeTextStyle.List,
                color = if (isSelected) KaraokeColors.AccentPrimary else KaraokeColors.TextSecondary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .padding(12.dp)
                    .clickable { onTabSelected(tab) },
            )
        }
    }
}

private val DrawerTab.label: String
    get() = when (this) {
        DrawerTab.Library -> "点歌"
        DrawerTab.Queue -> "已点"
        DrawerTab.Settings -> "设置"
    }

@Composable
private fun LibraryTabContent(
    query: String,
    songs: List<SongItem>,
    loading: Boolean,
    hasMore: Boolean,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onEnqueue: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KaraokeTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索歌曲",
        )
        TvLazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(songs) { _, song ->
                KaraokeFocusableRow(onClick = { onEnqueue(song.id) }) {
                    KaraokeText(
                        text = song.display_name,
                        style = KaraokeTextStyle.List,
                        modifier = Modifier.weight(1f),
                    )
                    KaraokeText(text = "+ 点歌", style = KaraokeTextStyle.Body, color = KaraokeColors.AccentPrimary)
                }
            }
            if (hasMore && !loading) {
                item {
                    KaraokeText(
                        text = "滚动加载更多",
                        style = KaraokeTextStyle.Hint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoadMore() }
                            .padding(8.dp),
                    )
                }
            }
            if (loading) {
                item {
                    KaraokeText(text = "加载中…", style = KaraokeTextStyle.Hint, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun QueueTabContent(
    queue: List<QueueItem>,
    focusIndex: Int,
    mode: QueueInteractionMode,
    action: QueueAction,
) {
    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            KaraokeText(text = "暂无已点歌曲", style = KaraokeTextStyle.Hint)
        }
        return
    }
    TvLazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(queue) { index, item ->
            val focused = index == focusIndex
            val borderColor = if (focused) KaraokeColors.AccentPrimary else KaraokeColors.BorderSubtle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (focused) KaraokeColors.BgHover else KaraokeColors.BgElevated,
                        RoundedCornerShape(8.dp),
                    )
                    .border(KaraokeDimens.FocusBorder, borderColor, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KaraokeText(
                        text = "${index + 1}.",
                        style = KaraokeTextStyle.Hint,
                        modifier = Modifier.width(28.dp),
                    )
                    KaraokeText(
                        text = item.name,
                        style = KaraokeTextStyle.List,
                        modifier = Modifier.weight(1f),
                    )
                    KaraokeText(
                        text = when {
                            item.isPlaying() -> "播放中"
                            item.isPending() -> "等待"
                            else -> item.state
                        },
                        style = KaraokeTextStyle.Body,
                        color = if (item.isPlaying()) KaraokeColors.Connected else KaraokeColors.TextSecondary,
                    )
                }
                if (focused && mode == QueueInteractionMode.Action && !item.isPlaying()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KaraokeActionChip(label = "置顶", selected = action == QueueAction.Top)
                        KaraokeActionChip(
                            label = "移除",
                            selected = action == QueueAction.Remove,
                            secondary = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    url: String,
    testing: Boolean,
    error: String?,
    savedUrl: String,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KaraokeText(text = "服务器地址", style = KaraokeTextStyle.Hint)
        KaraokeTextField(value = url, onValueChange = onUrlChange)
        KaraokeText(text = "当前：$savedUrl", style = KaraokeTextStyle.Hint)
        if (error != null) {
            KaraokeText(text = error, style = KaraokeTextStyle.Error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KaraokeButton(
                text = "测试连接",
                onClick = onTest,
                enabled = !testing,
                variant = KaraokeButtonVariant.Secondary,
            )
            KaraokeButton(
                text = "保存并重连",
                onClick = onSave,
                enabled = !testing,
            )
        }
    }
}

@Composable
private fun DrawerHintBar(state: PlayerUiState) {
    val hint = when (state.drawerTab) {
        DrawerTab.Library -> "◀ 关闭 · ◀▶ 切 Tab · OK 点歌"
        DrawerTab.Queue -> when (state.queueMode) {
            QueueInteractionMode.Browse -> "◀ 关闭 · ▲▼ 浏览 · OK 操作"
            QueueInteractionMode.Action -> "◀ 返回 · ◀▶ 切换 · OK 确认"
        }
        DrawerTab.Settings -> "◀ 关闭 · OK 保存"
    }
    KaraokeHintBar(text = hint)
}
