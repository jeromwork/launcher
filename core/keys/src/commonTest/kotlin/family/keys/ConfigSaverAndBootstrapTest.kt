package family.keys

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumAsymmetricCrypto
import cryptokit.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.api.BootstrapError
import family.keys.api.ConfigSaver
import family.keys.api.DeviceId
import family.keys.api.Outcome
import family.keys.api.RecipientPubKey
import family.keys.api.RemoteStorage
import family.keys.api.StorageError
import family.keys.fakes.FakeDeviceIdentity
import family.keys.fakes.FakeEnvelopeStorage
import family.keys.fakes.FakeIdentityProof
import family.keys.fakes.FakePublicKeyDirectory
import family.keys.fakes.FakeRecipientResolver
import family.keys.impl.DefaultEnvelopeBootstrap
import family.keys.impl.EnvelopeConfigCipherImpl
import family.keys.impl.EnvelopeRemoteStorage
import family.keys.impl.RemoteStorageConfigSaver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConfigSaverAndBootstrapTest {

    private suspend fun init() {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
    }

    @Test
    fun configSaverRoundtripOwnNamespace() = runTest {
        init()
        val asym = LibsodiumAsymmetricCrypto()
        val kp = asym.generateX25519KeyPair()
        val deviceId = DeviceId("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(RecipientPubKey(deviceId, kp.publicKey)))
        }
        val identityProof = FakeIdentityProof(AuthIdentity("admin-uid", "admin", "admin@example.com"))
        val remoteStorage: RemoteStorage = EnvelopeRemoteStorage(
            cipher = EnvelopeConfigCipherImpl(LibsodiumAeadCipher(), asym, LibsodiumRandomSource()),
            resolver = resolver,
            storage = storage,
            deviceIdentity = FakeDeviceIdentity(deviceId, kp.privateKey, kp.publicKey)
        )
        val saver: ConfigSaver = RemoteStorageConfigSaver(remoteStorage, identityProof)

        val payload = "my admin config".encodeToByteArray()
        assertIs<Outcome.Success<Unit>>(saver.saveOwn("default", payload))
        val loaded = (saver.loadOwn("default") as Outcome.Success<ByteArray>).value
        assertContentEquals(payload, loaded)
    }

    @Test
    fun configSaverListOwnReturnsNamesNotKeys() = runTest {
        init()
        val asym = LibsodiumAsymmetricCrypto()
        val kp = asym.generateX25519KeyPair()
        val deviceId = DeviceId("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            seed("admin-uid", listOf(RecipientPubKey(deviceId, kp.publicKey)))
        }
        val identityProof = FakeIdentityProof(AuthIdentity("admin-uid", "admin", "admin@example.com"))
        val remoteStorage: RemoteStorage = EnvelopeRemoteStorage(
            cipher = EnvelopeConfigCipherImpl(LibsodiumAeadCipher(), asym, LibsodiumRandomSource()),
            resolver = resolver,
            storage = storage,
            deviceIdentity = FakeDeviceIdentity(deviceId, kp.privateKey, kp.publicKey)
        )
        val saver: ConfigSaver = RemoteStorageConfigSaver(remoteStorage, identityProof)
        saver.saveOwn("default", "a".encodeToByteArray())
        saver.saveOwn("kitchen-tv", "b".encodeToByteArray())
        saver.saveOwn("grannys-phone", "c".encodeToByteArray())

        val names = (saver.listOwn() as Outcome.Success<List<String>>).value
        assertEquals(listOf("default", "grannys-phone", "kitchen-tv"), names)
    }

    @Test
    fun configSaverNoIdentityWhenNotSignedIn() = runTest {
        init()
        val asym = LibsodiumAsymmetricCrypto()
        val kp = asym.generateX25519KeyPair()
        val deviceId = DeviceId("admin-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver()
        // FakeIdentityProof default is signed-out (null identity).
        val identityProof = FakeIdentityProof(null)
        val remoteStorage: RemoteStorage = EnvelopeRemoteStorage(
            cipher = EnvelopeConfigCipherImpl(LibsodiumAeadCipher(), asym, LibsodiumRandomSource()),
            resolver = resolver,
            storage = storage,
            deviceIdentity = FakeDeviceIdentity(deviceId, kp.privateKey, kp.publicKey)
        )
        val saver: ConfigSaver = RemoteStorageConfigSaver(remoteStorage, identityProof)

        val r = saver.saveOwn("default", "x".encodeToByteArray())
        assertIs<Outcome.Failure<StorageError>>(r)
        assertEquals(StorageError.NoIdentity, r.error)
    }

    @Test
    fun configSaverCrossUserSaveAndLoad() = runTest {
        init()
        val asym = LibsodiumAsymmetricCrypto()
        val adminKp = asym.generateX25519KeyPair()
        val babushkaKp = asym.generateX25519KeyPair()
        val adminDevId = DeviceId("admin-phone")
        val babushkaDevId = DeviceId("babushka-phone")
        val storage = FakeEnvelopeStorage()
        val resolver = FakeRecipientResolver().apply {
            // Owner perspective (babushka): both her phone AND admin's phone (granted helper).
            seed("babushka-uid", listOf(
                RecipientPubKey(babushkaDevId, babushkaKp.publicKey),
                RecipientPubKey(adminDevId, adminKp.publicKey)
            ))
        }

        // Admin signs in, writes to babushka's namespace, reads back.
        val adminIdentity = FakeIdentityProof(AuthIdentity("admin-uid", "admin", "a@e.com"))
        val adminStorage: RemoteStorage = EnvelopeRemoteStorage(
            cipher = EnvelopeConfigCipherImpl(LibsodiumAeadCipher(), asym, LibsodiumRandomSource()),
            resolver = resolver,
            storage = storage,
            deviceIdentity = FakeDeviceIdentity(adminDevId, adminKp.privateKey, adminKp.publicKey)
        )
        val adminSaver: ConfigSaver = RemoteStorageConfigSaver(adminStorage, adminIdentity)

        val payload = "edited by admin".encodeToByteArray()
        assertIs<Outcome.Success<Unit>>(
            adminSaver.saveForOther("babushka-uid", "grannys-phone", payload)
        )
        val adminReadback = (adminSaver.loadForOther("babushka-uid", "grannys-phone")
                as Outcome.Success<ByteArray>).value
        assertContentEquals(payload, adminReadback)

        // Babushka signs in on her phone, reads the same config.
        val babushkaIdentity = FakeIdentityProof(AuthIdentity("babushka-uid", "babushka", "b@e.com"))
        val babushkaStorage: RemoteStorage = EnvelopeRemoteStorage(
            cipher = EnvelopeConfigCipherImpl(LibsodiumAeadCipher(), asym, LibsodiumRandomSource()),
            resolver = resolver,
            storage = storage,
            deviceIdentity = FakeDeviceIdentity(babushkaDevId, babushkaKp.privateKey, babushkaKp.publicKey)
        )
        val babushkaSaver: ConfigSaver = RemoteStorageConfigSaver(babushkaStorage, babushkaIdentity)
        val babushkaReadback = (babushkaSaver.loadOwn("grannys-phone")
                as Outcome.Success<ByteArray>).value
        assertContentEquals(payload, babushkaReadback)
    }

    @Test
    fun envelopeBootstrapPublishesPubKey() = runTest {
        val deviceId = DeviceId("admin-phone")
        val pub = ByteArray(32) { it.toByte() }
        val identityProof = FakeIdentityProof(AuthIdentity("admin-uid", "admin", "a@e.com"))
        val deviceIdentity = FakeDeviceIdentity(deviceId, ByteArray(32) { 0 }, pub)
        val directory = FakePublicKeyDirectory()
        val bootstrap = DefaultEnvelopeBootstrap(identityProof, deviceIdentity, directory)

        assertIs<Outcome.Success<Unit>>(bootstrap.bootstrap())
        val published = directory.fetchDevicesFor("admin-uid")
        assertIs<Outcome.Success<List<RecipientPubKey>>>(published)
        assertEquals(1, published.value.size)
        assertEquals(deviceId, published.value[0].deviceId)
        assertContentEquals(pub, published.value[0].pubKey)
    }

    @Test
    fun envelopeBootstrapNoIdentityReturnsNoIdentityError() = runTest {
        val identityProof = FakeIdentityProof(null)
        val bootstrap = DefaultEnvelopeBootstrap(
            identityProof,
            FakeDeviceIdentity(DeviceId("phone"), ByteArray(32), ByteArray(32)),
            FakePublicKeyDirectory()
        )
        val r = bootstrap.bootstrap()
        assertIs<Outcome.Failure<BootstrapError>>(r)
        assertEquals(BootstrapError.NoIdentity, r.error)
    }
}
