package com.launcher.cloud.api

sealed class ActionResult {
    data class Success(val message: String? = null) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
}
