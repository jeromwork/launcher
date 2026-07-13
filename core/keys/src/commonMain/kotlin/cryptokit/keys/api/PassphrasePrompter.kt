package cryptokit.keys.api

/**
 * Port для prompt'а passphrase у пользователя. Реализуется в app-layer
 * через UI (Compose screen + ViewModel state flow).
 *
 * **Lifecycle ownership** (FR-013, FR-013a):
 *  • Возвращённый CharArray — owned by caller. Caller обязан вызвать
 *    `passphrase.fill(' ')` сразу после Argon2id derivation.
 *  • Implementation НЕ должен хранить passphrase в long-lived state'е —
 *    только pipe от UI input до return value.
 *
 * **Setup vs Recovery различие**:
 *  • [requestSetupPassphrase] — user придумывает новый passphrase. UI
 *    показывает hints о требованиях (≥ 8 chars), enables Android Autofill
 *    `autofillHints=[newPassword]`.
 *  • [requestRecoveryPassphrase] — user вводит existing passphrase. UI
 *    enables `autofillHints=[password]`. Wrong attempt → retry.
 *
 * TODO(ios H-7): CharArray zeroize semantics в Kotlin/Native (iosX64/iosArm64)
 * отличается от JVM (где CharArray.fill действительно мутирует in-place).
 * iOS deferred per F-CRYPTO decisions 2026-06-17. При активации iOS target —
 * verify, что `for (i in indices) charArr[i] = ' '` обнуляет underlying
 * memory на iOS, а не создаёт copy.
 */
interface PassphrasePrompter {
    /** Setup new passphrase. Возвращает [RecoveryError.Cancelled] если user отменил. */
    suspend fun requestSetupPassphrase(): Outcome<CharArray, RecoveryError>

    /** Existing passphrase entry для recovery. */
    suspend fun requestRecoveryPassphrase(): Outcome<CharArray, RecoveryError>
}
