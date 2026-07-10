package com.launcher.app.preset.task120.adapter

import android.content.Context
import com.launcher.preset.model.Pool
import com.launcher.preset.port.PoolSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Reads pool.json from Android assets.
 *
 * TODO(shareability): future PoolSource adapters — file import, share intent,
 * marketplace. Add as new adapter classes without changing existing wire format.
 */
class BundledPoolSource(
    private val context: Context,
    private val assetPath: String = "preset/pool.json",
) : PoolSource {
    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    override suspend fun loadPool(): Pool = withContext(Dispatchers.IO) {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        json.decodeFromString(Pool.serializer(), text)
    }
}
