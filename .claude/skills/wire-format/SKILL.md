---
name: wire-format
description: The ecosystem's wire-format versioning discipline — a version is a runtime instrument (minReaderVersion / minWriterVersion gate reading and writing separately), not a label. Invoke whenever work touches schemaVersion / minReaderVersion / minWriterVersion / wire format / serialization / persisted config / migration / backward compatibility / roundtrip test / golden fixture / QR payload / push payload / encrypted blob / deep link, or asks "do I bump the version" or "how does our versioning work". Routes to docs/architecture/wire-format.md (single source of truth) so the rules are never re-derived or re-decided. Cross-app — this discipline spans the whole ecosystem, not only the launcher.
---

# Skill: wire-format — the adopted versioning discipline (router)

**This skill is a thin router, not a copy of the rules.** The single source of truth is [`docs/architecture/wire-format.md`](../../../docs/architecture/wire-format.md). Read its **AI-TLDR** first. **Do not re-derive the rules or re-decide settled questions** — they are decided and verified against primary sources there. If this skill and `wire-format.md` ever disagree, **`wire-format.md` wins** — fix the skill.

## When this fires
Any work touching: a `@Serializable` type that is persisted or transmitted, `schemaVersion` / `minReaderVersion` / `minWriterVersion`, serialization, migration, backward compatibility, roundtrip or golden-fixture tests, QR payloads, push payloads, deep links, encrypted blobs, exported files — in the launcher or any ecosystem app; or the questions "do I bump the version here?" / "is this change breaking?" / "how does our versioning work?".

## The adopted approach in one line (the beacon)
**A version is a runtime instrument, not a label**: the document declares the minimum reader it needs (`minReaderVersion`) and the minimum writer it needs (`minWriterVersion`), so a reader gets **three outcomes** — full access, read-only, or refuse — instead of two. The **writer** of a change decides whether it is breaking, judged per change rather than per release. Precedent: Matroska `DocTypeReadVersion` (RFC 9559), SQLite read/write version bytes. Full rules, industry grounding, and rejected alternatives live in `wire-format.md`.

## Guardrail invariants (never violate; authoritative list = wire-format.md §9)
1. **Three fields from commit one** — `schemaVersion` (diagnostics only), `minReaderVersion` (below it → refuse), `minWriterVersion` (below it → read-only). Ordered `minReader ≤ minWriter ≤ schemaVersion`.
2. **Dotted string, uniform, NOT SemVer** — `"2.0"`, `"3.0-beta"`. Never a bare integer, never a mixed string/int scheme, never SemVer grammar or precedence. A born-stable small format is `"1.0"`.
3. **A shipped version never decreases.** Pre-release tokens do not reset the counter. Refuse any change that lowers a shipped number.
4. **Unknown version → typed error, fail closed.** Never guess the shape, never best-effort parse, never decrypt to find out.
5. **Round-trippable formats preserve unknown fields.** `ignoreUnknownKeys = true` alone is the *field stripping* failure, not the fix — data vanishes silently and neither side can detect it.
6. **Additive-only discipline** — new fields carry defaults, enums carry an unknown variant, removed field names are permanently retired, field types never change.
7. **Encrypted formats** — version in cleartext outside the ciphertext; any must-understand list inside the authenticated region. The server never interprets it (rule 13).
8. **Unknown ≠ corrupt** — unknown follows the rule and continues; corrupt fails loudly. Never repair on the fly.
9. **A pre-release token requires a declared expiry.** Without a deadline, do not use one (`wire-format.md` §10 — this is what kept Kubernetes Ingress frozen in beta for 5 years).

## Hard sync rule
If you change a versioning rule, an invariant, or the enforcement mechanism, **update `wire-format.md` in the same commit** (`wire-format.md` §12). Never leave it behind — it is the one file the whole ecosystem reads. Other docs carry a link, never a copy.

## Reading map (jump straight to the section)
- Routine question → `wire-format.md` AI-TLDR, stop there.
- "Do I bump the version?" → §3 (the writer's decision procedure).
- Adding / renaming / removing a field → §5, then §11 (tests you must write).
- Reader hits a version it doesn't know → §4.
- Encrypted blob → §7.
- "Can we break this freely right now?" → §10 (pre-MVP mode + Article XX).
- "Why not integers / SemVer / a global alpha switch?" → §13 (decided — do not re-litigate).

## Related skill
[`checklist-wire-format`](../checklist-wire-format/SKILL.md) audits a **spec** against this discipline. This skill answers "what are the rules"; that one answers "does this spec comply". Neither restates the rules — both point at `wire-format.md`.
