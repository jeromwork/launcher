package family.keys.api

/**
 * Причина недоступности auth-провайдера для key-management операций (FR-005,
 * data-model.md §8).
 *
 * **Forbidden values** (SC-007): строки `Google`, `GMS`, `Huawei`, `HMS`, `Apple`,
 * `Firebase`, `OAuth` НЕ должны появляться в именах enum-значений или KDoc.
 * Причина — domain isolation: этот enum живёт в `:core:keys` (чистый domain),
 * который не знает ни о каком конкретном identity provider.
 *
 * Mapping от конкретного провайдера к [AvailabilityReason] выполняется в adapter-слое
 * (`:app` / `AuthAvailabilityAndroidImpl`).
 *
 * @see AuthAvailabilityStatus
 * @see AuthAvailability
 */
enum class AvailabilityReason {
    /**
     * Ни одного подходящего auth-провайдера нет на устройстве.
     * Пример: device не имеет поддерживаемого sign-in метода.
     */
    NoSupportedProvider,

    /**
     * Keystore заблокирован (например, экран блокировки ещё не разблокирован).
     * Retry после unlockScreen.
     */
    KeystoreLocked,

    /**
     * Сеть недоступна — операции требующие network (backup upload, init-claim) не смогут завершиться.
     * Не блокирует локальные операции (key derive из Keystore).
     */
    NetworkUnreachable,
}
