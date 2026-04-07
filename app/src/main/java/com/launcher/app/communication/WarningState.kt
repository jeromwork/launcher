package com.launcher.app.communication

import com.launcher.api.CommunicationWarningCode

data class WarningState(
    val code: CommunicationWarningCode,
    val title: String,
    val message: String,
)

