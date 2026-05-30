# 03 — Anonymous Firebase Auth удаляется полностью

## Решение

Anonymous Firebase Auth **удаляется из системы полностью**. После F-4 (спека 015) ни одно устройство не работает на anonymous UID. Каждый app = свой Google-аккаунт = свой Firebase UID, выпущенный через Google Sign-In.

## Почему

1. **Anonymous был временным решением** — для разработки и теста pair-pairing (спека 007). Тех долг.
2. **На своём сервере анонимов не будет.** Все конфиги, фото пользователя, контакты — только через JWT + авторизованного пользователя. Это влияет на дизайн сейчас.
3. **Identity homogeneous через всю систему.** Один путь авторизации, нет двух разных моделей (admin = named, бабушка = anonymous).
4. **Account recovery / GDPR delete / billing** требуют named identity.
5. **Бабушкин app — самостоятельный.** Это не «фасад без identity», это полноценный app с собственным owner Google-аккаунтом (см. файл 02 unified app model). Owner может быть сама бабушка, помощник, внук.

## Что переписывается

| Спека | Текущее состояние (anonymous) | После F-4 (Google) |
|---|---|---|
| **007 pair-pairing** | `signInAnonymously()` + QR pairing | Google Sign-In с обеих сторон; pair = delegation между двумя identified users |
| **008 config sync** | Owner = anonymous UID | Owner = Google-bound Firebase UID; sync logic та же |
| **009 EditorScreen** | Admin редактирует target через anonymous UID | Helper редактирует owner config через delegated permission |
| **010 setup wizard** | Pair-binding на первом запуске | Google Sign-In на первом запуске; pair = опциональный шаг |
| **011 contacts + E2E media** | Ключи на pair anonymous UIDs | Ключи на Google-bound UIDs |
| **012 contact photos** | То же | То же |
| **013 family group** | DEPRECATED 2026-05-28 | — |
| **014 tile editing** | F-014.0 local-only — НЕ затрагивается | F-014.1 (server backup) — теперь под Google UID |
| **F-5 ConfigDocument encryption** | Ключи derived from pair anonymous | Ключи derived from Google-bound identity |

## Что это значит для F-4 mega-block

F-4 — это **не тонкий тонкий port** с одним адаптером. Это **полное переписывание identity слоя** с распространением на 007-012. Estimate: **~12-16 недель**.

## Pair-binding в новой модели

В новой модели pair-binding — это **запись о делегировании** прав на редактирование чужого config'а:

```
/delegations/{ownerUid}/helpers/{helperUid}
├─ ownerUid: <Google UID владельца устройства>
├─ helperUid: <Google UID помощника>
├─ permissions: [edit_config, view_contacts, ...]
├─ grantedAt: <timestamp>
├─ revokedAt: <null или timestamp>
├─ encrypted_keys: <зашифрованные крипто-ключи для E2E>
└─ schemaVersion: 1
```

**Свойства:**
- Owner = Google UID человека, на чьём устройстве установлен app.
- Helper = другой Google UID, кому owner дал право редактировать.
- Может быть создана (через QR), может быть отозвана.
- Отзыв **не убивает** app — owner продолжает пользоваться сам.
- Хранится в шифрованом виде где возможно (см. CLAUDE.md rule 8 — TODO own-server).

## Migration существующих anonymous pair'ов

**Wipe.** Все pre-F-4 anonymous pair'ы недействительны, требуют переустановки. Это допустимо, потому что мы pre-release, реальных пользователей нет. Server-side cleanup при rollout F-4.

## Out of scope F-4

- Custom token issuing на Cloudflare Worker (вариант A из исходного обсуждения) — НЕТ. Бабушкин app самостоятельный, логинится сам Google'ом, не получает дочерний UID от admin'а.
- Бабушкин телефон без своего Firebase UID — НЕТ. Каждый app имеет свой Google → свой Firebase UID.
- Multi-user Android system — НЕТ. Owner = Google в app'е.
