package family.push.fakes

import com.launcher.wire.WireVersion

import family.push.api.FcmTokenPublisher
import family.push.api.FcmTokenPublisherError
import family.push.api.Outcome

/**
 * T031 — In-memory [FcmTokenPublisher] для unit tests. Captures published tokens.
 */
class FakeFcmTokenPublisher : FcmTokenPublisher {

    val publishedTokens: MutableList<String> = mutableListOf()
    private var nextResult: Outcome<Unit, FcmTokenPublisherError> = Outcome.Success(Unit)

    fun respondWith(result: Outcome<Unit, FcmTokenPublisherError>) {
        nextResult = result
    }

    override suspend fun publish(fcmToken: String): Outcome<Unit, FcmTokenPublisherError> {
        publishedTokens += fcmToken
        return nextResult
    }
}
