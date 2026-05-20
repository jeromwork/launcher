# Permissions & Platform Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Verify Android-specific platform constraints per constitution Article XIV + Article III §3.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## Inventory

### Permissions

| Permission | Type | Spec FR | Fallback |
|------------|------|---------|----------|
| `CALL_PHONE` | Runtime dangerous | FR-011..FR-016 | `ACTION_DIAL` (FR-012) |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | FR-008 | `!N` banner + Settings deep-link (US-4 #2) |
| `READ_CONTACTS` | Runtime (inherited спек 9) | Not new в спеке 10 | Inherited (rationale в спеке 9) |

### Roles

| Role | API | Spec FR | Fallback |
|------|-----|---------|----------|
| `ROLE_HOME` | Android 10+ (API 29) | FR-007 | `!N` banner + Settings deep-link (US-2 #2/#3) |

### Intents (outgoing)

- `ACTION_CALL` + `tel:` — FR-012 (granted path)
- `ACTION_DIAL` + `tel:` — FR-012 (fallback)
- `RoleManager.createRequestRoleIntent(ROLE_HOME)` — FR-007
- POST_NOTIFICATIONS runtime request — FR-008
- `ACTION_APP_NOTIFICATION_SETTINGS` — US-4 #2 (deep-link recovery)
- `Intent.ACTION_VIEW` + `https://wa.me/<phone>` — FR-014
- `Intent.ACTION_VIEW` + GMS support URL — FR-043

## Runtime permissions

- [X] **CHK001** — User-value justification per permission:
  - `CALL_PHONE`: «one-tap calling for elderly users» (US-3 explicit). ✓
  - `POST_NOTIFICATIONS`: «чтобы внук видел, что у тебя всё в порядке» (US-4 rationale). ✓
- [X] **CHK002** — First-launch permission flow:
  - `POST_NOTIFICATIONS` — wizard step с rationale (FR-008, US-4 #1). ✓
  - `CALL_PHONE` — НЕ в wizard, prompted on first Call-tile tap (FR-013 rationale-экран). ✓ (deliberate: только tile-driven, не upfront)
- [X] **CHK003** — Re-prompt strategy:
  - First denial — `!N` banner shown (US-2 #2, US-4 #2, US-3 #5). 
  - "Don't ask again" / permanent denial — fallback works (`ACTION_DIAL` для CALL_PHONE, `!N` banner для POST_NOTIFICATIONS) без crash. ✓
- [X] **CHK004** — Settings deep-link path:
  - `CALL_PHONE`: US-3 #5 — `!` indicator with reason, Settings deep-link to app permissions.
  - `POST_NOTIFICATIONS`: US-4 #2 — `ACTION_APP_NOTIFICATION_SETTINGS`. ✓
  - ROLE_HOME: US-2 #3 — re-trigger `RoleManager.createRequestRoleIntent`. ✓
- [X] **CHK005** — Pre-permission rationale:
  - `CALL_PHONE`: explicit rationale-экран FR-013 («Чтобы звонок шёл сразу одной кнопкой»). ✓
  - `POST_NOTIFICATIONS`: explicit rationale-экран US-4 #1 («Чтобы внук видел, что у тебя всё в порядке»). ✓
  - ROLE_HOME: wizard step explanation FR-007 («Сделать этот лончер главным»). ✓

## Manifest declarations

- [X] **CHK006** — Required permissions listed, no broad permissions:
  - `<uses-permission android:name="android.permission.CALL_PHONE" />` — explicit в спеке (Затрагиваемые внешние артефакты).
  - `POST_NOTIFICATIONS` — Android 13+, auto-declared при targetSdk 33+. Implicit. Plan should explicit.
  - No broad permissions (`INTERNET` inherited, `WAKE_LOCK` not added, etc.). ✓
- [X] **CHK007** — `<uses-feature>`:
  - `android.hardware.telephony` — нужен для `CALL_PHONE`. **Plan must declare** `<uses-feature android:name="android.hardware.telephony" android:required="false" />` (`required="false"` так как fallback `ACTION_DIAL` работает без telephony hardware).
- [⚠️⚠️] **CHK008** — `<queries>` element (Android 11+):
  - **CRITICAL GAP**: `Intent.ACTION_CALL` / `Intent.ACTION_DIAL` resolution на Android 11+ требует `<queries>` declaration для `tel:` scheme. Без неё `resolveActivity()` returns null, `startActivity()` throws `ActivityNotFoundException`.
  - **Plan.md MUST добавить** в `AndroidManifest.xml`:
    ```xml
    <queries>
      <intent>
        <action android:name="android.intent.action.DIAL" />
        <data android:scheme="tel" />
      </intent>
      <intent>
        <action android:name="android.intent.action.CALL" />
        <data android:scheme="tel" />
      </intent>
      <!-- WhatsApp universal link (https://wa.me/) — browser handles, no queries needed -->
      <!-- Optional: <package android:name="com.whatsapp" /> if direct package intent desired -->
    </queries>
    ```
  - **Без этого FR-012/FR-014 НЕ РАБОТАЮТ на Android 11+** (target SDK 30+).

## Android version specifics

- [X] **CHK009** — Scoped storage: спец 10 не делает file I/O. ✓
- [X] **CHK010** — Foreground service: спец 10 не добавляет foreground service. ✓
- [X] **CHK011** — Exact alarms: спец 10 не использует `SCHEDULE_EXACT_ALARM`. ✓
- [X] **CHK012** — `POST_NOTIFICATIONS` Android 13+: FR-008 explicit. ✓
- [⚠️] **CHK013** — Predictive back gesture (Android 14+):
  - Challenge gate screen (FR-022) overrides back → ОТМЕНА равноценна. Должен использовать `OnBackPressedDispatcher` + `BackHandler` в Compose, **не** override `onBackPressed()`. **Plan-level confirmation.**
  - Call confirmation dialog (FR-016) — same. **Plan-level confirmation.**
  - GMS hard-block screen (FR-042) — back → `finishAffinity()`. **Plan must verify** Predictive Back animation compatibility.

## HOME / launcher role

- [⚠️] **CHK014** — `ROLE_HOME` behaviour:
  - When role denied: explicit FR-007 «Пропускаемый (кнопка «Позже»)», US-2 #2 `!N` banner. ✓
  - **GAP: Android < 29 (API 29) `RoleManager` не существует.** Если minSdk проекта < 29 — нужен fallback на legacy `IntentFilter` + system chooser. **Plan must check minSdk** (likely 26+ per ADR-005); если < 29, add fallback.
- [X] **CHK015** — Default launcher fallback:
  - Если не default — `!N` banner, ARCH-016 раскладка всё равно показывается (но only когда user явно открывает app). Acceptable. ✓

## OEM quirks

- [⚠️] **CHK016** — Samsung KNOX: no AccessibilityService used. ✓ (challenge gate uses standard input, не AccessibilityService).
- [⚠️] **CHK017** — Xiaomi MIUI: aggressive battery saver:
  - Спец 10 не вводит новые background workers (FR-020a explicit). ✓
  - `ConfigRefreshWorker` (спек 8) — battery restrictions inherited (A-10 + `TODO-DEVICE-002`). ✓
  - **No new Xiaomi-specific quirks introduced.**
- [⚠️] **CHK018** — Huawei EMUI:
  - GMS hard-block (FR-042) **excludes most Huawei post-2019** at first launch. ✓ Это deliberate (A-12).
  - No Huawei-specific path required.
- [⚠️] **CHK019** — OEM launcher quirks:
  - Samsung One UI / OnePlus / Xiaomi MIUI launchers also use `RoleManager` since их Android base = AOSP Android 10+. Standard API behaviour expected.
  - **OEM-specific edge**: некоторые vendor launchers могут перехватывать `RoleManager` flow (rare, vendor-specific). Plan-level test matrix should validate.

## Package visibility (Android 11+)

- [⚠️⚠️] **CHK020** — `<queries>` for inspected/launched apps:
  - **CRITICAL** — связано с CHK008. **`tel:` scheme queries обязательны.**
  - WhatsApp universal link (`https://wa.me/`) — система резолвит без queries (browser fallback). ✓
- [X] **CHK021** — `QUERY_ALL_PACKAGES`: not used. ✓ (Play Store policy hostile to broad query)

## Compliance docs

- [X] **CHK022** — `permissions-and-resource-budget.md` update:
  - **Explicit в спеке** §Затрагиваемые внешние артефакты: добавление `CALL_PHONE` row. ✓
  - **Plan must also add**: `POST_NOTIFICATIONS` (Android 13+ runtime), `ROLE_HOME` (role, not permission), `<uses-feature> telephony required=false`, `<queries>` for tel:.

---

## Open items

1. **CHK008/CHK020 — `<queries>` для `tel:` (CRITICAL).** Plan.md `AndroidManifest.xml` change MUST включать `<queries>` for `ACTION_DIAL` and `ACTION_CALL` with `tel:` scheme. Без этого FR-012/FR-014 broken на Android 11+ target SDK. **Highest priority gap из всех checklists.**

2. **CHK007 — `<uses-feature>` telephony.** Plan must add `<uses-feature android:name="android.hardware.telephony" android:required="false" />` чтобы app не filtered из Play для tablet'ов без телефонии.

3. **CHK014 — RoleManager API 29 minimum.** Plan must check project minSdk:
   - Если ≥ 29: `RoleManager` directly. ✓
   - Если < 29: add legacy fallback (IntentFilter `CATEGORY_HOME` + first-launch chooser).
   - **Recommendation**: проверить `app/build.gradle` minSdk перед `/speckit.plan`.

4. **CHK013 — Predictive back gesture.** Plan must verify все новые screens с back override используют `BackHandler` Compose, не legacy `onBackPressed()`.

5. **CHK022 — Compliance doc updates.** Plan tasks.md должен include task для `permissions-and-resource-budget.md` update со всеми deltas (CALL_PHONE, POST_NOTIFICATIONS, queries, uses-feature).

## Result

**18/22 ✓, 4 observations** — но **CHK008/CHK020 — CRITICAL** (без `<queries>` for tel: features broken на Android 11+). **Это самый важный gap из всех checklists** — необходимо адресовать в первой же phase plan.md AndroidManifest update.

**Остальные observations** (CHK007 uses-feature, CHK014 RoleManager API check, CHK013 predictive back) — solid plan-level concerns, не invalidate спец.

---

## Краткое содержание (для не-разработчика)

Проверили: правильно ли учтены Android-специфические штуки (permissions, manifest, OEM-quirks). **Самый критичный gap**: на Android 11+ для запуска dialer / call нужны `<queries>` в `AndroidManifest.xml`, иначе FR-012/FR-014 функционально не работают. **План обязан** добавить queries в первую же фазу. Также: проверить minSdk проекта (если < 29 — нужен fallback для RoleManager), `<uses-feature> telephony required=false`, Predictive Back compatibility для новых screens.
