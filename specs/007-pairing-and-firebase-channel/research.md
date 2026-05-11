# Research: Pairing and Firebase Channel (spec 007)

**Generated**: 2026-05-11 by `speckit-plan` orchestrator.
**Scope**: альтернативные подходы к одно-сторонним дверям (OWD-1..6), технологические выборы, обоснование C1 ревизий.

---

## §History: три ревизии C1

Спек 007 прошёл три фундаментальные ревизии решения о механизме FCM-доставки. Этот раздел — история «почему пришли к Cloudflare Worker».

### Ревизия (a) — 2026-05-11: **Variant A — Firestore-trigger Cloud Function**

Initial decision в `/speckit.clarify` pass 1. Worker отсутствовал; FCM-push отправляется Firestore-trigger Cloud Function'ом на `onWrite` к `/links/{linkId}/config` и `/links/{linkId}/commands/{cmdId}`.

**Почему отвергли:** Cloud Functions требует **Blaze plan**, что = привязка карты. Project owner подтвердил отсутствие карты. **Commit `c6a57ac`**.

### Ревизия (b) — 2026-05-11: **Variant C — client-side FCM topic-publish**

Admin-app сам публикует FCM-push в topic `link-{linkId}` через FCM HTTP v1 API из приложения. Старый Spark-compatible путь.

**Почему отвергли (выяснилось при `/speckit.plan`):**
1. FCM HTTP v1 send API требует OAuth scope `https://www.googleapis.com/auth/firebase.messaging`.
2. Этот scope выдаётся **только сервисным аккаунтам**, не пользовательским токенам.
3. Legacy FCM API (Server-Key) — deprecated, отключён.
4. Поэтому **никакая клиентская публикация FCM невозможна** — это архитектурное ограничение Firebase.

**Commit `1f0097c`**. Урок: при выборе варианта в `/speckit.clarify` надо проверять технические допущения на этапе planning, а не верить «общему ощущению что должно работать».

### Ревизия (c) — 2026-05-11 c: **Cloudflare Worker + FCM HTTP v1** (current)

Cloudflare Worker — отдельный сервис (~50 LOC TS), hosted на бесплатном tier `*.workers.dev`. Worker хранит Firebase service-account JSON в Cloudflare Secrets, принимает HTTPS POST от admin/OLD клиентов, валидирует Firebase ID-token, авторизует через Firestore (`uid == links/{linkId}.adminId`), отправляет FCM data-message.

**Почему принято:**
- ✅ Spark-plan совместимо (Firebase не требует Blaze).
- ✅ Технически работает (в отличие от Variant C).
- ✅ Cloudflare free tier 100k req/day — на годы хватит.
- ✅ Service-account secret защищён (Cloudflare Secrets, не в APK).

**Минусы:**
- +1 сервис в стеке (Worker).
- +Admin SDK подобный код в Worker'е для JWT.

**Exit ramp:** контракт `POST /notify` фиксированный; миграция на Cloud Functions / Vercel / Fly.io / Lambda = переписать deploy, не клиента. См. OWD-6.

---

## §JWT verification library в Worker'е

### Контекст

Worker должен валидировать Firebase ID-token (RS256 JWT) — стандартная задача.

### Альтернативы

| Library | Bundle size | KMP-friendly? | Поддержка JWKS | Activity |
|---|---|---|---|---|
| `firebase-admin` (Node) | ~3 MB | ❌ Node-only | yes | актив, но огромный для Worker'а |
| `jose@5+` | ~50 KB minified | ✅ pure JS | yes (`createRemoteJWKSet`) | актив, indиndustry-standard |
| `jsonwebtoken` | ~120 KB | ❌ только signing/verify, без JWKS | manual fetch | актив, но без JWKS API |
| Self-rolled (manual RS256) | ~5 KB | ✅ | manual | риск багов |

### Решение

**`jose@5+`**. Размер 50 KB — комфортно укладывается в 1 MB Cloudflare bundle limit. `createRemoteJWKSet` сам кеширует Firebase public keys на 6ч (в module scope). Industry-standard.

```typescript
// push-worker/src/auth.ts (sketch)
import { jwtVerify, createRemoteJWKSet } from 'jose';
const JWKS = createRemoteJWKSet(new URL(
  'https://www.googleapis.com/robot/v1/metadata/jwks/securetoken@system.gserviceaccount.com'
));
const { payload } = await jwtVerify(idToken, JWKS, {
  issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
  audience: env.FIREBASE_PROJECT_ID,
});
// payload.sub = uid
```

**TODO в Worker коде**: «при росте трафика — рассмотреть локальный pre-fetch JWKS при startup вместо on-demand».

---

## §QR-Encoding / Decoding на Android

### Encoding (на стороне OLD — показ QR)

ZXing — pure Java, KMP-compatible (через JVM). Опубликован как `.jar`. Использование в Android — без проблем; `BitMatrix` → `Bitmap` в ~20 строк.

### Decoding (на стороне admin — сканирование QR)

| Подход | APK Δ | KMP-friendly | Точность на низкосортных камерах | Зависимости |
|---|---|---|---|---|
| ZXing only (с CameraX preview) | ~0.4 MB | ✅ pure Java | Средняя; ручная работа с blur | ZXing + CameraX |
| CameraX + ML Kit Barcode Scanning | ~4.0 MB | ❌ Android-only (`com.google.mlkit:barcode-scanning`) | Высокая; авто-фокус, blur recovery | CameraX + ML Kit |

### Решение

**CameraX + ML Kit Barcode** для admin-сканера. Admin-устройство — современный смартфон; APK delta не критичен. ML Kit «работает из коробки», CameraX лучше управляется с lifecycle.

ZXing **остаётся** для **encode-стороны (генерации QR)** — pure Kotlin/Java, KMP-compatible, легче и без необходимости ML Kit на OLD-устройстве.

**Exit ramp:** scanner за port `QrScanner: Flow<ScannedToken>`. Подмена реализации — один файл.

**TODO в admin scanner коде**: «MLKit зависимость может быть удалена при downsize APK — fallback на ZXing-only сканер; см. spec 007 §QR-Decoding».

---

## §FCM HTTP v1 send из Worker'а

### Контекст

Worker должен отправить FCM data-push после авторизации запроса. Нужны: (а) OAuth access-token, (б) HTTPS POST в FCM API.

### OAuth access-token из service-account

Сервисный аккаунт даёт `RS256 JWT → access-token` flow:
1. Compose JWT с claims: `iss: <sa.client_email>`, `scope: "https://www.googleapis.com/auth/firebase.messaging"`, `aud: "https://oauth2.googleapis.com/token"`, `iat`, `exp`.
2. Sign with `sa.private_key` (RS256).
3. POST на `https://oauth2.googleapis.com/token` с `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=<signed-jwt>`.
4. Получаем `access_token` (3600s TTL).

Cache access-token в module scope на 50 минут (немного меньше TTL).

```typescript
// push-worker/src/fcm.ts (sketch)
import { SignJWT, importPKCS8 } from 'jose';
async function getAccessToken(env: Env) {
  if (cached && cached.expiresAt > Date.now() + 60_000) return cached.token;
  const sa = JSON.parse(env.FIREBASE_SA_JSON);
  const privateKey = await importPKCS8(sa.private_key, 'RS256');
  const jwt = await new SignJWT({ scope: 'https://www.googleapis.com/auth/firebase.messaging' })
    .setProtectedHeader({ alg: 'RS256' })
    .setIssuer(sa.client_email)
    .setAudience('https://oauth2.googleapis.com/token')
    .setIssuedAt()
    .setExpirationTime('1h')
    .sign(privateKey);
  const resp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  });
  const data = await resp.json();
  cached = { token: data.access_token, expiresAt: Date.now() + data.expires_in * 1000 };
  return cached.token;
}
```

### FCM POST

```typescript
async function sendFcm(env: Env, linkId: string, type: string, payload?: any) {
  const accessToken = await getAccessToken(env);
  const body = {
    message: {
      topic: `link-${linkId}`,
      data: { schemaVersion: '1', type, linkId, ...(payload ?? {}) },
      android: { priority: 'HIGH' },  // wake from Doze
    },
  };
  const resp = await fetch(
    `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    }
  );
  if (!resp.ok) throw new Error(`FCM send failed: ${resp.status} ${await resp.text()}`);
}
```

**TODO в Worker коде**: «retry с exponential backoff на 5xx FCM ошибках — реализовать в Phase 5».

---

## §Anonymous Firebase Auth — identity persistence

### Проблема

Anonymous Firebase Auth UID — **транзитный**: при reinstall app — новый UID. Но FR-001 требует `oldDeviceId` сохранялся между перезапусками.

### Решение

- `oldDeviceId` = UUIDv4, generated on first launch, stored in DataStore (FR-001).
- `IdentityProvider.signInAnonymous()` returns Firebase Auth UID.
- В `/devices/{oldDeviceId}` Firestore-doc записываем **наш** `oldDeviceId` как ключ + поле `firebaseAuthUid`. Pairing идентифицирует устройство по нашему `oldDeviceId`.
- В `/links/{linkId}.oldDeviceFirebaseUid` пишем актуальный Firebase UID. Security Rules позволяют OLD писать в state по `request.auth.uid == oldDeviceFirebaseUid`.
- При reinstall: новый `oldDeviceId` → пользователь должен **новый pairing**. Документировано как known limitation (OWD-2 exit ramp описывает миграцию к named auth).

**TODO в `IdentityCache.kt`**: «при добавлении named auth (OWD-2 exit ramp) — `linkWithCredential` сохраняет UID; oldDeviceId остаётся unchanged как stable id».

---

## §OWD Analyses

Все 6 OWD из спека имеют exit ramps. Здесь — дополнительный анализ для нескольких.

### OWD-1: Firebase as backend

Подтверждено решение спека. Alternatives (Supabase, self-hosted, P2P) — в `docs/product/session-2026-05-05-research.md`. Firebase остаётся MVP-выбором. Exit ramp работает: всё за `RemoteSyncBackend` port; замена adapter'ом ~1 неделя.

### OWD-2: Anonymous Auth → named Auth

Anonymous достаточен для MVP. Уточнение в §FirebaseAuth-Identity-Persistence.

### OWD-3: 6-char alphanumeric token

Подтверждено. Collision at 32^6 = 1.07B, 5-min TTL, vanishingly low.

### OWD-4: linkId = Firestore document ID

Подтверждено. Opaque string в домене; никаких semantics.

### OWD-5: mockBackend default for debug

Подтверждено. Integration tests против Firebase Emulator — отдельный CI job.

### OWD-6: Cloudflare Workers as push-relay platform *(новый)*

**Альтернативы (с cost/benefit):**

| Platform | Free tier | Migration effort | Why not now |
|---|---|---|---|
| **Cloudflare Workers** | 100k req/day | — | **CHOSEN** |
| Vercel Functions | 100GB-Hr/mo (Hobby) | ~2ч переписать deploy | Эквивалент, но Vercel больше про frontend |
| Fly.io Apps | $5 credit/mo | ~4ч (это полноценный VM) | Сложнее настраивать; больше surface area |
| AWS Lambda | 1M req/mo | ~3ч | AWS-account overhead; cold start выше |
| Firebase Cloud Functions | (Blaze only) | ~1ч (Firebase deploy) | **Требует Blaze plan / карту** — основной reason почему не выбрали |
| Self-hosted (DigitalOcean/Hetzner) | от $5/mo | ~6ч (VM + nginx + TLS) | Никакого free tier; больше maintenance |

**Exit ramp procedure:** замена Worker'а на любой другой backend = (а) повторить FCM send логику, (б) валидировать те же Firebase ID-token, (в) сохранить контракт `POST /notify`. Кодовая база Worker'а — 50 LOC, портирование — день работы.

**TODO в `push-worker/README.md`**: подробная секция «Migration to <platform>» для каждой альтернативы.

---

## §Cloudflare free tier capacity planning

### Лимиты

| Resource | Free tier | Наш MVP |
|---|---|---|
| Workers requests | 100,000 / day | ~50/day (5 пользователей × 10 пушей) |
| Workers CPU time | 10 ms / request | ~3 ms / request (JWT verify + Firestore read + FCM send) |
| Workers bundle size | 1 MB | ~80 KB |
| Workers Secrets | unlimited | 1 (FIREBASE_SA_JSON) |
| Cloudflare KV reads | 100,000 / day (если включим) | not used |
| Cloudflare KV writes | 1,000 / day (если включим) | not used |

**Capacity headroom**: ~2000× для requests, ~3× для CPU per request, ~12× для bundle.

**Migration trigger**: если daily requests > 50k → апгрейд на Workers Paid ($5/mo, 10M requests). Это **two-way door** (1 клик в dashboard).

**TODO в `push-worker/README.md`**: monitoring queries для отслеживания approach to limits.

---

## §google-services.json в публичном репо

### Аргументы «коммитить (для dev project)»

- Firebase API key — **публичный identifier**, не секрет. Реальная защита — Security Rules + App Check.
- Без него новый разработчик не соберёт `realBackend` flavor.
- Стандартная практика для open-source Firebase samples.

### Аргументы «не коммитить»

- Сканирование публичных репо ботами; теоретическая атака на rate-limits.
- Возможная путаница dev/prod credentials.

### Решение

- **Dev** `google-services.json` (project `launcher-old-dev`) — **commited** (already in `05bb7ae`).
- **Production** — CI secret (отдельное решение когда production-project появится).

**TODO в `app/google-services.json`** (не реально comment в JSON, но в repo's `CONTRIBUTING.md`): «production credentials never committed; this is dev-only».

---

## §Recursive subtree delete при revoke

### Проблема

Firestore не имеет client-side API для recursive collection delete. Cloud Function `firestore.recursiveDelete` — только Blaze.

### Решение для Spark

Клиент явно итерирует по known subcollection paths:
```kotlin
listOf("state", "config", "capabilities", "health", "commands").forEach { sub ->
  backend.deleteSubcollection(DocPath.Links(linkId).child(sub))
}
backend.deleteDoc(DocPath.Links(linkId))
```

Each `deleteSubcollection` — batched delete (100 docs / batch). Security Rules разрешают delete только owner-OLD.

### Trade-off

Если в будущем появятся новые subcollections не учтённые в коде — останутся как orphans.

**Mitigation**: добавить known-children list как const в `Link.kt` с migration policy.

**TODO в `LinkRegistry.revoke()`**: «при добавлении новой subcollection (например `/private-media` в спеке 011) — обновить known list здесь».

---

## §UI senior-safe (наследуется из спека 006)

- QR-screen, consent-screen, paired-status-screen — все strings **externalized** в `commonMain/resources/strings/` (ADR-004).
- Senior-safe overrides: font ≥ 18sp, tap target ≥ 56dp (constitution Article VIII §7).
- AlertDialog «Отвязать?» — два чётких кнопки с одинаковым весом, без negative-prominence.
- Consent screen — **фиксированный** список категорий доступа (не free text, не editable).

---

## §QR-pairing как reusable trust primitive

Добавлено 2026-05-11 c (post-checklist), после замечания project owner'а: pairing-flow универсален и будет использоваться много где, не только admin↔OLD.

### Будущие use case'ы trust-pairing

| Use case | Spec | Result-тип pairing'а |
|---|---|---|
| admin↔OLD | 007 (текущий) | `LinkBootstrap` |
| Добавление trusted contact бабушки | 011 (`contacts-and-e2e-encrypted-media`) | `TrustedContactBootstrap` |
| Trust для входящих звонков | future `jitsi-calls-and-rooms` | `CallTrustEdgeBootstrap` |
| Multi-admin (вторая внучка) | future `multi-admin-flows` | `LinkBootstrap` (повторно) или `SubAdminLinkBootstrap` |
| Device replacement | backlog `config-portability` | `DeviceReplacementBootstrap` |
| Trust для специалистов (врач, соц.работник) | future | `ServiceProviderTrustBootstrap` |

### Architectural decision

`PairingService.claim()` должен возвращать **sealed result type** `TrustEdgeBootstrap`, а не конкретный `Link`. Это позволяет:

```kotlin
sealed interface TrustEdgeBootstrap {
  val edgeId: String
  val createdAt: Instant
}
data class LinkBootstrap(
  override val edgeId: String,   // = linkId
  val adminId: AdminIdentity,
  val oldDeviceId: String,
  val oldDeviceFirebaseUid: String,
  override val createdAt: Instant
) : TrustEdgeBootstrap

// Future:
// data class TrustedContactBootstrap(...) : TrustEdgeBootstrap     // spec 011
// data class CallTrustEdgeBootstrap(...) : TrustEdgeBootstrap      // future
```

И в Firestore — разные subcollections: `/links/{linkId}`, `/trustedContacts/{contactId}`, etc., созданные при claim в зависимости от **типа pairing'а**. Тип задаётся в `/pairings/{token}.pairingType` field — добавляется в wire-format **уже сейчас** (forward-compat).

### Изменения в спеке 007 для подготовки

1. **wire-format `/pairings/{token}`**: добавить optional поле `pairingType: String?` (default `"admin-old-link"` если отсутствует — backward-compat). Реализуется в `contracts/pairing-token.md` как additive change (schemaVersion остаётся 1).

2. **`PairingService.claim()` signature**: возвращает `TrustEdgeBootstrap` (sealed), не `Link`. В 007 — единственный subtype `LinkBootstrap`.

3. **`Link.kt`** в `:core/api/link/` остаётся как specialization, но имплементирует `TrustEdgeBootstrap` interface.

### Why this matters now

Если в 007 зашить `Link` глубоко в `PairingService` без sealed-абстракции — через 2-3 спека (контакты, звонки) этот код придётся рефакторить. CLAUDE.md §4 (MVA): «добавлять абстракцию сегодня, если иначе потребуется rewrite завтра». Здесь **есть** будущий consumer (видим уже сейчас в backlog и `launcher_jitsi_feature_summary.md`), который мы знаем — планировать его сейчас = корректное MVA, не speculation.

### Migration path для будущих use case'ов

При добавлении нового typeoftrust в спеке 011/12/etc.:
1. Добавить новый `TrustEdgeBootstrap` subtype.
2. Расширить `PairingService.claim()` branching по `pairingType`.
3. Создать новую Firestore subcollection (с её Security Rules).
4. wire-format `/pairings/{token}` — НЕ меняется (`pairingType` уже там); schemaVersion остаётся 1.

**Cost**: ~1 день на каждый новый use case (новый subtype + Firestore subcollection + UI flow).

### Open: ADR-007

Когда дойдём до спека 011 (или раньше) — создать `docs/adr/ADR-007-qr-pairing-as-trust-primitive.md`:
- Зафиксировать pattern и его границы.
- Регламенты безопасности: TTL 5 мин, alphabet `[A-HJ-NP-Z2-9]{6}`, idempotency через `claimed` flag.
- Когда **не** использовать pairing (когда proximity подразумевается, например NFC + Bluetooth).
- Migration policy при breaking changes wire-format.

---

## §что НЕ исследуется здесь

- **Применение `/config`** к раскладке OLD'а — спек 008.
- **Cloud Functions deploy mechanics** — не используются.
- **Production Firebase / Cloudflare projects** — отдельные решения.
- **iOS** — отдельный спек.
- **Звонки / Jitsi** — будущий спек; переиспользует Worker.

---

## §Open questions для /speckit.tasks

1. **Phase ordering**: Worker (Phase 5) до или после Android Firebase adapters (Phase 4)? Текущий план — Worker позже, чтобы Android тесты не зависели от Worker'а. Можно ли распараллелить?
2. **Firebase Auth Emulator + Worker dev**: как организовать integration-тестирование «Worker × локальный Firebase»? Wrangler не подключается к emulator'у напрямую — возможно нужны env-variable overrides.
3. **CameraX vs CameraView (Compose-friendly)**: какой API использовать? `androidx.camera.view.PreviewView` + AndroidView interop, или `androidx.camera.compose:camera-compose` (alpha)?
4. **Compliance docs**: при обновлении country-legal-tax-register — нужен ли вендор-аудит для Cloudflare (GDPR data-processor agreement)?

---

<!-- novice summary -->

## TL;DR для новичка

Этот документ — **«почему мы выбрали именно так»** для всех нетривиальных архитектурных решений спека 007.

**Главные истории**:
1. **C1 ревизировалась три раза**. Сначала выбрали Cloud Function (нужна карта, отказались). Потом client-side FCM (выяснилось технически невозможно). Сейчас — Cloudflare Worker (всё работает).
2. **Worker'у нужны библиотеки** для проверки ID-token (`jose` — лёгкая, 50KB) и для отправки FCM (raw HTTP — никаких тяжёлых SDK).
3. **QR — две библиотеки**: ZXing для генерации (легче, KMP-friendly), ML Kit для сканирования (точнее на плохих камерах).
4. **Firebase API key — публичный**, поэтому `google-services.json` в репо для dev — нормально. Реальная защита — Security Rules.
5. **Cloudflare free tier гигантский** — нам хватит на ~2000× нашего ожидаемого трафика. Когда упрёмся — апгрейд $5/мес.

**TODO-метки** разбросаны по всему документу — это инструкции «когда понадобится — иди сюда, делай так». Точки апгрейда: KV для rate-limit, retry-policy для FCM ошибок, named auth, custom domain, monitoring.
