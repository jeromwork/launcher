# Checklist: security (analyze re-run after Worker + Article XIV §7)

Re-applied to `specs/task-6-root-key-hierarchy-recovery/` on 2026-06-28 after substantial changes:
- Drive App Data → **Cloudflare Worker (`workers/backup/`) + R2** storage.
- Firebase JWT with custom claim `stableId` (variant (i) per Q-M).
- Worker-side in-memory rate-limit, Idempotency-Key requirement on POST.
- Constitution Article XIV §7 (Server-side data minimization) added 2026-06-28.

Reference: OWASP MASVS + Article XIV (incl. §7 a–e) + `checklist-security` SKILL.md.

## Results — MASVS core

| ID | Item | Status | Evidence |
|----|------|--------|----------|
| CHK001 | No PII in clear-text prefs / files | PASS | Only opaque `stableId` (UUID v4) + ciphertext + `recovery-attempts/{stableId}` counter. No name/phone/email anywhere; wire-format Section 4 lists forbidden fields enforced by `RecoveryKeyBackupBlobProviderAgnosticTest`. |
| CHK002 | Sensitive data in Keystore / EncryptedSharedPreferences | PASS | `AndroidKeystoreRegistry` alias `key-registry/{stableId}/{purpose}`, StrongBox API 28+ (plan.md Storage §). |
| CHK003 | Cache TTL / clear-on-uninstall | PASS | DataStore counter cleared on Fallback wipe (FR-019) and uninstall; no persistent caches outside scope. |
| CHK004 | Logs exclude PII | PASS | Worker contract §7: `no-logging.test.ts` greps `console.*` calls touching request body / token / Authorization. Android side inherits FR-011 spirit (no passphrase/key/ciphertext in logs). |
| CHK005 | HTTPS / TLS 1.2+ | PASS | OkHttp 5 → `<account>.workers.dev/backup` (HTTPS only, Cloudflare enforces). No cleartext traffic. |
| CHK006 | Cert pinning | N/A | Cloudflare edge — pinning would brick on cert rotation; documented absence is correct. |
| CHK007 | Privileged actions: permission + role | PASS | INTERNET permission justified by FR-010 Worker upload; JWT subject-ownership check (`claims.stableId == path/body stableId`) per worker-api-v1 §2. |
| CHK008 | No security-by-obscurity | PASS | Argon2id + HKDF-SHA256 + XChaCha20-Poly1305 documented openly; Worker contract public. |
| CHK009 | Exported components justified | PASS | No new Activities/Services/Receivers/Providers exported. |
| CHK010 | Explicit-package intents | PASS | Only outgoing `Settings.ACTION_*` intents. |
| CHK011 | Deep-link validation | N/A | No deep-link receivers introduced. |
| CHK012 | Intent extras size-bounded | N/A | No external intent reception. |
| CHK013 | No exported ContentProvider | PASS | None introduced. |
| CHK014 | WebView | N/A | None. |
| CHK015 | Each permission justified by FR | PASS | `INTERNET` ↔ FR-010 (Worker upload). No `drive.appdata` (Drive removed). |
| CHK016 | No "future-use" permissions | PASS | Only INTERNET (pre-existing from F-CRYPTO/F-4). |
| CHK017 | Fallback for denied permissions | PASS | `AuthAvailability.Unavailable(reason)` → local-only mode + Fallback screen (FR-016, US-4). |
| CHK018 | `docs/compliance/permissions-and-resource-budget.md` update planned | **CLOSED (was FAIL)** | plan.md Constraints + Phase 6 + Open-items §3 explicitly list INTERNET-only update; no `drive.appdata` scope. tasks.md T675 covers. |
| CHK019 | No hidden behavioural data collection | PASS | Only ciphertext + opaque routing metadata leaves device; no analytics/telemetry. |
| CHK020 | Local-first / network justified | PASS | US-4 local-only first-class; Worker upload justified by US-2 cross-device recovery. |
| CHK021 | Data-leaves-device notice + opt-in | PARTIAL (unchanged) | Setup screen text mentions cloud backup; explicit pre-upload disclosure with Privacy Policy link still recommended — not blocker (ciphertext only). |
| CHK022 | Data minimisation | PASS | recovery-key-backup-v1 §3 = 7 fields closed-set; provider-agnostic test enforces. |
| CHK023 | No debug logging in release | PASS (advisory) | `BuildConfig.DEBUG` gate recommended for any diagnostic line; no debug-only surfaces in spec. |
| CHK024 | Backup rules reviewed | **CLOSED (was FAIL)** | plan.md Project Structure: `android:allowBackup="false"` + `data_extraction_rules.xml` exclude DataStore `recovery-attempts/*` + Keystore aliases. tasks.md T676/T677. |

## Article XIV §7 — Server-side data minimization (NEW)

| Clause | Item | Status | Evidence |
|--------|------|--------|----------|
| §7(a) | Opaque identifiers reach server | PASS | `stableId` UUID v4 routes everything; Firebase UID stays client-side; mapping `stableId ↔ FirebaseUid` lives in separate Firestore `/identity-links/` (worker-api-v1 §7, recovery-blob §9). |
| §7(b) | Ciphertext + routing metadata only | PASS | Blob = ciphertext + salt + kdfParams + nonce + opaque stableId + timestamp. No plaintext PII ever transits Worker. |
| §7(c) | No cross-user correlation by access pattern | PASS | Single-user API (no `/list`, no `/find-by-*`, no `/groups`, no `/share`); R2 bucket `recovery-blobs` dedicated; no shared rooms (worker-api-v1 §7). |
| §7(d) | Access logs minimal | PASS | Path = `/backup/{stableId}` only; `no-logging.test.ts` contract test forbids logging body/token/Authorization. CF logs see `{method, /backup/UUID, status, IP, timestamp}` — opaque. |
| §7(e) | Worst-case-provider assumption | PASS | CF subpoena yields only opaque UUID + IP + timestamp; reidentification requires separate Firestore mapping (access-controlled). plan.md R-9 risk row explicitly states this. |

## New issues from Worker + JWT-claim flow

| Concern | Status | Note |
|---------|--------|------|
| JWT custom-claim attack surface (Q-M variant i) — `setCustomUserClaims` via firebase-admin in Worker | PASS | Service-account secret stored via `wrangler secret put` (worker-api-v1 §9). One-time write at first sign-in; subsequent reads server-side via JWT signature verification. Trade-off documented in plan R-5; fallback variant (ii) explicitly rejected for security. |
| JWKS cache race (daily refresh, clamped 60s–86400s) | PASS | `@familycare/auth-jwt` handles refresh atomically per its TTL policy; shared KV `JWKS_CACHE` namespace with `workers/push/` — same battle-tested pattern. No race introduced by F-5. |
| Idempotency-Key memory exhaustion (attacker spams fresh UUIDs) | ADVISORY | In-memory Map per Worker instance, TTL 24h, **per-stableId** keyed. Per-`stableId` POST rate-limit (10 / 5min, §5) caps growth to ~2880 entries/stableId/day. Cloudflare instance recycles every few hours → bounded. Acceptable MVP; TODO(server-roadmap) for persistent KV present. |
| Rate-limit bypass via stableId rotation | PASS | `stableId` is set as JWT custom claim by Worker at first sign-in via firebase-admin — attacker cannot mint a new `stableId` without re-signing in as a different Firebase user, which is itself rate-limited by Firebase Auth quotas. Per-IP rotation not viable (counter is per-stableId, but Firebase signup itself is the choke point). Brute-force economics dominated by Argon2id work-factor (plan R-8). |
| Cross-Worker security (shared Firebase project across `workers/backup/` + `workers/identity/` + `workers/push/`) | PASS | Each Worker has separate service-account secret (per `wrangler secret put`), separate R2 bucket bindings, separate route. Compromise of one Worker's secret rotates that Worker's IAM key only — does not grant access to other Workers' R2 buckets / KV namespaces. Architectural rule in plan.md (workers folder = future microservices) enforces this isolation. |
| Body logging via Worker error path | PASS | `no-logging.test.ts` source-grep contract (worker-api-v1 §7) catches `console.*` referencing body/token. Error responses use closed-set codes (worker-api-v1 §6), no echoed user input. |
| Forward-compat: v2 blob on v1 client | PASS | Wire-format §5: `schemaVersion > 1` → `BackupError.UnsupportedSchema`, no partial-parse. Closes silent-field-drop risk that could brick decryption. |

## Previously-open items

- **CHK018** (permissions-and-resource-budget.md update): **CLOSED**. Drive scope removed; only INTERNET permission to document. plan.md Constraints + Phase 6, tasks.md T675.
- **CHK024** (`android:allowBackup` posture): **CLOSED in plan**. `android:allowBackup="false"` + `data_extraction_rules.xml` listed in plan.md Project Structure. tasks.md T676/T677 own implementation. Verification → manual gate after T677 lands.
- **CHK021** (pre-upload Privacy Policy disclosure): **STILL ADVISORY** — same posture as initial pass; not regressed, not blocking.

## Summary

- **24/24 MASVS items**: 19 PASS, 4 N/A, 1 PARTIAL (CHK021 advisory unchanged).
- **5/5 Article XIV §7 items** (NEW): all PASS.
- **CHK018 + CHK024**: both closed in plan (were FAIL in initial pass).
- **No new blockers** introduced by Worker + JWT-claim architecture.
- **Advisories carried forward**: CHK021 pre-upload disclosure copy in FR-014; CHK023 `BuildConfig.DEBUG` log-level gating in plan.
