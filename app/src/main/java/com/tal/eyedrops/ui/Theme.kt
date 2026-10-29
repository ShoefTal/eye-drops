package com.tal.eyedrops.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.tal.eyedrops.data.Store

private val hc get() = Store.highContrast.value

// Charcoal and amber. Colors read the high-contrast switch, so every screen follows it.
val Brand get() = if (hc) Color(0xFF000000) else Color(0xFF2B3440)
val Amber get() = if (hc) Color(0xFFFFC21A) else Color(0xFFF2A93B)
val Ink get() = if (hc) Color(0xFF000000) else Color(0xFF1C1F24)
val Muted get() = if (hc) Color(0xFF1F1F1F) else Color(0xFF50555E)
val Line get() = if (hc) Color(0xFF000000) else Color(0xFFDDDFE3)
val Bg get() = if (hc) Color(0xFFFFFFFF) else Color(0xFFF2F2F0)
val CardBg get() = Color(0xFFFFFFFF)
val SoftFill get() = if (hc) Color(0xFFFFFFFF) else Color(0xFFF2F2F0)
val Warn get() = if (hc) Color(0xFF8A1C12) else Color(0xFFB42318)
val Ok get() = if (hc) Color(0xFF000000) else Color(0xFF2B3440)
val OkSoft get() = if (hc) Color(0xFFE6E6E6) else Color(0xFFE4E6EA)
val DoneBg get() = if (hc) Color(0xFFF2F2F2) else Color(0xFFF8F8F7)
val NowBg get() = Color(0xFFFFFFFF)
val BorderW get() = if (hc) 3 else 1

/** Heebo, bundled at build time; falls back to the phone's font if it is missing. */
private fun heebo(ctx: Context): FontFamily {
    val have = runCatching { ctx.assets.list("fonts")?.toSet() }.getOrNull() ?: emptySet()
    val files = listOf(400 to FontWeight.Normal, 500 to FontWeight.Medium, 700 to FontWeight.Bold, 800 to FontWeight.ExtraBold)
        .filter { "heebo_${it.first}.ttf" in have }
    if (files.isEmpty()) return FontFamily.Default
    return runCatching {
        FontFamily(files.map { (w, fw) -> Font("fonts/heebo_$w.ttf", ctx.assets, fw) })
    }.getOrDefault(FontFamily.Default)
}

private fun Typography.withFamily(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

/** Hebrew UI: always right-to-left; text size = phone setting × the app's own size setting. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val family = remember { heebo(ctx.applicationContext) }
    val scheme = lightColorScheme(
        primary = Brand, onPrimary = Color.White, background = Bg, surface = CardBg, onSurface = Ink,
        secondary = Amber, onSecondary = Ink,
    )
    val d = LocalDensity.current
    MaterialTheme(colorScheme = scheme, typography = Typography().withFamily(family)) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalDensity provides Density(d.density, d.fontScale * Store.textScale.floatValue),
            androidx.compose.material3.LocalTextStyle provides TextStyle(fontFamily = family),
        ) {
            content()
        }
    }
}
