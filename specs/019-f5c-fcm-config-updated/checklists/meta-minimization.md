# Checklist: meta-minimization — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass (особо критичен — re-scope расширил scope с узкого на foundation)

> **Контекст оценки**: re-scope 2026-06-20 evening явно изменил scope с «узкий config-updated» на «generic foundation + first use case». Это **prima facie meta-violation** против rule 4. Однако в spec.md задокументирован rule 4 self-test с 9 known consumers (S-2, S-4, S-9, S-10, V-2, V-3, V-6, spec 008, config-updated), который явно доказывает что узкий путь forces rewrite × 9. Поэтому каждый CHK ниже должен оценивать НЕ «есть ли abstraction», а **«оправдана ли она rule 4 critically applied»**.

## New abstractions

- [⚠️] **CHK001** Every new interface has concrete consumer in this spec.
  - `PushTrigger` → consumer = `ConfigSaver` (этот spec). ✅
  - `EventType` (sealed) → используется `ConfigSaver`. ✅
  - `TargetScope` (enum) → `OwnAndGrants` использует `ConfigSaver`. ✅
  - `PushHandler` → `ConfigUpdatedHandler` (этот spec). ✅
  - `PushHandlerRegistry` → `LauncherFirebaseMessagingService` (этот spec, реализует dispatch). ✅
  - `FcmTokenPublisher` → app startup hook + `FirebaseMessagingService.onNewToken` (этот spec). ✅
  - `EventTypeRegistry` (Worker TypeScript) → `config-updated` entry + foundation logic в этом spec'е. ✅
  - **Все 7 abstractions имеют concrete consumer В ЭТОМ spec'е**. Не нарушение CHK001 в строгом чтении.

- [x] **CHK002** Single-implementation interfaces justified.
  - `PushTrigger` — два impls: `HttpPushTrigger` (Android real) + `FakePushTrigger` (test fakes). DI justified.
  - `EventType` (sealed) — type-safety, not single-impl problem.
  - `TargetScope` (enum) — type-safety.
  - `PushHandler` — `ConfigUpdatedHandler` сейчас. Multiple impls **планируются** в future consumers (SosTriggeredHandler, MessageArrivedHandler, ...). По строгому чтению — single impl сейчас. Но registry pattern требует interface (см. CHK004).
  - `FcmTokenPublisher` — real + fake adapter. DI justified.

- [x] **CHK003** Mediator/orchestrator justified by transformation.
  - `PushHandlerRegistry` — dispatch table: `Map<EventType, PushHandler>` + lookup-and-invoke. Технически transformation (eventType string → handler instance).
  - `HttpPushTrigger` — orchestrates Firebase ID-token acquisition + UUID generation + HTTP call + error mapping. Transformation, not pass-through.
  - Не pass-through wrappers.

- [⚠️] **CHK004** No custom DSL/registry/plugin system unless simpler composition tried and documented as failing.
  - **Есть две registries**: `EventTypeRegistry` (Worker) + `PushHandlerRegistry` (client).
  - **Simpler composition** would be: hardcoded switch statements `if (eventType === 'config-updated') {...} else if (...) {...}`.
  - **Не tried explicitly** в этом spec'е. Однако:
    - В Worker — switch statement в entrypoint означает что каждый новый event type требует правки Worker entrypoint code. Это и есть проблема которую foundation решает.
    - В client — switch statement в `LauncherFirebaseMessagingService.onMessageReceived` означает то же самое для receiver side.
  - **Argument for registry**: explicit goal foundation'а — «add event type without touching foundation» (FR-060). Registry — минимальная abstraction которая этого достигает.
  - **Status**: borderline. Не было formal «tried switch, failed» документации, но архитектурный аргумент очевиден для 9 known consumers.
  - **Action**: добавить в spec.md одну строку «Why registry over switch: switch requires foundation modification per new event type (FR-060 contradiction)».

## New modules / packages

- [x] **CHK005** New gradle module satisfies Article V §3.
  - `core/push/` — ownership boundary (push infrastructure), build isolation (Kotlin + own deps), stable API (`api/` package), material testability gain (`FakePushTrigger`).
  - `workers/family-push/` — separate language/runtime (TypeScript vs Kotlin). Совершенно independent build/deploy.
  - Оба satisfy.

- [⚠️] **CHK006** Why is package not enough?
  - `core/push/` MOG быть package внутри existing module (например `core/push` package в `core/launcher` или `app`). Не сделан package'ом потому что extraction-ready design ([TODO-ARCH-017](../../docs/dev/project-backlog.md#todo-arch-017)).
  - **Argument**: extraction-readiness — «future need» (V-2 spec start). Per rule 4 strict reading, можно было сделать package сейчас, refactor в module при V-2 trigger.
  - **Counter-argument**: package→module refactor требует rebuild dependency graph + переименовать всех imports across launcher. Module сейчас = one extra `build.gradle.kts`, ~30 строк. Extraction-readiness стоит почти ноль; refactor стоит дни.
  - **Status**: borderline. Justified тонко.
  - **Action**: документировать в spec.md «Why module not package: extraction-readiness preserved at near-zero cost; package→module refactor expensive».

- [x] **CHK007** No utils/common/helpers dumping ground.
  - `core/push/impl/` — concrete implementations. Не dumping.
  - `core/push/internal/` — wire-format DTOs. Не dumping.
  - `workers/family-push/src/auth/`, `dispatch/`, `dedupe/`, `ratelimit/` — domain-grouped, не utils.

## New configuration

- [x] **CHK008** New config field has current FR consuming it.
  - `schemaVersion` → FR-013, FR-050.
  - `eventType` → FR-001, FR-004.
  - `targetScope` → FR-007.
  - `ownerUid` → FR-007.
  - `payload` (Map<String, String>) → FR-001 (generic), FR-040 (config-updated specific).
  - `triggerId` → FR-024, FR-044.
  - `Idempotency-Key` header → FR-010.
  - `Authorization` header → FR-002.
  - `linkId: String?` (deprecated) → FR-024 (backward-compat parsing старых событий из spec 007 codebase, не в проде).

- [x] **CHK009** Config field defaults documented, backward-compat policy.
  - Defaults: `schemaVersion: Int = 1`, `linkId: String? = null`, `payload: Map<String, String>? = null`.
  - Backward-compat policy: FR-051 (additive vs major bump rule, parallel reads на 1 release).
  - Migration path: FR-051.

## CLAUDE.md rule 4 self-test

- [x] **CHK010** Test 1 (if inlined, what lost?).
  - **PushTrigger port**: if inlined в ConfigSaver — ConfigSaver делает HTTP + JWT + idempotency directly. Когда S-4 SosService приходит — must duplicate JWT/HTTP/idempotency code. **Rewrite × N consumers, not addition.** Test passes.
  - **EventTypeRegistry**: if inlined as switch в Worker — каждый новый event type требует Worker entrypoint modification + redeploy (cannot add event by adding only new files). **Rewrite of foundation per consumer.** Test passes.
  - **PushHandlerRegistry**: similar argument для receiver side.
  - **FcmTokenPublisher**: if inlined в `EnvelopeBootstrap` — token rotation triggers re-publish pub key (lifecycle mismatch waste). Минор — но **не** rewrite. Borderline.
  - **Documented in spec.md re-scope discussion (9 known consumers table)**.

- [x] **CHK011** Test 2 (if dependency doubled / deprecated / privacy violation, swap cost).
  - **FCM deprecated tomorrow**: with abstraction (PushTrigger port) → swap `HttpPushTrigger` implementation для WebSocket/Pushover/own backend. Days.
  - **Without abstraction**: 9 places (one per consumer) to rewrite + each consumer's tests + each consumer's DI. Weeks.
  - **Cloudflare doubles в цене**: with abstraction (Worker as one of impl) → port Worker code на own backend (per SRV-PUSH-FOUNDATION). 5-7 дней (one place).
  - **Privacy violation FCM token storage**: with FcmTokenPublisher port → swap storage adapter. Hours.
  - **Documented in SRV-PUSH-FOUNDATION + SRV-PUSH-EXTRACTION**.

## Removal validation

- [⚠️] **CHK012** Spec removes existing abstractions — dangling references audited.
  - **Removed/breaking-changed**: `PushPayload` shape changes — `linkId` from required to optional, добавлены first-class fields (`ownerUid`, `eventType`, `triggerId`, `payload`).
  - **Existing consumers of old `PushPayload.linkId`**: spec 007 + spec 008 (legacy). Spec 008 rewrite Шаг 4b парallel.
  - **Audit**: NOT done explicitly в этом spec'е. Spec говорит «единственный consumer spec 008, переписывается параллельно» но не привёл grep results.
  - **Action**: запустить `Grep "linkId" core/ app/` чтобы убедиться что нет других consumers. Если есть — добавить в spec 008 rewrite scope или handle в этом spec'е.

- [⚠️] **CHK013** Deprecated markers have removal tasks.
  - `linkId: String? = null` (deprecated) — должен быть removed в schemaVersion 2 bump.
  - **Когда?**: «после того как spec 008 rewrite ships». Не specific task в tasks.md (его ещё нет).
  - **Action**: после `/speckit.tasks` — добавить task «remove deprecated linkId from PushPayload + schemaVersion bump к 2» в спеку 008 rewrite tasks.md (когда она появится). Сейчас inline TODO в spec.md.

## Summary

- **Pass**: 9/13
- **Partial/Warning**: 4/13 (CHK004, CHK006, CHK012, CHK013)
- **Fail**: 0/13

**Big picture**: re-scope от «узкий» к «foundation» — **оправдан** по rule 4 (9 known consumers force rewrite). Все 7 новых abstractions имеют concrete consumer в этом spec'е (CHK001). Все sealed/enum impls JUSTIFIED type-safety или DI. Tests 1 и 2 явно покрыты (CHK010, CHK011) с количественными estimate'ами.

**Concerns**:
1. Registry pattern — formal justification «simpler composition tried and failed» не сделана. Архитектурный аргумент очевиден но не задокументирован одной фразой.
2. Module vs package — borderline, оправдывается дешёвостью module-сейчас vs refactor-позже.
3. Audit dangling references на `linkId` НЕ запущен (только assertion в spec.md).
4. Deprecation marker (`linkId`) не привязан к specific removal task.

## Action items

1. **Низкая стоимость**: добавить в spec.md одну строку «Why registry over switch» (закрывает CHK004) и «Why module not package» (закрывает CHK006).
2. **Средняя стоимость**: запустить `Grep linkId` audit чтобы убедиться, что нет других consumers кроме spec 008. Закрывает CHK012.
3. **Низкая стоимость**: inline TODO в spec.md «remove deprecated linkId in schemaVersion 2 bump after spec 008 rewrite ships». Закрывает CHK013.

---

## Заметка для новичка (TL;DR)

Проверено: не добавили ли мы «архитектурное барахло на будущее»? Re-scope с узкой мини-спеки на foundation — **это потенциально подозрительно**, потому что обычно «делать generic» = anti-pattern. Но в нашем случае мы доказали: 9 known consumers (SOS, Health, Messenger, Album, ...) уже в roadmap'е, и **без** foundation придётся писать всё 9 раз. Это rule 4 PASS, не violation.

Из 13 проверок 9 чистых, 4 «частично». Реальных проблем нет — нужны три косметические правки в spec.md:
1. Объяснить в одной строке зачем registry а не switch.
2. Объяснить зачем gradle module а не package.
3. Убедиться (grep'ом) что нет других consumers старого `linkId` поля.

Это **не блокирует** переход к /speckit.plan — это уточнения в spec.md, которые делаются за 5 минут.
