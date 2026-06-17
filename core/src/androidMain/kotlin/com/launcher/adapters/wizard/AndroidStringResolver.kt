package com.launcher.adapters.wizard

import android.content.Context
import com.launcher.api.localization.StringResolver
import java.util.Locale

/**
 * Android binding for [StringResolver]. Looks up keys by name against the
 * Android resources table — works for the 11 locales declared in
 * core/src/androidMain/res/values-LOCALE/strings_wizard.xml.
 *
 * Fallback chain (per FR-029):
 *   1. Current locale (system Resources.configuration).
 *   2. EN base (Android picks values/ when no values-LANG/ match).
 *   3. Key literal (getIdentifier returns 0).
 *
 * Plurals: Android handles ICU plural rules via getQuantityString once
 * plurals entries are declared. F-3 keeps the simple {count} interpolation
 * shape for the MVP. ICU plurals can be added per-key as needed.
 */
class AndroidStringResolver(private val context: Context) : StringResolver {

    override fun resolve(key: String, args: Map<String, Any>): String {
        val resId = context.resources.getIdentifier(key, "string", context.packageName)
        val template = if (resId != 0) context.getString(resId) else key
        return interpolate(template, args)
    }

    override fun resolvePlural(key: String, count: Int, args: Map<String, Any>): String {
        val pluralId = context.resources.getIdentifier(key, "plurals", context.packageName)
        val template = if (pluralId != 0) {
            context.resources.getQuantityString(pluralId, count)
        } else {
            resolve(key, args + ("count" to count))
        }
        return interpolate(template, args + ("count" to count))
    }

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
