# Modular Delivery — spec 014

Generated: 2026-05-29.

Article V (modular delivery), Article VII §8 (profile-driven), Project-Specific Direction §6 (form-factor variants).

## New modules

- [x] **CHK001** F-014 не вводит новых gradle modules. Reuses: `core` (domain), `app` (presentation), `data` (ConfigEditor adapter).
- [x] **CHK002** Justified: ни одна F-014 capability не требует module boundary (per Article V §3 criteria — нет independent enable/disable, ownership boundary, etc.).

## New presets / profiles

- [x] **CHK003** F-014 вводит **EditUiProfile** abstraction (AdminProfile / SeniorProfile) per Q2. Это **internal presentation concept**, не deliverable preset. Не trogает modular delivery (живёт в `app` module).
- [x] **CHK004** Profile selection (FR-008) by target preset, not by user — это compositability в presentation layer, not module split.

## Vendor SDKs / form-factor specific

- [x] **CHK005** F-014 не вводит form-factor specific код (no Leanback / TIF / Tizen / CarAppService / Wear Compose).
- [x] **CHK006** No new vendor SDKs.

## Core bloat для one form factor

- [x] **CHK007** F-014 polishes existing form factors (mobile phone admin + senior). Doesn't bloat for new form factor. PASS.

## Exit ramp на F-2 (Capability Registry)

- [x] **CHK008** FR-008a + FR-008b — explicit exit ramp на F-2. Когда Capability Registry land'ится:
  - `selectProfile(presetId: String)` → `selectProfile(capabilities: Set<Capability>)`.
  - Built-in enum stays as pre-packaged composition.
  - Custom presets (через TODO-FUTURE-PRODUCT-006 Configurator) — explicit refuse в F-014, поддержка добавляется в F-2.
- [x] **CHK009** Vision documented в [docs/product/future/ecosystem-vision.md §Compositable Presets](../../docs/product/future/ecosystem-vision.md). One-way door avoided through explicit phasing.

## Profile depending on modules

- [x] **CHK010** AdminProfile and SeniorProfile — both **in-app** modules (no separate APK or dynamic feature). Justified: presentation rules — small code, no SDK weight.
- [x] **CHK011** Future TODO-UX-027 (Widget rendering) — могут стать отдельным module (AppWidgetHost dependency). Spec defers. PASS.
- [x] **CHK012** Future TODO-UX-028 (Action — SOS/phone/flashlight) — могут стать отдельным module. Spec defers. PASS.

## Bundle/install size

- [x] **CHK013** SC-008 ≤300 KB APK delta. F-014 fits in base APK без dynamic feature.

## Backlog hygiene

- [x] **CHK014** F-014 явно регистрирует backlog items:
  - TODO-UX-025: Tutorial / onboarding overlay.
  - TODO-UX-026: Recently deleted / Trash bin.
  - TODO-UX-027: Widget tile-type real rendering.
  - TODO-UX-028: Action tile-type implementation.
  - TODO-FUTURE-PRODUCT-006: Professional Configurator (B2B).
  - TODO-FUTURE-SPEC-007: Named config export/import.
  - TODO-FUTURE-SPEC-008: Auto-GC orphan configs.
- [x] **CHK015** Adjacent decisions captured (Q1 clarification section).

## Open items

Нет major. F-014 — minimum viable architecture per CLAUDE.md rule 4: добавляет только что нужно сейчас, остальное explicit deferred to backlog.

**Verdict**: PASS (15/15 ✓). Spec exemplifies modular delivery discipline — no new modules, explicit exit ramps, all form-factor / profile decisions documented as future TODOs.
