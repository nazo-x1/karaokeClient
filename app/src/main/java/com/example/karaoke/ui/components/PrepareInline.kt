package com.example.karaoke.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.karaoke.data.remote.dto.PrepareStatus
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

fun isPrepareActive(status: PrepareStatus): Boolean =
    !status.ready && status.status in setOf("idle", "pending", "running")

fun isPrepareFailed(status: PrepareStatus): Boolean =
    status.status == "failed"

@Composable
fun PrepareInline(
    prepare: PrepareStatus?,
    modifier: Modifier = Modifier,
) {
    if (prepare == null) return
    val inPrepare = isPrepareActive(prepare)
    val failed = isPrepareFailed(prepare)
    if (!inPrepare && !failed) return

    if (failed) {
        prepare.error?.takeIf { it.isNotBlank() }?.let { error ->
            KaraokeText(
                text = error,
                style = KaraokeTextStyle.Hint,
                color = KaraokeColors.Error,
                modifier = modifier.padding(top = KaraokeDimens.SpaceXs),
            )
        }
        return
    }

    val progress = (prepare.progress?.toFloat() ?: 0f).coerceIn(0f, 100f)
    Column(modifier = modifier.padding(top = KaraokeDimens.SpaceXs)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            KaraokeText(
                text = prepare.message ?: "正在准备播放资源",
                style = KaraokeTextStyle.Hint,
                modifier = Modifier.weight(1f),
            )
            KaraokeText(
                text = "${progress.toInt()}%",
                style = KaraokeTextStyle.Hint,
                color = KaraokeColors.AccentPrimary,
            )
        }
        KaraokeLinearProgress(
            progress = progress / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = KaraokeDimens.SpaceXs),
        )
    }
}
