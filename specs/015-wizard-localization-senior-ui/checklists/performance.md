# Checklist: performance

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 11 ✓ / 7 ⚠ / 1 ✗ + 1 N/A — один real gap (WizardEngine first-run cold-start budget)

---

## Startup

- [✗] **CHK001** Cold/warm start target per ADR-005.
  - **VIOLATION**: F-3 WizardEngine запускается на **first-run** cold start (US-1 Acceptance #4).
  - SC-011 говорит «не регрессирует» vs существующий baseline спеки 010 — но **не задаёт explicit budget** для самого wizard'а.
  - Если wizard.manifest parsing + checkpoint check + StringResolver init добавят 500ms — это значимая regression.
  - **Fix**: добавить SC-001a с explicit budget. См. Issue PF-1.

- [⚠] **CHK002** No new I/O / parsing on cold-start без budget.
  - WizardEngine cold-start path:
    - `WizardCheckpointStore.load()` — DataStore read (~10-50ms)
    - `ConfigSource.load(WizardManifest, ...)` — JSON parsing (~20-100ms для small manifest)
    - `StringResolver` init — moko-resources lookup (~30-80ms)
  - Все в `suspend` functions на `Dispatchers.IO`, не blocking main thread.
  - **Estimate**: 60-230ms total для wizard init на medium-tier device.
  - **Acceptable** при добавлении PF-1 budget.

- [⚠] **CHK003** Application.onCreate cost.
  - F-3 не специфицирует Application.onCreate hooks (DI module wiring). Implementation detail.
  - **Acceptable** foundation defer.

## Runtime — frames

- [⚠] **CHK004** Scrolling frame budget.
  - `TileSetPickerStep` может иметь scrollable список tile.set вариантов (если их > 5).
  - F-3 не задаёт explicit frame budget.
  - **Acceptable** foundation defer: `core/ui-senior/` Composable's responsibility; plan.md / S-1 разберётся когда реальная list ships.

- [✓] **CHK005** Animations duration.
  - OUT-018 explicit: «baseline animations (slide / fade); polished motion — design polish в S-1 / S-2».
  - Compose defaults (200-300ms) acceptable for senior-friendly transitions. ✓

- [✓] **CHK006** No sync I/O on main thread.
  - All ports — `suspend` functions, dispatchers managed by callers (typical viewModelScope on Default/IO). ✓
  - **Note**: `AndroidSystemSettingAdapter.status()` для AccessibilityService check (`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`) — это content provider read; должен быть on IO dispatcher (suspend interface гарантирует). ✓

## ANR risk

- [✓] **CHK007** User input blocking > 100ms.
  - Wizard «Далее» button → checkpoint write (async via suspend) + step transition (in-memory). Combined < 100ms expected. ✓
  - `SystemSettingPort.applyOrPrompt` → Intent.startActivity — synchronous Android API call but returns immediately. ✓

- [N/A] **CHK008** BroadcastReceiver — F-3 не uses.

## Background work

- [✓] **CHK009** New background tasks justified.
  - F-3 не declares background services, WorkManager jobs, или Application-scope coroutines. ✓

- [✓] **CHK010** Polling avoided. F-3 — event-driven (user-input). ✓

- [N/A] **CHK011** Event listeners — DiagnosticEmitter is emitter, not listener.

## Memory

- [⚠] **CHK012** Caches with size limit / invalidation.
  - `StringResolver` может cache resolved strings (moko-resources internal cache).
  - Spec не explicit задаёт cache policy.
  - **Recommendation**: добавить FR/note: «StringResolver MAY cache resolved strings внутри implementation; cache invalidated при locale change (через `LocaleProvider`); bounded by process lifecycle».
  - **Acceptable** foundation defer.

- [N/A] **CHK013** Bitmap/image — F-3 не loads images (icons via `iconKey` string; icon loading — `core/ui-senior/` или `app/` concern).

- [✓] **CHK014** Long-lived Activity/Context references.
  - All ports в commonMain — no Activity/Context references. ✓
  - `AndroidSystemSettingAdapter` в `:app/androidMain` — должен быть scoped properly (singleton through DI, не Activity-scoped). Implementation detail.

## APK / binary size

- [✓] **CHK015** APK delta.
  - SC-010: ≤ +1.5 MB. ✓ Explicit budget documented.
  - Components: 3 new modules + moko-resources runtime (~200 KB) + Konsist test deps (test-only, not APK) + kotlinx-datetime (small) + bundled JSONs (KB).
  - Estimate: ~500-800 KB. Comfortable margin vs SC-010 budget.

- [✓] **CHK016** Native libraries / large assets.
  - F-3 не adds native libs. moko-resources — pure Kotlin. ✓
  - Bundled JSONs — small (low KB).

## Measurement

- [⚠] **CHK017** Measurement method documented.
  - SC-001: «JVM unit-test for state-machine overhead < 500ms». Method = JUnit timing. ✓
  - SC-011: «cold start ≤ 1.5 сек». Method не explicit — instrumented test? Manual stopwatch? Macrobenchmark?
  - **Recommendation**: для SC-001a (PF-1) и SC-011 указать Macrobenchmark library (Android Vitals standard).

- [⚠] **CHK018** Perf checkpoint planned.
  - Спека 010 имеет `perf-checkpoint.md` artifact (см. `specs/010-*/perf-checkpoint.md`).
  - F-3 не listed `perf-checkpoint.md` в Cross-spec impact / tasks.md output.
  - **Recommendation**: добавить как deliverable когда `speckit-tasks` создаёт tasks.md.

## Battery

- [✓] **CHK019** Wake locks. F-3 не uses. ✓
- [✓] **CHK020** Alarms / Doze bypass. F-3 не uses. ✓

---

## Issues & fixes

### Issue PF-1 — WizardEngine cold-start budget (CHK001, severity Medium)

**Fix**: добавить SC-001a:
```
- **SC-001a**: WizardEngine first-run cold-start (от Application.onCreate до первого
  кадра wizard step 0) ≤ **300ms** на medium-tier эмуляторе (Pixel 5 API 34).
  Budget covers: WizardCheckpointStore.load() (target ≤ 50ms), 
  ConfigSource.load(WizardManifest, defaultId) (target ≤ 100ms), 
  StringResolver init для current locale (target ≤ 80ms), 
  step 0 Composable first composition (target ≤ 70ms).
  Measurement: Android Macrobenchmark library со startup mode = COLD; recorded в perf-checkpoint.md.
```

И обновить SC-011 для clarity:
```
- **SC-011**: Time-to-first-frame главного экрана **не регрессирует** после F-3:
  cold start ≤ 1.5 сек (same baseline as спека 010 SC-002) измеряется
  ПОСЛЕ wizard completion. До wizard completion первый relevant frame = wizard step 0 (per SC-001a, ≤ 300ms).
```

### Issue PF-2 (Optional) — perf-checkpoint.md as deliverable (CHK018)

Добавить в Cross-spec impact → внутренние артефакты:
```
- specs/015-wizard-localization-senior-ui/perf-checkpoint.md — measurement results 
  для SC-001a и SC-011 после implementation completes. Создаётся как часть speckit-tasks output.
```

### Issue PF-3 (Optional) — StringResolver cache policy (CHK012)

Опционально добавить note в FR-027:
```
StringResolver MAY cache resolved strings internally (moko-resources внутренне
оптимизирует this); cache invalidated при locale change через LocaleProvider observation;
bounded by process lifecycle (нет custom cache size limit нужен).
```

---

## Резюме

**11 ✓ / 7 ⚠ / 1 ✗ + 1 N/A** — один real fix:

- **PF-1**: SC-001a про wizard cold-start budget 300ms.

Остальные warning'и (perf-checkpoint.md как artifact, measurement method specifics, cache policy) — opportunistic improvements, не блокеры.

Applying PF-1 inline.
