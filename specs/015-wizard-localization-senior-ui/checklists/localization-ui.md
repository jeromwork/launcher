# Checklist: localization-ui

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 11 ✓ / 7 ⚠ / 1 ✗ — один real gap (per-primitive screenshot tests в 3 locales)

> **Context**: F-3 — foundation primitives. Concrete screens — S-1 / S-2 territory. UI-localization gates apply to `core/ui-senior/` primitives + wizard step rendering pattern.

---

## Length expansion

- [⚠] **CHK-UI-001** Layout flex/wrap vs fixed width documented per surface.
  - FR-034 SeniorButton: implicit `wrapContentHeight()`, width policy не explicit.
  - **Fix**: applying L-3 (optional из localization checklist) — добавить explicit `wrapContentWidth()` policy.

- [N/A] **CHK-UI-002** Max-character budget для fixed-width labels.
  - F-3 не has concrete labels — это S-1 territory. Pure primitives без max-char constraints.

- [✓] **CHK-UI-003** No `Text` `maxLines=1` без overflow handling.
  - US-4 Acceptance #2 explicit: long text wraps to 2 lines, **не** clipped.
  - F-3 primitives не set maxLines=1 by default.

- [✓] **CHK-UI-004** Multi-line button labels reserve 2 lines vertical space.
  - US-4 Acceptance #2 ✓.

## Mock-screenshots (minimum 3 locales)

- [✗] **CHK-UI-005** Mock-screenshots in 3 locales (EN, DE/RU, AR/HE).
  - **VIOLATION** — F-3 spec не explicitly requires per-primitive Compose preview screenshots в 3 locales.
  - SC-007 covers RTL screenshot test (ar-SA wizard render), но не expansion test (DE/RU long strings).
  - **Severity**: Medium. Без 3-locale screenshot tests, F-3 primitives могут shipped с layouts которые ломаются в DE/RU.
  - **Fix**: добавить SC-006a. См. Issue LU-1.

- [⚠] **CHK-UI-006** Longest-string locale без clipping.
  - Covered by SC-006 (max fontScale) + US-4 Acceptance, но не explicit для longest **language** strings.
  - Will be covered by LU-1 fix.

- [✓] **CHK-UI-007** RTL locale shows mirrored UI.
  - SC-007: ar-SA RTL screenshot — кнопка «Назад» справа, текст выровнен по правому краю. ✓

## RTL specifics

- [⚠] **CHK-UI-008** Directional drawables `autoMirrored = true`.
  - **Fix**: applying L-2 (optional из localization checklist) — добавить explicit autoMirrored policy в FR-034.

- [✓] **CHK-UI-009** Custom layouts RTL.
  - F-3 не uses custom Canvas / gestures. ✓ N/A.

- [N/A] **CHK-UI-010** Number / currency formatting locale.
  - F-3 не displays numbers / currency.

## Plural rule variations

- [✓] **CHK-UI-011** Count-driven labels via plurals.
  - FR-031e (just added): «Шаг N из M» использует moko-resources plurals. ✓

- [⚠] **CHK-UI-012** Layout reserves space для widest plural form.
  - Не explicit. Связано с CHK-UI-001/006.
  - Implicit покрыто `wrapContentWidth()` policy (L-3 fix).

## Line-height / vertical fit

- [⚠] **CHK-UI-013** Content-based height (no fixed-pixel heights).
  - F-3 primitives implicit `wrapContentHeight()`. Не explicit.
  - **Recommendation**: дополнить FR-034 — все примитивы используют `wrapContentHeight()`, не fixed-pixel.

- [⚠] **CHK-UI-014** AR/HI tall glyphs vertical padding.
  - Compose default line-height ≈ 1.5× font size — обычно достаточно.
  - F-3 не explicit задаёт line-height multiplier.
  - **Acceptable**: Compose default handles это; explicit override unnecessary для foundation.

## Senior-safe overlap

- [✓] **CHK-UI-015** Senior-safe min font + 30% expansion НЕ push action below tap target.
  - FR-034: SeniorButton ≥ 56dp **baseline**; `wrapContentHeight` ensures tap target grows c text. Senior-safe minimums preserved. ✓

- [✓] **CHK-UI-016** fontScale=2.0 tested.
  - SC-006 explicit. ✓

## Locale change at runtime

- [✓] **CHK-UI-017** Runtime locale change behaviour.
  - SC-005a (just added): wizard re-renders в новом языке за < 500ms. ✓

- [✓] **CHK-UI-018** No cached pre-rendered text bitmaps.
  - Compose не кэширует text bitmaps; re-renders на recomposition. ✓

## Translation deferral path

- [⚠] **CHK-UI-019** Translation deferral path declared.
  - F-3 generates все 11 локалей одновременно через AI pipeline (FR-031a). **Нет phased rollout** — все 11 ship simultaneously.
  - Human review deferred для AR/HI/ZH/JA/KK (OUT-005a) — но **strings всё равно shipped** (просто AI-generated quality).
  - CI fitness function policy clear: missing key в любой локали → fails build (FR-031).
  - **Acceptable**: phased rollout не applicable; quality-gating через OUT-005a explicit.

---

## Issues & fixes

### Issue LU-1 — Per-primitive screenshot tests в 3 locales (CHK-UI-005, severity Medium)

**Fix**: добавить SC-006a:
```
- **SC-006a (length expansion test)**: Compose preview screenshot tests для каждого 
  `core/ui-senior/` primitive (SeniorButton, SeniorIconButton, SeniorTextField, 
  SeniorBodyText, SeniorTitleText) MUST render в **трёх locale fixtures**:
  - **EN**: baseline short string («Save» / «Next»).
  - **DE**: long expansion («Speichern» / «Einstellungen anwenden») — ~30-40% длиннее EN.
  - **AR**: RTL + tall glyphs («حفظ» / «التطبيق»).
  Verification: 100% pass без clipped text, без overlapping siblings, без layout 
  collapse — verified в CI через Roborazzi или Paparazzi screenshot library.
```

### Issue LU-2 — Applying L-2 + L-3 from localization checklist

**Fix**: дополнить FR-034 (consolidated):
```
SeniorButton + SeniorIconButton MUST:
- Use `wrapContentWidth()` + `wrapContentHeight()` — adapt к translated label length 
  (RU/DE ~30-40% длиннее EN) и fontScale growth.
- Directional icons (back arrow в `start` slot, next arrow в `end` slot) MUST use 
  `autoMirrored = true` (Compose `ImageVector.autoMirrored` или drawable XML attribute) 
  для RTL auto-mirror в AR/HI locales.
- Default line-height = 1.5× font size (Compose default), которая accommodates 
  AR/HI tall glyphs без vertical clipping.
```

---

## Резюме

**11 ✓ / 7 ⚠ / 1 ✗** — два fix'а:

- **LU-1**: SC-006a про screenshot tests в 3 locales (EN/DE/AR) для каждого primitive.
- **LU-2**: applying L-2 + L-3 (autoMirrored + wrapContentWidth + line-height) consolidated в FR-034.

Applying inline.
