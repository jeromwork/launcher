---
id: TASK-121
title: 'Messenger tile with SSO handoff (AuthHandoffService pattern)'
status: Draft
assignee: []
created_date: '2026-07-10 12:30'
updated_date: '2026-07-10 12:30'
labels:
  - phase-2
  - feature
  - preset
  - handoff
  - messenger
milestone: m-1
dependencies:
  - TASK-120
  - TASK-27
priority: medium
ordinal: 121000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Плитка на экране лаунчера, ведущая в мессенджер (наш или сторонний), при клике **автоматически логинит** пользователя без ввода пароля. Механизм — **SSO handoff**: лаунчер уже знает identity пользователя (через pairing / cloud auth) и передаёт мессенджеру **подписанную ссылку-referrer** «этого юзера подтвердил лаунчер», по которой мессенджер заходит в аккаунт.

**Сценарий**:
1. Опекун настраивает бабушке лаунчер, привязывает pairing-id.
2. Бабушка тапает плитку «Сообщения» → лаунчер генерит referrer с её identity.
3. Наш мессенджер по deep-link открывается на её account'е, никакого «введите номер телефона», никакого 6-значного кода из SMS.
4. Если наш мессенджер не установлен — Play Store link на установку (или сторонний fallback типа WhatsApp через generic AppTile).

## Зачем

Бабушке невозможно ввести пароль / SMS-код. Каждый раз когда мессенджер выкидывает на re-auth (после update, после сброса кэша, после долгого простоя) — она застревает. SSO handoff убирает эту failure mode.

Технически это **паттерн доверенного handoff'а** — reusable trust primitive, потенциально применимый и к другим будущим приложениям (свой видео-звонковый app, свой health-forward client).

## Что входит технически (для AI-агента)

- **Component**: `MessengerTile` sealed subtype с параметром `handoffService: String` (id порта в реестре handoff-сервисов).
- **Port**: `AuthHandoffService` в `core/preset/handoff/` — метод `generateReferrer(userIdentity: IdentityRef, targetApp: PackageRef): SignedReferrer`.
- **Adapter (MVP)**: `OurMessengerHandoffAdapter` — использует локальный signing key (Ed25519), генерит short-lived JWT-like token, зашивает в `Intent.ACTION_VIEW` с custom scheme (`ourapp://auth-handoff?token=...`).
- **Refresh policy**: token TTL ~24h, background WorkManager job обновляет token за 6h до истечения. При истёкшем token — synchronous refresh на click с loading indicator.
- **Fallback**: Если `AuthHandoffService.generateReferrer` возвращает Failed — плитка ведёт себя как generic AppTile (открывает пакет напрямую, без handoff, пользователь получает обычный auth prompt).
- **Signing key management**: Ed25519 keypair сгенерирован при первом install, хранится в Android Keystore (hardware-backed где возможно). Public key передан нашему мессенджеру out-of-band (при paring или через bundled resources).
- **Wire format**: `pool.json` +1 declaration `{"id":"messenger-tile-our","component":{"type":"MessengerTile","packageName":"com.ourapp.messenger","handoffService":"our-messenger-handoff","labelKey":"pool.tile.messenger.label"}}`.

## Состояние

**Draft** — заведён 2026-07-10 в ходе `speckit-clarify` цикла TASK-120 (Q3). Owner:
- «важно, не обязательно наш мессенджер, но функционал проверки хотелось бы отработать»
- «по умолчанию к примеру плитка 3 — ватсап, но что если не установлен, именно это отработать»
- «Правда наверное лучше в отдельной таске, т.к. возможно сильное разрастание кода»

Дефер обоснован тем что full SSO handoff требует нетривиальные суб-компоненты (signing key mgmt, JWT-like token contract с мессенджером, refresh policy, offline behavior, revocation), которые расширят TASK-120 foundation-фичу за приемлемый scope. Generic AppTile с проверкой доступности пакета (WhatsApp/Госуслуги случай) остаётся в TASK-120 — это простой check-install pattern.

## Про роли в этой задаче

Использую обобщённые роли per CLAUDE.md convention:

- **primary user** (end-user, тот кого настраивают) — бабушка, пациент, сотрудник — тапает плитку, попадает в мессенджер логин'нутый.
- **remote administrator** — тот кто устанавливал pairing и запушил preset с MessengerTile — получает работающий messenger flow без объяснения primary user'у как логиниться.

## Пример сценария (иллюстративный)

- **Family**: дочка настроила бабушке лаунчер, привязала pairing. MessengerTile ведёт в наш семейный чат. Бабушка тапает и попадает в чат без ввода данных.
- **Clinic**: IT-support привязал пациенту устройство к clinic identity. MessengerTile ведёт в клинический messenger для связи с врачом.
- **B2B contract**: HR настроил корпоративный телефон, MessengerTile ведёт в корпоративный messenger.

## Готовый промт для `/speckit.specify`

```
ЧТО СТРОИМ:
`MessengerTile` Component для лаунчера с SSO handoff — плитка автоматически логинит primary user'а в мессенджер через подписанный referrer, минуя обычный auth-prompt.

ЗАЧЕМ:
Primary user не может проходить обычный auth flow (не помнит пароль, не читает SMS-коды, устройство простое). Каждый re-auth = broken workflow. SSO handoff убирает эту failure mode для нашего мессенджера, устанавливает reusable trust primitive для будущих доверенных приложений.

SCOPE ВКЛЮЧАЕТ:
- `MessengerTile` sealed subtype в Component (core/preset/, KMP commonMain).
- `AuthHandoffService` port + wire format `SignedReferrer` (JWT-like, Ed25519).
- `OurMessengerHandoffAdapter` MVP реализация.
- Signing key management через Android Keystore (hardware-backed).
- Refresh policy: 24h TTL, WorkManager background refresh за 6h.
- Fallback: если handoff недоступен — generic AppTile behavior.
- Wire format: pool.json declaration + preset примеры.
- Unit tests: FakeAuthHandoffService, roundtrip token, refresh scheduling.

SCOPE НЕ ВКЛЮЧАЕТ:
- Реализация серверной части нашего мессенджера (это TASK-27).
- Multi-messenger support (WhatsApp/Telegram/etc через handoff) — future feature.
- Revocation UI («отозвать handoff у мессенджера») — future.
- Cross-device signing key sync — future (нужен когда мессенджер на другом устройстве primary user'а).

DEPENDENCIES:
- TASK-120 (foundation Component/Provider/Preset).
- TASK-27 (наш мессенджер должен принять referrer).

ACCEPTANCE CRITERIA (человеко-проверяемые):
1. Тапаю плитку «Сообщения» — открывается мессенджер сразу на моей учётке, без пароля.
2. Если мессенджер не установлен — вижу «Установить» → Play Store.
3. Если token истёк и refresh не удался (offline) — вижу loading, затем сообщение «попробуйте через минуту».
4. Если снял pairing (identity revoke'нута) — плитка перестаёт handoff'ить, ведёт себя как обычная AppTile.

LOCAL TEST PATH:
- Unit tests JVM: `FakeAuthHandoffService` возвращает canned SignedReferrer, `MessengerTileProvider` вызывает `Intent` construction, verify intent extras contains referrer.
- Integration test (emulator API 34): реальный Android Keystore, генерация Ed25519 keypair, sign/verify roundtrip.
- Manual test (physical device, deferred): реальный мессенджер принимает referrer и логинит user'а.

CONSTITUTION GATES:
- Rule 1: `AuthHandoffService` port в domain, adapter в androidMain — ✓.
- Rule 5: `SignedReferrer` wire format несёт `schemaVersion` — ✓.
- Rule 9: не shareable (identity-bound, содержит user credentials) — правильно.
- Rule 12: если future revocation endpoint появится — zero-trust baseline применяется.
- Rule 13: сервер (когда появится cross-device sync) не видит plaintext identity — zero-knowledge.

EFFORT:
Medium — 3-4 недели: SSO протокол design (~1 week), Android Keystore integration (~1 week), refresh policy + edge cases (~1 week), tests + documentation (~0.5 week).
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] Тап на плитке «Сообщения» открывает наш мессенджер, авторизованный на identity primary user'а — без ввода пароля / SMS-кода
- [ ] #2 [hand] Если мессенджер не установлен — плитка ведёт себя как generic AppTile (Play Store intent)
- [ ] #3 [hand] Token истёк offline — loading indicator, затем graceful failure с retry
- [ ] #4 [hand] Identity revoked (pairing снят) — плитка перестаёт handoff'ить, ведёт себя как обычная AppTile
- [ ] #5 [auto:checklist] specs/task-121-.../checklists/*.md (будут созданы в speckit-cycle)
<!-- AC:END -->

## Definition of Done

Merged в main. `MessengerTile` Component работает end-to-end с нашим мессенджером (TASK-27 Provider готов). Все AC зелёные (`[x]`).
