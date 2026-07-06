---
id: TASK-67
title: Pairing Feature + pairing-edges bucket
status: Draft
assignee: []
created_date: '2026-06-28 18:30'
updated_date: '2026-07-06'
labels:
  - phase-2
  - F-feature
  - pairing
  - cloud
  - one-way-door
milestone: m-1
dependencies:
  - TASK-65
  - TASK-66
  - TASK-51
  - TASK-102
  - TASK-106
priority: high
ordinal: 67000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Pairing — это **связь между двумя устройствами**, не «admin app для родственника». Любое устройство в любом профиле (`simple-launcher`, `workspace`, future `clinic-patient`) может: (а) **управляться** другим устройством («кто управляет мной»), (б) **управлять** другими устройствами («кем управляю я»). Профиль решает только **где в UI** эти списки находятся (в simple-launcher — через 7-tap settings, в workspace — на главном экране). Связи (pairing-edges) живут под root key (TASK-6) как один из buckets — переживают переключение профиля, переживают потерю устройства, восстанавливаются автоматически при recovery.

## Что это простыми словами

Два устройства встречаются через QR-код и устанавливают **доверенную связь** (pairing edge): обмен криптографическими ключами, чтобы потом каждое могло убедиться «да, я говорю с тем, с кем спарилось».

**Что происходит по шагам (нормальный сценарий — pairing):**
1. Устройство A открывает экран pairing (через плитку на главном или через settings — зависит от профиля).
2. Нажимает «Показать QR».
3. На экране A показывается QR с временным публичным ключом + nonce.
4. Устройство B открывает свой экран pairing → «Сканировать QR».
5. B сканирует QR с A. Происходит handshake (Curve25519): оба обменялись эфемерными ключами, подписали обмен своими identity-ключами (TASK-6 KeyRegistry), вычислили общий секрет.
6. Через <10 секунд оба видят сообщение «связь установлена».
7. У A в списке «кем я управляю» появилось B. У B в списке «кто управляет мной» появилось A.

**Что происходит по шагам (recovery):**
1. У A сломался телефон, купил новый.
2. Зашёл через тот же Google + ввёл passphrase (TASK-6 flow).
3. Root key восстановлен, все buckets автоматически восстановились через TASK-66 — **включая pairing-edges**.
4. У A в списке снова видны все спаренные устройства. Никакого re-pairing.

**Что происходит при разрыве (revoke):**
1. A решил больше не управлять B (или наоборот).
2. Открыл список → нажал «Отозвать».
3. Edge помечается как revoked в bucket (`AddOnlyRevokeOnly` conflict policy из TASK-66). 
4. При следующей синхронизации обе стороны видят отзыв.
5. Reconciliation в bab's device (per [TASK-102](task-102%20-%20Decision-Revoke-policy.md)) triggers MLS Remove — target device теряет crypto group access.

## Детали протокола (шаг за шагом)

Полная последовательность от нажатия «Показать QR» до «оба устройства в MLS group». Совмещает pairing handshake (TASK-67 core) + LOCAL→CLOUD upgrade (TASK-106) + MLS group ownership (TASK-102).

**Предпосылки**:
- **A** (bab, "показывает QR"): LOCAL identity + `root_A_keypair` в TEE. `identity_id_A = hash(root_A_public)`. Может уже иметь MLS device management group (не первое pairing) или нет (первое).
- **B** (Тана, "сканирует QR"): LOCAL identity + `root_B_keypair` в TEE. `identity_id_B = hash(root_B_public)`. Ещё не сделала cloud upgrade, нет JWT.

**Шаги**:

1. **A показывает QR**. Генерит ephemeral X25519 keypair `(eph_A_priv, eph_A_pub)`. Формирует `PairingHandshakeBlob`:
   ```
   { schemaVersion: 1,
     ephemeralPubKey: eph_A_pub,
     nonce: random(32),
     claimToken: random(16),           // server-side session correlation
     sourceDeviceId: A_device_id,
     sourceIdentityId: identity_id_A,   // B нужно знать чтоб указать target на claim
     expiresAt: now() + 5min }
   ```
   Сериализует → QR-encode на экран.

2. **B сканирует QR**. Parses blob. Validates `expiresAt` (не expired). Генерит свой ephemeral X25519 keypair `(eph_B_priv, eph_B_pub)`.

3. **B делает cloud upgrade** (первый раз):
   - Firebase Auth Anonymous provider → JWT.
   - `POST /v1/identity/register { schemaVersion: 1, publicKey: root_B_public }` — server регистрирует `identity_id_B` в реестре.

4. **B публикует KeyPackages**:
   - `POST /v1/keypackage/publish { schemaVersion: 1, batch: [KP1..KP100] }` с JWT.
   - Server: сохраняет в `KEYPACKAGE_POOL[identity_id_B]`. Response: `{ stored: 100, poolSize: 100 }`.

5. **B выполняет ECDH handshake**:
   - `shared_secret = X25519(eph_B_priv, eph_A_pub)`.
   - Signs proof: `signature = Ed25519_sign(root_B_priv, nonce || eph_B_pub || eph_A_pub)`.
   - Формирует `PairingResponseBlob`:
     ```
     { schemaVersion: 1,
       ephemeralPubKey: eph_B_pub,
       identityId: identity_id_B,
       identityPublicKey: root_B_public,
       signature: <Ed25519 sig>,
       claimToken: <echoed from step 1> }
     ```

6. **B доставляет responseBlob к A через сервер**:
   - `POST /v1/pairing/complete { schemaVersion: 1, targetSourceDeviceId: A_device_id, claimToken, responseBlob }`.
   - Server: validates `claimToken` matches active session (записанный when QR displayed), кладёт responseBlob в `PAIRING_INBOX[A_device_id]`, FCM push A: "pairing response arrived".

7. **A получает responseBlob** через inbox:
   - Poll `GET /v1/pairing/inbox` или FCM wakeup → download.
   - `shared_secret = X25519(eph_A_priv, eph_B_pub)` — должен match B's computation.
   - Verify `signature` used `identityPublicKey` (proves B holds `root_B_priv`).
   - Constructs `TrustEdge { edgeId, peerIdentity: identity_id_B, peerPubKey: root_B_public, role: EdgeRole.ManagedByMe, createdAt: now() }`.
   - Stores в `pairing-edges` bucket.

8. **A выполняет MLS Add** (per [TASK-102](task-102%20-%20Decision-Revoke-policy.md), A = sole executor):
   - `POST /v1/keypackage/claim { schemaVersion: 1, targetIdentityId: identity_id_B }` → server returns один KP из `pool[identity_id_B]` (consume).
   - openmls: check если device management group exists для A:
     - **Нет** (first pairing ever) → `MlsGroup.create()` → epoch 0, только A внутри.
     - **Есть** → загружает existing group state.
   - `MlsGroup.add_member(kp_B)` → генерирует Commit (для existing members, если есть) + Welcome (для B). Epoch → N+1.

9. **A отправляет Welcome к B**:
   - `POST /v1/group/welcome { schemaVersion: 1, targetIdentityId: identity_id_B, welcomeBlob }`.
   - Server: кладёт в `GROUP_INBOX[identity_id_B]`, FCM push B: "welcome arrived".

10. **A отправляет Commit existing members** (если group уже была не пустая):
    - `POST /v1/group/commit { schemaVersion: 1, groupId, commitBlob }`.
    - Server: fanout всем members' `GROUP_INBOX[identity_id_X]` (кроме B — B получает Welcome отдельно).

11. **B получает Welcome, joins**:
    - Poll inbox / FCM wakeup → download Welcome.
    - `MlsGroup.join_from_welcome(welcome_blob)` → materialize group state локально в SQLCipher.
    - Symmetric `TrustEdge { role: EdgeRole.ManagerOfMe }` в B's `pairing-edges` bucket.

12. **UI обоих устройств**: показывает "connected" из TrustEdge + MLS group membership.

**Total round-trips**:
- 1 physical (QR screen → camera).
- ~6 server round-trips (register identity, publish KP, complete pairing, claim KP, send Welcome, send Commit).
- <10 sec в нормальных условиях.

**Ephemeral secrets lifecycle**:
- `eph_A_priv`, `eph_B_priv`: discarded after handshake complete.
- `shared_secret`: **не хранится** — используется только для authentication handshake, потом отброшен. TrustEdge хранит long-term `identityPublicKey` вместо (для future verify operations).
- MLS group state (`root_secret`, tree, ratchets): persists в SQLCipher под TEE-derived encryption key.

**Security properties**:
- **MitM невозможен** если QR передан через secure physical channel (screen→camera). Attacker не может подменить B's ephemeral pubkey не имея физического доступа к экрану A.
- **Replay защищён**: `nonce + claimToken + expiresAt` в blob предотвращает re-play старых pairing sessions.
- **Sybil защищён**: physical scan requirement — attacker не может remote получить QR через сеть.

**Endpoint contracts** (все per [TASK-105](task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) baseline: versioned URL, `schemaVersion` in body, JWT + rate limit + `zod` validation + structured logs + failure modes):

| Endpoint | Purpose | Rate limit tier |
|---|---|---|
| `POST /v1/identity/register` | Register `identity_id` (once per identity) | Durable Object (critical) |
| `POST /v1/keypackage/publish` | Publish KP batch | Edge (normal) |
| `POST /v1/keypackage/claim` | Claim one KP for target identity | Edge (normal) |
| `POST /v1/pairing/complete` | Deliver responseBlob to A's inbox | Edge (normal) |
| `GET /v1/pairing/inbox` | Poll A's pairing inbox | Edge (normal) |
| `POST /v1/group/welcome` | Deliver Welcome to target identity's group inbox | Edge (normal) |
| `POST /v1/group/commit` | Fanout Commit to all group members' inboxes | Edge (normal) |
| `GET /v1/group/inbox` | Poll group inbox | Edge (normal) |

**Server-side KV bindings** (Cloudflare Worker MVP):
- `KEYPACKAGE_POOL` — indexed by `identity_id`, list of KeyPackages.
- `PAIRING_SESSIONS` — indexed by `claimToken`, tracks active pairing sessions (TTL = `expiresAt`).
- `PAIRING_INBOX` — indexed by `device_id`, single-item queue for responseBlob delivery.
- `GROUP_INBOX` — indexed by `identity_id`, queue of Welcome/Commit/AppMessage payloads.
- `PROFILE_STORE` — indexed by `identity_id`, encrypted profile blob (for [TASK-102](task-102%20-%20Decision-Revoke-policy.md) reconciliation).

## Зачем

Без pairing нет канала между двумя устройствами одного семейного / медицинского / сервисного контура. С pairing — любое устройство в любом профиле может:
- Принимать удалённое управление настройками (контакты, темы, плитки) от спаренного admin'а.
- Управлять чужим устройством (своим вторым, родственника, пациента).
- Получать push-уведомления о событиях на спаренных устройствах (SOS, изменение конфига, через TASK-5 push routing + TASK-66 bucket routing).

Это **общий примитив**, не «admin app». В будущем поверх него поедет: remote config edit, audit log (TASK-32), photo sharing (TASK-11), calls (TASK-27).

## Что входит технически (для AI-агента)

- **`core/pairing/`** — новый модуль:
  - **`PairingChannel` port** — абстракция канала передачи pairing payload. QR — первый адаптер (`QrPairingChannel`). Future: `LinkInvitePairingChannel` (TASK-31), `NfcPairingChannel`, etc.
  - **`PairingHandshakeBlob` wire format** `schemaVersion=1` — содержимое QR: `{ schemaVersion, ephemeralPubKey, nonce, claimToken, sourceDeviceId, expiresAt }`.
  - **Curve25519 X25519 ECDH** handshake — реализация через `core/crypto` (TASK-2 / TASK-51 libsodium ristretto255).
  - **`TrustEdge` domain type** — pure data: `{ edgeId, peerIdentity, peerPubKey, role: EdgeRole, createdAt, revokedAt? }`. `EdgeRole` sealed: `ManagedByMe | ManagerOfMe`.
  - **`PairingService`** — оркестратор handshake'а: показать QR → дождаться сканирования → выполнить handshake → положить TrustEdge в `pairing-edges` bucket.
  - **Camera permission handling** — `system.permission.CAMERA` pool entry для QR scanner (вероятно добавляется в pool в рамках этой задачи).
- **`pairing-edges` bucket** (через TASK-66):
  - `BucketTypeSpec(id="bucket.pairing.edges", conflictPolicy=AddOnlyRevokeOnly, recipientPolicy=SelfOnly, serializer=List<TrustEdge>.serializer())`.
  - Регистрация в `EncryptedBucketRegistry` на app init.
  - Автоматический recovery через TASK-66 mechanism.
- **Pool entries** (через TASK-65 composition foundation):
  - `tile.pool.json`: `tile.pairing.list` (показывает edges), `tile.pairing.add` (запускает pairing flow).
  - `wizard-step.pool.json` (или существующий `system-settings.pool.json`): `wizard.step.pair-device`.
  - `system-settings.pool.json`: `android.permission.CAMERA` (если ещё нет).
- **Generic CheckSpec / ApplySpec variants** — добавить в sealed hierarchy:
  - `CheckSpec.PairingState(role: EdgeRole? = null)` — есть ли хотя бы один edge (опционально с фильтром по роли).
  - `ApplySpec.OpenPairingScreen(mode: PairingMode)` — открывает pairing UI (показ QR / сканирование).
  - **Renamed from Amendment 1.10 `PairAdminLink` / `PairAdminIntent`** — generic, без «admin» в названии.
- **simple-launcher manifest update** — добавить generic `pair-device` step (выполняет Amendment 1.10 deferral). Step попадает в визард как Optional/canSkip — пользователь может пропустить.
- **`PairingChannel` fake adapter** для тестов — `InMemoryPairingChannel` симулирует передачу payload между двумя `PairingService` инстансами без UI.
- **Fitness tests:**
  - Round-trip `PairingHandshakeBlob` JSON.
  - End-to-end fake pairing: two `PairingService` → handshake → оба видят TrustEdge с симметричным state.
  - `TrustEdge` сохраняется в bucket, recovery восстанавливает.

## Состояние

**Planned.** Зависит от TASK-65 (composition foundation для pool entries) + TASK-66 (bucket registry для pairing-edges) + TASK-51 (libsodium ristretto255 — Done).

---

## Готовый промт для `/speckit.specify`

```
Реализуй F-?? (TBD): Pairing Feature + pairing-edges bucket.

ЧТО СТРОИМ:
Generic pairing primitive: QR-обмен ключами через Curve25519, две устройства устанавливают
двустороннюю trust edge. Edge живёт в pairing-edges bucket (через TASK-66 registry),
переживает profile switch и recovery. Доступно в любом профиле через pool entries.

ЗАЧЕМ:
Pairing — фундамент для remote management, SOS, audit log, photo sharing, calls.
Не admin-specific, а общий примитив для любых two-device связей в нашем контуре.

SCOPE ВКЛЮЧАЕТ:
- core/pairing/ модуль: PairingChannel port, PairingHandshakeBlob wire format (schemaVersion=1).
- Curve25519 X25519 ECDH handshake (через TASK-2 / TASK-51 libsodium).
- TrustEdge domain type + EdgeRole sealed (ManagedByMe | ManagerOfMe).
- PairingService оркестратор.
- pairing-edges bucket через TASK-66 (AddOnlyRevokeOnly, SelfOnly).
- Pool entries: tile.pairing.list, tile.pairing.add, wizard.step.pair-device, system.permission.CAMERA (если нужно).
- Generic CheckSpec.PairingState + ApplySpec.OpenPairingScreen variants (renamed from Amendment 1.10 PairAdminLink/Intent).
- simple-launcher manifest получает pair-device step.
- InMemoryPairingChannel fake adapter для тестов.

SCOPE НЕ ВКЛЮЧАЕТ:
- LinkInvitePairingChannel (remote invite через ссылку) — TASK-31 Phase 4.
- Pairing UI styling под workspace профиль — это TASK-68 (только данные).
- Multi-admin/group pairing — отложено.
- Remote config edit через pairing — TASK-13 Phase 2.

DEPENDENCIES:
- TASK-65 (Profile Composition Foundation v2) — для pool entries.
- TASK-66 (Generic Encrypted Bucket Registry) — для pairing-edges bucket.
- TASK-51 (libsodium ristretto255) — Done.

ACCEPTANCE CRITERIA:
- Два устройства A и B (оба simple-launcher для regression): A показал QR → B отсканировал → handshake <10s → оба видят TrustEdge.
- У A в списке 'кем управляю' — B. У B в списке 'кто управляет мной' — A.
- В simple-launcher оба списка доступны через 7-tap settings.
- Revoke edge → обе стороны видят revoked state после синхронизации.
- Recovery (TASK-6 flow на новом устройстве) → все TrustEdges автоматически восстановлены через TASK-66.
- Pairing handshake устойчив к обрыву сети (timeout + retry).
- Никакого admin-specific кода (lint check на TASK-65 правила).
- PairingHandshakeBlob JSON round-trip + backward-compat тест.

LOCAL TEST PATH:
- Two emulators (skill android-emulator): A pixel_5_api_34, B pixel_6_api_34.
- QR через экранный код в одном эмуляторе + сканер в другом (физическая верификация на двух устройствах в верификации).
- Unit tests с InMemoryPairingChannel — fake handshake.

CONSTITUTION GATES:
- Rule 1 (domain isolation): PairingChannel port в core/pairing/, QR / Curve25519 adapters не вытекают.
- Rule 2 (ACL): libsodium API не вытекает в domain.
- Rule 5 (wire format): PairingHandshakeBlob schemaVersion=1, roundtrip + backward-compat.
- Rule 6 (mock-first): InMemoryPairingChannel fake.
- Article VII §13: никакого admin-specific кода (lint).
- Article VII §16: generic CheckSpec/ApplySpec variants, не Custom step.

EFFORT: Large (~3 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Два устройства A и B: A показал QR → B отсканировал → handshake <10s → оба видят TrustEdge
- [ ] #2 У A в списке 'кем управляю' появилось B, у B в списке 'кто управляет мной' появилось A (симметрия)
- [ ] #3 В simple-launcher через 7-tap settings доступны оба списка (без отдельного UI на главном)
- [ ] #4 Revoke edge → обе стороны видят revoked state после синхронизации
- [ ] #5 Recovery на новом устройстве (TASK-6 flow) → все TrustEdges автоматически восстановлены, не нужно re-pairing
- [ ] #6 Pairing handshake устойчив к обрыву сети (timeout + retry)
- [ ] #7 Никакого admin-specific кода в репе — lint check (правила из TASK-65) проходит
<!-- AC:END -->
