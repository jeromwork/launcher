package com.launcher.core.catalog

import android.content.Context
import android.content.pm.PackageManager
import com.launcher.api.CatalogEntry
import com.launcher.api.CatalogSnapshot
import com.launcher.api.ProjectEvent
import com.launcher.core.events.EventRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Installed launchable apps snapshot for UI (queries via [PackageManager], refresh on package events).
 */
class AppIndex(
    private val context: Context,
    private val scope: CoroutineScope,
    private val eventRouter: EventRouter,
    /**
     * When true, skips [PackageManager] scans (Robolectric smoke tests; avoids blocking PM on some hosts).
     */
    private val skipPackageScan: Boolean = false,
) {
    private val pm = context.packageManager
    private val generation = AtomicLong(0L)
    private val _snapshot = MutableStateFlow(CatalogSnapshot(0L, emptyList()))
    val snapshot: StateFlow<CatalogSnapshot> = _snapshot.asStateFlow()

    private var eventJob: Job? = null

    fun start() {
        if (eventJob != null) return
        eventJob = scope.launch {
            eventRouter.events.collect { event ->
                if (event is ProjectEvent.PackageSetChanged) {
                    launch(Dispatchers.Default) { refreshNow() }
                }
            }
        }
        scope.launch(Dispatchers.Default) { refreshNow() }
    }

    fun stop() {
        eventJob?.cancel()
        eventJob = null
    }

    suspend fun refreshNow() {
        val next = buildSnapshot()
        _snapshot.value = next
    }

    fun findEntry(stableKey: String): CatalogEntry? =
        _snapshot.value.entries.firstOrNull { it.stableKey == stableKey }

    private suspend fun buildSnapshot(): CatalogSnapshot = withContext(Dispatchers.Default) {
        if (skipPackageScan) {
            return@withContext CatalogSnapshot(
                generation = generation.incrementAndGet(),
                entries = emptyList(),
            )
        }
        val entries = mutableListOf<CatalogEntry>()
        runCatching {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (ai in apps) {
                val pkg = ai.packageName
                if (pm.getLaunchIntentForPackage(pkg) == null) continue
                val label = ai.loadLabel(pm)?.toString() ?: pkg
                entries.add(
                    CatalogEntry(
                        stableKey = pkg,
                        displayLabel = label,
                        contentDescription = label,
                        isLaunchable = true,
                    ),
                )
            }
        }
        CatalogSnapshot(
            generation = generation.incrementAndGet(),
            entries = entries.sortedBy { it.displayLabel.lowercase() },
        )
    }
}
