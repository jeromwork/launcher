# Feature Specification: Contact Photos and Private Documents (First Client of Crypto Foundation)

**Feature Branch**: `012-contact-photos-and-private-documents` *(branch will be created when spec moves to implementation)*
**Created**: 2026-05-22
**Status**: Stub (вынесено из 011 в scope-split mentor-сессии 2026-05-22; полная разработка — после merge'а 011)
**Input**: roadmap §Spec 012 ([docs/product/roadmap.md](../../docs/product/roadmap.md)) — первый visible-client крипто-фундамента (спек 011). Реализует фото контактов из Contacts Picker (спек 009) на плитках у бабушки + фото личных документов через новый UX-flow.

---

## ⚠️ Stub — этот спек ещё не разрабатывался

Этот файл — **placeholder**, созданный во время scope-split спека 011 (2026-05-22). Полная разработка (`speckit-specify` → `speckit-clarify` → `speckit-plan` → `speckit-tasks` → `speckit-analyze`) — **после merge'а спека 011** в main.

Спек 012 описан здесь верхнеуровнево, чтобы:
1. Спеки 6-9 имели куда ссылаться при упоминании «фото контактов» (раньше они ссылались на 011).
2. План спека 011 имел явного «потребителя» — это валидирует scope 011 (никаких неиспользуемых абстракций).
3. Roadmap имел запись с правильными зависимостями.

---

## Контекст

**Что добавляет:** фотографию контакта на плитке у бабушки + фото личных документов. Сейчас (после спеков 8-9) `Contact.photoRef` всегда `null`, плитка показывает серый placeholder. Спек 012 наполняет это реальными фотографиями, используя крипто-фундамент 011 — фото шифруется на устройстве admin'a, грузится в Firebase Storage зашифрованным, расшифровывается локально у бабушки.

**Зависит от:** спек 011 (e2e-crypto-foundation) — все крипто-операции, Storage adapter, RecipientResolver, envelope format. Спек 008 (`Contact.photoRef: String?`). Спек 009 (Contacts Picker).

**НЕ зависит от:** спек 013+ (модели доверия — 012 использует одностороннюю пару из 011).

---

## User Stories (черновик — финализируются в speckit-specify)

### US-1: Фото контакта на плитке у бабушки (P1)

Admin добавляет внучку Машу через Contacts Picker — Android отдаёт фото из контактной книги. Приложение шифрует фото через 011, грузит зашифрованный blob в Storage по `/links/{linkId}/private-media/{uuid}`, пишет `Contact.photoRef = "private:<uuid>"` в `/config`, пушит. Через ≤ 30 секунд у бабушки на плитке «Маша» — реальная фотография.

### US-2: Фото личных документов (P2)

Admin добавляет к раскладке бабушки плитки с её личными документами (паспорт, СНИЛС, медкарта, страховые). Бабушка тапает «Паспорт» → видит фото в полноэкранном просмотрщике. Новый UX-flow: «+ документ» в редакторе раскладки, picker из галереи, label editor. Documents шифруются тем же envelope из 011, type — в `metadata.kind = "document"`.

### US-3: Honest reporting на ошибках (P2)

Если расшифровка blob'а падает (`MacFailed`, `KeyNotFound`, `BlobMissing`) — плитка показывает placeholder, `/state.partialApplyReasons` дополняется `media_decrypt_failed` (впервые в проекте — enum value зарезервирован в спеке 008), admin UI показывает индикатор.

### US-4: Cleanup при удалении (P2)

Admin удаляет контакт «Маша». Через ≤ 5 минут связанный blob удалён из Storage (если refCount = 0). Уже реализовано в 011 (BlobReferenceLedger + reference counting). Спек 012 только активирует наполнение references реальными `photoRef` значениями.

---

## Что 012 реализует поверх 011

1. **`PrivateMediaResolver`** — IconStorage namespace dispatch для `private:` (объявлен как future в спеке 006 [icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)). На `IconStorage.resolve("private:<uuid>")` → проверяет DecryptCache, при miss — download + unseal + decrypt + cache + return.
2. **DecryptCache** — in-memory LRU расшифрованных blob bytes (max 10 entries, ≤ 5 MB total).
3. **Contacts Picker integration** — расширение спека 009: picker теперь читает `Contact.Photo` URI, передаёт байты в crypto pipeline 011.
4. **DocumentPicker UX** — новый flow для US-2: кнопка «+ документ» в редакторе раскладки, picker из галереи, label editor.
5. **DocumentViewer** — full-screen photo viewer для тап-по-документу-плитке.
6. **`PartialReason.MediaDecryptFailed` emit** — впервые в реальном коде (зарезервировано в спеке 008).
7. **Admin UI индикатор** «фото не отрисовалось у бабушки» с подсказкой re-add или re-pair.

---

## Что 012 НЕ реализует (явный out-of-scope)

- Любую криптографию — это всё в 011 (фундамент).
- Realtime stream encryption — не входит ни в 011 ни в 012; это будущий Jitsi-integration спек.
- Аудио/видеосообщения «как WhatsApp voice» — отдельный future спек, использует те же envelope из 011 с другим `metadata.kind`.
- iOS — out of project scope.

---

## Зависимости-обещания (что 012 обязан выполнить за предыдущие спеки)

- **Спек 006** → реализовать `PrivateMediaResolver` под `IconStorage` ([icon-id-namespace.md:48](../006-provider-capabilities-and-health/contracts/icon-id-namespace.md#L48)).
- **Спек 008** → впервые **заполнить** `Contact.photoRef` реальным значением и **впервые эмитить** `partialApplyReasons += media_decrypt_failed` ([data-model.md:64](../008-bidirectional-config-sync/data-model.md#L64), [state-applied.md:65](../008-bidirectional-config-sync/contracts/state-applied.md#L65)).
- **Спек 009** → подключить фото к Contacts Picker'у ([009 spec.md:218](../009-admin-mode-flows/spec.md#L218)).
- **Compliance** → обновить `READ_CONTACTS` cross-link на 012 (а не на 011).

---

## Когда разрабатывается

После merge'а спека 011 в main. Ожидаемый scope при специфы — ~30-35 task'ов, ~3 недели. Большая часть времени — UI (DocumentPicker, DocumentViewer, admin-side photo upload progress, индикатор у admin'а).

---

<!-- novice summary -->

## TL;DR (простым языком)

**О чём этот спек.** Это **первый видимый продукт** на крипто-фундаменте 011. Здесь появляются фотографии контактов на плитках у бабушки, плюс возможность хранить фото личных документов.

**Почему отдельный спек от 011.** Спек 011 строит инфраструктуру (порты, адаптеры, шифрование, Storage). Сама по себе она ничего видимого не делает. Спек 012 — это «дом на этом фундаменте»: подключает Contacts Picker, расшифровывает фото, показывает на плитках, делает viewer для документов.

**Что спек делает:**
1. Когда admin добавляет контакт с фото — фото шифруется (через 011), грузится в Storage, у бабушки появляется на плитке.
2. Когда admin добавляет «+ документ» — фото из галереи шифруется и грузится, у бабушки появляется плитка-документ.
3. Бабушка тапает по плитке-документу — открывается полноэкранный просмотрщик.
4. Если что-то пошло не так (фото повреждено, ключа нет) — плитка показывает placeholder, admin видит индикатор.

**Когда это будет:** после спека 011 (фундамент) merge'нется в main. Ориентир — 4-7 недель от старта 011.
