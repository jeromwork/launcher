package cryptokit.keys.fakes

import cryptokit.keys.api.AuthAvailability
import cryptokit.keys.api.AuthAvailabilityStatus
import cryptokit.keys.api.AvailabilityReason

/**
 * Configurable [AuthAvailability] для тестов (FR-022, CLAUDE.md rule 6).
 *
 * По умолчанию возвращает [AuthAvailabilityStatus.Available]. Тест может
 * задать конкретный статус через [setStatus] перед вызовом [check].
 *
 * @see AuthAvailability
 * @see AuthAvailabilityStatus
 */
class FakeAuthAvailability : AuthAvailability {

    private var status: AuthAvailabilityStatus = AuthAvailabilityStatus.Available

    /**
     * Задаёт статус, который будет возвращён следующим вызовом [check].
     * @param status Фиксированный статус для теста.
     */
    fun setStatus(status: AuthAvailabilityStatus) {
        this.status = status
    }

    /** Shortcut: сделать unavailable с конкретной причиной. */
    fun setUnavailable(reason: AvailabilityReason) {
        status = AuthAvailabilityStatus.Unavailable(reason)
    }

    /** Shortcut: сделать доступным. */
    fun setAvailable() {
        status = AuthAvailabilityStatus.Available
    }

    override suspend fun check(): AuthAvailabilityStatus = status
}
