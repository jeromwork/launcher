# 04 — PairingChannel Abstraction

**Status**: ACCEPTED 2026-06-15
**Clarifies**: [`specs/007-pairing-and-firebase-channel/spec.md`](../../../../specs/007-pairing-and-firebase-channel/spec.md) primary pairing flow

## Принцип

> **QR-pairing (физическое присутствие, камера наводится на экран) — primary path.** Любые remote-invite каналы (через ссылку, NFC, Bluetooth, push) — additive add'ы через `PairingChannel` adapter.

## Что было неправильно сказано

Ранее в `roadmap.md` фигурировала фраза «QR-pairing отвергнут потому что требует физического присутствия, в пользу remote signed invite link». Это **неверно**:

- В [`specs/007-pairing-and-firebase-channel/spec.md`](../../../../specs/007-pairing-and-firebase-channel/spec.md) **User Story 1 (Priority P1)** — QR pairing через камеру + ML Kit. **Это primary path.** Уже реализован в коде.
- Signed invite link появляется в [`use-cases/05-pairing-identity-trust.md` §Remote Invite Flow](../../use-cases/05-pairing-identity-trust.md) только для co-admin / caregiver remote invite (S-7) — **дополнительный** flow, не primary.

Roadmap правится в этом PR.

## Архитектура

### `PairingChannel` port

```kotlin
interface PairingChannel {
    val id: String  // "qr-code", "signed-link", "nfc", ...

    /** Initiator side: produce a pairing token. */
    suspend fun initiate(scope: PairingScope): PairingToken

    /** Receiver side: consume a token, return verified pairing request. */
    suspend fun consume(token: PairingToken): Outcome<PairingRequest, PairingError>
}
```

`PairingToken` — sealed wire-format с `schemaVersion`:

```kotlin
@Serializable
data class PairingToken(
    val schemaVersion: Int,           // bump для breaking changes
    val channelId: String,            // "qr-code", "signed-link"
    val payload: ByteArray,           // signed payload, channel-specific
    val ttl: Instant,
    val initiatorPubKey: ByteArray
)
```

### MVP implementations

| Adapter | Когда | Use case |
|---------|-------|----------|
| **`QrPairingChannel`** | MVP, primary | Два телефона физически рядом, admin наводит камеру на QR на бабушкином экране (как сейчас в спеке 007) |
| **`LinkInvitePairingChannel`** | S-7 (Caregiver) | Caregiver не рядом физически — admin генерирует signed invite link и шлёт через share intent |

`LinkInvitePairingChannel` — **additive**, не блокирует MVP. Появляется в S-7.

### Future (post-MVP)

- `NfcPairingChannel` — touch-to-pair, без камеры.
- `BluetoothPairingChannel` — для accessibility сценариев.
- `MarketplaceConfigPairingChannel` — претendant пресет из marketplace, accept'ишь, становишься paired с автором (для подписки на updates от автора).

Все — additive через тот же port, без переписывания `PairingService`.

## Wire format compatibility

`PairingToken.channelId` — discriminator. `PairingService` диспатчит на нужный adapter по этому полю. `schemaVersion` per channelId — adapter сам отвечает за backward-compat своего payload'а.

## QR-pairing UX (как сейчас в коде)

1. Admin: в Settings → «Связать с другим устройством» → app генерирует ephemeral pairing token, отображает как QR-код на экране.
2. Receiver: в Settings → «Сканировать QR-код» → камера читает QR (ML Kit Barcode), token приходит в `PairingChannel.consume`.
3. Receiver: видит preview «Устройство admin@gmail.com хочет получить доступ для редактирования вашего конфига. Принять?»
4. Receiver tap «Принять» → `ConfigGrant` создаётся в receiver's namespace на сервере (per [02-config-ownership-per-device.md](02-config-ownership-per-device.md)).

## Sign-In requirement

Pairing — это **cloud feature**. Требует Sign-In на **обоих** устройствах:
- Initiator (admin): Sign-In чтобы подписать token (`initiatorPubKey` принадлежит admin'у).
- Receiver (бабушка): Sign-In чтобы было namespace, где появится `ConfigGrant`.

Если на одном из устройств юзер ещё в local mode — pairing wizard просит Sign-In с понятным объяснением: «Чтобы связать устройства, нужно войти в Google-аккаунт. Без этого устройства не смогут найти друг друга».

Это согласуется с [01-deferred-sign-in.md](01-deferred-sign-in.md) — Sign-In появляется в момент cloud action, на конкретном trigger'е.

## Что нужно поправить

- **`roadmap.md` S-2 «Admin App Preset + Remote Pairing»**: убрать упоминания «signed invite link» как primary. Primary — QR (см. спека 007). Remote link — S-7 only.
- **`docs/product/use-cases/05-pairing-identity-trust.md`**: подчеркнуть QR primary; перенести «remote invite» в раздел «Future additive channels».
- **`specs/007-pairing-and-firebase-channel/spec.md`**: добавить ссылку на `PairingChannel` abstraction, отметить, что текущая реализация = `QrPairingChannel`, и оставить inline TODO про future channels.

## Exit ramp

Если QR-only окажется недостаточным (например, accessibility-limited юзер не может навести камеру) — `PairingChannel` уже abstract, **дополнительный** adapter добавится без переписывания. Это применение [CLAUDE.md rule 2](../../../../CLAUDE.md) (ACL для каждой external dependency) и [rule 4](../../../../CLAUDE.md) (MVA — добавляем adapter, когда есть конкретный consumer).
