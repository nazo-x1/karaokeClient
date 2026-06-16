package com.example.karaoke

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.example.karaoke.di.AppContainer
import com.example.karaoke.ui.AppViewModel
import com.example.karaoke.ui.components.KaraokeToast
import com.example.karaoke.ui.navigation.AppPhase
import com.example.karaoke.ui.player.PlayerScreen
import com.example.karaoke.ui.player.PlayerViewModel
import com.example.karaoke.ui.setup.SetupScreen
import com.example.karaoke.ui.theme.KaraokeTheme

object KeyEventRouter {
    var handler: ((Int) -> Boolean)? = null
}

@Composable
fun KaraokeApp(container: AppContainer) {
    val toastMessage by container.uiMessenger.message.collectAsStateWithLifecycle()

    KaraokeTheme {
        val appViewModel: AppViewModel = viewModel(
            factory = remember(container) {
                androidx.lifecycle.ViewModelProvider.Factory { AppViewModel(container.repository) }
            },
        )
        val phase by appViewModel.phase.collectAsStateWithLifecycle()
        val connecting by appViewModel.connecting.collectAsStateWithLifecycle()
        val setupError by appViewModel.setupError.collectAsStateWithLifecycle()

        DisposableEffect(phase) {
            if (phase != AppPhase.Player) KeyEventRouter.handler = null
            onDispose { KeyEventRouter.handler = null }
        }

        when (phase) {
            AppPhase.Setup -> SetupScreen(
                initialUrl = container.settings.server,
                connecting = connecting,
                error = setupError,
                onConnect = appViewModel::connect,
            )
            AppPhase.Player -> PlayerRoot(container)
        }

        KaraokeToast(
            message = toastMessage,
            onDismiss = container.uiMessenger::dismiss,
        )
    }
}

@Composable
private fun PlayerRoot(container: AppContainer) {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = viewModel(
        factory = remember(container) {
            androidx.lifecycle.ViewModelProvider.Factory {
                PlayerViewModel(
                    container.repository,
                    container.settings,
                    container.playbackEngine,
                    container.uiMessenger,
                )
            }
        },
    )
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val exoPlayerView = remember(context) { createPlayerView(context, container) }

    DisposableEffect(playerViewModel) {
        KeyEventRouter.handler = playerViewModel::handleKey
        onDispose { KeyEventRouter.handler = null }
    }

    PlayerScreen(
        state = playerState,
        playerView = exoPlayerView,
        onToggleDrawer = playerViewModel::toggleDrawer,
        onOpenSettings = playerViewModel::openSettingsFromError,
        onTabSelected = playerViewModel::setDrawerTab,
        onQueryChange = playerViewModel::updateLibraryQuery,
        onLoadMore = { playerViewModel.loadLibrary(reset = false) },
        onEnqueue = playerViewModel::enqueueSong,
        onSettingsUrlChange = playerViewModel::updateSettingsUrl,
        onTestConnection = playerViewModel::testConnection,
        onSaveReconnect = playerViewModel::saveAndReconnect,
    )
}

private fun createPlayerView(context: android.content.Context, container: AppContainer): PlayerView =
    PlayerView(context).apply {
        player = container.playbackEngine.videoPlayer
        useController = false
        setKeepContentOnPlayerReset(true)
    }
