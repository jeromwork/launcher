package com.launcher.core.preset

import com.launcher.api.FlowPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryPresetRepositoryTest {

    @Test
    fun startsEmpty() = runTest {
        val repo = InMemoryPresetRepository()
        assertNull(repo.getActivePreset())
    }

    @Test
    fun startsWithInitialValueIfProvided() = runTest {
        val repo = InMemoryPresetRepository(FlowPreset.WORKSPACE)
        assertEquals(FlowPreset.WORKSPACE, repo.getActivePreset())
    }

    @Test
    fun setAndReadBack() = runTest {
        val repo = InMemoryPresetRepository()
        repo.setActivePreset(FlowPreset.LAUNCHER)
        assertEquals(FlowPreset.LAUNCHER, repo.getActivePreset())
    }

    @Test
    fun clearResetsToNull() = runTest {
        val repo = InMemoryPresetRepository(FlowPreset.SIMPLE_LAUNCHER)
        repo.clear()
        assertNull(repo.getActivePreset())
    }

    @Test
    fun observeEmitsCurrentValue() = runTest {
        val repo = InMemoryPresetRepository(FlowPreset.LAUNCHER)
        assertEquals(FlowPreset.LAUNCHER, repo.observeActivePreset().first())
    }

    @Test
    fun fromSlugRoundtripsAllValues() {
        FlowPreset.values().forEach { preset ->
            assertEquals(preset, FlowPreset.fromSlug(preset.slug))
        }
        assertNull(FlowPreset.fromSlug("nonexistent"))
        assertNull(FlowPreset.fromSlug(null))
    }
}
