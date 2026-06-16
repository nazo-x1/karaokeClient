package com.example.karaoke

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
fun KaraokeApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val toastMessage by container.uiMessenger.message.collectAsStateWithLifecycle()

    KaraokeTheme(modifier = modifier) {
        val appViewModel: AppViewModel = viewModel(
            factory = remember(container) {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return AppViewModel(container.repository) as T
                    }
                }
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

        // Toast 仅在 message 非空时渲染，不遮挡触摸
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
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(
                        container.repository,
                        container.settings,
                        container.playbackEngine,
                        container.uiMessenger,
                    ) as T
                }
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
        onPlayerTap = playerViewModel::onPlayerTap,
        onOpenSettings = playerViewModel::openSettingsFromError,
        onTabSelected = playerViewModel::setDrawerTab,
        onQueryChange = playerViewModel::updateLibraryQuery,
        onLoadMore = { playerViewModel.loadLibrary(reset = false) },
        onEnqueue = playerViewModel::enqueueSong,
        onSettingsUrlChange = playerViewModel::updateSettingsUrl,
        onTestConnection = playerViewModel::testConnection,
        onSaveReconnect = playerViewModel::saveAndReconnect,
        onQueueRowTapped = playerViewModel::onQueueRowTapped,
        onQueueActionTapped = playerViewModel::onQueueActionTapped,
    )
}

private fun createPlayerView(context: android.content.Context, container: AppContainer): PlayerView =
    PlayerView(context).apply {
        player = container.playbackEngine.videoPlayer
        useController = false
        setKeepContentOnPlayerReset(true)
        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(android.graphics.Color.BLACK)
    }
