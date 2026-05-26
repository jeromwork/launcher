package com.launcher.api.crypto

import com.launcher.fake.crypto.FakeDeviceIdentityRepository
import com.launcher.fake.crypto.FakeDigitalSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

// Contract-level test для resolver shape. Подтверждает: filter out own deviceId,
// missing peer → empty list (graceful). Реальный adapter (PairRecipientResolver)
// делает то же, использует тот же DeviceIdentityRepository port.
class PairRecipientResolverContractTest {

    private fun resolverFor(
        repo: FakeDeviceIdentityRepository,
        own: DeviceId,
    ): RecipientResolver = RecipientResolver { linkId ->
        repo.listAll(linkId).filter { it.deviceId != own }
    }

    @Test
    fun resolveRecipients_returns_only_peer() = runTest {
        val signer = FakeDigitalSignature()
        val repo = FakeDeviceIdentityRepository(signer)
        val own = DeviceId("f1111111-1111-4111-8111-111111111111")
        val peer = DeviceId("f2222222-2222-4222-8222-222222222222")
        val link = "link-A"
        val signKpOwn = signer.generateEd25519Pair("own")
        val signKpPeer = signer.generateEd25519Pair("peer")
        repo.publishOwn(link, identity(own, signKpOwn.publicKey, signer, signKpOwn))
        repo.publishOwn(link, identity(peer, signKpPeer.publicKey, signer, signKpPeer))

        val recipients = resolverFor(repo, own).resolveRecipients(link)
        assertEquals(1, recipients.size)
        assertEquals(peer, recipients.first().deviceId)
    }

    @Test
    fun resolveRecipients_missing_peer_returns_empty() = runTest {
        val signer = FakeDigitalSignature()
        val repo = FakeDeviceIdentityRepository(signer)
        val own = DeviceId("f1111111-1111-4111-8111-111111111111")
        val signKp = signer.generateEd25519Pair("solo")
        repo.publishOwn("link-A", identity(own, signKp.publicKey, signer, signKp))

        val recipients = resolverFor(repo, own).resolveRecipients("link-A")
        assertTrue(recipients.isEmpty())
    }

    private fun identity(
        deviceId: DeviceId,
        signingPub: SigningPublicKey,
        signer: FakeDigitalSignature,
        signKp: DeviceSigningKeyPair,
    ): DeviceIdentity {
        val payload = DeviceIdentity(
            deviceId = deviceId,
            publicKey = PublicKey(ByteArray(X25519_KEY_SIZE) { it.toByte() }),
            signingPublicKey = signingPub,
            signedTimestamp = 1_700_000_000_000L,
            signature = ByteArray(ED25519_SIGNATURE_SIZE),
            createdAt = 1_700_000_000_000L,
        )
        val sig = signer.sign(payload.signedPayloadBytes(), signKp)
        return DeviceIdentity(
            deviceId = deviceId,
            publicKey = payload.publicKey,
            signingPublicKey = signingPub,
            signedTimestamp = payload.signedTimestamp,
            signature = sig,
            createdAt = payload.createdAt,
        )
    }
}
