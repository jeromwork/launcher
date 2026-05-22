# ADR-007: TrustEdgeBootstrap Subtypes for Per-Device Asymmetric Keys

**Status**: Draft (Phase 0); Accepted on Phase 1 finalization (T010).
**Date**: 2026-05-22 (initial draft)
**Decided in**: spec 011 mentor session 2026-05-21 + scope-split 2026-05-22.
**Linked specs**: [spec 007](../../specs/007-pairing-and-firebase-channel/spec.md) (pairing flow base), [spec 011](../../specs/011-contacts-and-e2e-encrypted-media/spec.md) (crypto foundation).
**Linked backlog task**: `TODO-DOC-001` ([project-backlog.md:378](../dev/project-backlog.md#L378)).

---

## Context

Спек 007 ввёл концепт `TrustEdgeBootstrap` — sealed interface, описывающий initial state доверительной связи между двумя устройствами при pairing. На момент 007 единственный subtype — `LinkBootstrap(presetId: String)` — описывает выбор раскладочного preset'a у Managed устройства.

Спек 011 (e2e-crypto-foundation) вводит **per-device asymmetric keys**: каждое устройство генерирует свою пару X25519 (encryption) и Ed25519 (signing) при первом запуске приложения. После pairing'a (`consent.allow` в спеке 007) оба устройства должны публиковать свои public ключи в Firestore `/links/{linkId}/devices/{deviceId}` с Ed25519 подписью над payload.

Это поднимает вопрос: **как лучше представить публикацию публичных ключей в архитектуре `TrustEdgeBootstrap`?** Варианты:

1. Расширить существующий `LinkBootstrap` дополнительными полями `pubX25519`, `pubEd25519`, `signature`.
2. Создать второй subtype `DeviceKeyBootstrap(deviceId, pubX25519, pubEd25519, signature, signedTimestamp)`, который пишется параллельно с `LinkBootstrap`.
3. Не использовать `TrustEdgeBootstrap` вообще — публиковать через отдельный repository (`DeviceIdentityRepository`).

---

## Decision

**Принято: вариант 2** — второй subtype `TrustEdgeBootstrap.DeviceKeyBootstrap` плюс отдельный `DeviceIdentityRepository` port для read/write операций.

```kotlin
sealed interface TrustEdgeBootstrap {
    // existing from спек 007
    data class LinkBootstrap(val presetId: String) : TrustEdgeBootstrap

    // NEW in спек 011
    data class DeviceKeyBootstrap(
        val deviceId: DeviceId,
        val publicKey: PublicKey,                 // X25519, 32 bytes
        val signingPublicKey: SigningPublicKey,   // Ed25519, 32 bytes
        val signedTimestamp: Long,                // epoch millis
        val signature: ByteArray,                 // Ed25519 over canonical CBOR of above 4 fields
    ) : TrustEdgeBootstrap
}
```

Pairing flow (спек 007 `PairingCoordinator`) **additively** расширяется: после `consent.allow` каждое устройство:
1. Генерирует (или загружает существующие) X25519 и Ed25519 keypairs через `SecureKeystore`.
2. Собирает `DeviceKeyBootstrap` с canonical CBOR payload + Ed25519 signature.
3. Публикует через `DeviceIdentityRepository.publishOwn(linkId, identity)` → Firestore `/links/{linkId}/devices/{ownDeviceId}`.

Pairing-token wire-format (`PairingTokenWireFormat` из спека 007) **не меняется** — `schemaVersion` остаётся 1. Обмен публичными ключами идёт **через Firestore document после `consent.allow`**, не через QR-token. Поле `pairingKey` в pairing-token, зарезервированное в спеке 007, **остаётся неиспользованным**.

### Почему именно второй subtype, а не расширение существующего

- **Архитектурная чистота** — `LinkBootstrap` описывает выбор preset'a; `DeviceKeyBootstrap` описывает crypto identity. Это разные responsibility, mixing их в одном data class нарушает Single Responsibility.
- **Sealed exhaustiveness** — Kotlin compiler enforced'ает обработку всех subtypes; добавление нового subtype = explicit consideration в каждом use site.
- **Forward-compat** — будущие спеки (013-016 модели доверия, TBD-Jitsi, TBD-vendor, TBD-hardware) могут добавлять собственные subtypes (`GroupKeyBootstrap`, `JitsiRoomKeyBootstrap`, и т.д.) без изменения существующих.

### Почему отдельный `DeviceIdentityRepository`, а не публикация через pairing flow

Pairing flow (спек 007) — **one-shot** operation на момент scan QR + consent. Pub-keys могут публиковаться не только при первом pairing'е, но и при:
- **Key rotation** (будущий спек 016) — устройство ротирует ключи периодически, перепубликовывает identity.
- **Re-pairing** после Keystore loss (Xiaomi MIUI quirk) — устройство генерирует новые ключи, перепубликовывает.
- **Multi-device addition** (будущий спек 015) — новое устройство владельца публикует свои ключи в существующий link.

`DeviceIdentityRepository` обеспечивает unified API для всех этих случаев. `DeviceKeyBootstrap` — это в первую очередь **data shape**, не workflow trigger.

---

## Consequences

### Positive

- Crypto identity (`DeviceKeyBootstrap`) изолирована от bootstrap workflow (`LinkBootstrap`).
- Sealed hierarchy расширяется новыми subtypes без breaking existing использования.
- Pairing-token wire-format спека 007 не trogается — schemaVersion остаётся 1 (additive evolution per CLAUDE.md rule 5).
- `DeviceIdentityRepository` готов к future use cases (rotation, multi-device, re-pairing).

### Negative

- Дополнительная Firestore запись на каждый pairing — но это **один document, ~200 bytes**, негативно не сказывается на quota.
- Two-step pairing: scan QR → consent → **затем** оба устройства публикуют ключи. Это **не атомарно** на client side. Mitigation: publishOwn idempotent, retry via WorkManager.

### Neutral / accepted risks

- **MITM при первом fetch peer Pub'a** через Firestore — accepted as risk до спека ~016 (safety numbers UX). TLS Firestore + Security Rules (ownership check + signedTimestamp 7-day freshness gate) — minimal protection layer. Documented в spec 011 §Architectural one-way doors. Backlog task: `TODO-SEC-011`.
- **Schema rev. 2** в `/links/{linkId}/devices/{deviceId}` document (added 2026-05-22) — breaking change к rev. 1, но **pre-production phase** (нет реальных пользователей до спека ~35), миграция не нужна — старые документы (если есть в development) можно стереть.

---

## Exit ramp

Если в будущем (например, спек ~020) решим перейти на другую identity модель (например, federated identity провайдер, hardware-attestation chains), exit ramp:

1. Добавить новый subtype `TrustEdgeBootstrap.FederatedIdentityBootstrap` (или каким он будет).
2. Реализовать новый `DeviceIdentityRepository` adapter под новую модель.
3. Старые devices с `DeviceKeyBootstrap` продолжают работать через старый adapter.
4. Постепенная миграция per device при rotation.

Никакой single-step breaking migration не требуется. Это и есть смысл sealed hierarchy.

---

## Implementation traceability

| Concept | Domain type / port | File | Phase |
|---|---|---|---|
| Sealed hierarchy | `TrustEdgeBootstrap` | `core/.../trust/TrustEdgeBootstrap.kt` (existing спек 007) | Phase 1 (T015) — extend with new subtype |
| Data shape | `DeviceKeyBootstrap` | same file | Phase 1 (T015) |
| Generation | `generateX25519Pair`, `generateEd25519Pair` | `AsymmetricCrypto`, `DigitalSignature` ports | Phase 1 (T022, T023) |
| Storage | Android Keystore wrapping | `SecureKeystore` port + `AndroidKeystoreSecureKeystore` | Phase 1 (T025), Phase 3 (T054-T055) |
| Publication | `publishOwn`, `fetchPeer` | `DeviceIdentityRepository` port + `FirestoreDeviceIdentityRepository` adapter | Phase 1 (T028), Phase 4 (T060) |
| Pairing extension | call `publishOwn` after `consent.allow` | `PairingCoordinator` (existing спек 007) | Phase 4 (T061) |
| Security Rules | freshness + ownership gates | `firestore.rules` | Phase 4 (T062) |

---

## Open questions (finalized at T010)

1. ~~**Pub-key publication через QR или Firestore?**~~ — **Resolved**: через Firestore, после `consent.allow`. Pairing-token wire-format не trogается.
2. ~~**Один subtype с дополнительными полями или второй?**~~ — **Resolved**: второй subtype (`DeviceKeyBootstrap`).
3. ~~**Per-link или global Keystore alias?**~~ — **Resolved**: global alias (`launcher_device_priv_x25519_v1`, `launcher_device_priv_ed25519_v1`). При revoke link priv-ключи **не удаляются** (могут быть нужны для других link'ов в будущем). При factory reset Keystore стирается ОС — это acceptable cost (re-pair required).
