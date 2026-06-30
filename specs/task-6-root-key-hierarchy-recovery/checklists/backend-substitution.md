# Checklist: backend-substitution

Applied to `specs/task-6-root-key-hierarchy-recovery/spec.md` on 2026-06-28.

Anchored in CLAUDE.md rules 1 (domain isolation), 2 (ACL per external SDK), 5 (wire-format versioning), 8 (server-roadmap tracking), and constitution §7 (Backend Substitution Readiness).

## Backend-touching surfaces identified

1. `RecoveryKeyBackup` port — blob upload/fetch/delete (Google Drive App Data on GMS, NoOp on non-GMS).
2. `AuthProvider` port (consumed from F-4 / spec 017) — provides `AuthIdentity` and `currentUser` flow.
3. `KeyRegistry` / `RootKeyManager` — local-only via Android Keystore + libsodium (not backend, but identity-coupled).

## Gate review

| ID | Gate | Verdict | Evidence / Rationale |
|----|------|---------|----------------------|
| CHK001 | No provider type in domain signatures | PASS | FR-001 requires zero `Google\|Firebase\|OAuth\|Apple\|Phone\|Email\|Sub\|IdToken` strings in `core/keys/src/commonMain/`. SC-007 enforces via Konsist/Detekt fitness function. FR-006 `RecoveryKeyBackupBlob` carries only `stableId`, `salt`, `kdfParams`, `ciphertext`, `nonce`, `createdAt`. |
| CHK002 | One wrapper adapter per provider; domain references only the port | PASS | `RecoveryKeyBackup` port (FR-004) implemented by `GoogleDriveAppDataRecoveryKeyBackup` (FR-010, GMS) and `NoOpRecoveryKeyBackup` (FR-011, fallback). DI selector by capability detection, not provider type (FR-012). |
| CHK003 | "Provider disappears" cost-of-swap answer is bounded to one adapter | PASS | Replacing Google Drive App Data with own-server: rewrite `GoogleDriveAppDataRecoveryKeyBackup.kt` + adjust `RecoveryKeyBackupSelector` DI binding. Wire-format `RecoveryKeyBackupBlob` is provider-agnostic so re-upload to new backend is a transport switch, not a format migration. See cost-of-swap paragraph below. |
| CHK004 | Persisted wire format is domain-owned, not provider-shaped | PASS | `RecoveryKeyBackupBlob` (FR-006) is plain JSON via kotlinx-serialization; no Drive `File` resource, no Firestore `Timestamp` / `FieldValue` semantics leak into the domain model. |
| CHK005 | Wire format carries explicit `schemaVersion` from first commit | PASS | FR-006 mandates `schemaVersion: 1` field. |
| CHK006 | Roundtrip test exists in CI | PASS | FR-023 enumerates `RecoveryKeyBackupBlobRoundtripTest` + `BackwardCompatTest` + `ProviderAgnosticTest`. |
| CHK007 | Domain primary key is project-owned, not provider-issued | PASS | `AuthIdentity.stableId` is a project-owned UUID (inherited from F-4 / spec 017), not Firebase UID / Google `sub`. F-5 uses only `stableId` (FR-002, key-entities section). |
| CHK008 | Provider-issued IDs stored as credentials inside auth adapter | PASS | F-4 (spec 017) already handles this. F-5 receives only `stableId`; Google `sub` never reaches `core/keys/`. |
| CHK009 | If provider UID used as domain ID, called out as one-way door | N/A | Not applicable — domain ID is a UUID, not a provider UID. |
| CHK010 | Domain talks in domain verbs, not provider verbs | PASS | Port surface: `uploadBlob(blob)`, `fetchBlob(stableId)`, `deleteBlob(stableId)`. No `drive.files.list`, no Firestore `collection().document()` shapes in domain. |
| CHK011 | No security-rules-shaped or transport-shaped logic in calling code | PASS | OAuth scope, 401/403 revoke handling, idempotent re-upload all live in `GoogleDriveAppDataRecoveryKeyBackup` adapter (FR-010). Domain sees `Outcome<…, BackupError>` only. |
| CHK012 | Server-roadmap entry exists for this client-side workaround | PASS | `docs/dev/server-roadmap.md` already carries SRV-RECOVERY-001 (own-server recovery key vault), SRV-CRYPTO-006 (server-side rate-limit on recovery attempts), SRV-CRYPTO-PARAMS-REVIEW (Argon2id parameter cadence), SRV-STORAGE-001 (EnvelopeStorage own-server). Spec links to `docs/dev/server-roadmap.md` in three places (Out-of-Scope, FR-007, US-4 exit-ramp). The Drive App Data adapter for F-5 is covered by SRV-RECOVERY-001 (vault replacement) — adapter naming differs (legacy Firestore wording vs spec's Drive App Data) but the migration destination is the same. Recommend a one-line update in SRV-RECOVERY-001 noting both backends are covered. |
| CHK013 | Inline `TODO(server-roadmap)` markers at the point of use | PASS | FR-007 mandates inline TODO at three sites: `RootKeyManager` (key-rotation exit ramp), `RecoveryKeyBackupBlob` (`TODO(server-roadmap): own-server replacement per docs/dev/server-roadmap.md §«F-5 recovery backup»`), `KeyRegistry.derive` (Purpose-registry future-when-N+5). FR-010 also mandates inline TODO on `GoogleDriveAppDataRecoveryKeyBackup`. US-4 mandates one on `NoOpRecoveryKeyBackup`. |
| CHK014 | Does not classify exempt platform integrations (FCM, telephony, biometrics) as substitutable backend | PASS | Spec touches Android Keystore (platform integration, correctly wrapped as `AndroidKeystoreRegistry` adapter per FR-008) and Android Autofill (platform integration, correctly used via standard `ContentType.NewPassword/Password` hints per FR-014/015). Neither is over-engineered for cross-provider swap. |
| CHK015 | No needless cross-provider abstraction for exempt platform integration | PASS | No "universal keystore abstraction" or "universal autofill router" invented. Autofill is wired via standard Compose semantic hints. |
| CHK016 | Cost-of-swap paragraph present | PASS (added below) | The paragraph below is the deliverable; it can be lifted into the spec's design section if desired. |

## Cost-of-swap paragraph

> If Google Drive App Data were replaced by our own server tomorrow, the work would be: (1) rewrite `app/androidMain/keys/GoogleDriveAppDataRecoveryKeyBackup.kt` as `HttpRecoveryKeyBackup.kt` against our REST endpoint; (2) switch the `RecoveryKeyBackupSelector` DI binding to point at it whenever the new backend is reachable; (3) run a one-time migration that re-uploads existing `RecoveryKeyBackupBlob` JSON (schemaVersion=1, unchanged wire format) from clients to the new backend on next sign-in. Estimated bounded cost: **2 files in `app/androidMain/keys/`** (adapter + DI module), zero changes in `core/keys/`, zero changes in UI Composables, zero `RecoveryKeyBackupBlob` schema migration. The wire format is provider-agnostic by FR-006 so the blob bytes are reusable across backends. SRV-RECOVERY-001 in `docs/dev/server-roadmap.md` is the destination ledger.

## Summary

15 PASS, 1 N/A (CHK009), 0 FAIL.

Optional follow-up (not blocking): update SRV-RECOVERY-001 in `docs/dev/server-roadmap.md` to explicitly mention that both the Firestore-based legacy vault and the Drive App Data adapter for F-5 share the same own-server destination, so future readers don't think they are independent migrations.
