package com.launcher.preset.port

interface LocalizedResources {
    fun resolve(key: String, args: Map<String, String> = emptyMap()): String
}
