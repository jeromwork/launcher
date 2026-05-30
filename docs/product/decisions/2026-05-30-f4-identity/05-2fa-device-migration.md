# 05 — 2FA admin device migration (отдельная спека post-own-server)

## Решение

При смене телефона admin'а (потерял, заменил, factory reset) — pair-binding с другими устройствами **восстанавливается через 2FA escrow**, не через rescan QR. Зашифрованные ключи хранятся **на своём сервере** под admin UID, расшифровываются на новом устройстве через second factor (Google passkey / device PIN).

**Это отдельная спека** post-own-server cutover. В F-4 (спека 015) — out of scope, с known limitation «pair migration требует rescan QR в MVP».

## Почему 2FA escrow, а не rescan QR

1. **UX-катастрофа rescan QR**: бабушка должна вытащить телефон, открыть QR-экран, понять что происходит. Это противоречит Article VIII §7 (senior cognitive load).
2. **Половина value F-4 пропадает** без admin recovery — мы делали F-4 именно ради устойчивости к смене телефона admin'а.
3. **Zero-friction для receiving стороны** — бабушка ничего не делает, не путается, app continues работать как раньше.

## Почему post-own-server, не сейчас

1. **Зашифрованный key escrow** требует надёжного storage и server-side authorization, который мы хотим контролировать. Firebase escrow возможен, но это новый компонент, который потом всё равно мигрирует на свой сервер.
2. **2FA enrollment flow** — отдельная UX, требует passkey / device PIN integration.
3. **Recovery flow на новом устройстве** — отдельная UX, требует проверки second factor.
4. **Лучше один раз сделать на своём сервере**, чем сначала на Firebase escrow, потом переписать.

## Архитектура (когда реализуется)

```
Когда admin pair'ится с бабушкой:
  1. Admin генерирует pair-keys (X25519) на устройстве.
  2. Admin делает 2FA enrollment (Google passkey или device PIN).
  3. Admin шифрует pair-keys ключом, derived из 2FA factor.
  4. Зашифрованные ключи отправляются на свой сервер под adminUid.
  5. Серверу видны: adminUid, encrypted_keys (opaque bytes), 2FA metadata.

Когда admin меняет телефон:
  1. Admin ставит app на новом устройстве, Google Sign-In → тот же UID.
  2. App запрашивает encrypted_keys с сервера под adminUid.
  3. App просит 2FA (Google passkey или device PIN).
  4. App расшифровывает pair-keys локально.
  5. Pair-binding восстановлен без участия бабушки.
```

## Что прописать в F-4 сейчас

- В спеке 015: явный disclaimer «MVP: при смене телефона admin'а pair-binding не мигрирует автоматически; требует rescan QR на бабушкином устройстве».
- В backlog: `TODO-FUTURE-SPEC-012: Admin device migration via 2FA escrow (post-own-server)`.
- Inline TODO в коде рядом с pair-binding logic: `// TODO(future-spec-012): migration via 2FA escrow — MVP requires rescan QR`.
- В roadmap: добавить F-7 (или другой номер) — «2FA admin device migration» как post-own-server foundation spec.

## Dependencies

| Что | Должно быть готово до 2FA migration spec'а |
|---|---|
| Own-server with JWT auth | Phase 1 cutover завершён |
| F-5 ConfigDocument encryption | AeadCipher / AsymmetricCrypto порты готовы |
| 2FA enrollment infrastructure | Отдельный design call |
| Recovery flow UX | Отдельный design call |

## Out of scope этой спеки (когда напишется)

- Социальный recovery (через trusted contacts) — accepted edge case «потерял так потерял» (per roadmap).
- Hardware security keys (YubiKey etc) — V-2 post-MVP.
- Multi-admin recovery (несколько admin'ов одного owner'а одновременно мигрируют) — post-MVP.
