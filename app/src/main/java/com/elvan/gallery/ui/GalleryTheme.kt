package com.elvan.gallery.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import com.elvan.navil.ElvanSansFontFamily
import com.elvan.navil.LocalAppFontFamily
import com.elvan.navil.LocalShellColors
import com.elvan.navil.rememberShellColors

/** Elvan Navil's colours and Elvan Sans for every screen of the Gallery. */
@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    val colors = rememberShellColors()
    val ff = ElvanSansFontFamily
    val scheme = remember(colors) {
        if (colors.isDark) darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.surface, onSurface = colors.textPrimary)
        else lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.surface, onSurface = colors.textPrimary)
    }
    val typography = remember {
        Typography().run {
            copy(
                displayLarge = displayLarge.copy(fontFamily = ff), displayMedium = displayMedium.copy(fontFamily = ff),
                displaySmall = displaySmall.copy(fontFamily = ff), headlineLarge = headlineLarge.copy(fontFamily = ff),
                headlineMedium = headlineMedium.copy(fontFamily = ff), headlineSmall = headlineSmall.copy(fontFamily = ff),
                titleLarge = titleLarge.copy(fontFamily = ff), titleMedium = titleMedium.copy(fontFamily = ff),
                titleSmall = titleSmall.copy(fontFamily = ff), bodyLarge = bodyLarge.copy(fontFamily = ff),
                bodyMedium = bodyMedium.copy(fontFamily = ff), bodySmall = bodySmall.copy(fontFamily = ff),
                labelLarge = labelLarge.copy(fontFamily = ff), labelMedium = labelMedium.copy(fontFamily = ff),
                labelSmall = labelSmall.copy(fontFamily = ff)
            )
        }
    }
    // As in Udukkai: the phone's larger-text setting is not applied, and a phone held sideways draws everything a little smaller
    val base = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(androidx.compose.ui.Modifier.fillMaxSize()) {
        val capped = Density(base.density, base.fontScale.coerceAtMost(1f))
        val sideways = maxWidth > maxHeight && maxHeight < 480.dp
        val density = if (sideways) Density(capped.density * 0.86f, capped.fontScale) else capped
        CompositionLocalProvider(LocalDensity provides density, LocalShellColors provides colors, LocalAppFontFamily provides ff) {
            MaterialTheme(colorScheme = scheme, typography = typography) {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = ff, color = colors.textPrimary)) {
                    content()
                }
            }
        }
    }
}
