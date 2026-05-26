# Research: E2E Crypto Foundation

**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)
**Date**: 2026-05-21 / rev. 2 2026-05-22 (scope-split — universal crypto foundation; добавлены §2b Ed25519, §2c BLAKE2b, §3b clear-data grace, §11 constant-time recipient search; §6 PrivateMediaResolver и §7 Document picker UX перенесены в спек 012)

Этот документ собирает результаты исследования по архитектурным решениям, которые требуют сравнительного анализа альтернатив (CLAUDE.md §3 one-way doors).

---

## §1. One-way door: выбор Lazysodium-android как crypto-библиотеки

**Решение принято в**: spec.md §Clarifications C-4 (mentor session 2026-05-21).

**Альтернативы:**

| Кандидат | Pros | Cons | Verdict |
|---|---|---|---|
| **Lazysodium-android (libsodium)** | Open-source community-maintained; identical runtime across platforms (Android, iOS, server-side); `crypto_box_seal` ready (hybrid encryption out of box); минимальное API (мало кнопок — мало ошибок); RFC-aligned algorithms (XChaCha20-Poly1305 = RFC 8439 extended; X25519 = RFC 7748); active maintainer (Frank Denis aka jedisct1) | JNI native binding (.so per ABI = APK size delta); on JVM unit tests need libsodium installed on CI; Lazysodium API design quirky (3 layers — Native, Lazy, Sealing) | **CHOSEN** |
| Tink (Google) | First-class Android support; pure Java; KMS integration (опционально); maintained by Google crypto team | KMS-orientation = doc noise; runtime locked to Java/Kotlin/Go/C++/Python (no Rust if future server picks Rust); ownership risk — Google may archive projects | Rejected: lose flexibility for future server migration (CLAUDE.md rule 8) |
| Conscrypt + BouncyCastle | No new dependencies (ship as platform); fine-grained API | Low-level: build cryptosystem from primitives = high error risk; противоречит spec.md «никакой самописной крипты» | Rejected: violates explicit user requirement |
| Signal Protocol (libsignal) | Forward secrecy out of box (Double Ratchet); X3DH key agreement; battle-tested in Signal/WhatsApp | Overkill for current scope; async session state complexity; Java port less polished than libsodium | Deferred: candidate для spec ~018 (forward secrecy) |

**Decision rationale**:
- libsodium закрывает все 011 needs одним пакетом без избытка.
- Universal runtime — критично для CLAUDE.md rule 8 (server migration future).
- Vendor neutrality — нет owner, который может закрыть проект; форки NaCl от Bernstein.
- AEAD из коробки (XChaCha20-Poly1305) — extended-nonce variant даёт misuse-resistance (192-bit nonce — random collisions cryptographically негде).

**Exit ramp (per CLAUDE.md rule 3)**:
- `cipherSuiteId: String` в envelope с первого commit'a (см. contracts/crypto-envelope.md).
- Текущее значение: `xchacha20poly1305_x25519_sealed_v1`.
- При смене библиотеки: новые blob'ы пишутся с другим `cipherSuiteId`, старые читаются по правилам v1 через сохранённый Libsodium-adapter.
- Стоимость перехода = код миграции (один раз, ≈ 1-2 недели для написания второго adapter set), НЕ перешифровка blob'ов.

**Action items для Phase 0**:
1. Зафиксировать exact version Lazysodium-android в `gradle/libs.versions.toml` (target: latest stable 5.x.x на дату Phase 0; на 2026-05-21 это `5.1.4` per Maven Central).
2. Проверить, что Lazysodium-android выпускается под подходящие ABI (armeabi-v7a, arm64-v8a, x86, x86_64) — на 2026-05-21 все четыре поддерживаются.
3. Добавить в CI machine setup установку libsodium для desktop JVM unit tests (`apt-get install libsodium23` для Ubuntu CI, `brew install libsodium` для macOS).

---

## §2. Algorithm choices внутри libsodium

**AEAD: XChaCha20-Poly1305 (libsodium `crypto_secretbox_xchacha20poly1305_*`)**

Альтернативы внутри libsodium:
- ChaCha20-Poly1305 (RFC 8439, 96-bit nonce) — стандартный, но nonce 96 бит → коллизия после ~2^32 blob'ов при random nonce. Для нашего scope (≤ 1000 pairs × 80 blob = 80K) — безопасно, но запас минимальный.
- **XChaCha20-Poly1305 (libsodium-specific, 192-bit nonce)** — extended-nonce variant, random nonce безопасен до 2^96 операций. Эффективная гарантия misuse-resistance.
- AES-256-GCM-SIV — отличная альтернатива, но не входит в libsodium core; требует extension.

**Verdict**: XChaCha20-Poly1305 — для нашего use case оптимальный баланс безопасности и простоты.

**Asymmetric: X25519 + crypto_box_seal**

`crypto_box_seal` = libsodium-native «anonymous sender hybrid encryption». Шаги внутри:
1. Sender генерирует ephemeral X25519 keypair.
2. ECDH между ephemeral priv и recipient pub → shared secret.
3. KDF: BLAKE2b(ephemeral_pub || recipient_pub) → encryption key для CEK.
4. ChaCha20-Poly1305 encrypt CEK using derived key.
5. Output = ephemeral_pub || sealed_cek (sender identity not in envelope — anonymous).

В нашем случае sender authentication обеспечивается отдельным механизмом — Firestore Security Rules (write `/config` от admin'a проходит только если writer's uid = adminId). Crypto envelope не нуждается в sender identity. **Анонимный sender — exactly what we want.**

Альтернатива — `crypto_box` (authenticated sender via long-term sender keypair). Отброшено: добавляет сложность без выгоды (sender auth уже есть через Firestore).

**Verdict**: `crypto_box_seal` over X25519.

**KDF: библиотека по умолчанию**

libsodium внутри `crypto_box_seal` использует BLAKE2b для derivation. Это нативное решение библиотеки — не наш выбор. Хорошо, потому что устраняет один источник bugs (KDF spec'aми ошибиться сложно, но возможно).

---

## §2b. Algorithm choice: Ed25519 для signing (new 2026-05-22)

**Context**: спек 011 rev. 2 расширен на universal crypto foundation — `DigitalSignature` port нужен для:
- Anti-tamper подпись Pub-key publication в Firestore (FR-006 + DRIFT-7 remediation).
- Будущая авторизация Jitsi room join (TBD-Jitsi).
- Будущая HMAC/JWT signing для vendor APIs (TBD-Vendor).
- Будущая device attestation для hardware (TBD-Hardware).

**Alternatives:**

| Кандидат | Pros | Cons | Verdict |
|---|---|---|---|
| **Ed25519** (libsodium `crypto_sign`) | Современный elliptic-curve; быстро (~50 µs sign/verify на ARMv8); 32-byte Pub, 64-byte signature; RFC 8032 standard; deterministic signatures (нет nonce reuse risk); native в Android Keystore с API 31+ | API 30 (наш minSdk) не имеет native Keystore Ed25519 — fallback на AES-wrap | **CHOSEN** |
| ECDSA P-256 | Native в Android Keystore с API 23+ | Non-deterministic signatures (требуется secure RNG для nonce); большая complexity implementation; больший signature size (~70 bytes) | Rejected: deterministic Ed25519 устраняет один источник bugs |
| RSA-2048 / RSA-3072 | Wide compatibility | Медленный (~ms per op); большие ключи + signatures (256+ bytes); legacy | Rejected: overhead против Ed25519 без выгоды |
| HMAC-SHA-256 (для b2b APIs) | Симметричный, простой | Требует shared secret — нерелевантно для anti-tamper Pub publication (нет общего секрета между admin и Firestore admin attacker) | Rejected for primary use; libsodium HMAC доступен через тот же binding для будущих vendor integrations |

**Decision rationale**:
- Ed25519 — modern best practice (используется в Signal, WireGuard, SSH, TLS 1.3).
- libsodium has built-in `crypto_sign` API (одна функция, no parameter choices).
- Deterministic — устраняет class of nonce-reuse vulnerabilities.
- Native в Android Keystore с API 31+ (Android 12) — на новых устройствах hardware-backed; на API 30 fallback на AES-wrap.

**Exit ramp**: смена signing algorithm в будущем = новый `signatureAlgorithm` field в DeviceIdentity wire-format (sister к `cipherSuiteId` в envelope). Старые подписи продолжают verify'иться старым кодом.

---

## §2c. Algorithm choice: BLAKE2b для hashing (new 2026-05-22)

**Context**: `HashFunction` port нужен для:
- Integrity checks (например, fingerprint Pub-ключа для logging — log shows hash, not key).
- Future spec 012: дедупликация blob'ов по content-hash.
- Future TBD-Jitsi: room key fingerprint для safety numbers UX.
- Future TBD-Vendor / TBD-Hardware: integrity verification.

**Alternatives:**

| Кандидат | Pros | Cons | Verdict |
|---|---|---|---|
| **BLAKE2b** (libsodium `crypto_generichash`) | Быстрее SHA-256 на 64-bit (наш Android — все ARMv8 64-bit); RFC 7693; cryptographically strong; output size configurable (мы берём 32 bytes); libsodium native; используется внутри `crypto_box_seal` (consistency) | Менее известен чем SHA-256 в широкой публике | **CHOSEN** |
| SHA-256 | Стандарт; быстрый на dedicated hardware (Intel SHA-NI); вездесущность | Чуть медленнее BLAKE2b на ARM без SHA extensions; **уже встроен в Android SDK** через `MessageDigest`, но мы хотим всё через libsodium ports | Rejected: один primitive vendor (libsodium) лучше, чем mix |
| SHA-3 (Keccak) | Standardized; cryptographically diverse от SHA-256 | Медленнее обоих на CPU; редко используется на mobile | Rejected: no current use case |
| Argon2id (если когда-то понадобится password hashing) | Memory-hard для password resistance | Heavy — не для general hashing | Defer: libsodium has it; добавится если когда-нибудь введём password-protected backup |

**Decision rationale**:
- BLAKE2b — modern, fast, libsodium-native.
- Все hashing — через один port; библиотека libsodium уже в проекте.

---

## §2d. Constant-time recipient search (new 2026-05-22 — CHK-SEC-018)

**Context**: при unsealing CEK из envelope, наше устройство ищет свой `deviceId` в `recipients[]` массиве. Naive implementation — `recipients.find { it.deviceId == ownDeviceId }` — **не constant-time**: время выполнения зависит от позиции own deviceId в массиве.

**Threat**: external observer (TLS layer, network monitoring) может измерить timing of decryption и определить, в какой позиции own deviceId находится в массиве. В 011 при длине 1 это не утечка, но **в спеках 014 (группы) и 015 (multi-device)** длина может быть N. Timing leak позволяет attacker'у учить «admin device в позиции 3 из 5» — privacy утечка для membership.

**Mitigation**:
- В `unsealCEK` flow перебрать **все** recipients, попытаться unsealCEK каждым (с одним `Priv`), отметить success/fail в constant-time fashion (например, через `libsodium.utils.SODIUM_constant_time_compare`).
- Возвращать первый successful unsealCEK.
- Время = O(N) независимо от позиции.

**Implementation note**: libsodium `crypto_box_seal_open` уже constant-time (внутри MAC verification). Мы добавляем constant-time на уровень recipient search above it.

**Test**: `ConstantTimeRecipientSearchTest` — measure timing variance for envelopes where own deviceId at position 0 vs position N-1. Variance MUST be < 5% (статистический шум).

**Cost**: O(N) расшифровка вместо O(1). При N=1 (011-012) overhead = 0. При N=10 (groups) overhead = ~5 ms на типичном телефоне (negligible).

---

## §3. Android Keystore — OEM quirks и mitigation

**Risk**: spec.md §Risks R2 — некоторые OEM (Xiaomi MIUI, Huawei EMUI, Samsung) теряют Keystore-хранённые ключи после:
- OTA-обновления системы.
- Factory reset (ожидаемо, но иногда без warning'a пользователю).
- Clear app data через настройки.
- В редких случаях — реboot или MIUI «freeze unused apps» feature.

**Researched data sources**:
- Android Keystore [official docs](https://developer.android.com/training/articles/keystore).
- StackOverflow / Android Issue Tracker known issues — список консистентен с MIUI 12-14 и EMUI 9-12.
- Lazysodium issue tracker — не упоминает OEM-specific проблем.

**Decisions for implementation**:

1. **Prefer hardware-backed Keystore** где доступен:
   - StrongBox (API 28+, hardware separate chip).
   - TEE (Trusted Execution Environment, API 23+).
   - Software-backed (API 23+, always available).
   - Fallback ordering в `AndroidKeystoreSecureKeystore` adapter: StrongBox → TEE → Software.

2. **Key invalidation policy:**
   - Detect missing key при попытке `loadPrivate("launcher_device_priv_v1")` → throw `CryptoError.KeyNotFound`.
   - UI flow: пользователь видит сообщение «требуется re-pairing» (already covered by spec FR-024 Scenario 2).
   - Re-pairing flow (спек 007) генерирует свежий keypair, публикует новый Pub, отзывает старый Pub document в Firestore (admin может удалить через manual revoke).

3. **Pre-emptive backup decision:** **НЕ делаем** backup приватного ключа в облако.
   - Причина: e2e гарантия — нарушение если key escrow.
   - Trade-off: потеря Keystore = потеря старых blob'ов. Принимается как UX-cost (см. spec.md §Clarifications C-1, §Assumptions «No server-side key escrow»).
   - Recovery problem решается отдельным спеком ~017 (multi-device recovery).

4. **Known-broken OEM list maintained in research.md** (this document, future updates):
   - **Xiaomi MIUI 13+**: known issue with Keystore loss after OTA. Mitigation: detect + UI prompt.
   - **Huawei EMUI 10-12**: known issue with Software-backed key invalidation after «Phone Cloner» migration. Mitigation: same.
   - **Samsung One UI 5+**: generally stable but known issue with «Maintenance Mode» — Keystore inaccessible during maintenance. Mitigation: retry on `KeyPermanentlyInvalidatedException`.

5. **Testing**: Phase 11 manual smoke includes 1 device per major OEM (Xiaomi, Samsung, generic AOSP); если есть Huawei — добавить.

---

## §4. Firebase Storage — Security Rules, layout, и quota

**Storage Rules requirements (FR-050):**

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /links/{linkId}/private-media/{uuid} {
      function isLinkMember() {
        return request.auth != null && (
          firestore.get(/databases/(default)/documents/links/$(linkId)).data.adminId == request.auth.uid ||
          firestore.get(/databases/(default)/documents/links/$(linkId)).data.managedDeviceFirebaseUid == request.auth.uid
        );
      }
      allow read: if isLinkMember();
      allow write: if isLinkMember() && request.resource.size < 500 * 1024; // 500 KB cap
      allow delete: if isLinkMember();
    }
  }
}
```

**Rationale**:
- Cross-link reads blocked.
- 500 KB cap matches FR-cap для photo compression на admin side.
- Firestore.get() inside Storage Rules: cross-service rule check, supported per [Firebase docs](https://firebase.google.com/docs/rules/rules-and-auth#cross-service_rules) (verify version at Phase 0).

**Storage quota analysis** (Spark plan):
- 5 GB total / 1 GB download daily / 20K writes daily.
- Average pair: 80 blobs × 200 KB avg = 16 MB.
- Pair count vs quota:
  - 100 pairs → 1.6 GB → comfortable.
  - 250 pairs → 4 GB → close to limit.
  - 500+ pairs → exceed Spark → need Blaze or own server.
- **Download quota**: assume Managed re-downloads blobs ≤ 1× per day (DecryptCache hits hot path) → 80 × 200 KB × 100 pairs = 1.6 GB/day → **exceeds 1 GB/day at 100 pairs**.
- **Mitigation**: DecryptCache LRU keep most-accessed; eviction policy budget conservative. Phase 11 measures actual cache hit ratio.

**Action**: add `SRV-CRYPTO-001` to `docs/dev/server-roadmap.md` — универсальный маршрут переезда крипто-инфраструктуры на собственный backend (не привязан к Firebase лимитам, триггер = запуск собственного backend проекта). ✅ Сделано 2026-05-22.

---

## §5. Reference counting with history snapshots — design

**Requirement**: spec.md FR-031 + Clarifications C-7 — учитываем references из current + всех history snapshots.

**Design approaches considered**:

1. **Local-only ledger on admin device (BlobReferenceLedger.sq)** — admin tracks все references locally в SQLite; при удалении контакта пересчитывает; cleanup async через WorkManager. **CHOSEN**.
   - Pros: simple, no server-side logic, fits «no server» constraint.
   - Cons: admin device is single source of truth — если admin лост телефон без revoke, blob'ы остаются в Storage forever.

2. **Server-side reference count document** в Firestore `/links/{linkId}/private-media-index/{uuid}` с полями `{refCount, lastSeen}`. **Considered, rejected**.
   - Pros: durable across admin device loss.
   - Cons: introduces server-side logic complexity; requires Cloud Functions для recount triggers (или client-side transactions с race condition risk); противоречит CLAUDE.md rule 8 «no server beyond Worker FCM relay».

3. **Periodic full scan** — раз в день admin собирает все references из current + history snapshots, считает delta vs Storage list, удаляет orphans. **Considered as supplement**.
   - Pros: self-healing, не зависит от local ledger consistency.
   - Cons: enumerate Storage objects requires Storage list permission (Spark plan supports listing).
   - **Decision**: implement в Phase 7 как background reconciler (24h cadence), backup over local ledger.

**Final design** = local ledger (primary) + periodic reconciler (safety net).

**Algorithm для BlobReferenceLedger**:
```
on /config push:
  for each Contact.photoRef = "private:<uuid>":
    ledger.incrementRef(uuid, source = "config-current")
  for each Slot.iconId = "private:<uuid>" (документ-tile):
    ledger.incrementRef(uuid, source = "config-current")

on /config push (after the new config wins):
  for each previously-known ref no longer in new config:
    ledger.decrementRef(uuid, source = "config-current")
    if total refs == 0 AND lastChangeAge > 24h:
      schedule delete via WorkManager (best-effort)

on history snapshot capture (спек 009 retention 10):
  same as /config push but source = "history-slot-N"

on history snapshot eviction (спек 009 retention 10):
  decrement refs from that slot
  cleanup как выше

on revoke link:
  delete all blobs under /links/{linkId}/private-media/* via spec 007 FR-033 recursive delete
  clear ledger entries for this linkId
```

**24h grace period** — защищает от race conditions (admin removes + immediate rollback before delete fires). Принимается как acceptable storage cost.

### §5b. WorkManager retry policy (new 2026-05-22 — CHK-FR-012)

Все Storage delete/upload через WorkManager. Конкретная политика:
- **Backoff**: exponential — 1m → 5m → 30m → 2h → 12h.
- **Max attempts**: 5 (cumulative ~15 hours wall time).
- **After exhaustion**: warning log с категорией `storage_delete_exhausted` / `storage_upload_exhausted`, blob/operation остаётся «pending forever» в ledger; **никаких автоматических retry** после exhaustion (предотвращает infinite quota burn).
- **Foreground service**: WorkManager использует JobScheduler; на API 34+ foreground service не нужен, потому что individual retry < 10 min (CHK-PERM-006 ok).
- **Surfacing**: в спеке 012 admin UI отобразит indicator «N blob'ов pending delete после network outage». В 011 — только log.

### §5c. Clear-data edge case (new 2026-05-22 — CHK-FR-015)

**Сценарий**: пользователь делает «настройки Android → приложение Launcher → очистить данные». Effect:
- Вся SQLite DB стирается (включая BlobReferenceLedger).
- SharedPreferences стирается (включая own DeviceId).
- Android Keystore — НЕ стирается (живёт отдельно), но без знания alias'а ключи бесполезны.

**Проблема**: после restart приложение генерирует новый DeviceId, новые keypairs, начинает новую identity. Blob'ы в Storage остаются с references на старый identity. Background reconciler видит «refCount = 0 для всех» → запускает delete → теряет blob'ы, которые ещё нужны в /config бабушки.

**Mitigation**:
- При первом запуске после clear-data — записать sentinel-row в systemctl-table DB с `clearDataAt = now`.
- Reconciler перед запуском проверяет: `if (now - clearDataAt < 7 days) → SKIP this cycle, log "clear-data grace period"`.
- За 7 дней:
  - /config от Firestore синхронизируется (refs восстановятся для `"config-current"`).
  - History snapshots (спек 009) либо пере-захватятся, либо явно теряются (acceptable — clear-data = пользователь согласился потерять историю).
  - Pairing re-established (новый identity опубликован).
- После 7 дней reconciler работает нормально.

**Why 7 days, а не 24h или 30 days**:
- 24h недостаточно — типичный offline-сценарий (отпуск, выходные) длиннее.
- 30 days — слишком долгое накопление orphan'ов, может превысить Storage квоту.
- 7 days — sweet spot: покрывает большинство sync delays (~95% случаев), не накапливает слишком много orphan'ов.

**Implementation**: sentinel-row в `BlobReferenceLedger` системной partition (или отдельная маленькая `SystemMeta` таблица с key/value). Detect через absence: если row отсутствует на startup, значит — DB была wiped, запиши `clearDataAt = now`. Безопасно — false positive (rare случай DB corruption) приводит к 7-day delay, не к data loss.

---

## §6. (out of 011 scope) iconStorage namespace dispatcher

Реализация `PrivateMediaResolver` (IconStorage namespace dispatch для `private:`) переехала в спек **012**. В 011 фундамент дает порты + adapters + storage; resolver — отдельная feature, реализуется клиентом фундамента.

**Кратко для контекста**: спек 012 добавит `ChainedIconStorage : IconStorage` в `core/api/capability/`, который пробует resolver'ы по очереди (`BundledIconStorage` → `RemoteIconStorage` → `PrivateMediaResolver`). До спека 012 — `IconStorage.resolve("private:<uuid>")` возвращает `Placeholder` per спек 006 [icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48).

---

## §7. (out of 011 scope) Document picker UX flow

UX-flow добавления документов (через `Intent.ACTION_GET_CONTENT`, label dialog, compression, fullscreen viewer) — переехал в спек **012**. В 011 нет UI вообще.

---

## §8. ADR-007 contents — TrustEdgeBootstrap second subtype

**Pre-existing context**:
- ADR-007 был запланирован как future в `TODO-DOC-001` ([project-backlog.md:378](../../docs/dev/project-backlog.md#L378)).
- Спек 007 ввёл `TrustEdgeBootstrap` концепт (один subtype — `LinkBootstrap`, описывает initial state link'a).

**ADR-007 — content draft**:
- **Title**: TrustEdgeBootstrap subtypes for per-device asymmetric keys.
- **Status**: Accepted (after Phase 1 — finalized in code).
- **Context**: Спек 011 вводит per-device key pairs. При первом запуске приложения генерируется (Pub, Priv). При pairing'е (спек 007) оба устройства должны опубликовать Pub-ключи для последующей шифровки сообщений друг другу. Текущий `TrustEdgeBootstrap.LinkBootstrap` имеет одно поле — `presetId`. Нужен второй subtype.
- **Decision**:
  - Renaming `TrustEdgeBootstrap` → keep as sealed interface.
  - First subtype `LinkBootstrap` — existing from спека 007.
  - Second subtype `DeviceKeyBootstrap(deviceId: DeviceId, publicKey: PublicKey)` — added in спеке 011.
  - Pairing flow (спек 007) extended additive — after `consent.allow` оба устройства публикуют DeviceKeyBootstrap.
  - Sequencing: Pub publishing должен завершиться до первого encrypt — а первый encrypt происходит сильно позже pairing'а (когда admin фактически добавит фото). Sufficient time.
- **Consequences**:
  - Schema migration: none (additive).
  - Wire format: new collection `/links/{linkId}/devices/` per pairing.
  - Security Rules: members of link can read peers Pub; only owner uid can write own Pub.
- **Final document**: создаётся в Phase 1 как `docs/adr/ADR-007-trust-edge-bootstrap-subtypes.md` (если есть `docs/adr/` directory — иначе inline в plan.md).

---

## §9. Performance budgets — justification

**SC-001 ≤ 60s p95 manual smoke (16 bytes synthetic blob from admin device to managed device)**:

В 011 — только smoke с синтетическими 16 байтами, не реальные фото. End-to-end measurement пути encrypt → upload → push pairing-state → download → decrypt:
- Encrypt 16 bytes: ≤ 1 ms (negligible).
- Storage upload Wi-Fi typical: ≤ 200 ms.
- Pairing state sync (Firestore): ≤ 2s.
- FCM delivery: ≤ 5s typical, ≤ 15s p95 (per спека 007 SC-006).
- Managed picks up FCM, fetches envelope: ≤ 2s.
- Storage download: ≤ 200 ms.
- Decrypt + signature verify: ≤ 10 ms.

Total worst-case: ~25s, with 60s budget — comfortable headroom для slow networks.

**Performance budgets для крипто-операций (verified в Phase 3)**:
- libsodium XChaCha20-Poly1305 throughput на ARMv8 (Pixel 4a class) ~ 300 MB/s (per [libsodium benchmarks](https://download.libsodium.org/doc/secret-key_cryptography/aead/chacha20-poly1305/xchacha20-poly1305_construction.html)).
- 200 KB blob (для будущих фото в 012) / 300 MB/s = ~0.7 ms. Подавляющий запас.
- crypto_box_seal на X25519: ~ 100 µs per op. Negligible.
- Ed25519 sign/verify: ~50 µs per op. Negligible.
- BLAKE2b-256: ~500 MB/s. Negligible.

**Cold-start regression**: нет UI в 011, нет lazy decrypt на cold-start path. Cold start не должен меняться от 011.

**APK delta budget**: 1.0-1.2 MiB до ABI splits; ≤ 300 KiB per ABI after splits. Phase 9 (Konsist) фиксирует это в perf-checkpoint.

---

## §10. (deferred) Backward compatibility — после merge 011

В 011 нет visible feature → нет наблюдаемого backward-compat risk. Реальный risk появится со спеком 012 (когда `Contact.photoRef` начнёт получать non-null значения и старые Managed на 010-011 столкнутся с `private:<uuid>` URI). Анализ — в research.md спека 012.

В 011 only forward-compat concern — envelope `cipherSuiteId` registry. Документировано в [contracts/crypto-envelope.md](contracts/crypto-envelope.md).

---

## §11. Constant-time recipient search

Переехало в §2d (см. выше). Здесь только note: implementation на Phase 3, testing на Phase 9 (Konsist + timing variance test).

---

## §10. Backward compatibility — Managed на 010 vs admin на 011

**Scenario**: admin обновился на 011 и публикует `/config` с `Contact.photoRef = "private:<uuid>"`. Managed ещё на 010.

**Behaviour на Managed 010**:
- 010 не знает crypto code, не пытается скачать blob.
- `IconStorage.resolve("private:<uuid>")` per spec 006 [icon-id-namespace.md:63](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L63) возвращает `Placeholder` (gracefully).
- Tile показывает generic placeholder. Не падает. Не вызывает `media_decrypt_failed`.
- Когда Managed обновится на 011 + сделает re-pair (для key exchange) — фото появится.

**Verdict**: spec 006's forward-compat для unknown namespace + спека 011's additive `photoRef` field позволяют rolling upgrade без breaking changes. ✓

---

<!-- novice summary -->

## TL;DR (простым языком)

**Что в этом документе.** Разбор архитектурных решений по 11 направлениям для криптофундамента: почему libsodium, какие 4 примитива внутри (encryption + signing + hashing + key-agreement), как обходить Android Keystore quirks, как считать references, что делать при clear-data, как защититься от timing leaks.

**Ключевые цифры (исследованы):**
- libsodium шифрует 200 KB за ~1 мс. У нас бюджет 80 мс — огромный запас.
- Ed25519 sign/verify: 50 µs per op. Negligible.
- BLAKE2b-256: 500 MB/s. Negligible.
- APK потяжелеет на 1.0-1.2 MB до ABI splits, ≈ 300 KB после splits per device.

**Подводные камни, которые исследованы:**
1. **Android Keystore теряет ключи на Xiaomi после OTA-обновлений.** Решение — у пользователя кнопка «re-pair» (в спеке 012, UX там).
2. **APK потяжелеет.** Решение — ABI splits в Google Play.
3. **Firebase Storage 5 GB/1 GB-day квота.** Не критично для 011 (фундамент льёт только синтетические smoke-блобы). Реальное наполнение — спек 012. Migration documented через `SRV-CRYPTO-001` в server-roadmap.
4. **Timing side-channel** при поиске own deviceId в recipients[] (новое 2026-05-22) — наивная реализация даёт privacy утечку про membership в группе. Решение — constant-time iteration через `libsodium.utils`.
5. **Clear-data wipe** локальной DB (новое 2026-05-22) — стирает ledger references, reconciler может удалить blob'ы, которые ещё нужны. Решение — 7-day grace period перед reconciliation после detect clear-data.

**Что закрыто как решения этого спека:**
- libsodium (Lazysodium-android) — universal crypto vendor.
- AEAD: XChaCha20-Poly1305 (extended-nonce, misuse-resistance).
- Asymmetric encryption: X25519 + `crypto_box_seal` (anonymous sender, hybrid).
- **Signing: Ed25519** (новое — для anti-tamper Pub publication + future Jitsi/vendor/hardware) (§2b).
- **Hashing: BLAKE2b-256** (новое — для integrity + future deduplication + future fingerprints) (§2c).
- Reference counting local на admin device + 24h grace + 7-day clear-data grace + WorkManager exp backoff с max 5 attempts.
- Constant-time recipient search.

**Что переехало в спек 012 (не в этом research):**
- IconStorage namespace dispatcher для `private:` (§6).
- Document picker UX (§7).
- Real photo encrypt/upload performance budgets.

**Что не закрыто и пойдёт в Phase 0:**
- Точная версия Lazysodium-android (latest stable на момент Phase 0; на 2026-05-22 — `5.1.4`).
- ABI list verification (поддерживаются ли все 4 нужных).
- libsodium на CI machines installation.
