# 06. 2FA Admin Device Migration

**Дата фиксации**: 2026-05-30

---

## Суть решения

При смене телефона admin'а (helper, который удалённо настраивает бабушкин телефон) — pair-binding (delegation + crypto keys для шифрования помощник↔бабушка) **должны восстанавливаться через 2FA escrow на own-server**, а не через rescan QR.

**Это отдельная спека post-own-server cutover**, не часть F-4.

---

## Почему это важно

**Сценарий**: helper потерял телефон, купил новый, поставил наш app, залогинился через Google Sign-In (получил тот же UID). Что должно произойти с его delegation на бабушкин конфиг?

**Вариант (a) rescan QR**: бабушка должна вытащить телефон, открыть QR-экран, helper сканирует. **UX-катастрофа для бабушки** (Article VIII §7 senior cognitive load). Убивает половину value Google Sign-In recovery.

**Вариант (b) 2FA escrow (выбран)**: helper'овские crypto keys для общения с бабушкой хранятся **зашифрованно** на own-server под helper'овским UID. На новом устройстве helper расшифровывает их через **second factor** (Google passkey / device PIN / TOTP). Бабушкин телефон ничего не делает — для него helper остаётся тем же UID.

**Вариант (c) auto-restore через server-side UID match**: бабушкин телефон обнаруживает «новое устройство того же helper UID» и автоматически перепринимает. Требует бабушкин телефон online в момент смены. Менее надёжно.

Выбран **(b)**.

---

## Почему post-own-server, не сейчас

**Технические зависимости:**

1. **Encrypted key storage**: на Firebase это означало бы хранить ключи в Firestore зашифрованными — но **управление ключами шифрования** этих ключей само нуждается в **stable identity** и **server-side trust**. На Firebase trust расщеплён (Firebase Auth + Firestore Security Rules). На own-server — всё под одним authorization layer'ом. Проще и чище.

2. **2FA enrollment flow** (как helper регистрирует second factor) — это **отдельный UX**, требующий:
   - UI экраны для choice второго фактора (passkey vs PIN vs TOTP),
   - server-side storage second-factor credentials,
   - recovery flow если потерян и второй фактор.
   Всё это естественнее на own-server.

3. **Recovery flow** (на новом устройстве helper'а):
   - Helper логинится через Google Sign-In.
   - Сервер видит, что у этого UID есть encrypted keys в escrow.
   - Helper проходит second factor.
   - Сервер возвращает (или передаёт recovery instruction).
   - Клиент расшифровывает keys.

Это **server-side flow**, который **естественно** живёт на own-server.

**Делать это сейчас на Firebase = строить временное решение, которое выкинется при own-server cutover.** CLAUDE.md rule 4 (MVA) — не делаем.

---

## Что в F-4 MVP

**В MVP при смене телефона helper'а:**
- Pair-binding **не мигрирует автоматически**.
- Helper заново сканирует QR на бабушкином телефоне (как при первом pairing).
- Известное ограничение, документировано в spec 015.

**Это нормально для MVP**, потому что:
- Реальных пользователей пока нет (pre-release).
- Если helper'ов мало и они продвинутые — rescan QR приемлем как **temporary**.
- 2FA migration требует own-server инфраструктуры.

---

## Что записывается в roadmap

Новая спека **F-7: 2FA Helper Device Migration** добавляется в roadmap как:

```
Phase 4 (post-own-server) — Расширение
  F-7: 2FA Helper Device Migration (encrypted key escrow)
  - Requires: own-server (Phase 1+)
  - Requires: F-5 ConfigDocument E2E Encryption (same crypto ports)
  - Estimate: ~3-4 weeks
```

И в backlog: `TODO-FUTURE-SPEC-012: Helper device migration via 2FA escrow (post-own-server)`.

---

## Inline TODO в коде F-4

В local pairing logic спеки 007 (после переписки в F-4):

```kotlin
// TODO(future-spec-012): Helper device migration via 2FA escrow.
//   MVP: requires rescan QR при смене устройства helper'а.
//   После own-server: encrypted keys в escrow, recovery через second factor.
//   Подробнее: docs/product/decisions/2026-05-30-f4-identity/06-2fa-admin-device-migration.md
```

---

## Связанные документы

- [02-identity-anonymous-removal.md](02-identity-anonymous-removal.md) — pair = delegation, identity named.
- [05-own-server-migration-strategy.md](05-own-server-migration-strategy.md) — own-server timeline, 2FA относится к Phase 4.
- [08-f4-spec-scope.md](08-f4-spec-scope.md) — F-4 явно excludes 2FA migration.
- F-5 spec (когда напишется) — same crypto ports (`AeadCipher`, `AsymmetricCrypto` из 011).
