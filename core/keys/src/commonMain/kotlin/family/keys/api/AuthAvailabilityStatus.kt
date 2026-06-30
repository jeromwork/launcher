package family.keys.api

/**
 * Статус доступности auth-провайдера для key-management операций (FR-005,
 * data-model.md §7).
 *
 * Возвращается из [AuthAvailability.check]. UI использует его чтобы решить —
 * показывать setup/recovery screens или fallback/offline экран.
 *
 * @see AuthAvailability
 * @see AvailabilityReason
 */
sealed class AuthAvailabilityStatus {
    /** Auth-провайдер доступен; key-management операции могут быть выполнены. */
    object Available : AuthAvailabilityStatus() {
        override fun toString(): String = "AuthAvailabilityStatus.Available"
    }

    /**
     * Auth-провайдер недоступен по указанной [reason].
     *
     * @param reason Конкретная причина недоступности; используется UI для отображения
     *   user-friendly explainer и определения retry-стратегии.
     */
    data class Unavailable(val reason: AvailabilityReason) : AuthAvailabilityStatus()
}
