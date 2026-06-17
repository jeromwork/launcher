# Checklist: failure-recovery

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 13 ✓ / 3 ⚠ / 1 ✗ — один real gap (SystemSettingStep behavior on user denial)

---

## Error categories

- [✓] **CHK001** Each FR involving external action lists failure mode.
  - `ConfigSource.load` → `IncompatibleVersion` (FR-016), `ParseError` / `NotFound` (FR-019).
  - `WizardCheckpoint` write → atomic с return Failed (FR-049).
  - `SystemSettingPort` → sealed `ApplyResult` с `Failed`, `UnsupportedMechanism` (FR-054).
  - `StringResolver` → fallback chain с logging (FR-029).
  - Translation pipeline → skill fails с понятным сообщением при missing API key (FR-031a).
  - DataStore reads/writes — implicit error semantics (port-level errors not explicit, **minor gap**).

- [✓] **CHK002** User-visible behaviour specified per failure mode:
  - `IncompatibleVersion` → hard-fail dialog с кнопкой «Понятно» (FR-016).
  - `ParseError` → user-facing error через UI layer (паттерн спека 010 FR-042).
  - `WizardCheckpoint` corruption → graceful start from step 0 (FR-003 modification).
  - Missing translation key → fallback to EN → key literal (visible as debug signal).
  - `SettingStatus.Indeterminate` → wizard показывает self-attest UI (FR-058).
  - `Indeterminate` follow-up → `[!] N` banner per FR-059 (cross-reference спек 010).

- [✓] **CHK003** No silent failures of user-initiated actions:
  - SystemSettingStep fail → user explicitly видит self-attest или deep-link result.
  - Translation pipeline fails → explicit dev-facing message.

## Fallbacks

- [✓] **CHK004** Fallback max depth defined.
  - StringResolver fallback chain: **requested locale → EN → key literal** — 3 уровня, finite. ✓ no cycle possible.
  - ConfigSource: NotFound terminal. ✓
  - SystemSettingPort: `PromptShown` → user action → `status()` check, finite cycle. ✓

- [N/A] **CHK005** Fallback specified by data, не hardcoded.
  - StringResolver fallback chain — hardcoded (locale → EN → key). **Acceptable** для foundation: chain is foundational, не feature-dependent. Data-driven fallback bezsmysleнно (один fallback path для всех).

- [✓] **CHK006** Terminal behaviour after fallback exhaustion:
  - StringResolver: key literal (visible debug signal). ✓
  - ConfigSource: NotFound returned terminal. ✓
  - SystemSettingPort: Indeterminate → user self-attest или skip step. ✓

## Retries

- [⚠] **CHK007** Retry behaviour explicit.
  - Wizard step «Назад» → возврат + retry — implicit, не explicit FR.
  - Translation pipeline (Claude API failure) — не specified retry policy.
  - **Acceptable** foundation defer: spec 010 challenge gate использует similar implicit retry pattern.

- [✓] **CHK008** No infinite retry without user intervention.
  - StringResolver fallback chain finite. ✓
  - No background retry loops в F-3 (нет network, нет FCM).
  - Wizard manual retry via «Назад» — user-driven. ✓

- [✓] **CHK009** Idempotency:
  - WizardCheckpoint write — full state per write, idempotent. ✓
  - UserPreferences write — full struct replacement. ✓
  - SystemSettingPort.applyOrPrompt — opening same deep-link multiple times = same Settings screen, idempotent. ✓

## Offline / degraded modes

- [N/A] **CHK010** Offline behaviour. F-3 — local-only (A-10), нет network surface.
- [N/A] **CHK011** Stale data TTL. F-3 — нет remote state.

## Permissions denied

- [✗] **CHK012** Each permission required: behaviour when denied first time documented.
  - **VIOLATION — SystemSettingStep с `mechanism = StandardPermission` denied behavior не explicit**.
  - Сейчас FR-008 `SystemSettingStep` декларирует «behavior определяется mechanism» — но не explicit, что делать если пользователь denied permission:
    - Re-prompt immediately? (annoying для бабушки)
    - Skip step (если canSkip=true)?
    - Show rationale + give chance to retry?
    - Continue wizard, проблема всплывёт позже в [!] banner?
  - **Severity**: Medium. Это **главный UX pattern** в wizard'е — обработка denial критична для elderly users.
  - **Fix**: добавить FR-008a с explicit denial policy. См. Issue FR-1.

- [⚠] **CHK013** Permanent denial recovery.
  - `shouldShowRequestPermissionRationale = false` (don't-ask-again) — Android pattern.
  - F-3 не explicit фиксирует, что в этом случае wizard показывает deep-link на app settings (как backup).
  - **Recommendation**: расширить FR-008a про permanent denial — deep-link на `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.

## Recovery from invalid state

- [✓] **CHK014** Persistent state corruption recovery:
  - WizardCheckpoint corrupt / unknown schemaVersion → start from step 0 (FR-003).
  - UserPreferences unknown schemaVersion → defaults (FR-047).
  - CONTEXT.json schemaVersion mismatch → skill fails с сообщением (FR-031b).

- [✓] **CHK015** No "crash and restart" recovery.
  - All failures → user-facing error или fallback. No silent crashes designed. ✓

## Diagnostics

- [✓] **CHK016** Failures observable via diagnostic events.
  - DiagnosticEmitter (A-17) emits wizard lifecycle events.
  - StringResolver fallback → warn/error level logging (FR-029).
  - ConfigSource ParseError → diagnostic warning.
  - Unknown stepType → diagnostic warning (FR-010).
  - Unknown actionType → diagnostic warning (FR-014).

- [⚠] **CHK017** Failures aggregated by category.
  - DiagnosticEmitter port shape определяет, как events типизированы. Сейчас названы (wizardStarted, wizardStepCompleted, etc.), но **error event taxonomy** не explicit (нет `wizardFailed(category, reason)`).
  - **Recommendation**: при future analytics integration (S-1+) добавить error categorization.
  - **Acceptable** foundation defer.

---

## Issues & fixes

### Issue FR-1 — SystemSettingStep denial policy (CHK012/013, severity Medium)

**Problem**: что делает wizard step, если пользователь denied permission (single или permanently)? Не explicit.

**Fix**: добавить FR-008a:
```
- **FR-008a (SystemSettingStep denial behavior)**: При denial пользователем системного permission/setting (return value `SettingStatus.NotApplied`), `SystemSettingStep` MUST:
  (a) показать **rationale screen** с explanation почему нужно (текст из pool entry's `extendedInstructionKey` или дефолтного `descriptionKey`),
  (b) предоставить **две кнопки**: «Попробовать снова» (re-trigger `applyOrPrompt`) и «Пропустить» (если pool entry имеет `canSkip = true`) или «Назад» (если canSkip = false),
  (c) при permanent denial (`shouldShowRequestPermissionRationale = false` для StandardPermission) — заменить «Попробовать снова» на **«Открыть настройки приложения»** (deep-link на `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`),
  (d) сохранить event в DiagnosticEmitter (`wizardStepDenied(settingId, isPermanent)`).
  Это применяется ко всем mechanisms кроме `InAppOnly` (там denial невозможен — наш app сам управляет).
```

---

## Резюме

**13 ✓ / 3 ⚠ / 1 ✗** — один real fix:

- **FR-1**: SystemSettingStep denial behavior (FR-008a) — explicit rationale + retry / skip / app settings deep-link для permanent denial.

Остальные warning'и (CHK007 retry policy, CHK013 permanent denial — частично покрыто FR-1, CHK017 error categorization) — acceptable foundation defer.

Applying FR-1 inline.
