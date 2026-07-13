package cryptokit.keys.fakes

import cryptokit.keys.api.Outcome
import cryptokit.keys.api.PassphrasePrompter
import cryptokit.keys.api.RecoveryError

/**
 * Scripted Fake — last-in-first-out queue of passphrases для тестов.
 *
 * Setup'у возвращает первую passphrase из [setupResponses]; recovery — из
 * [recoveryResponses]. Если queue пуст — возвращает [Cancelled].
 */
class FakePassphrasePrompter(
    private val setupResponses: ArrayDeque<String> = ArrayDeque(),
    private val recoveryResponses: ArrayDeque<String> = ArrayDeque()
) : PassphrasePrompter {

    fun enqueueSetup(pw: String) { setupResponses.add(pw) }
    fun enqueueRecovery(pw: String) { recoveryResponses.add(pw) }

    override suspend fun requestSetupPassphrase(): Outcome<CharArray, RecoveryError> {
        val pw = setupResponses.removeFirstOrNull() ?: return Outcome.Failure(RecoveryError.Cancelled)
        return Outcome.Success(pw.toCharArray())
    }

    override suspend fun requestRecoveryPassphrase(): Outcome<CharArray, RecoveryError> {
        val pw = recoveryResponses.removeFirstOrNull() ?: return Outcome.Failure(RecoveryError.Cancelled)
        return Outcome.Success(pw.toCharArray())
    }
}
