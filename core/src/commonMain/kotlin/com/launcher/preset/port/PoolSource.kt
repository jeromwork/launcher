package com.launcher.preset.port

import com.launcher.preset.model.Pool

interface PoolSource {
    suspend fun loadPool(): Pool

    // TODO(shareability): future PoolSource adapters — file import, share intent,
    // marketplace. Add as new adapter classes without changing existing wire format.
}
