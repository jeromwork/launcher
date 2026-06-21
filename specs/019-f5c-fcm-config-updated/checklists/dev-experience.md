# Checklist: dev-experience — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

## Local-test path

- [x] **CHK001** Local Test Path section filled. Содержит Emulator/device, Fake adapters, Fixtures, Verification commands, Cannot-test-locally gaps. Не template placeholder.

- [x] **CHK002** Verification command exact. Listed:
  - `./gradlew :core:push:test --tests *PushTrigger*` (port contract)
  - `./gradlew :core:push:jvmTest --tests *WireFormat*` (roundtrip + backward-compat)
  - `./gradlew :app:connectedDebugAndroidTest --tests *ConfigUpdatedPushE2ETest*` (2 эмулятора)
  - `cd workers/family-push && npm test` (Worker logic Vitest)
  - `cd workers/family-push && wrangler dev` + integration test от `core/push/jvmTest/` (wire-format contract)

- [⚠️] **CHK003** Under 5 minutes cold? JVM tests (`:core:push:test`) — да, <30s. AndroidTest с 2 эмуляторами — **borderline**, реально 5-10 минут (старт эмуляторов 1-2 min + APK install + test run). Worker tests — <10s.
  - **Action**: split AndroidTest на (a) fast JVM-only suite — under 5 min cold; (b) emulator-bound integration suite — отдельно, не блокирует typical PR cycle.

- [x] **CHK004** At least one path verifiable без эмулятора. `:core:push:test` JVM tests покрывают port contract + wire-format roundtrip без эмулятора. `:core:push:jvmTest` тоже JVM-only. Worker tests — JVM-equivalent (Vitest).

- [x] **CHK005** Emulator preset named. `pixel_5_api_34` (skill `android-emulator`) явно указан в Local Test Path.

## Fake adapters

- [x] **CHK006** Every external port has fake adapter:
  - `FakeFcmDispatcher` (Worker test) — replaces Google FCM API.
  - `FakeFirebaseAuth` — issues test ID-tokens.
  - `InMemoryRecipientResolver` — replaces Firestore directory read.
  - `FakePushTrigger` (`core/push/commonTest/`) — captures trigger calls.
  - `LauncherPushReceiverTestProbe` — captures push events на Android side.

- [x] **CHK007** Tests don't require real Firebase/CF/FCM. JVM tests fakes-only. AndroidTest использует Firebase Emulator (локально). Worker tests через `wrangler dev` (локально).

- [⚠️] **CHK008** DI wiring picks fakes for debug/test, реальные для release.
  - **НЕ explicitly stated в spec**. Project convention уже имеет `mockBackend` / `realBackend` flavors (видно из `app/src/realBackend/` references в codebase).
  - **Assumption**: следуем существующей convention — `mockBackend` flavor wires `FakePushTrigger`, `realBackend` flavor wires `HttpPushTrigger`.
  - **Action**: добавить в spec.md одну строку в Local Test Path: «Flavor split: `mockBackend` → fakes, `realBackend` → реальные HTTP/FCM. Соответствует существующей convention F-5b».

## Fixtures

- [x] **CHK009** Test data в checked-in fixtures.
  - `workers/family-push/test/fixtures/jwks-test.json` — synthetic JWKS.
  - `workers/family-push/test/fixtures/grant-firestore-snapshot.json` — Firestore directory.
  - `core/push/commonTest/resources/push-payload-v1.json` — wire-format roundtrip fixture.

- [⚠️] **CHK010** Fixtures stable across runs (no Random/now).
  - **Concern**: `triggerId: UUID v4` генерируется runtime в `HttpPushTrigger`. Тесты, которые проверяют integration → roundtrip — должны inject FixedUuidGenerator для воспроизводимости.
  - **Concern**: `Idempotency-Key: UUID v4` — то же самое.
  - **НЕ explicitly addressed в spec**. Подразумевается через `FakePushTrigger` который тесты используют, но `HttpPushTrigger` integration test нуждается в clock + UUID injection.
  - **Action**: добавить в spec.md FR-XXX: «`HttpPushTrigger` MUST принимать `UuidGenerator` + `Clock` через constructor (DI) для test reproducibility».

- [x] **CHK011** Cross-version fixtures для wire format.
  - Spec — это первая версия (`schemaVersion: 1`). Нет v0 чтобы тестировать.
  - FR-050: «Roundtrip test (write → read → assert equal). Backward-compat test (read v0 формат, который не существует пока, но contract запрещает регрессии)».
  - Future-compat covered.

## Cannot-test-locally gaps

- [x] **CHK012** Gaps explicitly listed. 4 gap'a:
  1. Real FCM delivery latency from production Google.
  2. Real CF edge CPU limits (free-tier production vs `wrangler dev`).
  3. Multi-device Xiaomi MIUI battery restrictions.
  4. JWKS rotation от Google в production.

- [⚠️] **CHK013** Each gap has TODO(physical-device) or TODO(real-account).
  - OEM matrix gaps имеют explicit `TODO(physical-device)` (Samsung, Xiaomi, Huawei).
  - **JWKS rotation gap** — упомянут «monitor + alert в production», но нет inline TODO маркера в коде.
  - **Real CF edge CPU** — упомянут «manual smoke перед merge», но нет TODO.
  - **Action**: добавить в spec.md (или в Worker source при implementation) inline TODOs:
    - `TODO(production-monitor SRV-PUSH-FOUNDATION): JWKS rotation incident detection — alert if jose verification fails > 1% in 5min window`.
    - `TODO(physical-network): measure real FCM latency p95 from prod Google infrastructure post-release`.

- [⚠️] **CHK014** No gap silently swept under «test in prod».
  - FCM real latency — implicit «test in prod» — acknowledged, no concrete monitoring plan.
  - CF cold start — implicit «test in prod», no concrete plan.
  - **Action**: добавить в SRV-PUSH-FOUNDATION в server-roadmap.md raw section «Production monitoring» с specific metrics к watch: (a) FCM latency p50/p95 per eventType, (b) CF Worker cold start frequency, (c) jose verification failure rate.

## Build cycle

- [⚠️] **CHK015** Clean-build time increase ≤ 30s.
  - Adding new `core/push/` KMP module — adds Gradle dependency resolution, KMP target compilation. **Not measured**.
  - `workers/family-push/` — separate TypeScript build, не влияет на Android build time.
  - Likely 10-30s impact на clean Android build.
  - **Action**: после implementation в `plan.md` — measure clean-build time delta и зафиксировать в плане.

- [⚠️] **CHK016** Manual setup step documented.
  - **НЕ documented в spec**:
    - Создание Cloudflare account.
    - `wrangler` CLI установка.
    - Cloudflare Worker project creation (`wrangler init`).
    - KV namespace creation (`wrangler kv namespace create JWKS_CACHE`, `wrangler kv namespace create IDEMPOTENCY_CACHE`, `wrangler kv namespace create RATE_LIMIT`).
    - `FCM_SERVER_KEY` setup (Firebase Console → Project Settings → Cloud Messaging → Server Key) + `wrangler secret put FCM_SERVER_KEY`.
    - First deploy (`wrangler deploy`).
    - Получение `*.workers.dev` URL для hardcode в client.
  - **Action**: создать `workers/family-push/README.md` с setup instructions. Тhis должно быть part of tasks.md.

- [⚠️] **CHK017** No new credential for debug builds unless documented.
  - Local dev cycle:
    - `wrangler dev` runs Worker локально на `localhost:8787`.
    - No FCM_SERVER_KEY needed для local dev (FakeFcmDispatcher используется).
    - JWKS — synthetic test JWKS fixture.
  - **НЕ documented в spec explicitly**. Implicit через fake adapters list.
  - **Action**: добавить в Local Test Path section: «Local dev: `wrangler dev` без credentials; production deploy: `wrangler secret put FCM_SERVER_KEY` (см. README)».

## Crash + log diagnostics

- [⚠️] **CHK018** Logcat signal for diagnosis.
  - **НЕ explicit FR в spec**. Implicit что `LauncherFirebaseMessagingService` logs.
  - **Action**: добавить FR-XXX: «Push receipt + dispatch MUST emit structured log: Logcat tag `PushReceiver`, fields `{eventType, ownerUid, triggerId, handlerResult}`. Worker MUST emit Cloudflare Analytics event per request с labels `{eventType, status, latency_ms}`».

- [⚠️] **CHK019** Silent failures have opt-in log.
  - FR-023: «Unknown eventType → silent log + ignore» — ✅ unknown eventType.
  - **НЕ covered**:
    - `FcmTokenPublisher.publish()` returning `Outcome.Failure` — log?
    - `HttpPushTrigger.trigger()` returning `Outcome.Failure` (Worker 5xx) — caller does nothing, но должен ли быть log?
    - PushHandler dispatch coroutine cancellation — silent?
  - **Action**: добавить FR-XXX: «All `Outcome.Failure` paths в push subsystem MUST emit Logcat warning. Background coroutine cancellation MUST be wrapped в try/catch с log».

- [N/A] **CHK020** Runtime feature flags loggable. Нет feature flags / remote config в этом spec (Q5 = hardcoded URL). N/A.

## Cross-developer reproducibility

- [x] **CHK021** No developer-machine-specific paths/env. Spec использует generic identifiers (`currentUid`, `ownerUid`). Нет hardcoded user emails / phone numbers / paths.

- [⚠️] **CHK022** Onboarding documented в <1 page.
  - Spec references skill `android-emulator` для эмулятор setup. ✅
  - Push-specific onboarding (Cloudflare Worker setup, FCM key acquisition) — НЕ documented.
  - **Action**: создать `workers/family-push/README.md` (already в CHK016 action) + одну строку в `docs/dev/dev-environment.md`: «Push foundation dev setup: see `workers/family-push/README.md`».

## Summary

- **Pass**: 8/22
- **Partial/Warning**: 13/22
- **Fail**: 0/22
- **N/A**: 1/22 (CHK020)

**Big picture**: Local test path хорошо описан для **happy path** (commands, fakes, fixtures). **Gaps** — в operational concerns:
- Logging / observability требований почти нет (FRs).
- Manual setup process не задокументирован (Cloudflare account, KV namespaces, FCM key).
- Build cycle impact не измерен.
- UUID/Clock injection для test reproducibility не addressed.

Это типичные «забываются при clarify» пункты — критичные при implementation, не блокирующие spec quality.

## Action items (priority order)

1. **Высокая** (для tasks.md когда дойдём): создать `workers/family-push/README.md` с setup instructions (CHK016 + CHK022).
2. **Высокая** (в spec.md): добавить FRs про logging / observability (CHK018, CHK019). Минимум 2 FRs: «Logcat tag + structured fields» и «Outcome.Failure paths логируются».
3. **Средняя** (в spec.md FR): UUID + Clock injection в `HttpPushTrigger` constructor для test reproducibility (CHK010).
4. **Средняя** (в spec.md или server-roadmap): production monitoring spec — FCM latency, CF cold start, jose failure rate (CHK013, CHK014).
5. **Низкая** (в spec.md одна строка): flavor split (mockBackend/realBackend) для DI wiring (CHK008).
6. **Низкая** (в spec.md одна строка): local dev flow без credentials (CHK017).
7. **Низкая** (для plan.md): measure clean-build time delta (CHK015).

---

## Заметка для новичка (TL;DR)

Проверено: сможет ли разработчик за разумное время **собрать, запустить и проверить** эту фичу на своей машине без production-аккаунтов? Базово — да: есть конкретные команды (`./gradlew test`, `wrangler dev`), есть fake-адаптеры (заменители реального Firebase / FCM), есть фикстуры (заготовленные тестовые данные).

**Чего не хватает** (13 «частично» из 22):
- Не прописано как настроить Cloudflare Worker в первый раз (создать account, deploy, get URL).
- Не прописаны требования к логам — если что-то упадёт незаметно (например, отправка push в фон), разработчик не узнает почему.
- Не прописано как делать тесты воспроизводимыми (UUID и время — генерируются каждый раз заново, тесты ломаются).

Это **операционные** проблемы, не архитектурные. Решаются при написании `tasks.md` (создаём README + задачи на логи) и `plan.md`. **Не блокирует** переход к /speckit.scenarios / /speckit.plan, но **обязательно к закрытию** до implementation start.
