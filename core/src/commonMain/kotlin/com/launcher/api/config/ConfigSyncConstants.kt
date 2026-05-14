package com.launcher.api.config

/**
 * Single source of truth для всех числовых констант spec 008. Per Article XII §3
 * and CLAUDE.md «no magic numbers scattered».
 *
 * Origins:
 *  - [AUTOSAVE_DEBOUNCE_MS]: FR-056 + research.md §3 — autosave granularity.
 *  - [PUSH_NO_NETWORK_WARNING_DELAY_MS]: SC-001 + FR-015 — UX threshold для «нет интернета».
 *  - [POST_STARTUP_FETCH_DELAY_MS]: SC-004b — отложенный refresh после first frame.
 *  - [RESUMED_TRIGGER_THROTTLE_MS]: FR-022 T4 — Activity#onResume throttle (Q1 clarify).
 *  - [WORKMANAGER_POLL_INTERVAL_MINUTES]: FR-022 T3 — fallback poll (Q1 clarify, mirrors спек 007 FR-018).
 *  - [PUSH_BUTTON_DEBOUNCE_MS]: ux-quality CHK011 — accidental double-tap protection.
 */
object ConfigSyncConstants {
    const val AUTOSAVE_DEBOUNCE_MS: Long = 300L
    const val PUSH_NO_NETWORK_WARNING_DELAY_MS: Long = 5_000L
    const val POST_STARTUP_FETCH_DELAY_MS: Long = 5_000L
    const val RESUMED_TRIGGER_THROTTLE_MS: Long = 2L * 60_000L
    const val WORKMANAGER_POLL_INTERVAL_MINUTES: Long = 15L
    const val PUSH_BUTTON_DEBOUNCE_MS: Long = 500L
}
