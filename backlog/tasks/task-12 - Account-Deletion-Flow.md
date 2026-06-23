---
id: TASK-12
title: Account Deletion Flow
status: Planned
assignee: []
created_date: '2026-06-23 05:37'
updated_date: '2026-06-23 06:16'
labels:
  - phase-2
  - s-spec
  - s-6
  - gdpr
  - deletion
  - play-store
milestone: m-1
dependencies:
  - TASK-3
  - TASK-4
priority: high
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Функция «удалить мой аккаунт и все мои данные» в Settings. Обязательная по требованию Google Play и GDPR (европейский закон о защите данных) — без неё приложение не пройдёт ревью в Play Store.

**Что происходит по шагам:**
1. Пользователь (бабушка или admin) идёт в Settings → «Удалить аккаунт».
2. Большой жёлтый warning: «Вы потеряете все настройки, контакты, фото. Это нельзя отменить через 30 дней.»
3. Пользователь подтверждает паролем (тот же passphrase из TASK-6 F-5).
4. На email приходит подтверждение со ссылкой «Передумал, отменить удаление».
5. Аккаунт переходит в состояние «помечен на удаление, действительно удалится через 30 дней».
6. **В течение 30 дней** пользователь может передумать → кликнуть ссылку → восстановить аккаунт.
7. После 30 дней — автоматическое окончательное удаление: identity, конфигурация, paired-keys, фото-blob'ы в облаке. Каскадно.

**Защита от случайного удаления:**
- Двойное подтверждение (паролем + email-кликом).
- 30-дневный grace period (можно отменить).

## Зачем

**Pre-public-release blocker.** Без этой функции:
- Google Play не пропустит приложение в ревью.
- GDPR (Европа) — штраф до 4% годового оборота за отсутствие.
- Доверие пользователей: без видимой кнопки «удалить» нет уверенности что данные не лежат вечно.

## Что входит технически (для AI-агента)

- Settings → Delete Account UI с подтверждением через passphrase (из TASK-6).
- 30-day grace period (configurable per region post-MVP).
- Email confirmation с deletion summary + undo-link.
- Cascade wipe после grace: identity (Firebase Auth) + configs (Firestore) + KeyRegistry (Keystore) + blobs (R2/B2 from TASK-11).
- Privacy Policy section про deletion (published в Settings).

## Состояние

**Planned.** Зависит от TASK-3 (identity provider) + TASK-4 (configs to wipe).

---

## Готовый промт для `/speckit.specify`

```
Реализуй S-6: Account Deletion Flow.

ЧТО СТРОИМ:
Settings → Delete Account → passphrase confirm → email confirmation с undo-link → 30-day grace period → cascade wipe (identity / configs / KeyRegistry / blobs). Visible в Settings + email confirmation. Mandatory per Google Play + GDPR.

ЗАЧЕМ:
Pre-public-release blocker. Google Play не пропустит без этой функции. GDPR compliance.

SCOPE ВКЛЮЧАЕТ:
- Settings → Delete Account UI с passphrase confirmation (из TASK-6).
- Email confirmation с deletion summary + undo-link (Firebase Auth email).
- 30-day grace period (Cloudflare Worker scheduled cleanup, configurable per region).
- Cascade wipe после grace: Firebase Auth identity + Firestore configs + Android Keystore KeyRegistry + R2/B2 blobs.
- Privacy Policy section про deletion + retention.
- Audit log entry перед wipe (если TASK-32 V-7 готов) — иначе deferred.

SCOPE НЕ ВКЛЮЧАЕТ:
- Partial deletion (только photos / только configs) — out of scope, всё-или-ничего.
- Caregiver-initiated deletion — TASK-31 V-6.
- Cross-app deletion (messenger / album) — TASK-25 P-10 chain-of-trust extends.

DEPENDENCIES:
- TASK-3 (F-4 AuthProvider) — для identity wipe.
- TASK-4 (F-5b config E2E) — для config wipe.
- TASK-6 (F-5 Root Key) — для passphrase confirm + KeyRegistry wipe.
- TASK-11 (S-5 photos) — для blob cascade (если ready, иначе deferred trigger).

ACCEPTANCE CRITERIA:
- Settings → Delete Account → ввёл passphrase → увидел warning экран.
- На email пришло письмо с undo-link.
- Кликнул undo-link в 30 дней → аккаунт восстановлен полностью.
- НЕ кликнул 30 дней → identity + configs + keys + blobs удалены каскадно.
- После окончательного удаления попытка login на новом устройстве → стандартный flow создания нового аккаунта (никаких остатков).
- Privacy Policy в Settings содержит секцию про deletion.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 — manual flow до email send (Firebase emulator).
- Unit-tests cascade wipe с FakeAuthProvider + FakeBlobStorage + InMemoryConfigStore.
- E2E с реальным Firebase staging project (короткий grace = 1 минута для тестов).

CONSTITUTION GATES:
- Rule 1 (domain isolation): DeletionRequest — pure domain.
- Rule 2 (ACL): Firebase Admin SDK не вытекает в domain.
- Rule 8 (server migration): cleanup job в Worker — inline TODO про переезд на own server scheduled job.
- Rule 14 (security): passphrase confirmation обязателен, не просто tap.

EFFORT: Medium (~1-2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 30-day grace period (с undo-button в email confirmation)
- [ ] #2 После grace: cascade wipe identity + configs + KeyRegistry + blobs
- [ ] #3 Email confirmation с deletion summary
- [ ] #4 Privacy Policy section про deletion published
- [ ] #5 Settings → Delete Account → ввёл passphrase → увидел warning-экран
- [ ] #6 На email пришло письмо с undo-link
- [ ] #7 Кликнул undo-link в 30 дней → аккаунт восстановлен полностью
- [ ] #8 НЕ кликнул 30 дней → identity + configs + keys + blobs удалены каскадно
- [ ] #9 После окончательного удаления попытка login на новом устройстве → стандартный flow нового аккаунта (никаких остатков)
- [ ] #10 Privacy Policy в Settings содержит секцию про deletion
<!-- AC:END -->
