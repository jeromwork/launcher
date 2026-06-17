# Checklist: dev-experience

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16 (post meta-minimization fixes)
**Verdict**: 17 ✓ / 4 ⚠ / 1 ✗ — четыре minor gaps + один real gap (Claude API key)

---

## Local-test path

- [✓] **CHK001** Local Test Path section filled in — emulator preset, fakes, fixtures, commands, gaps.
- [✓] **CHK002** Verification commands exact: `./gradlew :core:wizard:check`, `:core:localization:check`, `:core:ui-senior:check`, `:app:connectedDebugAndroidTest --tests *WizardE2ETest`, `checkLauncherAgnosticImports`.
- [⚠] **CHK003** Verification ≤ 5 min cold?
  - JVM unit tests (core/wizard, core/localization) — оценка < 2 min cold.
  - core/ui-senior с Compose preview screenshot tests — оценка 1-3 min cold.
  - `:app:connectedDebugAndroidTest` — **зависит от emulator boot** (~2-4 min) + test execution (~1 min). **Cold cycle может превысить 5 min** на slow laptop.
  - **Recommendation**: разделить verification на JVM-only (быстрый dev loop) и emulator (CI / pre-PR). JVM path < 5 min ✓; emulator path explicitly slow, OK.
- [✓] **CHK004** JVM path без эмулятора: `WizardEngine`, `ConfigSource`, `StringResolver`, `UserPreferencesStore`, `SystemSettingPort` (с FakeAdapter) — все тестируются на JVM commonTest. ✓ core/wizard + core/localization фундаментально emulator-free.
- [✓] **CHK005** `pixel_5_api_34` preset из skill `android-emulator` явно упомянут.

## Fake adapters

- [✓] **CHK006** Each port has fake:
  - `WizardCheckpointStore` → `InMemoryCheckpointStore` (FR-006)
  - `ConfigSource` → `FakeConfigSource` (FR-022)
  - `DismissedHintsStore` → `InMemoryDismissedHintsStore` (Local Test Path)
  - `UserPreferencesStore` → `InMemoryUserPreferencesStore` (FR-048)
  - `LocaleProvider` → `FakeLocaleProvider` (Local Test Path)
  - `SystemSettingPort` → `FakeSystemSettingAdapter` (FR-056)
  - `DiagnosticEmitter` → `RecordingDiagnosticEmitter` (Local Test Path)
  - `StringResolver` — не имеет отдельного fake, но конструируется с `FakeLocaleProvider` + in-memory string map (acceptable, тесты можно собрать без отдельного class'а).
- [✓] **CHK007** Fakes used in tests, no real Firebase/Cloudflare/FCM. F-3 — pure local (A-10 + decision 2026-06-15-deferred-cloud). No external service dependency. ✓
- [⚠] **CHK008** DI wiring debug/test vs release split.
  - **Finding**: спека не описывает explicit build flavor / DI module composition pattern (fakes для debug, reals для release).
  - **Rationale**: это implementation detail для `plan.md`. Foundation spec фиксирует **port-based** architecture; конкретные DI bindings (Hilt module, koin module, manual factory) — plan.md решит.
  - **Acceptable** для F-3.

## Fixtures

- [✓] **CHK009** Fixtures checked in:
  - `test-app-family.json`, `test-3x4.json`, `test-classic-6.json`
  - `tile-set-with-future-fields.json`, `tile-set-future-version.json`
  - `test-pool.json`, `android-pool.json`
  - `de.strings-with-gap.properties`
  - Все в `core/wizard/src/commonTest/resources/fixtures/` или production `commonMain/resources/`. ✓
- [⚠] **CHK010** Fixtures stable (no `Random()`, no `now()` без fixed clock).
  - **Finding**: `AttestationRecord(attestedAt: Instant, ...)` (FR-058) использует `Instant` — если test пишет `AttestationRecord(now(), true)`, тесты non-deterministic.
  - **Fix**: добавить `Clock` port (или kotlinx-datetime `Clock` interface) с `FakeClock` для тестов. См. Spec edit ниже.
- [⚠] **CHK011** Cross-version fixtures для wire format.
  - **Finding**: `tile-set-with-future-fields.json` (forward-compat) ✓; `tile-set-future-version.json` (hard-fail) ✓; **backward-compat** fixture — FR-018 признаёт: «placeholder — пока единственная version, тест валидирует механизм».
  - **Acceptable сейчас** (одна schemaVersion = 1 для каждой схемы); при первом bump'е добавится v1 fixture для v2 reader test.

## Cannot-test-locally gaps

- [✓] **CHK012** Все gaps explicitly listed в Local Test Path → Cannot-test-locally gaps:
  - TalkBack interactions
  - OEM-specific text rendering (Samsung/Xiaomi/Huawei)
  - iOS UI rendering
  - Translation quality для AR/HI/ZH/JA/KK
- [✓] **CHK013** Каждый gap имеет inline `// TODO(physical-device)` или explicit «not verified в F-3».
- [✓] **CHK014** No silent prod gaps — все 4 gaps explicit, нет «потом разберёмся».

## Build cycle

- [⚠] **CHK015** Clean-build time impact ≤ 30 sec? **Не явно подтверждено**.
  - **Finding**: F-3 добавляет: 3 новых Gradle модуля + Kotlin Multiplatform toolchain (для core/wizard, core/localization) + moko-resources annotation processor / KSP + Konsist test deps.
  - **Realistic estimate**: +1-2 min на clean build из-за KMP toolchain (initial download + KSP processing). **Above 30 sec threshold**.
  - **Mitigation в спеке**: SC-010 fixes APK size delta (≤ +1.5 MB), но build time не зафиксирован.
  - **Recommendation**: добавить acknowledgement в Assumptions: «Build time impact: +1-2 min на clean build из-за KMP toolchain setup; justified by JVM testability gain (avoid эмулятор для core/* tests)».
  - **Acceptable** при добавлении этого acknowledgement.
- [✗] **CHK016** One-time manual setup documented?
  - **VIOLATION — Claude API key для translation skill**:
    - Translation pipeline (FR-031a, C-10) запускается в конце `speckit-tasks` orchestrator через skill `procedure-translate-spec-strings`. Skill зовёт Claude API.
    - **Spec не документирует**: где developer получает API key, куда кладёт, как переменная окружения называется.
    - **Severity**: Medium. Translation skill **не запустится** без key — это блокирующий gap для нового developer'а.
    - **Fix**: добавить в FR-031a: «Claude API key читается из `ANTHROPIC_API_KEY` env var. Setup instructions в `core/localization/README.md`».
- [⚠] **CHK017** New credentials для debug builds?
  - F-3 build itself: nothing new. ✓
  - Translation skill workflow: **нужен Claude API key** — см. CHK016.
  - Debug build app не требует key (skill = dev-side tool, не runtime).
  - **Acceptable** при fix'е CHK016.

## Crash + log diagnostics

- [✓] **CHK018** Adequate log signal:
  - `DiagnosticEmitter` emits `wizardStarted`, `wizardStepCompleted`, `wizardCompleted`, `wizardCancelled` (A-17)
  - `ConfigSource` parse errors → logged warning (FR-019 + edge cases)
  - `StringResolver` fallback → warn/error level (FR-029)
  - Unknown stepType → diagnostic warning (FR-010)
  - Unknown actionType → diagnostic warning (FR-014)
- [✓] **CHK019** Silent crash modes logged:
  - Checkpoint write failure → wizard не завершается (FR-049 atomic), будет visible через `WizardOutcome` propagation.
  - Background coroutine cancellations — implicit, наследуется от стандартных Kotlin coroutines patterns.
- [N/A] **CHK020** Runtime feature flags loggable — F-3 не имеет runtime feature flags.

## Cross-developer reproducibility

- [✓] **CHK021** No developer-machine-specific paths/env:
  - Все пути relative (`core/wizard/src/commonTest/...`). ✓
  - Claude API key — это env var (standard pattern), acceptable.
- [⚠] **CHK022** Onboarding documented:
  - Developer должен прочитать: F-3 spec, `glossary.md`, `CLAUDE.md`, skill `android-emulator`, **new**: `core/localization/README.md` (для translation skill setup).
  - **Recommendation**: создать `core/wizard/README.md`, `core/localization/README.md`, `core/ui-senior/README.md` (это уже в FR-042 для extraction TODO) + добавить «Quick start» section с verification command + dependencies.
  - **Acceptable** при выполнении FR-042 c quick-start.

---

## Issues & fixes

### Issue D-1 — Claude API key setup не документирован (CHK016, severity Medium)

**Fix**: дополнить FR-031a:
```
... Skill читает Claude API key из ANTHROPIC_API_KEY env var. Setup instructions
для нового developer'а — в core/localization/README.md ("Translation pipeline setup").
Без key skill terminates с понятным сообщением: "ANTHROPIC_API_KEY not set —
see core/localization/README.md".
```

### Issue D-2 — FakeClock missing для AttestationRecord testing (CHK010, severity Low)

**Fix**: добавить в Local Test Path → Fake adapters:
```
- FakeClock (kotlinx-datetime Clock) — для deterministic AttestationRecord testing
  (FR-058 пишет attestedAt: Instant; тесты используют FakeClock).
```

И в Assumptions добавить:
```
A-18: AttestationRecord использует kotlinx-datetime Clock port; production реализация —
Clock.System; test — FakeClock с fixed instant.
```

### Issue D-3 — Build time impact не acknowledged (CHK015, severity Low)

**Fix**: добавить в Assumptions:
```
A-19: Clean-build time impact — оценка +1-2 min из-за KMP toolchain + moko-resources
KSP processing. Justified by JVM testability gain (core/wizard, core/localization
тестируются без эмулятора, что экономит 2-4 min на test cycle).
```

---

## Резюме

**17 ✓ / 4 ⚠ / 1 ✗** — спека dev-friendly с одним блокирующим gap'ом:

- **Блокер**: Claude API key setup для translation pipeline (CHK016) — fix D-1 обязателен.
- **Minor**: FakeClock для AttestationRecord (D-2), build time acknowledgement (D-3), DI build variant split (acceptable foundation deferral), backward-compat fixture (acceptable для single-version).

Applying fix'ы D-1, D-2, D-3 непосредственно в spec.md.
