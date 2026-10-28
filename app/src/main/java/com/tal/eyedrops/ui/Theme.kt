package com.tal.eyedrops.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.tal.eyedrops.data.Store

private val hc get() = Store.highContrast.value

// Colors read the high-contrast switch, so every screen follows it.
val Brand get() = if (hc) Color(0xFF14532D) else Color(0xFF2E6B47)
val Ink get() = if (hc) Color(0xFF000000) else Color(0xFF17261C)
val Muted get() = if (hc) Color(0xFF1F1F1F) else Color(0xFF3F4E44)
val Line get() = if (hc) Color(0xFF000000) else Color(0xFFC9D5CC)
val Bg get() = if (hc) Color(0xFFFFFFFF) else Color(0xFFF3F6F2)
val CardBg get() = Color(0xFFFFFFFF)
val Warn get() = if (hc) Color(0xFF7A2E00) else Color(0xFFA34A0D)
val Ok get() = if (hc) Color(0xFF14532D) else Color(0xFF2E7D4F)
val OkSoft get() = if (hc) Color(0xFFD7F0DE) else Color(0xFFDCEFE3)
val DoneBg get() = if (hc) Color(0xFFEFF7F1) else Color(0xFFF6FAF7)
val NowBg get() = if (hc) Color(0xFFFFE45C) else Color(0xFFFFFFFF)
val BorderW get() = if (hc) 3 else 2

/** Hebrew UI: always right-to-left; text size = phone setting × the app's own size setting. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Brand, onPrimary = Color.White, background = Bg, surface = CardBg, onSurface = Ink,
    )
    val d = LocalDensity.current
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalDensity provides Density(d.density, d.fontScale * Store.textScale.floatValue),
        ) {
            content()
        }
    }
}
