package com.example.karaoke.ui.navigation

/** 侧边栏焦点区域：Tab 标题行 / 内容区。 */
enum class DrawerFocusZone { Tabs, Content }

object LibraryFocus {
    const val SEARCH = 0
    fun songRow(index: Int) = index + 1
    fun loadMore(songCount: Int) = songCount + 1

    fun itemCount(songCount: Int, hasMore: Boolean): Int {
        var n = 1 + songCount
        if (hasMore) n += 1
        return n
    }

    /** 将内容区焦点索引映射为 LazyColumn 条目索引（搜索框不在列表内则返回 null）。 */
    fun lazyListIndex(focusIndex: Int, songCount: Int, hasMore: Boolean): Int? = when {
        focusIndex <= SEARCH -> null
        focusIndex in 1..songCount -> focusIndex - 1
        hasMore && focusIndex == loadMore(songCount) -> songCount
        else -> null
    }
}

object RandomFocus {
    const val REFRESH = 0
    fun songRow(index: Int) = index + 1

    fun itemCount(songCount: Int): Int = 1 + songCount

    fun lazyListIndex(focusIndex: Int, songCount: Int): Int? = when {
        focusIndex <= REFRESH -> null
        focusIndex in 1..songCount -> focusIndex - 1
        else -> null
    }
}

object SettingsFocus {
    const val URL = 0
    const val TEST = 1
    const val SAVE = 2
    const val COUNT = 3
}
