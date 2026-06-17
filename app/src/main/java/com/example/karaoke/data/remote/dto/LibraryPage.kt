package com.example.karaoke.data.remote.dto

data class LibraryPage(
    val songs: List<SongItem>,
    val hasMore: Boolean,
)
