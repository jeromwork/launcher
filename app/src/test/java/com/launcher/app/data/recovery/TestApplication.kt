package com.launcher.app.data.recovery

import android.app.Application

/**
 * Минимальный Application для Robolectric DataStore тестов — не стартует Koin
 * (в отличие от production LauncherApplication), избегая
 * KoinApplicationAlreadyStartedException между тестами.
 */
class TestApplication : Application()
