package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.model.AccentColorPreset

val LocalAccentColor = staticCompositionLocalOf { LeicaAmber }

private val HungCamShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HungCamTheme(
    darkTheme: Boolean = true,
    accentPreset: AccentColorPreset = AccentColorPreset.LEICA_AMBER,
    content: @Composable () -> Unit
) {
    val accent = when (accentPreset) {
        AccentColorPreset.LEICA_AMBER -> LeicaAmber
        AccentColorPreset.ELECTRIC_CYAN -> ElectricCyan
        AccentColorPreset.CRIMSON_PRO -> CrimsonShutter
        AccentColorPreset.EMERALD -> EmeraldOptical
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = PureBlack,
            primaryContainer = ElevatedObsidian,
            onPrimaryContainer = PureWhite,
            secondary = SoftWhite,
            onSecondary = PureBlack,
            secondaryContainer = CardSlate,
            onSecondaryContainer = SoftWhite,
            tertiary = ElectricCyan,
            background = PureBlack,
            onBackground = PureWhite,
            surface = ObsidianSurface,
            onSurface = PureWhite,
            surfaceVariant = ElevatedObsidian,
            onSurfaceVariant = MutedSlate,
            outline = HairlineBorder,
            error = ErrorCrimson,
            onError = PureWhite
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = PureBlack,
            background = Color(0xFFF5F6F8),
            onBackground = Color(0xFF0E1015),
            surface = PureWhite,
            onSurface = Color(0xFF0E1015),
            surfaceVariant = Color(0xFFE9ECF2),
            onSurfaceVariant = Color(0xFF4B5260),
            outline = Color(0xFFD0D5DF),
            error = ErrorCrimson,
            onError = PureWhite
        )
    }

    CompositionLocalProvider(LocalAccentColor provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HungCamTypography,
            shapes = HungCamShapes,
            content = content
        )
    }
}
