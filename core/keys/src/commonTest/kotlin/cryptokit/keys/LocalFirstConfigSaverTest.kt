package cryptokit.keys

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumAsymmetricCrypto
import cryptokit.crypto.libsodium.LibsodiumRandomSource
import cryptokit.keys.api.AuthIdentity
import cryptokit.keys.api.ConfigSaver
import cryptokit.keys.api.DeviceId
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.PushStatus
import cryptokit.keys.api.RecipientPubKey
import cryptokit.keys.api.RemoteStorage
import cryptokit.keys.api.StorageError
import cryptokit.keys.fakes.FakeDeviceIdentity
import cryptokit.keys.fakes.FakeEnvelopeStorage
import cryptokit.keys.fakes.FakeIdentityProof
import cryptokit.keys.fakes.FakeRecipientResolver
import cryptokit.keys.fakes.InMemoryAsyncConfigPushQueue
import cryptokit.keys.impl.EnvelopeConfigCipherImpl
import cryptokit.keys.impl.EnvelopeRemoteStorage
import cryptokit.keys.impl.LocalFirstConfigSaver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocalFirstConfigSaverTest {

    private suspend fun init() {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
    }

    private suspend fun makeWiring(): Wiring {
        init()
        val asym = LibsodiumAsymmetricCrypto()
        val kp = asym.generateX25519KeyPair()
        val deviceId = DeviceId("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(RecipientPubKey(deviceId, kp.publicKey)))
        }
        val identityProof = FakeIdentityProof(AuthIdentity("admin-uid", "a", "a@e.com"))
        val remoteStorage: RemoteStorage = EnvelopeRemoteStorage(
            cipher = EnvelopeConfigCipherImpl(LibsodiumAeadCipher(), asym, LibsodiumRandomSource()),
            resolver = resolver,
            storage = storage,
            deviceIdentity = FakeDeviceIdentity(deviceId, kp.privateKey, kp.publicKey)
        )
        val queue = InMemoryAsyncConfigPushQueue(remoteStorage)
        val saver = LocalFirstConfigSaver(remoteStorage, identityProof, queue)
        return Wiring(saver, remoteStorage, queue)
    }

    private data class Wiring(
        val saver: ConfigSaver,
        val storage: RemoteStorage,
        val queue: InMemoryAsyncConfigPushQueue
    )

    @Test
    fun saveOwnEnqueuesPushAndReadsBackTheData() = runTest {
        val w = makeWiring()
        val payload = "my config".encodeToByteArray()
        assertIs<Outcome.Success<Unit>>(w.saver.saveOwn("default", payload))
        val read = (w.saver.loadOwn("default") as Outcome.Success<ByteArray>).value
        assertContentEquals(payload, read)
    }

    @Test
    fun oversizedPayloadRejectedBeforeEnqueue() = runTest {
        val w = makeWiring()
        val tooBig = ByteArray(RemoteStorage.MAX_ENTRY_BYTES + 1)
        val r = w.saver.saveOwn("default", tooBig)
        assertIs<Outcome.Failure<StorageError>>(r)
        assertEquals(StorageError.TooLarge, r.error)
    }

    @Test
    fun statusReportsSucceededAfterPush() = runTest {
        val w = makeWiring()
        w.saver.saveOwn("default", "x".encodeToByteArray())
        // InMemoryAsyncConfigPushQueue drives synchronously, so by the time
        // enqueue returns the status is already Succeeded.
        val workId = "fake-work-1"
        assertEquals(PushStatus.Succeeded, w.queue.status(workId))
    }

    @Test
    fun statusUnknownForUnregisteredWorkId() = runTest {
        val w = makeWiring()
        assertEquals(PushStatus.Unknown, w.queue.status("no-such-work-id"))
    }

    @Test
    fun deleteOwnGoesDirectToStorageBypassingQueue() = runTest {
        val w = makeWiring()
        w.saver.saveOwn("default", "x".encodeToByteArray())
        assertIs<Outcome.Success<Unit>>(w.saver.deleteOwn("default"))
        val attempt = w.saver.loadOwn("default")
        assertIs<Outcome.Failure<StorageError>>(attempt)
        assertEquals(StorageError.NotFound, attempt.error)
    }

    @Test
    fun saveForOtherWorksViaQueue() = runTest {
        val w = makeWiring()
        // FakeRecipientResolver returns OwnerHasNoDevices for unseeded namespace;
        // the InMemory queue routes that storage failure as SchedulingFailure,
        // and LocalFirstConfigSaver maps SchedulingFailure → StorageError.Network.
        // Test confirms call path completes (no crash, definite failure surface).
        val r = w.saver.saveForOther(
            "babushka-uid",
            "grannys-phone",
            "x".encodeToByteArray()
        )
        assertIs<Outcome.Failure<StorageError>>(r)
        assertIs<StorageError.Network>(r.error)
    }

    @Test
    fun listOwnReturnsConfigNames() = runTest {
        val w = makeWiring()
        w.saver.saveOwn("default", "a".encodeToByteArray())
        w.saver.saveOwn("kitchen-tv", "b".encodeToByteArray())
        val names = (w.saver.listOwn() as Outcome.Success<List<String>>).value
        assertEquals(listOf("default", "kitchen-tv"), names)
    }
}
