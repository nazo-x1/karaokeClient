package com.example.karaoke.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

object KaraokeColors {
    val BgPrimary = Color(0xFF0B0B12)
    val BgSecondary = Color(0xFF14141F)
    val BgElevated = Color(0xFF1C1C2E)
    val BgHover = Color(0xFF252538)
    val AccentPrimary = Color(0xFFA855F7)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF9494A8)
    val Connected = Color(0xFF69F0AE)
    val BorderSubtle = Color(0xFF2A2A3D)
    val Error = Color(0xFFFF6B6B)
    val OverlayDim = Color(0xCC000000)
}

/** 设计稿常量（1920×1080 画布上的数值，单位 dp/sp）。 */
object DesignTokens {
    const val DRAWER_WIDTH = 630f
    const val SETUP_CARD_WIDTH = 840f
    const val FOCUS_BORDER = 3f
    const val BORDER = 1.5f

    const val TEXT_TITLE = 36f
    const val TEXT_LIST = 30f
    const val TEXT_BODY = 24f
    const val TEXT_HINT = 21f

    const val RADIUS_SM = 12f
    const val RADIUS_MD = 18f
    const val RADIUS_LG = 24f

    const val SPACE_XS = 12f
    const val SPACE_SM = 18f
    const val SPACE_MD = 24f
    const val SPACE_LG = 36f
    const val SPACE_XL = 48f

    const val BUTTON_HEIGHT = 72f
    const val PROGRESS_HEIGHT = 9f
    const val INDEX_WIDTH = 42f
    const val TOAST_BOTTOM = 72f
}

object KaraokeDimens {
    val DrawerWidth: Dp @Composable get() = wdp(DesignTokens.DRAWER_WIDTH)
    val SetupCardWidth: Dp @Composable get() = wdp(DesignTokens.SETUP_CARD_WIDTH)
    val FocusBorder: Dp @Composable get() = wdp(DesignTokens.FOCUS_BORDER)
    val Border: Dp @Composable get() = wdp(DesignTokens.BORDER)
    val SpaceXs: Dp @Composable get() = wdp(DesignTokens.SPACE_XS)
    val SpaceSm: Dp @Composable get() = wdp(DesignTokens.SPACE_SM)
    val SpaceMd: Dp @Composable get() = wdp(DesignTokens.SPACE_MD)
    val SpaceLg: Dp @Composable get() = wdp(DesignTokens.SPACE_LG)
    val SpaceXl: Dp @Composable get() = wdp(DesignTokens.SPACE_XL)
    val RadiusSm: Dp @Composable get() = wdp(DesignTokens.RADIUS_SM)
    val RadiusMd: Dp @Composable get() = wdp(DesignTokens.RADIUS_MD)
    val ButtonHeight: Dp @Composable get() = wdp(DesignTokens.BUTTON_HEIGHT)
}

object KaraokeTypography {
    val Title: TextUnit @Composable get() = ssp(DesignTokens.TEXT_TITLE)
    val List: TextUnit @Composable get() = ssp(DesignTokens.TEXT_LIST)
    val Body: TextUnit @Composable get() = ssp(DesignTokens.TEXT_BODY)
    val Hint: TextUnit @Composable get() = ssp(DesignTokens.TEXT_HINT)
}
