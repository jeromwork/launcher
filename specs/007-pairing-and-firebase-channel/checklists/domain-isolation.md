# Checklist: domain-isolation — spec 007

**Generated**: 2026-05-11 by `/speckit.plan` Step 5.
**Per**: CLAUDE.md rules §1 (domain isolation) + §2 (ACL) + ADR-001 (Platform Parity).

## Result: 16/16 PASS (2 informational notes)

### Vendor SDKs

| # | Check | Result | Justification |
|---|---|---|---|
| CHK001 | No vendor SDK types in domain signatures | **PASS** | All 5 ports use only domain types (`DocPath`, `DocSnapshot`, `JsonElement`, `Result<T, BackendError>`). `BackendError` sealed — no `FirebaseFirestoreException` crosses boundary (FR-013). |
| CHK002 | One adapter per SDK | **PASS** | Firestore→`FirebaseRemoteSyncBackend`, Auth→`FirebaseIdentityProvider`, FCM Receive→`LauncherFirebaseMessagingService`, Worker HTTPS→`WorkerPushSender`, ML Kit Barcode→`QrScannerScreen`. **Note**: ZXing для QR-encode в androidMain без явного port — acceptable для Android-only spec; при iOS-расширении завернуть в `QrEncoder` port. |
| CHK003 | "Vendor disappears" ≤ 1 adapter module | **PASS** | Firestore=1 file, Auth=2 files (Provider + IdentityCache), FCM=3 files (Service + Registration + Sender), Cloudflare=push-worker/ subproject ~50 LOC. Все confined to adapter dirs. См. OWD-1, OWD-6 + research.md §FCM-via-Cloudflare-Worker. |

### Transport types

| # | Check | Result | Justification |
|---|---|---|---|
| CHK004 | No transport types in domain | **PASS** | Нет `okhttp3.*`, `Firestore.Query`, `RemoteMessage` в `:core/api/`. JSON через `kotlinx.serialization.json.JsonElement` (KMP-стандарт). |
| CHK005 | Wire format types domain-owned | **PASS with note** | `PushPayload`, `LinkBootstrap`, `Link`, `PairingToken` — все в `:core/api/`. **Note**: `@Serializable` annotation на domain типах = serialization-framework, но это project-wide KMP-стандарт начиная со спека 005; custom serializers живут в adapter. |

### Platform types

| # | Check | Result | Justification |
|---|---|---|---|
| CHK006 | No `android.*`/`Intent`/`Uri`/`Context` in commonMain | **PASS** | Все ports/entities — pure Kotlin (`kotlinx.datetime.Instant`, `JsonElement`, `Flow`, `Result`). |
| CHK007 | Domain-typed projections | **PASS** | QR deep-link → `parsePairingDeepLink(uri: String): QrParseResult` → domain `PairingToken`; Worker URL = `String` в build config. |

### Ports

| # | Check | Result | Justification |
|---|---|---|---|
| CHK008 | Every external surface через port | **PASS** | 5 ports + `DeviceIdProvider` + `QrScanner` (research.md). ZXing encode — direct (acceptable Android-only). |
| CHK009 | Port shape domain-driven | **PASS** | `RemoteSyncBackend` operations — domain-meaningful, не SDK-specific. `PushSender.notify(linkId, type, extra)` — domain language. |
| CHK010 | Fake adapter per port | **PASS** | 5 Fakes в `:core/commonMain/fake/` — `FakeRemoteSyncBackend` (offline queue per C5), `FakeIdentityProvider`, `FakePushSender` (counter), `FakePushReceiver`, `FakeLinkRegistry`. |
| CHK011 | Real adapter per port | **PASS** | `Firebase*` adapters в `:core/androidMain/adapters/`; iOS source-set пустой — отдельный спек. |
| CHK012 | DI wiring per build | **PASS** | FR-034 (flavors), FR-035 (Koin source-set discovery), `BackendModule.kt`. |

### Source-set placement

| # | Check | Result | Justification |
|---|---|---|---|
| CHK013 | Each file source-set assigned | **PASS** | plan.md §Module map содержит полный список с разделением commonMain/androidMain. |
| CHK014 | Default commonMain, deviations justified | **PASS** | androidMain — Firebase-зависимые; `push-worker/` — TypeScript subproject (Cloudflare Workers runtime, не Kotlin). |

### Existing-code regressions

| # | Check | Result | Justification |
|---|---|---|---|
| CHK015 | No vendor types reintroduced | **PASS** | Спек 006 очистил commonMain; спек 007 строго соблюдает. |
| CHK016 | No new expect/actual where pure Kotlin sufficies | **PASS** | Спек 007 не добавляет новых expect/actual в commonMain. |

## Informational notes

1. **CHK002**: ZXing для QR-encode прямо в androidMain без port-обёртки. Acceptable для Android-only спека. При iOS-расширении завернуть в `QrEncoder` port в `:core/api/`. **TODO в `androidMain/.../qr/QrBitmapGenerator.kt`**: «при iOS-расширении extract в `:core/api/qr/QrEncoder.kt` port + iOS impl через `expect/actual`».

2. **CHK005**: `@Serializable` (kotlinx.serialization) на domain типах = consistent с project-wide convention со спека 005. Это KMP-стандартная serialization, не «transport SDK leak». Если когда-либо потребуется отказ от kotlinx.serialization (unlikely — это `org.jetbrains.kotlinx`, не vendor) — будет breaking change на уровне всего проекта.

## Re-run trigger

Этот checklist пере-запускается в `/speckit.analyze` (Step 5) с финальным состоянием артефактов (включая написанный код в /speckit.implement).
