# Checklist: wire-format — Spec 005

**Skill**: `checklist-wire-format` | **Status**: ⚠ PASS-WITH-CAVEATS (16/18)

## Schema version

- [x] CHK001 `schemaVersion: 1` mandatory in `Action` from first commit (§4.1.1).
- [x] CHK002 `schemaVersion` parsed first per §7.1 step 1 (`if action.schemaVersion > SUPPORTED_SCHEMA_VERSION → Failure`).
- [x] CHK003 `SUPPORTED_SCHEMA_VERSION` constant in `Action.kt` companion — single source of truth.

## Backward compatibility

- [x] CHK004 Spec 003 mock JSON readable via `migrateLegacyAction` (§4.1.5).
- [x] CHK005 Adding fields fine (kotlinx-serialization defaults).
- [x] CHK006 Renames require migration written before breaking change (§9 NFR row).
- [x] CHK007 Migration scoped to `migrateLegacyAction(json): Action` — single function, removed in spec 006 per Clarification C5.

## Forward compatibility

- [x] CHK008 Reading newer `providerId` graceful per Clarification C1: `ProviderUnavailable(UnknownInThisVersion)`, not crash.
- [x] CHK009 Unknown `payload.kind` (sealed class discriminator) yields `Failure("unknown kind")` per §7.1; this is in scope of the same `Failure` channel as missing handler.

## Tests

- [x] CHK010 Roundtrip test for every `ActionPayload`-вариант — `ActionWireFormatTest.allPayloadVariantsRoundtrip()` (§4.1.5).
- [x] CHK011 Backward-compat test: spec 003 mock JSONs read via `migrateLegacyAction`, asserted equal to manually-constructed `Action` (§4.1.5).
- [x] CHK012 Fixtures stored as files in `core/src/commonTest/resources/fixtures/action-wire-format/` per Clarification C4.

## Persistence specifics

- [⚠] CHK013 SharedPreferences/DataStore — only one transient cleanup access (§5.3 deletes `launcher.communication.return_context`). **Open**: spec doesn't define namespace for any *new* SharedPreferences keys. None planned in 005, but plan.md must explicitly say "no new persistent keys introduced" or define namespace if any.
- [x] CHK014 N/A — no SQLDelight in this spec.
- [x] CHK015 ReturnContextStore removal: cleanup written + grep-anchor comment per §5.3 (`// FEATURE-RETURN-CONTEXT-REMOVED-IN-SPEC-005`).

## Deep-link / QR / exported config

- [x] CHK016 N/A — spec 005 does not introduce deep-link/QR consumers; only Android adapter consumes external intents.
- [x] CHK017 N/A.

## Contract folder

- [⚠] CHK018 `contracts/` folder structure exists in repo precedent (spec 002), but spec 005 currently doesn't list a `contracts/action-wire-format.md` file. **Open**: speckit-plan must create `contracts/action-wire-format.md` with semver, breaking-change policy, and link to roundtrip fixture.

---

## Open items

- **CHK013** — plan.md must state explicitly: "no new persistent SharedPreferences/DataStore keys in spec 005 (only transient cleanup of legacy)".
- **CHK018** — plan.md must produce `contracts/action-wire-format.md` (semver `1.0.0`, breaking-change → major bump, link to fixture path).
