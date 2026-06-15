# 01 — Deferred Google Sign-In

**Status**: ACCEPTED 2026-06-15
**Supersedes**: [2026-05-30-f4-identity/02-unified-app-model.md §43,54](../2026-05-30-f4-identity/02-unified-app-model.md), [08-f4-spec-scope.md §37](../2026-05-30-f4-identity/08-f4-spec-scope.md)

## Принцип

> **Google Sign-In появляется в момент первого cloud action, не на первом шаге wizard'а.**

App после установки полностью работоспособен **без** Sign-In:
- Wizard проходит локально (язык, тема, размер шрифта, выбор preset'а, ROLE_HOME permission).
- Launcher функционирует: плитки, контакты (введённые локально), темы, размер шрифта.
- Конфиг хранится в локальном DataStore/Room.
- **Cloud features OFF**: нет pairing, нет sync, нет push, нет remote management.

Sign-In запрашивается, когда юзер делает действие, требующее сервера:
- Pair с другим устройством.
- Включить cloud backup конфига.
- Получать push (например, для SOS, если устройство пожилого).
- Сохранить шаблон раскладки в shared marketplace.

## Поведение

| Состояние | Что работает | Что не работает |
|-----------|--------------|------------------|
| **Local mode** (без Sign-In) | Wizard, launcher, плитки, локальный конфиг, ACTION_DIAL звонок | Pair, sync, push, remote, marketplace |
| **Cloud mode** (после Sign-In) | Всё | — |

Переход Local → Cloud — **upgrade**, не пересоздание. Локальный конфиг **загружается** в серверный namespace юзера. Если в облаке уже есть конфиг этого аккаунта (юзер пользовался на другом устройстве) — открывается `VersionedConfigViewer` (из S-8) для разрешения.

## Sign-In trap (двойной аккаунт)

Если юзер зашёл под **другим** Google-аккаунтом, чем когда-либо раньше на этом устройстве — это **новый чистый аккаунт**. Никаких prompt'ов «продолжить со старым?». Хочет старые данные — выходит, заходит под старым.

## Почему отменили mandatory Sign-In

| Аргумент | Цена прежнего решения |
|----------|------------------------|
| ~30% юзеров бросают app на mandatory Sign-In screen | Потеря установок |
| Юзеры без интернета не могут даже запустить app | Сценарий «у бабушки только Wi-Fi дома, мы хотим показать app в магазине» — не работает |
| Юзеры без Google-аккаунта не могут пользоваться | Сегмент пожилых без Google — большой |
| Конфликт с принципом «каждое устройство самодостаточно» | Архитектурное противоречие |
| Заставляет переписывать UX «бабушке нужен Sign-In» | Сейчас её делает «компетентный взрослый», который не против Sign-In — но это не повод обязывать всех |

## Что **не** изменилось

- Anonymous Firebase Auth остаётся удалённым (нет anonymous UID).
- Когда Sign-In всё-таки происходит — только Google Sign-In (никаких Phone/Email-Password в MVP).
- F-4 спека остаётся, **но переходит из Phase 1 шаг 1 в группу cloud-features** (активируется по требованию).
- Identity model, token refresh, session management — без изменений.

## Industry precedent

Pattern широко известен под именами `deferred authentication`, `lazy sign-in`, `guest mode → upgrade`. Применяют:

- **Mobile games**: Genshin Impact, Brawl Stars — играешь как guest, в settings «привязать аккаунт».
- **Notion, Obsidian** — заметки локально, sync опционально.
- **Web3 wallets** (MetaMask, Phantom) — кошелёк работает, backup опциональный.

Roblox исторически имел guest mode (2008–2018), потом убрал — но это product decision Roblox под их business model (forced engagement), не архитектурное ограничение pattern'а.

## Risks acknowledged

- **«Скользкий пользователь»** годами в local mode → теряет телефон → нечего восстанавливать. Mitigation: **отдельные nudge-спеки post-MVP** (N-1, N-2, ...) контекстуально подсказывают, что Sign-In откроет cloud features. Не блокирующие, frequency-capped.
- **Local→cloud merge** при promotion — решается тем же `VersionedConfigViewer` из S-8 (третий use case кроме history rollback и multi-admin conflict).
- **Subscription billing**: только cloud-режим. Local — бесплатно бессрочно. Подробнее в [03-billing-cloud-only.md](03-billing-cloud-only.md).

## Что нужно поправить

- **`roadmap.md`**: F-4 не первый шаг Phase 1. Первый — F-3 (wizard, работает локально). F-4 переезжает в зону «cloud features setup».
- **Спека 010** (setup-assistant): первый шаг = НЕ Google Sign-In. Sign-In — в Settings или на cloud feature trigger.
- **F-5 ConfigDocument E2E Encryption**: применяется только при push на сервер. Локальный конфиг не нуждается в E2E (storage device-encryption Android достаточно).
- **Спека 014 F-014.0**: уже работает без Sign-In локально — это **становится canonical pattern**, не legacy mode.

## Exit ramp

Если deferred Sign-In окажется источником багов / поддержки («пользователи теряют данные, потому что не привязали аккаунт») — можно усилить nudges, но **не возвращаться** к mandatory Sign-In. Mandatory Sign-In нарушит принцип самодостаточности устройства.

Если **в Phase 3+** появится фича, для которой local mode принципиально невозможен — она объявляется cloud-only от старта, без претензии работать локально.
