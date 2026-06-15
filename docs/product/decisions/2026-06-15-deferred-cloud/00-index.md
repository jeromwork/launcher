# Decision set — 2026-06-15: Deferred Cloud Architecture

Эта серия решений **переопределяет** ключевые архитектурные положения, зафиксированные в [`2026-05-30-f4-identity/`](../2026-05-30-f4-identity/). Принцип, который их объединяет:

> **Каждое устройство самодостаточно. Sign-In — условие cloud-режима, не условие первого запуска. Конфиг принадлежит аккаунту, в котором сделан Sign-In на этом устройстве. Pairing = права править чужой конфиг, не передача собственности.**

## Документы

1. **[01-deferred-sign-in.md](01-deferred-sign-in.md)** — отменяет mandatory Google Sign-In при первом запуске. Sign-In появляется в момент первого cloud action.
2. **[02-config-ownership-per-device.md](02-config-ownership-per-device.md)** — отменяет модель `ownerUid = admin Google UID` для бабушкиного конфига. Конфиг принадлежит локальному Google-аккаунту устройства.
3. **[03-billing-cloud-only.md](03-billing-cloud-only.md)** — фиксирует: local-режим бесплатен бессрочно. Subscription нужна только для cloud-features.
4. **[04-pairing-channel-abstraction.md](04-pairing-channel-abstraction.md)** — фиксирует: QR-pairing (физическое присутствие) — primary path. Любые remote-invite каналы — через `PairingChannel` adapter, additive add'ы.

## Что эти решения **отменяют** из 2026-05-30-f4-identity

| Файл | Что отменено |
|------|--------------|
| [`02-unified-app-model.md`](../2026-05-30-f4-identity/02-unified-app-model.md) §43,54 «Google Sign-In всегда обязателен» | Sign-In не обязателен при первом запуске. Wizard работает локально. |
| [`03-anonymous-removed.md`](../2026-05-30-f4-identity/03-anonymous-removed.md) «Anonymous Auth удалён полностью» | Anonymous Firebase Auth остаётся удалённым, но это **не означает**, что Sign-In обязателен. Local-режим работает без Auth вообще (не через anonymous UID, а без серверной identity). |
| [`08-f4-spec-scope.md`](../2026-05-30-f4-identity/08-f4-spec-scope.md) §37 «010 setup wizard: первый шаг = Google Sign-In» | Первый cloud action триггерит Sign-In, не первый запуск. |

## Что **сохраняется** из 2026-05-30-f4-identity

- Anonymous Firebase Auth удалён — да, остаётся.
- Google Sign-In — единственный auth provider в MVP (когда вообще нужен).
- Email-bound identity, token refresh, session management — без изменений.
- Exit ramp на own-server JWT в Phase 1 — без изменений.

## Status

**ACCEPTED 2026-06-15** на основе разговора владельца с AI о Phase 2 архитектуре.

## Downstream effects (что нужно поправить)

| Артефакт | Что |
|----------|-----|
| `docs/product/roadmap.md` | F-4 не первый шаг Phase 1; F-3 (wizard local) первый |
| `docs/product/use-cases/01-vision-and-positioning.md` | Добавить «каждое устройство самодостаточно» |
| `docs/product/use-cases/05-pairing-identity-trust.md` | Подчеркнуть QR primary; убрать signed invite link как primary |
| `specs/014-tile-editing-admin-senior-profiles/spec.md` FR-003h | Переписать `ownerUid` модель |
| `specs/008-bidirectional-config-sync/spec.md` FR-034 | Переписать revoke семантику (config остаётся, удаляется только access record) |
| `specs/007-pairing-and-firebase-channel/spec.md` | Подчеркнуть QR — primary; remote invite — через `PairingChannel` adapter |

Эти amendments — **последующие** PR'ы. Текущий PR фиксирует **только decisions**, не trogает реализацию.
