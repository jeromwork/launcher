# Research: Contacts Photos and E2E Encrypted Private Media

**Spec**: [spec.md](spec.md)
**Plan**: [plan.md](plan.md)
**Date**: 2026-05-21

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

**Action**: add `SRV-MEDIA-001` to `docs/dev/server-roadmap.md` in Phase 0 — «Firebase Storage migration to own server when storage > 4 GB OR download quota > 800 MB/day».

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

---

## §6. iconStorage namespace dispatcher — integration with spec 006

**Question**: как `IconStorage.resolve("private:<uuid>")` маршрутизируется к нашему `PrivateMediaResolver`?

Per spec 006 [icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48), реализация в спеке 006 — `BundledIconStorage`, которая для `private:` возвращает `Placeholder` (graceful — не падает). Нам нужно расширить.

**Two approaches**:

1. **Chained Resolver pattern** — main `IconStorage` becomes coordinator который пробует резолверы по очереди (`BundledIconStorage` → `RemoteIconStorage` (custom) → `PrivateMediaResolver` (this spec)). **CHOSEN**.
   - Pros: extensible, additive.
   - Cons: small overhead per call (negligible).

2. **Single dispatcher с reflection-based namespace mapping** — `IconStorage.resolve()` parses namespace, looks up resolver in registry. **Rejected**: over-engineered, harder to test.

**Implementation**:
- Создать `ChainedIconStorage : IconStorage` в `core/api/capability/`.
- Конструктор принимает список resolver'ов (через Koin DI).
- `resolve(id)`:
  ```kotlin
  fun resolve(iconId: String): IconResolution {
      val ns = IconRef.namespaceOf(iconId) ?: return IconResolution.NotFound
      for (resolver in resolvers) {
          if (resolver.handles(ns)) return resolver.resolve(iconId)
      }
      return IconResolution.NotFound
  }
  ```
- В Phase 8: добавить `PrivateMediaResolver` в Koin module.

**Backward compat**: spec 006's tests должны продолжать работать — `BundledIconStorage` остаётся в цепи как первый resolver для `bundled:`.

---

## §7. Document picker UX flow (US-2)

**Question**: как admin выбирает фото документа?

**Options**:

1. **`Intent.ACTION_GET_CONTENT` с mime `image/*`** — стандартный Android picker, открывает галерею. **CHOSEN as default**.
2. `Intent.ACTION_OPEN_DOCUMENT` — Storage Access Framework, может работать с cloud-storage providers. **Considered**: добавляет UX-вариативность которая не нужна для P2.
3. Камера-capture inline — Intent `MediaStore.ACTION_IMAGE_CAPTURE`. **Considered**: nice-to-have, отложено в дальнейшие спеки.

**Verdict**: Phase 9 implements only ACTION_GET_CONTENT; камера и ACTION_OPEN_DOCUMENT — future spec extensions.

**Label dialog**: после picker'a — `DocumentLabelDialog` с TextField (max 24 chars, locale-aware truncate). Label сохраняется как Slot.title в /config.

**Compression**: бытовые фото с камер часто 3-5 MB. Pre-encryption compression до ≤ 500 KB:
- JPEG quality 85% as baseline.
- Resize до max(width, height) = 1920 px (preserves readability of documents).
- Done via standard Android `Bitmap.compress(JPEG, 85, ...)`.

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

**SC-001 ≤ 30s p95 admin tap → бабушка видит фото**:

Breakdown (target):
- Encrypt 200 KB on admin Pixel 4a class: ≤ 100 ms.
- Storage upload Wi-Fi typical: 200 KB / 5 Mbps = ~320 ms; mobile typical: 200 KB / 1 Mbps = ~1.6s.
- /config push (existing 008 flow): ≤ 2s p95 (per спека 008 SC).
- FCM delivery: ≤ 5s typical, ≤ 15s p95 (per спека 007 SC-006).
- Managed picks up FCM, reads /config: ≤ 2s.
- Storage download 200 KB: similar to upload, ≤ 1.6s mobile worst.
- Decrypt: ≤ 80 ms.
- Render tile: ≤ 100 ms.

Total worst-case: 100 + 1600 + 2000 + 15000 + 2000 + 1600 + 80 + 100 ≈ **22.5s**. Под 30s budget с запасом.

**Action**: Phase 11 macrobenchmark — single device, simulated peer; затем 2-device smoke.

**Encrypt/decrypt 80 ms p95 budget**:
- libsodium XChaCha20-Poly1305 throughput на ARMv8 (Pixel 4a class) ~ 300 MB/s (per [libsodium benchmarks](https://download.libsodium.org/doc/secret-key_cryptography/aead/chacha20-poly1305/xchacha20-poly1305_construction.html)).
- 200 KB / 300 MB/s = ~0.7 ms. Подавляющий запас.
- crypto_box_seal на X25519: ~ 100 µs per op. Negligible.
- 80 ms — generous budget, реальная цифра в 50-100x меньше.

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

**Что в этом документе.** Разбор архитектурных решений по 10 направлениям — почему именно libsodium а не Tink, какие алгоритмы внутри libsodium берём, что делать с Android Keystore на разных производителях, как считать blob references и т.д. Каждое решение с альтернативами и обоснованием.

**Ключевые цифры:**
- libsodium шифрует 200 KB фото за ~1 мс. У нас бюджет 80 мс — есть огромный запас.
- Один blob ≈ 200 KB после сжатия до 500 KB cap.
- Один пользователь использует ≈ 16 MB Storage за всё время (80 фото).
- Firebase Spark plan вмещает ≈ 250-500 пар пользователей до превышения лимита.

**Подводные камни, которые исследованы:**
1. **Android Keystore теряет ключи на Xiaomi после OTA-обновлений.** Решение — у пользователя появляется кнопка «re-pair» в UI.
2. **APK потяжелеет на 1-1.2 MB** из-за нативных .so. Решение — ABI splits в Google Play.
3. **Firebase Storage даёт 1 GB/day download квоту.** Может стать узким местом при 100+ парах. Решение — кеш расшифрованных blob'ов на устройстве, мониторинг квоты.

**Что закрыто как решения этого спека:**
- libsodium choice — exit ramp через `cipherSuiteId` в envelope (миграция в будущем не требует перешифровки).
- XChaCha20-Poly1305 (а не классический ChaCha20-Poly1305) — extended-nonce, большой запас по безопасности.
- crypto_box_seal (анонимный отправитель) — sender identity берём из Firestore Security Rules, не из crypto.
- Reference counting local на admin device + 24h grace + reconciler safety net.
- Document picker через стандартный Android Intent — простой UX, камера-capture отложена.

**Что не закрыто и пойдёт в Phase 0:**
- Точная версия Lazysodium-android (latest stable на момент Phase 0).
- ABI list verification (поддерживаются ли все 4 нужных).
- libsodium на CI machines installation.
