---
name: checklist-wire-format
description: Verifies wire formats (anything that leaves the device or persists across app versions) carry schema-version, have roundtrip tests, and follow backward-compat rules per CLAUDE.md rule 5 and Article VII. Triggered by mentions of JSON, schema, persistence, SharedPreferences, DataStore, SQLDelight, deep-link, QR, sync, contracts.
---

# Checklist: wire-format

Enforces **wire-format and contract versioning** per [`CLAUDE.md`](CLAUDE.md) rule 5 and Article VII §3 of [`/.specify/memory/constitution.md`](/.specify/memory/constitution.md).

A "wire format" is anything that:
- leaves the device (network sync, deep-link, QR, exported file),
- persists across app versions (SharedPreferences with structure, DataStore Proto, SQLDelight tables, files in app storage),
- crosses module boundaries with versioning needs (cross-platform contracts).

---

## Schema version

- [ ] CHK001 Every wire format carries an explicit `schemaVersion: Int` field from its first commit.
- [ ] CHK002 `schemaVersion` field is **read first** during deserialization (so unsupported versions can be detected before parsing the rest).
- [ ] CHK003 Currently-supported `schemaVersion` constant is documented in code (single source of truth — no magic number scattered).

## Backward compatibility

- [ ] CHK004 Reads of **previous** schema versions remain possible for at least one major release.
- [ ] CHK005 Adding a field is allowed; the deserializer handles missing fields with documented defaults.
- [ ] CHK006 Renaming or removing a field requires a versioned migration **written before the breaking change ships**.
- [ ] CHK007 Migration code is **scoped** — `migrateLegacy(json): Action` style — not branching `if (version == 1) ... else ...` everywhere.

## Forward compatibility

- [ ] CHK008 Reading **newer** schema versions is handled gracefully (skip unknown fields, fail-closed on unknown discriminator OR explicit upgrade prompt — choice documented).
- [ ] CHK009 If discriminator (e.g. `kind: "..."`) is open: an unknown value yields `Failure("unknown kind")`, not a crash.

## Tests

- [ ] CHK010 Roundtrip test exists for every wire-format type: write → read → assertEquals.
- [ ] CHK011 Backward-compat test exists: a fixture from previous schema version reads successfully.
- [ ] CHK012 Test fixtures are **stored as files** in `commonTest/resources/` (not literal strings in test code) — easier to detect drift.

## Persistence specifics

- [ ] CHK013 SharedPreferences/DataStore: keys namespaced (`<domain>.<feature>.<key>`), not bare strings.
- [ ] CHK014 SQLDelight: every migration script has a corresponding test that loads an N-1 schema and applies the migration.
- [ ] CHK015 If a stored type is **removed** entirely (feature gone): one-shot cleanup written; documented with grep-anchor comment.

## Deep-link / QR / exported config

- [ ] CHK016 URL/QR payload embeds `schemaVersion` in the path or first JSON field.
- [ ] CHK017 Truncated/corrupted payload yields user-facing error, not crash (defense against scan misreads).

## Contract folder

- [ ] CHK018 If `contracts/` exists: each contract file lists its semantic version, breaking-change policy, and a link to roundtrip test fixture.

---

## How to apply

1. Identify every wire format introduced or touched by spec.
2. Walk the gates per format.
3. Failures → add `schemaVersion`, write tests, document migrations.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-wire-format: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/wire-format.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.
