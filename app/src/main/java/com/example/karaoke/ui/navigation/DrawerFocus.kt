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
}

object SettingsFocus {
    const val URL = 0
    const val TEST = 1
    const val SAVE = 2
    const val COUNT = 3
}
