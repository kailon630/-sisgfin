package br.com.sisgfin

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val WsDarkColorScheme = darkColorScheme(
    primary          = darkWsColors.accent,
    onPrimary        = Color.White,
    secondary        = darkWsColors.textSecondary,
    onSecondary      = darkWsColors.textPrimary,
    tertiary         = darkWsColors.accentMuted,
    background       = darkWsColors.background,
    surface          = darkWsColors.surface,
    onBackground     = darkWsColors.textPrimary,
    onSurface        = darkWsColors.textPrimary,
    surfaceVariant   = darkWsColors.elevated,
    onSurfaceVariant = darkWsColors.textSecondary,
    outline          = darkWsColors.border,
    error            = darkWsColors.danger
)

private val WsLightColorScheme = lightColorScheme(
    primary          = lightWsColors.accent,
    onPrimary        = Color.White,
    secondary        = lightWsColors.textSecondary,
    onSecondary      = lightWsColors.textPrimary,
    tertiary         = lightWsColors.accentMuted,
    background       = lightWsColors.background,
    surface          = lightWsColors.surface,
    onBackground     = lightWsColors.textPrimary,
    onSurface        = lightWsColors.textPrimary,
    surfaceVariant   = lightWsColors.elevated,
    onSurfaceVariant = lightWsColors.textSecondary,
    outline          = lightWsColors.border,
    error            = lightWsColors.danger
)

// Colors removed from Typography — Material3 resolves them via LocalContentColor/colorScheme.
private val WsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily    = FontFamily.SansSerif,
        fontWeight    = FontWeight.Bold,
        fontSize      = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 20.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize   = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize   = 13.sp
    ),
    labelMedium = TextStyle(
        fontFamily    = FontFamily.SansSerif,
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        letterSpacing = 0.5.sp
    )
)

// Monospace style for monetary values — color resolved at compose time from current theme.
val WsMoneyStyle: TextStyle
    @Composable get() = TextStyle(
        fontFamily    = FontFamily.Monospace,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 14.sp,
        letterSpacing = (-0.2).sp,
        color         = WsTextPrimary
    )

val WsMoneyStyleLarge: TextStyle
    @Composable get() = TextStyle(
        fontFamily    = FontFamily.Monospace,
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        letterSpacing = (-0.5).sp,
        color         = WsTextPrimary
    )

@Composable
fun SisgFinTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val wsColors    = if (isDark) darkWsColors else lightWsColors
    val colorScheme = if (isDark) WsDarkColorScheme else WsLightColorScheme

    CompositionLocalProvider(
        LocalWsColors provides wsColors,
        LocalContentColor provides wsColors.textPrimary
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = WsTypography,
            content     = content
        )
    }
}
