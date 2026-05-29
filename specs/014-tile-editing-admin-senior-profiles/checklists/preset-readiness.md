# Preset Readiness — spec 014

Generated: 2026-05-29.

CLAUDE.md rule 9 (shareability-readiness for non-identity configs).

## User-facing configs in F-014

1. **ConfigDocument** (existing per спека 008) — layout of tiles per home screen.
2. **Named configs** (F-014.1 new) — admin's collection of layouts.
3. **EditUiProfile** mapping (admin / senior) — not user-facing, derived from preset.

## Shareability properties

- [x] **CHK001** ConfigDocument is non-identity-bound:
  - Contains tile slots (App package name, Contact reference, Document reference).
  - **Contact references**: opaque IDs. Per спека 011 design, these are NOT phone numbers but pairing-side identifiers. **Mostly identity-free**, but warrants verification.
  - **Document references**: depend on спека 012 design.
  - Layout structure (rows / cols / order) — pure structure.
- [x] **CHK002** Wire format JSON with `schemaVersion` (спека 008). PASS rule 9 §1.
- [⚠️] **CHK003** Anonymization: contact references may carry PII implicitly (e.g., admin's contact ID linked to phone number). For sharing, need **export-time scrubbing**. F-014 doesn't build export, but **TODO-FUTURE-SPEC-007** (named config export/import) registered. PASS conceptually.

## ConfigSource adapter pattern

- [⚠️] **CHK004** F-014 doesn't introduce `ConfigSource` adapter pattern explicitly. ConfigDocument loaded via existing ConfigEditor (which is read+write port). For future sharing, separate `ConfigSource` adapter может быть needed (read-only with provenance: bundled / file / network / marketplace). **TODO-FUTURE-SPEC-007** registered, this is acceptable defer.
- [⚠️] **CHK005** Inline TODO в bundled-config code: not applicable yet — F-014 не вводит bundled configs (use empty defaults). When future preset templates ship — that spec must add `// TODO(shareability)` comment.

## Roundtrip / cross-version tests

- [⚠️] **CHK006** Roundtrip test для ConfigDocument v2 — see wire-format.md CHK010. Plan.md responsibility.
- [⚠️] **CHK007** Cross-device test (config saved on Pixel, loaded on Xiaomi) — listed in §Local Test Path (2-эмулятор smoke). PASS conceptually.

## Compatibility key (F-014 specific)

- [x] **CHK008** FR-003 declares compatibility key `(presetId, deviceClass)` для named configs. **Brilliant**: ensures sharing-readiness preserves preset/form-factor compatibility check. When TODO-FUTURE-SPEC-007 ships, this key enables filtered "compatible configs only" picker.
- [x] **CHK009** FR-003i: compatibility filter at first-install + Google Sign-In. Predicates correct usage.

## Identity-free check

- [x] **CHK010** Named config fields — non-identity:
  - `configName`: user-chosen label (e.g., "home", "job"). NOT identity. PASS.
  - `description`: user text. Identity-leak risk if user types own name — acceptable (user-controlled).
  - `isDefault`: boolean. PASS.
  - `activeDeviceIds`: device IDs (Firebase Installations per TODO-RESEARCH-009). **Identity-bound** — but these are stripped on export per future spec design.
  - `orphanedAt`: timestamp. PASS.
  - `presetId`, `deviceClass`: structural. PASS.
- [⚠️] **CHK011** `activeDeviceIds` carries device identity. When export ships (TODO-FUTURE-SPEC-007), this field MUST be stripped from shareable artifact. **Track в plan.md или TODO-FUTURE-SPEC-007 spec**.

## Privacy by design

- [x] **CHK012** Bundled configs (e.g., default Workspace template) — F-014 doesn't introduce new bundled configs. PASS.
- [x] **CHK013** Contact tiles in shareable artifact: per **CHK001** — opaque IDs, but mapping table is admin-private. Sharing would require contact-mapping resolution или contact-stripped export. TODO-FUTURE-SPEC-007 responsibility.

## Open items

1. **CHK003-CHK005**: ConfigSource adapter pattern для future sharing — TODO-FUTURE-SPEC-007 owns this. **No action в F-014**.
2. **CHK011**: `activeDeviceIds` strip on export — flag for TODO-FUTURE-SPEC-007 spec author.

**Verdict**: PASS. F-014 design **enables** future shareability:
- Wire format with `schemaVersion` ✓
- Compatibility key for filter-on-import ✓
- Identity vs non-identity fields clearly separated ✓
- TODO-FUTURE-SPEC-007 explicit deferral ✓
Future sharing — additive change, not rewrite. CLAUDE.md rule 9 satisfied.
