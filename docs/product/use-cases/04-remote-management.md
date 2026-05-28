# 04. Remote Management — что admin делает на своей стороне

> **Status**: 🆕 первый проход · **Created**: 2026-05-27
> **Зачем читать**: 12 спек уже строят сторону admin'a, но без целостной модели «что вообще admin делает». Здесь — раскладка admin workflow по уровням: одно устройство, несколько устройств, несколько admin'ов.
> **Источник**: `user-journeys-draft.md` §7.1 + спеки 007/008/009.

---

## Что это за документ (просто)

Admin — это не «второй пользователь», а **отдельный продукт внутри продукта**. У него свои потребности (увидеть статус, отредактировать удалённо, понять что что-то не доставилось), свой UI (на телефоне или планшете admin'а), свои страхи («я что, испортил бабушке экран?»).

По принципу user'а 2026-05-27: **«удалённая настройка = та же настройка лаунчера»**. Это значит, что **компонент редактирования один**, но **scope «над чем редактируем»** разный:
- local-edit: «над моим устройством»
- remote-edit: «над bound Managed-устройством»

Этот документ — про **админский слой** поверх редактирования: список устройств, health monitoring, history, multi-admin coordination, notifications admin'у.

## Главные понятия (просто)

- **Admin dashboard** — главный экран admin'а: список paired Managed-устройств с их состоянием.
- **Device card** — карточка одного Managed-устройства: фото / имя / последний раз онлайн / battery / permissions / есть ли проблемы.
- **Health snapshot** — снимок состояния Managed-устройства, который admin видит: battery, connectivity, last-seen, OS version, app version, permissions.
- **Config push** — admin меняет что-то локально, потом push'ит на Managed. Может доехать сразу (FCM) или через 15 мин (polling fallback) или не доехать (offline).
- **Audit log** — журнал «кто/что/когда менял». Особенно важен для multi-admin: иначе непонятно, почему вдруг изменилась раскладка.
- **History** — последовательность снимков config'a, можно делать rollback (009).
- **Collision** — admin1 и admin2 одновременно меняют один и тот же config. Спека 008 решила через optimistic concurrency + merge UI.

## Use case инвентарь

| ID | Кейс | Status | Доп. notes |
|---|---|---|---|
| R-001 | Admin видит список paired Managed-устройств | 🟡 ARCH-008 stub | какая инфо в карточке? |
| R-002 | Admin видит health одного Managed | 🟡 (009) | какие фильтры, какие threshold'ы |
| R-003 | Admin открывает editor удалённо | ✅ (009) | core flow |
| R-004 | Admin push'ит layout, видит статус доставки | 🟡 (007 FCM + 15min polling) | «доставлено» / «в очереди» / «failed» |
| R-005 | Admin alert: Managed offline >N часов | ❌ ARCH-012 | как нотифицировать admin'а — push? email? |
| R-006 | Admin делает rollback (history) | 🟡 (009) | UI sufficient? |
| R-007 | Multi-admin merge collision | ✅ (008) | проверить, что UX реальный, а не теоретический |
| R-008 | Передача ownership на другого admin'а | ❌ | связан с P-005 / S-404 |
| R-009 | Audit log: кто/что/когда менял | ❌ (SEC-003) | нужен для multi-admin trust |
| R-010 | Один admin — N Managed (дашборд) | 🟡 | смотри V-002 family pack |
| R-011 | N admin'ов — один Managed | ✅ (008) | core supported |
| R-012 | Admin видит, что Managed не пользуется тайлом | ❌ | telemetry, может быть privacy issue |
| R-013 | Version mismatch (Managed на старой app version) | ❌ (ARCH-007) | как admin узнаёт; какие fallback'и |
| R-014 | Admin unlink Managed | 🟡 (007 hard delete) | что происходит с накопленными данными |
| R-015 | Admin меняет своё фото — у Managed обновляется | 🟡 (012 stub) | связано с 012 |
| R-016 | Admin читает help / docs | ❌ | будет через 11-support |

## Главные открытые вопросы

### D-13. Multi-admin privacy — admin1 видит то, что внёс admin2?

**Контекст**: спека 008 разрешает N admin'ов на одного Managed. Когда admin1 заходит в editor, видит ли он, что admin2 добавил «контакт X»? Или ему показывают **только итоговую раскладку**, без авторства?

**Варианты**:
- **Прозрачно**: каждое изменение видно с author — «admin2 добавил контакт X 3 дня назад». Плюс — trust. Минус — privacy (admin2 не хочет, чтобы admin1 знал, что он что-то менял).
- **Анонимно**: видна только итоговая раскладка, без авторства. Плюс — нет privacy конфликта. Минус — admin1 не понимает, почему «вдруг» что-то изменилось.
- **Hybrid**: для тривиальных изменений — анонимно, для крупных (удаление контактов, смена SOS) — с автором.

**Регрет**: если выберем «прозрачно» — конфликты между admin'ами выползут. Если «анонимно» — admin теряют context.

**Рекомендация (best-guess)**: **прозрачно**. Доверие важнее privacy в multi-admin семейном контексте (admin'ы — это родственники, не competitors). Можно сделать opt-out для admin'a «я хочу анонимно».

### D-Admin-1. Что в admin dashboard карточке Managed-устройства?

**Варианты, что показывать**:
- **Минимум**: имя, фото, online/offline.
- **Стандарт**: + battery, last-seen, permissions ok, app version.
- **Максимум**: + usage stats (как часто пользуется), последний звонок, состояние SOS (готов или нет).

**Регрет**: максимум — privacy issue для Managed; минимум — admin не понимает, что происходит.

**Рекомендация**: **стандарт**, с opt-in расширения. Usage stats — только если Managed дал согласие.

### D-Admin-2. Notifications admin'у — когда и через что

**Контекст**: admin должен узнавать о проблемах. Но как: push? email? in-app? SMS?

**Варианты**:
- **In-app only**: admin сам заходит проверять. Плюс — privacy. Минус — admin может не заходить неделями, проблема не решается.
- **Push notifications**: «Managed offline 3 дня», «SOS triggered». Плюс — реактивно. Минус — privacy (Google знает, что мы шлём).
- **Email digest**: weekly summary. Плюс — async, не мешает. Минус — slow для critical alerts.
- **All three**: configurable per alert type.

**Рекомендация**: push для critical (SOS, offline >24h) + email для weekly digest. In-app — всегда.

## Что в спеках уже зафиксировано

| Спек | Что фиксирует |
|---|---|
| 007 Pairing | admin → Managed pairing, FCM, polling fallback |
| 008 Config Sync | bidirectional sync, multi-admin merge UI, optimistic concurrency |
| 009 Admin Mode Flows | layout editor, phone health, contacts, history (полноценный admin UX) |
| `project-backlog.md` ARCH-008 | real device list (сейчас stub) |
| ARCH-012 | phone health critical → push admin |

## Связь с другими документами

- **03 Launcher UI** — admin edit UI = local edit UI (один компонент).
- **05 Pairing & trust** — multi-admin = много trust-edges на одного Managed.
- **07 Data & privacy** — multi-admin privacy (D-13) детализируется здесь и там.
- **09 Backend & reliability** — admin notifications зависят от reliable push delivery.
- **10 Monetization** — family pack (несколько admin) = новый pricing tier.

## Источники

- Спеки 007, 008, 009 и их contracts.
- `docs/dev/project-backlog.md` ARCH-008, ARCH-012, SEC-003.
- [Google Family Link](https://families.google.com/familylink/) — modern reference for child/parent management UI (хотя у нас обратная роль).
- [GrandPad family dashboard](https://www.grandpad.net/) — closest reference for elderly-care family management.

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |
