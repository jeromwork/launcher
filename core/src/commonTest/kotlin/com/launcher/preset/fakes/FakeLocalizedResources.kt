package com.launcher.preset.fakes

import com.launcher.preset.port.LocalizedResources

class FakeLocalizedResources(
    private val bundle: Map<String, String> = emptyMap(),
) : LocalizedResources {
    override fun resolve(key: String, args: Map<String, String>): String {
        val template = bundle[key] ?: return key
        return args.entries.fold(template) { acc, (k, v) -> acc.replace("{$k}", v) }
    }
}
