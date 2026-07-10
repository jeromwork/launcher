package com.launcher.preset.fakes

import com.launcher.preset.model.Pool
import com.launcher.preset.port.PoolSource

class FakePoolSource(private val pool: Pool) : PoolSource {
    override suspend fun loadPool(): Pool = pool
}
