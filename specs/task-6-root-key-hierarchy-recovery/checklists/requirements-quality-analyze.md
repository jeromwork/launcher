# Checklist: requirements-quality (analyze re-run)

Applied to: `specs/task-6-root-key-hierarchy-recovery/spec.md` (state after rounds 2-3 rework: Worker storage swap, Article XIV §7, FR-011/012 simplification, US-4 rewrite).
Date: 2026-06-28
Source: `.claude/skills/checklist-requirements-quality/SKILL.md`
Prior run: [`requirements-quality.md`](requirements-quality.md) — 14/16, fails on CHK001 / CHK009.

## Results

| CHK | Status | Reasoning |
|-----|--------|-----------|
| CHK001 | ✗ | Still fails. Original leaks (Composable, `Modifier.semantics`, `ContentType.NewPassword/Password`, `SavedStateHandle`, `kotlinx-serialization`, `libsodium`, `Konsist/Detekt`, `crypto_kdf_*`) untouched — T678 still open. Rounds 2-3 **added new leaks**: `OkHttp / Ktor` (FR-010), `BuildConfig.RECOVERY_BACKUP_WORKER_URL` + `app/build.gradle.kts` (Q-O), `wrangler dev` (Q-N, Assumptions, Notes), `Idempotency-Key UUID v4` header mechanics (FR-010), `XChaCha20-Poly1305 AEAD` + `HKDF-SHA256 (RFC 5869)` + `Argon2id` named primitives in FR-002/006/009, `Firebase Admin SDK setCustomUserClaims` (Q-M), `R2 / KV` storage types (Edge Cases, Article XIV note), explicit `Bearer <firebase-jwt>` / `Authorization` header shapes (FR-010, sequences), Cloudflare-as-vendor in body text (SEQ MENTOR-DETAIL blocks). Wire-format JSON schema + KDF parameter numbers remain justified by rule 5; transport / header / library / file-path mechanics are not. |
| CHK002 | ✓ | Unchanged. |
| CHK003 | ✓ | Unchanged; NOVICE-SUMMARY rewritten for Worker swap, still senior-readable. |
| CHK004 | ✓ | All mandatory sections present. |
| CHK005 | ✓ | No `[NEEDS CLARIFICATION]`; Q-M / Q-N / Q-O resolved. |
| CHK006 | ✓ | Observable assertions for every FR (grep, contract test, byte-equal, button-disabled, dialog presence, 401 → re-sign-in path). |
| CHK007 | ✓ | Quantified thresholds intact (≥8 chars, 5 attempts, ≤3s P95, ≥18sp / ≥56dp / ≥4.5:1, 32-byte salt, 24-byte nonce, 30s timeout, 3 retries, in-memory N attempts / 5min). |
| CHK008 | ✓ | SCs measurable. |
| CHK009 | ✗ | Still fails. Original tech names persist (SC-007 `Konsist / Detekt`, SC-010 `pixel_5_api_34`, SC-005 `Google Password Manager` + `ContentType.NewPassword`) — T679 still open. Rounds 2-3 did not touch SC-005/007/010 and added Worker-flavoured concrete naming into SEQ MENTOR-DETAILs and FRs but not directly into SCs. SC text itself unchanged from prior run. |
| CHK010 | ✓ | Each US has numbered Given/When/Then. |
| CHK011 | ✓ | Edge Cases expanded (Worker down, R2/KV quota, corrupted blob, 401 auth expired) — coverage improved over round 1. |
| CHK012 | ✓ | Out-of-scope list grew (passphrase change, local→cloud upgrade, server-side persistent rate-limit, own-server replacement) — explicit deferrals with target task IDs / roadmap entries. |
| CHK013 | ✓ | Assumptions updated: `workers/backup/` deployment, Firebase Admin SDK custom-claim issuance, Spark-plan limits. |
| CHK014 | ✓ | FR → US mapping intact; new FR-010 v2 maps to US-1/US-2 upload/fetch/delete flows; FR-011/012 simplification consistent with US-4. |
| CHK015 | ✓ | Error paths broadened (`BackupError.NetworkUnavailable`, `BackupError.AuthExpired`, `BackupError.ServerQuotaExceeded`, `RootKeyError.CorruptedBlob`). |
| CHK016 | ✓ | Every SC tied to an FR. |

## Summary

14/16 ✓ — same headline count as round 1, but **CHK001 severity increased**: original leaks plus a new layer of transport / vendor / library mechanics from rounds 2-3.

## Still-open tasks

- **T678** (CHK001) — still open and now larger in scope. Cleanup must cover both original UI/library leaks AND the new Worker/HTTP/JWT/library mechanics added in rounds 2-3.
- **T679** (CHK009) — still open; SC-005/007/010 text unchanged.

## New issues introduced by rounds 2-3 (CHK001 sub-list)

1. `OkHttp / Ktor` literal client choice in FR-010 — should read «HTTPS client» behavioural requirement.
2. `Bearer <firebase-jwt>` / `Authorization` / `Idempotency-Key` header shapes inside FR-010 — wire-protocol-level detail, move to `plan.md` / `contracts/recovery-backup-endpoints.md`.
3. `XChaCha20-Poly1305 AEAD` named in FR-006 blob schema comment — algorithm choice belongs in plan/contracts; the schema only needs «AEAD-encrypted root-key material» behaviourally (rule 5 justifies *fields*, not algorithm names).
4. `HKDF-SHA256 (RFC 5869)` and `libsodium crypto_kdf_hkdf_sha256_*` in FR-002 — primitive choice belongs in plan.md.
5. `Argon2id` named in FR-009/FR-006 plus iteration/memory/parallelism numbers — *numbers* are wire-format (justified), but **algorithm name** is implementation; behavioural requirement is «slow password-hashing KDF with iteration/memory/parallelism parameters preserved in blob».
6. `BuildConfig.RECOVERY_BACKUP_WORKER_URL` + `app/build.gradle.kts` (Q-O) — Android-build-system specific, plan-territory.
7. `wrangler dev` / `wrangler deploy` (Q-N, Notes) — tool name; spec should say «local dev-stub» behaviourally.
8. `Firebase Admin SDK setCustomUserClaims` (Q-M) — SDK API name; behaviourally «server-side claim issuance after first identity link».
9. `R2 / KV` storage backend names in Edge Cases + Article XIV note + Notes — Cloudflare-specific; spec should say «server storage» / «opaque-key object store» behaviourally.
10. `Cloudflare Worker` repeatedly named as the chosen vendor in body text (Overview, SEQ MENTOR-DETAILs, OEM matrix, NOVICE-SUMMARY) — vendor name leakage. Justifiable in one place (Architectural decision rationale + server-roadmap exit ramp), but currently sprinkled throughout user-facing scenario narratives.
11. `Argon2id`-as-name appears in FR-002/006/009/021, NOVICE-SUMMARY, SEQ MENTOR-DETAIL — same as #5.
12. New `BackupError.AuthExpired` / `BackupError.ServerQuotaExceeded` / `BackupError.NetworkUnavailable` enum-member names are domain types (acceptable per rule 1) but tied to specific HTTP status codes (401, 507) in prose — status codes belong in `contracts/`.

## Recommended remediation (extends original T678/T679)

- **Expand T678** to cover items 1-12 above. Move concrete library / SDK / header / status-code / build-tool names to `plan.md` (most already are) and to `contracts/recovery-backup-endpoints.md` (new file recommended in plan). Keep in spec.md only: behavioural requirement, wire-format field names + numeric parameters, error-domain enum names.
- **T679** unchanged: rewrite SC-005 / SC-007 / SC-010 as outcome statements (static-analysis rule reports 0 matches; ≤3s P95 on target reference device class; cross-device autofill prefills when same provider session is shared).
- Consider adding a one-sentence note at the top of `## Architectural decision` blocks: «vendor names appear here for traceability; implementation mechanics live in plan.md / contracts/», so the reader knows the boundary.
