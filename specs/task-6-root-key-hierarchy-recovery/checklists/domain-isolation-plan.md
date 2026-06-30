# Checklist: domain-isolation — **plan-level re-run**

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Data model**: [../data-model.md](../data-model.md)
**Contracts**: [recovery-key-backup-v1.md](../contracts/recovery-key-backup-v1.md), [worker-api-v1.md](../contracts/worker-api-v1.md)
**Date**: 2026-06-28
**Skill**: [.claude/skills/checklist-domain-isolation/SKILL.md](../../../.claude/skills/checklist-domain-isolation/SKILL.md)

> **Plan-level re-run.** The spec-level pass already scored 16/16 ✓. This re-run applies each CHK item to the **plan-level artifacts** (plan.md project structure, data-model.md type declarations, contracts/) to verify the plan didn't reintroduce isolation violations during decomposition.

---

## Vendor SDKs

| CHK | Verdict | Evidence (plan-level) |
|-----|---------|-----------------------|
| CHK001 | ✓ | `data-model.md` §1–§10: every domain type (`StableId`, `RootKey`, `DerivedKey`, `KdfParams`, `RecoveryKeyBackupBlob`, `AuthAvailabilityStatus`, `AvailabilityReason`, `RootKeyError`, `BackupError`) is plain Kotlin — no `FirebaseUser`, `GoogleSignInAccount`, `R2Bucket`, `OkHttp`, `libsodium` types appear. `plan.md` Project Structure confines libsodium-kmp / firebase-admin / hono to `core/keys/androidMain/` and `workers/backup/`. |
| CHK002 | ✓ | One adapter per SDK: `Argon2RootKeyManager` (libsodium via `core/crypto` KeyDerivation port — double-wrapped), `WorkerRecoveryKeyBackup` (OkHttp + Firebase JWT), `AndroidKeystoreRegistry` (Android Keystore via `core/crypto` SecureKeystore port). Worker artifact (`workers/backup/`) is itself an adapter — Cloudflare R2 / firebase-admin stay inside TS code, never leak into Kotlin domain. |
| CHK003 | ✓ | "Vendor disappears" test documented in `plan.md` Summary + Risks (R-3, R-4, R-5): swap to own-server PostgreSQL/S3 per SRV-RECOVERY-001 = single file change to `WorkerRecoveryKeyBackup` (becomes `HttpRecoveryBackupStorage`). Domain ports (`RecoveryKeyBackup`) untouched. |

## Transport types

| CHK | Verdict | Evidence |
|-----|---------|----------|
| CHK004 | ✓ | Port signatures in `core/keys/commonMain/api/` (plan.md Project Structure lines 122–134) take/return only domain types — no `Response`, `Call`, `OkHttpClient`, `Request`, `Bearer`, HTTP status codes. Worker HTTP concerns (`Authorization: Bearer`, `Idempotency-Key`, 401/403/404/409/429/507) live only in `contracts/worker-api-v1.md` and `WorkerRecoveryKeyBackup` adapter. |
| CHK005 | ✓ | `RecoveryKeyBackupBlob` declared as domain data class in `data-model.md §6` under `family.keys.api`; serializers live in `RecoveryBlobCodec.kt` (impl/, plan.md line 138). HTTP error codes mapped to domain `BackupError` sealed class (`data-model.md §10`) at the adapter boundary, not exposed as HTTP status to callers. |

## Platform types

| CHK | Verdict | Evidence |
|-----|---------|----------|
| CHK006 | ✓ | `data-model.md` types use only `String`, `Int`, `ByteArray`, `Instant`, sealed/enum classes. No `Context`, `Intent`, `Uri`, `Bundle`, `LifecycleOwner`, `SavedStateHandle` in any commonMain type. `SavedStateHandle` (FR-017) appears only in `RecoveryViewModel.kt` under `app/src/main/kotlin/` (plan.md line 170) — adapter side. |
| CHK007 | ✓ | `StableId = String` (typealias, `data-model.md §2`) — pure domain projection of the UUID, not `android.accounts.Account` or `FirebaseUser.uid`. `RootKey` / `DerivedKey` wrap raw `ByteArray` (32 bytes) — primitive, no platform key-handle types leaking through. |

## Ports

| CHK | Verdict | Evidence |
|-----|---------|----------|
| CHK008 | ✓ | Four ports in `commonMain/api/`: `KeyRegistry`, `RootKeyManager`, `RecoveryKeyBackup`, `AuthAvailability` (plan.md lines 123–126). Every external surface (Keystore, Argon2/HKDF, Worker HTTPS, auth-provider capability) gated behind one. |
| CHK009 | ✓ | Port methods named by domain need: `derive(stableId, purpose)`, `create(identity, passphrase)`, `uploadBlob/fetchBlob/deleteBlob`, `check()`. No `getFromSharedPreferences`, `httpPost`, `argon2idHash`, `firestoreDocument`. Adapter convenience names absent. |
| CHK010 | ✓ | Fakes enumerated in plan.md lines 148–151: `FakeKeyRegistry`, `FakeRootKeyManager`, `FakeRecoveryKeyBackup` (in-memory `Map<StableId, RecoveryKeyBackupBlob>`), `FakeAuthAvailability`. All under `core/keys/src/commonTest/kotlin/family/keys/fakes/`. |
| CHK011 | ✓ | Real adapters in `androidMain` (`AndroidKeystoreRegistry`, `Argon2RootKeyManager`, plan.md lines 156–158) + `app/` (`WorkerRecoveryKeyBackup`, `AuthAvailabilityAndroidImpl`, lines 161, 164). |
| CHK012 | ✓ | `KeysModule.kt` in `app/src/main/kotlin/com/launcher/di/` (plan.md line 173) — DI wiring binds fake/real per build. Test Strategy table (plan.md lines 247–263) shows JVM unit tests use fakes, connectedAndroidTest uses real adapters. |

## Source-set placement

| CHK | Verdict | Evidence |
|-----|---------|----------|
| CHK013 | ✓ | Plan.md Project Structure block explicitly assigns every file to `commonMain` / `commonTest` / `androidMain` / `androidTest` / `app/`. Justification stated in §«Structure Decision» (lines 207–210): ports stay in `commonMain` per rule 1 even though only Android adapters ship. |
| CHK014 | ✓ | All 12 domain files default to `commonMain`. Deviations explicit: `AndroidKeystoreRegistry` / `Argon2RootKeyManager` / `DeviceKeyNamespaceProvider` in `androidMain` because they need Android Keystore + libsodium-android binding; Compose UI screens in `app/` because Compose-Android-specific. |

## Existing-code regressions

| CHK | Verdict | Evidence |
|-----|---------|----------|
| CHK015 | ✓ | Plan.md Constraints (lines 36–42) explicitly forbid Firebase / Google / Cloudflare imports in `core/keys/commonMain`, enforced via Konsist fitness function (Test Strategy table line 252). libsodium imports forbidden outside `core/crypto` (line 37). Migration from spec 018 `ConfigCipher2` preserves byte-equal ciphertext without touching commonMain isolation (FR-018, plan.md line 11). |
| CHK016 | ✓ | No new `expect`/`actual` declarations in plan. Argon2/HKDF/AEAD primitives go through existing F-CRYPTO `KeyDerivation` / `AeadCipher` ports (TASK-51). Android-specific code (`AndroidKeystoreRegistry`) is a regular `class` in `androidMain` implementing a `commonMain` interface — no `expect fun` introduced. |

---

## Plan-specific deep checks (asked in re-run)

| Check | Verdict | Evidence |
|-------|---------|----------|
| All ports in `core/keys/src/commonMain/` | ✓ | plan.md lines 121–134: every port file under `core/keys/src/commonMain/kotlin/family/keys/api/`. |
| All adapters in `app/` or `core/keys/src/androidMain/` | ✓ | plan.md lines 155–164: `AndroidKeystoreRegistry`, `Argon2RootKeyManager`, `DeviceKeyNamespaceProvider` in `androidMain`; `WorkerRecoveryKeyBackup`, `DataStorePassphraseAttemptCounter`, `DataStoreSchemaVersionMemory`, `AuthAvailabilityAndroidImpl` in `app/`. |
| No Firebase / Cloudflare / OkHttp imports in `commonMain` paths | ✓ | plan.md Constraints lines 37–38 enforce this; Konsist fitness rule (line 252) verifies at build time. |
| `RecoveryKeyBackupBlob` fields domain-pure | ✓ | data-model.md §6 + contract §3: 7 fields (`schemaVersion`, `stableId`, `salt`, `kdfParams`, `ciphertext`, `nonce`, `createdAt`) — all primitives + domain `KdfParams`. Contract §4 enumerates forbidden fields (`googleSub`, `firebaseUid`, `providerKind`, `providerId`, `googleDriveFileId`, `appleId`, `phoneNumber`, `email`, `displayName`, `recipientId`, `groupId`). Fitness test `RecoveryKeyBackupBlobProviderAgnosticTest` enforces. |
| `AvailabilityReason` enum domain-level only | ✓ | data-model.md §8: three cases — `NoSupportedProvider`, `KeystoreLocked`, `NetworkUnreachable`. Explicit invariant (line 227): "Forbidden domain values: any name mentioning Google, GMS, Huawei, HMS, Apple, Firebase, Sub, OAuth." |
| Worker contract doesn't push Worker concerns back into domain ports | ✓ | `worker-api-v1.md` lives **outside** the domain — concerns (`Authorization: Bearer`, HTTP 401/403/409/429/507, `Idempotency-Key`) terminate at `WorkerRecoveryKeyBackup` adapter. Domain `RecoveryKeyBackup` port takes/returns `RecoveryKeyBackupBlob` + `BackupError` (sealed) only — no `Bearer`, no HTTP status, no `Idempotency-Key` parameter in port signature. HTTP errors translated to `BackupError.{NetworkUnavailable, AuthExpired, ServerQuotaExceeded, Conflict, UnsupportedSchema}` (data-model.md §10). |

---

## Summary

**Result**: 16/16 ✓, no fails.

Plan-level decomposition preserved the spec-level isolation guarantees. Notable design choices that reinforce isolation:

- **Double-wrap** of libsodium: `core/keys/` doesn't import libsodium directly — uses `core/crypto`'s `KeyDerivation` / `AeadCipher` ports (TASK-51), which is the only module wrapping libsodium-kmp.
- **Worker is itself an adapter**: HTTP / R2 / firebase-admin concerns terminate in TS code (`workers/backup/`), not in Kotlin domain. The `worker-api-v1.md` contract is a *boundary spec*, not part of the domain.
- **`BackupError` codifies HTTP**: instead of letting HTTP 401/403/etc. leak through, the adapter translates to a domain-owned sealed hierarchy with 5 cases. This is the canonical ACL pattern (rule 2).
- **Konsist fitness function**: `core/keys/src/commonMain/` token grep for `Google/Firebase/OAuth/Apple/Phone/Email/Sub/IdToken` (Test Strategy line 252) — automated regression catch per CLAUDE.md rule 7.

No remediation required.
