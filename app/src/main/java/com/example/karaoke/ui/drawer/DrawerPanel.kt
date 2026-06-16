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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.karaoke.ui.components.LaunchedEffectFocusScroll
import com.example.karaoke.ui.components.PrepareProgressBanner
import com.example.karaoke.ui.navigation.RandomFocus
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.example.karaoke.ui.navigation.DrawerFocusZone
import com.example.karaoke.ui.navigation.DrawerTab
import com.example.karaoke.ui.navigation.LibraryFocus
import com.example.karaoke.ui.navigation.QueueAction
import com.example.karaoke.ui.navigation.QueueInteractionMode
import com.example.karaoke.ui.navigation.SettingsFocus
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
    onEnqueue: (Int, String) -> Unit,
    onRefreshRandom: () -> Unit,
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
                DrawerTabs(
                    selected = state.drawerTab,
                    focusZone = state.drawerFocusZone,
                    onTabSelected = onTabSelected,
                )
                if (state.prepareTracks.isNotEmpty()) {
                    PrepareProgressBanner(
                        tracks = state.prepareTracks,
                        modifier = Modifier.padding(horizontal = KaraokeDimens.SpaceMd, vertical = KaraokeDimens.SpaceXs),
                    )
                }
                Box(modifier = Modifier.weight(1f).padding(horizontal = KaraokeDimens.SpaceMd)) {
                    when (state.drawerTab) {
                        DrawerTab.Library -> LibraryTabContent(
                            query = state.libraryQuery,
                            songs = state.librarySongs,
                            loading = state.libraryLoading,
                            hasMore = state.libraryHasMore,
                            focusIndex = state.libraryFocusIndex,
                            contentFocused = state.drawerFocusZone == DrawerFocusZone.Content,
                            onQueryChange = onQueryChange,
                            onLoadMore = onLoadMore,
                            onEnqueue = onEnqueue,
                        )
                        DrawerTab.Random -> RandomTabContent(
                            songs = state.randomSongs,
                            loading = state.randomLoading,
                            focusIndex = state.randomFocusIndex,
                            contentFocused = state.drawerFocusZone == DrawerFocusZone.Content,
                            onRefresh = onRefreshRandom,
                            onEnqueue = onEnqueue,
                        )
                        DrawerTab.Queue -> QueueTabContent(
                            queue = state.queue,
                            focusIndex = state.queueFocusIndex,
                            contentFocused = state.drawerFocusZone == DrawerFocusZone.Content,
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
                            focusIndex = state.settingsFocusIndex,
                            contentFocused = state.drawerFocusZone == DrawerFocusZone.Content,
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
private fun DrawerTabs(
    selected: DrawerTab,
    focusZone: DrawerFocusZone,
    onTabSelected: (DrawerTab) -> Unit,
) {
    val shape = RoundedCornerShape(KaraokeDimens.RadiusSm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KaraokeColors.BgElevated)
            .padding(horizontal = KaraokeDimens.SpaceXs, vertical = wdp(6f)),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DrawerTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val isFocused = focusZone == DrawerFocusZone.Tabs && isSelected
            Box(
                modifier = Modifier
                    .border(
                        width = when {
                            isFocused -> KaraokeDimens.FocusBorderStrong
                            isSelected -> KaraokeDimens.FocusBorder
                            else -> KaraokeDimens.Border
                        },
                        color = when {
                            isFocused || isSelected -> KaraokeColors.AccentPrimary
                            else -> KaraokeColors.BorderSubtle
                        },
                        shape = shape,
                    )
                    .background(
                        if (isFocused) KaraokeColors.BgHover else KaraokeColors.BgElevated,
                        shape,
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = KaraokeDimens.SpaceSm, vertical = KaraokeDimens.SpaceXs),
                contentAlignment = Alignment.Center,
            ) {
                KaraokeText(
                    text = tab.label,
                    style = KaraokeTextStyle.List,
                    color = if (isSelected) KaraokeColors.AccentPrimary else KaraokeColors.TextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private val DrawerTab.label: String
    get() = when (this) {
        DrawerTab.Library -> "点歌"
        DrawerTab.Random -> "推荐"
        DrawerTab.Queue -> "已点"
        DrawerTab.Settings -> "设置"
    }

@Composable
private fun LibraryTabContent(
    query: String,
    songs: List<SongItem>,
    loading: Boolean,
    hasMore: Boolean,
    focusIndex: Int,
    contentFocused: Boolean,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onEnqueue: (Int, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceSm),
    ) {
        KaraokeTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索歌曲",
            selected = contentFocused && focusIndex == LibraryFocus.SEARCH,
        )
        val listState = rememberLazyListState()
        LaunchedEffectFocusScroll(
            focusIndex = focusIndex,
            contentFocused = contentFocused,
            listState = listState,
            lazyIndexForFocus = { idx ->
                LibraryFocus.lazyListIndex(idx, songs.size, hasMore && !loading)
            },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceXs),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                KaraokeFocusableRow(
                    selected = contentFocused && focusIndex == LibraryFocus.songRow(index),
                    onClick = { onEnqueue(song.id, song.display_name) },
                ) {
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
                    val loadMoreIndex = LibraryFocus.loadMore(songs.size)
                    val shape = RoundedCornerShape(KaraokeDimens.RadiusSm)
                    KaraokeText(
                        text = "加载更多",
                        style = KaraokeTextStyle.Hint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (contentFocused && focusIndex == loadMoreIndex) {
                                    KaraokeColors.BgHover
                                } else {
                                    KaraokeColors.BgElevated
                                },
                                shape,
                            )
                            .border(
                                width = if (contentFocused && focusIndex == loadMoreIndex) {
                                    KaraokeDimens.FocusBorderStrong
                                } else {
                                    KaraokeDimens.Border
                                },
                                color = if (contentFocused && focusIndex == loadMoreIndex) {
                                    KaraokeColors.AccentPrimary
                                } else {
                                    KaraokeColors.BorderSubtle
                                },
                                shape = shape,
                            )
                            .clickable { onLoadMore() }
                            .padding(KaraokeDimens.SpaceSm),
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
private fun RandomTabContent(
    songs: List<SongItem>,
    loading: Boolean,
    focusIndex: Int,
    contentFocused: Boolean,
    onRefresh: () -> Unit,
    onEnqueue: (Int, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceSm),
    ) {
        KaraokeButton(
            text = if (loading) "加载中…" else "换一批推荐",
            onClick = onRefresh,
            enabled = !loading,
            selected = contentFocused && focusIndex == RandomFocus.REFRESH,
            variant = KaraokeButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        val listState = rememberLazyListState()
        LaunchedEffectFocusScroll(
            focusIndex = focusIndex,
            contentFocused = contentFocused,
            listState = listState,
            lazyIndexForFocus = { idx -> RandomFocus.lazyListIndex(idx, songs.size) },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceXs),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                KaraokeFocusableRow(
                    selected = contentFocused && focusIndex == RandomFocus.songRow(index),
                    onClick = { onEnqueue(song.id, song.display_name) },
                ) {
                    KaraokeText(
                        text = song.display_name,
                        style = KaraokeTextStyle.List,
                        modifier = Modifier.weight(1f),
                    )
                    KaraokeText(text = "+ 点歌", style = KaraokeTextStyle.Body, color = KaraokeColors.AccentPrimary)
                }
            }
            if (!loading && songs.isEmpty()) {
                item {
                    KaraokeText(
                        text = "暂无可推荐歌曲",
                        style = KaraokeTextStyle.Hint,
                        modifier = Modifier.padding(KaraokeDimens.SpaceSm),
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
    contentFocused: Boolean,
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
    val listState = rememberLazyListState()
    LaunchedEffectFocusScroll(
        focusIndex = focusIndex,
        contentFocused = contentFocused,
        listState = listState,
        lazyIndexForFocus = { it },
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceXs),
    ) {
        itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
            val focused = contentFocused && index == focusIndex
            val borderWidth = if (focused) KaraokeDimens.FocusBorderStrong else KaraokeDimens.Border
            val borderColor = if (focused) KaraokeColors.AccentPrimary else KaraokeColors.BorderSubtle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (focused) KaraokeColors.BgHover else KaraokeColors.BgElevated,
                        RoundedCornerShape(KaraokeDimens.RadiusSm),
                    )
                    .border(borderWidth, borderColor, RoundedCornerShape(KaraokeDimens.RadiusSm))
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
    focusIndex: Int,
    contentFocused: Boolean,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KaraokeDimens.SpaceSm)) {
        KaraokeText(text = "服务器地址", style = KaraokeTextStyle.Hint)
        KaraokeTextField(
            value = url,
            onValueChange = onUrlChange,
            selected = contentFocused && focusIndex == SettingsFocus.URL,
        )
        KaraokeText(text = "当前：$savedUrl", style = KaraokeTextStyle.Hint)
        if (error != null) {
            KaraokeText(text = error, style = KaraokeTextStyle.Error)
        }
        KaraokeButton(
            text = "测试连接",
            onClick = onTest,
            enabled = !testing,
            selected = contentFocused && focusIndex == SettingsFocus.TEST,
            variant = KaraokeButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        KaraokeButton(
            text = "保存并重连",
            onClick = onSave,
            enabled = !testing,
            selected = contentFocused && focusIndex == SettingsFocus.SAVE,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DrawerHintBar(state: PlayerUiState) {
    val hint = when (state.drawerTab) {
        DrawerTab.Library -> when (state.drawerFocusZone) {
            DrawerFocusZone.Tabs -> "◀▶ 切换 Tab · ▼ 进入列表"
            DrawerFocusZone.Content -> "▲▼ 移动 · ◀▶ 切换 Tab · OK 点歌"
        }
        DrawerTab.Random -> when (state.drawerFocusZone) {
            DrawerFocusZone.Tabs -> "◀▶ 切换 Tab · ▼ 进入列表"
            DrawerFocusZone.Content -> "▲▼ 移动 · ◀▶ 切换 Tab · OK 点歌/刷新"
        }
        DrawerTab.Queue -> when (state.queueMode) {
            QueueInteractionMode.Browse -> when (state.drawerFocusZone) {
                DrawerFocusZone.Tabs -> "◀▶ 切换 Tab · ▼ 进入列表"
                DrawerFocusZone.Content -> "▲▼ 移动 · ◀▶ 切换 Tab · OK 操作"
            }
            QueueInteractionMode.Action -> "◀ 取消 · ▶ 切换置顶/移除 · OK 确认"
        }
        DrawerTab.Settings -> when (state.drawerFocusZone) {
            DrawerFocusZone.Tabs -> "◀▶ 切换 Tab · ▼ 进入设置项"
            DrawerFocusZone.Content -> "▲▼ 移动 · ◀▶ 切换 Tab · OK 执行"
        }
    }
    KaraokeHintBar(text = hint)
}
