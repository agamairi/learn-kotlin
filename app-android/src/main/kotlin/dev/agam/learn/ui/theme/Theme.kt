package dev.agam.learn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = LightSagePrimary,
    onPrimary = LightOnSagePrimary,
    primaryContainer = LightSageContainer,
    onPrimaryContainer = LightOnSageContainer,
    inversePrimary = LightSageInversePrimary,
    secondary = LightClaySecondary,
    onSecondary = LightOnClaySecondary,
    secondaryContainer = LightClayContainer,
    onSecondaryContainer = LightOnClayContainer,
    tertiary = LightOchreTertiary,
    onTertiary = LightOnOchreTertiary,
    tertiaryContainer = LightOchreContainer,
    onTertiaryContainer = LightOnOchreContainer,
    background = LightIvoryBackground,
    onBackground = LightWarmCharcoal,
    surface = LightOffWhiteSurface,
    onSurface = LightOnOffWhiteSurface,
    surfaceVariant = LightNeutralSurfaceVariant,
    onSurfaceVariant = LightOnNeutralSurfaceVariant,
    surfaceTint = LightSagePrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkSoftSagePrimary,
    onPrimary = DarkOnSoftSagePrimary,
    primaryContainer = DarkSageContainer,
    onPrimaryContainer = DarkOnSageContainer,
    inversePrimary = DarkSoftSageInversePrimary,
    secondary = DarkClaySecondary,
    onSecondary = DarkOnClaySecondary,
    secondaryContainer = DarkClayContainer,
    onSecondaryContainer = DarkOnClayContainer,
    tertiary = DarkMutedOchreTertiary,
    onTertiary = DarkOnMutedOchreTertiary,
    tertiaryContainer = DarkOchreContainer,
    onTertiaryContainer = DarkOnOchreContainer,
    background = DarkCharcoalBackground,
    onBackground = DarkWarmText,
    surface = DarkCharcoalSurface,
    onSurface = DarkOnCharcoalSurface,
    surfaceVariant = DarkNeutralSurfaceVariant,
    onSurfaceVariant = DarkOnNeutralSurfaceVariant,
    surfaceTint = DarkSoftSagePrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

private val LearnShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeNumber: Color,
    val codeComment: Color,
    val codeBackground: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        success = LightSuccess,
        onSuccess = LightOnSuccess,
        successContainer = LightSuccessContainer,
        onSuccessContainer = LightOnSuccessContainer,
        codeKeyword = LightCodeKeyword,
        codeString = LightCodeString,
        codeNumber = LightCodeNumber,
        codeComment = LightCodeComment,
        codeBackground = LightCodeSurface
    )
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

@Composable
fun LearnKotlinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        ExtendedColors(
            success = DarkSuccess,
            onSuccess = DarkOnSuccess,
            successContainer = DarkSuccessContainer,
            onSuccessContainer = DarkOnSuccessContainer,
            codeKeyword = DarkCodeKeyword,
            codeString = DarkCodeString,
            codeNumber = DarkCodeNumber,
            codeComment = DarkCodeComment,
            codeBackground = DarkCodeSurface
        )
    } else {
        ExtendedColors(
            success = LightSuccess,
            onSuccess = LightOnSuccess,
            successContainer = LightSuccessContainer,
            onSuccessContainer = LightOnSuccessContainer,
            codeKeyword = LightCodeKeyword,
            codeString = LightCodeString,
            codeNumber = LightCodeNumber,
            codeComment = LightCodeComment,
            codeBackground = LightCodeSurface
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = LearnShapes,
            content = content
        )
    }
}
