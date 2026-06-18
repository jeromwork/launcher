package com.launcher.api.auth

import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus

/**
 * Runtime selector между production-адаптерами [AuthProvider]:
 *  - GMS доступен → real Google adapter (Phase 5).
 *  - GMS недоступен → [NoSupportedAuthProvider] (всегда [AuthError.ProviderUnavailable]).
 *
 * Решение принимается **один раз** при создании DI graph (по сути, lazy
 * binding в KoinModule). Альтернатива «делать pick на каждый signIn» —
 * лишний оверхед, GMS-состояние стабильно в рамках процесса.
 *
 * Per spec 017 FR-018, data-model.md §"AuthAdapterSelector".
 *
 * NB: использует [GmsAvailabilityPort] (spec 010), а не прямой
 * `GoogleApiAvailability` — соответствует CLAUDE.md §1 (domain isolated
 * from infrastructure). Real check инкапсулирован в
 * `com.launcher.adapters.setup.GmsAvailabilityAdapter`.
 *
 * NB: Spec 010 различает 3 состояния GMS:
 *  - [GmsStatus.Available] → real adapter.
 *  - [GmsStatus.MissingRecoverable] → также real adapter (пользователь
 *    может разрешить системным диалогом). Если разрешить не получится,
 *    Credential Manager сам вернёт ошибку → [AuthError.ProviderUnavailable].
 *  - [GmsStatus.MissingFatal] → [NoSupportedAuthProvider]
 *    (Huawei без GMS, отдельная Spec 010 проблема).
 */
class AuthAdapterSelector(
    private val gmsAvailabilityPort: GmsAvailabilityPort,
    private val realAdapterFactory: () -> AuthProvider,
) {
    /**
     * Возвращает [AuthProvider], которого DI должен биндить как singleton.
     * Suspend, потому что [GmsAvailabilityPort.status] suspend (туда
     * залезает PackageManager).
     */
    suspend fun pick(): AuthProvider = when (gmsAvailabilityPort.status()) {
        GmsStatus.Available -> realAdapterFactory()
        is GmsStatus.MissingRecoverable -> realAdapterFactory()
        is GmsStatus.MissingFatal -> NoSupportedAuthProvider
    }
}
