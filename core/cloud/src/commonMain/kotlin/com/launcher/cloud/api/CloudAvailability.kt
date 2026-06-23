package com.launcher.cloud.api

import kotlinx.coroutines.flow.Flow

/**
 * Port «работаем ли мы сейчас с облаком». Источник правды — boolean флаг,
 * двигаемый только событиями [com.launcher.api.auth.AuthProvider.currentUser]
 * внутри implementation. Caller'ы могут только читать.
 *
 * ## Invariants (из contracts/cloud-availability-port.md)
 *
 *  - INV-1: [isCloudAvailable] возвращает текущий boolean из persistent storage
 *    (typ. < 10 мс).
 *  - INV-2: После внутренней записи (`true` через AuthProvider sign-in) —
 *    [isCloudAvailable] синхронно возвращает `true`.
 *  - INV-3: [isCloudAvailableFlow] эмиттит initial value сразу после subscribe.
 *  - INV-4: [isCloudAvailableFlow] эмиттит на каждое изменение флага.
 *  - INV-5: [isCloudAvailableFlow] не эмиттит дубли (distinct-until-changed).
 *  - INV-6: Default на свежем install / clear data = `false`.
 *  - INV-7: Persistence — после destroy + recreate instance (kill app)
 *    последнее записанное значение доступно.
 */
interface CloudAvailability {
    /**
     * Быстрое булево чтение. Используется в точке принятия решения cloud-фичи:
     * `if (cloudAvailability.isCloudAvailable()) { ... } else { showSignInExplanation() }`.
     */
    suspend fun isCloudAvailable(): Boolean

    /**
     * Реактивная подписка. Эмиттит initial value на subscribe (INV-3) и каждое
     * последующее изменение (INV-4), distinct-until-changed (INV-5).
     */
    val isCloudAvailableFlow: Flow<Boolean>
}
