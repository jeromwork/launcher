# Phase 3 status — receiver-side migration deferred

Commit: pending (after Phase 3 trigger-side work).

## What Phase 3 commit delivers (trigger-side end-to-end)

- **`:core:keys`** — new port `ConfigChangeNotifier` (SAM, default no-op).
  `EnvelopeAsyncPushWorker.doWork()` looks it up via Koin GlobalContext after
  successful `RemoteStorage.put` and fires. Notifier failure cannot affect
  storage outcome — wrapped in `runCatching` per FR-031.
- **`:core:push:androidMain`** — `HttpPushTrigger.create()` factory (CIO engine,
  JSON content negotiation, timeouts), `WorkerBaseUrl.URL` constant (placeholder
  для T420 deploy), `WorkManagerBackgroundDispatcher` (inline timeout + retry
  per documented limitation — see class kdoc).
- **`app/main`** — `ConfigUpdatedHandler` (T121/T122) with 2-sec triggerId
  debounce. `f019PushCommonModule` Koin DSL registering handler + registry.
- **`app/realBackend`** — `FcmTokenPublisherImpl` (Firestore merge write),
  `FirebaseIdTokenProviderAdapter` (wraps existing `FirebaseTokenSupplier`),
  `PushTriggerConfigChangeNotifier` (bridges F-5b save → F-5c trigger),
  `f019PushBackendModule` wiring all of the above.
- **`app/mockBackend`** — no-op wiring (NullPushTrigger, NoOpConfigChangeNotifier).
- **Tests** — 7/7 `ConfigUpdatedHandlerTest`, T151 `EventTypeExtensibilityTest`,
  `NullPushTriggerTest`, `DefaultPushTriggerTest` (5 cases, MockEngine).

## What Phase 3 does NOT deliver (deferred to Phase 4)

### T140 / T141 — new LauncherFirebaseMessagingService + manifest entry

**Why deferred**: An existing `LauncherFirebaseMessagingService` already lives в
`core/src/androidRealBackend/kotlin/com/launcher/adapters/push/` and is declared
в `app/src/realBackend/AndroidManifest.xml`. Android dispatches FCM events to
**one** service (by `MESSAGING_EVENT` action). Adding a second service in
`:core:push:androidMain` would not be invoked. Two correct paths:

1. **Modify existing service** to look up new `PushHandlerRegistry` in addition
   к the legacy `PushReceiver` interface. Fork on payload shape:
   `eventType` field present → new path; `type` field present → legacy path.
2. **Replace existing service** with new one, bridge legacy types (`PushType.ConfigChanged`,
   `PushType.CommandIssued`, `PushType.Revoke`) as `PushHandler` impls registered
   in DI (this is exactly Phase 4 T206).

Both options touch existing spec 007/008 functionality and require receiver-side
runtime verification — best done together as Phase 4 in a focused PR. **For now,
Phase 3 trigger-side works end-to-end**: save → push trigger → Worker → FCM → recipient
device (which uses the existing service that drops the new-shape payload and falls
through to F-5b pull-on-app-open per FR-038).

### T142 — `onNewToken` override

**Why deferred**: belongs to the new service work in Phase 4. Existing
`LauncherFirebaseMessagingService.onNewToken` logs but does not publish — Phase 4
adds `fcmTokenPublisher.publish(newToken)` to the unified service.

### T150 — 2-emulator E2E test

**Why deferred**: user-side constraint (no two-device hardware available right
now). Single-device manual verification still possible (T421 path: save on
device A, observe push trigger via Worker logs, observe Worker-side rate-limit /
recipient-resolution behaviour — no recipient device confirmation but trigger
half of the flow exercised).

### Sign-in observer wiring for FcmTokenPublisher (T131)

**Why deferred**: `FcmTokenPublisher` is wired in DI but not called from any
sign-in completion hook yet. The hook lives in `LauncherApplication`'s
`envelopeBootstrap` observer (post-F-5b). Adding `fcmTokenPublisher.publish(...)`
inline там reasonable, but requires identifying the right FCM-token source — the
`onNewToken` in messaging service is the canonical source; relying on it
ties Phase 3 to Phase 4. Pragmatic: ship Phase 3 trigger-side now, do publisher
wiring inside Phase 4 alongside the service rewrite.

## Build + test status

| Module / suite | Result |
|---|---|
| `:core:push:jvmTest` (commonTest) | ✅ all tests green |
| `:core:push:compileKotlinJvm` | ✅ |
| `:app:assembleRealBackendDebug` (APK build) | ✅ |
| `:app:assembleMockBackendDebug` (APK build) | ✅ |
| `:app:testMockBackendDebugUnitTest` для `ConfigUpdatedHandlerTest` | ✅ 7/7 |
| `:core:keys:jvmTest` `ImportRestrictionsFitnessTest.appCodeOutsideAdaptersMustNotImportKeysApiInternal` | ⚠ **pre-existing F-5b debt** (PublicKeyDirectoryRecipientResolver.kt imports `family.keys.api.internal.*` in `app/main`). Fails on `main` too — not caused by F-5c. |

## Manual setup still needed

- `WorkerBaseUrl.URL` placeholder `<account>` — replace with Cloudflare subdomain
  after `wrangler deploy` (T420).
- T004 KV namespaces + T005 secrets — see `workers/push/README.md`.

## Phase 4 prerequisites

When user has bandwidth для Phase 4 receiver-side migration:
1. Decide path: modify-existing vs replace-with-bridge.
2. Rewrite `LauncherFirebaseMessagingService` to dispatch via `PushHandlerRegistry`.
3. Register legacy bridge handlers (`LegacyConfigChangedHandler`, etc.) in DI.
4. Implement `onNewToken` → `FcmTokenPublisher.publish`.
5. Single-device smoke test (save → push received → loadOwn invoked).
6. Update `AndroidManifest.xml` (realBackend) if class name changes.
