package com.tal.eyedrops.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val Brand = Color(0xFF2E6B47)
val Ink = Color(0xFF17261C)
val Muted = Color(0xFF4A5A4F)
val Line = Color(0xFFD3DDD5)
val Bg = Color(0xFFF3F6F2)
val Warn = Color(0xFFB4530F)
val Ok = Color(0xFF2E7D4F)
val OkSoft = Color(0xFFDCEFE3)
val DoneBg = Color(0xFFF6FAF7)

private val scheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    background = Bg,
    surface = Color.White,
    onSurface = Ink,
)

/** Hebrew UI: always right-to-left, regardless of the phone language. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
