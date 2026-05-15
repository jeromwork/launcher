# Checklist: core-quality — spec 009

**Generated**: 2026-05-15
**Spec**: `specs/009-admin-mode-flows/spec.md` (rev. after batch 1+2, ~559 lines, 46 FR + 2 NFR)
**Source**: [Google Core App Quality](https://developer.android.com/docs/quality-guidelines/core-app-quality) + [Android Vitals](https://developer.android.com/topic/performance/vitals)
**Trigger**: LARGE spec heuristic + production-bound feature (Play Store release path, exported VCard intent, permissions, PII)

---

## Visual experience

- [x] **CHK001** UI follows Material Design where not overridden by senior-safe rules.
  - **Evidence**: A-9 reuses existing `HomeScreen` / `FlowScreen` / `BottomFlowBar` / `TileCard` Composables (spec 5 Material 3 pipeline). FR-005 «тот же rendering pipeline». Senior-safe override naturally inherited via Article VIII (≥ 56 dp tap target).
  - **Status**: PASS.

- [⚠️] **CHK002** Light + dark themes both supported (or single-theme decision documented).
  - **Evidence**: Spec is silent on theme handling. Inherited from spec 5 baseline but **no explicit FR** for admin-mode editor or HistoryScreen theme behaviour. Severity indicators use color (🔴/🟡/🟢 emoji in scenarios FR-018; Critical = red) — contrast on dark theme not verified.
  - **Gap**: No FR specifies "admin editor + health indicators MUST render correctly in both light and dark theme" or explicit exclusion.
  - **Status**: WARN — defer to plan.md if dark theme is a project policy; otherwise add 1-line FR-046b «admin-mode UI наследует тему spec 5; severity colors имеют distinct hue в обоих темах».

- [⚠️] **CHK003** Edge-to-edge layout (Android 15+) handled — system bars, gesture insets.
  - **Evidence**: Spec doesn't mention edge-to-edge, system bar insets, gesture areas. Drag-and-drop bottom "trash" target (FR-008) could conflict with Android 15+ gesture nav handle if not inset-aware.
  - **Gap**: Android 15 (API 35) enforces edge-to-edge by default for apps targeting SDK 35. FR-008 trash target at "корзина внизу экрана" needs `WindowInsets.systemBars` padding to not be unreachable behind the gesture pill.
  - **Status**: WARN — add inline-TODO in plan.md or FR clarifying `WindowInsets` handling for drag-target placement.

- [x] **CHK004** Foldable / large-screen layout sane (no broken stretching) OR exclusion noted.
  - **Evidence**: A-9 notes admin runs on admin-device (typically tablet/phone). FR-003 baner «экран ~Y" / N плиток в ряду» implies admin and Managed screen sizes differ — this is the **density mismatch decision** (FR-005, OUT-003): decorative frame, no pixel-accurate scaling. Exit ramp documented.
  - **Status**: PASS (foldable not explicitly addressed but tablet implied; admin tablet usage is mainstream not edge case).

---

## Functional

- [x] **CHK005** Feature works without internet OR offline behaviour documented.
  - **Evidence**: Edge Cases section: «Admin офлайн, нет локального кэша» → message «попробуйте позже». «Admin офлайн, есть локальный кэш» → editor opens with cached version + offline banner. FR-002 explicitly relies on Firestore offline persistence (A-4). FR-014a continuous autosave to Room survives offline. FR-033b «При оффлайн — pending action, применяется при первом online».
  - **Status**: PASS — best-in-spec offline contract.

- [x] **CHK006** Feature handles configuration changes, language change, dark mode toggle without state loss.
  - **Evidence**: FR-014a/FR-014b — continuous autosave per change to Room (per-Managed draft). Explicitly motivated by «OEM task-killer'ах (Xiaomi MIUI, Huawei EMUI) приложение может быть убито через 2-3 минуты в фоне». This is the strongest possible state-management contract — it survives not just config change but process kill.
  - **Caveat**: Form-level state (текст в текстовом поле «edit displayName» который ещё не committed в draft) — relies on `rememberSaveable`; not specifically called out. Adequately covered by `checklist-state-management.md`.
  - **Status**: PASS.

- [⚠️] **CHK007** Background-restricted devices (Doze, App Standby) handled if feature relies on background work.
  - **Evidence**: FR-020 (post C9 rewrite) explicitly **abandoned** background polling — listener attaches when screen is open, detaches when screen closes. No background work in spec 9 scope. Push admin при closed app deferred to TODO-ARCH-012 / SRV-MONITOR-001 (out-of-scope per OUT-009).
  - **Gap**: When admin screen closes, the listener detaches — this is correct lifecycle. But spec doesn't explicitly state "listener MUST detach in `onStop`/`DisposableEffect`". Article IX §3 reference is implicit.
  - **Status**: WARN — minor: add 1-line FR clarifying detach on `Lifecycle.STOPPED` to make the contract testable. Otherwise OK because no background dependency.

- [x] **CHK008** Multi-window / split-screen behaviour documented.
  - **Evidence**: Not explicitly mentioned, but inherited from existing Compose Activity (spec 5/6). FR-027a `launchMode="singleTask"` for VCard intent is compatible with multi-window.
  - **Status**: PASS by inheritance (not a regression area for spec 9).

---

## Performance (cross-checks `checklist-performance`)

- [x] **CHK009** ANR rate target: < 0.47% (Play Vitals threshold).
  - **Evidence**: NFR-002 (VCard parse) — explicitly runs on `Dispatchers.Default`, < 100 ms p95, linear-time parser (no regex backtracking). FR-028 — 10 KB payload cap. FR-002 — Firestore SDK call (async by default). FR-020 — realtime listener (non-blocking). No spec'd UI thread blocking operation.
  - **Status**: PASS — strong NFR coverage for the only realistic ANR vector (untrusted VCard parsing).

- [⚠️] **CHK010** Crash rate target: < 1.09% (Play Vitals threshold).
  - **Evidence**: Spec doesn't set a project-wide crash rate target. Defensive contracts: FR-028 (VCard validation), FR-031 (no-TEL VCard rejection), FR-043 (schema validation prevents crash on incompatible snapshot read), `Contact.fromRaw` typed `ValidationError` returns Result (no throw).
  - **Gap**: No explicit crash budget. Most likely crash vectors covered defensively, but no production telemetry FR (Crashlytics or equivalent).
  - **Status**: WARN — add to project backlog / plan.md: crash reporting integration is a Play Store readiness requirement (CHK018 below).

- [x] **CHK011** Excessive wakeups, battery drain, network use all within Vitals budget.
  - **Evidence**: FR-020 (post C9 rewrite) explicitly **eliminated polling**. Firestore realtime listener only when screen open. No AlarmManager / WorkManager / FCM in spec 9 scope. `PhoneHealthCriticalEvent` is local; no push admin (deferred to SRV-MONITOR-001).
  - **Status**: PASS — zero background battery footprint by design.

---

## Privacy / Play policy

- [⚠️] **CHK012** Data Safety section in Play Console matches data the feature actually collects.
  - **Evidence**: FR-033a/b/c covers **minimum** privacy compliance (list/delete contacts UI + GDPR-aligned deletion + rationale-screen disclosure of cloud storage). FR-046a (Android backup exclusion) prevents PII leaking to admin's Google Drive. OUT-014 explicitly defers **full** privacy compliance to TODO-LEGAL-001.
  - **Gap**: No FR specifies "Privacy Policy URL" — Play Console requires this for any app collecting PII (READ_CONTACTS qualifies). No FR specifies Data Safety form field-by-field content (data types collected, purposes, sharing).
  - **Status**: WARN — **production blocker**. TODO-LEGAL-001 in backlog covers this; spec 9 is consistent in deferring, but a release MUST NOT happen without resolving. Add `BLOCKER` annotation on TODO-LEGAL-001 to make explicit.

- [x] **CHK013** No prohibited content / SDK per Play Developer Program Policies.
  - **Evidence**: Firebase Auth / Firestore / Messaging are first-party Google SDKs. No SMS / call log / accessibility-service abuse. READ_CONTACTS used for legitimate user-driven action (contact selection by user, not silent harvesting).
  - **Status**: PASS.

- [x] **CHK014** Permissions follow restricted-permissions policy (e.g. SMS, Call Log require declared use).
  - **Evidence**: Only `READ_CONTACTS` is requested in spec 9. Triggered by user action (FR-023, "Выбрать из контактов" button). Rationale screen mandatory (FR-023, FR-033c). Permanent-denial fallback (FR-023b → ACTION_APPLICATION_DETAILS_SETTINGS). Manual entry alternative (FR-023a — admin doesn't need permission to add a contact at all).
  - **Note**: READ_CONTACTS is **not** a restricted permission (Play requires declaration only for SMS, Call Log, Accessibility, Location, AllFilesAccess, ExactAlarm). Standard runtime permission.
  - **Status**: PASS — exemplary permission UX.

---

## Compatibility

- [⚠️] **CHK015** minSdk / targetSdk alignment with project policy.
  - **Evidence**: Spec doesn't restate min/targetSdk. A-6 «Android 5+» (intent-filter VCard) implies minSdk ~21. A-7 «Android 11+» (queries block) implies targetSdk ≥ 30. FR-027a `launchMode="singleTask"` works on all versions.
  - **Gap**: Play Store currently requires **targetSdk = 34 (Android 14)** for new releases since Aug 2024, and **targetSdk = 35 (Android 15)** from Aug 2025. Edge-to-edge (CHK003) is enforced at targetSdk 35. Spec doesn't bind targetSdk to a Play Store policy floor.
  - **Status**: WARN — should be a plan.md / gradle concern, but if release timing matters, add 1-line FR or NFR fixing targetSdk floor («targetSdk MUST be latest stable Android release minus 1»).

- [⚠️] **CHK016** Tested on at least medium-tier device (Pixel 4a class) and one OEM (Samsung / Xiaomi).
  - **Evidence**: NFR-001 explicitly targets **Pixel 4a class — 60 fps**. NFR-002 — Pixel 4a class. FR-014b motivates Room continuous autosave by **Xiaomi MIUI / Huawei EMUI task-killers** — implies OEM testing is planned.
  - **Gap**: Spec doesn't say *where* tested. tasks.md needs explicit `T0xx: Run NFR-001/002 benchmarks on Pixel 4a + Xiaomi (MIUI) emulator` or similar.
  - **Status**: WARN — content acceptable; cross-trace into tasks.md may already cover this (out of scope for this checklist to verify).

---

## Distribution

- [⚠️] **CHK017** Feature flag / staged rollout strategy documented if behaviour-changing.
  - **Evidence**: No mention of feature flags. Spec 9 ships as a behaviour-changing release (admin gains full editor; previously stubs). User Stories are prioritized P1/P2/P3 (US-1, US-2 = P1) which suggests phased landing, but no Remote Config / build-flavour / Play staged rollout strategy is specified.
  - **Gap**: Production rollout of major feature without staged-rollout plan is a Play Store readiness gap. For elderly-targeted production, even more so (bug = bricked грандма's phone).
  - **Status**: WARN — add to plan.md: "Phase rollout per user story priority (P1 first, P2/P3 follow); Play staged release 1% → 10% → 100% over 1 week; remote-kill switch via Firebase Remote Config for admin-mode editor entry point". One-way door consideration — without staged rollout, exit ramp is "release v+1".

- [⚠️] **CHK018** Crash reporting / Vitals dashboard updated for new code paths.
  - **Evidence**: No FR mentions Crashlytics or any crash-reporting SDK. Existing spec 7/8 may already integrate (need to verify in plan.md / cross-trace), but spec 9 adds significant new surface (drag-and-drop, VCard parser, history rollback) that needs crash funnel visibility.
  - **Gap**: Article IX §3 referenced for performance; analogous Article for crash reporting not invoked. R8 minification (TODO-ARCH-006 in backlog) is **release-build requirement** that's a hard prerequisite for shipping (Play Vitals + reduced APK size).
  - **Status**: WARN — production blocker. Add to plan.md: "(a) Crashlytics integrated for new admin-mode code paths; (b) R8 minification enabled per TODO-ARCH-006 before first release". Both are infrastructure-level, not FR-level, but should be tracked.

---

## Summary

| Category | PASS | WARN | FAIL |
|----------|------|------|------|
| Visual | 2 | 2 | 0 |
| Functional | 3 | 1 | 0 |
| Performance | 2 | 1 | 0 |
| Privacy/Policy | 2 | 1 | 0 |
| Compatibility | 0 | 2 | 0 |
| Distribution | 0 | 2 | 0 |
| **Total**    | **9** | **9** | **0** |

**Verdict**: 9 PASS / 9 WARN / 0 FAIL. Spec is **functionally complete** for the feature itself — no FAIL items. The 9 WARNs cluster around **release-engineering / Play Store readiness** rather than feature design:

- Theme handling (CHK002) and edge-to-edge (CHK003) — Android 15 polish.
- Crash reporting (CHK010, CHK018), R8 (CHK018), targetSdk floor (CHK015) — release infra.
- Data Safety form + Privacy Policy URL (CHK012) — Play Store prerequisite.
- Staged rollout strategy (CHK017) — risk-management.
- OEM device matrix in tasks.md (CHK016) — cross-trace concern.
- Listener detach lifecycle (CHK007) — minor explicitness gap.

None of these are spec-9-content gaps; they are **production-release readiness** gaps that any LARGE feature ships into. Most are infrastructure (plan.md / project-backlog) rather than FR additions.

---

## Top 3 Play Store blocker risks (must resolve before production release)

### 1. Privacy Policy URL + Data Safety form (CHK012)
**Risk**: Play Console submission **will be rejected** if a Privacy Policy URL is missing for an app declaring `READ_CONTACTS` and writing PII to Firestore (third-party PII, no less — Maша's number). Data Safety form requires field-by-field disclosure ("personal info / contacts / shared with third parties: Google Firebase").
**Status**: deferred to **TODO-LEGAL-001**. FR-033a/b/c provide the in-app primitives (list/delete UI) but external Privacy Policy hosting + Data Safety filled correctly is **not in spec 9 scope**.
**Action**: Annotate TODO-LEGAL-001 with `🚨 PLAY-STORE-BLOCKER` tag in project-backlog.md. Cannot ship to production without resolution.

### 2. R8 minification not enabled (CHK018 / TODO-ARCH-006)
**Risk**: Already documented in TODO-ARCH-006 — spec 7 SC-006 fails by 0.99 MB without R8. Spec 9 adds drag-and-drop infrastructure, VCard parser, HistoryScreen — APK size delta grows. Release build without R8 is large, slow to install on slow connections, and obscures Crashlytics deobfuscation paths.
**Status**: pre-existing backlog item, not introduced by spec 9.
**Action**: Resolve TODO-ARCH-006 before first production release. Annotate as PLAY-STORE-BLOCKER alongside item #1.

### 3. targetSdk floor not bound in spec / project policy (CHK015)
**Risk**: Play Store enforces targetSdk = 35 for new releases since Aug 2025. If `app/build.gradle.kts` is on targetSdk < 35, the release-build rejection comes only at upload time. Spec 9 features (edge-to-edge handling for drag trash target, `<queries>` block per FR-035a which requires API 30+) implicitly depend on targetSdk ≥ 34.
**Status**: implicit in spec 9 (A-7 assumes Android 11+), but no FR/NFR locks it.
**Action**: Add 1-line NFR or a project-wide constitution rule: «targetSdk MUST be ≥ Play Store current floor minus 1». Check `app/build.gradle.kts` to confirm current value. Low-cost change, prevents rejection-at-upload surprise.

---

## Recommendations

**Do not block spec 9 progression** to plan.md / tasks.md / implementation. The 9 WARN items are:

- **2 add to spec** (cheap, ~30 min):
  - FR-046b «admin-mode UI наследует тему spec 5; severity colors имеют distinct hue в light + dark» (CHK002).
  - 1-line clarifying `WindowInsets` handling for drag-trash placement on Android 15 (CHK003).
  - (Optional) 1-line in FR-020 about `Lifecycle.STOPPED` listener detach (CHK007 — likely overkill, leave to plan.md).

- **3 add to plan.md** (project infra, not spec content):
  - targetSdk floor binding (CHK015).
  - Crashlytics + R8 integration tasks (CHK010, CHK018).
  - OEM device test matrix in tasks.md (CHK016).

- **4 add to project-backlog.md** (release-readiness, separate from spec 9):
  - `🚨 PLAY-STORE-BLOCKER` annotations on TODO-LEGAL-001 + TODO-ARCH-006 (CHK012, CHK018).
  - Staged rollout strategy (CHK017) — could be a new TODO-OPS-001 item.

This separation is **intentional**: spec 9 is feature spec, not release-engineering spec. Production-blockers belong in backlog, not spec.

---

## TL;DR (на русском)

Spec 9 — **функционально полный** под Google Core App Quality. 9 PASS, 9 WARN, 0 FAIL.

WARN'ы — это **не дыры в фиче**, а **release-engineering инфра**: privacy policy URL для Play Console, R8 минификация, targetSdk policy floor, Crashlytics, staged rollout. Всё это либо уже в backlog (TODO-LEGAL-001, TODO-ARCH-006), либо относится к plan.md / project-backlog, а не к spec.md.

**Топ-3 production-blocker'а перед релизом в Play Store:**

1. **Privacy Policy URL + Data Safety form** (TODO-LEGAL-001) — Play Console **отклонит** submission без URL для приложения с READ_CONTACTS + PII в Firestore. Spec 9 в FR-033a/b/c сделал in-app primitives (удаление контактов, GDPR-aligned); внешний Privacy Policy + правильно заполненный Data Safety form — отдельная работа. Промаркировать TODO-LEGAL-001 как `🚨 PLAY-STORE-BLOCKER`.

2. **R8 минификация не включена** (TODO-ARCH-006) — pre-existing backlog item; spec 9 только добавляет ещё больше кода к APK (drag-and-drop, VCard, HistoryScreen). Тоже PLAY-STORE-BLOCKER.

3. **targetSdk floor не зафиксирован** в спеке / project policy — Play Store с авг 2025 требует targetSdk = 35. Spec 9 неявно ждёт API 30+ (FR-035a `<queries>`, FR-027a `launchMode`). Низкозатратное решение: 1-строчная NFR / constitution rule.

**Нужно ли добавлять в spec 9 до production-релиза?** Только **минимум**:
- FR-046b: dark theme support для severity-индикаторов (severity-цвета должны быть видны в обоих темах).
- 1 строка про `WindowInsets` для drag-trash target (Android 15 edge-to-edge).

Остальное — в plan.md / project-backlog. Spec не должен расти под release-engineering concerns; для этого есть backlog.

**Рекомендация**: не блокировать прогрессию spec 9 → plan.md → tasks.md. Добавить 2 FR (theme + insets), промаркировать 2 backlog item'а как PLAY-STORE-BLOCKER, продолжить.
