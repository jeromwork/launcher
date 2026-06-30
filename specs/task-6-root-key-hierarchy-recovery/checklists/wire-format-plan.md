# Checklist: wire-format (plan-level re-run)

**Scope**: contracts/ files for F-5 (`recovery-key-backup-v1.md` + `worker-api-v1.md`), cross-referenced against `plan.md`. Verifies that prior open items from the spec-level pass (CHK003 / CHK005 / CHK008 / CHK018) are now closed by the concrete contract artifacts.

**Wire formats in scope**:
1. `RecoveryKeyBackupBlob` JSON envelope (see `contracts/recovery-key-backup-v1.md`).
2. HTTP API between Android client and `workers/backup/` Worker (see `contracts/worker-api-v1.md`).

---

## Schema version

| ID | Status | Evidence |
|---|---|---|
| CHK001 | [x] | `recovery-key-backup-v1.md` §2 — `"schemaVersion": 1` first field in canonical example; §3 marks Required=yes. Worker API treats every `POST /backup` body as carrying this field (`worker-api-v1.md` §4.1). |
| CHK002 | [x] | `worker-api-v1.md` §4.1 — "Worker parses `schemaVersion` first; rejects unsupported versions before any other field". `recovery-key-backup-v1.md` §5 — "The client reads `schemaVersion` first" with full read-strategy table. |
| CHK003 | [x] **CLOSED** (was open spec-level) | `recovery-key-backup-v1.md` §1 — Kotlin declares `public const val SCHEMA_VERSION_V1: Int = 1` and `MAX_SUPPORTED_SCHEMA_VERSION` in `core/keys/src/commonMain/`. Single source of truth at code level. |

## Backward compatibility

| ID | Status | Evidence |
|---|---|---|
| CHK004 | [x] | `recovery-key-backup-v1.md` §5 ("Required-field changes / removals") + `worker-api-v1.md` §8 — both pin "one major release" backward-compat window per CLAUDE.md rule 5. |
| CHK005 | [x] **CLOSED** (was open spec-level) | `recovery-key-backup-v1.md` §5 "Additive fields (no schemaVersion bump)" — explicit policy: new optional fields allowed when a sane default exists and old readers can ignore them; example given. |
| CHK006 | [x] | `recovery-key-backup-v1.md` §5 "Field rename" — "Renaming any field MUST bump `schemaVersion` and ship a written migration **before** the breaking change reaches production". |
| CHK007 | [x] | `recovery-key-backup-v1.md` §5 forward-compat table specifies single decision point (`>1 → BackupError.UnsupportedSchema`); no scattered `if (v==1)…` branching admitted by design (no partial parse). |

## Forward compatibility

| ID | Status | Evidence |
|---|---|---|
| CHK008 | [x] **CLOSED** (was open spec-level) | `recovery-key-backup-v1.md` §5 — explicit table: `schemaVersion > 1` → reject with `BackupError.UnsupportedSchema(version)`, UI prompts owner to update. Rationale documented ("never silently degrade"). Worker mirrors via `UNSUPPORTED_SCHEMA` 400 (`worker-api-v1.md` §6). |
| CHK009 | [x] | `recovery-key-backup-v1.md` §6 — unknown `KdfParams.algorithm` enum value yields `BackupError.UnsupportedAlgorithm`, fail-closed. Worker error enum (§6) is closed set, unknown error codes "treated as generic transient failure" by client (`worker-api-v1.md` §6). |

## Tests

| ID | Status | Evidence |
|---|---|---|
| CHK010 | [x] | `recovery-key-backup-v1.md` §7 — `RecoveryKeyBackupBlobRoundtripTest` declared mandatory; `plan.md` Test Strategy table row "Domain port contract / Roundtrip". |
| CHK011 | [x] | `recovery-key-backup-v1.md` §7 — `RecoveryKeyBackupBlobBackwardCompatTest` reads immutable fixture `recovery-blob-v1-sample.json`; `plan.md` lists same in `commonTest/`. |
| CHK012 | [x] | `recovery-key-backup-v1.md` §8 — fixture path `core/keys/src/commonTest/resources/fixtures/recovery-blob-v1-sample.json` (file, not inline string). Second fixture `recovery-blob-v2-sample.json` for unsupported-schema test. |

## Persistence specifics

| ID | Status | Evidence |
|---|---|---|
| CHK013 | [x] | `plan.md` Project Structure — DataStore keys namespaced (`recovery-attempts/{stableId}`, `recoveryBackupDeferred`); Keystore aliases `key-registry/{stableId}/{purpose}`. Per-identity prefix throughout. |
| CHK014 | [N/A] | No SQLDelight in F-5 (DataStore Preferences + Keystore + R2 only). |
| CHK015 | [N/A] | No type-removal in scope; F-5 introduces wire format, doesn't retire one. |

## Deep-link / QR / exported config

| ID | Status | Evidence |
|---|---|---|
| CHK016 | [N/A] | No deep-link / QR payload in F-5 wire format. (QR-pairing is a separate spec — out of scope here.) |
| CHK017 | [x] | `recovery-key-backup-v1.md` §5 — missing/malformed `schemaVersion` → `BackupError.Malformed`, no partial parse. Worker mirrors via `MALFORMED_BODY` 400 (`worker-api-v1.md` §6). User-facing error, not crash. |

## Contract folder

| ID | Status | Evidence |
|---|---|---|
| CHK018 | [x] **CLOSED** (was open spec-level) | `contracts/` folder now contains two contract files. Each lists semantic version (v1 / `SCHEMA_VERSION_V1=1`), breaking-change policy (`recovery-key-backup-v1.md` §5, `worker-api-v1.md` §8), and links to test fixtures (`recovery-blob-v1-sample.json` in `commonTest/resources/fixtures/`). |

---

## Summary

- **Total**: 18 items.
- **[x]**: 15.
- **[N/A]**: 3 (CHK014 no SQLDelight, CHK015 no type-removal, CHK016 no deep-link/QR).
- **[ ]**: 0.
- **Previously-open closed**: CHK003, CHK005, CHK008, CHK018 — all four resolved by the contract artifacts produced in plan Phase 1.

**Verdict**: PASS. All applicable wire-format gates satisfied at plan level.
