package com.example.karaoke.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** 遥控器焦点变化时，让 LazyColumn 自动滚动到可见区域。 */
@Composable
fun LaunchedEffectFocusScroll(
    focusIndex: Int,
    contentFocused: Boolean,
    listState: LazyListState,
    lazyIndexForFocus: (Int) -> Int?,
) {
    LaunchedEffect(focusIndex, contentFocused) {
        if (!contentFocused) return@LaunchedEffect
        val lazyIndex = lazyIndexForFocus(focusIndex) ?: return@LaunchedEffect
        listState.scrollToItem(lazyIndex.coerceAtLeast(0))
    }
}
