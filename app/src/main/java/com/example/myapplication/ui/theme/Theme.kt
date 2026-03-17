package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FantasyColorScheme = darkColorScheme(
    primary                = AncientGold,
    onPrimary              = TavernBrown,
    primaryContainer       = GoldContainer,
    onPrimaryContainer     = CandleLight,

    secondary              = EmberOrange,
    onSecondary            = TavernBrown,
    secondaryContainer     = EmberContainer,
    onSecondaryContainer   = EmberLight,

    background             = TavernBrown,
    onBackground           = WarmParchment,

    surface                = WarmWood,
    onSurface              = WarmParchment,
    surfaceVariant         = AgedWood,
    onSurfaceVariant       = FadedInk,

    outline                = OakBorder,
    outlineVariant         = WarmWood,

    error                  = CrimsonError,
    onError                = CandleLight,
)

@Composable
fun CadenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FantasyColorScheme,
        typography  = Typography,
        content     = content
    )
}
