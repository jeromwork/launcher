# Research: TASK-7 — Architectural decisions with alternatives considered

**Date**: 2026-06-24 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

> **⚠️ UPDATE 2026-06-25.** The decision "Pairing as Custom step via DI extension" documented below was **reversed** per constitution amendment 1.10. The exit ramp described in the decision (use generic `CheckSpec` / `ApplySpec` if `Custom` mechanism proves wrong) was **executed**: `StepType.Custom` retired, `CustomStep` / `CustomStepHandler` / `PairAdminCustomStepHandler` deleted. Pair-admin returns at TASK-8 as standard `SystemSetting` step. The original decision text is preserved below as historical record of how the wrong path was taken — note in particular that the exit ramp was correctly anticipated but ignored at Phase-5 implementation time.

This document captures the one-way doors (per [`CLAUDE.md`](../../CLAUDE.md) §3) and significant design decisions for TASK-7. Each decision lists alternatives considered, why we chose the path, and exit ramp if we later need to reverse.

---

## R-001 — Pool schema v1 → v2 with declarative `CheckSpec` / `ApplySpec` blocks

**Decision**: Bump `system-settings.pool` wire format from schemaVersion 1 to 2. Each `SystemSettingEntry` gains optional `check: CheckSpec?` and `apply: ApplySpec?` blocks (sealed classes with `@JsonClassDiscriminator("kind")`). The hardcoded `when (settingId)` dispatch in `AndroidSystemSettingAdapter` is replaced by a handler registry keyed on `CheckSpec`/`ApplySpec` variant class.

**Why now (in TASK-7)**:
- Without this, adding a new system setting requires both JSON change AND Kotlin dispatch branch — directly contradicts Article VII §13 ("profile = data, not code"). The first concrete profile (`simple-launcher`) is the right moment to validate the declarative dispatch.
- Pre-aligns with future MCP / capability registry direction (Article XV §14 + TASK-33): `CheckSpec`/`ApplySpec` variant catalog **doubles as MCP capability surface schema**. No double work later.

**Alternatives considered**:

| Alternative | Why rejected |
|---|---|
| Keep `mechanism + settingId` enum dispatch, defer schema v2 to future task | Each new pool entry would still require Kotlin code change → violates Article VII §13. Adding declarative dispatch later means refactoring all handlers → larger one-way door. |
| Inline check logic as `JsonElement` lambdas in pool entries | JSON can't contain Kotlin callbacks. Could use symbolic strings + reflection, but reflection is fragile + breaks Konsist isolation tests. |
| Use Kotlin Multiplatform annotation processing to generate handler registry | Adds KSP / build complexity. Sealed class + manual registry is simpler and sufficient (rule 4 MVA). |
| Use JSON-RPC / OpenRPC spec for capability description | Big spec, designed for distributed invocation. Overkill for local dispatch. |

**Exit ramp** (per CLAUDE.md §3): if schema v2 turns out to be wrong shape (e.g., handler dispatch by `kind` proves insufficient for genuinely platform-specific extension), we bump to schemaVersion 3 with another wire-format change. CLAUDE.md §5 backward-compat read means v1 readers continue working during transition. Migration cost: ~1 day per breaking change in v3 (rewrite pool entries + update handlers). The shape of the schema bump itself is two-way through the standard rule 5 migration policy.

**References**:
- [contracts/system-settings-pool-v2.md](contracts/system-settings-pool-v2.md) — full wire format schema.
- [data-model.md](data-model.md) §1.1 — CheckSpec/ApplySpec sealed class definitions.

---

## R-002 — Config-check master: state-of-device, not snapshot-of-manifest

**Decision**: `WizardEngine.computePending(manifest)` queries `SystemSettingPort.status()` for SystemSetting steps and `UserPreferencesStore.current()` for UIChoice steps. Returns only steps whose **current device state** is not in the desired configuration. Replaces the existing linear traversal in `WizardEngineImpl.run()`. The existing `diffPending(savedCompletedManifest, currentManifest)` snapshot-based method is **deprecated**.

**Why**:
- Per constitution Article VII §14: "the engine MUST detect that a setting *is* in the desired state regardless of *how* it got there". Snapshot-of-manifest approach (`savedCompletedManifest`) drifts from reality whenever a setting is applied via non-wizard path (Android Settings direct, admin remote push, AI agent capability, future file import).
- Owner explicitly rejected snapshot approach during clarify pass 2026-06-24: «нет! не завязываемся хранение то какие настройки на визард... мастер проверки конфига более того, было бы хорошо в pool настроек держать не только как отобржать, подпись, но и колбэк, как проверять».

**Alternatives considered**:

| Alternative | Why rejected |
|---|---|
| Keep `diffPending(savedCompletedManifest, currentManifest)` snapshot approach (F-3 design) | Snapshot diverges from device reality when settings applied via non-wizard paths. Owner explicitly rejected. |
| Compute pending only on cold-start, not on every wizard launch | Misses cases where setting changes between cold-start and wizard launch (e.g., user goes to Android Settings, comes back). Cache (with invalidate-on-resume) gives same speed with correctness. |
| Use observable Flow from SystemSettingPort instead of synchronous query | More complex API; engine doesn't need reactive updates — one-shot status check is enough. Adds plumbing without value (rule 4 MVA). |

**Exit ramp**: if computePending pre-flight proves slow (>100ms for 30+ settings), move to background coroutine that pre-computes pending while WizardActivity is loading. Cache result. Both approaches share the same `WizardEngine.computePending()` signature, so the change is internal.

**References**:
- Constitution Article VII §14 (config-check master pattern).
- F-3 spec 015 `WizardEngine.diffPending()` — deprecated by this decision.

---

## R-003 — App-level locale override via `AppCompatDelegate.setApplicationLocales()`

**Decision**: After wizard completion (or any path that updates `UserPreferences.languageOverride`), call `AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale.forLanguageTag(override)))`. Also call at `LauncherApplication.onCreate()` to apply persisted override on cold-start.

**Why**:
- Per constitution Article III §7 (stability over system-level changes): user-applied settings are persistent user intent; system locale change must not silently override.
- AppCompatDelegate provides the cross-API-version semantics. On API 33+ it uses native `LocaleManager`. On API < 33 it manages a shim that persists via app preferences.

**Alternatives considered**:

| Alternative | Why rejected |
|---|---|
| Don't persist override; follow system locale at all times | Violates Article III §7. Elderly user expects stable language; system locale changes (often accidental) destroy trust. |
| Restart Activity with `Configuration` change on each language switch | Brittle on Android lifecycle; doesn't survive process death without app code re-applying. `setApplicationLocales` is designed for this. |
| Build custom locale persistence layer | Reinvents AppCompatDelegate. Rule 4 MVA. |
| Use system `LocaleManager` directly (API 33+) without AppCompat shim | Breaks API < 33 support without explicit fallback. AppCompatDelegate is the standard cross-version wrapper. |

**Exit ramp**: If `AppCompatDelegate.setApplicationLocales()` proves insufficient (e.g., specific OEM ignores it), we can layer custom logic in `LauncherApplication.attachBaseContext()` to apply locale via `Configuration` wrap. Same `UserPreferences.languageOverride` storage backs both paths.

**References**:
- [Android Developers — Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages) (per Article XV §14 cite UX precedent — official Android guide is the precedent).
- Constitution Article III §7.

---

## R-004 — Cache for `SystemSettingPort.status()` — TTL + invalidate-on-resume

**Decision**: `AndroidSystemSettingAdapter` maintains an internal `Map<settingId, Pair<SettingStatus, Instant>>` cache with 30-second TTL. Invalidated wholesale on `Lifecycle.RESUMED` of any Activity using SystemSettingPort. Also invalidated when `applyOrPrompt()` returns `Applied` (we just changed state).

**Why**:
- Performance: 6 settings × Android API call per status check, repeated on every wizard launch / Settings screen render. ~6 × 50ms ≈ 300ms worst case without cache. With cache: ~6 × 5ms ≈ 30ms.
- Correctness: state can change between checks (user goes to Android Settings, returns). Lifecycle.RESUMED is the canonical "user came back to our UI" signal.

**Alternatives considered**:

| Alternative | Why rejected |
|---|---|
| No cache, query every time | Slower; UI feels sluggish on settings-heavy screens. |
| Cache forever, manual invalidation | Stale state when external changes happen. Easy to miss invalidation sites. |
| Background coroutine that refreshes cache every N seconds | Battery waste; we don't need fresh status when app is backgrounded. |
| Observable Flow from SystemSettingPort with active subscription | Complex; engine doesn't subscribe — one-shot check is enough. |

**Exit ramp**: increase TTL to 5 minutes if 30s proves too aggressive; or remove cache entirely if pool grows to require background-refresh pattern (inline TODO at cache site).

**References**:
- F-3 spec 010 FR-020a — Lifecycle.RESUMED re-check pattern; this proposal reuses the same hook.

---

## R-005 — Walk-through mode: shared engine method vs separate WalkThroughEngine

**Decision**: Add `WizardEngine.runWalkThrough(manifest)` as a new method on the existing `WizardEngine` interface. Implementation in `WizardEngineImpl` shares step traversal logic with `run()` but traverses **all** steps (not filtered by `computePending`), pre-populates current values, and offers `Оставить` / `Изменить` actions per step.

**Why**:
- Per Article VII §11 (wizard is one view of profile): walk-through is **another view** of the same profile config — same engine, different traversal mode.
- Avoids introducing a parallel `WalkThroughEngine` class (rule 4 MVA + Article XI Anti-Abstraction).

**Alternatives considered**:

| Alternative | Why rejected |
|---|---|
| Separate `WalkThroughEngine` class | Duplicates step-traversal logic from `WizardEngine`. Two classes to keep in sync when adding new step types (e.g., future `Custom("X")`). |
| Reuse `run(manifest)` with a flag parameter `runAllSteps: Boolean = false` | Boolean parameter to control major behavioural difference is anti-pattern (FOLO — "false / null / 0 means different thing depending on context"). Two methods clearer. |
| Don't ship walk-through in TASK-7; defer to future task | Spec FR-014a explicitly includes it after owner clarify (Сценарий 5). Deferring means user has no path to re-edit settings except per-row Settings UI. |

**Exit ramp**: if walk-through grows complex enough to deserve its own class, extract into `WalkThroughEngine` — both methods would still satisfy the existing `WizardEngine` interface contract; extraction is two-way (rule 4).

**References**:
- spec.md FR-014a.
- Article VII §11 (wizard = view of profile).

---

## R-006 — UX pattern for pending settings — checklist+banner, NOT auto wizard re-run

**Decision**: When app update brings new bundled JSON with new pool entries, **no automatic wizard re-run**. Instead: silent Settings banner with `[!] N` indicator, expanded into a checklist of pending settings (one row per pending setting, "Настроить сейчас" CTA per row). Sequential walk-through (`engine.runWalkThrough()`) is **separately** triggered by an explicit Settings button.

**Why**:
- Per Article XV §14 (cite UX precedent): checklist + banner pattern is dominant in modern onboarding completion UX — **GitHub** ("Setup your repo"), **Slack** ("Get started"), **Stripe** ("Activate your account"), **Notion** (onboarding panel). Sequential wizard re-run is reserved for factory reset / major migration scenarios (Apple Setup Assistant on factory reset, Windows OOBE on rerun).
- Auto wizard re-run after app update would interrupt user's expected workflow — they open the app to use it, not to be walked through 5 questions again. Article III §7 (stability) supports this.

**Alternatives considered**:

| Alternative | Why rejected |
|---|---|
| Auto wizard re-run with only pending steps shown | Disrupts user expectation. UX precedent (GitHub, Slack, etc.) doesn't do this. Owner explicitly rejected during scenarios pass. |
| Sequential walk-through on every app launch with pending | Same disruption issue. |
| Push notification when pending appears | Violates CLAUDE.md rule 10 (notification minimization) — not actionable + time-sensitive + user-relevant simultaneously. |
| No UI surface for pending; rely on user opening Settings for any reason | Pending settings never noticed by user → loss of value. Banner indicator is the right signal level. |

**Exit ramp**: if checklist + banner proves not visible enough (analytics post-MVP show low engagement with pending), add a non-intrusive in-app message-bar (still banner-like, but more visible) — same wire format, different rendering policy. No code change to engine.

**References**:
- Article XV §14 (UX precedent rule, established in amendment 1.9).
- Сценарий 4 in spec.md — checklist + banner.
- Сценарий 5 in spec.md — walk-through button (separate path).

---

## R-007 — Pairing as `Custom("pair-admin")` step, not new ConfigKind

**Decision**: Pairing in wizard is represented as `StepEntry { stepType: Custom("pair-admin"), refId: "pair-admin" }` in `simple-launcher.wizard.manifest.json`. Step handler (`PairAdminCustomStepHandler` in androidMain) launches `PairingActivity` (spec 007 existing UI). Does not introduce a new `ConfigKind` enum entry.

**Why**:
- Per Article VII §10: new ConfigKind requires schemaVersion bump on the kind enum and a strong justification why existing kinds insufficient. Pairing fits into existing `Custom` step type from F-3.
- Pairing UI is already built in spec 007 — reuse via explicit intent launch, not reimplement.

**Alternatives considered**:

| Alternative | Why rejected |
|---|---|
| New ConfigKind `PairingFlow` with dedicated wire format | Overkill for one step type. Custom + DI handler is sufficient (rule 4 MVA). |
| Inline pairing UI directly in WizardActivity | Bypasses spec 007's PairingActivity, duplicates QR + consent UI. |
| Skip pairing in TASK-7, add later | Owner explicitly requested ("killer feature, давай включим"). Deferring means MVP demo doesn't show product differentiation. |

**Exit ramp**: if Custom step proves too constrained (e.g., needs richer parameters than `refId + JsonElement`), promote to dedicated ConfigKind in future task. Migration: existing `Custom("pair-admin")` entries continue working through legacy path.

**References**:
- spec 007 `PairingActivity`, `ConsentScreen`, `QrDisplayScreen` (verified working).
- F-3 `StepType.Custom(name: String)`.

---

## R-008 — Walk-through "Оставить / Изменить" rather than "Skip / Next"

**Decision**: In walk-through mode, each step displays "Текущее: <value>. [Оставить] [Изменить]" — two explicit actions. "Оставить" advances without modification; "Изменить" opens the standard step picker.

**Why**:
- Per Article XV §14 cite UX precedent: **Apple Setup Assistant** on factory reset uses this pattern ("Use Current / Change"); **Windows OOBE** on rerun uses "Keep / Update". Pattern explicitly designed for re-walking through settings without losing existing values.
- Senior-safe baseline: two large buttons, clear semantics. Generic "Next / Skip" loses information ("did I skip because I wanted no change, or because I didn't understand?").

**Alternatives considered**:

| Alternative | Why rejected |
|---|---|
| "Next / Back" with current value displayed read-only | "Next" doesn't communicate whether change is applied. Ambiguous. |
| "Skip" button as in regular wizard mode | "Skip" implies leaving setting in unset state; here we want it in current state. |
| Single "Continue" button + value tap to change | Less obvious affordance; senior users may not tap the value. |

**Exit ramp**: if "Оставить / Изменить" labels prove unclear in usability testing post-MVP, rename to "Использовать текущий / Поменять" (or similar) — JSON label keys; no code change.

**References**:
- Apple Human Interface Guidelines — Setup Assistant patterns.
- Windows OOBE design — Microsoft Docs.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** 8 архитектурных решений TASK-7 с alternatives considered. Главные one-way doors: pool schema v1→v2 (R-001 — мы это делаем сейчас, потому что без declarative dispatch добавление настроек = code change, нарушение Article VII §13); replacement snapshot-approach на config-check master в engine (R-002 — owner явно отверг snapshot, state-of-device = единственный source of truth per Article VII §14); AppCompatDelegate для locale persistence (R-003 — это enforcement Article III §7).

**Конкретика, которую стоит запомнить:**
- **R-001 Pool v1→v2** — sealed `CheckSpec` / `ApplySpec` с `@JsonClassDiscriminator("kind")`. Replaces `when(settingId)` hardcoded dispatch. Backward-compat read через legacy fallback path для v1 entries.
- **R-002 computePending** — engine pre-flight queries `SystemSettingPort.status()` per SystemSetting step, `UserPreferences` per UIChoice. Skip applied. Замена линейного traversal в `WizardEngineImpl.run()`. `diffPending(savedCompletedManifest, currentManifest)` deprecated.
- **R-003 Locale** — `AppCompatDelegate.setApplicationLocales()` called after wizard exit + on cold-start. App-level override persists против system locale change.
- **R-004 Cache** — TTL 30s + invalidate-on-resume hook reuses spec 010 FR-020a pattern.
- **R-005 Walk-through** — `WizardEngine.runWalkThrough(manifest)` method, не отдельный класс. Та же engine, другой режим traversal'а.
- **R-006 Checklist+banner** — UX precedent GitHub/Slack/Stripe/Notion. NOT автоматический wizard re-run после app update.
- **R-007 Pairing as Custom step** — переиспользует spec 007 `PairingActivity` через DI; не вводит новый ConfigKind.
- **R-008 «Оставить/Изменить»** — UX precedent Apple Setup Assistant. Clearer чем «Skip/Next».

**На что смотреть с осторожностью:**
- **Pool v1→v2 — one-way door** через CLAUDE.md rule 5 path. Если шейп v2 окажется wrong — bump до v3 ещё один такой же migration. Cost ~1 day per change.
- **computePending false positives** на OEM quirks (Xiaomi MIUI battery API throws SecurityException → `Indeterminate` → included in pending; graceful, не silent skip).
- **AppCompatDelegate API < 33 fallback** через AppCompat shim — поведение может differ on некоторых OEM. inline TODO(physical-device).
- **`Custom("pair-admin")` зависит от spec 007 PairingActivity working** — verified 2026-06-24. Если spec 007 partial breaks в будущем, TASK-7 step graceful fall (toast + skip).
