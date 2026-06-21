# Checklist: domain-isolation — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass (KMP module + ports/adapters design)

## Vendor SDKs

- [x] **CHK001** No vendor SDK type в domain signatures.
  - `core/push/api/` (public domain port surface):
    - `PushTrigger.trigger(eventType: EventType, scope: TargetScope, ownerUid: String, payload: Map<String, String>): Outcome<Unit, PushTriggerError>` — все типы domain-owned.
    - `PushHandler.handle(payload: PushPayload)` — `PushPayload` domain-owned wire DTO.
    - `EventType`, `TargetScope`, `PushTriggerError` — sealed/enum в `core/push/api/`.
    - `FcmTokenPublisher.publish(token: String)` — `String`, не `FirebaseMessaging` type.
  - Никакого `FirebaseMessaging`, `RemoteMessage`, `Task<T>`, `MessagingException` в public API.

- [x] **CHK002** Each external SDK has exactly one wrapper module (adapter).
  - **FCM SDK** (`com.google.firebase:firebase-messaging-ktx`): wrapped в `LauncherFirebaseMessagingService` + `FcmTokenPublisher` impl. Both в `core/push/androidMain/` (или `core/push-android/`).
  - **Firebase Auth** (для ID-token acquisition): consumed через existing `core/auth/api` port из F-4 (spec 017). Не added в F-5c.
  - **Ktor HTTP client**: lives в `HttpPushTrigger` impl. **Placement uncertain**: spec упоминает `androidMain`, но Ktor — KMP-compatible (доступен в commonMain).
    - **Action из modular-delivery CHK003**: документировать в spec.md HTTP client placement choice.
  - **jose** (Worker JWT verification): TypeScript-only в `workers/family-push/src/auth/jwks-verifier.ts`. Не Android.

- [x] **CHK003** "Vendor disappears tomorrow" test.
  - **FCM**: 3 files в 2 modules (per modular-delivery CHK014):
    - `FcmTokenPublisher` impl (Android).
    - `LauncherFirebaseMessagingService` (Android Service).
    - `workers/family-push/src/dispatch/fcm-dispatcher.ts` (Worker TS).
  - **Cloudflare**: Worker code (full `workers/family-push/`) — port на own backend per SRV-PUSH-FOUNDATION, ~5-7 дней.
  - **jose**: 1 file (`workers/family-push/src/auth/jwks-verifier.ts`).
  - All bounded.

## Transport types

- [x] **CHK004** No transport types в domain signatures.
  - `PushTrigger` port не принимает `HttpRequest`/`HttpResponse`/Ktor types.
  - `PushHandler.handle(payload: PushPayload)` — `PushPayload` domain DTO, не Ktor/Firebase response.
  - HTTP орchestration инкапсулирована в `HttpPushTrigger` impl, ports unaware.

- [x] **CHK005** Wire format type domain-owned, not generated DTO posing as domain model.
  - `PushTriggerRequest` (wire format для HTTP body) — explicit `@Serializable data class` в `core/push/commonMain/internal/`.
  - Serializers (`kotlinx.serialization`) в commonMain — это серверная-агностик lib, не vendor-specific.
  - `PushPayload` (FCM payload) — explicit data class в `core/push/api/`.
  - Не Retrofit/OkHttp/Firebase-generated DTOs.

## Platform types

- [x] **CHK006** No `android.*`, `androidx.*`, `Intent`, `Uri`, `Context`, `Bundle`, `LifecycleOwner` в commonMain.
  - Source files в `core/push/commonMain/` оперируют только Kotlin stdlib + kotlinx.coroutines + kotlinx.serialization + сторонние KMP libs (potentially Ktor).
  - `FirebaseMessagingService` (Android) — strictly androidMain.

- [N/A] **CHK007** Domain values carry domain-typed projection, not raw platform type.
  - F-5c domain values (`EventType`, `TargetScope`) не carry platform-derived data. No platform-typed leakage.

## Ports

- [x] **CHK008** Every external surface exposed через port.
  - FCM token retrieval/rotation → `FcmTokenPublisher` port (Android Firebase Messaging SDK behind).
  - FCM message receipt → `PushHandler` port (Android FirebaseMessagingService behind).
  - HTTP push trigger → `PushTrigger` port (Ktor HTTP client behind).
  - Firebase Auth ID-token → existing `AuthIdentity` port из F-4 (reused, not re-introduced).

- [x] **CHK009** Port shape driven by domain need.
  - `PushTrigger.trigger(eventType, scope, ownerUid, payload)` — domain verbs «trigger event for recipients». Не adapter-shaped (`postJsonToWorker(body)` would be wrong shape).
  - `FcmTokenPublisher.publish(token)` — domain verb «publish my token». Не `writeToFirestore(path, value)`.
  - `PushHandler.handle(payload)` — domain verb «handle incoming event».
  - All match Article XII §2.2 «Domain-driven shapes».

- [x] **CHK010** Each port has fake adapter (commonTest или shared test artifact).
  - `FakePushTrigger` (`core/push/commonTest/`) — captures trigger calls.
  - `FakeFcmTokenPublisher` (`core/push/commonTest/`) — captures publish calls. **НЕ explicit в spec** — assumed.
  - `FakePushHandler` (`core/push/commonTest/`) — assumed per pattern.
  - Worker fakes: `FakeFcmDispatcher`, `FakeFirebaseAuth`, `InMemoryRecipientResolver` (TypeScript-side test helpers).
  - **Action**: добавить в spec.md Local Test Path explicit list: «`FakePushTrigger`, `FakeFcmTokenPublisher`, `FakePushHandler` живут в `core/push/commonTest/fakes/`».

- [x] **CHK011** Each port has real adapter (androidMain).
  - `HttpPushTrigger` → real impl of `PushTrigger`.
  - `FcmTokenPublisher` impl → real impl using Firebase Messaging SDK.
  - `LauncherFirebaseMessagingService` → Android Service that dispatches к `PushHandlerRegistry`.

- [⚠️] **CHK012** DI wiring picks fake/real per build per CLAUDE.md rule §6.
  - **НЕ explicit в spec** (duplicate с dev-experience CHK008 + modular-delivery CHK009 actions).
  - Existing convention: `mockBackend` flavor wires fakes, `realBackend` wires real adapters. F-5b следует этой convention (видно из `app/src/realBackend/`).
  - **Action** (объединить с уже identified): spec.md одна строка «DI wiring per existing flavor split convention».

## Source-set placement

- [⚠️] **CHK013** Every new file assigned к source set с justification.
  - Spec структурирует `core/push/` директорию (видно в spec.md Notes), но не явно justifies каждый file's source-set placement.
  - **Concrete mapping** (моё восстановление):
    - `core/push/commonMain/.../api/*.kt` — pure Kotlin interfaces, no platform deps → commonMain.
    - `core/push/commonMain/.../impl/DefaultPushTrigger.kt` — uses Ktor (KMP) → commonMain.
    - `core/push/commonMain/.../impl/IdempotencyKeyGenerator.kt` — pure Kotlin (UUID generation через `kotlin.uuid.Uuid`) → commonMain.
    - `core/push/commonMain/.../internal/PushTriggerRequest.kt` — kotlinx.serialization → commonMain.
    - `core/push/androidMain/.../HttpPushTrigger.kt` — **должен ли быть android-only?** Если Ktor commonMain — нет, переместить в commonMain как `DefaultPushTrigger`. Если есть Android-specific (например, OkHttp engine pinning) — оставить.
    - `core/push/androidMain/.../FcmTokenPublisher.kt` impl — Firebase Messaging SDK → androidMain (correct).
    - `core/push/androidMain/.../LauncherFirebaseMessagingService.kt` — Android Service → androidMain (correct).
  - **Action**: документировать в spec.md mapping каждого файла к source set с одной строкой justification.

- [⚠️] **CHK014** Default commonMain, deviation has reason.
  - androidMain placement justified для FcmTokenPublisher impl + LauncherFirebaseMessagingService (Firebase SDK + Android Service).
  - `HttpPushTrigger` — placement borderline. Ktor commonMain-compatible. Если поместить в commonMain — meta-isolation strict. Если только androidMain — premature platform-coupling.
  - **Action** (дополнение к CHK013): если HTTP client = Ktor commonMain → `DefaultPushTrigger` в commonMain, не `HttpPushTrigger` в androidMain. Renaming clarity.

## Existing-code regressions

- [x] **CHK015** Spec не reintroduces vendor type в commonMain file already cleansed.
  - F-5b (spec 018) cleansed `core/keys/` от vendor types. F-5c добавляет `core/push/` — нетронут F-5b's namespace.
  - Не impacts `core/auth/`, `core/keys/`, `core/crypto/` commonMain files.

- [x] **CHK016** No new expect/actual без необходимости.
  - Spec не вводит expect/actual.
  - `FcmTokenPublisher` impl полностью в androidMain (не expect/actual interface + per-platform impl). iOS future port будет через separate iOS adapter (например `ApnsTokenPublisher` implementing same port), не expect/actual.

## Summary

- **Pass**: 13/16
- **Partial/Warning**: 3/16 (CHK012, CHK013, CHK014)
- **Fail**: 0/16
- **N/A**: 1/16 (CHK007)

**Big picture**: Domain isolation **clean**:
- Все vendor SDKs за adapters.
- Public ports (`PushTrigger`, `PushHandler`, `FcmTokenPublisher`) типизированы domain types.
- Wire format DTOs domain-owned (`PushTriggerRequest`, `PushPayload`).
- No platform types в commonMain.
- "Vendor disappears" test bounded.

**Concerns** (косметические):
1. DI wiring fake/real split не explicit (CHK012 — dup с dev-experience CHK008, modular-delivery CHK009).
2. Source-set mapping каждого файла не явно (CHK013, CHK014).
3. `HttpPushTrigger` placement (androidMain vs commonMain) требует решения (CHK014 — dup с modular-delivery CHK003).

## Action items (priority order)

1. **Низкая** (одна правка в spec.md, consolidates 4 checklists): добавить блок «Module structure & source-set mapping» который:
   - Перечисляет каждый файл с source set + justification (CHK013).
   - Документирует Ktor HTTP client placement choice (commonMain → `DefaultPushTrigger`, или androidMain → `HttpPushTrigger`) (CHK014).
   - Документирует DI flavor split (mockBackend/realBackend) (CHK012).
2. **Низкая** (одна правка в spec.md Local Test Path): explicit list fake adapter locations (`core/push/commonTest/fakes/`) (CHK010).

---

## Заметка для новичка (TL;DR)

Проверено: правильно ли разделили **домен** (общая логика, чистый Kotlin) от **инфраструктуры** (Firebase, Android, HTTP). Цель — чтобы домен можно было тестировать без эмулятора и в случае ухода Google FCM поменять адаптер за день, а не переписывать пол-проекта.

**Хорошо сделано** (13/16):
- В общем коде (`core/push/api/`) нет ни одного Google/Firebase/Android типа. Только обычный Kotlin.
- Каждый внешний SDK (FCM, jose, Ktor) живёт в **одном** месте за «портом» (интерфейс).
- Если завтра Google отключит FCM — нужно править 3 файла, не 30.

**Чего не хватает** (3 «частично»):
- Не записано явно «какой файл в каком source-set лежит» (общий код vs Android-only). Это важно для KMP — путаница приведёт к build errors.
- HTTP-клиент (Ktor) — может жить в общем коде, может только в Android-коде. Spec не выбрал.
- DI (как «склеиваются» интерфейсы и реализации) — следуем существующей конвенции проекта, но не записали явно.

Это **не блокирует** /speckit.plan. Закрывается одним блоком «Module structure» в spec.md (~15 строк).
