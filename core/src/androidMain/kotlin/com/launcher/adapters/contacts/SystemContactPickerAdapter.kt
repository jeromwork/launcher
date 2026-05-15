package com.launcher.adapters.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.launcher.api.contacts.PickError
import com.launcher.api.contacts.RawPickerContact
import com.launcher.api.contacts.SystemContactPicker
import com.launcher.api.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android adapter for [SystemContactPicker] — resolves a contact URI
 * returned from `ActivityResultContracts.PickContact` into a
 * [RawPickerContact] (spec 009 FR-024).
 *
 * Two-step protocol:
 *  1. UI launches `ActivityResultContracts.PickContact` directly (it's
 *     an Activity result contract — `pickContact()` here exists only for
 *     the URI-resolution side so the port shape stays domain-clean).
 *  2. UI calls [resolveUri] with the URI from the contract result.
 *
 * Permission policy (FR-023): READ_CONTACTS is requested by the UI layer
 * before launching the picker. If permission was denied at call time,
 * [resolveUri] returns [PickError.PermissionDenied].
 *
 * The `suspend fun pickContact()` overload of the port is unusable from
 * pure-Kotlin (needs Activity); call [resolveUri] directly from the
 * `ActivityResultLauncher` callback instead. The bare `pickContact()`
 * here fails fast with `PickError.Other(IllegalStateException)` to make
 * misuse loud.
 *
 * Konsist gate (T100): `android.provider.ContactsContract` lives only
 * here; commonMain port exposes only [RawPickerContact].
 */
class SystemContactPickerAdapter(
    context: Context,
) : SystemContactPicker {

    private val appContext: Context = context.applicationContext

    override suspend fun pickContact(): Outcome<RawPickerContact, PickError> = Outcome.Failure(
        PickError.Other(
            IllegalStateException(
                "Direct pickContact() not supported on Android adapter — UI must launch " +
                    "ActivityResultContracts.PickContact and call resolveUri() with the result.",
            ),
        ),
    )

    /**
     * Resolve the URI returned by `ActivityResultContracts.PickContact` into
     * a domain [RawPickerContact]. Pure adapter logic; safe to call from any
     * dispatcher (does its own IO).
     */
    suspend fun resolveUri(uri: Uri?): Outcome<RawPickerContact, PickError> = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext Outcome.Failure(PickError.UserCancelled)
        if (!hasReadContactsPermission()) {
            return@withContext Outcome.Failure(PickError.PermissionDenied)
        }

        val resolver: ContentResolver = appContext.contentResolver
        try {
            val displayName = readDisplayName(resolver, uri)
                ?: return@withContext Outcome.Failure(
                    PickError.Other(IllegalStateException("contact display name missing")),
                )
            val contactId = readContactId(resolver, uri)
                ?: return@withContext Outcome.Failure(
                    PickError.Other(IllegalStateException("contact id missing")),
                )
            val phones = readPhoneNumbers(resolver, contactId)
            Outcome.Success(
                RawPickerContact(displayName = displayName, phoneNumbers = phones),
            )
        } catch (e: SecurityException) {
            Outcome.Failure(PickError.PermissionDenied)
        } catch (e: Throwable) {
            Outcome.Failure(PickError.Other(e))
        }
    }

    private fun hasReadContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun readDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { c: Cursor ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return null
    }

    private fun readContactId(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(ContactsContract.Contacts._ID)
        resolver.query(uri, projection, null, null, null)?.use { c: Cursor ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(ContactsContract.Contacts._ID)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return null
    }

    private fun readPhoneNumbers(resolver: ContentResolver, contactId: String): List<String> {
        val phones = mutableListOf<String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )?.use { c: Cursor ->
            val idx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                if (idx >= 0) {
                    c.getString(idx)?.let { phones.add(it) }
                }
            }
        }
        return phones
    }
}
