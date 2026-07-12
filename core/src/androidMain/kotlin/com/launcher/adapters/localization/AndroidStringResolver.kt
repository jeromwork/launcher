package com.launcher.adapters.localization

import android.content.Context
import com.launcher.api.localization.StringResolver
import java.util.Locale

/**
 * TASK-126 Phase 7: moved from `com.launcher.adapters.wizard`. Not
 * wizard-related — pure Android resource lookup for the shared
 * [StringResolver] port.
 */
class AndroidStringResolver(private val context: Context) : StringResolver {

    override fun resolve(key: String, args: Map<String, Any>): String {
        val resName = key.toAndroidResName()
        val resId = context.resources.getIdentifier(resName, "string", context.packageName)
        val template = if (resId != 0) context.getString(resId) else key
        return interpolate(template, args)
    }

    override fun resolvePlural(key: String, count: Int, args: Map<String, Any>): String {
        val resName = key.toAndroidResName()
        val pluralId = context.resources.getIdentifier(resName, "plurals", context.packageName)
        val template = if (pluralId != 0) {
            context.resources.getQuantityString(pluralId, count)
        } else {
            resolve(key, args + ("count" to count))
        }
        return interpolate(template, args + ("count" to count))
    }

    private fun String.toAndroidResName(): String =
        this.replace('.', '_').replace('-', '_')

    override fun currentLocaleTag(): String {
        val config = context.resources.configuration
        val locale: Locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
        return locale.toLanguageTag()
    }

    private fun interpolate(template: String, args: Map<String, Any>): String {
        if (args.isEmpty()) return template
        var out = template
        for ((k, v) in args) {
            out = out.replace("{$k}", v.toString())
        }
        return out
    }
}
