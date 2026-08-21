package com.diskpie.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF101311)
val RaisedInk = Color(0xFF191D1A)
val SoftInk = Color(0xFF222823)
val Leaf = Color(0xFF9FE870)
val Sky = Color(0xFF6DD5ED)
val Cream = Color(0xFFF2F5EE)
val Muted = Color(0xFFA9B2AA)
val Outline = Color(0xFF343C35)
val Warning = Color(0xFFFFB86B)

private val DiskPieColors = darkColorScheme(
    primary = Leaf,
    onPrimary = Ink,
    primaryContainer = Color(0xFF283D21),
    onPrimaryContainer = Color(0xFFD7FFC3),
    secondary = Sky,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = RaisedInk,
    onSurface = Cream,
    surfaceVariant = SoftInk,
    onSurfaceVariant = Muted,
    outline = Outline,
    error = Color(0xFFFF8A80),
)

@Composable
fun DiskPieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DiskPieColors,
        content = content,
    )
}
