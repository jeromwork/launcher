# P-10 (placeholder) — Multi-app cohabitation + chain-of-trust recovery

**Status**: PLACEHOLDER, **MOVED to Phase 3 as P-10** (2026-06-18 by owner). Каталог сохранён под старым именем `017-multi-app-cohabitation/` чтобы не ломать inline TODO в коде и cross-references из `docs/dev/crypto-review.md` и `ADR-008`. При создании полной спеки каталог будет переименован в `specs/NNN-multi-app-cohabitation/` со свободным порядковым номером на тот момент.

Created 2026-06-18 to lock context от mentor-сессии перед messenger spec.

**Trigger**: создание полной спеки запускается **до** написания `spec.md` для messenger MVP (планируется ~2026-11 по owner-roadmap). По roadmap'у — после P-9 в Phase 3.

---

## Background context (DO NOT lose)

Семейство приложений (~5 месяцев от 2026-06-18):

- **launcher** — это приложение (F-CRYPTO 1.0.0 готов).
- **messenger** — E2E чат для пожилых.
- **photo album** — управление семейными фото.

Каждое — отдельный Android-package, отдельный sandbox. Каждое имеет свой экземпляр `:core:crypto` и свои ключи. Android **не разрешает** одному app читать файлы другого.

---

## Owner vision (буквальные слова от 2026-06-18)

> «При восстановлении лаунчера мы подтвердили его — и сам лаунчер также мог подтвердить, что вот мессенджер тоже доверенный. Чтобы одни ключи шифровали другие ключи. Как двухфакторная авторизация между разными лаунчерами на разных телефонах — здесь чтобы примерно так же работало для разных приложений, чтобы одно подтверждало другое. Условно один клик — нажал пользователь, восстановил доступ. Чтоб не для каждого приложения свои.»

UX-цель: **один клик восстанавливает доступ ко всем same-family app на устройстве**.

---

## Decision deferred to spec authoring time

Три технических варианта для `chain-of-trust` (см. `docs/dev/crypto-review.md` §A2):

- **B — ContentProvider + custom permission** (Signal-style). Cohabitation на одной платформе.
- **C — Server-mediated handoff**. Cross-platform recovery (launcher Android → messenger iPhone).
- **B + C гибрид** (рекомендация mentor-сессии).

Variant **A (Independent)** — текущее поведение MVP первого релиза каждого app. Не меняется без новой спеки.

---

## Research questions для будущей spec-фазы

1. **ContentProvider permission UX** на Android 15/16 — изменилось ли поведение custom permission'ов?
2. **iOS App Groups + shared Keychain** — какие ограничения для cross-app data в одной Team ID?
3. **Cross-platform handoff format** — какой wire format для encrypted-pending-handoff на сервере? CBOR? Protobuf?
4. **Standalone install** — если пользователь поставил только messenger (без launcher), как messenger делает свой recovery? Нужен ли отдельный standalone flow?
5. **Reverse trust** — может ли messenger подтверждать launcher (а не только launcher подтверждает messenger)? Влияет на UX, если пользователь восстанавливает messenger первым.
6. **Key rotation cascade** — если launcher rotation, messenger тоже rotation? Или независимо?
7. **Trust revocation** — если пользователь хочет «удалить messenger из доверенного семейства», как это работает?

---

## NOT в scope этой спеки

- Сам messenger MVP — отдельная спека.
- Сам photo album MVP — отдельная спека.
- Server architecture для handoff'а — отдельная спека (`server-roadmap` или новая F-Server).
- Key rotation real-impl — спека 017 (если будет переименована, или отдельная новая).
- Data export UI — `docs/dev/crypto-review.md` §A3, отдельная спека.

---

## Inline TODOs в коде, ссылающиеся на эту спеку

- `core/crypto/build.gradle.kts` — `TODO(extract-when-2nd-consumer)` + `TODO(pre-release-audit): library extract sequence`.
- `app/src/main/java/com/launcher/app/di/F016CryptoModule.kt` — `TODO(pre-release-audit): multi-app cohabitation`.
- `core/crypto/src/iosMain/kotlin/family/crypto/SecureKeyStore.ios.kt` — `TODO(pre-release-audit): App Groups + shared Keychain access groups`.

Все TODO grep-discoverable: `grep -r "TODO(pre-release-audit):" core/ app/ docs/ specs/`.

---

## TL;DR простым языком

Когда у нас будут 3 приложения одной семьи (launcher + messenger + photo) на одном телефоне, владелец хочет, чтобы пользователь **один раз** подтвердил «это новое устройство — реально я», и после этого **все 3 приложения** автоматически восстановили доступ. Не три раза по очереди.

**Сейчас (MVP)** — каждое приложение восстанавливается отдельно. Не идеально, но работает.

**Через ~5 месяцев** (когда выйдет messenger) — надо реализовать «launcher подтверждает мессенджер, мессенджер подтверждает фото» через ContentProvider + custom permission на Android и App Groups + Keychain sharing на iOS.

**Документ нужен** чтобы через 5 месяцев мы не забыли контекст и точно знали, какие вопросы решать.
