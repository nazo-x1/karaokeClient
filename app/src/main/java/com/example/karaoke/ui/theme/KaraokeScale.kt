package com.example.karaoke.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** 设计稿基准画布（16:9 TV）。 */
object DesignCanvas {
    const val WIDTH = 1920f
    const val HEIGHT = 1080f
}

@Stable
data class KaraokeScale(val factor: Float) {
    fun dp(value: Float): Dp = (value * factor).dp
    fun sp(value: Float): TextUnit = (value * factor).sp
}

val LocalKaraokeScale = compositionLocalOf { KaraokeScale(1f) }

@Composable
fun KaraokeScaleProvider(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthDp = if (maxWidth.value > 0f) maxWidth.value else configuration.screenWidthDp.toFloat()
        val heightDp = if (maxHeight.value > 0f) maxHeight.value else configuration.screenHeightDp.toFloat()
        val factor = min(
            widthDp / DesignCanvas.WIDTH,
            heightDp / DesignCanvas.HEIGHT,
        ).coerceIn(0.55f, 1.85f)
        CompositionLocalProvider(LocalKaraokeScale provides KaraokeScale(factor)) {
            content()
        }
    }
}

/** 将设计稿 dp（1920×1080 坐标系）换算为当前屏幕 dp。 */
@Composable
fun wdp(value: Float): Dp = LocalKaraokeScale.current.dp(value)

/** 将设计稿 sp（1920×1080 坐标系）换算为当前屏幕 sp。 */
@Composable
fun ssp(value: Float): TextUnit = LocalKaraokeScale.current.sp(value)
