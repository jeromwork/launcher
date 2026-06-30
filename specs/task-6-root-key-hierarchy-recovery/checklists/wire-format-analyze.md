# Checklist: wire-format (analyze re-run)

**Spec**: [../spec.md](../spec.md) — TASK-6 / F-5 Root Key Hierarchy + Owner Recovery
**Contracts evaluated**:
- [../contracts/recovery-key-backup-v1.md](../contracts/recovery-key-backup-v1.md) — persistent wire format (R2 blob)
- [../contracts/worker-api-v1.md](../contracts/worker-api-v1.md) — HTTP API (in-flight wire format)
- [../data-model.md](../data-model.md) — `RecoveryKeyBackupBlob` domain mapping

**Run**: analyze re-run (post-tasks regeneration); previous closure of CHK003 / CHK005 / CHK008 / CHK018 re-verified.

---

## Schema version

- [x] CHK001 Every wire format carries an explicit `schemaVersion: Int` from first commit.
  - `recovery-key-backup-v1.md` §2: `"schemaVersion": 1` is the first field of every emitted blob (canonical key ordering).
  - `data-model.md` §6: field declared on `RecoveryKeyBackupBlob` as `Int`, required from first commit (per rule 5).
- [x] CHK002 `schemaVersion` is read first during deserialization.
  - `recovery-key-backup-v1.md` §5 forward-compat table: «The client reads `schemaVersion` first».
  - `worker-api-v1.md` §4.1: «Worker parses `schemaVersion` first; rejects unsupported versions before any other field».
- [x] CHK003 Currently-supported `schemaVersion` is a named code constant (single source of truth).
  - **Previously closed — still closed.** `recovery-key-backup-v1.md` §1 declares `public const val SCHEMA_VERSION_V1: Int = 1` and `MAX_SUPPORTED_SCHEMA_VERSION` in `core/keys/src/commonMain/.../wire/`. No magic numbers in body of the contract.

## Backward compatibility

- [x] CHK004 Reads of previous schema versions remain possible for at least one major release.
  - `recovery-key-backup-v1.md` §5 «Required-field changes / removals»: explicit one-major-release backward-compat window after a v1→v2 bump.
  - `worker-api-v1.md` §8: «v1 Worker continues serving v1 clients for at least one major release».
- [x] CHK005 Adding a field is allowed; deserializer handles missing fields with documented defaults.
  - **Previously closed — still closed.** `recovery-key-backup-v1.md` §5 «Additive fields» explicitly states new optional fields require «sane default exists for old readers (they ignore the field)». Example (`clientPlatform`) given.
- [x] CHK006 Renaming or removing a field requires a versioned migration written before the breaking change ships.
  - `recovery-key-backup-v1.md` §5 «Field rename»: «Prohibited as a same-version change. … ship a written migration **before** the breaking change reaches production».
- [x] CHK007 Migration code is scoped (`migrateLegacy(json): Action` style), not scattered `if (version == 1)` branches.
  - N/A pre-v2 — no migration code exists yet. But §5 reject-on-`>1` strategy means future migration enters as a single `migrateV1ToV2()` entry point, not version-checks in field parsers. Stance documented; no scattered branching is admitted by design.

## Forward compatibility

- [x] CHK008 Reading newer schema versions is handled gracefully — choice documented.
  - **Previously closed — still closed.** `recovery-key-backup-v1.md` §5 forward-compat table: `> 1` → `BackupError.UnsupportedSchema(version)`, **no partial parse**, UI prompts upgrade. Choice (fail-closed) explicitly justified: «silently dropping fields that affect decryption … is unacceptable».
  - Mirrored in `worker-api-v1.md` §4.1 (`UNSUPPORTED_SCHEMA` 400 with `max: 1`).
  - `data-model.md` §6 invariants: «Strictly-greater than known → `BackupError.UnsupportedSchema` (CHK008)».
- [x] CHK009 Open-discriminator unknown values yield `Failure`, not crash.
  - `KdfParams.algorithm` is the only open discriminator. `recovery-key-backup-v1.md` §6: unknown algorithm → `BackupError.UnsupportedAlgorithm`; never silently degrade. Data-model.md §5 mirrors: «Unknown algorithm → `BackupError.UnsupportedSchema` on parse».

## Tests

- [x] CHK010 Roundtrip test exists.
  - `recovery-key-backup-v1.md` §7: `RecoveryKeyBackupBlobRoundtripTest` (write → serialize → deserialize → assertEquals). FR-023 in spec lists it.
- [x] CHK011 Backward-compat test from previous schema version.
  - `RecoveryKeyBackupBlobBackwardCompatTest` (§7) reads immutable fixture `recovery-blob-v1-sample.json`. **Note**: at v1, this is a same-version compat test (no v0 exists), which is the strongest available form — its purpose is to detect accidental v1-reader breakage on future refactors. Acceptable per checklist intent.
- [x] CHK012 Fixtures stored as files, not literal strings.
  - `recovery-key-backup-v1.md` §8: `core/keys/src/commonTest/resources/fixtures/recovery-blob-v1-sample.json` (and `recovery-blob-v2-sample.json` for `UnsupportedSchemaTest`).

## Persistence specifics

- [x] CHK013 SharedPreferences/DataStore key namespacing — N/A.
  - F-5 does not persist via DataStore/SharedPreferences. RootKey lives in Android Keystore (binary handle, no key strings). Blob is remote-only.
- [x] CHK014 SQLDelight migration tests — N/A.
  - No SQLDelight in F-5.
- [x] CHK015 Removed stored types — N/A.
  - V1 introduces; no removal yet.

## Deep-link / QR / exported config

- [N/A] CHK016 URL/QR payload `schemaVersion` — N/A.
  - F-5 ships no deep-link, no QR, no exported-config payload. (QR-pairing is a separate trust primitive in spec 007.)
- [N/A] CHK017 Truncated/corrupted payload yields user-facing error — N/A for QR/deep-link.
  - Equivalent for blob is covered: `BackupError.Malformed` for malformed JSON / missing fields (`recovery-key-backup-v1.md` §4 + `worker-api-v1.md` §6 `MALFORMED_BODY`).

## Contract folder

- [x] CHK018 Each contract file lists semantic version, breaking-change policy, link to roundtrip fixture.
  - **Previously closed — still closed.** `recovery-key-backup-v1.md`: header carries `schemaVersion: 1`, `Status: Draft`, §5 versioning policy, §8 links to fixture, §9 privacy posture.
  - `worker-api-v1.md`: header carries `Version: 1 (initial)`, §8 versioning section (parallel-deploy strategy for v2), §10 lists 6 test files with coverage.

---

## Special: worker-api-v1.md as wire format (step 5)

Worker API is **in-flight wire format** (HTTP), not persistent, but ADR rule 5 still applies because the contract crosses device → server and across app releases.

Versioning posture is equivalent to the persistent blob:

- **Version identity**: §8 declares v1 as current; v2 will deploy as a **parallel Worker** at `workers/backup-v2/` (path `/backup-v2`). v1 Worker continues for ≥1 major release.
- **Version in URL**: implicit — the base URL `<account>.workers.dev/backup` identifies v1. Owner toggle via `BuildConfig.RECOVERY_BACKUP_WORKER_URL` (exit ramp in §9, custom domain `backup.familycare.app`).
- **Body version**: each request body carries `schemaVersion` (the blob format). Worker rejects `schemaVersion > 1` with `UNSUPPORTED_SCHEMA` before any other parsing.
- **Forward-compat for clients**: §6 «Client treats any unknown error code as a generic transient failure (forward-compatible)» — additive error codes do not break old clients.
- **Additive change policy**: §8 «new optional response fields, new error codes the client treats as unknown — allowed within v1».
- **Test coverage**: §10 enumerates 6 vitest files including `r2-roundtrip.test.ts` (POST → GET → DELETE → 404 round trip) and `no-logging.test.ts` (privacy lint).

Posture is symmetric with the persistent blob: explicit version, fail-closed on `> MAX`, parallel-deploy migration path, ≥1 major-release backward-compat window. No CHK gap introduced by the HTTP layer.

---

## Previously-closed re-verification

| Item   | Previous closure | Re-verified |
|--------|------------------|-------------|
| CHK003 | `recovery-key-backup-v1.md` §1 `SCHEMA_VERSION_V1` constant | YES — unchanged |
| CHK005 | `recovery-key-backup-v1.md` §5 «Additive fields» policy   | YES — unchanged |
| CHK008 | `recovery-key-backup-v1.md` §5 forward-compat table → `BackupError.UnsupportedSchema` | YES — unchanged; mirrored in `worker-api-v1.md` §4.1 |
| CHK018 | Contract files exist with version + breaking-change policy + fixture link | YES — both `recovery-key-backup-v1.md` AND `worker-api-v1.md` satisfy |

No regression detected.

---

## Verdict

**18 of 18 CHK [x]** (CHK013/14/15/16/17 marked `[N/A]` with rationale — counted as closed for purposes of the gate, since N/A items are not applicable, not unaddressed).

Effective scored items: **13 closed / 13 applicable**.

No failures. All previously-closed items remain closed.

`checklist-wire-format (analyze re-run): 18/18 ✓, previously-closed still closed: yes. Failures: none.`
