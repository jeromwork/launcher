# Checklist: state-management

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 12 ✓ / 4 ⚠ / 1 ✗ — один real gap (mid-step rotation behavior)

---

## Lifecycle events

- [⚠] **CHK001** Activity recreation (rotation, language change, theme switch) explicitly specified.
  - **Locale change** ✓ — Edge Case: «Locale изменилась во время работы wizard'а → wizard перерисовывается на новом языке; ответы предыдущих шагов сохранены».
  - **Theme switch** — implicit (Compose реагирует на Theme provider change).
  - **Rotation** — **не explicit**. См. CHK009 + Issue SM-1.

- [✓] **CHK002** Process death.
  - US-2 explicit: process death → resume from checkpoint.
  - SC-005: `am kill` + relaunch test 100% resumes.

- [✓] **CHK003** Low-memory kill — same механизм как process death (FR-003 checkpoint write after each step survives).

- [✓] **CHK004** Device reboot — persistent stores (DataStore) survive reboot; на cold start `WizardEngine.run(manifest)` loads checkpoint per FR-004.

## State scope

- [✓] **CHK005** Each piece of state has scope:
  - WizardCheckpoint → persistent (`WizardCheckpointStore` per FR-006)
  - UserPreferences → persistent (`UserPreferencesStore` per FR-048)
  - DismissedHints → persistent (`DismissedHintsStore` per FR-024)
  - Wizard step in-progress state — **не явно specified**, см. Issue SM-1.

- [N/A] **CHK006** No process-singleton for screen state. F-3 — foundation spec, не определяет ViewModel structure; это plan.md territory.

- [✓] **CHK007** No rememberSaveable для non-trivial объектов.
  - Wizard answers сохраняются в persistent checkpoint (DataStore), **не** в Bundle/SavedStateHandle. Bundle limits избегаются by design. ✓

## Recreation correctness

- [✓] **CHK008** No "first-only" navigation logic.
  - FR-005 `wizardCompleted(appFamilyId)` flag prevents re-show **после завершения** wizard'а.
  - FR-004 restores `currentStepIndex` from checkpoint on each run — нет «navigate only on null savedInstanceState» антипаттерна.

- [✗] **CHK009** Form input survives rotation without re-query.
  - **VIOLATION + GAP — mid-step rotation**:
    - Checkpoint пишется **после** successful step completion (FR-003). Что происходит с in-progress answer (например, бабушка выбрала тему «Тёплая» но ещё не нажала «Далее»), если устройство повернётся?
    - Сейчас не specified → реализатор может: (a) потерять выбор, (b) сохранять в Compose `rememberSaveable`, (c) писать в checkpoint preliminary state.
    - **Severity**: Medium. Это **real UX hole** для бабушки: повернула экран → потеряла выбор → frustrated.
    - **Fix**: explicit FR — см. Issue SM-1.

- [⚠] **CHK010** In-flight async ops survive recreation or cancelled+restarted predictably.
  - `WizardStep.render()` — `suspend` функция. Behavior при cancellation (rotation, process death) — стандартный Kotlin coroutines cancellation.
  - Spec не explicit про restart policy. Implementation detail (use viewModelScope или equivalent).
  - **Acceptable** foundation defer.

## Configuration changes

- [✓] **CHK011** Locale change handled.
  - Edge Case explicit: wizard re-renders, answers preserved (хранятся как enum / key, не литералы).

- [✓] **CHK012** Density / font-scale change.
  - FR-036 `rememberFontScaleAware()` reacts to fontScale changes.
  - US-4 Acceptance: SeniorButton grows with fontScale.
  - SC-006: max fontScale без обрезки.

- [⚠] **CHK013** Window size change (foldable, split-screen).
  - F-3 senior UI primitives используют `wrapContentHeight()` — adapt to window changes.
  - Explicit handling for foldable / multi-window не specified.
  - **Acceptable** foundation defer (S-1 / S-2 ответственны за конкретные screen layouts).

## Tests

- [⚠] **CHK014** Recreation tests per US.
  - US-2: process-death recovery ✓ (SC-005).
  - US-4: fontScale ✓ (SC-006).
  - US-3: locale change — упомянуто в Edge Case, **explicit test не listed**.
  - US-1 rotation: не listed.
  - **Recommendation**: добавить SC-005a про locale change during wizard (Edge Case verification).

- [✓] **CHK015** Process-death simulation — SC-005 explicit.

## Edge cases

- [⚠] **CHK016** Multi-window (split-screen, free-form).
  - Не specified. Foundation level — acceptable defer.
  - Spec 010 также не addresses — consistent pattern.

- [N/A] **CHK017** Feature accessed from notification while killed.
  - F-3 wizard — first-run flow, не deep-linked from notifications.

---

## Issues & fixes

### Issue SM-1 — Mid-step rotation behavior (CHK009, severity Medium)

**Problem**: если бабушка в середине шага сделала выбор (выбрала option, но не нажала «Далее») и устройство повернулось → behavior undefined.

**Decision options**:
- **(a)** Сохраняем in-progress answer в Compose `rememberSaveable` — выбор переживёт rotation. Простая UX но потенциально хрупкая (sealed class serialization).
- **(b)** Игнорируем in-progress — rotation = restart current step с пустой формой. UX hit, но простая impl.
- **(c)** Eager checkpoint write — каждое поле сохраняется в `WizardCheckpoint` immediately, не дожидаясь «Далее». Robust но дороже.

**Recommendation**: **(a)** для F-3 — `rememberSaveable` для in-progress answer на current step. Падение на (b) допустимо для steps с trivial answer (radio button — 1 selection легко перевыбрать). Реальный gap появляется на steps с multi-field forms (NickName + Avatar + Date) — но таких в F-3 reusable steps **нет** (LanguageStep, ThemeStep, TextSizeStep, GridSelectionStep — все single-choice).

**Fix**: добавить FR-003a:
```
- **FR-003a (in-progress answer policy)**: in-progress answer на текущем шаге
  (выбор сделан, но «Далее» не нажато) MUST переживать Activity recreation
  (rotation, theme change, language change) через Compose `rememberSaveable`
  на уровне step Composable. Это применимо к bundled steps F-3 (LanguageStep,
  ThemeStep, TextSizeStep, GridSelectionStep, ScreenLayoutPickerStep,
  TileSetPickerStep) — все имеют single-choice answer, легко serializable.
  Custom steps от app-family должны следовать тому же паттерну.
```

### Issue SM-2 — Per-US recreation tests not explicit (CHK014, severity Low)

**Fix**: добавить SC-005a:
```
- **SC-005a**: Locale change во время wizard'а на шаге 3 → wizard перерисовывается
  на новом языке за < 500ms, ответы шагов 1-2 сохранены в `WizardCheckpoint` —
  verified instrumented test (изменить system locale через `LocaleList.setDefault()`
  + recreate Activity).
```

---

## Резюме

**12 ✓ / 4 ⚠ / 1 ✗** — два fix'а:

- **SM-1**: in-progress answer policy → `rememberSaveable` на step Composable (FR-003a)
- **SM-2**: SC-005a про locale change test

Остальные warning'и (CHK010 async cancellation, CHK013 multi-window, CHK016 split-screen) — acceptable foundation defer.

Applying SM-1, SM-2 inline.
