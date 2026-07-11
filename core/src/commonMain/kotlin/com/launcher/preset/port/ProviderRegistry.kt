package com.launcher.preset.port

import com.launcher.preset.adapter.NoOpProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.HandlerKey
import com.launcher.preset.model.Vendor

interface ProviderRegistry {
    fun resolve(component: Component): Provider<Component>
}

/**
 * Reference implementation with 3-tier fallback:
 *   (type, platform, vendor) → (type, platform, null) → (type, null, null) → NoOp.
 */
class DefaultProviderRegistry(
    private val handlers: Map<HandlerKey, Provider<out Component>>,
    private val runtimePlatform: String? = null,
    private val runtimeVendor: Vendor? = null,
) : ProviderRegistry {

    @Suppress("UNCHECKED_CAST")
    override fun resolve(component: Component): Provider<Component> {
        val type = component::class
        val hit = handlers[HandlerKey(type, runtimePlatform, runtimeVendor)]
            ?: handlers[HandlerKey(type, runtimePlatform, null)]
            ?: handlers[HandlerKey(type, null, null)]
        return (hit ?: NoOpProvider) as Provider<Component>
    }
}
