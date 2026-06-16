package com.example.karaoke.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.karaoke.data.remote.dto.QueueItem
import com.example.karaoke.data.remote.dto.SongItem
import com.example.karaoke.ui.navigation.DrawerTab
import com.example.karaoke.ui.navigation.QueueAction
import com.example.karaoke.ui.navigation.QueueInteractionMode
import com.example.karaoke.ui.player.PlayerUiState
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens
import com.example.karaoke.ui.theme.KaraokeTypography

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
                DrawerTabs(
                    selected = state.drawerTab,
                    onTabSelected = onTabSelected,
                )
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
            Text(
                text = tab.label,
                color = if (isSelected) KaraokeColors.AccentPrimary else KaraokeColors.TextSecondary,
                fontSize = KaraokeTypography.List,
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
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(KaraokeColors.BgElevated, RoundedCornerShape(8.dp))
                .border(1.dp, KaraokeColors.BorderSubtle, RoundedCornerShape(8.dp))
                .padding(12.dp),
            textStyle = TextStyle(color = KaraokeColors.TextPrimary, fontSize = KaraokeTypography.Body),
            cursorBrush = SolidColor(KaraokeColors.AccentPrimary),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("搜索歌曲", color = KaraokeColors.TextSecondary, fontSize = KaraokeTypography.Body)
                }
                inner()
            },
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(songs) { _, song ->
                FocusableRow(onClick = { onEnqueue(song.id) }) {
                    Text(
                        text = song.display_name,
                        color = KaraokeColors.TextPrimary,
                        fontSize = KaraokeTypography.List,
                        modifier = Modifier.weight(1f),
                    )
                    Text("+ 点歌", color = KaraokeColors.AccentPrimary, fontSize = KaraokeTypography.Body)
                }
            }
            if (hasMore && !loading) {
                item {
                    Text(
                        text = "滚动加载更多",
                        color = KaraokeColors.TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoadMore() }
                            .padding(8.dp),
                    )
                }
            }
            if (loading) {
                item {
                    CircularProgressIndicator(
                        color = KaraokeColors.AccentPrimary,
                        modifier = Modifier.padding(8.dp),
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
) {
    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无已点歌曲", color = KaraokeColors.TextSecondary, fontSize = KaraokeTypography.Body)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text(
                        text = "${index + 1}.",
                        color = KaraokeColors.TextSecondary,
                        fontSize = KaraokeTypography.Body,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text = item.name,
                        color = KaraokeColors.TextPrimary,
                        fontSize = KaraokeTypography.List,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when {
                            item.isPlaying() -> "播放中"
                            item.isPending() -> "等待"
                            else -> item.state
                        },
                        color = if (item.isPlaying()) KaraokeColors.Connected else KaraokeColors.TextSecondary,
                        fontSize = KaraokeTypography.Body,
                    )
                }
                if (focused && mode == QueueInteractionMode.Action && !item.isPlaying()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionChip(
                            label = "置顶",
                            selected = action == QueueAction.Top,
                        )
                        ActionChip(
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
private fun ActionChip(label: String, selected: Boolean, secondary: Boolean = false) {
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
    Text(
        text = label,
        color = fg,
        fontSize = KaraokeTypography.Body,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
        Text("服务器地址", color = KaraokeColors.TextSecondary, fontSize = KaraokeTypography.Body)
        BasicTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(KaraokeColors.BgElevated, RoundedCornerShape(8.dp))
                .border(1.dp, KaraokeColors.BorderSubtle, RoundedCornerShape(8.dp))
                .padding(12.dp),
            textStyle = TextStyle(color = KaraokeColors.TextPrimary, fontSize = KaraokeTypography.Body),
            cursorBrush = SolidColor(KaraokeColors.AccentPrimary),
            singleLine = true,
        )
        Text("当前：$savedUrl", color = KaraokeColors.TextSecondary, fontSize = KaraokeTypography.Hint)
        if (error != null) {
            Text(error, color = KaraokeColors.Error, fontSize = KaraokeTypography.Body)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onTest,
                enabled = !testing,
                colors = ButtonDefaults.buttonColors(containerColor = KaraokeColors.BgElevated),
            ) { Text("测试连接") }
            Button(
                onClick = onSave,
                enabled = !testing,
                colors = ButtonDefaults.buttonColors(containerColor = KaraokeColors.AccentPrimary),
            ) { Text("保存并重连") }
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
    Text(
        text = hint,
        color = KaraokeColors.TextSecondary,
        fontSize = KaraokeTypography.Hint,
        modifier = Modifier
            .fillMaxWidth()
            .background(KaraokeColors.BgElevated)
            .padding(12.dp),
    )
}

@Composable
private fun FocusableRow(onClick: () -> Unit, content: @Composable Row.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused) KaraokeColors.BgHover else KaraokeColors.BgElevated,
                RoundedCornerShape(8.dp),
            )
            .border(
                width = if (focused) KaraokeDimens.FocusBorder else 1.dp,
                color = if (focused) KaraokeColors.AccentPrimary else KaraokeColors.BorderSubtle,
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
