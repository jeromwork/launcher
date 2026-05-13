package com.launcher.api.push

/** Discriminated error surface for [PushSender]. Maps Worker HTTP status codes
 *  to domain concepts so admin-side UI surfaces "rate-limited" / "auth failed"
 *  rather than raw HTTP. */
sealed interface PushError {
    data object NetworkUnavailable : PushError

    /** Worker rejected the Firebase ID-token, or `uid != link.adminId`. */
    data object Unauthorized : PushError

    /** Worker responded 429 (per FR-025, SC-012). Admin should back off — see
     *  failure-recovery TODO in project-backlog. */
    data object RateLimited : PushError

    /** Worker 5xx — outage, deploy in flight, runtime crash. */
    data class WorkerError(val code: Int, val message: String) : PushError

    data class Unknown(val message: String) : PushError
}
