package com.launcher.fake.push

import com.launcher.api.push.PushPayload
import com.launcher.api.push.PushReceiver

/**
 * Recording [PushReceiver] for tests (FR-012). Stores every payload so tests
 * can assert what Managed-side handlers received and in what order.
 */
class FakePushReceiver : PushReceiver {

    private val log: MutableList<PushPayload> = mutableListOf()

    override suspend fun onPush(payload: PushPayload) {
        log.add(payload)
    }

    fun received(): List<PushPayload> = log.toList()

    fun lastPayload(): PushPayload? = log.lastOrNull()

    fun reset() {
        log.clear()
    }
}
