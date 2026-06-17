package com.launcher.api.wizard

interface PermissionRequestPort {
    suspend fun request(permission: String): PermissionResult
    fun isGranted(permission: String): Boolean
    fun isPermanentlyDenied(permission: String): Boolean
}

sealed class PermissionResult {
    data object Granted : PermissionResult()
    data object Denied : PermissionResult()
    data object PermanentlyDenied : PermissionResult()
}
