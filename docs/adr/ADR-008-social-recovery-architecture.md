# ADR-008: Social Recovery Architecture для приватных ключей без server-side key escrow

> **Numbering note 2026-06-18**: This ADR previously reserved spec 017 for multi-device-recovery. Owner reassigned spec 017 to F-4 AuthProvider + Google Sign-In. Multi-device-recovery spec will receive its number when `/speckit.specify` is run for it.

**Status**: Draft (2026-05-23); ренумерация 2026-06-17 → finalize в **future multi-device-recovery spec (TBD number, will be assigned at /speckit.specify time; spec 017 reassigned to F-4 AuthProvider 2026-06-18)** (после того как 016 = F-CRYPTO `core/crypto/` KMP module foundation).
**Date**: 2026-05-23 (initial draft)
**Decided in**: spec 011 mentor session 2026-05-23 (Part E «Recovery в деталях»).
**Linked specs**: [spec 011](../../specs/011-contacts-and-e2e-encrypted-media/spec.md) (crypto foundation — supplies envelope + primitives), [spec TBD](../dev/project-backlog.md#todo-auth-001-authprovider-port--firebase-emailpassword-adapter-) (auth-provider abstraction — supplies named identity), [future multi-device-recovery spec (TBD)](../dev/project-backlog.md#todo-recovery-001-social-recovery-password--peer_nonce--hkdf--aead-backup-) (recovery implementation — applies this ADR).
**Linked backlog tasks**: `TODO-RECOVERY-001`, `TODO-AUTH-001`.

---

## Context

### Проблема

Спек 011 устанавливает per-device asymmetric keys в Android Keystore с non-extractable защитой. Это даёт сильное **E2E privacy guarantee** (никто кроме устройства не имеет приватных ключей), но **ломает recovery**:

- При потере / повреждении / factory-reset устройства все приватные ключи **безвозвратно потеряны**
- Все blob'ы, зашифрованные для этого устройства, **невозможно расшифровать** новым экземпляром приложения
- Бабушка теряет всю медкарту, фото, документы — это **product-unacceptable** для нашей целевой аудитории (пожилые с физическим риском утери / поломки телефона выше среднего)

### Constraints

В mentor-сессии 2026-05-23 пользователь явно зафиксировал constraints:

1. **No named auth изначально** → решение зависит от перехода на named auth (email через `AuthProvider` port — см. TODO-AUTH-001). Этот ADR **предполагает** named identity доступна.
2. **No server-side key escrow** — сервер **никогда** не должен иметь возможность расшифровать данные бабушки в одиночку. Это сохраняет E2E privacy guarantee.
3. **No vendor lock-in для auth** — recovery flow должен работать с любым `AuthProvider` (Firebase Auth, future SMS, future Telegram, future own backend).
4. **Бабушка-friendly UX** — passphrase max 6 цифр (PIN); peer involvement через single-tap confirmation (без сложных gestures).
5. **Старые blob'ы должны восстанавливаться** — не «начать с чистого листа»; восстановление = доступ к **existing** encrypted data.
6. **Приватные документы остаются приватными** — recovery не должен compromise privacy через shared access (peer **не** видит plaintext данных).

### Альтернативы рассмотренные и отвергнутые

| Альтернатива | Почему отвергнута |
|---|---|
| **Pure E2E без recovery** (Signal до 2021) | Product-unacceptable для нашей аудитории — слишком высокий риск permanent data loss |
| **Server-side key escrow** (мы храним ключи бабушки и отдаём по email-recovery) | Compromise E2E privacy — мы получаем технический доступ к данным; нарушение constraint 2 |
| **Passphrase-only encryption backup** (Signal-style без 2FA peer) | Бабушка с PIN 4 цифры → brute-force атака на server-leaked backup тривиальна |
| **iCloud / Google Drive backup** | Vendor lock-in; нарушение constraint 3; передача encrypted blob'ов внешнему облаку = другая модель угроз |
| **Hardware-backed key escrow (Titan Security Key)** | UX-катастрофа для пожилых; cost overhead |
| **Peer-to-peer direct transfer без сервера** (Magic Wormhole pattern) | Требует одновременного online обоих устройств; не работает asynchronously; ломается при unstable mobile network |

---

## Decision

**Принято: гибридная social recovery архитектура** через комбинацию `passphrase` + `peer_nonce` с HKDF derivation и AEAD-encrypted backup на сервере.

### Криптографическая схема

#### Setup phase (выполняется при первом setup устройства, после auth + первого pairing)

```
INPUT:
  - бабушкин passphrase (PIN 4-6 цифр от пользователя)
  - бабушкин priv_keys_bundle (X25519 priv + Ed25519 priv + linkIds)
  - trusted_peer.publicKey (из existing pairing, через DeviceIdentityRepository.fetchPeer)

ALGORITHM:
  1. peer_nonce = CSPRNG(32 bytes)
     (libsodium randombytes_buf)

  2. recovery_key = HKDF-SHA256(
       ikm = peer_nonce,
       salt = HMAC-SHA256(key=passphrase, data="recovery-salt-v1"),
       info = "launcher-recovery-aead-v1",
       length = 32 bytes,
     )

  3. encrypted_backup = AEAD-XChaCha20-Poly1305(
       key = recovery_key,
       nonce = CSPRNG(24 bytes),
       aad = canonical_cbor(externalId, schemaVersion=1, createdAt),
       plaintext = canonical_cbor(priv_keys_bundle),
     )

  4. encrypted_peer_nonce = AsymmetricCrypto.sealCEK(
       cek = peer_nonce,
       recipient_pub = trusted_peer.publicKey,
     )
     (Использует existing 011 primitive — crypto_box_seal)

OUTPUTS storage:
  - encrypted_backup → server: /backups/{externalId}/v1
  - encrypted_peer_nonce → trusted_peer storage (через 011 envelope в peer's pair storage)
  - passphrase → НИГДЕ не сохраняется, только в голове бабушки

CLEANUP:
  - sodium_memzero(recovery_key)
  - sodium_memzero(peer_nonce)
```

#### Recovery phase (выполняется на новом устройстве после потери старого)

```
INPUT:
  - email + password (для auth через AuthProvider)
  - бабушкин passphrase (PIN — из памяти бабушки)
  - online trusted_peer для 2FA confirmation

ALGORITHM:
  1. AuthProvider.signIn(EmailPassword) → external_id
     (Стандартный auth flow; сервер validates credentials)

  2. new_device.X25519_keypair = AsymmetricCrypto.generateX25519Pair()
     new_device.publishOwn(temporary_link)
     (Свежий keypair для receiving peer_nonce)

  3. server.request2FA(external_id, target=trusted_peer)
     → push to trusted_peer: "User X requests recovery, approve?"

  4. trusted_peer taps confirm:
     - trusted_peer fetches new_device.publicKey from temporary_link
     - unsealed_nonce = AsymmetricCrypto.unsealCEK(encrypted_peer_nonce, trusted_peer.privKey)
     - reSealed_nonce = AsymmetricCrypto.sealCEK(unsealed_nonce, new_device.publicKey)
     - server relays reSealed_nonce → new_device
     - sodium_memzero(unsealed_nonce)

  5. new_device.unseal:
     peer_nonce = AsymmetricCrypto.unsealCEK(reSealed_nonce, new_device.privKey)

  6. new_device asks user: "Enter your PIN"
     passphrase = user_input
     recovery_key = HKDF-SHA256(... same params as setup ...)

  7. new_device.download:
     encrypted_backup = server.get(/backups/{external_id}/v1)

  8. priv_keys_bundle = AEAD-decrypt(
       key = recovery_key,
       nonce = encrypted_backup.nonce,
       aad = encrypted_backup.aad,
       ciphertext = encrypted_backup.ciphertext,
     )
     IF decrypt fails → MacFailed → wrong PIN or wrong nonce, abort

  9. new_device stores priv_keys_bundle в новом Keystore namespace:
     - X25519 priv → Keystore with AES-wrap (как в 011 T054)
     - Ed25519 priv → Keystore native API 31+ или AES-wrap (как в 011 T055)
     - linkIds → SQLDelight DB

  10. CLEANUP:
      sodium_memzero(recovery_key)
      sodium_memzero(peer_nonce)
      sodium_memzero(priv_keys_bundle)

OUTCOME:
  - Все старые blob'ы, зашифрованные для бабушки, теперь расшифровываются
  - Existing pairings продолжают работать (priv keys restored)
  - Identity бабушки на сервере сохранена (тот же external_id)
```

### Свойства защиты

| Угроза | Защита |
|---|---|
| Компрометация сервера (атакующий читает `encrypted_backup`) | Не может расшифровать — нет passphrase (только у бабушки) + нет peer_nonce (только у trusted peer) |
| Утечка email + password бабушки | Не может расшифровать — нужны passphrase **и** peer-confirmation (2FA blocks recovery) |
| Компрометация trusted_peer's устройства | Не может расшифровать — нет passphrase бабушки; peer_nonce alone — useless |
| Slabый passphrase (PIN 4 цифры) | Атакующий не может перебрать без peer_nonce; peer_nonce имеет 256 бит энтропии; HKDF computational binding |
| Social engineering — атакующий звонит peer'у «подтверди recovery» | Peer видит push с email бабушки + timestamp — может verify out-of-band (телефонный звонок бабушке) |
| Replay-attack на 2FA push | Push требует **свежий** new_device.publicKey, сгенерированный ad-hoc; replay невозможен |
| Forgotten passphrase | **Data lost** — accepted as risk; consequence of true privacy |
| All trusted peers offline / lost | **Data lost** — accepted as risk; mitigation = multi-peer (MVP — 1-of-N, future — N-of-M Shamir) |

### MVP scope (future multi-device-recovery spec (TBD))

- **1-of-N peer authorization** — любой trusted peer достаточен для confirmation. Простая, working logic.
- **Single passphrase per backup** — нет multi-passphrase flow.
- **Single encrypted backup version** — full rewrite при rotation (no incremental).
- **Periodic backup refresh** при изменении identity (например, добавление нового pairing): re-create `encrypted_backup` с обновлённым `priv_keys_bundle`, replace на сервере.

### Future enhancements (за пределами MVP)

- **N-of-M Shamir Secret Sharing** для peer_nonce — например, 2-of-3 (любые 2 из 3 trusted peers достаточны). Robust to single peer loss, но complex flow.
- **Server-side rate limiting** на recovery attempts — atomic counter в Cloudflare KV / Firestore, block после N неудачных попыток в hour.
- **Audit log** для пользователя — «recovery attempted at TIME from PLATFORM» visible бабушке через peer push notifications.
- **Pre-emptive backup rotation** при подозрении на compromise (peer теряет телефон → new peer_nonce, new backup).

---

## Consequences

### Positive

- **E2E privacy maintained** — сервер никогда не имеет access к plaintext данным бабушки.
- **Старые данные восстанавливаются** — recovery возвращает priv keys, не «начинает с нуля».
- **Бабушка-friendly UX** — single PIN + один тап peer'а.
- **No vendor lock-in** — HKDF + AEAD primitives portable across providers; auth abstraction (`AuthProvider` port) даёт миграцию между Firebase / SMS / Telegram / own backend.
- **Reusable patterns** — `peer_nonce` через 011 envelope = reuse existing infrastructure; не нужен новый wire-format слой.

### Negative

- **Forgotten passphrase = data lost** — accepted, consistent with true E2E.
- **All peers offline = recovery impossible** — accepted in MVP (1-of-N); future N-of-M reduces probability.
- **Дополнительная responsibility у peer'а** — он становится частью security periphery бабушки; ему нужно объяснить, что подтверждение recovery = серьёзное действие.
- **Server requirement** — recovery flow требует server-mediated 2FA push (FCM), что блокирует pure-P2P architectures (acceptable trade-off — мы и так server-mediated через Firebase).

### Neutral / accepted risks

- **Social engineering on peer** — атакующий звонит/пишет peer'у «нажми кнопку recovery». Mitigation: UI peer'а четко показывает контекст («бабушка восстанавливается с устройства <device_label> по email <email>»); тренинг seniors / family education о scams.
- **Compromised email account** + compromised peer **одновременно** — recovery возможен атакующему. Это **3-фактор атака** (email + PIN + peer device) — высокий threshold, but not impossible. Accepted.
- **Quantum threat to X25519** — `peer_nonce` zashифрован через `crypto_box_seal` (X25519). При появлении quantum computer'a с практической Shor's algorithm — нужен переход на post-quantum scheme. См. spec 011 §Out-of-scope FR-096 (libsodium upstream).

---

## Exit ramp

Если в будущем потребуется отказаться от social recovery (например, регуляторные требования отменяют ban на server-side escrow, или приходит regulatory mandate на law-enforcement access):

1. **Не нужно перешифровывать blob'ы** — schema самой recovery архитектуры isolated от данных.
2. Опции миграции (forward-compatible additions, не replacements):
   - **Hybrid**: добавить опциональный server-side escrow в дополнение к social recovery — пользователь выбирает; default остаётся social.
   - **Per-document opt-out**: пользователь может пометить отдельные документы как «excluded from social recovery» — тогда они **не** попадают в `priv_keys_bundle`'s scope and are not recoverable (current behavior).
   - **Multi-method recovery**: passphrase + peer + (optional) hardware token / law-enforcement key with audit trail.
3. **Existing backups** остаются valid — recovery flow продолжает работать. Новые backup'ы могут использовать новую схему.

Реализация любой из этих опций — **отдельный спек после future multi-device-recovery spec (TBD)**.

---

## Implementation traceability

| Concept | Domain type / port | File | Phase |
|---|---|---|---|
| HKDF derivation | `KeyDerivation` port | `core/commonMain/api/recovery/KeyDerivation.kt` | future multi-device-recovery spec (TBD) Phase 1 |
| HKDF-SHA256 impl | `LibsodiumKeyDerivation` | `core/androidMain/adapters/recovery/LibsodiumKeyDerivation.kt` (uses `crypto_kdf_hkdf_sha256`) | future multi-device-recovery spec (TBD) Phase 3 |
| `encrypted_backup` wire format | `RecoveryBackup` data class + CBOR contract | `core/commonMain/api/recovery/RecoveryBackup.kt` + `specs/TBD/contracts/recovery-backup.md` | future multi-device-recovery spec (TBD) Phase 1 |
| `peer_nonce` storage | extends 011 envelope (no new wire format) | reuses `EncryptedEnvelope` from 011 | future multi-device-recovery spec (TBD) Phase 1 |
| 2FA push flow | `RecoveryRequestService` port | `core/commonMain/api/recovery/RecoveryRequestService.kt` + Cloudflare Worker endpoint | future multi-device-recovery spec (TBD) Phase 4 |
| New device key generation | reuses 011 `AsymmetricCrypto.generateX25519Pair` | — | — |
| Peer confirmation UI | `RecoveryConfirmScreen` Composable | `app/src/main/.../recovery/RecoveryConfirmScreen.kt` | future multi-device-recovery spec (TBD) Phase 5 |
| Server-side rate limiting | extends `firestore.rules` + Cloudflare Worker | — | future multi-device-recovery spec (TBD) Phase 4 |

---

## Open questions (finalized в future multi-device-recovery spec (TBD))

1. **Где именно хранить `encrypted_backup` на сервере?** Кандидаты: Firestore document `/backups/{externalId}` (size limit 1 MiB — достаточно для ключей), или Firebase Storage `/backups/{externalId}/v1` (для будущей extension под larger payload). Решается в future multi-device-recovery spec (TBD) plan-phase.
2. **TTL для `peer_nonce` стабилен или ротируется?** Возможные политики: (a) static — `peer_nonce` живёт пока pairing активен; (b) periodic rotation — каждые 90 дней генерируется новый `peer_nonce`, encrypted_backup пересоздаётся. Решение зависит от forward secrecy ambitions.
3. **Что делать при failed unauthorized recovery attempt?** Rate-limit + peer notification («Кто-то пытался восстановить ваш аккаунт») + audit log. Detail в future multi-device-recovery spec (TBD).
4. **Multi-peer Shamir SSS в MVP или future?** Текущее решение — 1-of-N в MVP, Shamir N-of-M через 1-2 release позже. Финализуется в future multi-device-recovery spec (TBD) §Open Items.

---

<!-- novice summary -->

## TL;DR (простым языком)

**Проблема:** бабушка теряет телефон → все её зашифрованные фото и медкарты пропадают навсегда. Это **намеренная** черта настоящего e2e шифрования (никто кроме её телефона не может расшифровать), но для пожилых это product-unacceptable.

**Решение:** social recovery через комбинацию **пароля** + **доверенного peer'а** (внука).

**Как работает setup (один раз при первом запуске):**
1. Бабушка задаёт PIN 4-6 цифр
2. Приложение генерирует случайные 32 байта — peer_nonce
3. Из PIN и peer_nonce смешивается специальная функция HKDF → получается recovery_key
4. Этим recovery_key шифруются ВСЕ бабушкины приватные ключи
5. Зашифрованный бэкап летит на сервер; peer_nonce — внуку (зашифрованный для него); PIN — только в голове бабушки

**Как работает восстановление:**
1. Бабушка купила новый телефон, ставит приложение
2. Логинится по email+пароль (это named auth, добавляется в spec TBD)
3. Сервер: «подтвердите через trusted peer» — push внуку
4. Внук тапает «да это бабушка» — отправляет peer_nonce на новое устройство
5. Бабушка вводит PIN
6. Новое устройство: PIN + peer_nonce → HKDF → recovery_key → расшифровывает backup → все старые ключи возвращены
7. Старые медкарты, фото, документы — расшифровываются как раньше

**Почему это безопасно:**
- Сервер видит только зашифрованный backup. Без PIN и peer_nonce — расшифровать невозможно
- Внук видит только зашифрованный peer_nonce. Без PIN — расшифровать невозможно
- PIN бабушка помнит сама. Без peer_nonce — слабый PIN не помогает (brute-force невозможен)
- Все три фактора нужны одновременно: PIN (что знает) + email/password (что знает) + peer (что у неё есть через доверенное лицо)

**Что НЕ работает:**
- Бабушка забыла PIN → данные потеряны (accepted cost истинной приватности)
- Все доверенные peer'ы потеряли свои телефоны одновременно → recovery невозможен (mitigation: иметь несколько peer'ов; в MVP — 1-of-N, в будущем — N-of-M Shamir Secret Sharing)
- Атакующий получил email+пароль И уговорил peer'а нажать «подтвердить» → есть PIN надо ещё угадать; brute-force запрещён rate-limit

**Когда реализуется:** future multi-device-recovery spec (TBD) (multi-device-recovery после ренумерации 2026-05-22). Зависит от TODO-AUTH-001 (email auth). До тех пор — кейс accepted как known risk с инструкциями пользователям не терять телефон.

**Что в spec 011 от этого:** **ничего не меняется** в коде. 011 envelope + primitives используется будущим спеком 015 (multi-device-recovery) как-есть. Этот ADR закрывает архитектурный one-way door OWD-4 — exit ramp зафиксирован, future direction понятна.
