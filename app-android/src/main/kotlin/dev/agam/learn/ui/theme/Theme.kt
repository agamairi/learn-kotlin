package dev.agam.learn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LightJadePrimary,
    onPrimary = LightOnJadePrimary,
    primaryContainer = LightJadeContainer,
    onPrimaryContainer = LightOnJadeContainer,
    inversePrimary = LightJadeInversePrimary,
    secondary = LightCoralSecondary,
    onSecondary = LightOnCoralSecondary,
    secondaryContainer = LightPeachContainer,
    onSecondaryContainer = LightOnPeachContainer,
    tertiary = LightGoldTertiary,
    onTertiary = LightOnGoldTertiary,
    tertiaryContainer = LightGoldContainer,
    onTertiaryContainer = LightOnGoldContainer,
    background = LightWarmBackground,
    onBackground = LightWarmInk,
    surface = LightPaperSurface,
    onSurface = LightOnPaperSurface,
    surfaceVariant = LightSandSurfaceVariant,
    onSurfaceVariant = LightOnSandSurfaceVariant,
    surfaceTint = LightJadePrimary,
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
    primary = DarkMintPrimary,
    onPrimary = DarkOnMintPrimary,
    primaryContainer = DarkJadeContainer,
    onPrimaryContainer = DarkOnJadeContainer,
    inversePrimary = DarkMintInversePrimary,
    secondary = DarkCoralSecondary,
    onSecondary = DarkOnCoralSecondary,
    secondaryContainer = DarkCoralContainer,
    onSecondaryContainer = DarkOnCoralContainer,
    tertiary = DarkGoldTertiary,
    onTertiary = DarkOnGoldTertiary,
    tertiaryContainer = DarkGoldContainer,
    onTertiaryContainer = DarkOnGoldContainer,
    background = DarkTealBackground,
    onBackground = DarkWarmInk,
    surface = DarkTealSurface,
    onSurface = DarkOnTealSurface,
    surfaceVariant = DarkTealSurfaceVariant,
    onSurfaceVariant = DarkOnTealSurfaceVariant,
    surfaceTint = DarkMintPrimary,
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
            content = content
        )
    }
}
