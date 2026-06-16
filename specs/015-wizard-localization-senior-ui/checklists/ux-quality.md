# Checklist: ux-quality

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 18 ✓ / 3 ⚠ / 1 ✗ — один real gap (double-tap protection для SeniorButton)

> **Context**: F-3 — foundation primitives, не concrete app-family screens. S-1 / S-2 provide reálne wizard UI design. F-3 verified на step-framework level.

---

## Completeness — coverage of screens

- [⚠] **CHK001** Every user-facing screen listed.
  - F-3 listed **step types** (LanguageStep, ThemeStep, TextSizeStep, GridSelectionStep, ScreenLayoutPickerStep, TileSetPickerStep, PairingStep stub, TutorialHintStep, SystemSettingStep) — каждый renders своё screen.
  - **Дополнительные screens / overlays**:
    - Hard-fail dialog для IncompatibleVersion (FR-016) ✓
    - Self-attest dialog для Indeterminate settings (FR-058) ✓
    - Rationale screen для denied permissions (FR-008a) ✓
    - Tutorial hint overlay (US-7) ✓
  - Concrete screen mockups — S-1 / S-2 territory (per OUT-001).
  - **Acceptable** foundation defer.

- [⚠] **CHK002** UX states per screen.
  - Per-step states not explicit (loading, success, error).
  - Implicit: каждый step имеет initial render → user interaction → answer captured (commits на «Далее»).
  - **Recommendation**: оставить implicit для foundation, S-1 explicit per concrete screen.

- [✓] **CHK003** Navigation transitions.
  - US-1 Acceptance #1-3: forward, back, completion. ✓
  - Deep-link entry — N/A (first-run only).
  - Recreation — FR-003 + FR-003a explicit. ✓
  - Self-attest re-check — FR-059. ✓

- [✓] **CHK004** Cross-cutting overlays.
  - TutorialHintManager overlay с «Понял» button (US-7).
  - Hard-fail dialog с «Понятно» button (FR-016).
  - Self-attest dialog с «Да / Нет» buttons (FR-058).
  - Rationale screen с «Попробовать снова / Пропустить / Открыть настройки приложения» (FR-008a).

## Clarity — terminology and rules

- [✓] **CHK005** UX terms unambiguous.
  - «Wizard», «step», «manifest», «pool», «setting», «self-attest», «attestation», «mechanism», «detection» — все defined в Key Entities + Clarifications.
  - «App-family / layout-grid / tile.set / screen.layout / wizard.manifest» — fixed via glossary (no «preset» ambiguity).

- [✓] **CHK006** Vague qualifiers operationalised.
  - Performance: SC-001a (300ms), SC-005a (500ms), SC-006 (max fontScale), SC-011 (1.5 сек) — explicit numbers.
  - Few casual usages в TL;DR («простая и правильная архитектура») — acceptable в TL;DR per requirements-quality CHK007.

- [✓] **CHK007** Action vocabulary explicit.
  - «Тап на тайл», «нажимает «Далее»», «system back», «overlay-tap «Понял»» — explicit. ✓ Нет vague «interact».

- [✓] **CHK008** Button labels exact strings.
  - «Далее», «Назад», «Понял», «Понятно», «Да я сделал», «Нет», «Попробовать снова», «Пропустить», «Открыть настройки приложения». ✓

## Consistency

- [✓] **CHK009** In-Scope ↔ FR alignment.
  - 7 USes map ↔ 70+ FRs cover all stated capabilities. OUT-list covers exclusions.

- [✓] **CHK010** Confirmation policy consistent.
  - Wizard step transitions: НЕТ confirmation, just «Далее» (senior-friendly default). ✓
  - Self-attest: IS confirmation (FR-058). ✓
  - Hard-fail: IS confirmation («Понятно» = acknowledge, no choice). ✓
  - Denial rationale: IS choice (retry/skip/app settings). ✓

- [✗] **CHK011** Multi-tap / accidental-double-tap protection.
  - **VIOLATION** — F-3 не explicit specifies debounce/double-tap protection в `SeniorButton`.
  - **Senior users — known risk**: tremor, slow motor response → могут случайно нажать «Далее» дважды → double-trigger transition / double-checkpoint write.
  - **Severity**: Medium. Senior-focused product **должен** debounce.
  - **Fix**: добавить FR-034a — debounce policy в SeniorButton.

## Acceptance — measurability

- [✓] **CHK012** Each US explicit Given/When/Then.
  - All 7 USes имеют 3-4 Given/When/Then scenarios.

- [✓] **CHK013** Success criteria measurable per UX moment.
  - SC-001a: cold start ≤ 300ms.
  - SC-005a: locale change render ≤ 500ms.
  - SC-006: max fontScale render без обрезки.
  - SC-013: self-attest button press → AttestationRecord saved.

- [✓] **CHK014** Returning-user UX.
  - FR-005: `wizardCompleted` flag — wizard НЕ показывается повторно при следующем launch. ✓ explicit.

## Coverage — alternative paths

- [✓] **CHK015** Negative path UX для primary action.
  - US-1 manifest read fail → FR-016 hard-fail.
  - US-2 process death → resume.
  - US-3 missing locale key → fallback chain.
  - US-4 extreme fontScale → SC-006 без обрезки.
  - US-5 lint guard → build fails.
  - US-6 breaking schema → hard-fail.
  - US-7 hint dismissed → persistent.
  - Permission denied → FR-008a.

- [N/A] **CHK016** Multiple entry points.
  - F-3 wizard — first-run only. Нет notification / deep-link / widget entry.

- [✓] **CHK017** Long-pause scenarios.
  - Process death — covered (FR-003 + US-2).
  - Long pause без kill — Activity recreation covered (FR-003a + SC-005a).

## Non-functional UX

- [✓] **CHK018** Accessibility → `checklist-accessibility` (next).
- [✓] **CHK019** Localization → `checklist-localization` (next).
- [✓] **CHK020** Diagnostic UX (telemetry visibility).
  - F-3 emits diagnostic events через DiagnosticEmitter (A-17) — **не user-visible**.
  - Analytics backend deferred to S-1+ (A-17).
  - User-visible diagnostic UX (например, «телеметрия включена» indicator) — N/A для F-3.

## Dependencies / assumptions

- [✓] **CHK021** UX doesn't depend on out-of-scope capabilities.
  - moko-resources (in scope), SystemSettingPort (in scope), Compose (in scope для ui-senior). ✓

- [✓] **CHK022** Mock-data limitations noted.
  - Test fixtures (`test-app-family.json`, `test-classic-6.json`, etc.) — explicit для тестов.
  - Production bundled `tile.set` / `screen.layout` / `wizard.manifest` — S-1 / S-2 territory per OUT-001/002.

---

## Issues & fixes

### Issue UX-1 — SeniorButton debounce (CHK011, severity Medium)

**Problem**: senior users (tremor, slow motor) могут случайно double-tap → double-trigger action.

**Fix**: добавить FR-034a:
```
- **FR-034a (SeniorButton debounce)**: `SeniorButton` и `SeniorIconButton` MUST implement
  click debouncing: повторный tap в пределах **500ms** после первого — игнорируется
  (no callback triggered). Это применяется по умолчанию для всех instances; opt-out
  через `Modifier.allowRapidClicks` для специфических use cases (например, wizard
  «Назад» где rapid navigation полезно). Rationale: senior users с tremor / slow motor
  response могут случайно double-tap; double-triggered action (двойной checkpoint write,
  двойной step transition) frustrating.
```

---

## Резюме

**18 ✓ / 3 ⚠ / 1 ✗** — один real fix:

- **UX-1**: SeniorButton debounce policy (FR-034a).

Остальные warning'и (per-step UX states, mockups) — appropriate foundation defer'ы к S-1 / S-2.

Applying UX-1 inline.
