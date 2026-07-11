package com.launcher.preset.model

import kotlin.reflect.KClass

data class HandlerKey(
    val componentType: KClass<out Component>,
    val platform: String? = null,
    val vendor: Vendor? = null,
)
