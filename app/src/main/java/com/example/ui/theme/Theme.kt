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

val LocalAccentColor = staticCompositionLocalOf { CrimsonLeica }
val LocalOnAccentColor = staticCompositionLocalOf { PureWhite }

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
    accentPreset: AccentColorPreset = AccentColorPreset.CRIMSON_PRO,
    content: @Composable () -> Unit
) {
    val accent = when (accentPreset) {
        AccentColorPreset.CRIMSON_PRO -> CrimsonLeica
        AccentColorPreset.PURE_WHITE -> PureWhite
        AccentColorPreset.ELECTRIC_CYAN -> ElectricCyan
        AccentColorPreset.EMERALD -> EmeraldOptical
    }
    val onAccent = when (accentPreset) {
        AccentColorPreset.PURE_WHITE, AccentColorPreset.ELECTRIC_CYAN -> PureBlack
        AccentColorPreset.CRIMSON_PRO, AccentColorPreset.EMERALD -> PureWhite
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
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
            onPrimary = onAccent,
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

    CompositionLocalProvider(
        LocalAccentColor provides accent,
        LocalOnAccentColor provides onAccent
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HungCamTypography,
            shapes = HungCamShapes,
            content = content
        )
    }
}

fun resolveAccentPreviewColor(preset: AccentColorPreset): Color = when (preset) {
    AccentColorPreset.CRIMSON_PRO -> CrimsonLeica
    AccentColorPreset.PURE_WHITE -> PureWhite
    AccentColorPreset.ELECTRIC_CYAN -> ElectricCyan
    AccentColorPreset.EMERALD -> EmeraldOptical
}

fun resolveCameraModeColor(
    mode: com.example.model.CameraMode,
    style: com.example.model.ModeColorStyle,
    fallbackAccent: Color = CrimsonLeica
): Color = when (style) {
    com.example.model.ModeColorStyle.DEFAULT_RED -> fallbackAccent
    com.example.model.ModeColorStyle.MONOCHROME_WHITE -> PureWhite
    com.example.model.ModeColorStyle.PER_MODE_VIBRANT -> when (mode) {
        com.example.model.CameraMode.PRO -> ModeProCyan
        com.example.model.CameraMode.VIDEO -> ModeVideoCoral
        com.example.model.CameraMode.PHOTO -> ModePhotoCrimson
        com.example.model.CameraMode.PORTRAIT -> ModePortraitRose
        com.example.model.CameraMode.SLOW_MOTION -> ModeSlowMoEmerald
        com.example.model.CameraMode.TIMELAPSE -> ModeTimelapseViolet
    }
}
