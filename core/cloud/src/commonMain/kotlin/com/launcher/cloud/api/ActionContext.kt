package com.launcher.cloud.api

data class ActionContext(
    val callerId: String,
    val parameters: Map<String, String> = emptyMap(),
)
