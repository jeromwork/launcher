---
id: TASK-21
title: Account Recovery + 2FA escrow
status: Draft
assignee: []
created_date: '2026-06-23 05:38'
updated_date: '2026-06-23 06:23'
labels:
  - phase-3
  - p-spec
  - p-6
  - recovery
  - 2fa
  - security
milestone: m-2
dependencies:
  - TASK-3
  - TASK-6
  - TASK-12
priority: high
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Расширение recovery (TASK-6 F-5): admin потерял телефон, на новом устройстве заходит через Google + вспоминает пароль — НО ещё восстанавливаются и **связи с paired-устройствами бабушки**, без необходимости заново сканировать QR-коды.

**Что происходит по шагам (без P-6 — текущая ситуация):**
1. Admin сменил телефон.
2. Установил приложение, зашёл через Google + пароль (TASK-6).
3. Конфиги и контакты вернулись.
4. **НО** связи с устройствами бабушки (pairing) пропали — нужно физически прийти к бабушке и пересканировать QR.
5. Неудобно.

**Что происходит по шагам (с P-6):**
1. Admin сменил телефон → Google + пароль → конфиги вернулись.
2. На устройство бабушки приходит push «У admin'а новый телефон, подтвердите».
3. Бабушка видит на своём устройстве короткий 6-значный код.
4. Admin (по телефону или мессенджеру) узнаёт код у бабушки и вводит у себя.
5. Pairing восстановлен — admin снова может управлять устройством бабушки удалённо.

**Что НЕ делается (deprecated):**
- «Социальное восстановление» (друг помогает восстановить если admin забыл пароль и потерял телефон одновременно) — TASK-39 L-6 в Phase 5, отложено как «потерял так потерял».

## Зачем

Без P-6 admin теряет доступ к устройствам родственников при каждой замене телефона → семья перестаёт пользоваться продуктом.

## Что входит технически (для AI-агента)

- 2FA escrow document в Firestore (encrypted, server не видит код).
- Recovery wizard в new-device flow admin'а.
- 6-значный код TTL = 10 минут.
- Cooldown after failed attempts (3 неправильные попытки → 1 час блок).
- UI на устройстве бабушки: «У admin'а новый телефон, код: 123456».

## Состояние

**Planned.** Зависит от TASK-3 (F-4 identity), TASK-6 (F-5 root recovery), TASK-12 (S-6 account flow infrastructure).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-6: Account Recovery + 2FA escrow.

ЧТО СТРОИМ:
Расширяет baseline recovery (TASK-6 F-5). При смене admin'ового устройства: identity + configs восстанавливаются через Google + passphrase, но pairing с Managed-устройствами требует подтверждения через 6-значный код. Код генерируется на Managed (бабушка), вводится на новом Admin device. Encrypted 2FA escrow document в Firestore. TTL 10 мин, cooldown after failed attempts.

ЗАЧЕМ:
Без P-6 admin при каждой замене телефона физически приходит к бабушке. С P-6 — восстанавливается дистанционно через короткий код.

SCOPE ВКЛЮЧАЕТ:
- 2FA escrow document schema в Firestore (encrypted with Managed's pair key).
- Recovery wizard на новом Admin device (после Google + passphrase).
- 6-значный код generation на Managed (пуш notify через TASK-5).
- UI на Managed: «У admin'а новый телефон, код: 123456, действителен 10 минут».
- Cooldown: 3 неправильные попытки на admin'е → 1 час блок.
- TTL код = 10 минут.

SCOPE НЕ ВКЛЮЧАЕТ:
- Social recovery (TASK-39 L-6 в Phase 5).
- Cross-app pair recovery (TASK-25 P-10 chain-of-trust).
- Email-based recovery (TASK-12 S-6 уже покрывает identity).

DEPENDENCIES:
- TASK-3 (F-4 identity для нового admin device).
- TASK-6 (F-5 root recovery работает).
- TASK-12 (S-6 deletion flow infrastructure переиспользуется).

ACCEPTANCE CRITERIA:
- Admin сменил телефон → Google + passphrase → конфиги вернулись.
- Открыл устройство в Admin App → увидел «Подтвердить через бабушку».
- Бабушка увидела на своём устройстве «У admin'а новый телефон, код: 123456».
- Admin ввёл код → pairing восстановлен → может редактировать.
- 3 неправильные попытки → 1 час блок.
- Не ввёл код 10 минут → код истёк, нужно генерировать новый.

LOCAL TEST PATH:
- Два эмулятора через android-emulator skill (admin + managed).
- Manual flow с timer'ом.
- Unit-tests cooldown + TTL logic.

CONSTITUTION GATES:
- Rule 1 (domain isolation): RecoveryCode — pure domain.
- Rule 5 (wire format): EscrowBlob schemaVersion=1.
- Rule 14 (security): cooldown обязателен, TTL обязателен.

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Recovery wizard в new-device flow
- [ ] #2 Short code TTL 10 минут
- [ ] #3 Cooldown after failed attempts
- [ ] #4 Admin сменил телефон → Google + passphrase → конфиги вернулись
- [ ] #5 Открыл устройство в Admin App → увидел 'Подтвердить через бабушку'
- [ ] #6 Бабушка увидела на своём устройстве 'У admin'а новый телефон, код: 123456'
- [ ] #7 Admin ввёл код → pairing восстановлен → может редактировать
- [ ] #8 3 неправильные попытки → 1 час блок на этот код
- [ ] #9 Не ввёл код 10 минут → код истёк, нужно генерировать новый
<!-- AC:END -->
