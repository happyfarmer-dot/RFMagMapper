package com.rfmagmapper.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDeep = Color(0xFF0B1220)
val SurfaceCard = Color(0xFF131C2E)
val SurfaceElevated = Color(0xFF1C2840)
val CyanAccent = Color(0xFF00E5FF)
val GreenSafe = Color(0xFF00E676)
val YellowWarn = Color(0xFFFFEA00)
val RedHotspot = Color(0xFFFF1744)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

private val RFMagDarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = BgDeep,
    secondary = GreenSafe,
    onSecondary = BgDeep,
    tertiary = YellowWarn,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = RedHotspot
)

@Composable
fun RFMagMapperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RFMagDarkColorScheme,
        content = content
    )
}
