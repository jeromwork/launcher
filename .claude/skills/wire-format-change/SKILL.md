---
name: wire-format-change
description: Executes a change to a wire format end to end. Invoke whenever editing a type that implements WireVersionHeader, a *WireFormat codec, a bundled JSON asset with a version header, firestore.rules version logic, or a workers/*/src/contract/*.ts constant — that is, whenever a field is added, renamed, removed, retyped, or its meaning changes. Asks at most two questions, then performs the whole mechanic: picks the numbers, updates constants on every side, regenerates fixtures, updates the security rules, and runs the checks. Sister skill to `wire-format`, which answers "what are the rules"; this one answers "make the change".
---

# Skill: wire-format-change — make the change, don't recite the rules

Rules live in [`docs/architecture/wire-format.md`](../../../docs/architecture/wire-format.md). **Do not re-derive them.** This skill is the procedure that applies them so nobody has to hold them in mind.

**Default to breaking.** Every ambiguous answer resolves to the more conservative choice. A version bumped when it did not need to be costs nothing; a version not bumped when it should have been corrupts other people's documents silently.

---

## Step 1 — Establish what actually changed

Read the diff. Do not ask the user what they changed — you made the edit and the diff is on hand. Classify every touched field:

| Observed in the diff | Class |
|---|---|
| New field with a default | additive |
| New enum case, new optional value | additive |
| Field removed | **breaking** |
| Field renamed | **ask — question A** |
| Field type changed (`Int` → `String`, `T` → `T?` narrowing) | **breaking** |
| Meaning of an existing field changed while its name stayed | **breaking** |
| Comment, formatting, test-only change | none — stop here, no bump |

The one thing a diff cannot show is whether a rename preserved meaning. That is question A.

## Step 2 — At most two questions

**Question A — only when a field was renamed or its semantics may have shifted:**
> "`<old>` became `<new>`. Same thing under a new name, or does it now mean something different?"

If the user does not answer, or the answer is uncertain: **treat as breaking.**

**Question B — only when the format is read-modify-written by more than one writer** (a shared cloud document, a config both devices edit). Skip it for write-once transports (push payloads, QR links, one-shot requests):
> "Can an older build overwrite this document after a newer build has written it?"

If yes, `minWriterVersion` rises with the change. If unknown: **assume yes** for anything under `users/`, `links/` or any Firestore path; assume no for transport payloads.

Ask nothing else. Everything below is derived.

## Step 3 — Pick the numbers

Let `S` be the current `SCHEMA_VERSION` of the format.

| Situation | schemaVersion | minReaderVersion | minWriterVersion |
|---|---|---|---|
| Additive only | MINOR + 1 | unchanged | unchanged |
| Breaking (removal, retype, changed meaning, uncertain rename) | MAJOR + 1, MINOR 0 | **raise to the new schemaVersion** | raise to the new schemaVersion |
| Additive, but an old writer would drop the new field on rewrite (question B = yes) | MINOR + 1 | unchanged | **raise to the new schemaVersion** |

Never lower a number that has shipped (invariant I3). Pre-release tokens require a declared expiry date (I9) — do not introduce one without it.

## Step 4 — Mechanic (do all of it; nothing here is optional)

1. **Constants beside the type.** Update `SCHEMA_VERSION` / `MIN_READER_VERSION` / `MIN_WRITER_VERSION` in the format's companion (or the file-level constants next to it). Never a literal at a call site — `ArchitectureFitnessTest.wireFormatVersionsComeFromNamedConstants` fails on that.

2. **Every mirror of that constant.** Search the repo for the same protocol on another side:
   - `workers/*/src/contract/*.ts` — the Worker's copy. `ArchitectureFitnessTest.kotlinAndTypeScriptAgreeOnThePushWireVersion` compares these and fails on drift.
   - `firestore.rules` — `maxAcceptedReaderVersion()` is the ceiling above which a document would lock every reader out. Raise it when `minReaderVersion` rises, or the new documents are refused on write.
   - Bundled JSON assets under `app/src/main/assets/` that declare the same schema.

3. **Renames carry `@JsonNames("<old>")`** so existing documents still parse (`wire-format.md` §5). A removed field name is retired permanently — never reused for a different meaning.

4. **Fixtures.** Add a golden fixture at the new version; **keep every older one** — the backward-compat test reads them and is the only proof an old document still opens. Never edit an existing golden file to make a test pass; that deletes the evidence.

5. **Security rules.** If the format lives in Firestore, its monotonic guard must compare through `versionOrder()`, never raw `>=` — string comparison orders `"10.0"` below `"9.0"` and would permit the rollback the guard exists to block.

6. **Checks.** Run, in this order:
   ```
   ./gradlew fitnessCheck
   ./gradlew :core:testMockBackendDebugUnitTest :core:testRealBackendDebugUnitTest
   cd firestore-tests && npm test      # if firestore.rules changed
   cd workers/<name> && npm test       # if a Worker contract changed
   ```

## Step 5 — Report

State in one or two lines: what changed, which class it fell into, the new numbers, and every file touched as `file:line` links. If question A was answered "different meaning", say so explicitly — that is the fact a future reader needs and the diff will not show.

---

## Traps that have actually bitten this repo

Each of these shipped, was found later, and cost real debugging. They are why the mechanic above is not optional.

- **A hand-assembled document is invisible to the compiler.** `identity-links` and `users` are built as Firestore maps, not from a typed class. They kept writing the pre-conversion integer through all of TASK-138 while the rules already demanded a string, and account creation was rejected at write time with every test green. If your format is written as a map or a `buildJsonObject`, no type change will remind you.
- **A value that loses its type loses its shape.** `payload["schemaVersion"] = schemaVersion` put a `WireVersion` object into `Map<String, Any>`; Firestore stored a nested map, the reader's `as? String` produced null, and the rules compared two maps. Always `.toString()` at a wire boundary.
- **`encodeDefaults = false` silently drops the version.** Without `@EncodeDefault(EncodeDefault.Mode.ALWAYS)` on all three fields, a document ships with no version at all.
- **A green build can mean the rule never ran.** Gradle infers a test task's inputs from its classpath; files scanned at runtime must be declared in `app/build.gradle.kts`. Two rules passed on deliberately broken code before that was fixed.
- **A rules change can lock everyone out.** `minReaderVersion` above what the rules accept makes a shared document unreadable by every device including its owner, with no client-side repair. The ceiling exists for that.

## Related skills

- [`wire-format`](../wire-format/SKILL.md) — the rules and where they live. Read that for "why"; read this for "how".
- [`checklist-wire-format`](../checklist-wire-format/SKILL.md) — audits a **spec** for compliance, not a code change.
