# 02. Удаление Anonymous Firebase Auth полностью

**Дата фиксации**: 2026-05-30
**Заменяет**: hybrid модель «admin = named Google, Managed = anonymous + paired»

---

## Суть решения

**Anonymous Firebase Auth удаляется из системы полностью.** На своём сервере (target post-MVP) анонимных конфигов не будет — только JWT после авторизации. Поэтому **уже сейчас** мы не строим зависимость от anonymous.

Каждое устройство = свой Google-аккаунт (или другой auth provider) = свой Firebase UID, выпущенный после Sign-In.

---

## Почему так

### Раньше думали

Бабушка — пассивный потребитель («фасад без identity»). Anonymous Firebase Auth даёт устройству UID без необходимости логиниться. Admin = registered Google. Hybrid модель.

### Что изменилось

Бабушкин телефон — это **самостоятельный app** с собственным владельцем. Владелец = тот, кто залогинился. Это может быть:
- сама бабушка (если у неё есть Google-аккаунт),
- помощник / внук / сосед, который установил и настроил app для бабушки физически (или удалённо через screen-sharing),
- семейный аккаунт, привязанный к бабушкиному устройству.

В **любом** случае — это **named identity**, не anonymous.

### Преимущества

1. **Recovery работает естественно.** При смене телефона / factory reset — Google помнит email, app восстанавливается.
2. **Identity homogeneous** через всю систему. Domain не различает «admin vs бабушка» на уровне auth.
3. **Own-server путь чище.** На своём сервере anonymous конфигов **не будет** — поэтому Firebase anonymous — это **тех долг**, продлевать который не нужно.
4. **GDPR / billing / account deletion** работают для всех одинаково.
5. **Type system гарантия:** в domain нет `AnonymousUser` case — нельзя случайно обработать pair-binding с null email.

---

## Что переписывается в существующих спеках

Anonymous сейчас используется в спеках 007-012. Удаление anonymous требует переписать в части identity binding'а:

| Спека | Что переписывается |
|---|---|
| **007** pair-pairing | Сейчас оба устройства анонимны. После: оба должны иметь auth identity (Google). Pair = delegation (см. ниже). |
| **008** config sync | Owner = anonymous UID → Owner = Google UID. Все ссылки в Firestore переписываются. |
| **009** EditorScreen | Helper редактирует config owner'а через delegated permission. Sessions = named, не anonymous. |
| **010** setup wizard | Pair-binding в первом запуске → Google Sign-In в первом запуске. Pair = optional шаг. |
| **011** contacts + E2E media | Crypto keys derived from Google-bound UID, не anonymous. |
| **012** contact photos | То же. |

Это и есть **F-4 mega-block scope** (см. [08-f4-spec-scope.md](08-f4-spec-scope.md)).

---

## Pair-binding после удаления anonymous = Delegation

Старая модель: pair = крипто-связь двух anonymous устройств через QR.
Новая модель: pair = **delegation** — запись о том, что владелец устройства A разрешил владельцу устройства B (помощнику) редактировать конфиг устройства A.

**Структура:**
```
/delegations/{ownerUid}/helpers/{helperUid}
  ├─ permissions: [edit_config, view_contacts, ...]
  ├─ grantedAt: timestamp
  ├─ revokedAt: timestamp?
  ├─ encrypted_keys: ...   ← (опционально, на свой сервер, зашифровано)
  ├─ schemaVersion: 1
```

**Security rules**: helper может писать в owner config **только если** delegation существует и не revoked.

**Создание delegation**: owner показывает QR → helper сканирует → helper подписывает grant своим Google ID → запись в Firestore.

**Удаление delegation**: owner может revoke в settings → delegation помечается revoked → helper теряет доступ.

**Важно**: после удаления delegation app у обоих сторон **продолжает работать самостоятельно**. Helper остаётся owner'ом своего устройства, owner остаётся owner'ом своего.

---

## Migration существующих anonymous pair'ов

**Wipe.** Мы pre-release, реальных пользователей нет. Все pre-F-4 anonymous данные в Firestore очищаются как часть cutover.

Никакой server-side migration tool — это разработка, можно крашить.

---

## Связанные документы

- [01-unified-app-model.md](01-unified-app-model.md) — каждый app самостоятельный, имеет свой UID.
- [03-auth-provider-port.md](03-auth-provider-port.md) — архитектура auth port'а.
- [04-google-as-one-of-many.md](04-google-as-one-of-many.md) — Google = один из провайдеров; в будущем Email/Phone/Apple.
- [05-own-server-migration-strategy.md](05-own-server-migration-strategy.md) — на своём сервере anonymous не существует.
- [08-f4-spec-scope.md](08-f4-spec-scope.md) — F-4 scope включает переписку 007-012.
