# 02 — Config Ownership Per Device

**Status**: ACCEPTED 2026-06-15
**Supersedes**: [`specs/014-tile-editing-admin-senior-profiles/spec.md` FR-003h](../../../../specs/014-tile-editing-admin-senior-profiles/spec.md) `ownerUid = admin Google UID` model

## Принцип

> **Каждый конфиг принадлежит Google-аккаунту, в котором сделан Sign-In на устройстве, где этот конфиг живёт. Pairing = права править чужой конфиг, не передача собственности.**

Никакого «admin владеет бабушкиным конфигом». Конфиг бабушки **принадлежит** аккаунту, в который вошла бабушка (или компетентный взрослый, настраивавший её телефон) на её устройстве. Тот же принцип для admin'ского телефона, планшета, любого другого.

## Поведение

### Local mode (без Sign-In)

Конфиг существует только локально, никаких ownership-полей. Никаких прав у других устройств.

### Cloud mode (после Sign-In)

```
Server namespace per Google account:
  /users/{googleUid}/devices/{deviceId}/config/current
                                       /history/...
  /users/{googleUid}/access-grants/{otherGoogleUid}  ← кто может править мой конфиг
```

`ownerUid` каждого конфига = Google UID того, кто Sign-In'нулся на этом устройстве.

### Pairing — модель прав

Pair = `ConfigGrant`:

```
/users/{managedUid}/access-grants/{adminUid}:
  - grantedAt: <timestamp>
  - permissions: [read, write]
  - revokedAt: null
```

Admin читает / правит конфиг бабушки **через её namespace**, по grant'у. Не через свой namespace.

### Revoke pair

- Запись `access-grants/{adminUid}` помечается `revokedAt: <timestamp>`.
- Конфиг бабушки **остаётся** в её namespace, без изменений.
- Admin теряет доступ. Cleanup на client side: его app забывает linkId.

### Удаление admin аккаунта

- Удаляется namespace `/users/{adminUid}/...`.
- Удаляются все `access-grants/{adminUid}` записи у других юзеров (cascading).
- **Конфиги других юзеров не трогаются**.

## Что отменено в спеке 014

[`specs/014-tile-editing-admin-senior-profiles/spec.md` FR-003h](../../../../specs/014-tile-editing-admin-senior-profiles/spec.md):

> «System MUST для бабушкиных устройств поддерживать ту же named configs модель, но `ownerUid` = admin Google UID (бабушка не имеет Google Sign-In, не управляет configs)»

Это противоречит принципу самодостаточности. Переписать на:

> «Each device's config has `ownerUid` = Google UID of the account signed in on that device. If no Sign-In — config is local-only, no ownerUid. Pairing creates a `ConfigGrant` record in the **owner's** namespace, granting access to other Google UIDs.»

## Что отменено в спеке 008

[`specs/008-bidirectional-config-sync/spec.md` FR-034](../../../../specs/008-bidirectional-config-sync/spec.md):

> «При revoke (FR-033 спека 007) `/config/current` и `/state/current` MUST быть удалены рекурсивно»

Это противоречит самодостаточности конфига. Переписать на:

> «On revoke: only the `access-grant` record is marked revoked. The `/config/current` document **remains** in the owner's namespace. Access from revoked party is denied via Firestore Security Rules.»

## Wire format implications

`ConfigDocument` получает поле `ownerUid` (nullable — null в local mode). При промоции local→cloud `ownerUid` устанавливается = текущий Google UID.

Schemaversion bump: `ConfigDocument` v1 → v2.
Backward-compat read: v1 без `ownerUid` → принимаем как local mode artifact.

## Pairing wire format

`ConfigGrant` — отдельный wire-format document в namespace владельца:

```json
{
  "schemaVersion": 1,
  "grantedTo": "admin@gmail.com:google:uid:xxx",
  "permissions": ["read", "write"],
  "grantedAt": "2026-06-15T...",
  "revokedAt": null,
  "establishedVia": "qr-pairing",
  "pairingTokenHash": "<hash of QR token used>"
}
```

`establishedVia` — расширяемое поле, поддерживает будущие `PairingChannel` adapters (см. [04-pairing-channel-abstraction.md](04-pairing-channel-abstraction.md)).

## Migration

Если в проде уже есть данные с `ownerUid = adminUid` для managed configs — нужна migration.

В production система ещё не запущена → existing test data можем стереть. Migration job не требуется. Это записано как note в спеке 014 amendment.

## Что **не** изменилось

- Anonymous Firebase Auth остаётся удалённым.
- Google Sign-In — единственный auth provider.
- F-5 ConfigDocument E2E Encryption применяется к конфигам в cloud namespace.

## Risks

- **Что если бабушка не Sign-In'ится, а admin хочет настроить её телефон remote?** — Без её Sign-In нет namespace для grant'а. Решение: **первая настройка бабушкиного телефона = "компетентный взрослый берёт телефон в руки и делает Sign-In"** (vision pattern). Это согласуется с [vision update](../../use-cases/01-vision-and-positioning.md) «setup делает компетентный человек».
- **Account deletion cascade**: при удалении одного юзера grants у других нужно вычистить. Cloudflare Worker делает это в момент deletion (как уже планируется в S-6).

## Exit ramp

Если модель окажется ограничивающей (например, появится use case «семейный shared config, не привязанный к одному UID») — можно ввести `Group namespace` (то, что было в deprecated F-1). Но это **additive change**, не rewrite принципа самодостаточности.
