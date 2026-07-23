package family.crypto.ports

import family.crypto.api.values.Ciphertext

/**
 * Domain port for application-message encryption inside an established MLS group (TASK-123).
 *
 * A thin surface over the MLS epoch secrets held by the group: [encryptMessage] produces an
 * MLS application message ([Ciphertext], reused from `family.crypto.api.values` per FR-006);
 * [decryptMessage] is the inverse (the application-message branch of `GroupPort.processMessage`,
 * surfaced directly for the common send/receive path — final shape locked here, FR-009).
 *
 * Both `suspend` for the same reason as [GroupPort]: the real adapter is blocking-wrapped-in-IO.
 */
interface CryptoPort {

    /** Encrypt [plaintext] as an MLS application message in [groupId]'s current epoch. */
    suspend fun encryptMessage(groupId: GroupId, plaintext: ByteArray): Ciphertext

    /** Decrypt an MLS application [ciphertext] received in [groupId]. */
    suspend fun decryptMessage(groupId: GroupId, ciphertext: Ciphertext): ByteArray
}
