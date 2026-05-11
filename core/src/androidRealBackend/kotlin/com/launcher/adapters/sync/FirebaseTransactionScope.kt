package com.launcher.adapters.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.DocSnapshot
import com.launcher.api.sync.TransactionScope
import kotlinx.serialization.json.JsonElement

/**
 * [TransactionScope] adapter wrapping Firestore's [Transaction]. Lives only
 * for the duration of `FirebaseFirestore.runTransaction(...)` — captures
 * the SDK [Transaction] in a private field and never escapes it.
 *
 * Note on suspend/blocking: Firestore's [Transaction.get] is **blocking**
 * (it's executed on the SDK's internal transaction executor). We expose
 * `suspend` methods on the port so adapters can do async work, but in this
 * implementation the body runs synchronously inside the SDK call.
 */
internal class FirebaseTransactionScope(
    private val firestore: FirebaseFirestore,
    private val transaction: Transaction,
) : TransactionScope {

    override suspend fun get(path: DocPath): DocSnapshot? {
        val ref = firestore.document(path.rawPath)
        val snapshot = transaction.get(ref)
        return FirestoreDocMapper.fromFirestore(path, snapshot)
    }

    override suspend fun set(path: DocPath, data: JsonElement, schemaVersion: Int) {
        val ref = firestore.document(path.rawPath)
        val payload = FirestoreDocMapper.toFirestore(data).toMutableMap()
        // Ensure schemaVersion is present in the body — adapters write it
        // even if the caller forgot, so readers can short-circuit version
        // routing without re-parsing the payload.
        payload["schemaVersion"] = schemaVersion
        // Server-side updatedAt per FR-030. Read-back will surface this as
        // a real Long via FirestoreDocMapper.
        payload["updatedAt"] = FieldValue.serverTimestamp()
        transaction.set(ref, payload)
    }

    override suspend fun delete(path: DocPath) {
        val ref = firestore.document(path.rawPath)
        transaction.delete(ref)
    }
}
