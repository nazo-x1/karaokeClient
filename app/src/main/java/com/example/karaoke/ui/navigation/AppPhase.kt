package com.example.karaoke.ui.navigation

sealed interface AppPhase {
    data object Setup : AppPhase
    data object Player : AppPhase
}

enum class DrawerTab { Library, Random, Queue, Settings }

enum class QueueInteractionMode { Browse, Action }

enum class QueueAction { Top, Remove }
