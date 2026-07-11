package com.launcher.app.preset.task120.adapter

import android.content.Context
import com.launcher.preset.port.LocalizedResources

/**
 * Resolves dotted i18n keys ("pool.font.description") against Android string
 * resources ("pool_font_description"). Fallback: return key itself.
 */
class AndroidLocalizedResources(private val context: Context) : LocalizedResources {

    override fun resolve(key: String, args: Map<String, String>): String {
        val resourceName = key.replace('.', '_')
        val id = context.resources.getIdentifier(resourceName, "string", context.packageName)
        if (id == 0) return key
        val raw = context.getString(id)
        if (args.isEmpty()) return raw
        return args.entries.fold(raw) { acc, (k, v) -> acc.replace("{$k}", v) }
    }
}
