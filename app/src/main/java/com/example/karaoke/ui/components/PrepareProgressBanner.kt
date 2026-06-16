package com.example.karaoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.karaoke.data.remote.dto.PrepareStatus
import com.example.karaoke.ui.player.PrepareTrack
import com.example.karaoke.ui.theme.KaraokeColors
import com.example.karaoke.ui.theme.KaraokeDimens

@Composable
fun PrepareProgressBanner(
    tracks: List<PrepareTrack>,
    modifier: Modifier = Modifier,
) {
    val active = tracks.filter { !it.status.ready && it.status.status !in setOf("failed", "not_needed") }
    if (active.isEmpty()) return
    val first = active.first()
    val progress = (first.status.progress?.toFloat() ?: 0f).coerceIn(0f, 100f)
    val label = first.status.message ?: "正在准备播放资源"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KaraokeColors.BgElevated, RoundedCornerShape(KaraokeDimens.RadiusSm))
            .padding(KaraokeDimens.SpaceSm),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            KaraokeText(
                text = if (active.size > 1) "$label（${active.size} 首进行中）" else label,
                style = KaraokeTextStyle.Body,
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
        KaraokeText(
            text = "后台处理中，可继续浏览点歌",
            style = KaraokeTextStyle.Hint,
            modifier = Modifier.padding(top = KaraokeDimens.SpaceXs),
        )
    }
}

fun isPrepareActive(status: PrepareStatus): Boolean =
    !status.ready && status.status in setOf("idle", "pending", "running")
