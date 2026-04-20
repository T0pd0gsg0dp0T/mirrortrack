package com.potpal.mirrortrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Terminal-inspired palette
private val Background = Color(0xFF0D1117)      // deep dark, like GitHub dark
private val Surface = Color(0xFF161B22)          // slightly lighter panels
private val SurfaceVariant = Color(0xFF1C2333)   // cards
private val OnSurface = Color(0xFFE6EDF3)        // primary text — light gray
private val OnSurfaceVariant = Color(0xFF8B949E)  // secondary text — muted gray
private val Primary = Color(0xFF3FB950)           // terminal green
private val PrimaryContainer = Color(0xFF1A3A2A)  // muted green bg
private val OnPrimary = Color(0xFF0D1117)
private val Secondary = Color(0xFF58A6FF)         // link blue / info
private val SecondaryContainer = Color(0xFF152238)
private val OnSecondary = Color(0xFF0D1117)
private val Tertiary = Color(0xFFD2A8FF)          // purple accent
private val TertiaryContainer = Color(0xFF2D1F4E)
private val Error = Color(0xFFF85149)             // red
private val ErrorContainer = Color(0xFF3D1518)
private val OnError = Color(0xFFFFFFFF)
private val Outline = Color(0xFF30363D)           // borders
private val OutlineVariant = Color(0xFF21262D)
private val InverseSurface = Color(0xFFE6EDF3)
private val InverseOnSurface = Color(0xFF0D1117)
private val InversePrimary = Color(0xFF238636)

val MirrorTrackDarkScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Primary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = Secondary,
    tertiary = Tertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = Tertiary,
    error = Error,
    errorContainer = ErrorContainer,
    onError = OnError,
    onErrorContainer = Error,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,
    surfaceTint = Primary
)

private val MirrorTrackTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp)
)

@Composable
fun MirrorTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MirrorTrackDarkScheme,
        typography = MirrorTrackTypography,
        content = content
    )
}
