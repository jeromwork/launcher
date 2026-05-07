package com.launcher.ui.theme

import androidx.compose.runtime.Composable

// iOS uses the same calculation that Compose Multiplatform exposes; until iOS bootstrap,
// this is the simple system-trait read.
@Composable
internal actual fun isSystemInDarkTheme(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()
