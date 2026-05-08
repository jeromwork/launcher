package com.launcher.core.contacts

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * In-memory contact directory loaded once from `assets/mock_contacts.json`.
 *
 * Provider-agnostic: the same `ref → MockContact` lookup serves
 * `WhatsAppHandler` (needs `phoneE164` for `wa.me/<phone>`), `PhoneHandler`
 * (needs `phoneE164` for `tel:`), and `SmsHandler` (needs `phoneE164` for
 * `smsto:`). When backend contacts arrive (spec 007), this whole class is
 * replaced — the port (`ContactRepository` if it grows beyond mock) stays.
 *
 * Loading is lazy and threadsafe via `lazy { }`. Failures (missing asset,
 * malformed JSON) throw at first access — fail loud, the launcher cannot
 * function without contacts in spec 005's mock-driven flow.
 */
class MockContactsRepository(private val context: Context) {

    private val contacts: Map<String, MockContact> by lazy { loadFromAssets() }

    /** Look up a contact by its provider-agnostic [ref]. Returns null if unknown. */
    fun findByRef(ref: String): MockContact? = contacts[ref]

    /** Snapshot of the entire directory; intended for diagnostics / wizard pickers. */
    fun all(): List<MockContact> = contacts.values.toList()

    private fun loadFromAssets(): Map<String, MockContact> {
        val text = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val list = Json.decodeFromString<List<MockContact>>(text)
        return list.associateBy { it.ref }
    }

    companion object {
        const val ASSET_NAME = "mock_contacts.json"
    }
}

@Serializable
data class MockContact(
    val ref: String,
    val displayName: String,
    val phoneE164: String? = null,
)
