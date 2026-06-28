# Checklist: domain-isolation

Applied: 2026-06-28
Spec: [specs/task-6-root-key-hierarchy-recovery/spec.md](../spec.md)
Reference: [.claude/skills/checklist-domain-isolation/SKILL.md](../../../.claude/skills/checklist-domain-isolation/SKILL.md)

Enforces CLAUDE.md rules 1 (Domain isolated from infrastructure) and 2 (Anti-Corruption Layer). This is the **centerpiece check** for F-5 per owner ask 2026-06-28 ("verify Google login isn't the only possible provider").

---

## Results

| ID | Item | Status | Evidence / Why |
|----|------|--------|----------------|
| CHK001 | No vendor SDK type in domain signatures | [x] | FR-001 explicitly bans `Google\|Firebase\|OAuth\|Apple\|Phone\|Email\|Sub\|IdToken` strings in `core/keys/src/commonMain/`. SC-007 codifies this as Konsist/Detekt fitness function. Ports use only `AuthIdentity`, `StableId`, `CharArray`, `RootKey`, `DerivedKey`, `RecoveryKeyBackupBlob`. |
| CHK002 | One wrapper module per SDK | [x] | Drive SDK isolated in `GoogleDriveAppDataRecoveryKeyBackup` (FR-010); Keystore in `AndroidKeystoreRegistry` (FR-008); Argon2 in `Argon2RootKeyManager` (FR-009). Each adapter implements a single port. |
| CHK003 | Vendor-disappears test documented | [x] | FR-012 `RecoveryKeyBackupSelector` uses capability detection (not provider-type check); replacing Drive → own-server requires changing only `GoogleDriveAppDataRecoveryKeyBackup` and the selector branch. Inline TODO at FR-007 + server-roadmap.md documents the exit ramp. |
| CHK004 | No transport types in domain signatures | [x] | `RecoveryKeyBackupBlob` is a domain data class in `core/keys/src/commonMain/`; no Retrofit/HTTP/Ktor types in port signatures. Note: kotlinx-serialization annotations on the domain blob are acceptable per project conventions (serializer lives where the blob lives; Drive REST transport is encapsulated inside `GoogleDriveAppDataRecoveryKeyBackup`). |
| CHK005 | Wire-format type is domain-owned with schemaVersion | [x] | `RecoveryKeyBackupBlob { schemaVersion: 1, stableId, salt, kdfParams, ciphertext, nonce, createdAt }` (FR-006). Roundtrip + BackwardCompat + ProviderAgnostic contract tests (FR-023). Owned by `core/keys/src/commonMain/`. |
| CHK006 | No `android.*` / `androidx.*` / `Intent` / `Uri` / `Context` in commonMain | [x] | Domain layer section explicitly scoped to `core/keys/src/commonMain/` with only KMP-pure types. Adapters are in `app/androidMain/keys/` (FR-008..FR-013). |
| CHK007 | Platform-derived data carries domain projection | [x] | Passphrase typed as `CharArray` (not Android `EditText` content), `StableId` typed as `String` UUID alias. No raw platform types in port signatures. |
| CHK008 | Every external surface exposed through a port | [x] | Four ports: `KeyRegistry` (FR-002), `RootKeyManager` (FR-003), `RecoveryKeyBackup` (FR-004), `AuthAvailability` (FR-005). Each covers one external surface (Keystore, Argon2/KDF, Drive, GMS availability). |
| CHK009 | Port shape driven by domain need | [x] | Methods are domain verbs: `derive(namespace, purpose)`, `create/recover/forget(identity, passphrase)`, `uploadBlob/fetchBlob/deleteBlob(stableId)`, `check()`. No leaked adapter-shaped methods like `getFromSharedPreferences` or `callDriveApi`. |
| CHK010 | Each port has a fake adapter | [x] | FR-022: `FakeKeyRegistry`, `FakeRootKeyManager`, `FakeRecoveryKeyBackup`, `FakeAuthAvailability` in `core/keys/src/commonTest/`. US-6 and SC-009 explicitly exercise fakes to prove provider-agnosticism (`FakeAuthAdapter` / hypothetical `FakePhoneAuthAdapter` swap with zero F-5 code changes). |
| CHK011 | Each port has a real adapter | [x] | FR-008 `AndroidKeystoreRegistry`, FR-009 `Argon2RootKeyManager`, FR-010 `GoogleDriveAppDataRecoveryKeyBackup` + FR-011 `NoOpRecoveryKeyBackup`, FR-013 `AuthAvailability` Android impl. All in `app/androidMain/keys/`. |
| CHK012 | DI wiring picks fake/real per build | [x] | FR-012 `RecoveryKeyBackupSelector` based on capability detection (`AuthAvailability.check()` + Drive accessible). Local Test Path lists fakes used in JVM unit tests + real `AndroidKeystoreRegistry` in instrumented tests. |
| CHK013 | Source-set placement clearly assigned per file | [x] | FR sections grouped by `core/keys/src/commonMain/`, `app/androidMain/keys/`, `app/androidMain/ui/recovery/`, `core/keys/src/commonTest/`. Each file path explicitly stated. |
| CHK014 | Default placement is commonMain | [x] | All ports + value-objects + errors in `commonMain`. Deviations (`androidMain`) justified by platform-specific APIs (Keystore, Drive, Compose UI, Autofill). |
| CHK015 | No regression of vendor type into cleansed commonMain | [x] | F-4 (spec 017) cleansed `core/domain/auth/`. F-5 inherits — `AuthIdentity.stableId` used as opaque UUID; no reintroduction of `Google*`/`Firebase*` types in `core/keys/`. SC-007 fitness function guards regression. |
| CHK016 | No new expect/actual where pure Kotlin suffices | [x] | Ports are pure Kotlin interfaces; no `expect class`/`expect fun` declared in spec. Crypto primitives delegated to `core/crypto` (TASK-51 libsodium consolidation) rather than expect/actual'd into F-5. |

---

## Summary

**16 / 16 PASS.** Centerpiece check confirmed: F-5 domain is provider-agnostic. The Google adapter is one of N possible adapters; replacing it is an additive change (new `RecoveryKeyBackup` impl + new selector branch), not a domain rewrite.

Key provider-agnostic guarantees:
- FR-001 + SC-007: grep fitness function on `core/keys/src/commonMain/`.
- FR-006 + SC-008: wire-format contains only `stableId` + crypto material.
- FR-012: capability-based selector, not provider-type check.
- US-6 + SC-009: `FakePhoneAuthAdapter` swap test proves portability.

No fails.
