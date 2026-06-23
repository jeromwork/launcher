---
id: TASK-8
title: Admin App + QR Pairing
status: Planned
assignee: []
created_date: '2026-06-23 05:36'
updated_date: '2026-06-23 06:13'
labels:
  - phase-2
  - s-spec
  - s-2
  - cloud
  - admin
  - pairing
milestone: m-1
dependencies:
  - TASK-3
  - TASK-6
  - TASK-7
priority: high
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Отдельное приложение для родственника-помощника (admin), который удалённо настраивает устройство пожилого. Соединение двух устройств через QR-код.

**Что происходит по шагам:**
1. Родственник устанавливает Admin App на свой телефон.
2. Регистрируется через Google-вход (это происходит при первом cloud-действии, не при запуске — см. TASK-3 F-4).
3. В Admin App нажимает «Подключить устройство бабушки» — открывается QR-код на экране.
4. Бабушка на своём устройстве (Simple Launcher из TASK-7) в Settings выбирает «Принять подключение» — открывается сканер.
5. Бабушка наводит камеру на QR-код Admin App.
6. Происходит обмен криптографическими ключами (handshake) — связь установлена.
7. Теперь admin видит устройство бабушки в своём списке, может редактировать конфигурацию (раскладка плиток, контакты).
8. Изменения отправляются на устройство бабушки через защищённый push (TASK-5 F-5c).

**Что если admin потерял своё устройство:**
- Зашёл на новом телефоне через тот же Google + пароль → доступ к paired-устройствам восстановился (через TASK-6 F-5 recovery).
- Pairing не нужно делать заново.

## Зачем

Без admin app родственник физически приходит к бабушке настраивать каждое изменение. С admin app — может делать удалённо. Это **главная фича для семейной коммуникации** — родственник за 200 км обновляет иконки и контакты.

## Что входит технически (для AI-агента)

- `AdminAppWizardManifest` — отдельный preset wizard (не Simple Launcher).
- QR-based `PairingChannel` adapter (primary per decision 2026-06-15-deferred-cloud/04).
- Handshake через Curve25519 (из TASK-2 core/crypto).
- Pairing key хранится в `KeyRegistry` per identity (из TASK-6).
- Admin dashboard: список paired Managed-устройств + статус последнего синка.
- Sign-out preserves pairing (для recovery flow на новом устройстве admin'а).
- Remote invites через адаптер (deferred, второй PairingChannel — TASK-31 V-6).

## Состояние

**Planned.** Cloud feature. Зависит от TASK-3 (Google Sign-In), TASK-6 (Root Key Registry), TASK-7 (Simple Launcher как target pairing'а).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-2: Admin App + QR Pairing.

ЧТО СТРОИМ:
Отдельное Android-приложение для родственника-помощника (admin). Admin регистрируется через Google Sign-In, сканирует QR-код от Managed-устройства (Simple Launcher), происходит pairing handshake через Curve25519. Admin видит paired Managed в списке, может редактировать config — изменения летят через F-5c push.

ЗАЧЕМ:
Главная фича для семейной коммуникации — родственник за 200 км обновляет иконки и контакты бабушки удалённо. Без этого admin физически приходит для каждого изменения.

SCOPE ВКЛЮЧАЕТ:
- AdminAppWizardManifest (отдельный preset).
- QRPairingChannel adapter (primary канал per decision 2026-06-15-deferred-cloud/04).
- Pairing handshake через Curve25519 (из TASK-2 / F-CRYPTO).
- Pairing key persistence в KeyRegistry per-identity (из TASK-6 / F-5).
- Admin dashboard UI: список paired Managed + статус последнего синка.
- Sign-out preserves pairing (recovery-ready).

SCOPE НЕ ВКЛЮЧАЕТ:
- Remote invite через ссылку (LinkInvitePairingChannel) — TASK-31 V-6 в Phase 4.
- Config editor UI — TASK-13 S-8 в Phase 2.
- Contact list editing — TASK-9 S-3.
- Photo upload — TASK-11 S-5.
- iOS Admin App — TASK-26 V-1 в Phase 4.

DEPENDENCIES:
- TASK-3 (F-4 AuthProvider + Google Sign-In) — done.
- TASK-6 (F-5 Root Key + KeyRegistry) — in progress.
- TASK-7 (S-1 Simple Launcher) — должен существовать как target pairing'а.

ACCEPTANCE CRITERIA:
- Admin установил приложение → зашёл через Google → увидел экран «подключить устройство».
- Сгенерировал QR → бабушка отсканировала с Simple Launcher → handshake прошёл за <10 секунд.
- Admin видит устройство бабушки в списке paired со статусом «online».
- Admin вышел из аккаунта → зашёл через тот же Google → pairing восстановлен, не нужно сканировать заново.
- Сменил телефон admin'а → Google + пароль → доступ к paired-устройствам сохранён через F-5 recovery.
- Pairing-handshake устойчив к network glitches (timeout + retry).

LOCAL TEST PATH:
- Два эмулятора (skill android-emulator) — Admin + Managed.
- QR через на-экранный код в одном эмуляторе + сканер в другом.
- Fake AuthProvider для unit-tests pairing logic.

CONSTITUTION GATES:
- Rule 1 (domain isolation): PairingChannel — port в core/pairing/.
- Rule 2 (ACL): Firebase / QR libraries не вытекают в domain.
- Rule 5 (wire format): PairingHandshakeBlob schemaVersion=1.
- Rule 9 (shareability): pairing не нарушает identity-bound data (pairing key не shareable).

EFFORT: Large (~3-4 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 QR pairing: Admin scan → Managed accept → handshake через Curve25519
- [ ] #2 Pairing key стored в KeyRegistry per identity
- [ ] #3 Admin dashboard: список paired Managed + статус последнего sync
- [ ] #4 Sign-out preserves pairing (для recovery)
- [ ] #5 Admin установил приложение → зашёл через Google → увидел экран 'подключить устройство'
- [ ] #6 Сгенерировал QR → бабушка отсканировала → handshake прошёл за <10 секунд
- [ ] #7 Admin видит устройство бабушки в списке paired со статусом 'online'
- [ ] #8 Admin вышел из аккаунта → зашёл через тот же Google → pairing восстановлен, не нужно сканировать заново
- [ ] #9 Сменил телефон admin'а → Google + пароль → доступ к paired-устройствам сохранён
- [ ] #10 Pairing-handshake устойчив к обрывам сети (timeout + retry)
<!-- AC:END -->
