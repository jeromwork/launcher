package family.crypto.mls

import family.crypto.contracts.CryptoPortContract
import family.crypto.contracts.GroupPortContract
import family.crypto.contracts.KeyPackagePortContract
import family.crypto.ports.GroupPort
import family.crypto.ports.KeyPackage
import family.crypto.ports.KeyPackagePort
import kotlinx.coroutines.runBlocking

/**
 * The TASK-123 port contracts, re-run against the REAL openmls adapters (TASK-124, T014–T016).
 *
 * No contract test moved or was rewritten: the abstract classes live in `commonTest` and these
 * subclasses only supply the implementation (SC-001). Two behaviours are declared through the
 * contract's hooks rather than asserted differently, because they are MLS protocol rules, not
 * adapter choices:
 *  - `canProcessOwnCommit() = false` and `canDecryptOwnMessage() = false` — RFC 9420 forbids a
 *    member from unprotecting its own message; the fake, having no protocol, allowed it.
 *
 * These run as JVM unit tests against the host-native `crypto_ffi` cdylib (see
 * `core/crypto/build.gradle.kts`, `jna.library.path`).
 */
class OpenMlsGroupPortContractTest : GroupPortContract() {

    /** A separate device whose KeyPackages we add to the group under test. */
    private val peer = OpenMlsStack()

    override fun createGroupPort(): GroupPort = OpenMlsStack().group

    override suspend fun keyPackageFor(tag: String): KeyPackage = peer.keyPackages.generate()

    override fun canProcessOwnCommit(): Boolean = false
}

class OpenMlsCryptoPortContractTest : CryptoPortContract() {

    override fun createPorts(): Ports {
        val stack = OpenMlsStack()
        return Ports(crypto = stack.crypto, group = stack.group)
    }

    override fun canDecryptOwnMessage(): Boolean = false
}

class OpenMlsKeyPackagePortContractTest : KeyPackagePortContract() {

    override fun createKeyPackagePort(): KeyPackagePort = OpenMlsStack().keyPackages
}

/** Generate a genuine RFC 9420 KeyPackage from [stack], blocking (test helper). */
internal fun realKeyPackage(stack: OpenMlsStack, lastResort: Boolean = false): KeyPackage =
    runBlocking { stack.keyPackages.generate(lastResort) }
