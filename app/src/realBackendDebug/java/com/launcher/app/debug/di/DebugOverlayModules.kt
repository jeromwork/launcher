package com.launcher.app.debug.di

import org.koin.core.module.Module

/**
 * Marker класс — LauncherApplication ищет его reflection'ом и подгружает
 * `debugModules()` в Koin start config. Существует ТОЛЬКО в realBackendDebug
 * source set — на release / mockBackend поиск возвращает ClassNotFoundException,
 * overlay не применяется.
 *
 * Используется для:
 *  - Spec 018: подмена AsyncConfigPushQueue на synchronous in-memory вариант
 *    (см. f018DebugOverlayModule).
 */
object DebugOverlayModules {
    @JvmStatic
    fun modules(): List<Module> = listOf(f018DebugOverlayModule)
}
