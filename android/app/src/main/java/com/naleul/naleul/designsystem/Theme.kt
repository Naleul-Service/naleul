// designsystem/Theme.kt
//
// 앱 전체를 감싸는 테마. 다크 모드 여부(LocalIsDark)와 기본 글꼴 · 머티리얼 색을 정해요.
//   NaleulTheme { RootScreen() }
// @Preview 에서도 꼭 NaleulTheme { } 으로 감싸야 NC 색이 맞게 나와요.

package com.naleul.naleul.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun NaleulTheme(dark: Boolean = ThemeStore.isDark(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDark provides dark) {
        val scheme = if (dark) {
            darkColorScheme(
                primary = NC.actionPrimary, onPrimary = NC.actionPrimaryFg,
                primaryContainer = NC.actionSecondaryBg, onPrimaryContainer = NC.actionSecondaryFg,
                secondary = NC.actionPrimary, onSecondary = NC.actionPrimaryFg,
                secondaryContainer = NC.actionSecondaryBg, onSecondaryContainer = NC.actionSecondaryFg,
                tertiary = NC.accentMint, onTertiary = NC.onAccentMint,
                background = NC.bgCanvas, onBackground = NC.textPrimary,
                surface = NC.bgSurface, onSurface = NC.textPrimary,
                surfaceVariant = NC.bgSubtle, onSurfaceVariant = NC.textSecondary,
                surfaceContainerLow = NC.bgSurface, surfaceContainer = NC.bgSurface, surfaceContainerHigh = NC.bgElevated,
                surfaceContainerHighest = NC.bgSubtle,
                inverseSurface = NC.bgInverse, inverseOnSurface = NC.textInverse, inversePrimary = NC.textBrand,
                scrim = NC.bgOverlay,
                outline = NC.borderControl, outlineVariant = NC.borderSubtle,
                error = NC.statusErrorFg, onError = NC.textInverse,
                errorContainer = NC.statusErrorBg, onErrorContainer = NC.statusErrorFg,
            )
        } else {
            lightColorScheme(
                primary = NC.actionPrimary, onPrimary = NC.actionPrimaryFg,
                primaryContainer = NC.actionSecondaryBg, onPrimaryContainer = NC.actionSecondaryFg,
                secondary = NC.actionPrimary, onSecondary = NC.actionPrimaryFg,
                secondaryContainer = NC.actionSecondaryBg, onSecondaryContainer = NC.actionSecondaryFg,
                tertiary = NC.accentMint, onTertiary = NC.onAccentMint,
                background = NC.bgCanvas, onBackground = NC.textPrimary,
                surface = NC.bgSurface, onSurface = NC.textPrimary,
                surfaceVariant = NC.bgSubtle, onSurfaceVariant = NC.textSecondary,
                surfaceContainerLow = NC.bgSurface, surfaceContainer = NC.bgSurface, surfaceContainerHigh = NC.bgElevated,
                surfaceContainerHighest = NC.bgSubtle,
                inverseSurface = NC.bgInverse, inverseOnSurface = NC.textInverse, inversePrimary = NC.textBrand,
                scrim = NC.bgOverlay,
                outline = NC.borderControl, outlineVariant = NC.borderSubtle,
                error = NC.statusErrorFg, onError = NC.textInverse,
                errorContainer = NC.statusErrorBg, onErrorContainer = NC.statusErrorFg,
            )
        }
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalTextStyle provides pt(15).copy(color = NC.textPrimary)) {
                content()
            }
        }
    }
}
