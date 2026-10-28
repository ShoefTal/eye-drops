package com.tal.eyedrops.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val Brand = Color(0xFF1F6F8B)
val Ink = Color(0xFF18232B)
val Muted = Color(0xFF5D6B75)
val Line = Color(0xFFD5DDE2)
val Bg = Color(0xFFEEF2F4)
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
