# Checklist: backend-substitution — analyze re-run — major backend choice change

Re-applied to `specs/task-6-root-key-hierarchy-recovery/spec.md` on 2026-06-28 after backend pivot.

**Previous pass (clarify, 2026-06-28 first half):** 15 PASS / 1 N/A / 0 FAIL against the **Google Drive App Data** backend with `NoOpRecoveryKeyBackup` fallback.

**This re-run:** backend changed to **two Cloudflare Workers** — `workers/backup/` (R2 object storage for the encrypted blob) + `workers/identity/` (Firebase Admin SDK `setCustomUserClaims` to mint `stableId` claim into the JWT envelope). `NoOpRecoveryKeyBackup` and `RecoveryKeyBackupSelector` were **deleted** (round-2 pushback): a single `WorkerRecoveryKeyBackup` adapter is universal, and "can this device recover?" reduces to "does any `AuthProvider` adapter return an `AuthIdentity`?" — already owned by F-4 / `AuthAvailability`.

Anchored in CLAUDE.md rules 1, 2, 5, 8 + constitution Article XIV §7 (server-side data minimization, added 2026-06-28).

## Backend-touching surfaces (revised)

1. `RecoveryKeyBackup` port — blob upload/fetch/delete via `workers/backup/` (R2 storage, JWT-bearer auth, opaque-`stableId` paths). Single adapter `WorkerRecoveryKeyBackup`.
2. `workers/identity/` (NEW surface, separate Worker) — one endpoint `POST /init-claim` invoked by F-4 `GoogleSignInAuthAdapter` adapter on first sign-in. Client wrapper `InitClaimClient.kt` (Android-side, in F-4 boundary). Does NOT touch `core/keys/`.
3. `AuthProvider` / `AuthAvailability` ports — provide `AuthIdentity.stableId` (provider-agnostic UUID). Already abstracted in F-4 (spec 017).
4. Local-only: Android Keystore (via `SecureKeystore` from core/crypto) + libsodium primitives. Not "backend" in the substitution sense.

## Gate review

| ID | Gate | Verdict | Evidence / Rationale |
|----|------|---------|----------------------|
| CHK001 | No provider type in domain signatures | PASS | `RecoveryKeyBackup` port surface (`uploadBlob`, `fetchBlob`, `deleteBlob`) uses domain types only (`RecoveryKeyBackupBlob`, `StableId`, `BackupError`). Zero Cloudflare Worker types, zero `Request`/`Response`/`R2Bucket`/JWT-library symbols leak across the port. SC-007 grep fitness function (Konsist) enforces `Google\|Firebase\|OAuth\|Cloudflare\|Worker\|R2\|JWT` token ban in `core/keys/src/commonMain/`. |
| CHK002 | One wrapper adapter per provider; domain references only the port | PASS | Single `WorkerRecoveryKeyBackup` adapter in `app/src/main/kotlin/com/launcher/data/recovery/`. `NoOpRecoveryKeyBackup` and `RecoveryKeyBackupSelector` removed — DI binds the Worker adapter unconditionally; recovery availability is gated upstream by `AuthAvailability` (F-4 concern). Separate `InitClaimClient.kt` adapter for `workers/identity/` lives in `app/src/main/kotlin/com/launcher/data/identity/` (F-4 boundary, not F-5). |
| CHK003 | "Provider disappears" cost-of-swap bounded to one adapter | PASS | If Cloudflare disappears tomorrow: rewrite (a) `WorkerRecoveryKeyBackup.kt` → `HttpRecoveryBackupStorage.kt` against own-server REST, (b) `InitClaimClient.kt` → point at own-server identity endpoint, (c) DI bindings in `KeysModule.kt` / F-4 auth module. **Bounded to ~3 Android files.** The TS Worker code (`workers/backup/` + `workers/identity/`) is **infrastructure layer** — for own-server migration it is replaced wholesale by Go microservices, not "rewritten". The wire-format `RecoveryKeyBackupBlob` (schemaVersion=1 JSON) is preserved byte-equal across the swap. |
| CHK004 | Persisted wire format is domain-owned, not provider-shaped | PASS | `RecoveryKeyBackupBlob` (`contracts/recovery-key-backup-v1.md`) is plain JSON: `schemaVersion`, `stableId`, `salt`, `kdfParams`, `ciphertext`, `nonce`, `createdAt`. Zero R2 metadata, zero Worker headers, zero Cloudflare-specific fields leak into the persisted record. |
| CHK005 | `schemaVersion` from first commit | PASS | FR-006 mandates `schemaVersion: 1` field. `contracts/recovery-key-backup-v1.md` §SCHEMA_VERSION_V1. |
| CHK006 | Roundtrip test exists | PASS | `RecoveryKeyBackupBlobRoundtripTest` + `BackwardCompatTest` + `ProviderAgnosticTest` in `core/keys/commonTest/`. Worker-side `r2-roundtrip.test.ts` covers HTTP-roundtrip. |
| CHK007 | Domain PK is project-owned | PASS | `stableId` is a project-owned UUID v4 (F-4 / spec 017). Never Firebase UID, never Google `sub`. |
| CHK008 | Provider IDs as adapter-level credentials | PASS | Firebase UID lives inside F-4 auth adapter; `workers/identity/` performs the `firebase UID → stableId` mapping server-side and binds `stableId` into the JWT custom-claim envelope. `core/keys/` only ever sees the opaque `stableId`. |
| CHK009 | Provider UID used as domain ID flagged as one-way door | N/A | Domain ID is project-owned UUID, not provider UID. |
| CHK010 | Domain talks in domain verbs | PASS | Port: `uploadBlob(blob)` / `fetchBlob(stableId)` / `deleteBlob(stableId)`. No HTTP verbs, no `POST /backup`, no R2 `put()` semantics in domain. |
| CHK011 | No transport/security-rule logic leaks into caller | PASS | JWT minting, `Idempotency-Key` generation, exponential-backoff retry, 401/403/429/507 error mapping all live inside `WorkerRecoveryKeyBackup`. Domain receives `Outcome<…, BackupError>` with sealed reasons (`NetworkUnavailable`/`AuthExpired`/`ServerQuotaExceeded`/`Conflict`). |
| CHK012 | server-roadmap entry exists | PASS | `docs/dev/server-roadmap.md` SRV-RECOVERY-001 **updated 2026-06-28** to reflect Worker-based MVP. Documents: (a) `HttpRecoveryBackupStorage` adapter as direct exit ramp; (b) wire-format preserved; (c) persistent atomic counter destination; (d) seam location; (e) trigger list (KV/R2 quota, audit log requirement, GDPR, production-readiness). Note: the entry does not yet **explicitly** mention `workers/identity/` as a separate microservice to migrate — see "Concerns" §1 below. |
| CHK013 | Inline `TODO(server-roadmap)` markers at point of use | PASS | Inline TODOs in `worker-api-v1.md` §3 (persistent idempotency), §5 (persistent rate-limit), §9 (R2 → S3-compatible). Code-level TODOs to be planted by tasks: T658, T667, T668 — in adapter constructors and DI binding sites. |
| CHK014 | Does not over-engineer exempt platform integrations | PASS | Android Keystore wrapped as `AndroidKeystoreRegistry` (FR-008) — one adapter, no cross-provider abstraction. Autofill via standard Compose `ContentType.NewPassword/Password` hints — no router. |
| CHK015 | No needless cross-provider abstraction for exempt integration | PASS | No "universal keystore", no "universal autofill". Single Android adapters. |
| CHK016 | Cost-of-swap paragraph present | PASS (revised, below) | |

## Cost-of-swap paragraph (revised for two-Worker architecture)

> If Cloudflare were replaced by our own server tomorrow, the work would be:
>
> **Android-side (≤ 3 files):**
> 1. Rewrite `app/src/main/kotlin/com/launcher/data/recovery/WorkerRecoveryKeyBackup.kt` → `HttpRecoveryBackupStorage.kt` against own-server REST (same `uploadBlob` / `fetchBlob` / `deleteBlob` port contract).
> 2. Rewrite `app/src/main/kotlin/com/launcher/data/identity/InitClaimClient.kt` → point at own-server identity endpoint (still wraps `firebase UID → stableId` mapping behind the same one-call surface).
> 3. Switch DI bindings in `KeysModule.kt` (and the F-4 auth module that wires `InitClaimClient`) to the new adapters.
>
> **Server-side (replace, do not migrate):**
> - `workers/backup/` Cloudflare TS Worker → **backup-service** Go microservice (own-server). Same HTTP contract (`POST /backup` + `GET /backup/{stableId}` + `DELETE /backup/{stableId}`), same wire format, same JWT semantics. Storage swaps from R2 → S3-compatible OR PostgreSQL `bytea` column.
> - `workers/identity/` Cloudflare TS Worker → **identity-service** Go microservice (own-server). Same `POST /init-claim` contract. Stops using Firebase Admin SDK; instead writes the `firebase_uid → stableId` mapping into a dedicated table and signs its own JWTs (with `stableId` claim) or proxies Firebase JWTs through an opaque session.
>
> **Wire format:** `RecoveryKeyBackupBlob` JSON (schemaVersion=1) preserved byte-equal across the swap. **Zero blob migration** — existing blobs re-uploaded on next user touch via dual-write window per SRV-RECOVERY-001.
>
> **Estimated bounded cost on the Android side: 3 files.** The TS Worker code is **not part of the Android app's cost** — it is infrastructure that gets replaced wholesale by Go services. `core/keys/commonMain/` (~12 files, all ports + value objects) and the 3 Compose UI screens (`RecoveryPassphraseSetupScreen` / `EntryScreen` / `FallbackScreen`) are unchanged.

## Two-Workers-vs-one analysis (new question raised in this re-run)

**Does the microservice split (`workers/backup/` + `workers/identity/`) make backend substitution easier OR harder than a single Worker?**

**Easier.** Reasons:

1. **Clean migration boundaries.** Each Worker maps 1:1 to a future Go microservice (memory `project_workers_microservice_mapping.md`). When SRV-RECOVERY-001 triggers, we replace `workers/backup/` independently of `workers/identity/`. A monolithic Worker would force atomic replacement of both surfaces, or messy refactor to extract them.
2. **Independent scaling triggers.** R2 quota exhaustion (backup) and Firebase Admin SDK quota (identity) are independent failure modes. Splitting lets us migrate the surface under pressure first while leaving the other in MVP.
3. **No cross-feature contamination.** A monolithic Worker would tempt putting identity-link logic inside the backup endpoint (e.g. "mint claim on the fly during POST /backup if missing"). The split forces the auth-bootstrap path to be a separate concern, owned by F-4, not F-5. This matches CLAUDE.md rule 1 (domain isolation) at the infrastructure layer too.
4. **Separate deploy + secret rotation.** `FIREBASE_SERVICE_ACCOUNT_JSON` (privileged Admin SDK credential) lives only in `workers/identity/`. `workers/backup/` has zero Firebase Admin credentials → blast radius of a Worker compromise is bounded.

**Tradeoff:** two `wrangler.toml` files, two deploys, two URL env vars in `BuildConfig`. Acceptable overhead — both Workers are tiny (<10 files each) and the deploy ritual is already established by `workers/push/`.

## Future Go microservice mapping (seam cleanliness)

| Cloudflare MVP | Future Go microservice | Seam quality |
|---|---|---|
| `workers/backup/` | `backup-service` | **Clean.** HTTP contract `worker-api-v1.md` is language-agnostic; Go service implements same routes + JWT verification + idempotency + rate-limit. Adapter swap on Android = one file. |
| `workers/identity/` | `identity-service` | **Clean.** `POST /init-claim` is a single endpoint with simple semantics ("mint stableId if absent, idempotent"). Go service drops `firebase-admin` for own JWT signing (or proxies, depending on auth roadmap). Adapter swap on Android = one file. |
| `workers/_shared/auth-jwt/` | own JWT library or reuse | **Acceptable.** TS implementation of Firebase JWKS-verification is replaceable by Go `golang-jwt` library — same JWT standards. |

The seams are architecturally clean: **each Worker is the future microservice in TypeScript prototype form.** The wire formats (blob JSON + JWT claims) are stable across the migration. The Android adapters need only swap their HTTP base URL constant and (potentially) re-do error mapping if status codes shift.

## Concerns

1. **SRV-RECOVERY-001 mentions `workers/backup/` but NOT `workers/identity/` explicitly.** The current entry text covers the recovery-blob storage migration; it does not name `workers/identity/` or its future `identity-service` counterpart as a separate migration target. Recommendation: add a sub-bullet to SRV-RECOVERY-001 (or open SRV-IDENTITY-001) explicitly tracking `workers/identity/` → `identity-service` migration with the same exit-ramp template (seam location: `InitClaimClient.kt`; trigger: Firebase Auth pricing change OR own-server cutover). **Non-blocking** — `workers/identity/` is mentioned in plan.md §"Architectural rule" and tasks.md Track B, but server-roadmap.md is the canonical destination ledger and should reflect both.

2. **`InitClaimClient.kt` is F-4 surface, not F-5.** Plan.md correctly places it in `app/src/main/kotlin/com/launcher/data/identity/` (T668) and notes "called once by F-4 `GoogleSignInAuthAdapter`". The F-5 backend-substitution analysis treats it as an additional touch-point that the F-5 spec inherits indirectly. No leak across the F-5 port boundary, but the cost-of-swap accounting must include it (done above — 3 files, not 2). Worth re-checking that no `InitClaimClient` symbols leak into `core/keys/commonMain/` (they don't — `InitClaimClient` is consumed only by the F-4 auth adapter, and F-5 sees the result via `AuthIdentity.stableId`).

## Summary

**16/16 PASS, 1 N/A (CHK009 unchanged), 0 FAIL. Cost-of-swap: 3 Android files.**

The Cloudflare Worker pivot **strengthens** backend-substitution readiness compared to the previous Drive App Data design:
- Wire format is now fully provider-agnostic JSON (no Drive `File` resource semantics ever existed in the previous design either, but the JWT-bearer + opaque-stableId path makes the substitution boundary even cleaner).
- The two-Worker microservice split mirrors the future Go architecture 1:1.
- `NoOpRecoveryKeyBackup` removal eliminates a misleading capability surface that would have needed re-engineering at own-server cutover.
- Constitution Article XIV §7 (server-side data minimization) is satisfied by design — opaque routing, ciphertext-only blobs, no cross-user correlation endpoints, no body logging.

**One non-blocking follow-up:** extend SRV-RECOVERY-001 (or add SRV-IDENTITY-001) to explicitly cover `workers/identity/` migration. Recommend tracking as an operational TODO in `docs/dev/project-backlog.md` rather than blocking F-5.
