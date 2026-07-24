# :core:crypto

KMP crypto foundation for the product family (launcher, messenger, gallery). `commonMain` is a
pure-Kotlin domain — primitives (`family.crypto.api`) and the MLS-shaped group ports
(`family.crypto.ports`): no Rust, no openmls, no serialization (TASK-146), fitness-gated. The real
MLS engine lives in `androidMain` (`family.crypto.mls`, TASK-124) over the `:crypto-ffi` Rust crate;
persistence is TASK-125.

`family` = the FAMILY OF PRODUCTS, not the target audience (see `build.gradle.kts` header).

## Domain ports (TASK-123)

Three interfaces let a consumer (messenger TASK-42, pairing TASK-67) be built and tested **today**
against in-memory fakes, before the real crypto engine exists (rule 6, mock-first):

- `GroupPort` — MLS group lifecycle (create / add / remove / self-update / process / two-phase merge).
- `CryptoPort` — application-message encrypt / decrypt inside an established group.
- `KeyPackagePort` — the async-add KeyPackage pool (publish / claim / local count, last-resort aware).

All value types are opaque `value class` wrappers over `ByteArray`/`String`; none carry
`@Serializable` — wire encoding is the TASK-124 adapter's job (crypto rule-11 exception: version and
wire live above the crypto primitive). `Ciphertext` is reused from `family.crypto.api.values`.

### Consumer usage (fakes)

This is the minimal send/receive flow a consumer writes against the ports. It compiles and runs on
the in-memory fakes with zero external dependencies (mirrored by `ConsumerUsageExampleTest`):

```kotlin
val group = FakeGroupPort()
val crypto = FakeCryptoPort(group)          // shares epoch state with the group

val chat = GroupId("family-chat")
group.createGroup(chat)

// Add a member (returns the commit + Welcome to fan out to the network).
val bundle = group.addMembers(chat, listOf(KeyPackage(aliceKeyPackageBytes)))
group.mergePendingCommit(chat)              // two-phase: epoch advances only on merge

// Send and receive an application message.
val envelope = crypto.encryptMessage(chat, "hello".encodeToByteArray())
val received = crypto.decryptMessage(chat, envelope)
check(received.decodeToString() == "hello")
```

The real openmls adapter (TASK-124) implements the SAME interfaces and passes the SAME contract
tests (`family.crypto.contracts.*`), so consumer code does not change when it lands.

## Real MLS engine (TASK-124, androidMain)

`family.crypto.mls` implements the same three ports over **openmls 0.8.1** through the UniFFI
binding of `:crypto-ffi`. State is **in-memory only** (lost on process death — SQLCipher is
TASK-125) and the MLS signing key is still **ephemeral**, generated in Rust
(`TODO(task-112)`: derive it from `KeyVault`). Architecture: [`crypto-mls.md`](../../docs/architecture/crypto-mls.md).

```kotlin
// One stack per device: the three ports share one MLS storage snapshot.
val alice = OpenMlsStack()
val bob = OpenMlsStack()

val chat = GroupId("family-chat")
alice.group.createGroup(chat)

// Bob publishes a genuine RFC 9420 KeyPackage; Alice adds him and fans out the Welcome.
val bundle = alice.group.addMembers(chat, listOf(bob.keyPackages.generate()))
alice.group.mergePendingCommit(chat)
bob.group.joinFromWelcome(bundle.welcome!!)

// Send.
val envelope = alice.crypto.encryptMessage(chat, "hello".encodeToByteArray())
check(bob.crypto.decryptMessage(chat, envelope).decodeToString() == "hello")
```

Three MLS rules that differ from the fakes and WILL bite a consumer:

- **You cannot decrypt your own message** — neither an application ciphertext nor your own commit.
  Keep your plaintext; only peers decrypt. Your own commit is applied with `mergePendingCommit`.
- **A peer's commit is two-phase**: `processMessage(...)` returns `StagedCommit`, and the epoch only
  advances on the following `mergePendingCommit(...)`.
- **Encrypting fails while proposals are pending** — commit them first
  (`commitToPendingProposals` + merge).

## Verification

```
./gradlew :core:crypto:jvmTest              # ports + fakes + contract tests + fitness (pure JVM)
./gradlew :core:crypto:testDebugUnitTest    # real openmls adapters (host cdylib via JNA)
./gradlew :core:crypto:verifyCryptoIsolation
cd crypto-ffi && cargo test --lib           # Rust-side MLS + snapshot tests
```
