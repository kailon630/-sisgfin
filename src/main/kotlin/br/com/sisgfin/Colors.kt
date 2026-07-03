package br.com.sisgfin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Dark palette ──────────────────────────────────────────────────────────────
private val Dark_Background    = Color(0xFF111318)
private val Dark_Surface       = Color(0xFF151922)
private val Dark_Elevated      = Color(0xFF1B1F2A)
private val Dark_Overlay       = Color(0xFF202431)
private val Dark_Border        = Color(0xFF2D323E)
private val Dark_BorderLight   = Color(0xFF383E4C)
private val Dark_Accent        = Color(0xFF4C8DFF)
private val Dark_AccentMuted   = Color(0x334C8DFF)
private val Dark_Info          = Color(0xFF58A6FF)
private val Dark_TextPrimary   = Color(0xFFE1E4E8)
private val Dark_TextSecondary = Color(0xFFA0A7B1)
private val Dark_TextDisabled  = Color(0xFF57606A)
private val Dark_Success       = Color(0xFF3FB950)
private val Dark_Danger        = Color(0xFFFF5D73)
private val Dark_Warning       = Color(0xFFE6A817)

// ── Light palette ─────────────────────────────────────────────────────────────
private val Light_Background    = Color(0xFFF4F6FB)
private val Light_Surface       = Color(0xFFFFFFFF)
private val Light_Elevated      = Color(0xFFEBEFF8)
private val Light_Overlay       = Color(0xFFE0E7F4)
private val Light_Border        = Color(0xFFCDD4E8)
private val Light_BorderLight   = Color(0xFFD8DEF0)
private val Light_Accent        = Color(0xFF2563EB)
private val Light_AccentMuted   = Color(0x262563EB)
private val Light_Info          = Color(0xFF2B6CB0)
private val Light_TextPrimary   = Color(0xFF1A1F2E)
private val Light_TextSecondary = Color(0xFF505870)
private val Light_TextDisabled  = Color(0xFF9AA2BB)
private val Light_Success       = Color(0xFF1A9E40)
private val Light_Danger        = Color(0xFFD03050)
private val Light_Warning       = Color(0xFF9C6E08)

// ── Palette data class ────────────────────────────────────────────────────────
data class WsColors(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val overlay: Color,
    val border: Color,
    val borderLight: Color,
    val accent: Color,
    val accentMuted: Color,
    val info: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val isDark: Boolean
)

val darkWsColors = WsColors(
    background    = Dark_Background,
    surface       = Dark_Surface,
    elevated      = Dark_Elevated,
    overlay       = Dark_Overlay,
    border        = Dark_Border,
    borderLight   = Dark_BorderLight,
    accent        = Dark_Accent,
    accentMuted   = Dark_AccentMuted,
    info          = Dark_Info,
    textPrimary   = Dark_TextPrimary,
    textSecondary = Dark_TextSecondary,
    textDisabled  = Dark_TextDisabled,
    success       = Dark_Success,
    danger        = Dark_Danger,
    warning       = Dark_Warning,
    isDark        = true
)

val lightWsColors = WsColors(
    background    = Light_Background,
    surface       = Light_Surface,
    elevated      = Light_Elevated,
    overlay       = Light_Overlay,
    border        = Light_Border,
    borderLight   = Light_BorderLight,
    accent        = Light_Accent,
    accentMuted   = Light_AccentMuted,
    info          = Light_Info,
    textPrimary   = Light_TextPrimary,
    textSecondary = Light_TextSecondary,
    textDisabled  = Light_TextDisabled,
    success       = Light_Success,
    danger        = Light_Danger,
    warning       = Light_Warning,
    isDark        = false
)

val LocalWsColors = compositionLocalOf { darkWsColors }

// ── Public color tokens — @Composable getters (zero changes at call sites) ────

val WsBackground: Color
    @Composable get() = LocalWsColors.current.background

val WsSurface: Color
    @Composable get() = LocalWsColors.current.surface

val WsElevated: Color
    @Composable get() = LocalWsColors.current.elevated

val WsOverlay: Color
    @Composable get() = LocalWsColors.current.overlay

val WsBorder: Color
    @Composable get() = LocalWsColors.current.border

val WsBorderLight: Color
    @Composable get() = LocalWsColors.current.borderLight

val WsAccent: Color
    @Composable get() = LocalWsColors.current.accent

val WsAccentMuted: Color
    @Composable get() = LocalWsColors.current.accentMuted

val WsInfo: Color
    @Composable get() = LocalWsColors.current.info

val WsTextPrimary: Color
    @Composable get() = LocalWsColors.current.textPrimary

val WsTextSecondary: Color
    @Composable get() = LocalWsColors.current.textSecondary

val WsTextDisabled: Color
    @Composable get() = LocalWsColors.current.textDisabled

val WsSuccess: Color
    @Composable get() = LocalWsColors.current.success

val WsDanger: Color
    @Composable get() = LocalWsColors.current.danger

val WsWarning: Color
    @Composable get() = LocalWsColors.current.warning
