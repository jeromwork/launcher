package family.keys

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumAsymmetricCrypto
import cryptokit.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.DeviceId
import family.keys.api.Outcome
import family.keys.api.RecipientPubKey
import family.keys.api.RemoteStorage
import family.keys.api.StorageError
import family.keys.fakes.FakeDeviceIdentity
import family.keys.fakes.FakeEnvelopeStorage
import family.keys.fakes.FakeRecipientResolver
import family.keys.impl.EnvelopeConfigCipherImpl
import family.keys.impl.EnvelopeRemoteStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Facade-level tests for [RemoteStorage] — verifies caller never sees crypto
 * internals and the three sub-ports (cipher, resolver, storage) wire correctly.
 */
class EnvelopeRemoteStorageTest {

    private suspend fun init() {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
    }

    private data class Device(
        val deviceId: DeviceId,
        val privKey: ByteArray,
        val pubKey: ByteArray
    ) {
        fun asRecipient() = RecipientPubKey(deviceId, pubKey)
    }

    private suspend fun newDevice(idStr: String): Device {
        val asym = LibsodiumAsymmetricCrypto()
        val kp = asym.generateX25519KeyPair()
        return Device(DeviceId(idStr), kp.privateKey, kp.publicKey)
    }

    private fun makeFacade(device: Device, resolver: FakeRecipientResolver, storage: FakeEnvelopeStorage): RemoteStorage =
        EnvelopeRemoteStorage(
            cipher = EnvelopeConfigCipherImpl(
                aead = LibsodiumAeadCipher(),
                asymmetric = LibsodiumAsymmetricCrypto(),
                random = LibsodiumRandomSource()
            ),
            resolver = resolver,
            storage = storage,
            deviceIdentity = FakeDeviceIdentity(device.deviceId, device.privKey, device.pubKey)
        )

    @Test
    fun putAndGetSelfEdit() = runTest {
        init()
        val phone = newDevice("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(phone.asRecipient()))
        }
        val facade = makeFacade(phone, resolver, storage)
        val plaintext = "my config".encodeToByteArray()

        assertIs<Outcome.Success<Unit>>(facade.put("admin-uid", "config/default", plaintext))
        val read = (facade.get("admin-uid", "config/default") as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, read)
    }

    @Test
    fun multiDeviceSameUidEachDeviceReadsOwn() = runTest {
        init()
        val phone = newDevice("admin-phone")
        val tablet = newDevice("admin-tablet")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(phone.asRecipient(), tablet.asRecipient()))
        }

        // Write from phone.
        val phoneFacade = makeFacade(phone, resolver, storage)
        val plaintext = "shared admin config".encodeToByteArray()
        assertIs<Outcome.Success<Unit>>(phoneFacade.put("admin-uid", "config/default", plaintext))

        // Read from tablet.
        val tabletFacade = makeFacade(tablet, resolver, storage)
        val read = (tabletFacade.get("admin-uid", "config/default") as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, read)
    }

    @Test
    fun crossUserAdminEditsBabushkaConfigViaGrantBothCanRead() = runTest {
        init()
        val adminPhone = newDevice("admin-phone")
        val babushkaPhone = newDevice("babushka-phone")
        val storage = FakeEnvelopeStorage()
        // Resolver returns babushka's namespace recipients: her phone + admin who has her grant.
        val resolver = FakeRecipientResolver().apply {
            seed("babushka-uid", listOf(babushkaPhone.asRecipient(), adminPhone.asRecipient()))
        }

        // Admin writes to babushka's namespace.
        val adminFacade = makeFacade(adminPhone, resolver, storage)
        val plaintext = "edited by admin".encodeToByteArray()
        assertIs<Outcome.Success<Unit>>(adminFacade.put("babushka-uid", "config/grannys-phone", plaintext))

        // Admin can read it back.
        val adminRead = (adminFacade.get("babushka-uid", "config/grannys-phone") as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, adminRead)

        // Babushka can also read.
        val babushkaFacade = makeFacade(babushkaPhone, resolver, storage)
        val babushkaRead = (babushkaFacade.get("babushka-uid", "config/grannys-phone") as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, babushkaRead)
    }

    @Test
    fun unauthorizedDeviceCannotReadOthersConfig() = runTest {
        init()
        val phone = newDevice("admin-phone")
        val stranger = newDevice("stranger-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(phone.asRecipient()))
        }

        val ownerFacade = makeFacade(phone, resolver, storage)
        assertIs<Outcome.Success<Unit>>(ownerFacade.put("admin-uid", "config/default", "secret".encodeToByteArray()))

        // Stranger tries to read using same storage.
        val strangerFacade = makeFacade(stranger, resolver, storage)
        val attempt = strangerFacade.get("admin-uid", "config/default")
        assertIs<Outcome.Failure<StorageError>>(attempt)
        assertEquals(StorageError.NotARecipient, attempt.error)
    }

    @Test
    fun listReturnsKeysSortedAndFiltered() = runTest {
        init()
        val phone = newDevice("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(phone.asRecipient()))
        }
        val facade = makeFacade(phone, resolver, storage)
        facade.put("admin-uid", "config/default", "a".encodeToByteArray())
        facade.put("admin-uid", "config/kitchen-tv", "b".encodeToByteArray())
        facade.put("admin-uid", "photo/img1.jpg", "c".encodeToByteArray())

        val configs = (facade.list("admin-uid", "config/") as Outcome.Success<List<String>>).value
        assertEquals(listOf("config/default", "config/kitchen-tv"), configs)

        val all = (facade.list("admin-uid", "") as Outcome.Success<List<String>>).value
        assertEquals(3, all.size)
    }

    @Test
    fun deleteRemovesEntry() = runTest {
        init()
        val phone = newDevice("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(phone.asRecipient()))
        }
        val facade = makeFacade(phone, resolver, storage)
        facade.put("admin-uid", "config/default", "x".encodeToByteArray())
        assertIs<Outcome.Success<Unit>>(facade.delete("admin-uid", "config/default"))
        val attempt = facade.get("admin-uid", "config/default")
        assertIs<Outcome.Failure<StorageError>>(attempt)
        assertEquals(StorageError.NotFound, attempt.error)
    }

    @Test
    fun oversizedPayloadRejected() = runTest {
        init()
        val phone = newDevice("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(phone.asRecipient()))
        }
        val facade = makeFacade(phone, resolver, storage)
        val tooBig = ByteArray(RemoteStorage.MAX_ENTRY_BYTES + 1)
        val attempt = facade.put("admin-uid", "config/default", tooBig)
        assertIs<Outcome.Failure<StorageError>>(attempt)
        assertEquals(StorageError.TooLarge, attempt.error)
    }

    @Test
    fun ownerWithoutDevicesCannotWrite() = runTest {
        init()
        val phone = newDevice("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver() // no seed for "babushka-uid"
        val facade = makeFacade(phone, resolver, storage)
        val attempt = facade.put("babushka-uid", "config/grannys-phone", "x".encodeToByteArray())
        assertIs<Outcome.Failure<StorageError>>(attempt)
        // Resolver returned OwnerHasNoDevices; mapped to Malformed.
        assertIs<StorageError.Malformed>(attempt.error)
    }

    @Test
    fun firestorePathOpaqueToCallerGreptest() = runTest {
        // SC-001 emulator analog at unit-test level: stored envelope contains
        // no plaintext substring of the original payload.
        init()
        val phone = newDevice("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(phone.asRecipient()))
        }
        val facade = makeFacade(phone, resolver, storage)
        val marker = "Bobby Tables 555-1234"
        val plaintext = "prefix:$marker:suffix".encodeToByteArray()
        facade.put("admin-uid", "config/default", plaintext)

        val envelope = storage.snapshot()["admin-uid"]!!["config/default"]!!
        val markerBytes = marker.encodeToByteArray()
        // Ciphertext does not contain the marker as a contiguous substring.
        for (i in 0..(envelope.ciphertext.size - markerBytes.size)) {
            val slice = envelope.ciphertext.copyOfRange(i, i + markerBytes.size)
            assertTrue(
                !slice.contentEquals(markerBytes),
                "plaintext marker leaked at offset $i — encryption opacity violated"
            )
        }
    }
}
