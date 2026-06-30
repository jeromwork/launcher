# Checklist: modular-delivery — TASK-65 (re-run after revised model)

Applied: 2026-06-30 (2nd pass).

## Scope

- [x] CHK001 Form-factor classification — **yes**. Agnostic (handheld now, designed for cross-app).
- [x] CHK002 N/A (agnostic).
- [x] CHK003 No SDK/platform leak в shared — **yes**, защищено `ExtractionReadinessDetector`.

## Module placement

- [x] CHK004 No new vendor SDK in Core — **yes**.
- [x] CHK005 N/A (no new Gradle module).
- [x] CHK006 Regret condition for foundation in shared — **yes**. Extraction trigger = TASK-27/28.

## Profile / preset declaration

- [x] CHK007 requiredModules / optionalModules — **yes** (FR-001).
- [x] CHK008 Schema bump + backward-compat — **yes**. preset.json schemaVersion=1, wizard.manifest bump + migration FR-002.
- [x] CHK009 Base app loads без новых modules — **yes**.

## One-way doors

- [x] CHK013 Irreversible without exit ramp — **partial**.
  - **NEW one-way door**: `PresetRef` schema (`uid: String, version: Int`). Once shipped — Map storage key. Migration writer if structure changes — exit ramp documented (rule 5).
  - **Naming `uid` vs `id`**: owner explicit choice 2026-06-30, rationale в Clarification #13. Locked.
- [x] CHK014 Vendor disappears test — **yes**.
- [x] CHK015 N/A (no server workaround).

## Anti-bloat

- [x] CHK016-018 — **yes**. No module for single class; foundation в existing modules; future split = regret condition.

---

**Total**: 14/14 ✓
**Red-only summary**: modular-delivery: 14/14 ✓ (1 partial — PresetRef schema is new one-way door, exit ramp via migration writer documented).
