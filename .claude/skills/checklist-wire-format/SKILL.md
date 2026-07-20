---
name: checklist-wire-format
description: Audits a spec against the ecosystem wire-format versioning discipline — version fields present, reader/writer gating declared, unknown-version behavior specified, roundtrip and golden-corpus tests planned. The rules themselves live in docs/architecture/wire-format.md; this skill only checks compliance. Triggered by mentions of JSON, schema, schemaVersion, persistence, SharedPreferences, DataStore, SQLDelight, deep-link, QR, sync, push payload, encrypted blob, contracts.
---

# Checklist: wire-format (spec audit)

**This skill audits; it does not define.** The rules are in [`docs/architecture/wire-format.md`](../../../docs/architecture/wire-format.md) — read its AI-TLDR before applying this checklist, and cite section numbers in findings. If a gate here and `wire-format.md` disagree, **`wire-format.md` wins** — fix this file.

Scope of "wire format": `wire-format.md` §1. Companion skill: [`wire-format`](../wire-format/SKILL.md) answers "what are the rules"; this one answers "does this spec comply".

---

## Version fields (§2, §3)

- [ ] CHK001 Every wire format carries all three fields — `schemaVersion`, `minReaderVersion`, `minWriterVersion` — from its first commit.
- [ ] CHK002 Version values are dotted strings (`"2.0"`), not integers, not SemVer-shaped. Ordering `minReader ≤ minWriter ≤ schemaVersion` holds.
- [ ] CHK003 One named constant per format (`SCHEMA_VERSION` / `MIN_READER_VERSION` / `MIN_WRITER_VERSION`) declared beside the type — no version literals at call sites (§11).
- [ ] CHK004 Version fields are **read first** during deserialization, before parsing the rest.
- [ ] CHK005 No shipped version number decreases (I3). A pre-release token does not reset the counter.

## Reader behavior (§3, §4, §8)

- [ ] CHK006 Spec states what happens when `minReaderVersion` exceeds support: typed error, fail closed, no best-effort parse.
- [ ] CHK007 Spec states the read-only path when `minWriterVersion` exceeds support — including what the user is shown.
- [ ] CHK008 Unknown-version errors are distinguishable by callers from corrupt-data errors.
- [ ] CHK009 Corrupt input fails loudly; no on-the-fly repair (§8).
- [ ] CHK010 Open discriminators (`kind: "..."`) yield a typed failure on unknown values, never a crash.

## Change discipline (§5)

- [ ] CHK011 New fields carry defaults; enums carry an unknown/default variant.
- [ ] CHK012 No removed field name is reused; no field changes type.
- [ ] CHK013 Any rename carries both names through a transition (`@JsonNames`), or the change is classified as breaking per §3.
- [ ] CHK014 If the change is breaking, the spec says so and raises MAJOR + `minReaderVersion` — with the writer's reasoning recorded (§3 decision procedure).

## Round-trip safety (§6)

- [ ] CHK015 For formats that can be written back by a party that did not author them: unknown fields survive read-modify-write. `ignoreUnknownKeys = true` alone does **not** satisfy this.
- [ ] CHK016 A preservation test is planned (fixture with undeclared fields → modify → write → assert undeclared fields unchanged).

## Encrypted formats (§7)

- [ ] CHK017 Version is cleartext, outside the ciphertext, readable before any crypto operation.
- [ ] CHK018 No trial decryption on unrecognized versions.
- [ ] CHK019 Any must-understand field list sits inside the authenticated/signed region.
- [ ] CHK020 Server treats the blob as opaque — no routing or transforming on version (rule 13).

## Tests and enforcement (§11)

- [ ] CHK021 Roundtrip test per format: write → read → assertEquals.
- [ ] CHK022 Golden corpus covers **every** historically shipped version, not just the previous one (no retention window ⇒ pairwise checks are insufficient).
- [ ] CHK023 Fixtures are files in test resources, not literal strings in test code.
- [ ] CHK024 `wire-format-hygiene` passes; any `@Suppress("WireFormatHygiene")` carries a justification.

## Maturity marker (§10)

- [ ] CHK025 If a pre-release token is used, an expiry is declared where the format is defined. No expiry ⇒ no token.

## Persistence and payload specifics

- [ ] CHK026 SharedPreferences/DataStore keys namespaced (`<domain>.<feature>.<key>`).
- [ ] CHK027 SQLDelight: every migration script has a test loading the N-1 schema.
- [ ] CHK028 A removed stored type has a one-shot cleanup, documented with a grep-anchor comment.
- [ ] CHK029 QR/deep-link payloads embed the version in the path or first JSON field; truncated payloads yield a user-facing error, not a crash.

---

## How to apply

1. Identify every wire format introduced or touched by the spec.
2. Walk the gates per format, skipping sections that do not apply (encrypted, round-trippable, QR).
3. Failures → cite the `wire-format.md` section, propose the corrected shape.

**Transitional note**: formats predating `wire-format.md` still carry integer `schemaVersion` and no reader/writer fields. That is expected — they convert on next touch (TASK-138). Flag it as a finding only if the spec *touches* such a format without converting it.

## Output

Chat only — one red-only summary line per ADR-011 §5:
`checklist-wire-format: N/Total ✓, FAIL: CHK-XXX (short why)`.
Do NOT create `specs/<id>/checklists/wire-format.md`. Scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.
