package com.launcher.adapters.localization

import android.content.Context
import com.launcher.api.localization.LocaleProvider
import java.util.Locale

/**
 * TASK-126 Phase 7: moved from `com.launcher.adapters.wizard` to a
 * standalone localization package. Not wizard-related — carries no
 * dependency on the deleted `com.launcher.api.wizard.*` tree.
 */
class AndroidLocaleProvider(private val context: Context) : LocaleProvider {
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
}
