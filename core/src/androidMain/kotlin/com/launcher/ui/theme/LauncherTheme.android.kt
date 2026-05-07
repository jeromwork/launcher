package com.launcher.ui.theme

import androidx.compose.runtime.Composable

@Composable
internal actual fun isSystemInDarkTheme(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()
