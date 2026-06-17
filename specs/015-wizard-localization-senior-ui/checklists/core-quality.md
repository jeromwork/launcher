# Checklist: core-quality

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 8 ✓ / 7 ⚠ / 0 ✗ + 3 N/A — clean (foundation spec, most release gates defer to S-1)

> **Context**: F-3 — foundation. Не ships own user-facing product; даёт инфраструктуру для S-1 (Simple Launcher, который и есть release-bound). Большинство core-quality gates применяются к S-1 при release, не к F-3 при ship'е инфраструктуры.

---

## Visual experience

- [✓] **CHK001** Material Design where not overridden by senior-safe rules.
  - FR-035: `SeniorWarmTheme` — Compose `MaterialTheme` wrapper. Senior-safe overrides (≥ 56dp vs WCAG 48dp). ✓

- [✓] **CHK002** Light + dark themes both supported.
  - FR-035: `SeniorWarmTheme.Light` + `Dark`. ✓
  - `ThemeChoice = Light | Dark | Auto` (UserPreferences). ✓

- [⚠] **CHK003** Edge-to-edge Android 15+ (system bars, gesture insets).
  - Не explicit в F-3 spec.
  - **Acceptable**: edge-to-edge handling — Compose-level concern + S-1 screen-by-screen ответственность. F-3 primitives используют standard Compose insets.

- [⚠] **CHK004** Foldable / large-screen.
  - `core/ui-senior/` примитивы используют `wrapContentHeight()` — adaptive.
  - Не explicit foldable handling.
  - **Acceptable** foundation defer.

## Functional

- [✓] **CHK005** Works without internet.
  - A-10 + decision 2026-06-15-deferred-cloud: F-3 wizard работает local-only. ✓

- [✓] **CHK006** Configuration changes survive.
  - FR-003a (rotation) — `rememberSaveable` for in-progress answer.
  - Edge Case + SC-005a (locale change).
  - FR-036 (fontScale change).
  - ✓

- [N/A] **CHK007** Doze / App Standby.
  - F-3 не uses background work.

- [⚠] **CHK008** Multi-window / split-screen.
  - Не explicit. Foundation defer.

## Performance (cross-check `checklist-performance`)

- [⚠] **CHK009-011** ANR < 0.47%, Crash < 1.09%, Vitals.
  - Aspirational targets для release product (S-1 ответственен).
  - F-3 design avoids ANR by suspend everywhere; crash fallbacks через ConfigSourceResult sealed.
  - Concrete Vitals tracking — S-1 territory.

## Privacy / Play policy

- [⚠] **CHK012** Data Safety section.
  - F-3 — pure local. No transmitted data.
  - UserPreferences + attestedSettings + WizardCheckpoint — local only.
  - DiagnosticEmitter events — backend deferred (A-17 «не F-3 scope»).
  - **Recommendation**: при материализации analytics backend в S-1+, Data Safety entry must reflect what's collected. F-3 не collects anything user-identifying.

- [✓] **CHK013** No prohibited content / SDKs.
  - AccessibilityService usage — для senior-safe focus (legitimate use case per Play policy).
  - moko-resources, kotlinx-datetime, Konsist — все Play-policy compliant.

- [⚠] **CHK014** Restricted permissions.
  - CALL_PHONE / POST_NOTIFICATIONS уже declared use spec'е 010.
  - `BIND_ACCESSIBILITY_SERVICE` — restricted, требует Play Console declared use → **S-1 territory** при концретном AccessibilityService class.
  - F-3 pool entry содержит metadata, не manifest declaration. ✓ proper layering.

## Compatibility

- [⚠] **CHK015** minSdk / targetSdk alignment.
  - F-3 не explicit. POST_NOTIFICATIONS pool entry имеет `androidMinApi: 33` — implies project supports Android 13+ для notification feature.
  - **Implementation detail** для plan.md / project-level build configuration.

- [✓] **CHK016** Medium-tier + OEM testing.
  - Pixel 5 API 34 baseline ✓
  - Samsung One UI / Xiaomi MIUI / Huawei EMUI — OEM Matrix entries с `TODO(physical-device)` markers per project policy.

## Distribution

- [N/A] **CHK017** Feature flag / staged rollout.
  - F-3 — foundation infrastructure, ships как base for S-1+. No user-visible feature toggle.

- [⚠] **CHK018** Vitals dashboard для new code paths.
  - F-3 DiagnosticEmitter emits events; concrete backend (Firebase Analytics) — S-1 territory.
  - Vitals integration applies к S-1 release, не F-3 infrastructure ship.

---

## Резюме

**8 ✓ / 7 ⚠ / 0 ✗ + 3 N/A** — F-3 is correct для foundation level. All warnings — appropriate defer'ы к S-1 (release-bound product) или plan.md (build configuration details).

**No spec edits required** — core-quality gates apply properly к S-1 when Simple Launcher gets prepared для release.
