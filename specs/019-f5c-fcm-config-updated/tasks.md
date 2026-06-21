# Tasks: F-5c — Push-trigger Foundation + `config-updated` Event

**Input**: Design documents from `/specs/019-f5c-fcm-config-updated/`
**Prerequisites**: [plan.md](plan.md) ✅, [spec.md](spec.md) ✅, [research.md](research.md) ✅, [data-model.md](data-model.md) ✅, [contracts/](contracts/) ✅.

**Tests**: Тесты обязательны per CLAUDE.md rule 6 (mock-first) + rule 7 (fitness functions) + Article X. Каждый port имеет fake adapter, каждый wire-format имеет roundtrip + backward-compat tests.

**Organization**: Tasks grouped по 4 фазам plan.md → US coverage в каждой:
- Phase 1: Setup (gradle modules + Cloudflare KV + deps).
- Phase 2: Foundational (wire format + ports + fakes + Worker entrypoint).
- Phase 3: First use case (US-0 + US-1 — config-updated integration).
- Phase 4: Migration existing push subsystem (8 files).
- Phase 5: US-2/US-3 (cross-UID + graceful degradation E2E coverage).
- Phase 6: Polish (perf checkpoint + docs + fitness + smoke).

**Format**: `[ID] [P?] [Story] Description` — trace в parentheses `(FR/US/Plan)`.

**Path conventions**: KMP project (`core/push/commonMain/`, `core/push/androidMain/`, `core/push/commonTest/`) + TypeScript Workers (`workers/push/`, `workers/_shared/auth-jwt/`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Создать пустые модули + конфиги + dependencies, без бизнес-логики.

- [ ] **T001** Создать Gradle module `core/push/` (`build.gradle.kts` с KMP plugin, target Android + JVM, dependencies: kotlinx-coroutines, kotlinx-serialization, Ktor client core + engine cio). (Plan §Architecture). Acceptance: `./gradlew :core:push:assemble` зелёный.
- [ ] **T002** [P] Создать TypeScript module `workers/_shared/auth-jwt/` (`package.json` name `@familycare/auth-jwt`, dependencies: `jose`, dev: `@cloudflare/workers-types`, `vitest`, `tsconfig.json` strict). (Plan §Architecture). Acceptance: `cd workers/_shared/auth-jwt && npm install` без ошибок.
- [ ] **T003** [P] Создать TypeScript module `workers/push/` (`package.json` depends on `@familycare/auth-jwt` via `file:../_shared/auth-jwt`, `wrangler.toml` skeleton). (Plan §Architecture). Acceptance: `cd workers/push && npm install` без ошибок.
- [ ] **T004** [P] Создать KV namespaces в Cloudflare (`wrangler kv:namespace create JWKS_CACHE`, `IDEMPOTENCY_CACHE`, `RATE_LIMIT` + preview varianti). Записать IDs в `workers/push/wrangler.toml` `[[kv_namespaces]]` sections. (quickstart.md §1.3). Acceptance: `wrangler kv:namespace list` показывает 3 namespace × 2 (prod + preview).
- [ ] **T005** [P] Создать Cloudflare secrets для production deploy: `wrangler secret put FCM_SERVER_KEY` + `wrangler secret put FIREBASE_PROJECT_ID`. Для local dev — создать `workers/push/.dev.vars` (gitignored) с fake values. (quickstart.md §1.4). Acceptance: secrets visible в `wrangler secret list`; `.dev.vars` в `.gitignore`.

**Checkpoint Phase 1**: Все модули + tooling готовы для разработки. Локально `wrangler dev` startup'нет.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wire format DTOs + ports + fakes + Worker entrypoint без integration. **⚠️ BLOCKS все US** — нельзя начать integration без foundation.

### 2.1 Wire format DTOs + serializers

- [ ] **T010** [P] Создать `core/push/commonMain/api/WireFormatVersion.kt` с `const val MAX_SUPPORTED_SCHEMA_VERSION: Int = 1`. (FR-013, data-model.md §WireFormatVersion). Acceptance: compile зелёный.
- [ ] **T011** [P] Создать `core/push/commonMain/api/PushPayload.kt` data class (schemaVersion, eventType, ownerUid, triggerId, fields, linkId nullable). (FR-024, data-model.md §PushPayload, contracts/push-payload-v1.md). Acceptance: compile.
- [ ] **T012** [P] Создать `core/push/commonMain/api/TargetScope.kt` enum + `fromWireOrNull`. (FR-022, data-model.md §TargetScope). Acceptance: compile.
- [ ] **T013** [P] Создать `core/push/commonMain/api/EventType.kt` sealed interface с `wireValue` + `handlerTimeout: Duration` + companion `fromWireOrNull` + `data object ConfigUpdated`. (FR-021, FR-077, data-model.md §EventType). Acceptance: compile.
- [ ] **T014** [P] Создать `core/push/commonMain/api/PushTriggerError.kt` sealed class (Unauthorized / RateLimited / NetworkFailure / Backend). (FR-076, data-model.md §PushTriggerError). Acceptance: compile.
- [ ] **T015** Создать `core/push/commonMain/internal/PushTriggerRequest.kt` (`@Serializable` data class). Requires T010-T014. (data-model.md §PushTriggerRequest). Acceptance: compile + kotlinx-serialization annotation processes.
- [ ] **T016** Создать `core/push/commonMain/internal/PushTriggerWireFormat.kt` (encode/decode `Map<String, String>` ↔ PushPayload + helpers для flat fields). Requires T011. (contracts/push-payload-v1.md). Acceptance: compile.

### 2.2 Ports (public API)

- [ ] **T020** [P] Создать `core/push/commonMain/api/PushTrigger.kt` interface (`suspend fun trigger(...)` returning `Outcome<Unit, PushTriggerError>`). Requires T013, T012, T014. (FR-020, data-model.md §PushTrigger). Acceptance: compile.
- [ ] **T021** [P] Создать `core/push/commonMain/api/PushHandler.kt` interface (`suspend fun handle(payload: PushPayload)`). Requires T011. (FR-023, data-model.md §PushHandler). Acceptance: compile.
- [ ] **T022** [P] Создать `core/push/commonMain/api/PushHandlerRegistry.kt` interface (`fun handlerFor(eventType: EventType): PushHandler?`). Requires T013, T021. (FR-023). Acceptance: compile.
- [ ] **T023** [P] Создать `core/push/commonMain/api/FcmTokenPublisher.kt` interface + `FcmTokenPublisherError` sealed. (FR-027, data-model.md §FcmTokenPublisher). Acceptance: compile.
- [ ] **T024** [P] Создать `core/push/commonMain/api/BackgroundDispatcher.kt` interface + `RetryStrategy` sealed. (FR-078, data-model.md §BackgroundDispatcher). Acceptance: compile.

### 2.3 Fake adapters (per CLAUDE.md rule 6)

- [ ] **T030** [P] Создать `core/push/commonTest/fakes/FakePushTrigger.kt` — captures trigger calls в list для assertion. Requires T020. (Plan §Test strategy). Acceptance: `./gradlew :core:push:test` зелёный (test using fake).
- [ ] **T031** [P] Создать `core/push/commonTest/fakes/FakeFcmTokenPublisher.kt` — captures publish calls. Requires T023. (Plan §Test strategy). Acceptance: test compile зелёный.
- [ ] **T032** [P] Создать `core/push/commonTest/fakes/FakePushHandler.kt` — captures handle calls. Requires T021. (Plan §Test strategy). Acceptance: test compile.
- [ ] **T033** [P] Создать `core/push/commonTest/fakes/FakePushHandlerRegistry.kt` — in-memory map. Requires T022, T032. (Plan §Test strategy). Acceptance: test compile.
- [ ] **T034** [P] Создать `core/push/commonTest/fakes/FakeBackgroundDispatcher.kt` — synchronously executes block без real WorkManager. Requires T024. (Plan §Test strategy). Acceptance: test compile.

### 2.4 Wire format contract tests (per CLAUDE.md rule 5)

- [ ] **T040** Создать fixture `core/push/commonTest/resources/push-payload-v1.json` (per contracts/push-payload-v1.md). Acceptance: file exists + valid JSON.
- [ ] **T041** Создать fixture `core/push/commonTest/resources/push-trigger-request-v1.json` (per contracts/push-trigger-request-v1.md). Acceptance: file exists.
- [ ] **T042** Создать `core/push/commonTest/WireFormatRoundtripTest.kt` — read fixtures → deserialize → assertEquals; serialize → assertEquals against fixture. Requires T011, T015, T016, T040, T041. (FR-050, contracts roundtrip section). Acceptance: `./gradlew :core:push:test --tests *WireFormatRoundtripTest*` зелёный.
- [ ] **T043** Создать `core/push/commonTest/WireFormatBackwardCompatTest.kt` — read fixture с legacy `linkId` field → assert linkId populated, new fields parse correctly. (FR-051, contracts §Versioning). Acceptance: test passes.
- [ ] **T044** Создать `core/push/commonTest/WireFormatForwardCompatTest.kt` — fixture с unknown extra field + schemaVersion=2 → parse не crashes, schemaVersion=2 → silent ignore at receiver path. (FR-013, Wire-format policy). Acceptance: test passes.

### 2.5 Default impls (foundation)

- [ ] **T050** Создать `core/push/commonMain/impl/IdempotencyKeyGenerator.kt` — UUID v4 generation + injection-friendly interface (для FixedUuidGenerator в tests). (CHK010 dev-experience). Acceptance: compile + test с FixedUuidGenerator passes.
- [ ] **T051** Создать `core/push/commonMain/impl/DefaultPushHandlerRegistry.kt` — Map-backed implementation. Requires T022. Acceptance: `PushHandlerRegistryTest.kt` зелёный (T052).
- [ ] **T052** Создать `core/push/commonTest/PushHandlerRegistryTest.kt` — register handler, lookup hit + miss, idempotent registration. Requires T051. Acceptance: test passes.
- [ ] **T053** Создать `core/push/commonMain/impl/NullPushTrigger.kt` — no-op implementation (returns Outcome.Success без HTTP). Requires T020. (CHK-DSS-007, FR-020). Acceptance: compile + smoke test.
- [ ] **T054** Создать `core/push/commonMain/impl/DefaultPushTrigger.kt` — orchestrator using Ktor HTTP client + AuthIdentity (via constructor) + IdempotencyKeyGenerator + Clock. Requires T015, T020, T050. (Plan §Architecture, FR-025). Acceptance: compile.

### 2.6 Auth-jwt module

- [ ] **T060** Создать `workers/_shared/auth-jwt/src/types.ts` — `Claims`, `VerificationResult`, `VerificationError` exports. (data-model.md §VerificationResult). Acceptance: tsc clean.
- [ ] **T061** Создать `workers/_shared/auth-jwt/src/jwks-cache.ts` — KV-based cache с dynamic TTL parsing `Cache-Control: max-age`. (FR-003, Q4 resolution). Acceptance: unit test mocks fetch, asserts TTL parsing.
- [ ] **T062** Создать `workers/_shared/auth-jwt/src/claims.ts` — validation rules (alg, kid, iss, aud, exp, iat, sub). Requires T060. (FR-002, Q4 resolution). Acceptance: unit tests для каждого invalid case.
- [ ] **T063** Создать `workers/_shared/auth-jwt/src/index.ts` — public `verifyFirebaseIdToken(token, projectId, kvBinding)` orchestrating jose verify + jwks-cache + claims. Requires T060-T062. (data-model.md §VerificationResult). Acceptance: integration test с test JWKS fixture.
- [ ] **T064** Создать test fixtures `workers/_shared/auth-jwt/test/fixtures/` — `jwks-test.json` (synthetic JWKS), `valid-token.json`, `expired-token.json`, `wrong-iss-token.json`, `wrong-aud-token.json`, `malformed-header.json`, `kid-not-in-jwks.json`. Acceptance: 6+ fixture files, valid JSON.
- [ ] **T065** Создать `workers/_shared/auth-jwt/test/jwks-verifier.test.ts` — каждый VerificationError variant covered. Requires T063, T064. Acceptance: `npm test` в `workers/_shared/auth-jwt/` зелёный.
- [ ] **T066** [P] Создать `workers/_shared/auth-jwt/test/generate-test-token.js` — utility для генерации test tokens (quickstart.md §3). Acceptance: script runs + outputs valid JWT signed by test JWKS.
- [ ] **T067** [P] Создать `workers/_shared/auth-jwt/README.md` — public API + setup + integration example. Acceptance: README ≥ 1 page, includes code samples.

### 2.7 Push Worker foundation (без event-type-specific logic)

- [ ] **T070** Создать `workers/push/src/contract/wire-format.ts` — `PushTriggerRequest`, `PushTriggerResponse` TypeScript interfaces (MUST match Kotlin DTOs). Acceptance: tsc clean, manual review confirms match.
- [ ] **T071** Создать `workers/push/src/registry/event-types.ts` с `EVENT_TYPES` const + `EventTypeRegistryEntry` interface. Изначально только заготовка empty registry. Requires T070. (contracts/event-type-registry.md). Acceptance: tsc clean.
- [ ] **T072** Создать `workers/push/src/auth/event-authorisation.ts` — `hasActiveWriteGrant(callerUid, ownerUid)` Firestore REST API helper. Acceptance: unit test с mock Firestore.
- [ ] **T073** Создать `workers/push/src/recipient/resolver.ts` — `resolveRecipients(ownerUid, targetScope)` Firestore directory + grants read. (FR-007). Acceptance: unit test с InMemoryRecipientResolver fixture.
- [ ] **T074** Создать `workers/push/src/dispatch/fcm-dispatcher.ts` — FCM HTTP v1 API call с SA JWT generation + bounded retry (3× exp backoff 500ms/2s/8s). (FR-008, FR-009). Acceptance: unit test с FakeFcmDispatcher captures messages.
- [ ] **T075** Создать `workers/push/src/dedupe/idempotency.ts` — KV-based dedupe (10-min TTL). (FR-010). Acceptance: unit test asserts cache hit returns cached, miss → executes.
- [ ] **T076** Создать `workers/push/src/ratelimit/rate-limiter.ts` — KV-based per-UID per-event counter с windowSeconds TTL. (FR-006). Acceptance: unit test asserts 429 when exceeded.
- [ ] **T077** Создать `workers/push/src/index.ts` — Worker entrypoint orchestrating auth → registry lookup → authorise → idempotency → rate-limit → resolve → dispatch. Requires T070-T076 + auth-jwt T063. Acceptance: `wrangler dev` startup'ёт без ошибок, returns 400 для empty registry.

### 2.8 Worker test fixtures + integration test

- [ ] **T080** [P] Создать `workers/push/test/fixtures/grant-firestore-snapshot.json` (synthetic Firestore directory + grants). Acceptance: file exists.
- [ ] **T081** [P] Создать `workers/push/test/fixtures/valid-trigger-request.json`. Acceptance: file exists.
- [ ] **T082** Создать `workers/push/test/integration.test.ts` — full Worker flow с fake adapters (FakeFcmDispatcher, FakeFirebaseAuth, InMemoryRecipientResolver). Requires T077, T080, T081. Acceptance: `npm test` в `workers/push/` зелёный.
- [ ] **T083** Создать `workers/push/test/registry.test.ts` — validate EVENT_TYPES entries structure. Requires T071. (contracts/event-type-registry.md §Validation tests). Acceptance: test passes.

**Checkpoint Phase 2**: Foundation infrastructure готова. Worker startup'ёт + JWT validation работает + wire-format roundtrip green. **Empty EventTypeRegistry — no actual event types ещё.**

---

## Phase 3: US-0 + US-1 — First use case `config-updated`

**Goal**: Add ConfigUpdated event type + ConfigSaver integration + multi-device sync working на 2 эмуляторах.

**Independent Test**: 2 эмулятора one signed-in account → save на A → assert B receives push within 5s median.

### 3.1 ConfigUpdated event type registration

- [ ] **T100** [US-1] Add `'config-updated'` entry в `workers/push/src/registry/event-types.ts` per contracts/event-type-registry.md (authorise = owner ∨ grant-holder, rateLimit 60/min, collapseKey formula, priority normal). Requires T071, T072. (FR-040). Acceptance: registry test (T083) updated and green.
- [ ] **T101** [US-1] Verify `EventType.ConfigUpdated` уже existed (T013) — wireValue = "config-updated", handlerTimeout = DEFAULT_TIMEOUT (30s). (FR-041). Acceptance: no-op, validated в T013.

### 3.2 Android adapters (real impls)

- [ ] **T110** [US-1] Создать `core/push/androidMain/HttpPushTrigger.kt` — реальный impl Ktor HTTP POST к Worker URL constant. Constructor accepts `AuthIdentity` (F-4), `UuidGenerator`, `Clock`. Requires T054. (FR-025, FR-026, FR-073, CHK010 dev-experience). Acceptance: compile + unit test против mocked Ktor engine.
- [ ] **T111** [US-1] Создать `core/push/androidMain/FcmTokenPublisherImpl.kt` — Firebase Messaging SDK call to retrieve current token + Firestore merge write `/users/{uid}/devices/{deviceId}/fcmToken`. Requires T023. (FR-027). Acceptance: instrumented test asserts Firestore write.
- [ ] **T112** [US-1] Создать `core/push/androidMain/WorkManagerBackgroundDispatcher.kt` — WorkManager-backed `BackgroundDispatcher` impl (enqueue OneTimeWorkRequest с input data taskName + timeout). Requires T024. (FR-074, FR-078). Acceptance: unit test асserts WorkManager.enqueue called.
- [ ] **T113** [US-1] Создать `WORKER_BASE_URL` constant в `core/push/androidMain/HttpPushTrigger.kt` (`https://launcher-push.<account>.workers.dev/push`). (Q5 resolution, OWD-3). Inline `TODO(server-roadmap SRV-PUSH-FOUNDATION): migrate to custom domain when TODO-ARCH-001 triggered`. Acceptance: constant present, TODO marker present.

### 3.3 ConfigSaver integration

- [ ] **T120** [US-1] Modify `core/keys/.../ConfigSaverImpl.kt` (or wherever `ConfigSaver` impl lives post-F-5b): inject `PushTrigger` через constructor; after successful `RemoteStorage.put(...)` invoke detached coroutine `applicationScope.launch { pushTrigger.trigger(EventType.ConfigUpdated, OwnAndGrants, ownerUid, mapOf("configName" to configName)) }`. (FR-042, FR-031). Acceptance: unit test с FakePushTrigger asserts trigger called с correct args после save.
- [ ] **T121** [US-1] Создать `app/.../push/ConfigUpdatedHandler.kt` — `PushHandler` impl extracting `configName` from payload.fields, invoke `ConfigSaver.loadOwn` if `ownerUid == currentUid` else `loadForOther(ownerUid, configName)`. (FR-043). Acceptance: unit test с FakeConfigSaver asserts loadOwn/loadForOther dispatched correctly.
- [ ] **T122** [US-1] Добавить debounce 2s в `ConfigUpdatedHandler` по `triggerId` — 10 duplicate receipts в 2-sec window → 1 loadOwn invocation. (FR-044, SC-006). Acceptance: test invokes handle 10× same triggerId → assert загрузка once.

### 3.4 DI wiring

- [ ] **T130** [US-1] Создать Koin module `app/.../di/PushModule.kt` registering: `PushTrigger` → realBackend wires `HttpPushTrigger`, mockBackend wires `NullPushTrigger` (CHK-DSS-007); `FcmTokenPublisher` → `FcmTokenPublisherImpl`; `BackgroundDispatcher` → `WorkManagerBackgroundDispatcher` (realBackend) or `FakeBackgroundDispatcher` (mockBackend); `PushHandlerRegistry` → `DefaultPushHandlerRegistry` populated with `ConfigUpdatedHandler` keyed on `EventType.ConfigUpdated`. Requires T053, T110-T112, T121. (Plan §Architecture, CHK012 domain-isolation). Acceptance: app builds with both flavors.
- [ ] **T131** [US-1] Wire `FcmTokenPublisher` invocation в Sign-In observer: after `AuthIdentity` establishes Sign-In + F-5b `EnvelopeBootstrap.bootstrap()` completed → launch detached coroutine `fcmTokenPublisher.publish(currentToken)`. (FR-027, FR-073). Acceptance: instrumented test asserts Firestore `fcmToken` field set after Sign-In.

### 3.5 Receiver-side rewiring

- [ ] **T140** [US-1] Создать `core/push/androidMain/LauncherFirebaseMessagingService.kt` (новый, в push module) — extends `FirebaseMessagingService`, `onMessageReceived` parses `data: Map<String, String>` via `PushPayloadWireFormat.parse` → lookup handler via `PushHandlerRegistry` → dispatch via `BackgroundDispatcher.dispatch(taskName, eventType.handlerTimeout) { handler.handle(payload) }`. Handles malformed payload via silent log + ignore. (FR-028, FR-074, FR-075). Acceptance: instrumented test simulates FCM message → asserts handler invoked.
- [ ] **T141** [US-1] Добавить `<service android:name=".push.LauncherFirebaseMessagingService" android:exported="false">` declaration в `AndroidManifest.xml` (realBackend flavor) + intent filter для `com.google.firebase.MESSAGING_EVENT`. **`android:exported="false"`** mandatory. (FR-071). Acceptance: manifest contains entry, `exported="false"` present.
- [ ] **T142** [US-1] Override `onNewToken(String)` в LauncherFirebaseMessagingService — invoke `fcmTokenPublisher.publish(newToken)` через scope. (FR-027). Acceptance: instrumented test simulates token rotation → Firestore update verified.

### 3.6 E2E test (2 эмулятора)

- [ ] **T150** [US-1] Создать `app/src/androidTest/.../ConfigUpdatedPushE2ETest.kt` — 2 эмулятора, same Sign-In, device A `ConfigSaver.saveOwn("main", testBytes)`, assert device B receives push within 5s median, 30s p95. Verifies SC-001. Requires T100-T142 + emulator setup per skill `android-emulator`. (US-1 acceptance). Acceptance: test passes (locally + CI matrix when available).
- [ ] **T151** [US-1] [P] Создать `core/push/commonTest/EventTypeExtensibilityTest.kt` — dummy `EventType.TestPing` + dummy handler → trigger via FakePushTrigger → assert dispatch works БЕЗ изменений в foundation. Verifies SC-008 + FR-060. Acceptance: test passes; documents «adding new event type = ≤ 15 LOC».

**Checkpoint Phase 3**: US-0 + US-1 working end-to-end. Multi-device sync verified on 2 emulators.

---

## Phase 4: Migration existing push subsystem

**Purpose**: Rewrite 8 existing files using legacy `PushPayload.linkId` to use new foundation. Per Plan §Rollout Phase 2.

**Independent Test**: existing tests (PushPayloadWireFormatTest, PairingEndToEndTest) still pass с new shape; legacy LauncherPushReceiver (spec 007) functionality preserved через PushHandlerRegistry bridge.

- [ ] **T200** Migrate `core/src/commonMain/kotlin/com/launcher/api/push/PushPayload.kt` to use NEW shape (delegate к `core.push.api.PushPayload` from new module, или rewrite в-place — выбор during implementation, predпочтительно delegate). Inline `TODO(removal SRV-PUSH-FOUNDATION future): drop linkId field в schemaVersion 2 bump после spec 008 rewrite ships`. (FR-024, Plan §Migration). Acceptance: compile + existing usages of `payload.linkId` continue работать.
- [ ] **T201** Migrate `core/src/commonMain/kotlin/com/launcher/api/push/PushPayloadWireFormat.kt` to use new wire format. Backward-compat read of legacy `linkId` field preserved. Requires T200. (FR-051). Acceptance: existing `PushPayloadWireFormatTest.kt` passes after update.
- [ ] **T202** Update `core/src/commonTest/kotlin/com/launcher/api/push/PushPayloadWireFormatTest.kt` — add tests для new fields (ownerUid, triggerId, fields map) alongside existing legacy linkId tests. (FR-050). Acceptance: test passes.
- [ ] **T203** Migrate `core/src/commonMain/kotlin/com/launcher/api/push/PushReceiver.kt` → adapt interface to bridge к новому `PushHandlerRegistry`. Если PushReceiver используется elsewhere — keep interface, redirect implementation. (FR-023). Acceptance: existing consumers (LauncherPushReceiver) still compile.
- [ ] **T204** Update `core/src/commonMain/kotlin/com/launcher/fake/push/FakePushReceiver.kt` к новому interface. Requires T203. Acceptance: test compile.
- [ ] **T205** Migrate `core/src/commonMain/kotlin/com/launcher/api/push/FcmReceiverContract.kt` — signature changes для new PushPayload shape. Requires T200. Acceptance: compile.
- [ ] **T206** Rewrite `core/src/androidRealBackend/kotlin/com/launcher/adapters/push/LauncherPushReceiver.kt` → bridge implementation: register existing handlers (handleConfigChanged для legacy, handleCommandIssued, handleRevoke) as `PushHandler` impls в `PushHandlerRegistry` keyed on legacy `PushType` wire values. Preserves spec 007/008 functionality. Requires T200-T203, T140. Acceptance: existing `PairingEndToEndTest` passes.
- [ ] **T207** Update `core/src/commonTest/kotlin/com/launcher/api/pairing/PairingEndToEndTest.kt` к новому PushPayload shape (linkId как optional). Requires T200. Acceptance: test passes.

**Checkpoint Phase 4**: Migration complete. Legacy spec 007/008 functionality preserved через bridge. No regression.

---

## Phase 5: US-2 (cross-UID) + US-3 (graceful degradation) E2E coverage

**Goal**: Verify cross-UID delegation + failure modes work as designed.

**Independent Test**: dedicated tests for each path.

- [ ] **T300** [US-2] Создать `app/src/androidTest/.../CrossUidPushE2ETest.kt` — 2 эмулятора, разные UIDs, F-5b grant подготовлен. Admin делает `saveForOther(grannyUid, "main", ...)` → assert granny device receives push + loadForOther invocation. Verifies SC-003. Requires T150 + F-5b grant setup helpers. Acceptance: test passes.
- [ ] **T301** [US-2] Add test case в CrossUidPushE2ETest: race condition — grant revoked between write и push. Assert Worker resolves recipients **на момент push** (revoked admin excluded). Verifies trouble case 2.b. Acceptance: test passes.
- [ ] **T302** [US-3] Создать `core/push/commonTest/PushTriggerErrorHandlingTest.kt` — каждый PushTriggerError variant covered (mocked Worker returns 401/429/network failure/5xx). Asserts client returns appropriate Outcome.Failure variant без throwing. (FR-076). Acceptance: test passes per variant.
- [ ] **T303** [US-3] Add test case в integration test (T082): Worker returns 503 после 3 FCM retry failures. Assert caller (FakePushTrigger consumer side) sees Outcome.Failure(Backend) and не retries. Verifies SC-005. Acceptance: test passes.
- [ ] **T304** [US-3] Создать `core/push/commonTest/NullPushTriggerSmokeTest.kt` — invoke `NullPushTrigger.trigger(...)` 100 times → assert all return Success без network call (no Ktor invocation). Verifies Scenario 6 / Cloud-mode integration. Acceptance: test passes; mockingly verifies no network.
- [ ] **T305** [US-3] Add test case в LauncherFirebaseMessagingService instrumented test: malformed payload (missing required field, schemaVersion=2, unknown eventType) → assert no crash, Logcat warning, no handler invocation. Verifies FR-075 + Сценарий 7. Acceptance: test passes.

**Checkpoint Phase 5**: All US covered. Edge cases verified.

---

## Phase 6: Polish & Cross-Cutting

**Purpose**: Performance, docs, fitness functions, smoke against deployed Worker, action items из clarify _overview.md.

### 6.1 Fitness functions (per CLAUDE.md rule 7)

- [ ] **T400** [P] Создать Konsist test (or manual lint rule) verifying `core/push/api/` package MUST NOT import any `android.*` types. (SC-009, CHK013 domain-isolation). Acceptance: test fails если introduce Android import in api/.
- [ ] **T401** [P] Создать Gradle dependency check verifying `core/push/` module MUST NOT depend on `core/launcher/*` or `feature/*` modules (only `core/auth/api` permitted). (SC-009). Acceptance: gradle task fails если new project dep added.
- [ ] **T402** [P] Создать CI script verifying `core/push/commonMain/api/WireFormatVersion.kt::MAX_SUPPORTED_SCHEMA_VERSION` const value matches `workers/push/src/contract/wire-format.ts::MAX_SUPPORTED_SCHEMA_VERSION`. (Wire-format CHK003, R10). Acceptance: script fails on mismatch, success on equal.

### 6.2 Performance checkpoint

- [ ] **T410** Создать `specs/019-f5c-fcm-config-updated/perf-checkpoint.md` measuring:
  - SC-001: end-to-end sync latency median + p95 (2 эмулятора smoke).
  - SC-002: `ConfigSaver.saveOwn` overhead delta vs F-5b baseline (Android macrobenchmark).
  - SC-007: Worker CPU per request (wrk against `wrangler dev` + production endpoint).
  - APK delta: release APK size vs main branch (`./gradlew :app:assembleRealBackendRelease`).
  Requires Phase 3-5 complete. (Plan §Rollout Phase 4, dev-experience CHK018). Acceptance: perf-checkpoint.md created с measured values; APK delta noted в context [TODO-ARCH-006](../../docs/dev/project-backlog.md) R8 minification.

### 6.3 Deploy smoke

- [ ] **T420** Deploy Worker к production: `cd workers/push && wrangler deploy`. Verify `WORKER_BASE_URL` constant в HttpPushTrigger matches deployed URL. (quickstart.md §6). Acceptance: Worker reachable at deployed URL; manual curl returns 200 (or expected error for fake token).
- [ ] **T421** Manual smoke on real device (Xiaomi 11T per F-5b existing hardware) — save config + verify push delivery on real Android + real Cloudflare + real FCM. (OEM matrix § Xiaomi). Acceptance: documented в perf-checkpoint.md с timing.

### 6.4 Documentation updates (per CLAUDE.md rule 8 + clarify action items)

- [ ] **T430** [P] Создать `workers/push/README.md` — Cloudflare setup + dev workflow + curl smoke (per quickstart.md §1, §6). (dev-experience CHK016 + CHK022). Acceptance: README ≥ 2 pages.
- [ ] **T431** [P] Update `docs/compliance/permissions-and-resource-budget.md` с F-5c entry: data-only FCM (no POST_NOTIFICATIONS), INTERNET + WAKE_LOCK + Firebase Messaging perms via SDK manifest merger. (security CHK018, permissions-platform CHK022). Acceptance: entry added; existing format preserved.
- [ ] **T432** [P] Add sub-entries в `docs/dev/server-roadmap.md`: `SRV-PUSH-QUOTA` (FCM Spark quota exhaustion → Blaze upgrade); `SRV-PUSH-KV` (Workers KV exhaustion → CF paid tier or own Redis). (modular-delivery CHK015). Acceptance: 2 new sub-sections.
- [ ] **T433** [P] Add `Production monitoring` section в SRV-PUSH-FOUNDATION (server-roadmap.md) — specific metrics: FCM latency p50/p95 per eventType, CF cold start frequency, jose verification failure rate. (dev-experience CHK013, CHK014). Acceptance: section added.
- [ ] **T434** Update [docs/product/roadmap.md](../../docs/product/roadmap.md) Шаг 4a — pomark F-5c effort 8-10 дней (final) + status. Acceptance: roadmap line updated.
- [ ] **T435** [P] Privacy policy hook (S-1 wizard territory placeholder): add inline TODO в S-1 spec (если она ещё не написана) или backlog item: «privacy policy disclosure обновить — F-5c sends event metadata + FCM token к family device push transport». (security CHK019, CHK021). Acceptance: TODO documented в [project-backlog.md](../../docs/dev/project-backlog.md) или inline в S-1.

### 6.5 Final cleanup

- [ ] **T440** Run `Grep linkId core/ app/` final audit — verify все usages migrated или explicitly nullable-safe. (meta-minimization CHK012 final). Acceptance: no broken references.
- [ ] **T441** Verify all `[NEEDS CLARIFICATION]` markers absent в spec.md / plan.md / contracts/. (requirements-quality CHK005). Acceptance: grep returns empty.
- [ ] **T442** Re-run `/speckit.analyze` для final consistency check перед merge. Acceptance: analyze report 0 critical issues.

**Checkpoint Phase 6**: Ship-ready. Все docs updated, fitness functions enforce architectural invariants, perf measured.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: standalone, parallel-safe.
- **Phase 2 (Foundational)**: requires Phase 1. **BLOCKS Phases 3-5.**
- **Phase 3 (US-0 + US-1)**: requires Phase 2.
- **Phase 4 (Migration)**: requires Phase 2 + Phase 3 (T140 — LauncherFirebaseMessagingService нужен до bridge). Может частично parallel с Phase 3 (если разделить файлы).
- **Phase 5 (US-2/US-3 tests)**: requires Phase 3 + Phase 4 (bridge handlers).
- **Phase 6 (Polish)**: requires Phase 3-5 complete.

### Parallel opportunities

- All [P]-marked tasks в Phase 1 — parallel.
- T010-T014 (data types) — parallel (different files).
- T020-T024 (port interfaces) — parallel after data types.
- T030-T034 (fakes) — parallel после respective ports.
- T060-T067 (auth-jwt module) — parallel с T070-T083 (Worker foundation), independent module.
- T400-T402 (fitness functions) — parallel.
- T430-T435 (doc updates) — parallel.

### Critical path

Phase 1 (T001-T005) → T010, T011 → T015, T016 → T077 (Worker entrypoint) → T100 (config-updated registry) → T110-T112 (Android adapters) → T120, T121, T130 (integration) → T140-T142 (receiver) → T150 (E2E test).

Total ≈ 50 atomic tasks. Phase 1-2 parallelizable significantly (≈ 1 dev × 5 дней), Phase 3 sequential bottleneck (≈ 1.5 дня), Phase 4-5 mostly parallel-safe (≈ 1.5 дня), Phase 6 mostly parallel (≈ 1 день).

---

## Trace summary

| FR | Covered by task(s) |
|---|---|
| FR-001 (Worker endpoint) | T077 |
| FR-002 (Firebase Auth validation) | T060-T065, T077 |
| FR-003 (JWKS dynamic TTL) | T061 |
| FR-004 (EventTypeRegistry lookup) | T071, T077 |
| FR-005 (per-event authorisation) | T072, T077 |
| FR-006 (rate limit) | T076 |
| FR-007 (recipient resolution) | T073 |
| FR-008 (FCM dispatch + collapse_key) | T074 |
| FR-009 (FCM retry 3×) | T074 |
| FR-010 (idempotency dedupe) | T075 |
| FR-011 (no content in push payload) | T074 (verified via integration test T082) |
| FR-012 (drop stale FCM tokens) | T074 |
| FR-013 (schemaVersion ≤ MAX) | T070, T077 |
| FR-020 (PushTrigger port) | T020 |
| FR-021 (EventType sealed) | T013 |
| FR-022 (TargetScope enum) | T012 |
| FR-023 (PushHandler + Registry) | T021, T022, T051 |
| FR-024 (PushPayload shape) | T011, T015 |
| FR-025 (HttpPushTrigger Idempotency-Key) | T110 |
| FR-026 (no client retry) | T110, T026 supplement в FR text |
| FR-027 (FcmTokenPublisher) | T023, T111, T142 |
| FR-028 (LauncherFirebaseMessagingService) | T140 |
| FR-031 (fire-and-forget) | T120 |
| FR-038 (graceful degradation) | T303 |
| FR-040 (config-updated registry entry) | T100 |
| FR-041 (EventType.ConfigUpdated) | T013 (confirmed T101) |
| FR-042 (ConfigSaver invokes pushTrigger) | T120 |
| FR-043 (ConfigUpdatedHandler) | T121 |
| FR-044 (receiver debounce by triggerId) | T122 |
| FR-045 (no user-visible notification) | T140 (data-only payload) |
| FR-050 (roundtrip test) | T042 |
| FR-051 (additive/breaking policy) | T043, T044 |
| FR-052 (sealed extension) | T151 |
| FR-060 (3-place extension pattern) | T151 |
| FR-070 (logging hygiene) | T140 (impl required), spec FR enforced via T206 (legacy bridge) |
| FR-071 (exported="false") | T141 |
| FR-072 (no POST_NOTIFICATIONS) | T141 (manifest verified) |
| FR-073 (IO dispatcher) | T110, T111, T120 |
| FR-074 (BackgroundDispatcher dispatch) | T112, T140 |
| FR-075 (malformed payload graceful) | T140, T305 |
| FR-076 (PushTriggerError variants) | T014, T302 |
| FR-077 (EventType.handlerTimeout) | T013 |
| FR-078 (BackgroundDispatcher port) | T024, T112 |

| US | Covered by task(s) |
|---|---|
| US-0 (foundation reusability) | T151 |
| US-1 (multi-device sync) | T150 |
| US-2 (cross-UID delegation) | T300, T301 |
| US-3 (graceful degradation) | T302-T305 |

| SC | Covered by task(s) |
|---|---|
| SC-001 (5s/30s sync latency) | T150, T410 |
| SC-002 (200ms p95 overhead) | T410 |
| SC-003 (cross-UID 10s) | T300, T410 |
| SC-004 (FCM token rotation 5s) | T142, instrumented test |
| SC-005 (graceful degradation pull fallback) | T303 |
| SC-006 (idempotency 10×=1) | T122 |
| SC-007 (100 concurrent <10ms CPU) | T410 |
| SC-008 (extensibility ≤15 LOC) | T151 |
| SC-009 (extraction-readiness fitness) | T400, T401 |

| Contract | Covered by task(s) |
|---|---|
| push-trigger-request-v1.md | T015, T070, T041, T082 |
| push-payload-v1.md | T011, T040, T042, T140 |
| event-type-registry.md | T071, T083, T100 |

| Required-task gates | Covered? |
|---|---|
| Every contract → roundtrip + backward-compat test | ✅ T042, T043, T044 + T082 (Worker side) |
| Every new port → fake adapter | ✅ T030-T034 |
| Every new module → Konsist/lint rule | ✅ T400, T401, T402 |
| `linkId` grep audit | ✅ T440 |
| Doc updates | ✅ T430-T435 |
| Perf checkpoint | ✅ T410 |
| Privacy/compliance docs | ✅ T431, T435 |

---

## Implementation strategy

### MVP first (Phase 1-3 minimal)

1. Complete Phase 1 (setup) + Phase 2 (foundational) → **infrastructure ready**.
2. Complete Phase 3 (US-0 + US-1) → **multi-device sync working на 2 эмуляторах**.
3. **STOP and VALIDATE**: demo to owner. Если happy — proceed.

### Then incremental

4. Phase 4 (migration existing push subsystem) → no regressions.
5. Phase 5 (US-2 + US-3 E2E coverage) → all scenarios verified.
6. Phase 6 (polish) → ship-ready.

### Parallel team strategy

С 2+ devs:
- **Dev A**: Phase 1 + Phase 2.1-2.5 (KMP client foundation + impls).
- **Dev B**: Phase 1 + Phase 2.6 (auth-jwt module) + Phase 2.7 (Worker foundation).
- После Phase 2 sync — Dev A на Phase 3 (integration), Dev B на Phase 4 (migration).
- Phase 5 + 6 — параллельно после Phase 3 + 4.

---

## Open issues / follow-ups

(none critical — все clarify action items распределены по tasks).

**Long-term**:
- TODO-ARCH-006 R8 minification — required before production release (existing блокер pre-F-5c, F-5c делает его even more critical).
- TODO-ARCH-017 push extraction trigger — V-2 spec start (Phase 4 roadmap).
- TODO-ARCH-018 auth-jwt extraction trigger — second Worker consumer.

---

## Краткое резюме (для не-разработчика)

Это **пошаговый план реализации** F-5c. Всего ~50 задач, разбитых на 6 фаз:

1. **Setup** (5 задач) — создать пустые модули + Cloudflare KV + конфиги.
2. **Foundational** (~30 задач) — реализовать «строительные блоки»: типы данных, интерфейсы (порты), их заглушки для тестов, тесты wire-format'а. **Блокирует всё остальное.**
3. **Первый use case** (~15 задач) — добавить `config-updated` event type, интегрировать с ConfigSaver, написать E2E тест на 2 эмуляторах.
4. **Миграция старого кода** (~8 задач) — переписать 8 файлов которые использовали старый `PushPayload.linkId`.
5. **Тесты остальных сценариев** (~6 задач) — cross-UID delegation, graceful degradation, malformed payload, local mode.
6. **Polish** (~12 задач) — performance checkpoint, deploy, документация, автоматические проверки архитектурных инвариантов.

**MVP-путь**: Phase 1 → 2 → 3 = 1 неделя работы → demo. Если ОК → Phase 4 → 5 → 6 ещё 1 неделя → ship.

**Параллельная работа**: если 2 dev — один делает KMP client, второй — Worker + auth-jwt module. После Phase 2 — один делает integration, второй — миграцию старого кода.

**Trace**: каждая задача привязана к конкретному FR (functional requirement), US (user story), или Plan-секции. Все 45 FR и 4 US покрыты задачами — проверено в таблицах выше.

**Следующий шаг**: `/speckit.analyze` (финальный consistency check перед началом работы).
