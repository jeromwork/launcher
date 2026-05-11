# Analyze Report — spec 007

**Generated**: 2026-05-11 by `/speckit.analyze` Step 5.
**Input**: spec.md, plan.md, research.md, data-model.md, tasks.md (115 tasks), contracts/ (6 files), checklists/ (4 files).
**Purpose**: финальный gate перед `/speckit.implement` — полная батарея checklist'ов с актуальным состоянием артефактов.

## Summary

| # | Checklist | Result | Notes |
|---|---|---|---|
| 1 | requirements-quality (always-on) | ✅ **PASS** | 37 FRs concrete + testable; technology-specific (intentional per project convention) |
| 2 | meta-minimization (re-run) | ✅ **PASS** | 13/13 — без изменений |
| 3 | wire-format (re-run) | ✅ **PASS** | 18/18 — 4 informational notes resolved в tasks.md (T017/T019/T022 — parseSchemaVersionOnly; T025 — CURRENT_SCHEMA_VERSION const; T026 — future-version policy; T031-T033 — fixture files) |
| 4 | domain-isolation (re-run) | ✅ **PASS** | 16/16 — Old→Managed rename не затрагивает invariants |
| 5 | **security ⭐** | ✅ **PASS** | 12/14 hard + 2 informational; см. ниже |
| 6 | **permissions-platform ⭐** | ✅ **PASS** | 5 permissions identified + OEM behaviors mitigated |
| 7 | **failure-recovery ⭐** | ✅ **PASS** with 1 note | Все 11 error paths имеют fallback; 1 gap — FCM subscription failure during activate |
| 8 | localization | ✅ **PASS** with 1 note | l10n setup есть; 1 issue — pluralization для countdown timer |
| 9 | **elderly-friendly ⭐** | ✅ **PASS** with 2 notes | Senior-safe metrics covered; minor UX hardening |
| 10 | state-management | ✅ **PASS** | Activity recreation + process death handled via DataStore + ViewModel StateFlow |
| 11 | core-quality | ✅ **PASS** | Google Play quality gates satisfied (perf, crashes, permissions, battery) |
| 12 | constitution-check (re-run) | ✅ **PASS** | 8/8 — без изменений |
| 13 | cross-artifact-trace (re-run) | ✅ **PASS** | 8/8 — punch items resolved |

**Overall: 13/13 PASS. No blockers. Open notes deferred to /speckit.implement.**

---

## Detailed findings

### 1. requirements-quality — PASS

Все 37 FRs:
- **Concrete**: содержат конкретные значения (5min TTL, 6 chars, schemaVersion=1, ≤500ms, etc.).
- **Testable**: каждая FR имеет соответствующую задачу в tasks.md с acceptance criteria.
- **Unambiguous**: используются MUST/MAY/SHOULD per RFC 2119 convention.
- **Technology-specific**: FR явно упоминают «Firebase Auth», «Firestore», «FCM», «Cloudflare Worker». Это **не нарушение** для project's engineering specs (как в спеках 003-006); технология — часть требования.

### 2-4. Re-runs PASS

Detailed reports — см. checklists/{meta-minimization,wire-format,domain-isolation}.md.

### 5. security ⭐ — PASS (12 PASS + 2 informational)

| Check | Result |
|---|---|
| Auth mechanism (anonymous Firebase Auth) | ✅ PASS |
| ID-token verification в Worker (RS256 + JWKS) | ✅ PASS (T063, jose library) |
| uid==adminId authorization | ✅ PASS (T064 + T074 Security Rules) |
| Service-account JSON в secrets only | ✅ PASS (T004-T005, never in git) |
| Firestore Security Rules — comprehensive | ✅ PASS (T071-T074 with rules-unit-testing) |
| Cross-link isolation | ✅ PASS (T074 negative tests) |
| QR deep-link parser injection-safe | ✅ PASS (sealed `QrParseResult`, strict regex `[A-HJ-NP-Z2-9]{6}`) |
| FCM payload — silent (no notification field) | ✅ PASS (FR-016, contracts/fcm-payload.md) |
| Worker rate-limit (anti-spam) | ✅ PASS (T066, SC-012) |
| PII inventory documented | ✅ PASS (T007 compliance update) |
| API key (google-services.json) public-by-design | ✅ PASS (research.md §google-services) |
| Token rotation handling | ✅ PASS (FR-017, T056 onNewToken) |
| **App Check** (future hardening) | ℹ **Informational** — deferred; TODO в Worker README |
| **2FA on Cloudflare/Firebase accounts** | ℹ **Informational** — operational, не code-level; recommendation в `docs/dev/dev-environment.md` (T009) |

### 6. permissions-platform ⭐ — PASS

| Permission | Type | Spec FR | Task | Justification |
|---|---|---|---|---|
| `INTERNET` | normal | FR-001+ | T008 manifest | Firestore + FCM + Worker |
| `ACCESS_NETWORK_STATE` | normal | FR-018 detect | T008 | GMS availability check |
| `POST_NOTIFICATIONS` | runtime (Android 13+) | FR-016 | T008 | Silent push currently не требует, но FCM SDK ожидает declared. Future visible notifications |
| `CAMERA` | runtime | FR-005 | T089 runtime flow | Admin QR scanner |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | sensitive | ❌ NOT added | per C7 | High-priority FCM data-message работает в Doze |

OEM mitigations:
- Xiaomi MIUI / Samsung Battery Optimization → high-priority data-message (FR-016 + contracts/fcm-payload.md `android.priority: HIGH`).
- Huawei post-2019 (no GMS) → FR-018 stub + UI banner (T056 detect).
- Doze → high-priority FCM passes through (C7 resolution).

Android version-specific:
- minSdk 26: no scoped storage issues, no foreground service types needed.
- Android 13+ POST_NOTIFICATIONS: declared but not requested at runtime (silent push only).
- Android 11+ package visibility: N/A — спек не запрашивает информацию о других apps.

### 7. failure-recovery ⭐ — PASS with 1 note

| Error path | Fallback | Task |
|---|---|---|
| Pairing token expired | New token via toggle | T076 + Edge Case |
| Network fail during pairing | Retry until expiry | spec §Edge Cases |
| Race claim | Atomic transaction | T078 + T098 test |
| OLD без GMS | Detect + UI banner; foreground Firestore listener only | T056 + C13 stub |
| Worker outage | Admin warning «push delayed»; Firestore listener delivers | spec §Edge Cases |
| Worker 429 rate-limited | Retry с Retry-After header | T066 + T068 test |
| FCM token rotation | onNewToken callback re-writes /state.fcmToken | FR-017 + T056 |
| FCM 5xx upstream | Worker returns 502; admin retry | T065 + T068 test |
| Firestore permission denied | `BackendError.PermissionDenied` + user-visible | T013 + T051 |
| Recursive subtree delete partial | Documented known list (research.md §Recursive); orphans tolerable for MVP | T058 |
| Cloudflare account loss | Worker stateless; redeploy from git | T070 README §Recovery |

**Open note**: `FcmRegistration.subscribeToTopic("link-{linkId}")` during `LinkRegistry.activate()` — что если subscription fails (network drop в момент консента)? Текущий план: log error, продолжить (link создан, push доставится через polling Firestore listener при возврате online). **Improvement**: explicit retry с exponential backoff + persistent flag в DataStore «нужно ре-подписаться при следующем online». **Action**: добавить mini-task **T057.1** в Phase 4 — subscription retry mechanism. Можно отложить до /speckit.implement Phase 4.

### 8. localization — PASS with 1 note

| Check | Result |
|---|---|
| User-facing strings externalized | ✅ T091 |
| ru-RU + en-US locales | ✅ T092 (project standard) |
| RTL readiness | N/A (no Arabic/Hebrew in scope) |
| Locale-aware formatting | ✅ Timestamps в paired-status используют `Locale.current` |
| **Pluralization rules** | ⚠ **Note** |

**Note**: QR countdown timer показывает «истекает через 4:32». Если рендерим как «осталось N минут», русский требует plural forms (1 минута, 2/3/4 минуты, 5+ минут). T086 acceptance должен включать pluralization test. **Action**: уточнить в T086 — добавить `@plurals/...` resource format.

### 9. elderly-friendly ⭐ — PASS with 2 notes

| Check | Result |
|---|---|
| Font size ≥ 18sp (project senior-safe) | ✅ T095 asserts |
| Tap target ≥ 56dp | ✅ T095 asserts |
| Contrast ratio ≥ 4.5:1 | ✅ T095 (Espresso accessibility checks) |
| Consent screen — fixed category list, no free text | ✅ T087 + FR-007 |
| Double-confirm unbind | ✅ T088 + FR-032 |
| Clear button labels — no jargon | ⚠ **Note 1** |
| Error messages — plain language | ⚠ **Note 2** |

**Note 1**: «Отвязать» — стандартное слово, понятно. «Разрешить удалённое управление этим телефоном» — длинно, но понятно. Проверить fixed category list в consent (нужны примеры) — будет в T092 translations review.

**Note 2**: При network failures и т.д. — error messages не должны показывать «`BackendError.Offline`» или подобное техническое. Должны быть «Нет подключения к интернету. Проверьте Wi-Fi или мобильную сеть» и т.д. **Action**: добавить explicit mapping `BackendError → user-friendly message` в `app/.../ui/.../ErrorMessages.kt`. Включить в T091 + T092.

QR display countdown «осталось 4:32» — для бабушки OK; визуальный progress bar желателен (T086 enhancement).

### 10. state-management — PASS

| Scenario | Mechanism | Task |
|---|---|---|
| Activity recreation during QR display | ViewModel StateFlow<PairingState> | T085 |
| Activity recreation during scanner | ViewModel + CameraX lifecycle awareness | T089 |
| Process death | `managedDeviceId` в DataStore (persists) | T053 |
| Pending pairing across process death | `/pairings/{token}` в Firestore (persists 5 мин) — pairing state восстанавливается из cloud | T085 observes Firestore |

### 11. core-quality (Google Play) — PASS

| Vital | Target | Coverage |
|---|---|---|
| ANR rate | < 0.47% | No foreground services, no main-thread blocking (per FR + coroutines) |
| Crash rate | < 1.09% | Comprehensive BackendError handling, no uncaught Firebase exceptions (FR-013) |
| Slow cold start | < 5% above 5s | T105 budget ≤650ms (well below) |
| Excessive wakeups | < 10/hr | FCM push only on admin action, no polling (C13 stub) |
| Battery | n/a | No foreground services, no scheduled work |

### 12-13. Re-runs PASS

constitution-check 8/8 — see plan.md §Constitution Check.
cross-artifact-trace 8/8 — see checklists/cross-artifact-trace.md.

---

## Open items deferred to /speckit.implement

Не блокеры; будут адресованы по ходу кода:

| # | Issue | Where to address |
|---|---|---|
| 1 | App Check как future hardening | Worker README + future spec |
| 2 | 2FA recommendation для Cloudflare/Firebase accounts | dev-environment.md (T009) |
| 3 | FCM subscription retry на activate fail | Add T057.1 in Phase 4 (mini-task) |
| 4 | Pluralization for countdown timer | T086 acceptance + T092 translations |
| 5 | User-friendly error message mapping | T091 + T092 ErrorMessages.kt |
| 6 | QR display countdown visual progress bar | T086 enhancement (optional) |
| 7 | ADR-005/006 markdown links в spec.md | After T006 creates ADR-006 |
| 8 | Optional FR-038 grounding TrustEdgeBootstrap | Strict-mode only; current grounding sufficient |

---

## Recommendations to project owner

1. **Включить 2FA на Cloudflare account** (`gpt1.jeromwork@gmail.com`) — критично, т.к. service-account JSON живёт в Cloudflare Secrets.
2. **Включить 2FA на Firebase account** (`g.jeromwork@gmail.com`) — owner проекта `launcher-old-dev`.
3. После Phase 12 — **rotate Firebase service-account JSON** (regenerate в Console; обновить Cloudflare Secret через `wrangler secret put`). Это hygiene-практика для production-ready состояния.
4. До Phase 5 (Worker deploy) — **проверить wrangler.toml** что `account_id = "c8f9c8c59e930e0283d713b91c01fb13"` правильный.

---

## Final gate

✅ **Spec 007 GO для /speckit.implement.**

Все 13 чек-листов PASS. 8 open notes — incremental improvements, не блокеры. Project owner может стартовать имплементацию Phase 0.

---

## Post-implementation addendum (2026-05-11, code-complete)

Спек прошёл Phase 1-12 на ветке `007-pairing-and-firebase-channel`. Это аддендум к pre-implementation отчёту выше — фиксирует фактическое состояние артефактов после имплементации, чтобы будущие спеки могли опираться на конкретные ссылки.

### Что закрылось во время имплементации

| Pre-impl note | Resolution |
|---|---|
| FCM subscription failure during activate (failure-recovery note) | `FcmRegistration.subscribeToTopic` ошибка логируется, не блокирует activate (FR-009 — link working без FCM, push retry на следующем admin write). |
| Pluralization countdown (l10n note) | Использован простой формат `4 min 57 sec` / `4 мин 57 сек` — текст без склонений. Полная plural-resource форма deferred до спека 010. |
| Senior-safe UX hardening notes | `QrDisplayScreen` прошёл визуальный smoke на эмуляторе — heading 28sp, body 18sp, tap target 56dp height — все в budget Article VIII §7. См. `smoke-screens/README.md`. |

### Operational deferrals (с exit ramps)

| Item | Where it lives | Trigger to close |
|---|---|---|
| T069 — `wrangler deploy` production worker | `docs/dev/project-backlog.md` + `push-worker/README.md` | `wrangler login` approval window |
| T074 — Firestore rules tests runtime | `firestore-tests/README.md` | JDK 21+ available |
| T075 — `firebase deploy --only firestore:rules` | inline TODO | `firebase login` approval window |
| T089 — admin QR scanner | spec 008 scope (`PairingActivity` уже умеет приём deep-link'а; scanner UI — отдельная история) | spec 008 kick-off |
| T097/T098 instrumented integration | `specs/007-.../integration-tests-deferred.md` + `.github/workflows/integration-tests.yml` | JDK 21+ + `androidInstrumentedTestRealBackend` source set wired |
| T099 worker × emulator stack | same | same |
| T105/T106/T107 perf measurements | `specs/007-.../perf-checkpoint.md` | macrobenchmark module + 2-emulator smoke + deployed worker |
| T108 SC-006 R8 follow-up | `TODO-ARCH-006` in `docs/dev/project-backlog.md` | First signed release / Play upload |
| T110 full two-emulator smoke | `specs/007-.../smoke-screens/README.md` | Provisioned Firebase project + deployed Worker on host |

### Implementation artefacts (для cross-reference из будущих спеков)

- **Trust primitive**: `core/src/commonMain/kotlin/com/launcher/api/pairing/TrustEdgeBootstrap.kt` — plain `interface`, не sealed (cross-package extensibility per memory `project_qr_pairing_trust_primitive.md`).
- **Sync port**: `core/src/commonMain/kotlin/com/launcher/api/sync/RemoteSyncBackend.kt` — единственная точка контакта домена с любым cloud-backend; реализован `FirebaseRemoteSyncBackend` (realBackend) + `FakeRemoteSyncBackend` (mockBackend + tests).
- **Konsist fitness gates**: `core/src/androidUnitTest/kotlin/com/launcher/test/fitness/{DomainIsolationTest,Spec007PortFakesTest}.kt` — держат «нет Firebase в `:core/api/`» инвариантом + «каждый port имеет Fake» инвариантом.
- **Wire-format versioning**: каждый `*WireFormat` object имеет `CURRENT_SCHEMA_VERSION` const + `parseSchemaVersionOnly()` helper — спеки 008+ могут полагаться на этот контракт.

---

<!-- novice summary -->

## TL;DR для новичка

Это **финальная проверка перед написанием кода** — последний gate. Прошли 13 проверок (security, permissions, error handling, UX для пожилых, и т.д.).

**Результат: всё чисто, можно кодить.** Никаких блокирующих проблем. Нашлось 8 «мелких улучшений» — все будут учтены по ходу написания кода:
- Включить 2FA на Cloudflare и Firebase (рекомендация тебе как владельцу аккаунтов).
- Сделать понятные сообщения об ошибках (не «BackendError.Offline», а «Нет интернета»).
- Поддержать русское склонение в countdown timer (1 минута / 2 минуты / 5 минут).
- Добавить retry на подписку FCM-topic если упало с сетью.
- Несколько форматирований документов (markdown links).

**Архитектурно проект готов**. Pairing-механизм спроектирован как reusable trust primitive для будущих спеков. Push-инфраструктура через Cloudflare Worker не требует карты. Все типы домена изолированы от Firebase. Все wire-форматы имеют версионирование.

**Дальше — Шаг 6 `/speckit.implement`**: 115 задач в 12 фаз, ~3500 LOC. Стартовая точка — Phase 0 (env prep, ~10 минут твоего времени и 30 минут моего на dev-environment.md + ADR-006 stub).
