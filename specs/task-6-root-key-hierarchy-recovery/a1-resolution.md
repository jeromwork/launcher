# A1 Resolution — Wire-Format Alignment to F-5 Contract

**Decision date**: 2026-06-28
**Decision maker**: owner (jeromwork)
**Resolves STOP-block**: A1 (wire-format divergence between code and `contracts/recovery-key-backup-v1.md`)

---

## Decision

**Variant 2 (clean rewrite).** Restructure `RecoveryKeyBackupBlob` and all dependents to match `contracts/recovery-key-backup-v1.md` exactly. No legacy compatibility, no migration reader.

**Rationale**: zero deployed users on spec-018 in production — confirmed by owner. The cleanup cost (~3 hours) is one-shot and avoids carrying spec-018 naming forward into the F-5 future. The hybrid Variant 3 would have been required only if production users existed.

## What changes

### 1. Wire format — `RecoveryKeyBackupBlob.kt`

**Replace existing data class** with this exact shape per contract §3:

```kotlin
@Serializable
data class RecoveryKeyBackupBlob(
    val schemaVersion: Int = SCHEMA_VERSION_V1,
    val stableId: StableId,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val salt: ByteArray,
    val kdfParams: KdfParams,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val ciphertext: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray,
    val createdAt: Instant,
) {
    init {
        require(salt.size == 32) { "salt MUST be exactly 32 bytes (XChaCha20 spec)" }
        require(nonce.size == 24) { "nonce MUST be exactly 24 bytes (XChaCha20 nonce width)" }
        require(ciphertext.size >= 48) { "ciphertext MUST be ≥ 48 bytes (32 key + 16 Poly1305 tag)" }
        require(stableId.isNotEmpty()) { "stableId MUST be non-empty UUID v4" }
    }

    override fun equals(other: Any?): Boolean { /* keep contentEquals pattern */ }
    override fun hashCode(): Int { /* keep contentHashCode pattern */ }

    companion object {
        const val SCHEMA_VERSION_V1: Int = 1
        const val SCHEMA_VERSION: Int = SCHEMA_VERSION_V1
        // No ALGORITHM_V1 const — AEAD primitive is implicit per schemaVersion (v1 = XChaCha20-Poly1305).
        // KDF algorithm string lives inside kdfParams.algorithm.
    }
}
```

**Field changes from legacy:**

| Legacy (delete) | F-5 v1 (use) | Note |
|---|---|---|
| `algorithm: String` (top-level) | _removed_ | AEAD implicit per schemaVersion; KDF moves to `kdfParams.algorithm` |
| `kdfSalt: ByteArray` | `salt: ByteArray` | 32 bytes (was 16 in spec-018) — bump for XChaCha20 standard |
| `wrappedRootKey: ByteArray` | `ciphertext: ByteArray` | generic name per contract |
| — | `stableId: StableId` | **NEW**, required, UUID v4 |
| `createdAt: Long` (epoch ms) | `createdAt: kotlinx.datetime.Instant` | ISO-8601 string in JSON |

**Imports to add**: `kotlinx.datetime.Instant`. Already a dependency (`core/keys/build.gradle.kts:42`).

### 2. KDF param consolidation

**Delete** `core/keys/src/commonMain/kotlin/family/keys/api/PassphraseKdfParams.kt` (legacy spec-018 type).

**Migrate all 13 usages** to existing `KdfParams.kt` (T603, currently unused dead code).

`KdfParams` fields (already correct per contract §3):
- `algorithm: String` (default `"Argon2id"`, validated in `KNOWN_ALGORITHMS`)
- `iterations: Int` (default 3)
- `memoryKb: Int` (default 65_536) — **note: `Kb` not `Kib`** per contract
- `parallelism: Int` (default 1)

**Field-name change**: legacy `PassphraseKdfParams.memoryKib` → `KdfParams.memoryKb`. All call sites update.

### 3. AAD constant — preserve (scope exclusion)

In `RecoveryFlow.kt:175`:
```kotlin
const val AAD_PREFIX: String = "f5-recovery-vault-v1"
```

**Leave as-is.** Add KDoc:
```kotlin
/**
 * AAD bytes mixed into AEAD computation. Wire-format constant — renaming would
 * change ciphertext bytes for any blob produced before/after the rename, breaking
 * roundtrip even within v1. Legacy name retained per inventory.md §D4 scope-exclusion.
 */
```

### 4. Fixtures — regenerate per contract §2

**`core/keys/src/jvmTest/resources/fixtures/recovery-blob-v1-sample.json`**:

```json
{
  "schemaVersion": 1,
  "stableId": "00000000-0000-4000-8000-000000000001",
  "salt": "Tx5LqK8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cE=",
  "kdfParams": {
    "algorithm": "Argon2id",
    "iterations": 3,
    "memoryKb": 65536,
    "parallelism": 1
  },
  "ciphertext": "P3vG2X5n0K8mZ3JpV2cBgWQpA9Fk1tR8nXmYzQwLpEsT2cD5fHaB+cD/eFgHiJkLmNoPqRsTuVwXyZ0123==",
  "nonce": "B+gWQpA9Fk1tR8nXmYzQwLpEsT2cD5fH",
  "createdAt": "2026-06-28T10:00:00Z"
}
```

**`recovery-blob-v2-sample-future.json`**: same shape, `schemaVersion: 2`. T625 (`UnsupportedSchemaTest`) continues to expect `BackupError.UnsupportedSchema`.

### 5. Dependent code — update 4 production files

| File | Change |
|---|---|
| [Argon2idPassphraseKdf.kt](../../core/keys/src/commonMain/kotlin/family/keys/impl/Argon2idPassphraseKdf.kt) | `PassphraseKdfParams` → `KdfParams`; `kdfSalt` param name stays (it's a function arg, not a wire field); KDoc field-name references updated |
| [RecoveryFlow.kt](../../core/keys/src/commonMain/kotlin/family/keys/impl/RecoveryFlow.kt) | `PassphraseKdfParams` → `KdfParams`; `setupKdfParams` field type changes; `kdfSalt = random.nextBytes(16)` → `salt = random.nextBytes(32)`; blob construction uses `salt = salt, ciphertext = cipherPart, stableId = identity.stableId, createdAt = Clock.System.now()` |
| [FirestoreRecoveryKeyBackup.kt](../../app/src/realBackend/java/com/launcher/app/data/recovery/FirestoreRecoveryKeyBackup.kt) | line 149 stale comment `isValidRecoveryVaultBlob` — verify it still matches `firestore.rules` (which is also pending rule rename, but D4 scope-excluded). Update Firestore field-mapping if it serializes the data class explicitly. |
| [NoOpRecoveryKeyBackup.kt](../../app/src/main/java/com/launcher/app/data/recovery/NoOpRecoveryKeyBackup.kt) | No-op, but check imports |

### 6. Tests — update 7 files

| File | Change |
|---|---|
| [WireFormatJsonTest.kt](../../core/keys/src/commonTest/kotlin/family/keys/WireFormatJsonTest.kt) | construct blob with new fields, asserts on new field names |
| [RecoveryKeyBackupBlobBackwardCompatTest.kt](../../core/keys/src/commonTest/kotlin/family/keys/RecoveryKeyBackupBlobBackwardCompatTest.kt) **(legacy from D4)** | **DELETE** this file. The new contract-spec test in `jvmTest/contracts/` supersedes it. The inline-fixture is for spec-018 shape which no longer exists. |
| [RecoveryFlowTest.kt](../../core/keys/src/commonTest/kotlin/family/keys/RecoveryFlowTest.kt) | `PassphraseKdfParams` → `KdfParams`; field-name updates |
| [RecoveryKeyBackupContractTest.kt](../../core/keys/src/commonTest/kotlin/family/keys/contracts/RecoveryKeyBackupContractTest.kt) | construct blob with new shape |
| [RecoveryKeyBackupBlobRoundtripTest.kt](../../core/keys/src/jvmTest/kotlin/family/keys/contracts/RecoveryKeyBackupBlobRoundtripTest.kt) | update for new fields; **add explicit `stableId` preservation assertion** |
| [RecoveryKeyBackupBlobProviderAgnosticTest.kt](../../core/keys/src/jvmTest/kotlin/family/keys/contracts/RecoveryKeyBackupBlobProviderAgnosticTest.kt) | also assert PRESENCE of `stableId` field (defence-in-depth marker), in addition to absence of `googleSub|firebaseUid|email|phone` |
| [RecoveryKeyBackupBlobContractBackwardCompatTest.kt](../../core/keys/src/jvmTest/kotlin/family/keys/contracts/RecoveryKeyBackupBlobContractBackwardCompatTest.kt) | reads new v1 fixture; renamed expectations |
| [Argon2idAndroidPerfBenchmark.kt](../../core/keys/src/androidInstrumentedTest/kotlin/family/keys/Argon2idAndroidPerfBenchmark.kt) | `PassphraseKdfParams` → `KdfParams` |

### 7. Update `contracts/recovery-key-backup-v1.md` — verify alignment

Contract is the source of truth. **No changes expected** since code is being brought TO it. But verify:
- `salt` is documented as 32 bytes (was contract §3 silent? check). If §3 didn't specify, add explicit byte length.
- `nonce` documented as 24 bytes.
- `kdfParams.algorithm` value set is documented (currently only `"Argon2id"`).

If the contract is silent on byte lengths — **add them now**. Code's `init { require(...) }` guards will then match contract.

---

## Commit plan (1 commit, single logical unit)

```
refactor(keys): A1 align RecoveryKeyBackupBlob with F-5 v1 contract

Variant 2 (clean rewrite) per owner decision 2026-06-28:
- Add stableId field for server-side defence-in-depth
- Rename kdfSalt→salt (32 bytes), wrappedRootKey→ciphertext
- Move algorithm from top-level into kdfParams (KDF-only)
- Drop ALGORITHM_V1 const (AEAD implicit per schemaVersion=1)
- createdAt: Long (epoch ms) → kotlinx.datetime.Instant (ISO-8601)
- Consolidate KdfParams ←→ delete legacy PassphraseKdfParams (13 usages)
- Regenerate v1 + v2 fixtures per contract §2
- Update RecoveryFlow + Argon2idPassphraseKdf + 7 tests
- Preserve AAD_PREFIX "f5-recovery-vault-v1" (scope-excluded wire constant per D4)
- Delete superseded legacy commonTest/RecoveryKeyBackupBlobBackwardCompatTest.kt

Owner-confirmed: zero deployed spec-018 users → no migration needed.

Resolves: A1 STOP-block (specs/task-6-.../a1-resolution.md)
Pre-Phase-2 blocker — Phase 2 (T632-T643) now unblocked.

[deferred-*] markers: none
```

## Self-check before commit

1. `./gradlew :core:keys:jvmTest --rerun-tasks` — all 110+ tests green.
2. `grep -rn "PassphraseKdfParams\|kdfSalt\|wrappedRootKey\|ALGORITHM_V1" core/keys/ app/` — must return **only**:
   - `BackupError.kt` KDoc historical mention (if any) — OK
   - `Argon2idPassphraseKdf.kt` filename / class name itself (the impl is named *Passphrase*Kdf; only its `params` ARG type changes) — class rename optional, defer.
   - `RecoveryFlow.kt:175` AAD constant `"f5-recovery-vault-v1"` (string literal, not field) — OK
3. `grep -rn "stableId" core/keys/src/commonMain/kotlin/family/keys/api/RecoveryKeyBackupBlob.kt` — must show new field declaration.
4. Fixture JSON files MUST parse via `RecoveryBlobCodec.decode()` returning `Outcome.Success`. New `RecoveryKeyBackupBlobContractBackwardCompatTest` covers this — must be green.
5. tasks.md — add `T600.5` line (see below) and tick it in this same commit (rule 11 tick-sync).

## tasks.md entry (add before T601 line)

```markdown
- [x] **T600.5** **A1 resolution — wire-format alignment**: implement Variant 2 per [a1-resolution.md](./a1-resolution.md). Restructures `RecoveryKeyBackupBlob` to contract `recovery-key-backup-v1.md` shape, consolidates `PassphraseKdfParams`→`KdfParams`, regenerates fixtures, updates 4 production + 7 test files. Pre-Phase-2 blocker. (A1 STOP-block resolution)
```

## What this unblocks

- **Phase 2 (T632-T643)** — Android adapters can now build against final wire format.
- **Phase 4 (T653-T670)** — Worker `POST /backup` validates `body.stableId == claims.stableId` as defence-in-depth.
- **Contract spec stable** — no more reader/writer divergence to chase.

## What this does NOT change

- Spec 018 ConfigCipher2 envelope format (different wire format, different file).
- AAD bytes mixed into AEAD (preserved).
- `firestore.rules` field validators — those run server-side on the JSON shape; rules need updating in a separate commit (Phase 4 T654 deployment work, not this commit). Note for later: rule `isValidRecoveryVaultBlob` will need new field-name validators (`salt`, `ciphertext`, `kdfParams.algorithm`, `stableId`) — file an issue under TASK-6 Phase 4.

---

## Process reminders for Gemini

- Re-read [gemini-handoff.md DZ-2](./gemini-handoff.md) — forbidden field names in blob. `stableId` is NOT forbidden; `googleSub|firebaseUid|email|phone|displayName|username` are.
- This is **one commit, one logical unit**. Don't split across phases. Don't bundle with Phase 2 first task.
- After this commit, immediately push and STOP — owner / Claude reviews before Phase 2 starts.
- Do NOT run `gh pr create` (CLAUDE.md rule 14 / handoff DZ-12).
