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
    primary = IndigoPrimaryLight,
    onPrimary = IndigoOnPrimaryLight,
    primaryContainer = IndigoPrimaryContainerLight,
    onPrimaryContainer = IndigoOnPrimaryContainerLight,
    secondary = TealSecondaryLight,
    onSecondary = TealOnSecondaryLight,
    secondaryContainer = TealSecondaryContainerLight,
    onSecondaryContainer = TealOnSecondaryContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = AmberOnTertiaryLight,
    tertiaryContainer = AmberTertiaryContainerLight,
    onTertiaryContainer = AmberOnTertiaryContainerLight,
    background = SlateBackgroundLight,
    onBackground = SlateOnBackgroundLight,
    surface = SlateSurfaceLight,
    onSurface = SlateOnSurfaceLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = SlateOnSurfaceVariantLight,
    outline = SlateOutlineLight,
    outlineVariant = SlateOutlineVariantLight,
    error = ErrorRedLight,
    onError = Color.White,
    errorContainer = ErrorRedContainerLight,
    onErrorContainer = ErrorRedLight
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = IndigoOnPrimaryDark,
    primaryContainer = IndigoPrimaryContainerDark,
    onPrimaryContainer = IndigoOnPrimaryContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = TealOnSecondaryDark,
    secondaryContainer = TealSecondaryContainerDark,
    onSecondaryContainer = TealOnSecondaryContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = AmberOnTertiaryDark,
    tertiaryContainer = AmberTertiaryContainerDark,
    onTertiaryContainer = AmberOnTertiaryContainerDark,
    background = SlateBackgroundDark,
    onBackground = SlateOnBackgroundDark,
    surface = SlateSurfaceDark,
    onSurface = SlateOnSurfaceDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = SlateOnSurfaceVariantDark,
    outline = SlateOutlineDark,
    outlineVariant = SlateOutlineVariantDark,
    error = ErrorRedDark,
    onError = Color.Black,
    errorContainer = ErrorRedContainerDark,
    onErrorContainer = ErrorRedDark
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
        success = SuccessGreenLight,
        onSuccess = Color.White,
        successContainer = SuccessGreenContainerLight,
        onSuccessContainer = SuccessGreenLight,
        codeKeyword = CodeKeywordLight,
        codeString = CodeStringLight,
        codeNumber = CodeNumberLight,
        codeComment = CodeCommentLight,
        codeBackground = CodeBackgroundLight
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
            success = SuccessGreenDark,
            onSuccess = Color.Black,
            successContainer = SuccessGreenContainerDark,
            onSuccessContainer = SuccessGreenDark,
            codeKeyword = CodeKeywordDark,
            codeString = CodeStringDark,
            codeNumber = CodeNumberDark,
            codeComment = CodeCommentDark,
            codeBackground = CodeBackgroundDark
        )
    } else {
        ExtendedColors(
            success = SuccessGreenLight,
            onSuccess = Color.White,
            successContainer = SuccessGreenContainerLight,
            onSuccessContainer = SuccessGreenLight,
            codeKeyword = CodeKeywordLight,
            codeString = CodeStringLight,
            codeNumber = CodeNumberLight,
            codeComment = CodeCommentLight,
            codeBackground = CodeBackgroundLight
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
