package family.push.fakes

import com.launcher.wire.WireVersion

import family.push.api.PushHandler
import family.push.api.PushPayload

/**
 * T032 — In-memory [PushHandler] для unit tests. Captures invoked payloads.
 *
 * Use в combination с [FakePushHandlerRegistry] для receiver-side dispatch tests.
 */
class FakePushHandler : PushHandler {

    val handledPayloads: MutableList<PushPayload> = mutableListOf()

    /** If set non-null — handle() throws this on next call (для testing retry). */
    var nextException: Throwable? = null

    override suspend fun handle(payload: PushPayload) {
        nextException?.let {
            nextException = null
            throw it
        }
        handledPayloads += payload
    }
}
