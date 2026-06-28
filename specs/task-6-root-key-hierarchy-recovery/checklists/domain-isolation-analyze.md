# Checklist: domain-isolation — analyze re-run after Worker addition

Applied: 2026-06-28 (re-run during `/speckit.analyze`, post round-3 Worker addition)
Spec: [specs/task-6-root-key-hierarchy-recovery/spec.md](../spec.md)
Reference: [.claude/skills/checklist-domain-isolation/SKILL.md](../../../.claude/skills/checklist-domain-isolation/SKILL.md)
Previous run: [domain-isolation.md](./domain-isolation.md) — 16/16 ✓

**Why re-run.** Round-3 changes (2026-06-28) added a substantial new artifact set: TS Cloudflare Workers `workers/backup/` + `workers/identity/` (T653-T670), the `worker-api-v1.md` HTTP contract, Android adapter `WorkerRecoveryKeyBackup` (T635), Android adapter `InitClaimClient` (T668), `BackupError` sealed class with HTTP-origin cases (data-model §10). Domain isolation is the **centerpiece** F-5 check ("provider-agnostic by FR-001"), so any Worker leakage into `core/keys/commonMain/` would be a regression.

---

## Results

| ID | Item | Status | Evidence / Why |
|----|------|--------|----------------|
| CHK001 | No vendor SDK type in domain signatures | [x] | `core/keys/src/commonMain/api/` (data-model §1-10, plan §Project Structure) holds only: `AuthIdentity` (re-exported domain type from F-4), `StableId` (String alias), `RootKey` / `DerivedKey` (opaque value classes), `KdfParams`, `RecoveryKeyBackupBlob`, sealed `RootKeyError` / `BackupError`, `AuthAvailabilityStatus` / `AvailabilityReason`. Zero Cloudflare / Worker / Firebase / Google / OkHttp / HTTP / Bearer / JWT types. Konsist rule T631 greps for `Google\|Firebase\|OAuth\|Apple\|Phone\|Email\|Sub\|IdToken\|Cloudflare\|Worker` (forbidden token list **expanded** to include `Cloudflare\|Worker` post round-3 — tasks.md T631). SC-007 wording in spec.md L621 still names the original token list — minor follow-up noted in §Findings below. |
| CHK002 | One wrapper module per SDK | [x] | OkHttp transport isolated in `WorkerRecoveryKeyBackup` (T635, `app/src/main/kotlin/com/launcher/data/recovery/`). Firebase Admin SDK isolated in `workers/identity/src/index.ts` (T663) — *not* in Kotlin. JWT verification reused via shared lib `workers/_shared/auth-jwt/` (one wrapper, multiple Worker consumers). Keystore in `AndroidKeystoreRegistry` (T632). Argon2 in `Argon2RootKeyManager` (T633). Each adapter implements a single port. |
| CHK003 | Vendor-disappears test documented | [x] | SRV-RECOVERY-001 in `docs/dev/server-roadmap.md` documents the Worker → own-server exit ramp. "If Cloudflare disappears tomorrow": replace `WorkerRecoveryKeyBackup` (single Android file, T635) + redeploy backend (`workers/backup/` → own Go service). Domain `RecoveryKeyBackup` port unchanged. Worker-side TS swap is parallel: `workers/identity/` becomes one Go microservice, `workers/backup/` becomes another (architectural rule in plan §«Architectural rule», memory `project_workers_microservice_mapping.md`). |
| CHK004 | No transport types in domain signatures | [x] | `RecoveryKeyBackup` port signatures (`uploadBlob` / `fetchBlob` / `deleteBlob`) — see T614 — use only `StableId` + `RecoveryKeyBackupBlob` + `Outcome<BackupError>`. No `Request` / `Response` / `Headers` / `Bearer` / `okhttp3.*` types. OkHttp lives only in `WorkerRecoveryKeyBackup` (T635, app/ adapter). HTTP status codes are mapped at the adapter boundary into the domain `BackupError` sealed class. |
| CHK005 | Wire-format type is domain-owned with schemaVersion | [x] | `RecoveryKeyBackupBlob` (data-model §6) in `core/keys/src/commonMain/`: `schemaVersion=1` const declared (contract §1, `SCHEMA_VERSION_V1`). Roundtrip (T622) + BackwardCompat (T623) + ForwardCompat / UnsupportedSchema (T625) + ProviderAgnostic (T624) tests. Worker treats body as opaque JSON for routing — does **not** define its own DTO. |
| CHK006 | No `android.*` / `androidx.*` / `Intent` / `Uri` / `Context` in commonMain | [x] | Plan §Project Structure puts all platform-using code under `androidMain/` or `app/src/main/`. `commonMain/` lists only KMP-pure types (data-model §1-10). |
| CHK007 | Platform-derived data carries domain projection | [x] | Passphrase = `CharArray`. `StableId` = String alias for UUID. JWT never enters domain (lives in `SessionStore` from F-4, opaque to F-5). Worker URL = `BuildConfig.RECOVERY_BACKUP_WORKER_URL` consumed only inside the adapter (T635, T639). |
| CHK008 | Every external surface exposed through a port | [x] | Four ports unchanged: `KeyRegistry`, `RootKeyManager`, `RecoveryKeyBackup`, `AuthAvailability`. The new Worker HTTP surface is consumed via `RecoveryKeyBackup` and (new, post round-3) `InitClaimClient`. `InitClaimClient` (T668) is **adapter-layer** — it's an F-4 implementation detail (called by `GoogleSignInAuthAdapter` to set the `stableId` claim) and does **not** introduce a new domain port. F-5 does not see it. |
| CHK009 | Port shape driven by domain need | [x] | All port methods are domain verbs. `RecoveryKeyBackup` methods take `StableId` (not "request") and return `Outcome<…, BackupError>`. No `httpPost` / `getEndpoint` shapes. |
| CHK010 | Each port has a fake adapter | [x] | T616-T619: `FakeKeyRegistry`, `FakeRootKeyManager`, `FakeRecoveryKeyBackup` (in-memory map shared across two test instances for cross-device SC-001), `FakeAuthAvailability`. US-6 + SC-009 exercise the fakes via `FakeAuthAdapter` swap. |
| CHK011 | Each port has a real adapter | [x] | `KeyRegistry` → `AndroidKeystoreRegistry` (T632). `RootKeyManager` → `Argon2RootKeyManager` (T633). `RecoveryKeyBackup` → `WorkerRecoveryKeyBackup` (T635, replaces previous `GoogleDriveAppDataRecoveryKeyBackup` per round-2 Worker pivot). `AuthAvailability` → `AuthAvailabilityAndroidImpl` (T638). |
| CHK012 | DI wiring picks fake/real per build | [x] | T648 `KeysModule.kt`: single `WorkerRecoveryKeyBackup` for real builds (Selector removed in round-2 simplification); `FakeRecoveryKeyBackup` for debug/test flavor. No NoOp adapter (removed round-2 — adheres to rule 4 anti-bloat). |
| CHK013 | Source-set placement clearly assigned per file | [x] | Plan §Project Structure enumerates 12 files in `core/keys/src/commonMain/api+impl`, 3 in `core/keys/src/androidMain/`, 4 in `app/src/main/kotlin/com/launcher/data/recovery/`, 1 in `app/src/main/kotlin/com/launcher/data/identity/` (`InitClaimClient`), 4 in `app/src/main/kotlin/com/launcher/ui/recovery/`, 10 in `workers/backup/` + `workers/identity/`. Worker TS code is **entirely** under `workers/` — no Kotlin module touches it. |
| CHK014 | Default placement is commonMain | [x] | Ports + value objects + sealed errors all in `commonMain`. Worker HTTP transport in `app/`. UI in `app/`. Adapters in `androidMain/` only where platform APIs are required (Keystore, libsodium-via-core/crypto). |
| CHK015 | No regression of vendor type into cleansed commonMain | [x] | F-4 (spec 017) cleansed `core/domain/auth/` to use `stableId` opaque UUID. F-5 + Worker addition does **not** reintroduce Google/Firebase types into commonMain. `BackupError.AuthExpired` is named generically (see CHK-NEW-1 below — investigated and judged correct). Konsist rule T631 (expanded token list including `Cloudflare\|Worker`) guards regression. |
| CHK016 | No new expect/actual where pure Kotlin suffices | [x] | Ports are pure Kotlin interfaces. Crypto via `core/crypto` (TASK-51). No new expect/actual added for Worker integration — HTTP transport lives in `app/` Android-only, no need to KMP-abstract. |

---

## Special investigation: is `BackupError.AuthExpired` an HTTP/JWT concept leaking into domain?

The user-facing reviewer asked specifically about this. Findings:

- **`BackupError`** (data-model §10) is a sealed class with cases: `NetworkUnavailable`, `AuthExpired`, `ServerQuotaExceeded`, `Conflict`, `UnsupportedSchema`. The "HTTP origin" column in the table notes the *adapter-internal* mapping source, not a coupling.
- **`AuthExpired` semantics from the domain perspective**: "the auth session that backs the backup operation is no longer valid; the UI should prompt re-sign-in." This is a *domain* concept — re-authentication is meaningful regardless of provider (Firebase JWT today, future OAuth2 / Email-Password / Phone). The name does **not** mention JWT, Bearer, Firebase, or token.
- **What would be a violation**: a case named `JwtExpired`, `BearerTokenExpired`, `FirebaseTokenExpired`, or `IdTokenInvalid`. None of these appear.
- **Mapping responsibility**: `WorkerRecoveryKeyBackup` (T635) maps HTTP `401 INVALID_TOKEN` → `BackupError.AuthExpired`. The mapping is a one-line adapter-internal `when`-branch; if we swap to email-password later, the adapter maps email-auth-expired → same domain value. Domain consumers (`RecoveryViewModel`) just see "re-sign-in needed."
- **Verdict**: `AuthExpired` is a correctly-placed *domain* concept. CHK001 passes for this name. No change requested.

If we wanted to be even more conservative we could rename to `IdentityExpired` or `SessionExpired` — but "Auth" is the domain word for "the authentication relationship," not a JWT-specific term. Leaving as-is.

## Special investigation: Worker TS code confinement

Verified via plan.md §Project Structure file listing:

- `workers/backup/` — all TS files (`src/index.ts`, `src/ratelimit.ts`, `src/idempotency.ts`, `__tests__/*.test.ts`, `README.md`, `wrangler.toml`, `package.json`, `tsconfig.json`). Tasks T653-T661.
- `workers/identity/` — same structure, separate `wrangler.toml` (port 8788 vs 8787). Tasks T662-T665.
- **Zero TS files** under `core/keys/`, `core/auth/`, `core/crypto/`, or `app/`. Architectural rule in plan §«Architectural rule» (one Worker = one future Go microservice) reinforces this.

## Special investigation: adapter locations

| Adapter | Location | Verdict |
|---|---|---|
| `WorkerRecoveryKeyBackup` (T635) | `app/src/main/kotlin/com/launcher/data/recovery/WorkerRecoveryKeyBackup.kt` | ✓ Correct app-layer adapter location. |
| `InitClaimClient` (T668) | `app/src/main/kotlin/com/launcher/data/identity/InitClaimClient.kt` | ✓ Correct app-layer (F-4 identity adapter helper). Not a domain port. |
| `AndroidKeystoreRegistry` (T632) | `core/keys/src/androidMain/kotlin/family/keys/impl/` | ✓ Correct androidMain placement. |
| `Argon2RootKeyManager` (T633) | `core/keys/src/androidMain/kotlin/family/keys/impl/` | ✓ Same. |

---

## Summary

**16 / 16 PASS.** Centerpiece check **still clean** after Worker addition. No domain drift detected. The Worker addition is a *pure adapter-side change* with respect to the F-5 domain layer.

Key confirmations post round-3:
- Konsist rule T631 forbidden-token list expanded to include `Cloudflare\|Worker` (tasks.md verified).
- `BackupError.AuthExpired` is correctly domain-named (not `JwtExpired`).
- Worker TS code 100% confined to `workers/backup/` + `workers/identity/`.
- `WorkerRecoveryKeyBackup` (T635) replaces the previous Drive adapter at the **adapter boundary** — domain port `RecoveryKeyBackup` is unchanged.
- `InitClaimClient` is an F-4 adapter helper, not a new F-5 domain port — does not widen the domain surface.

## Findings — minor follow-ups (no fails)

1. **SC-007 wording drift (informational)**: spec.md L621 still lists the original forbidden tokens `Google|Firebase|OAuth|Apple|Phone|Email|Sub|IdToken`. The Konsist rule T631 in tasks.md extends this to include `Cloudflare|Worker`. Spec.md and tasks.md should be aligned in a follow-up edit — non-blocking for analyze. Suggested wording: "spec.md SC-007 lists the canonical forbidden-token set; tasks.md T631 is the executable contract."
2. **`docs/dev/key-hierarchy.md` (T674) — not yet authored**: when written, it should explicitly state that "domain knows about `RecoveryKeyBackup` port only; Worker / Cloudflare / R2 are adapter-internal."

No fails. No restructuring required.
