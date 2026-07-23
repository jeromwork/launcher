# :core:crypto

KMP crypto foundation for the product family (launcher, messenger, gallery). Pure-Kotlin domain —
primitives (`family.crypto.api`) and the MLS-shaped group ports (`family.crypto.ports`). No Rust,
no openmls, no serialization (TASK-146); the real openmls adapter and persistence are TASK-124/125.

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

## Verification

```
./gradlew :core:crypto:jvmTest        # ports + fakes + contract tests + fitness (pure JVM, no device)
./gradlew :core:crypto:verifyCryptoIsolation
```
