package com.launcher.api.auth

import com.launcher.api.auth.internal.SessionRecord
import com.launcher.api.auth.internal.SessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [SessionStore] для commonTest. Детерминирован — никакого
 * `Random` или `Clock.now()`, единственное состояние — текущий [SessionRecord].
 *
 * Per spec 017 FR-026.
 *
 * NB: класс лежит в `commonTest` source set, поэтому работает на JVM/JS/iOS
 * без зависимости от Android Context — это и есть смысл mock-first
 * (CLAUDE.md §6): consumer-тесты не требуют Android для запуска.
 */
internal class FakeSessionStore : SessionStore {

    private val _changes = MutableStateFlow<SessionRecord?>(null)
    override val sessionChanges: Flow<SessionRecord?> = _changes.asStateFlow()

    override suspend fun save(session: SessionRecord) {
        _changes.value = session
    }

    override suspend fun current(): SessionRecord? = _changes.value

    override suspend fun clear() {
        _changes.value = null
    }
}
