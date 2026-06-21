# Checklist: modular-delivery — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass (CRITICAL — re-scope ввёл new gradle module + new Worker artifact)

## Scope of the feature

- [x] **CHK001** Spec states form-factor classification.
  - **Form-factor-agnostic**: F-5c — push messaging infrastructure. Работает на любом Android устройстве с FCM (handheld, foldable, tablet) и в будущем — на любой платформе с push-эквивалентом (iOS через APNS adapter — future port).
  - Не handheld-only, не TV-specific, не voice-specific.

- [N/A] **CHK002** Form-factor-specific module placement.

- [x] **CHK003** Form-factor-agnostic: no vendor SDK / platform API / form-factor UI assumption leaks в shared code.
  - `core/push/commonMain/` (KMP shared) содержит только port interfaces (`PushTrigger`, `PushHandler`, `EventType`, `TargetScope`, `PushPayload`) — нет Android types, нет FCM SDK.
  - Vendor SDK (`FirebaseMessaging`) живёт только в `core/push/androidMain/` (или `core/push-android/`) adapter (`FcmTokenPublisher` impl, `LauncherFirebaseMessagingService`).
  - HTTP client (Ktor) — в `commonMain/impl/` (KMP-compatible) или `androidMain/` если Android-specific. **Spec не уточняет**. Per CLAUDE.md rule 1, должен быть `commonMain` чтобы port работал на iOS/desktop в будущем.
  - **Action**: документировать в spec.md «HTTP client choice: Ktor в commonMain для KMP portability».

## Module placement

- [x] **CHK004** No new vendor SDK в Core.
  - FCM SDK (`firebase-messaging-ktx`) → `core/push/androidMain/`.
  - Ktor → `core/push/commonMain/impl/` (KMP-compatible, не vendor-specific в плане platform lock-in).
  - Firebase Auth (для acquiring ID-token) → consumed через existing `core/auth/api` port (F-4 spec 017), не added к F-5c.
  - Worker — отдельный artifact (`workers/family-push/`), не Gradle module Android codebase.

- [⚠️] **CHK005** Article V §7 answered для каждого нового Gradle module.
  - `core/push/` — answered partially:
    - **Why is package not enough?** Spec implies extraction-readiness ([TODO-ARCH-017](../../docs/dev/project-backlog.md)). Дешёво держать module сейчас vs дорого refactor package→module позже (когда V-2 trigger).
    - **What API boundary does it protect?** `api/` package public — port surface для всех consumers (ConfigSaver, future SosService, MessageService, ...). `impl/` + `internal/` invisible.
    - **What complexity does it remove now?** Forces strict isolation — нельзя случайно импортировать launcher-specific код в push foundation.
  - **Status**: оправдано, но требует одну строку в spec.md обоснования. **Action duplicate с meta-minimization CHK006**.

- [N/A] **CHK006** Form-factor-specific code in Core with regret condition. Не applicable — form-factor-agnostic.

## Profile / preset declaration

- [N/A] **CHK007** Profile modifications. Спека не вводит/не модифицирует profiles.

- [N/A] **CHK008** Profile schema bump.

- [⚠️] **CHK009** Base application loads без module — user-visible degradation documented.
  - **Concern**: после F-5c, `ConfigSaver` будет invoking `PushTrigger.trigger(...)` после save. Если `core/push/` module absent — `ConfigSaver` не compileт.
  - **Practical implication**: `core/push/` становится **hard dependency** для cloud features.
  - **Local mode** (per decision 2026-06-15-deferred-cloud): device self-sufficient без Sign-In. В local mode `ConfigSaver` не используется (per F-5b — ConfigSaver работает в cloud namespace only). Локальная конфигурация хранится через другой механизм.
  - **Status**: эффективно `core/push/` нужен только для cloud features. Если cloud features compile-time optional (build flavor split) — push module можно делать optional.
  - **НЕ documented в spec**: how cloud features compile when push module absent.
  - **Action**: документировать в spec.md «`core/push/` зависимость: hard для realBackend flavor (cloud features), не required для mockBackend (local-only dev/test). DI provides no-op `NullPushTrigger` для случаев когда push channel недоступен».

## Form-factor expansion

- [N/A] **CHK010** Non-handheld form factor delivery channel.
- [N/A] **CHK011** Form-factor-specific vendor SDKs placement.
- [N/A] **CHK012** First non-handheld form factor — ADR for delivery channel.

## One-way doors raised by the feature

- [x] **CHK013** No irreversible dependency/identifier/wire format без exit ramp.
  - PushPayload breaking change → exit ramp = schemaVersion bump 1→2 + parallel reads (rule 5).
  - Hardcoded Worker URL → exit ramp [TODO-ARCH-001](../../docs/dev/project-backlog.md) (custom domain).
  - CF Worker hosting → exit ramp [SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md) (own backend, 5-7 дней).
  - FCM как push transport → exit ramp = swap dispatch layer в Worker для APNS/WebPush/own server.
  - Все documented.

- [x] **CHK014** "Vendor disappears tomorrow" test answered.
  - **FCM deprecated/disabled by Google**:
    - Worker side: replace `dispatch/fcm-dispatcher.ts` для альтернативы (WebPush / APNS / own push protocol). 1 file.
    - Android side: replace `FcmTokenPublisher` impl + `LauncherFirebaseMessagingService` для другого SDK. 2 files.
    - Total: 3 files in 2 modules. Bounded.
  - **Cloudflare exits/double price**:
    - Per SRV-PUSH-FOUNDATION — port Worker logic to own Ktor server. ~5-7 дней. Client URL update (см. TODO-ARCH-001).
  - **Firebase Auth deprecated**:
    - Worker side: replace `jose` JWKS verification для другой OIDC provider. Same library.
    - Client side: replaces ID-token acquisition (F-4 spec 017 territory).
    - Bounded.

- [⚠️] **CHK015** Free workaround → server-roadmap entry + inline TODO planned.
  - **CF Worker free tier**: ✅ entry `SRV-PUSH-FOUNDATION` exists. Inline TODOs planned (per SRV-PUSH-FOUNDATION list).
  - **Workers KV free tier** (100K reads/day): **НЕТ отдельного entry**. Implicit часть CF stack.
  - **Spark plan FCM (10K push/day)**: упомянуто в Assumptions, но **НЕТ entry в server-roadmap** для quota exhaustion exit ramp.
  - **In-memory rate-limit fallback** (если KV недоступен — нет в spec, но возможный edge case): не addressed.
  - **Action**: добавить в server-roadmap.md sub-section к SRV-PUSH-FOUNDATION:
    - SRV-PUSH-QUOTA: «при Spark plan FCM quota исчерпан → upgrade Blaze ($25/month) или client-side throttle».
    - SRV-PUSH-KV: «при Workers KV quota исчерпан → upgrade CF paid tier или own Redis».

## Anti-bloat sanity

- [x] **CHK016** No module for single class / single-implementation interface.
  - `core/push/` содержит multiple classes:
    - `api/` (7 файлов: PushTrigger, PushHandler, PushHandlerRegistry, EventType, TargetScope, PushPayload, PushTriggerError).
    - `impl/` (3+ файла).
    - `internal/` (2+ файла wire DTOs).
    - `commonTest/` + `androidMain/`.
  - Не single class, не single-impl interface.

- [⚠️] **CHK017** No pre-emptive split "in case we go multi-form-factor later" без actual consumer.
  - `core/push/` как отдельный Gradle module — extraction-ready design без actual second consumer **сейчас**. Single consumer (launcher app) на момент F-5c.
  - **Однако** — 9 known consumers planned (S-2, S-4, S-9, S-10, V-2, V-3, V-6, spec 008 rewrite, config-updated). Это **actual consumers** в roadmap, не «in case».
  - **Justified** через rule 4 self-test (см. meta-minimization CHK010, CHK011 — Tests 1 и 2 passed).
  - **Tension**: между «не split preemptively» и «9 known consumers force rewrite». Решено в favor of split, с documented justification.

- [x] **CHK018** Future split (extraction to separate repos) recorded as regret condition не implemented now.
  - Trigger: **V-2 spec start** (Phase 4 Messenger как отдельное приложение).
  - Documented in [TODO-ARCH-017](../../docs/dev/project-backlog.md#todo-arch-017) + [SRV-PUSH-EXTRACTION](../../docs/dev/server-roadmap.md#srv-push-extraction-push-foundation--отдельный-repo-spec-019-f-5c-future).
  - Сейчас live в monorepo — правильное решение для one consumer.

## Summary

- **Pass**: 8/18
- **Partial/Warning**: 3/18 (CHK003, CHK005, CHK009, CHK015, CHK017)
- **Fail**: 0/18
- **N/A**: 7/18 (CHK002, CHK006, CHK007, CHK008, CHK010, CHK011, CHK012)

**Big picture**: Modular delivery — **clean**:
- Form-factor-agnostic infrastructure.
- Vendor SDK isolation (FCM в Android adapter, не в Core).
- All one-way doors имеют exit ramps.
- Extraction-readiness — design discipline, не premature work.

**Concerns** (cosmetic):
1. HTTP client choice (Ktor location в commonMain/androidMain) не documented (CHK003).
2. Module justification "why not package" requires one-line addition (CHK005 — dup с meta-minimization).
3. `core/push/` hard-dependency для cloud features vs optional для local mode — не explicit (CHK009).
4. Workers KV + Spark plan FCM quota — exit ramps не отдельные entries в server-roadmap (CHK015).
5. 9 known consumers vs «pre-emptive split» tension — оправдано но требует phrasing в spec.md (CHK017).

## Action items (priority order)

1. **Низкая** (одна правка в spec.md, дополняет meta-minimization actions): добавить одну строку «Why module not package: extraction-ready preserves at near-zero cost; package→module refactor expensive (CHK005)».
2. **Низкая** (одна правка в spec.md): «HTTP client: Ktor в commonMain для KMP portability (CHK003)».
3. **Средняя** (одна правка в spec.md): описать local-mode behavior — `NullPushTrigger` no-op в DI при absence cloud (CHK009).
4. **Низкая** (в server-roadmap.md): добавить SRV-PUSH-QUOTA + SRV-PUSH-KV sub-entries (CHK015).

---

## Заметка для новичка (TL;DR)

Проверено: правильно ли разложили код по модулям, не раздули ли «на всякий случай», не зашили ли намертво вендора (Google FCM) в место, откуда его потом не вытащить.

**Хорошо сделано**:
- Push-инфраструктура не привязана к конкретному устройству (телефон / планшет / в будущем TV).
- Google FCM SDK живёт только в Android-адаптере; «домен» (общая логика) его не знает.
- Все «бесплатные обходы» (Cloudflare Worker, Spark plan) явно записаны с маршрутом «куда переезжать когда станет тесно».
- Foundation спроектирована для извлечения в отдельный repo, но это **не сделано сейчас** (один consumer — launcher) — записано в backlog как «делать когда начнётся V-2 Messenger».

**Чего не хватает** (косметика, 5 «частично»):
- Не записано «когда нет облака — push-модуль работает как заглушка» (важно для local mode).
- Не записано в каком модуле живёт HTTP-клиент (Ktor).
- Quota limits для Workers KV и FCM Spark plan не оформлены отдельными exit ramp'ами.

Это **не блокирует** /speckit.plan. Решается 5 строк правок в spec.md + 2 entries в server-roadmap.md.
