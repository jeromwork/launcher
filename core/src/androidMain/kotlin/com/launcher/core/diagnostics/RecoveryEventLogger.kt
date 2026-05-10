package com.launcher.core.diagnostics

import android.util.Log

/**
 * Structured log emitter для recovery / failure paths per FR-052.
 *
 * Категории — закрытый набор (не строки на лету). Каждый event = `category` +
 * `code` + optional sanitised `details` map. **Никаких PII** (имён, номеров,
 * контактов, location, account ids).
 *
 * В спеке 006 logs идут через `android.util.Log` с tag `LauncherRecovery`.
 * В спеке 007 заменим на Firebase telemetry — интерфейс остаётся тот же,
 * call sites (BundledIconStorage, projections, banner buttons) не меняются.
 */
class RecoveryEventLogger {

    fun log(category: Category, code: String, details: Map<String, String> = emptyMap()) {
        val payload = buildString {
            append("category=").append(category.wireName)
            append(" code=").append(code)
            details.forEach { (k, v) ->
                append(' ').append(k).append('=').append(sanitize(v))
            }
        }
        Log.i(TAG, payload)
    }

    /**
     * Strip whitespace и trim к 64 chars. Это **не** PII filter (caller
     * отвечает за non-PII семантику), это просто формат-defence.
     */
    private fun sanitize(value: String): String =
        value.trim().replace(Regex("\\s+"), "_").take(MAX_VALUE_LEN)

    /**
     * Allowed event categories. Add new value here when introducing a new
     * recovery / failure path so categories stay grep-able.
     */
    enum class Category(val wireName: String) {
        /** DataStore file corrupted, fallback to defaults applied (FR-051). */
        Corruption("corruption"),

        /** Known namespace но resource (drawable, cached file) missing (FR-009/010). */
        MissingResource("missing_resource"),

        /** Unknown iconId namespace или invalid format (FR-009 fallback). */
        UnknownNamespace("unknown_namespace"),

        /** System API threw / returned unexpected value (NetworkCallback, AudioManager, etc.) (FR-049). */
        SystemApiFailure("system_api_failure"),

        /** User initiated banner action, OS rejected (DND restriction, missing intent target) (FR-050). */
        UserActionFailed("user_action_failed"),
    }

    companion object {
        const val TAG: String = "LauncherRecovery"
        private const val MAX_VALUE_LEN = 64
    }
}
