# Checklist: wire-format

Applied to `specs/task-6-root-key-hierarchy-recovery/spec.md`.
Wire format under review: **`RecoveryKeyBackupBlob`** (FR-006, JSON, schemaVersion=1).
Secondary: `ConfigCipher2` ciphertext envelope (FR-018 — byte-equal preservation, schemaVersion unchanged).

| ID    | Item | Status | Evidence / Note |
|-------|------|--------|-----------------|
| CHK001 | Every wire format carries explicit `schemaVersion: Int` from first commit | [x] | FR-006: `"schemaVersion": 1` as first field; spec is first commit |
| CHK002 | `schemaVersion` read first during deserialization | [x] | FR-006 places `schemaVersion` as the leading JSON field; FR-023 ProviderAgnosticTest enforces schema; convention preserved |
| CHK003 | Currently-supported `schemaVersion` constant documented in code (single source of truth) | [ ] | Spec mentions `schemaVersion=1` in FR-006 but does not require a single named constant (e.g., `RecoveryKeyBackupBlob.CURRENT_SCHEMA_VERSION`) — recommend adding to plan |
| CHK004 | Reads of previous schema versions remain possible for ≥ 1 major release | [N/A] | v1 is first version; FR-023 BackwardCompatTest enumerates the obligation for when v2 ships |
| CHK005 | Adding a field allowed; deserializer handles missing fields with documented defaults | [ ] | Spec does not state additive-field policy / kotlinx-serialization `@EncodeDefault` / `ignoreUnknownKeys = true` behavior — recommend explicit FR or plan note |
| CHK006 | Renaming/removing field requires versioned migration written before breaking change | [x] | Edge Q-A explicitly classifies KDF param changes as breaking; out-of-scope items defer rename/removal to TASK-41 |
| CHK007 | Migration code scoped (`migrateLegacy(json): Action`), not branching everywhere | [N/A] | No legacy versions yet for `RecoveryKeyBackupBlob` |
| CHK008 | Reading newer schema versions handled gracefully (skip unknown OR fail-closed, choice documented) | [ ] | FR-023 lists "read schemaVersion=2 → graceful error" but doesn't document the chosen strategy (fail-closed vs upgrade-prompt vs skip-unknown) |
| CHK009 | Unknown discriminator yields `Failure("unknown kind")`, not crash | [N/A] | No discriminator/kind field in blob; KDF `algorithm: "Argon2id"` is a hardcoded string, not an open discriminator |
| CHK010 | Roundtrip test exists: write → read → assertEquals | [x] | FR-023: `RecoveryKeyBackupBlobRoundtripTest` |
| CHK011 | Backward-compat test exists: previous-version fixture reads | [x] | FR-023: `RecoveryKeyBackupBlobBackwardCompatTest`; fixture path declared in Local Test Path |
| CHK012 | Test fixtures stored as files in `commonTest/resources/` (not literal strings) | [x] | Local Test Path: `core/keys/src/commonTest/resources/fixtures/recovery-blob-v1-sample.json` + `config-ciphertext-spec018-sample.bin` |
| CHK013 | SharedPreferences/DataStore keys namespaced (`<domain>.<feature>.<key>`) | [x] | FR-015: `recovery-attempts/{stableId}` (namespaced); identity cascade wipe respects namespace |
| CHK014 | SQLDelight migrations have N-1 → N tests | [N/A] | No SQLDelight tables introduced |
| CHK015 | Removed stored types: one-shot cleanup with grep-anchor comment | [N/A] | Nothing removed in this spec (FR-018 byte-equal preserve, no field rename/drop) |
| CHK016 | URL/QR payload embeds `schemaVersion` in path or first JSON field | [N/A] | No URL/QR/deep-link surface in F-5 |
| CHK017 | Truncated/corrupted payload yields user-facing error, not crash | [x] | Edge Cases: "Recovery blob corrupted" → `RootKeyError.CorruptedBlob` → UI fallback path |
| CHK018 | `contracts/` directory: each contract lists semantic version + breaking-change policy + roundtrip fixture link | [ ] | US-6 AS#4 references `contracts/recovery-key-backup-v1.md` but spec does not actually define this contract file or its mandatory fields (semver, breaking-change policy) — recommend creating in plan phase |

---

## Summary

- Pass: 9
- Fail: 4 (CHK003, CHK005, CHK008, CHK018)
- N/A: 5 (CHK004, CHK007, CHK009, CHK014, CHK015, CHK016) — counted as N/A (do not block)
- Total applicable: 13

**Fail rationale (all addressable in plan, not blocking spec):**
- **CHK003** — schemaVersion constant: add `const val CURRENT_SCHEMA_VERSION = 1` requirement to plan.
- **CHK005** — additive-field policy: add FR clause on `ignoreUnknownKeys = true` + default-on-missing semantics.
- **CHK008** — newer-version strategy: pick one of {fail-closed, skip-unknown, upgrade-prompt} and document in FR-006 or contract file.
- **CHK018** — `contracts/recovery-key-backup-v1.md` referenced (US-6 AS#4) but not yet created; required before implementation.

**ConfigCipher2 envelope (FR-018):** byte-equal preservation requirement keeps existing envelope schemaVersion stable, which itself satisfies CHK001/CHK006 transitively for the migrating wire format (no rename, no break).
