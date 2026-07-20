package family.keys.api

/**
 * Persistent attempt counter для recovery passphrase prompt (H-1 mitigation, FR-027).
 *
 * **Why persistent**: in-memory counter тривиально bypass'ится app kill / ViewModel
 * recreate. WhatsApp/Signal pattern — counter живёт в local KV (Android DataStore)
 * и переживает process death.
 *
 * **Auto-reset**: после `resetTimeoutMillis` от lastAttemptAt counter сбрасывается
 * (по умолчанию 1 час). Это balances UX (user не залочен навсегда) и security
 * (online brute-force throttling).
 *
 * **Accepted residual risk** (T122i): attacker может Clear App Data → counter
 * сброшен. Но **local root key cache тоже cleared**, и attacker должен заново
 * full recovery cycle. Документировано accepted.
 *
 * Реальная impl (Android DataStore) живёт в app/.
 */
interface PassphraseAttemptCounter {
    /** Текущий счётчик failed attempts для [uid]. */
    suspend fun currentCount(uid: String): Int

    /** Зафиксировать failed attempt. Возвращает новое значение counter'а. */
    suspend fun recordFailedAttempt(uid: String): Int

    /** Если прошло > resetTimeout с lastAttemptAt — сбросить counter. Idempotent. */
    suspend fun resetIfExpired(uid: String)

    /** Сбросить counter (успешный recovery / setup заново). */
    suspend fun clear(uid: String)

    /** Maximum allowed attempts перед lockout. По умолчанию 3 (US2 acceptance 4). */
    val maxAttempts: Int get() = 3
}
