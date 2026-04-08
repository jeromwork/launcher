package com.launcher.api

enum class CommunicationActionType {
    CALL,
    VIDEO,
}

data class WhatsAppHandoffRequest(
    val tileId: String,
    val contactRef: String,
    val actionType: CommunicationActionType,
    val actionCycleId: String,
    val homeSurfaceRef: String,
)

enum class WhatsAppHandoffResult {
    LAUNCH_STARTED,
    REJECTED_DUPLICATE_CYCLE,
    WHATSAPP_UNAVAILABLE,
    ACTION_NOT_SUPPORTED,
    LAUNCH_BLOCKED_BY_POLICY,
    LAUNCH_FAILED,
}

data class ReturnContextRecord(
    val schemaVersion: Int = 1,
    val initiatingTileRef: String,
    val homeSurfaceRef: String,
    val actionCycleRef: String,
    val savedAtEpochMs: Long,
)

enum class ReturnRestoreOutcome {
    RESTORED_EXACT_HOME,
    RESTORED_NEAREST_STABLE_HOME,
    NO_VALID_CONTEXT,
}

enum class CommunicationWarningCode {
    WHATSAPP_UNAVAILABLE,
    ACTION_NOT_SUPPORTED,
    HANDOFF_LAUNCH_FAILED,
    RESTORE_FALLBACK_USED,
}

enum class CommunicationDiagnosticEventType {
    WHATSAPP_LAUNCH_CONFIRMED,
    WHATSAPP_LAUNCH_FAILED,
    RETURN_RESTORE_SUCCESS,
    RETURN_RESTORE_FALLBACK,
    CONFIG_INVALID_OR_CAPABILITY_FAILED,
}

data class MockCommunicationEntry(
    val tileId: String,
    val contactRef: String,
    val displayNameKey: String,
    val photoRef: String?,
    val capability: Set<CommunicationActionType>,
)
