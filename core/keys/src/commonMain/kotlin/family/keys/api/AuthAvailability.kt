package family.keys.api

/**
 * Порт для проверки доступности auth-провайдера для key-management операций (FR-005).
 *
 * **Domain isolation**: этот порт НЕ знает ни о каком конкретном identity provider
 * (Google, Huawei, Apple, etc.) — возвращает только [AuthAvailabilityStatus].
 * Mapping выполняется в adapter-слое (`AuthAvailabilityAndroidImpl`).
 *
 * **Usage**:
 *  - [RecoveryViewModel] вызывает [check] при старте чтобы решить — показывать
 *    setup/recovery screen или fallback/offline-only UI.
 *  - Может вызываться повторно при изменении сетевого состояния.
 *
 * @see AuthAvailabilityStatus
 * @see AvailabilityReason
 */
interface AuthAvailability {
    /**
     * Проверяет доступность auth-провайдера.
     *
     * Вызов НЕ выполняет network request — проверяет локальное состояние
     * (залогинен ли пользователь, заблокирован ли Keystore, есть ли сеть).
     *
     * @return [AuthAvailabilityStatus.Available] если key-management операции можно выполнить;
     *         [AuthAvailabilityStatus.Unavailable] с конкретной причиной иначе.
     */
    suspend fun check(): AuthAvailabilityStatus
}
