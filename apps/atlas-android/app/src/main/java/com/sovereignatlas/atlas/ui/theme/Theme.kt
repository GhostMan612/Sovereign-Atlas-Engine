// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AtlasExtendedColors(
    val chrome: Color,
    val chromeDim: Color,
    val navCyan: Color,
    val cautionAmber: Color,
    val fieldOlive: Color,
    val alertRed: Color,
)

val LocalAtlasExtendedColors = staticCompositionLocalOf {
    AtlasExtendedColors(
        chrome = BrushedChrome,
        chromeDim = ChromeDim,
        navCyan = NavigationCyan,
        cautionAmber = CautionAmber,
        fieldOlive = FieldOlive,
        alertRed = AlertRed,
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = NavigationCyan,
    onPrimary = MatteCharcoal,
    primaryContainer = Graphite,
    onPrimaryContainer = BrushedChrome,
    secondary = BrushedChrome,
    onSecondary = MatteCharcoal,
    secondaryContainer = SurfaceDarkElevated,
    onSecondaryContainer = PureWhite,
    tertiary = CautionAmber,
    onTertiary = MatteCharcoal,
    background = MatteCharcoal,
    onBackground = BrushedChrome,
    surface = SurfaceDark,
    onSurface = PureWhite,
    surfaceVariant = Graphite,
    onSurfaceVariant = BrushedChrome,
    error = AlertRed,
    onError = PureWhite,
)

private val LightColorScheme = lightColorScheme(
    primary = NavigationCyanDark,
    onPrimary = PureWhite,
    primaryContainer = SurfaceLightElevated,
    onPrimaryContainer = GraphiteLight,
    secondary = ChromeDark,
    onSecondary = PureWhite,
    secondaryContainer = SurfaceLight,
    onSecondaryContainer = MatteCharcoal,
    tertiary = CautionAmber,
    onTertiary = MatteCharcoal,
    background = SurfaceLight,
    onBackground = MatteCharcoal,
    surface = SurfaceLightElevated,
    onSurface = MatteCharcoal,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = ChromeDark,
    error = AlertRed,
    onError = PureWhite,
)

@Composable
fun AtlasTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        AtlasExtendedColors(
            chrome = BrushedChrome,
            chromeDim = ChromeDim,
            navCyan = NavigationCyan,
            cautionAmber = CautionAmber,
            fieldOlive = FieldOlive,
            alertRed = AlertRed,
        )
    } else {
        AtlasExtendedColors(
            chrome = ChromeDark,
            chromeDim = ChromeDim,
            navCyan = NavigationCyanDark,
            cautionAmber = CautionAmber,
            fieldOlive = FieldOlive,
            alertRed = AlertRed,
        )
    }

    CompositionLocalProvider(LocalAtlasExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
