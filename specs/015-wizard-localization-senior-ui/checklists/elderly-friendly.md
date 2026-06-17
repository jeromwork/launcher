# Checklist: elderly-friendly

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16 (post accessibility fixes)
**Verdict**: 15 ✓ / 5 ⚠ / 2 ✗ — два real gaps (visual progress indicator, system Back behavior)

> **Article VIII §7**: «If a design is elegant for experts but confusing for elderly users, the elderly-friendly design wins by default». F-3 — primary persona spec.

---

## Visual

- [✓] **CHK001** Body text ≥ 18sp.
  - FR-034: `SeniorBodyText` ≥ 18sp. `SeniorTitleText` ≥ 24sp. ✓

- [⚠] **CHK002** Primary action labels ≥ 16sp.
  - FR-034 не explicit задаёт button text baseline.
  - **Recommendation**: дополнить FR-034 — `SeniorButton` text baseline ≥ 18sp (consistent с body text для elderly). Опционально.

- [✓] **CHK003** Tap targets ≥ 56dp.
  - FR-034 explicit. ✓

- [⚠] **CHK004** Spacing между interactive elements ≥ 16dp.
  - Compose default Material spacing ≈ 8dp.
  - F-3 spec не explicit specifies inter-element spacing.
  - **Recommendation**: дополнить FR-034 — minimum 16dp spacing между SeniorButton instances.

- [✓] **CHK005** Contrast ≥ 4.5:1 universally.
  - FR-035 ≥ 7:1 (AAA). ✓

## Cognitive load

- [✓] **CHK006** ONE primary action per screen.
  - Wizard step pattern: «Далее» primary, «Назад» secondary, selection controls между. ✓ standard pattern.

- [✗] **CHK007** Wizard ≤ 3 steps OR explicit progress indicator.
  - **VIOLATION (partial)** — F-3 wizard length определяется manifest (S-1 / S-2 могут иметь > 3 шагов).
  - **Audio progress** ✓ через FR-008b (just added): TalkBack announces «Шаг N из M».
  - **Visual progress** — **не explicit**: progress dots / bar / counter в top of step screen.
  - Спека 010 FR-008a уже устанавливает паттерн «текст "Шаг N из M" + visual dots/bar над content» для первоначального wizard'а.
  - **Critical для elderly users**: они не запоминают, сколько шагов осталось.
  - **Fix**: добавить FR-008c. См. Issue EF-1.

- [✓] **CHK008** No hidden gestures.
  - Wizard использует explicit «Назад» / «Далее» buttons. No swipes / long-press для primary navigation. ✓

- [✓] **CHK009** Plain language.
  - All button labels plain («Далее», «Назад», «Понял», «Понятно», «Да я сделал», «Нет»). No negation в confirmations. ✓

- [✓] **CHK010** Default values pre-filled.
  - Theme = Auto, fontScale = system, language = system — defaults that mostly «just work» for elderly без selection. ✓

## Predictable navigation

- [✓] **CHK011** Consistent button placement.
  - «Назад» left, «Далее» right — universal в wizard. ✓ implicit pattern.

- [✗] **CHK012** Back behaviour matches user expectation.
  - **VIOLATION** — System hardware/gesture Back button behavior не explicit specified.
  - **Senior expectation**: Back = previous step (одна позиция вверх). НЕ exit wizard, НЕ restart wizard.
  - **Fix**: добавить FR-008d. См. Issue EF-2.

- [✓] **CHK013** No surprise re-routing.
  - Step types fixed по manifest; нет dynamic action repurposing. ✓

## Error recovery

- [⚠] **CHK014** Errors have clear recovery action.
  - SystemSettingStep denial → FR-008a explicit «Попробовать снова / Пропустить / Открыть настройки приложения». ✓
  - Hard-fail IncompatibleVersion → «Понятно» button — но recovery action **не explicit** (что бабушка делает после? Закрыть app? Wait for update?).
  - ConfigSource ParseError → user-visible error без explicit recovery action.
  - **Recommendation**: clarify recovery action в FR-016: «после "Понятно" app закрывается через `finishAffinity()`; пользователь должен переустановить или обновить app».

- [⚠] **CHK015** No error states requiring app restart.
  - IncompatibleVersion — actually требует app update (restart implied). Это edge case (bundled-only MVP не должен hit'нуть).
  - **Acceptable** для edge case.

- [N/A] **CHK016** Destructive actions confirmation.
  - F-3 wizard не has destructive actions (no delete / remove).

## Sensory

- [✓] **CHK017** Animation reduced-motion-aware.
  - FR-036a (just added): respects `Settings.Global.ANIMATOR_DURATION_SCALE`. ✓

- [✓] **CHK018** No reliance on color alone.
  - F-3 не уses color-only signaling. SeniorWarmTheme — colors для background/text only. Hint overlay, dialog — shape + text + color. ✓
  - (Спека 010 уже устанавливает pattern shape + color + text для `!` indicator.)

## Time

- [✓] **CHK019** No timed challenges.
  - F-3 wizard не has timed UI. ✓
  - (Challenge gate timing — это спека 010, не F-3.)

- [N/A] **CHK020** Session timing — F-3 не handles sessions.

## Acceptance evidence

- [⚠] **CHK021** Senior-safe metrics в US acceptance criteria.
  - US-4 explicit cites senior-safe (≥ 56dp tap target, fontScale handling). ✓
  - US-1/2/3/5/6/7 — concentrate на functional aspects, не senior-safe metrics. Acceptable: senior-safe applies universally через `core/ui-senior/` primitives которые US-4 покрывает.

- [⚠] **CHK022** Manual elderly-simulation walkthrough.
  - Local Test Path не explicit lists manual walkthrough.
  - Cannot-test-locally gaps упоминают TalkBack physical-device TODO.
  - **Recommendation**: добавить как post-implementation gap: «Manual elderly-simulation walkthrough (squinting, slow tapping, voice-over) при первой S-1 alpha».

---

## Issues & fixes

### Issue EF-1 — Visual progress indicator (CHK007, severity High)

**Problem**: elderly не помнят, на каком шаге wizard'а. Audio progress (FR-008b) hammers только TalkBack users; sighted elderly без TalkBack теряются.

**Fix**: добавить FR-008c:
```
- **FR-008c (visual progress indicator)**: Каждый wizard step screen MUST содержать
  visual progress indicator над content: текст «Шаг N из M» (≥ 18sp) + visual dots
  (filled = completed, current step highlighted, remaining = empty) ИЛИ progress bar.
  Indicator updates после каждого completed / skipped шага. Применяется ко всем
  app-families (Simple Launcher, Admin App, future TV). Visual + audio (FR-008b)
  complement друг друга: TalkBack users get audio announcements, sighted users
  see visual progress.
```

### Issue EF-2 — System Back behaviour (CHK012, severity Medium)

**Problem**: senior expectation для system Back button: «previous step». Если Back exits wizard mid-way → лестница frustrated re-start.

**Fix**: добавить FR-008d:
```
- **FR-008d (System Back behaviour)**: System hardware/gesture Back button во время 
  wizard'а MUST вести себя identично к «Назад» button (FR-007 `canGoBack`):
  - На шаге N > 0 → возврат к шагу N-1 (preserve answers per FR-003a + checkpoint).
  - На шаге 0 → no-op (НЕ exit wizard, не restart). Display brief toast: «Чтобы выйти, 
    закройте приложение». User должен явно kill app для exit (это design choice — 
    wizard first-run is mandatory before launcher функционал available).
```

### Issue EF-3 (Optional) — Recovery action для hard-fail (CHK014)

**Fix**: дополнить FR-016:
```
... После tap «Понятно» app закрывается через `finishAffinity()`. User должен 
обновить приложение через Play Store (Android system pattern). Hard-fail dialog
text MUST содержать: «Версия конфигурации несовместима. Обновите приложение в 
Play Маркет, чтобы продолжить».
```

---

## Резюме

**15 ✓ / 5 ⚠ / 2 ✗** — два critical fix'а + один minor:

- **EF-1**: visual progress indicator (FR-008c) — критично для sighted elderly.
- **EF-2**: System Back behavior explicit (FR-008d) — prevents frustrated wizard re-starts.
- **EF-3 (optional)**: recovery action для hard-fail dialog.

Applying EF-1 + EF-2 inline; EF-3 optional.
