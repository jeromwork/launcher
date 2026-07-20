# Wire Formats — Versioning and Evolution Discipline

**Single source of truth for wire-format versioning across the whole app ecosystem.** If this file and any other doc, skill, spec, or ADR disagree, **this file wins**. Change a rule → update this file **in the same commit** (§12). No other file restates these rules; they link here.

<!-- AI-TLDR:BEGIN -->

## AI TL;DR

**THE ADOPTED APPROACH — the beacon (do NOT re-decide).** A version is a **runtime instrument**, not a label: the document declares the minimum reader it needs, and reading and writing are gated **separately** → three outcomes, not two. Model: Matroska `DocTypeReadVersion` (RFC 9559) + SQLite read/write version bytes.

**Wire format** = anything that leaves the device or survives an app upgrade (§1): persisted config, cloud docs, encrypted blobs, QR payloads, deep links, exported files, push bodies. Test: *can a version of our code that did not write this ever read it?*

**Three fields, three decisions** (§3):

| Field | Reader's action |
|---|---|
| `schemaVersion` | none — diagnostics only |
| `minReaderVersion` | reader below it → **refuse** (typed error) |
| `minWriterVersion` | reader below it → **read-only**, tell the user |

Invariant: `minReaderVersion ≤ minWriterVersion ≤ schemaVersion`.

**The writer decides, per change, not per release** (§3). The old reader cannot judge — it does not know what the new data means. Added a field with a default → raise nothing. Redefined an existing field's meaning → raise MAJOR + `minReaderVersion`.

**Format** (§2): dotted string `"MAJOR.MINOR"` + optional pre-release token — `"2.0"`, `"3.0-beta"`. **NOT SemVer** — do not apply its grammar or precedence; comparison defined in §2. No patch component. Uniform across all formats; a born-stable small format is `"1.0"`.

**Never decrease a shipped version** (§9 I3). Pre-release tokens do not reset the counter.

**Unknown version → fail closed** (§4): typed error. Never guess, never best-effort parse, never decrypt to find out.

**Preserve unknown fields** on round-trippable formats (§6). `ignoreUnknownKeys = true` alone is the *field-stripping* bug, not the fix.

**Additive-only** (§5): new fields carry defaults, enums carry an unknown variant, removed names are permanently retired, types never change.

**Encrypted formats** (§7): version cleartext, outside the ciphertext; must-understand lists inside the authenticated region; server never interprets it (rule 13).

**Unknown ≠ corrupt** (§8): unknown follows the rule and continues; corrupt fails loudly. Never repair on the fly.

**Pre-MVP mode** (§10) = Article XX, the only maturity switch. A pre-release token requires a **declared expiry** — without one, do not use it.

**Enforcement** (§11): roundtrip test + golden corpus of **every** shipped version + `wire-format-hygiene` fitness rule. Values live in code (one named constant per format), not in this file.

**Transitional**: existing formats still carry integer `schemaVersion` and lack the reader/writer fields. They convert **on next touch** (§11), not in one sweep.

**How AI should use this file:**
- Routine question → **this TL;DR, stop here.**
- "Do I bump the version?" → §3.
- Add / rename / remove a field → §5 + §11.
- Reader hits an unknown version → §4. Encrypted blob → §7.
- "Can we break this freely now?" → §10.
- "Why not integers / SemVer / global alpha?" → **§13 (decided — do not re-litigate).**
- **Changing a rule → §12.**

<!-- AI-TLDR:END -->

## 1. Scope

A wire format is structured data that **leaves the device or survives an app upgrade** — it behaves like a public API even when only our own code reads it, because reader and writer may be different app versions.

**In**: persisted config and profiles, cloud documents, encrypted blobs, QR payloads, deep-link parameters, exported/imported files, push payload bodies, anything in shared storage.
**Out**: in-memory types, UI state, request/response bodies fully consumed within one app version, logs.

## 2. The version identifier

Dotted string `"MAJOR.MINOR"` + optional pre-release token: `"2.0"`, `"2.1"`, `"3.0-beta"`.

- **MAJOR** — old readers cannot interpret correctly. **MINOR** — additive, old readers safely ignore.
- **No patch component** — a data shape has no bugfix that is not either additive or breaking.
- **Pre-release token** — only under §10, with a declared expiry.
- **Comparison**: MAJOR numerically, then MINOR numerically; a pre-release token sorts below the same version without one.

**Not SemVer.** SemVer requires `MAJOR.MINOR.PATCH` and describes released software artifacts, not data shapes. Do not cite it or apply its precedence.

**Why a string**: a bare integer encodes revision only; the MAJOR/MINOR split is itself a machine-readable statement about whether a difference is breaking, and the pre-release token is a machine-readable waiver of the stability guarantee. Precedent: Kubernetes `apiVersion`, protobuf package suffix, JSON Schema `$schema`.

## 3. Reader obligations — the three fields

```json
{ "schemaVersion": "2.1", "minReaderVersion": "2.0", "minWriterVersion": "2.1" }
```

- **`schemaVersion`** — what wrote this. Diagnostics only; **no reader decision depends on it**.
- **`minReaderVersion`** — minimum level required to *interpret* correctly. Reader below it **MUST refuse** with a typed error (§4).
- **`minWriterVersion`** — minimum level required to *write back without destroying meaning*. Reader at/above `minReaderVersion` but below this **MUST go read-only** and surface it ("configured in a newer version — update to change"), never write and silently degrade.

Invariant (fitness-enforced): `minReaderVersion ≤ minWriterVersion ≤ schemaVersion`.

### Writer's decision procedure

Raising `minReaderVersion` is deliberate and rare. For the change being made:

| Change | Raise |
|---|---|
| Added a field with a default; absent ⇒ old behavior | MINOR only |
| Added a field an old reader would drop on write-back | `minWriterVersion` (or better: implement §6) |
| Changed what an existing field means | MAJOR + `minReaderVersion` |
| Removed a field old readers require | MAJOR + `minReaderVersion` |

Judge the individual change, not the release. Matroska's canonical example: a new element that only improves seek precision does **not** raise the minimum, because playback works without it.

## 4. Unknown version → fail closed

A reader hitting a `minReaderVersion` above what it supports, or an unparseable version string, **MUST** raise a typed error (`UnknownWireVersionException` or the format's equivalent) and stop. Never guess the shape, never best-effort parse, never decrypt to find out.

Universal, not our invention: RFC 8446 §4.2.1 (TLS), RFC 7518 §3.6 (JWS), RFC 9420 §10 (MLS) all state it as MUST; libsignal returns typed errors for both too-old and too-new. Every system examined has tightened toward strictness over time; none moved the other way.

The error MUST be distinguishable by callers from a corrupt-data error (§8) — different UI, different fix.

## 5. Change discipline

Compatibility is a structural property of the change, not a migration ladder:

- **Add fields only with a default.** A field without one is a breaking addition.
- **Enums carry an unknown/default variant** — an unrecognized symbol from a newer writer maps to it instead of failing the read (Avro resolution rule).
- **Never reuse a removed field's name** for a different meaning — permanently retired (protobuf `reserved` principle). Reuse corrupts deserialization in a way version numbers cannot catch.
- **Never change a field's type.** Add a new field, retire the old.
- **Renaming = removal + addition.** In JSON the name *is* the wire. If unavoidable, carry both names through a transition via `@JsonNames("old_name")`.

## 6. Unknown-field preservation

**Applies to formats that can be written back by a party that did not author them** (synced config and profiles). Not required for write-once formats (QR, push bodies, exports).

Requirement: unrecognized fields **MUST survive the read-modify-write cycle** byte-for-byte.

Failure prevented: device A (newer app) writes a field; device B (older) reads, the user changes something unrelated, B writes back — the field is gone. Undetectable by both sides: B never saw it, and A cannot distinguish "user cleared it" from "old client ate it". Protobuf calls this *field stripping* and flipped its default in 3.5 to prevent it.

**`ignoreUnknownKeys = true` is the failure mode, not the fix** — it is exactly the silently-dropping mode. kotlinx.serialization needs a custom serializer collecting unrecognized keys into a holder and re-emitting them. Field-by-field manual copying destroys the holder — copy the document, do not rebuild it.

Test: read a fixture with undeclared fields, modify one known field, write, assert the undeclared fields are unchanged.

## 7. Encrypted formats

- **Version is cleartext, outside the ciphertext**, readable before any crypto operation — you cannot decrypt to learn how to decrypt. (Signal version nibble, TLS, JWS, MLS, age, Bitwarden.)
- **Unrecognized version → typed error, never trial decryption** (§4).
- **Server sees opaque bytes only** — no routing, transforming, or deciding on the version (rule 13).
- **A must-understand field list MUST be inside the authenticated/signed region.** COSE makes a `crit` label outside the protected bucket a fatal error — otherwise an attacker strips the list and an old reader accepts what it should reject.

## 8. Unknown versus corrupt

- **Unknown** (new field, new enum symbol) → apply the rule: preserve (§6), default (§5), continue. This is strict compliance with a specified rule, not leniency.
- **Corrupt** (dangling reference, negative length, `minReaderVersion > schemaVersion`, malformed structure) → **fail loudly**. Do not repair on the fly.

RFC 9413 §4.1: on-the-fly repair entrenches the other side's bug, forces every other implementation to replicate it, and the flaw becomes a de-facto standard nobody can remove. Since the server is zero-knowledge and cannot report content errors, the signal is local — a log entry, and an in-app indicator where the user is affected.

## 9. Invariants (decided — do NOT re-derive)

Changing one requires a `decision-supersedes` backlog task (rule 11), not an inline edit.

- **I1** — Every wire format carries the three version fields from its first commit.
- **I2** — Dotted string, uniform across all formats. Not SemVer, not an integer, not mixed.
- **I3** — **A shipped version number never decreases.** Not when adopting a pre-release token, not ever. Readers in the field compare it; a decrease misroutes them silently. Firestore rules additionally enforce monotonicity server-side (§11).
- **I4** — The writer decides whether a change is breaking, judged per change.
- **I5** — Unknown version fails closed with a typed error.
- **I6** — Round-trippable formats preserve unknown fields; `ignoreUnknownKeys` does not satisfy this.
- **I7** — Removed field names are permanently retired.
- **I8** — Encrypted formats: version cleartext outside the ciphertext; must-understand lists inside the authenticated region.

## 10. Pre-MVP mode — the only maturity switch

Article XX of [`constitution.md`](../../.specify/memory/constitution.md): while active, wire formats may be replaced outright with no migration path. The version *fields* stay mandatory; only the *migration guarantee* is suspended. Binary — pre-MVP = no migration, post-MVP = full migration, no middle state.

A pre-release token (`"3.0-beta"`) may mark an individual format as still breakable **only with an expiry declared where the format is defined**. Without a deadline, do not use it.

**Why the deadline is mandatory.** Unenforced maturity markers reliably fail: Kubernetes shipped beta-by-default and 90%+ of production clusters ran it (KEP-3136 could only fix it for future APIs); Ingress sat in beta 18 releases, became de-facto stable through adoption, and was replaced rather than evolved; GitHub abandoned per-feature preview markers entirely. The counter-example that works is Azure's `-preview`: retires 90 days after a replacement, dead within a year.

### Pre-MVP → GA ceremony (one-time)

Tag (`v1.0-mvp`) → sweep all formats, remove pre-release tokens → commit a golden fixture per format at its GA version → switch the fitness rule to strict mode → from that commit, migration writers are mandatory for every breaking change and old fixtures are never deleted (they become regression tests).

## 11. Enforcement

A rule no test checks is decoration. Per format:

- **Roundtrip test** — write → read → assert equal.
- **Golden corpus** — a checked-in document for **every historically shipped version**, all read by the current reader. Not just the previous one: our documents have **no retention window**, so pairwise checks are insufficient (V1→V2 and V2→V3 passing says nothing about V1→V3).
- **Round-trip preservation test** where §6 applies.
- **`wireFormatVersionsComeFromNamedConstants`** in [`ArchitectureFitnessTest`](../../app/src/test/java/com/launcher/app/fitness/ArchitectureFitnessTest.kt) — currently checks that every version value comes from a named constant, never a literal. Strict-mode checks (all three fields present and correctly ordered, golden fixture exists, renamed fields carry `@JsonNames`) land with TASK-138.

  It is an ordinary unit test, not a Detekt rule, and deliberately so (TASK-140): these rules lived as custom Detekt detectors from TASK-65 to 2026-07-20 and **never ran once** — Detekt's plugin loader never registered them, and a rule that fails to load reports nothing and passes. Run via `./gradlew fitnessCheck`.

**Naming**: one constant per format — `SCHEMA_VERSION`, `MIN_READER_VERSION`, `MIN_WRITER_VERSION` — declared beside the type. The constant is the single source of the value; no literals at call sites.

**Migration of existing formats.** Formats predating this document carry an integer `schemaVersion` and no reader/writer fields. They convert **on next touch**, not in one sweep — converting a format means: integer → dotted string at the *same or higher* number (never lower, I3), add the two fields, update fixtures and roundtrip tests, and update the Firestore security rule for that collection (rules compare `schemaVersion` numerically; string comparison is lexicographic and unsafe — `"10.0" < "9.0"`). Until a format is converted, its integer form remains valid.

## 12. How to change this document (sync rule — HARD)

- **Single source of truth.** Any change to a rule, an invariant (§9), or the enforcement mechanism MUST update this file **in the same commit**. Refuse a rule change that does not touch `wire-format.md`.
- **Other docs link, never restate.** `CLAUDE.md` rule 5, `AGENTS.md`, `docs/dev/agent-context.md`, `checklist-wire-format`, and every spec carry a link. A second copy is drift by construction.
- **The `wire-format` skill is a thin router** — trigger, pointer, guardrails, sync rule. Never a second copy.
- **§9 invariants are decided** — revising one needs a `decision-supersedes` task.
- **Historical artifacts are stamped, not rewritten.** Completed specs and superseded ADRs keep their text and gain a header pointing here.

## 13. Rejected (do not re-litigate)

- **Bare integer `schemaVersion`** — encodes revision only; cannot express breaking-vs-additive or "still breakable". Legitimate elsewhere (OCI manifest); rejected because §3 needs the parts.
- **Mixed scheme (strings for big formats, integers for small)** — no major system does this. The version field is itself a wire contract; a heterogeneous rule forces every reader to branch on format, and a small format that grows must change its own field type — a wire break. A born-stable small format is `"1.0"`.
- **SemVer semantics** — requires three components; strings like `"1-alpha.0"` are invalid under its own grammar, and its precedence describes release artifacts, not data shapes.
- **Decreasing a shipped version / "the pre-release counter resets"** — not a SemVer operation and unattested for a monotonic counter. Kubernetes' `v2alpha1 → v1beta1` CronJob case is a track-name change, not a counter decrement; it does not transfer.
- **A global alpha/beta switch across all formats** — maturity is per-format; a global switch makes shipping individual features hard. Article XX is a project-phase gate, a different axis.
- **A lenient reader that repairs corrupt documents** — entrenches bugs permanently (§8).
- **Server-side migration or version-aware routing** — violates rule 13; the server sees opaque blobs. Migration is client-side, always.

## 14. Related

Links here: [`CLAUDE.md`](../../CLAUDE.md) rule 5 · [`AGENTS.md`](../../AGENTS.md) · [`agent-context.md`](../dev/agent-context.md)
Skills: [`wire-format`](../../.claude/skills/wire-format/SKILL.md) (router) · [`checklist-wire-format`](../../.claude/skills/checklist-wire-format/SKILL.md) (spec audit)
Constitution: Article XX (pre-MVP override), Article VII §3 (configuration schemas)
Architecture: [`ecs.md`](ecs.md) (Preset/Profile/Pool are wire formats) · [`server.md`](server.md) · [`crypto.md`](crypto.md)
Backlog: TASK-16 (this discipline) · TASK-131 (lenient reader) · TASK-70 (cloud transfer unit)
