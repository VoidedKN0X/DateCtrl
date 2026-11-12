package com.vknxworks.datectrl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.datectrl.R

/** The accent fill (hero cards, primary buttons, power keys, the selected tab, switches that are on). One place. */
private val AccentFill = Color(0xFFB48CFF)

/**
 * One Ctrl / Signal palette. There are two, [CtrlPalettes.Dark] and [CtrlPalettes.Light], and the app uses the
 * one matching the phone's dark theme setting.
 */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    /** Accent as a fill; content on it is [OnAccent]. Same in both palettes. */
    val Accent: Color,
    /** Accent as text, icons and thin lines on the background: the fill is too pale for that on white. */
    val AccentText: Color,
    val Background: Color,
    val Surface: Color,
    val Raised: Color,
    /** Card borders. */
    val Outline: Color,
    /** Borders on controls: avatar, back button, text fields, the bottom bar. */
    val ControlOutline: Color,
    /** Lines between list rows. */
    val Divider: Color,
    val Text: Color,
    val Muted: Color,
    /** Disabled or stopped. */
    val Inactive: Color,
    val SwitchTrackOff: Color,
    val SwitchKnobOff: Color,
    val ProgressTrack: Color,
    /** Default progress fill (the main figure uses the accent). */
    val ProgressFill: Color,
    /** Edge of empty PIN cells. */
    val EmptyCell: Color,
    val Error: Color,
    val OnError: Color,
    /** Text and icons on the accent fill. */
    val OnAccent: Color,
)

object CtrlPalettes {
    val Dark = CtrlPalette(
        isDark = true,
        Accent = AccentFill,
        AccentText = AccentFill,
        Background = Color(0xFF08080A),
        Surface = Color(0xFF141417),
        Raised = Color(0xFF1C1C21),
        Outline = Color(0xFF232328),
        ControlOutline = Color(0xFF2A2A2F),
        Divider = Color(0xFF1E1E23),
        Text = Color(0xFFF4F4F2),
        Muted = Color(0xFFA3A3AB),
        Inactive = Color(0xFF8C8C94),
        SwitchTrackOff = Color(0xFF2A2A30),
        SwitchKnobOff = Color(0xFF8C8C94),
        ProgressTrack = Color(0xFF26262C),
        ProgressFill = Color(0xFFF4F4F2),
        EmptyCell = Color(0xFF3A3A41),
        Error = Color(0xFFFF6B6B),
        OnError = Color(0xFF08080A),
        OnAccent = Color(0xFF08080A),
    )

    /** "Signal Light". */
    val Light = CtrlPalette(
        isDark = false,
        Accent = AccentFill,
        AccentText = Color(0xFF6A3BDB),
        Background = Color(0xFFF5F5F1),
        Surface = Color(0xFFFFFFFF),
        Raised = Color(0xFFECECE7),
        Outline = Color(0xFFDEDED7),
        ControlOutline = Color(0xFFD2D2CA),
        Divider = Color(0xFFE6E6E0),
        Text = Color(0xFF0B0B0D),
        Muted = Color(0xFF5C5C64),
        Inactive = Color(0xFF74747C),
        SwitchTrackOff = Color(0xFFDCDCD5),
        SwitchKnobOff = Color(0xFF74747C),
        ProgressTrack = Color(0xFFE2E2DC),
        ProgressFill = Color(0xFF0B0B0D),
        EmptyCell = Color(0xFFC9C9C1),
        Error = Color(0xFFD93636),
        OnError = Color(0xFFFFFFFF),
        OnAccent = Color(0xFF08080A),
    )
}

/** The palette in use right now (dark or light, following the phone). */
val LocalCtrlColors = staticCompositionLocalOf { CtrlPalettes.Dark }

/**
 * Ctrl / Signal, the look shared by every Ctrl app: high contrast, one accent, dark or light following the
 * phone. Components write `CtrlColors.Surface` and always get the colour for the current theme.
 */
object CtrlColors {
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Accent
    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.AccentText
    val Background: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Raised
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Outline
    val ControlOutline: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.ControlOutline
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Divider
    val Text: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Muted
    val Inactive: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Inactive
    val SwitchTrackOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.SwitchTrackOff
    val SwitchKnobOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.SwitchKnobOff
    val ProgressTrack: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.ProgressTrack
    val ProgressFill: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.ProgressFill
    val EmptyCell: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.EmptyCell
    val Error: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Error
    val OnError: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.OnError
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.OnAccent
    /** "Due soon" amber, readable on both backgrounds. */
    val Warning: Color @Composable @ReadOnlyComposable get() = if (LocalCtrlColors.current.isDark) Color(0xFFFFC46B) else Color(0xFF9A5B00)
}

/** Material's colour scheme built from a palette, for the stock components (text buttons, menus, cursors). */
private fun schemeFor(p: CtrlPalette) = (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
    // Material uses primary for text buttons and other accent text, so it's the readable accent;
    // fills use primaryContainer.
    primary = p.AccentText,
    onPrimary = if (p.isDark) p.OnAccent else Color.White,
    primaryContainer = p.Accent,
    onPrimaryContainer = p.OnAccent,
    inversePrimary = p.Accent,
    secondary = p.Text,
    onSecondary = p.Background,
    secondaryContainer = p.Raised,
    onSecondaryContainer = p.Text,
    tertiary = p.AccentText,
    onTertiary = if (p.isDark) p.OnAccent else Color.White,
    tertiaryContainer = p.Raised,
    onTertiaryContainer = p.Text,
    background = p.Background,
    onBackground = p.Text,
    surface = p.Background,
    onSurface = p.Text,
    surfaceVariant = p.Raised,
    onSurfaceVariant = p.Muted,
    surfaceTint = Color.Transparent,
    surfaceBright = p.Raised,
    surfaceDim = p.Background,
    surfaceContainerLowest = p.Background,
    surfaceContainerLow = p.Surface,
    surfaceContainer = p.Surface,
    surfaceContainerHigh = p.Raised,
    surfaceContainerHighest = p.Raised,
    inverseSurface = p.Text,
    inverseOnSurface = p.Background,
    outline = p.ControlOutline,
    outlineVariant = p.Outline,
    error = p.Error,
    onError = p.OnError,
    errorContainer = p.Error,
    onErrorContainer = p.OnError,
    scrim = Color.Black,
)

private val DarkScheme = schemeFor(CtrlPalettes.Dark)
private val LightScheme = schemeFor(CtrlPalettes.Light)

// Space Grotesk (variable, weights 300 to 700) for all UI text, JetBrains Mono for numbers, data and labels.
// Both are bundled (SIL Open Font License 1.1); nothing is downloaded.
private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private fun mono(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

/** The mono styles that Material's type scale has no slot for. */
object CtrlType {
    /** Section labels: always UPPERCASE, muted. */
    val Label = mono(11, 14, FontWeight.Normal, 0.16)
    /** Labels on the accent (hero card). */
    val LabelStrong = mono(11, 14, FontWeight.SemiBold, 0.16)
    /** "SUM/CTRL" in the top bar. */
    val Wordmark = mono(13, 16, FontWeight.SemiBold, 0.14)
    /** Small data readouts: counts, codes, protocols, versions. */
    val Data = mono(12, 16, FontWeight.Normal)
    /** Trailing status in list rows. */
    val Status = mono(11, 14, FontWeight.Medium, 0.1)
    /** Keypad digits. */
    val Key = mono(26, 30, FontWeight.Medium)
    /** Big readouts in JetBrains Mono. */
    val Readout = mono(40, 44, FontWeight.SemiBold, -0.04)
    /** The giant number on a hero card. */
    val Hero = style(64, 60, 700, -0.05)
}

private val AppTypography = Typography(
    displayLarge = style(64, 60, 700, -0.05),
    displayMedium = style(56, 56, 700, -0.04),
    displaySmall = style(40, 42, 700, -0.035),
    headlineLarge = style(40, 42, 700, -0.035),
    headlineMedium = style(30, 34, 700, -0.03),
    headlineSmall = style(24, 30, 700, -0.02),
    titleLarge = style(18, 24, 600),
    titleMedium = style(16, 22, 600),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 20, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 500),
    labelSmall = mono(11, 14, FontWeight.Medium, 0.1),
)

/** Shapes: cards 22dp, hero cards and sheets 26dp, controls 14 to 16dp, chips 10dp. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val Dialog = RoundedCornerShape(26.dp)
    val Button = RoundedCornerShape(18.dp)
    val Control = RoundedCornerShape(14.dp)
    val Field = RoundedCornerShape(16.dp)
    val Chip = RoundedCornerShape(10.dp)
    val Key = RoundedCornerShape(22.dp)
    val Dot = RoundedCornerShape(2.dp)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * Ctrl / Signal, dark or light: follows the phone's dark theme setting, with no setting of its own and no
 * wallpaper colours.
 */
@Composable
fun DateCtrlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) CtrlPalettes.Dark else CtrlPalettes.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
