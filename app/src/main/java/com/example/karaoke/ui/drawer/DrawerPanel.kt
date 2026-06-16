package com.example.karaoke.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.example.karaoke.ui.components.consumeClicks
import com.example.karaoke.ui.navigation.DrawerTab
import com.example.karaoke.ui.navigation.QueueAction
import com.example.karaoke.ui.navigation.QueueInteractionMode
import com.example.karaoke.ui.player.PlayerUiState
import com.example.karaoke.ui.theme.DesignTokens
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens
import com.example.karaoke.ui.theme.wdp

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
    onQueueRowTapped: (Int) -> Unit,
    onQueueActionTapped: (QueueAction) -> Unit,
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
                    .consumeClicks()
                    .background(KaraokeColors.BgSecondary)
                    .border(width = KaraokeDimens.Border, color = KaraokeColors.BorderSubtle),
            ) {
                DrawerTabs(selected = state.drawerTab, onTabSelected = onTabSelected)
                Box(modifier = Modifier.weight(1f).padding(KaraokeDimens.SpaceMd)) {
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
                            onRowTapped = onQueueRowTapped,
                            onActionTapped = onQueueActionTapped,
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
            .padding(horizontal = KaraokeDimens.SpaceXs, vertical = wdp(6f)),
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
                    .padding(KaraokeDimens.SpaceSm)
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
    Column(verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceSm)) {
        KaraokeTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索歌曲",
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceXs)) {
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
                        text = "点击加载更多",
                        style = KaraokeTextStyle.Hint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoadMore() }
                            .padding(KaraokeDimens.SpaceXs),
                    )
                }
            }
            if (loading) {
                item {
                    KaraokeText(
                        text = "加载中…",
                        style = KaraokeTextStyle.Hint,
                        modifier = Modifier.padding(KaraokeDimens.SpaceXs),
                    )
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
    onRowTapped: (Int) -> Unit,
    onActionTapped: (QueueAction) -> Unit,
) {
    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            KaraokeText(text = "暂无已点歌曲", style = KaraokeTextStyle.Hint)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceXs)) {
        itemsIndexed(queue) { index, item ->
            val focused = index == focusIndex
            val borderColor = if (focused) KaraokeColors.AccentPrimary else KaraokeColors.BorderSubtle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (focused) KaraokeColors.BgHover else KaraokeColors.BgElevated,
                        RoundedCornerShape(KaraokeDimens.RadiusSm),
                    )
                    .border(KaraokeDimens.FocusBorder, borderColor, RoundedCornerShape(KaraokeDimens.RadiusSm))
                    .clickable { onRowTapped(index) }
                    .padding(KaraokeDimens.SpaceSm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KaraokeText(
                        text = "${index + 1}.",
                        style = KaraokeTextStyle.Hint,
                        modifier = Modifier.width(wdp(DesignTokens.INDEX_WIDTH)),
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
                    Spacer(modifier = Modifier.height(KaraokeDimens.SpaceXs))
                    Row(horizontalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceSm)) {
                        KaraokeActionChip(
                            label = "置顶",
                            selected = action == QueueAction.Top,
                            onClick = { onActionTapped(QueueAction.Top) },
                        )
                        KaraokeActionChip(
                            label = "移除",
                            selected = action == QueueAction.Remove,
                            onClick = { onActionTapped(QueueAction.Remove) },
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
    Column(verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceSm)) {
        KaraokeText(text = "服务器地址", style = KaraokeTextStyle.Hint)
        KaraokeTextField(value = url, onValueChange = onUrlChange)
        KaraokeText(text = "当前：$savedUrl", style = KaraokeTextStyle.Hint)
        if (error != null) {
            KaraokeText(text = error, style = KaraokeTextStyle.Error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceSm)) {
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
        DrawerTab.Library -> "点击遮罩关闭 · 点击歌曲点歌"
        DrawerTab.Queue -> when (state.queueMode) {
            QueueInteractionMode.Browse -> "点击歌曲进入操作 · 点击遮罩关闭"
            QueueInteractionMode.Action -> "点击置顶/移除 · 再次点击歌曲取消"
        }
        DrawerTab.Settings -> "点击遮罩关闭 · 点击按钮保存"
    }
    KaraokeHintBar(text = hint)
}
