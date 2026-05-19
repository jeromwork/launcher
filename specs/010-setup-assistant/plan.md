# Implementation Plan: Setup Assistant and Launcher Bootstrap

**Branch**: `010-setup-assistant` | **Date**: 2026-05-19 | **Spec**: [spec.md](spec.md)
**Input**: 44 FR / 8 SC / 13 OUT / 13 A из spec.md после `/speckit.specify` + `/speckit.clarify` (7 clarifications resolved) + 13 checklists (`checklists/_overview.md`).

---

## 1. Overview

Спек 010 — **связующий** спек: закрывает блокер `TODO-ARCH-016` (раскладка из mock → из `/config/current` спека 8), узаконивает ad-hoc-механики спеков 7/9 (7-tap admin-mode gate с rotating challenge вместо PIN, paired-devices visibility с local-first revocation), и добавляет тонкий слой onboarding'a (ROLE_HOME с Android 8/9 fallback, POST_NOTIFICATIONS, custom call confirmation, soft-checks `!N/?M` индикатор, hard-block для GMS-less устройств).

**Технический подход**: 4 новых ports в `core/commonMain/api/` (`SetupCheck`, `Challenge`, `SlotToActionMapper`, `GmsAvailabilityPort`) + 5 SetupCheck implementations + 6 новых UI Composables + 1 wizard step extension + extensive AndroidManifest updates (CALL_PHONE permission + `<queries>` для tel: + uses-feature telephony required=false + data_extraction_rules). **No new wire formats** (FR-040 explicit). **No new gradle dependencies**. Challenge state in-memory only (no persistent storage introduced).

---

## 2. Architecture

### Module map

```
:core (KMP, commonMain)
├── api/
│   ├── setup/              [NEW package]
│   │   ├── SetupCheck.kt              [port]
│   │   ├── Criticality.kt             [sealed: Required, Recommended]
│   │   ├── Surface.kt                 [sealed: Settings, MainScreen]
│   │   ├── CheckStatus.kt             [sealed: Ok, NotConfigured(reason)]
│   │   ├── IntentSpec.kt              [data class — platform-agnostic intent descriptor]
│   │   └── GmsAvailabilityPort.kt     [port — wraps GoogleApiAvailability]
│   ├── gate/               [NEW package]
│   │   └── Challenge.kt               [sealed: NumericEntry(answer), SequenceTap(buttonIds, order)]
│   │                                  [+ fun generateRandomChallenge(random) — free function, NOT registry class]
│   └── action/             (existing — extend)
│       └── SlotToActionMapper.kt      [NEW function — Slot+Contacts → Action]
├── ui/screens/             (existing — extend)
│   ├── HomeScreen.kt                  [+ reads ConfigEditor.appliedConfig vs FlowRepository]
│   └── FirstLaunchActivity host       [extend wizard navigation host]
├── ui/setup/               [NEW package]
│   ├── WizardProgressIndicator.kt     [Composable «Шаг N из M» + dots]
│   ├── RoleHomeStep.kt                [wizard step Composable]
│   ├── PostNotificationsStep.kt       [wizard step Composable, Android 13+ only]
│   ├── SetupChecksBadge.kt            [`[!] N` + `[?] M` w/ text + shape icons]
│   └── WhatNeedsConfiguringScreen.kt  [«Что не настроено» — Required first, Recommended below]
├── ui/gate/                [NEW package]
│   ├── ChallengeGateScreen.kt         [Composable host with Cancel button + challenge renderer]
│   ├── NumericEntryChallenge.kt       [Composable — small font number + numeric keypad]
│   └── SequenceTapChallenge.kt        [Composable — 6 buttons + instruction text]
├── ui/dialog/              [NEW package]
│   └── CallConfirmationDialog.kt      [Composable — photo, name, number, CANCEL/CALL ≥56dp]
└── ui/paired/              [NEW package]
    ├── PairedDevicesScreen.kt         [«Кто помогает мне» + «Кому я помогаю»]
    └── UnlinkConfirmationDialog.kt    [двухступенчатое подтверждение]

:core (androidMain — adapters per CLAUDE.md rule 2)
├── adapters/setup/
│   ├── RoleHomeCheckAdapter.kt        [real impl — checks isRoleHeld(ROLE_HOME) on API 29+, hasCategory(CATEGORY_HOME) on API 26-28]
│   ├── PostNotificationsCheckAdapter.kt [API 33+ only]
│   ├── CallPhoneCheckAdapter.kt
│   ├── NetworkOnlineCheckAdapter.kt
│   ├── BatteryOptimizationCheckAdapter.kt
│   └── GmsAvailabilityAdapter.kt       [wraps GoogleApiAvailability]
└── adapters/gate/
    └── (Challenge generation is pure Kotlin — no adapter needed)

:app (Android-only — Activity hosts + system integration)
├── setup/
│   ├── FirstLaunchActivity.kt          [host — extended with new steps + progress indicator]
│   └── GmsHardBlockActivity.kt         [shown before wizard on GMS-less devices]
├── call/
│   ├── CallConfirmationActivity.kt     [or Compose Navigation destination]
│   └── PhoneHandler.kt                 [SPEC 5 T541 EXTENDED — conditional ACTION_CALL/ACTION_DIAL]
├── gate/
│   └── ChallengeGateActivity.kt        [or Compose Navigation destination — 7-tap target]
└── paired/
    └── UnlinkCleanupWorker.kt          [WorkManager — queued server cleanup per FR-032a]

:app/src/main/res/values/strings.xml + values-ru/strings.xml
├── String resources for new UI (≥40 new strings)
└── plurals resources:
    ├── setup_badge_required_count_a11y (one/few/many/other for Russian)
    └── setup_badge_recommended_count_a11y (one/few/many/other for Russian)
```

### Port-adapter shape

Каждый port в `commonMain` — interface; каждый adapter в `androidMain` — class implementing port. Domain (`api/`) типы НЕ импортируют Android-specific (`Intent`, `Context`, `PackageManager`).

```
:core/commonMain                  :core/androidMain
─────────────────                 ─────────────────
SetupCheck                ← impl  RoleHomeCheckAdapter
                                  PostNotificationsCheckAdapter
                                  CallPhoneCheckAdapter
                                  NetworkOnlineCheckAdapter
                                  BatteryOptimizationCheckAdapter

GmsAvailabilityPort       ← impl  GmsAvailabilityAdapter
                                  (uses GoogleApiAvailability)

Challenge / generateRandomChallenge —  pure Kotlin (no adapter)

SlotToActionMapper        —  pure Kotlin function
```

`SetupCheck` port methods return `CheckStatus` (Ok / NotConfigured) — никогда `Intent`/`Context`. `resolveIntent()` returns `IntentSpec?` (data class with String fields). Real adapter maps `IntentSpec` → `Intent` в `:app/androidMain`.

### Plan-level decisions on meta-minimization findings

Per checklist `meta-minimization.md` open items + CLAUDE.md rule 4:

- **D-1: Keep `Surface` enum** (variants: `Settings`, `MainScreen`). Single-variant в спеке 10 (`MainScreen` no consumer), но **seam confirmed for spec 013** `offline-detection-and-emergency-reachability` (per roadmap §Spec 013 — нет связи с родственником главный экран banner). Cost — одно поле `surfaces: Set<Surface>` в port + Settings recompute filtering. Exit ramp: если спек 013 откажется от main-screen banner pattern, collapse Surface к single `Settings` constant — 5-минутное rename refactor. **Не speculative — anticipated single consumer documented**.

- **D-2: Collapse `SetupCheckRegistry` → `List<SetupCheck>` injection.** Koin module провайдит `single { listOf(roleHome, postNotif, ...) }`. UI consumes `List<SetupCheck>` directly. No registry class.

- **D-3: Collapse `ChallengeRegistry` → free function** `fun generateRandomChallenge(random: Random = Random.Default): Challenge`. Sealed `Challenge` type stays. Pure Kotlin, no class needed.

### Data flow — ARCH-016 (US-1: admin layout reaches бабушка)

```
Admin in admin-mode (спек 9) edits layout → "Опубликовать"
    │
    ▼ (спек 8 already implemented)
ConfigEditor publishes via Firestore /config/current
    │
    ▼ (спек 7 already implemented)
FCM push → Managed устройство
    │
    ▼ (спек 8 already implemented)
ConfigRefreshWorker updates Room-backed cache
    │ ConfigEditor.appliedConfig: Flow<ConfigCurrent> emits new value
    │
    ▼ (СПЕК 10 — НОВОЕ ЗВЕНО)
HomeScreen collects appliedConfig as State
    │ NO longer reads FlowRepository / flows_mock_*.json
    │
    ▼
SlotToActionMapper(slot, config.contacts) → Action
    │ resolves contactId references in Slot.kind = Call/WhatsAppCall
    │
    ▼
TileCard renders Action (existing спек 3/4/5 pipeline unchanged)
```

### Data flow — FR-032/32a (local-first paired-device unlink)

```
Бабушка tap "Прекратить помощь" → двухступенчатое подтверждение ДА
    │
    ▼ IMMEDIATE (synchronous, on tap)
LocalLinkRevocationStore.markRevoked(linkId)
    │ persistent storage (DataStore, key: revoked_link_<linkId> = true)
    │ Survives kill / restart.
    │
    ├──▶ ConfigEditor (спек 8): stops listening /config/{linkId} push'ей
    ├──▶ /state publisher (спек 7): stops publishing for this link
    └──▶ UI updates immediately: Маша disappears from "Кто помогает мне"
    
    ▼ ASYNC (background, via WorkManager)
UnlinkCleanupWorker enqueued (CONNECTED constraint)
    │
    ▼ when internet available
LinkRegistry.deactivate(linkId) [спек 7]
    │
    ├─ (a) Success → /config/{linkId} cascade delete, push admin'у, queue clears
    ├─ (b) Already revoked on server (admin parallel-deleted) → idempotent no-op
    └─ (c) Network failure → WorkManager retry (exponential backoff)
```

### Data flow — Challenge gate (US-7)

```
Бабушка taps non-interactive area 7 times within 5sec (±48dp delta)
    │ (no haptic for 1-3, medium haptic 4-6, success haptic 7)
    │
    ▼
ChallengeGateScreen opens
    │
    ▼
generateRandomChallenge() → Challenge.NumericEntry("1673") | Challenge.SequenceTap(...)
    │ stored in remember { } (Composable scope — see C-1 below)
    │
    ▼ user input
    ├─ Correct answer → navigate to admin-mode (спек 9)
    └─ Wrong answer → generateRandomChallenge() again (no counter, no lockout)
    
    OR
    
    ▼ Cancel button (≥56dp) or system Back
    └─ Return to HomeScreen, no side effects
```

---

## 3. Data model

См. spec.md §Key Entities. Краткий обзор:

- **`SetupCheck` (port)**: `id: String, criticality: Criticality, surfaces: Set<Surface>, check(): suspend () -> CheckStatus, resolveIntent(): IntentSpec?`
- **`Criticality`**: sealed (`Required`, `Recommended`)
- **`Surface`**: sealed (`Settings`, `MainScreen` — seam, no consumer in spec 010)
- **`CheckStatus`**: sealed (`Ok`, `NotConfigured(reason: String)`)
- **`IntentSpec`**: data class platform-agnostic. Example: `IntentSpec(category="SETTINGS", action="POST_NOTIFICATIONS_DETAILS")`. Mapping in `:app/androidMain`.
- **`GmsAvailabilityPort`**: `suspend fun status(): GmsStatus`
- **`GmsStatus`**: sealed (`Available`, `MissingRecoverable(reason, resolutionAvailable: Boolean)`, `MissingFatal(reason)`)
- **`Challenge`**: sealed (`NumericEntry(answer: String)`, `SequenceTap(buttonIds: List<Int>, expectedOrder: List<Int>)`)
- **`SlotToActionMapper`**: free function `fun Slot.toAction(contacts: List<Contact>): Action?`

**Persistent state introduced** (DataStore, не Room):
- `wizard_completed_step: Int` (за existing спек 3 wizard tracking, extended with new steps)
- `revoked_link_<linkId>: Boolean` (FR-032 local-first revocation marker)

**No new wire format types** (FR-040 explicit). `IntentSpec` — in-process only.

**No persistent challenge state** (FR-025). `LocalLinkRevocationStore` — единственное новое persistent storage в спеке 010, и оно покрывается existing backup exclusion rules для DataStore.

---

## 4. Wire formats

**Спец 010 не вводит новых wire format'ов** (FR-040 explicit). Все changes — local-only behaviour + UI. Существующие wire formats (consumed but not modified):

| Format | Source spec | Spec 010 usage |
|--------|-------------|----------------|
| `/config/current` Firestore | Спек 8 | Read-only (FR-001..FR-006 ARCH-016) |
| `/state/applied` Firestore | Спек 8 | Not touched |
| `/links/{linkId}` Firestore | Спек 7 | Read (paired devices list); deactivate (FR-032a) |
| QR-deeplink | Спек 7 | Not touched |
| FCM payload | Спек 7 | Not touched (consumed via existing listener) |

**No `contracts/` folder created** — nothing new to contract.

---

## 5. Dependency impact

**Article XIII (Dependency discipline) — PASS, без новых gradle deps:**

| Что | Откуда |
|---|---|
| `GoogleApiAvailability` | существующий `play-services-base` (inherited из спека 7 FCM dependency) |
| `RoleManager` (API 29+) | стандартный Android API, no extra lib |
| `HapticFeedbackConstants` | стандартный Android API + Compose `LocalHapticFeedback` |
| `androidx.work` для UnlinkCleanupWorker | существующий dep из спека 8 ConfigRefreshWorker |
| `androidx.datastore` для LocalLinkRevocationStore | существующий dep |

**`AndroidManifest.xml` дополнения (CRITICAL — finding из permissions-platform CHK008):**

```xml
<!-- Permission for one-tap calling -->
<uses-permission android:name="android.permission.CALL_PHONE" />

<!-- Telephony hardware not required (tablet compatibility) -->
<uses-feature
    android:name="android.hardware.telephony"
    android:required="false" />

<!-- CRITICAL: Android 11+ package visibility for tel: scheme -->
<queries>
    <intent>
        <action android:name="android.intent.action.DIAL" />
        <data android:scheme="tel" />
    </intent>
    <intent>
        <action android:name="android.intent.action.CALL" />
        <data android:scheme="tel" />
    </intent>
</queries>

<!-- New Activities (all non-exported) -->
<activity android:name=".setup.GmsHardBlockActivity"
          android:exported="false"
          android:noHistory="true" />
<!-- ChallengeGateActivity, CallConfirmationActivity — alternatively Compose Navigation destinations within MainActivity (preferred per implementation simplicity). Plan-level decision: use Navigation destinations, not separate Activities. -->
```

---

## 6. Test strategy

Per CLAUDE.md §6 (mock-first) + §7 (fitness functions):

### Contract tests (every port)

| Port | Fake adapter | Real adapter | Contract test |
|------|--------------|--------------|---------------|
| `SetupCheck` (5 implementations) | `FakeSetupCheck` (configurable status) | 5 real adapters | Each adapter: verify check() returns matching CheckStatus per system state |
| `GmsAvailabilityPort` | `FakeGmsAvailabilityPort` (returns programmable GmsStatus) | `GmsAvailabilityAdapter` | Maps `GoogleApiAvailability` codes → domain GmsStatus correctly |

### Domain-level tests

- **`SlotToActionMapperTest`** — все `SlotKind` variants → correct Action; missing contact → null Action.
- **`ChallengeGenerationTest`** — uniform distribution between NumericEntry / SequenceTap over 1000 iterations; numeric values 1000-9999 range; sequence-tap 3-position from 6 buttons.
- **`ChallengeFPRateTest` (SC-007)** — simulated random taps (1000 iterations) on NumericEntry keypad and SequenceTap buttons; assert false-positive rate ≤ 1% (≤ 0.83% theoretical for sequence, ≤ 0.01% for numeric).
- **`LocalLinkRevocationStoreTest`** — mark/check/clear; persistence across simulated process restart.

### UI tests (Compose UI test)

- **`HomeScreenArch016Test`** — HomeScreen renders from `FakeConfigEditor.appliedConfig`, not FlowRepository; offline cold-start renders last-applied (US-1 #2).
- **`WizardProgressIndicatorTest`** — «Шаг 2 из 4» renders correctly; updates after step skip/complete.
- **`CallConfirmationDialogTest`** — large buttons (≥56dp measured), TalkBack focus order (CANCEL first per accessibility CHK011), photo placeholder fallback (initials for no-photo per ux-quality CHK002), invalid-number disables CALL button.
- **`SetupChecksBadgeTest`** — `[!] N` red + `[?] M` yellow with text labels; shape-different icons; hidden when count = 0; TalkBack contentDescription via plurals resource.
- **`ChallengeGateScreenTest`** — 7-tap detection (one-point ±48dp, ≤5sec); cancel button returns to home; correct answer navigates; wrong answer regenerates challenge.
- **`PairedDevicesScreenTest`** — two sections; unlink confirmation 2-step; empty state with QR show button.
- **`UnlinkLocalFirstTest`** — tap "Прекратить" → Маша disappears immediately + DataStore flag set + UnlinkCleanupWorker enqueued. Offline + online paths both verified.
- **`GmsHardBlockTest`** — fatal GMS → hard-block screen → "Понятно" calls finishAffinity(); recoverable → system dialog.

### Backward-compat smoke

- `MockDataRemovalSafetyTest` — FR-004 verification: deletion of `flows_mock_*.json` does not break spec 3/4 Robolectric tests after rewrite to `FakeRemoteSyncBackend`. CI gate.

### Performance tests

- **SC-002 cold-start macrobenchmark**: HomeScreen first frame ≤ 1 sec on Pixel 4a class. Uses `androidx.benchmark.macro.FrameTimingMetric`. Gate: if fails → optimize Room read / Compose lazy load.
- **SC-009 APK size delta**: `apkdiff` CI step compares release build vs спек 9 release. Threshold ≤ +500 KB.

### Accessibility tests

- `AccessibilityScannerCITest` — Android Accessibility Scanner CLI on all new screens (wizard steps, call confirmation, challenge gate, settings badges, paired devices, GMS hard-block). Fail build on contrast / tap-target violations. **Plan-specific**: yellow `?` badge must use WCAG-compliant shade (decided in Phase 5: `#D97706` text on white, или yellow text on `#1F2937` dark badge).
- Manual TalkBack walkthrough — checklist for US-1, US-3, US-5, US-7. Verify CANCEL focus-first on Call confirmation (accessibility CHK011).

### Senior-safe walkthrough

- **Phase 8 senior-safe walkthrough** — 5 elder users (elderly-friendly CHK022). Scenarios:
  - Fresh install → wizard 4 steps → complete setup
  - Call confirmation tile-tap → choose CANCEL or CALL без помощи
  - Случайно tap 7+ раз → challenge → press CANCEL
  - TalkBack on: 7-tap → challenge text прочитан → press CANCEL (US-7 #7 verification)

### Fitness functions (Konsist gates)

4 NEW Konsist rules в `core/src/commonTest/.../KonsistGate*.kt`:

1. `api/setup/*.kt` MUST NOT import `android.*` / `androidx.*` / `com.google.android.gms.*`.
2. `api/gate/*.kt` MUST NOT import `android.*` / `androidx.*`.
3. `api/setup/IntentSpec` MUST contain only `String` / primitive fields (no `Intent` / `Uri` / `ComponentName`).
4. `:app/androidMain` package `setup`/`gate`/`call` — все Activity classes MUST have `android:exported="false"` declaration (lint-style check).

---

## 7. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|------------|--------|------------|
| R1 | `<queries>` для `tel:` забыт → FR-012/FR-014 broken на Android 11+ target SDK | M (если plan не followed) | **H — call breaks** | Phase 0 explicit AndroidManifest update + integration test что `Intent(ACTION_CALL, "tel:...")` resolves |
| R2 | RoleManager API 29 minimum vs minSdk 26 — на Android 8/9 нет RoleManager | H (любая 8/9 устройство) | M — wizard step fails | Phase 3: legacy path `IntentFilter` + system chooser для API < 29. Detect via `Build.VERSION.SDK_INT >= 29` |
| R3 | Challenge gate state lost on rotation — admin frustration | M | L — admin retries | Phase 7: `rememberSaveable` for challenge state (state-management CHK001/005/009 finding) |
| R4 | ARCH-016 flows_mock removal breaks 3-5 спек 3/4 Robolectric tests | H (известно) | M — CI red | Phase 2: rewrite affected tests on `FakeRemoteSyncBackend` (concern #6) в той же phase |
| R5 | SetupCheck.check() throws на OEM (Xiaomi PowerManager SecurityException) | M | H — Settings crash | FR-020b explicit handling: catch → `NotConfigured(reason)` (см. failure-recovery CHK001/002 closure) |
| R6 | Unlink offline + admin parallel-delete race condition | L | L (idempotent fix in FR-032a) | FR-032a (c) explicit «already revoked → no-op». Test covers both paths. |
| R7 | Yellow `?` badge fails WCAG contrast (Material Yellow #FFEB3B on white = 1.2:1) | H if default | M — accessibility scanner fail | Phase 5: use `#D97706` text on white (3.1:1) or yellow on dark badge. Documented in plan §6 a11y tests. |
| R8 | Call confirmation TalkBack focus order — CANCEL not first → 5 swipes for primary action | M | M — accessibility audit fail | Phase 4: `Modifier.semantics { traversalIndex = -1f }` on CANCEL. Manual TalkBack walkthrough verifies. |
| R9 | Russian plurals для badge a11y broken («Критичных проблем 1» вместо «Одна критичная проблема») | M | M — TalkBack grammatically incorrect | Phase 5: `plurals` resource w/ 4 forms (one/few/many/other). Localization CHK005/020 closure. |
| R10 | Challenge text ≤14sp at fontScale 200% becomes 28sp — readable to бабушка | L | L (accepted edge per spec.md A-13) | Edge documented. Бабушка с big-font может видеть challenge, но admin-mode UI ей всё равно непонятен → нажмёт CANCEL. |
| R11 | LocalLinkRevocationStore corrupted on OEM with DataStore quirks | L (rare) | M (stale revoked state) | Phase 6: catch IOException → assume not-revoked → next user action re-attempts; corruption logged via diagnostic event |

---

## 8. Required Context Review

Per конституция Article XII §7 — каждый relevant документ.

### Constitution & engineering rules

- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — все 16 Articles. Particular focus: Article I (Architecture), Article VII (Wire Format — N/A для спека 10 per FR-040), Article VIII §7 (Senior-Safe), Article IX (Performance), Article XI (MVA), Article XII (Context Review), Article XIII (Dependencies), Article XIV (Privacy/Security), Article XVI (Constitution Check).
- [`CLAUDE.md`](../../CLAUDE.md) — все 8 rules. Особенно rule 1 (domain isolation — `SetupCheck`/`Challenge`/`GmsAvailabilityPort` ports), rule 2 (ACL — `GmsAvailabilityAdapter`), rule 4 (MVA — D-1/D-2/D-3 plan decisions), rule 5 (wire format — N/A FR-040), rule 6 (mock-first — fakes for all 6 ports).

### Architectural Decision Records

- [`docs/adr/ADR-005-ui-stack-compose-multiplatform.md`](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md) — Compose Multiplatform UI stack (наш rendering pipeline для всех новых Composables).
- [`docs/adr/ADR-004-localization-and-global-readiness.md`](../../docs/adr/ADR-004-localization-and-global-readiness.md) — i18n strategy. Спец 10 FR-039 + plurals enforcement.

### Product

- [`docs/product/roadmap.md`](../../docs/product/roadmap.md) §Spec 010 (lines 241-260) — original scope. §Spec 014 (lines 386+, добавлено в clarify) — onboarding-and-tutorials future spec.
- [`docs/product/senior-safe-launcher-plan.md`](../../docs/product/senior-safe-launcher-plan.md) — overall product philosophy для elderly. Senior-safe baseline.

### Compliance

- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — RUNTIME permissions list. Plan-task Phase 0: добавить `CALL_PHONE` + `POST_NOTIFICATIONS` (Android 13+) + ROLE_HOME (not permission, role) + `<queries>` + `<uses-feature>`.

### Dev / Operations

- [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md) — `TODO-ARCH-016` (closed by Phase 2), `TODO-FUTURE-SPEC-006` (onboarding-and-tutorials spec 014), `TODO-ARCH-005` (non-GMS device support — alternative path для GMS hard-block survivors, не блокирующий).
- [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md) — no new server-side dependencies introduced. `UnlinkCleanupWorker` — client-side housekeeping per rule 8 server-roadmap pattern (inline TODO для eventual server cron migration).

### Existing code references (read-before-write)

- [`core/src/commonMain/kotlin/com/launcher/api/config/`](../../core/src/commonMain/kotlin/com/launcher/api/config/) — `Contact`, `Slot`, `ConfigCurrent` (consumed by SlotToActionMapper). Spec 8 ConfigEditor port — consumed by HomeScreen ARCH-016.
- [`core/src/commonMain/kotlin/com/launcher/api/action/`](../../core/src/commonMain/kotlin/com/launcher/api/action/) — extend with SlotToActionMapper.
- [`core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt`](../../core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt) — ARCH-016 refactor target.
- [`core/src/commonMain/kotlin/com/launcher/ui/components/TileCard.kt`](../../core/src/commonMain/kotlin/com/launcher/ui/components/TileCard.kt) — read-only consumer of SlotToActionMapper output.
- [`app/src/main/java/com/launcher/setup/FirstLaunchActivity.kt`](../../app/src/main/java/com/launcher/setup/FirstLaunchActivity.kt) — wizard host extension target.
- [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) — extend with CALL_PHONE, `<queries>`, `<uses-feature>`, new Activities.
- Спек 5 `PhoneHandler` (T541) — Phase 4 conditional ACTION_CALL extension.

### Спеки upstream

- [`specs/003-ui-skeleton/`](../003-ui-skeleton/) — `FirstLaunchActivity`, preset picker, language picker. Wizard extension target.
- [`specs/005-action-architecture-v2/`](../005-action-architecture-v2/) — Action model, PhoneHandler T541 extension point.
- [`specs/006-provider-capabilities-and-health/`](../006-provider-capabilities-and-health/) — IconStorage (consumed by Call confirmation photo).
- [`specs/007-pairing-and-firebase-channel/`](../007-pairing-and-firebase-channel/) — pairing identity, `LinkRegistry.deactivate()`, FCM channel.
- [`specs/008-bidirectional-config-sync/`](../008-bidirectional-config-sync/) — `ConfigEditor.appliedConfig` Room observable (ARCH-016 source).
- [`specs/009-admin-mode-flows/`](../009-admin-mode-flows/) — admin-mode entry point (target of 7-tap + challenge gate).

### Checklist findings (from `/speckit.clarify` Step 5)

- [`specs/010-setup-assistant/checklists/_overview.md`](checklists/_overview.md) — aggregate of 13 checklists. 3 critical findings закрываются на plan-level (см. Phase 0).
- Plan-level enumerations (13 items) — addressed across Phases per §10 Rollout.

---

## 9. Constitution Check

**Status**: ✅ **8/8 PASS** — plan COMPLETE (manual application of `procedure-constitution-check` since skill invocation deferred).

| Gate | Status | Justification |
|------|--------|---------------|
| 1 Architecture | ✅ PASS | Расширение existing `:core` / `:app` модулей. No new gradle modules. Ports/adapters per CLAUDE.md rule 2 с clear ownership + build isolation + testability. См. Module map в §2. **D-1/D-2/D-3 plan decisions** документируют minimal abstraction choices. |
| 2 Core/System Integration | ✅ PASS | No new BroadcastReceiver. Все Android types (`Intent`/`Uri`/`Context`/`PackageManager`) wrapped в `androidMain` адаптерах per rule 2. `RoleManager` legacy fallback для API 26-28 — explicit. `<queries>` для tel: explicit. `UnlinkCleanupWorker` использует existing `androidx.work` (спек 8 pattern). |
| 3 Configuration | ✅ PASS | **No wire format changes** (FR-040 explicit). Challenge state in-memory only (FR-025). LocalLinkRevocationStore — local DataStore, not wire format. Backup exclusion inherited from спек 1/9. |
| 4 Required Context Review | ✅ PASS | §8 links: constitution (все 16 Articles), CLAUDE.md (8 rules), ADR-005 (Compose), ADR-004 (i18n), roadmap §Spec 010+014, senior-safe-launcher-plan, permissions-and-resource-budget, project-backlog (TODO-ARCH-016/005, TODO-FUTURE-SPEC-006), server-roadmap, upstream specs 003/005/006/007/008/009, существующий код (Slot/Contact/HomeScreen/FirstLaunchActivity/PhoneHandler/AndroidManifest). |
| 5 Accessibility | ✅ PASS | FR-019 badges имеют triple-redundant cues (color + shape + text label + TalkBack contentDescription). FR-027 challenge TalkBack-friendly. FR-026 ≥ 56dp cancel button. A-13 documents intentional ≤ 14sp challenge text. FR-039 localization mandatory. Plan-level: WCAG-compliant yellow shade, TalkBack focus order (CANCEL first), plurals для badge a11y. Senior-safe walkthrough (Phase 8) на 5 elder users. |
| 6 Battery/Performance | ✅ PASS | FR-020a explicit «**НЕТ** background polling». SetupCheck — lazy + reactive (cold-start + Settings RESUMED). UnlinkCleanupWorker — one-shot, CONNECTED constraint, no polling. SC-002 cold-start ≤ 1 sec, SC-009 APK ≤ +500 KB — measurable via macrobenchmark / apkdiff. No new wake locks, no exact alarms. |
| 7 Testing | ✅ PASS | All 6 ports (SetupCheck × 5 + GmsAvailabilityPort) have fake + real + contract test. SC-007 challenge FP rate ≤ 1% — unit test with simulated random input. UI tests for all new Composables. ARCH-016 mock removal safety test (SC-008). 4 Konsist gates. Macrobenchmark + APK delta CI. Senior-safe walkthrough Phase 8. |
| 8 Simplicity | ✅ PASS | **D-2/D-3 collapse** registries to simpler shapes (List injection, free function). **D-1 Surface enum** — single anticipated consumer (спек 013) documented, exit ramp cheap (5-min rename). All 4 new ports have ≥ 2 consumers (real + fake). No speculative abstractions. Tutorial overlay removed (US-8 cut) — significant simplification vs original draft. |

**Watch items** (приняты deliberately, not violations):
- `Surface.MainScreen` enum variant — single anticipated consumer (спек 013 offline banner), documented anticipation. Reviewable если спек 013 откажется from main-screen banner pattern.
- Russian plurals require 4 forms (one/few/many/other) — implementation must enumerate all 4 explicitly. Caught by checklist `localization.md` CHK005.

**Verdict**: plan ready for Step 5 (plan-level checklist re-runs) + Step 6 (final report). После — переход к `/speckit.tasks`.

---

## 10. Rollout / verification

### Phase 0 — Preflight (~0.5 week, **CRITICAL** — blocks all subsequent phases)

Адресует 3 critical findings из `checklists/_overview.md`:

- **`AndroidManifest.xml` updates** (permissions-platform CHK008/020):
  - Add `<uses-permission android:name="android.permission.CALL_PHONE" />`.
  - Add `<uses-feature android:name="android.hardware.telephony" android:required="false" />`.
  - Add `<queries>` block for `tel:` scheme (DIAL + CALL actions).
- **`GmsAvailabilityPort` introduction** (domain-isolation CHK001/002/003/015):
  - Create `core/commonMain/api/setup/GmsAvailabilityPort.kt` + `GmsStatus` sealed.
  - Create `core/androidMain/.../GmsAvailabilityAdapter.kt` wrapping `GoogleApiAvailability`.
  - Spec.md FR-042 rephrased в спеке (post-plan): replace direct `GoogleApiAvailability.isGooglePlayServicesAvailable()` reference с domain `GmsAvailabilityPort.status()`. Optional spec edit OR plan-time decision recorded here.
- **Konsist gates × 4** registered before Phase 1 starts:
  - Domain isolation для `api/setup/` and `api/gate/`.
  - IntentSpec primitive-only fields.
  - androidMain Activity `exported="false"`.
- **Compliance doc update** ([docs/compliance/permissions-and-resource-budget.md](../../docs/compliance/permissions-and-resource-budget.md)) — добавить CALL_PHONE + POST_NOTIFICATIONS + ROLE_HOME + queries delta.

**Verification**: AndroidManifest builds; Konsist gates green; spec.md references updated; compliance doc updated.

---

### Phase 1 — Core types + ports + fake adapters (~1 week)

- New domain types: `SetupCheck`, `Criticality`, `Surface`, `CheckStatus`, `IntentSpec`, `GmsAvailabilityPort`, `GmsStatus`, `Challenge` (sealed), `generateRandomChallenge` (free fun), `SlotToActionMapper` (free fun).
- 6 fake adapters: `FakeSetupCheck` (configurable), `FakeGmsAvailabilityPort`, etc.
- Koin module wiring (`mockBackend` flavor uses fakes, `realBackend` uses real).
- Contract tests, domain-level tests (SlotToActionMapper, ChallengeGeneration, ChallengeFPRate).
- **Verification**: all contract + domain tests green; Konsist gates green; coverage on new types ≥ 80%.

---

### Phase 2 — ARCH-016 closure (~1 week, **HIGH PRIORITY** — closes TODO-ARCH-016)

- `HomeScreen` reads `ConfigEditor.appliedConfig` (Flow, спек 8 port) instead of `FlowRepository`.
- Wire `SlotToActionMapper` between Slot и Action в rendering pipeline.
- **Delete `flows_mock_*.json` + `MockFlowRepository`**.
- **Rewrite 3-5 affected Robolectric tests** на `FakeRemoteSyncBackend` (concern #6 — same phase as ARCH-016).
- Real adapters: `RoleHomeCheckAdapter` (with API 26-28 fallback), `NetworkOnlineCheckAdapter`.
- **Verification**: SC-002 macrobenchmark cold-start ≤ 1 sec; SC-008 (`flows_mock` removal не ломает CI); manual smoke: pair admin↔Managed, push, verify раскладка updates ≤ 10 sec (SC-001).

---

### Phase 3 — Wizard extension + GMS hard-block (~1 week)

- New wizard steps: `RoleHomeStep`, `PostNotificationsStep` (Android 13+ only).
- `WizardProgressIndicator` Composable («Шаг N из M» + dots).
- ROLE_HOME legacy fallback для Android 8/9 (`IntentFilter CATEGORY_HOME` + system chooser).
- `GmsHardBlockActivity` shown ON top of FirstLaunchActivity if GMS fatal (FR-042..FR-044).
- Real adapter: `PostNotificationsCheckAdapter`.
- **Verification**: manual wizard walkthrough on Android 8/9 (legacy ROLE_HOME), Android 13+ (POST_NOTIFICATIONS), GMS-less emulator (hard-block). Accessibility Scanner on wizard screens.

---

### Phase 4 — Call confirmation dialog (~1 week)

- `CallConfirmationDialog` Composable (≥ 56dp buttons, photo с initials placeholder, phone number formatter).
- `CallPhoneRationaleScreen` Composable (FR-013 first-tap rationale).
- WhatsApp variant (FR-014 — same dialog, deep-link instead of intent).
- `PhoneHandler` extension в `:app/androidMain` (spec 5 T541 cross-ref): conditional ACTION_CALL/ACTION_DIAL based on permission state.
- Real adapter: `CallPhoneCheckAdapter`.
- **Verification**: SC-003 (2-tap call when granted, 3-tap when denied); `Intent(ACTION_CALL, "tel:")` resolves correctly on Android 11+ thanks to `<queries>`; manual TalkBack walkthrough (CANCEL focus-first per CHK011).

---

### Phase 5 — Settings + soft-checks engine (~1 week)

- 5 `SetupCheck` implementations registered via Koin `List<SetupCheck>`.
- `SetupChecksBadge` Composable: `[!] N` red + `[?] M` yellow + text labels + shape icons + TalkBack contentDescription.
- **`plurals` resources** в `strings.xml` (en) и `values-ru/strings.xml` (ru): 4 forms для Russian (one/few/many/other).
- **Yellow badge color spec**: `#D97706` text on white background (3.1:1 WCAG contrast).
- `WhatNeedsConfiguringScreen` (FR-020 — Required + Recommended sections).
- FR-020a execution model (lazy on Settings RESUMED).
- **FR-020b exception handling** для check() throws.
- Real adapter: `BatteryOptimizationCheckAdapter` (Xiaomi quirks tested).
- **Verification**: SC-004/005 (`!N` indicator on fresh install, snaps to 0 after Required granted). Accessibility Scanner на Settings screens.

---

### Phase 6 — Paired devices + local-first unlink (~1 week)

- `PairedDevicesScreen` Composable (двойной список: «Кто помогает мне» / «Кому я помогаю»).
- `UnlinkConfirmationDialog` (двухступенчатое подтверждение FR-031).
- **`LocalLinkRevocationStore`** (DataStore-backed persistent flag).
- FR-032 immediate local revocation: ConfigEditor listener stop + UI update.
- **`UnlinkCleanupWorker`** (WorkManager): CONNECTED constraint, calls `LinkRegistry.deactivate()`, idempotent retry.
- FR-033 empty-state.
- **Verification**: Manual scenarios:
  - Online unlink — link disappears immediately + server cleanup succeeds.
  - Offline unlink — link disappears immediately + queued; reconnect → server cleanup runs.
  - Race condition (admin parallel-delete) — idempotent no-op.
  - Process kill mid-revocation — recovery via DataStore + WorkManager auto-restart.

---

### Phase 7 — Challenge gate + 7-tap (~1 week)

- 7-tap gesture detection: non-interactive area, ±48dp delta tracking, ≤ 5sec window.
- Vibration escalation: light (1-3) → medium (4-6) → success (7).
- `ChallengeGateScreen` host Composable (с `rememberSaveable` для state survival on rotation).
- `NumericEntryChallenge` Composable (≤ 14sp number, ≥ 56dp Cancel, custom 56dp numeric keypad).
- `SequenceTapChallenge` Composable (6 buttons 1-6, instruction text via plurals).
- FR-027 TalkBack accessibility (`importantForAccessibility="auto"`).
- **Verification**: SC-007 challenge FP rate ≤ 1% (unit test with simulated random input). Manual TalkBack walkthrough (US-7 #7). Vibration disabled edge (Edge Cases).

---

### Phase 8 — Verification & ship-readiness (~0.5 week)

- `/speckit.analyze` audit (procedure-cross-artifact-trace + re-run all 13 checklists).
- **Senior-safe walkthrough** на 5 elder users (elderly-friendly CHK022): scenarios — fresh install, tile→call, accidental 7-tap+cancel, TalkBack admin entry.
- **OEM matrix smoke**: Samsung One UI, Xiaomi MIUI, Pixel emulator — verify FR-012 call dialog, FR-020b SetupCheck exception handling on Xiaomi battery API, unlink offline+reconnect.
- Macrobenchmark gates final pass (SC-002 cold-start).
- APK size delta verification (SC-009).
- Update `perf-checkpoint.md` (если создан) + smoke results.
- Close `TODO-ARCH-016` в backlog with reference на implementation commits.

**Total estimate**: ~7 weeks (consistent с спеком 9 reference, спец 10 чуть меньше из-за no wire format work).

### Continuous verification gates

- **Pre-commit**: 4 Konsist gates fail build on domain isolation / IntentSpec primitives / exported activity violations.
- **Per-merge**: contract tests + UI tests + Accessibility Scanner CI gate.
- **Pre-release**: macrobenchmark SC-002 + APK size delta SC-009 + manual TalkBack walkthrough.
- **Pre-Play Store**: TODO-LEGAL-001 closed (спец 9 dependency); TODO-ARCH-006 R8 done; CALL_PHONE Data Safety form updated.

---

## 11. Implementation constraints (anti-bloat per meta-minimization plan-level)

Эти ограничения фиксируются как **обязательные** policy для `/speckit.tasks` и реализации:

- **C-1: `rememberSaveable` для Challenge state.** Challenge value хранится в `rememberSaveable` (survives rotation per state-management CHK001/005/009). Это **единственный** Compose state-management mechanism для challenge — без ViewModel, без Koin-scoped state, без Compose `LaunchedEffect`-stored state.

- **C-2: No persistent challenge counter.** FR-025 explicit «in-memory only». В коде не должно быть DataStore keys `challenge_attempts`, `challenge_last_*`, etc. Если в будущем потребуется attempt tracking (см. OUT-011 real security future-spec) — отдельный спек.

- **C-3: `List<SetupCheck>` injection, no Registry class.** Koin module: `single<List<SetupCheck>> { listOf(get<RoleHomeCheck>(), get<PostNotificationsCheck>(), ...) }`. UI consumes `List<SetupCheck>` directly. **NO** class `SetupCheckRegistry` per D-2 plan decision.

- **C-4: `generateRandomChallenge` — free function.** В `core/commonMain/api/gate/Challenge.kt`: `fun generateRandomChallenge(random: Random = Random.Default): Challenge`. **NO** interface `ChallengeRegistry` per D-3 plan decision.

- **C-5: `Surface.MainScreen` enum variant kept, documented seam.** Inline-TODO в `Surface.kt`: `// TODO(seam): MainScreen variant — anticipated consumer спек 013 offline-detection-and-emergency-reachability (main-screen banner pattern, см. roadmap §Spec 013). Collapse to single Settings constant if спек 013 откажется from main-screen banner pattern (5-min rename refactor).`

- **C-6: ROLE_HOME legacy fallback inline-TODO.** `RoleHomeCheckAdapter.kt` MUST contain inline-TODO для Android < 29 path: `// TODO(legacy): API 26-28 нет RoleManager. Fallback на queryIntentActivities(Intent.CATEGORY_HOME) + system chooser. minSdk = 26 per gradle/libs.versions.toml. Можно дропнуть когда minSdk поднимется до 29 (Android 10).` Соответствует memory rule `feedback_exit_ramps_as_todos`.

- **C-7: Yellow `?` badge color = `#D97706` (или dark badge background).** Hard-coded в theme. **NOT** Material Yellow `#FFEB3B` (1.2:1 WCAG fail). Accessibility CHK005 closure.

- **C-8: Russian plurals required, 4 forms.** Все count-dependent strings (badge a11y, paired devices count если будет) MUST use `plurals` resources с explicit `quantity="one|few|many|other"` for Russian. Localization CHK005/020 closure.

- **C-9: Non-exported activities.** All new Activity classes (`GmsHardBlockActivity`, etc.) MUST have `android:exported="false"`. Konsist gate #4 enforces. Security CHK009 closure.

- **C-10: GMS port — single concrete implementation в спеке 010.** `GmsAvailabilityAdapter` wraps `GoogleApiAvailability` directly. **NO** intermediate abstraction layer (e.g. `GmsAvailabilityChecker` factory) — port + adapter, done. Если спек 013 захочет non-GMS path (TODO-ARCH-005) — добавит second adapter.

Эти constraints фиксируются для `/speckit.tasks`, чтобы каждая ARCH task имела explicit «не делать X».

---

## Что внутри (TL;DR на русском)

Это **архитектурный план** спека 010 — переход от «что нужно сделать» (44 FR в спеке) к «как именно реализовать» (модули, порты, фазы работ).

**Главные архитектурные решения:**
- **4 новых порта** в `core/commonMain/api/` для подключения проверок и системных API: `SetupCheck` (5 реализаций), `GmsAvailabilityPort` (GMS detection), `Challenge` (sealed types) + `SlotToActionMapper` (свободная функция).
- **Никаких новых wire format'ов** (FR-040): спец 10 — connective tissue, всё что покидает устройство уже покрыто спеками 7/8/9.
- **Local-first revocation pattern** для FR-032/32a (твой design): «прекратить помощь» работает immediately локально, server cleanup eventually через WorkManager.
- **3 critical fixes из checklists** делаются в **Phase 0** перед всем остальным: (1) `<queries>` для `tel:` в AndroidManifest (без него call broken на Android 11+); (2) `GmsAvailabilityPort` чтобы domain не видел Google API; (3) `plurals` resource для русского badge a11y.

**Tests:** 6 портов имеют fake + real + contract test. Challenge FP rate ≤ 1% verified unit test'ом. ARCH-016 mock removal — safety test что спеки 3/4 тесты не ломаются. Macrobenchmark cold-start ≤ 1 sec. APK delta ≤ +500 KB. 4 Konsist gates следят за domain isolation. Senior-safe walkthrough на 5 elder users в Phase 8.

**8 фаз работ** общим объёмом **~7 недель**. **Constitution Check 8/8 PASS**. Все 13 plan-level findings из `checklists/_overview.md` addressable либо в plan (Phase 0/5/6/7) либо в реализации (yellow badge color, focus order и т.д.).

**Constraints на реализацию** (см. §11): `rememberSaveable` для challenge state, нет persistent challenge counter, `List<SetupCheck>` injection (не registry class), `generateRandomChallenge` свободная функция (не registry interface), Yellow badge `#D97706` (не Material Yellow), Russian plurals 4 формы, non-exported activities, ROLE_HOME legacy fallback для Android 8/9.

**Следующий шаг:** `/speckit.tasks` — разбиение plan'a на конкретные T-задачи (T001..Txxx) с traceability на FR/SC. Затем `/speckit.analyze` → имплементация.
