package com.launcher.adapters.crypto

import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceId
import com.launcher.api.crypto.DeviceIdentity
import com.launcher.api.crypto.DeviceIdentityRepository
import com.launcher.api.crypto.DigitalSignature
import com.launcher.api.crypto.ED25519_SIGNATURE_SIZE
import com.launcher.api.crypto.SUPPORTED_SCHEMA_VERSION
import com.launcher.api.crypto.SecureKeystore
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.first

// Spec 011 T061 — связывает крипто-фундамент с pairing flow спека 007.
//
// Responsibilities:
//   1) Гарантировать наличие per-device X25519 + Ed25519 ключей в Keystore
//      (ensureKeysReady — идемпотентно).
//   2) Construct + sign + publish DeviceIdentity в /links/{linkId}/devices/{deviceId}
//      после consent.allow (publishOwnIdentity).
//
// Используется PairingService (managed-side) и PairingViewModel (admin-side)
// в момент, когда link установлен. DeviceId — same UUID что использует
// PairingService (spec 007 §FR-001) — read через DeviceIdProvider.
//
// Naming alignment: tasks.md называет это «PairingCoordinator»; реальный
// PairingService уже есть в спеке 007, поэтому здесь — Coordinator над
// pairing crypto setup (а не сам pairing flow).
class PairingCryptoCoordinator(
    private val keystore: SecureKeystore,
    private val signature: DigitalSignature,
    private val repo: DeviceIdentityRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    // Idempotent: вызывается на каждом старте app. На первом запуске генерирует
    // ключи; на последующих — no-op. Возвращает alias-ы для использования
    // в publishOwnIdentity.
    fun ensureKeysReady(): Outcome<KeyAliases, CryptoError> {
        return try {
            if (!keystore.exists(ALIAS_ENCRYPTION)) {
                keystore.generateAndStoreEncryption(ALIAS_ENCRYPTION)
            }
            if (!keystore.exists(ALIAS_SIGNING)) {
                keystore.generateAndStoreSigning(ALIAS_SIGNING)
            }
            Outcome.Success(KeyAliases(ALIAS_ENCRYPTION, ALIAS_SIGNING))
        } catch (e: Throwable) {
            Outcome.Failure(CryptoError.KeystoreFailure(e))
        }
    }

    // Вызывается после spec 007 consent.allow (обе стороны).
    // Собирает DeviceIdentity, подписывает payload, publishOwn в Firestore.
    suspend fun publishOwnIdentity(linkId: String): Outcome<DeviceIdentity, CryptoError> {
        // 1) Read deviceId (stable UUIDv4, spec 007 §FR-001).
        val deviceIdStr = try {
            deviceIdProvider.currentDeviceId().first()
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.KeystoreFailure(e))
        }
        val deviceId = try {
            DeviceId(deviceIdStr)
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.KeystoreFailure(IllegalStateException("invalid deviceId format: $deviceIdStr", e)))
        }

        // 2) Ensure keys + load.
        val ensured = ensureKeysReady()
        if (ensured is Outcome.Failure) return Outcome.Failure(ensured.error)

        val encPair = when (val r = keystore.loadEncryption(ALIAS_ENCRYPTION)) {
            is Outcome.Failure -> return Outcome.Failure(r.error)
            is Outcome.Success -> r.value
        }
        val signPair = when (val r = keystore.loadSigning(ALIAS_SIGNING)) {
            is Outcome.Failure -> return Outcome.Failure(r.error)
            is Outcome.Success -> r.value
        }

        // 3) Build identity с placeholder signature, потом подписать payload.
        val now = nowMillis()
        val unsigned = DeviceIdentity(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            deviceId = deviceId,
            publicKey = encPair.publicKey,
            signingPublicKey = signPair.publicKey,
            signedTimestamp = now,
            signature = ByteArray(ED25519_SIGNATURE_SIZE),  // placeholder
            createdAt = now,
        )
        val sig = signature.sign(unsigned.signedPayloadBytes(), signPair)
        val signed = DeviceIdentity(
            schemaVersion = unsigned.schemaVersion,
            deviceId = unsigned.deviceId,
            publicKey = unsigned.publicKey,
            signingPublicKey = unsigned.signingPublicKey,
            signedTimestamp = unsigned.signedTimestamp,
            signature = sig,
            createdAt = unsigned.createdAt,
        )

        // 4) Publish.
        return when (val r = repo.publishOwn(linkId, signed)) {
            is Outcome.Failure -> Outcome.Failure(r.error)
            is Outcome.Success -> Outcome.Success(signed)
        }
    }

    // Called по revoke (LinkRegistry.revoke()) — удалить keys + Firestore document
    // создаст retention dilemma. Сейчас оставляем ключи между revoke'ами — re-pair
    // переиспользует те же. Только при clear-data они исчезают.
    // Если в будущем нужен «fresh keys per pair» — добавить deleteKeys() здесь.

    data class KeyAliases(val encryption: String, val signing: String)

    companion object {
        // Domain-level alias'ы. Реальные Keystore aliases — derived в
        // AndroidKeystoreSecureKeystore (см. aesAliasFor / aesAliasForSign).
        const val ALIAS_ENCRYPTION = "spec011.encryption.own"
        const val ALIAS_SIGNING = "spec011.signing.own"
    }
}
