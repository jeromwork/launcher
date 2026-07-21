package com.launcher.adapters.config

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.launcher.adapters.config.db.ConfigStore

/**
 * Android driver factory для [ConfigStore] SQLDelight database (spec 008
 * Phase 3 T051a).
 *
 * Konsist gate (plan.md §Module map): `SqlDriver` instances do NOT leak past
 * this file. Callers receive [ConfigStore] (KMP-pure) via [createConfigStore].
 *
 * Lazy: caller decides when to materialize the DB file (Application.onCreate
 * binds the lazy delegate; first SQL operation triggers actual disk open).
 */
object AndroidSqlDriverProvider {

    private const val DATABASE_NAME = "config_sync.db"

    /**
     * Build a [ConfigStore] backed by Android SQLite. Caller owns lifecycle
     * (release on app shutdown is optional — Android process tear-down is
     * sufficient for SQLite).
     */
    fun createConfigStore(context: Context): ConfigStore {
        val driver: SqlDriver = AndroidSqliteDriver(
            schema = ConfigStore.Schema,
            context = context.applicationContext,
            name = DATABASE_NAME,
        )
        return ConfigStore(driver)
    }
}
