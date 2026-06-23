---
id: TASK-27
title: Elderly-Friendly Messenger (Jitsi-based)
status: Planned
assignee: []
created_date: '2026-06-23 05:40'
updated_date: '2026-06-23 06:28'
labels:
  - phase-4
  - v-spec
  - v-2
  - messenger
  - jitsi
  - separate-app
milestone: m-3
dependencies:
  - TASK-3
  - TASK-25
priority: high
ordinal: 26000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Отдельное приложение-мессенджер на базе Jitsi Meet (open-source видеозвонки). Используется одной семьёй: бабушка + admin (родственник) + другие члены. Бабушка видит **упрощённый интерфейс** (большие кнопки, минимум функций), родственники — **полный** (как обычный мессенджер).

**Что происходит по шагам (для бабушки):**
1. Бабушка установила мессенджер (отдельный APK).
2. Поскольку launcher уже установлен (TASK-25 P-10 chain-of-trust работает) → мессенджер автоматически узнал её Google identity.
3. Видит **elderly preset**: один большой круг «Позвонить семье», тап → видео-звонок в семейный chat.
4. Никаких настроек, никаких чатов, никаких эмодзи.

**Что происходит по шагам (для admin'а):**
1. Дочка установила мессенджер.
2. Видит **adult preset**: список чатов, групповые звонки, можно создавать новые групповые звонки.
3. Создаёт group call → бабушке прилетает уведомление «Семья хочет вас увидеть» → бабушка тапает «Принять».

**Что общее:**
- Один Google account → одна семья → один identity (через TASK-3 F-4).
- Видео-поток шифруется концом-в-конец через core/crypto/ (TASK-2).
- Семейные настройки шифруются как у launcher (TASK-4 envelope).

**Что разное между preset'ами:**
- UI: elderly = 1 кнопка, adult = полный.
- Возможности: elderly = только принимать звонки + 1-tap позвонить семье; adult = создавать, инвайтить, текст.

## Зачем

Сейчас (Phase 2) семья использует WhatsApp / Telegram / Viber через handoff (TASK-9 S-3). Это **временное решение**. Полноценный продукт — собственный мессенджер на Jitsi, заточенный под семейное использование и elderly UX. Без него мы зависим от чужих платформ.

## Что входит технически (для AI-агента)

- Отдельный Android-пакет (отдельный APK / Play Store entry).
- SSO с launcher через F-4 AuthProvider (один Google login для обоих apps).
- Universal Preset Architecture: elderly preset + adult preset.
- Group call invites через Jitsi room codes.
- Reuse `core/crypto/` для encrypted media (видео-поток).
- Cohabitation через TASK-25 P-10 chain-of-trust.

## Состояние

**Planned.** Зависит от TASK-3 (auth), TASK-25 (cohabitation), TASK-2 (crypto для media).

---

## Готовый промт для `/speckit.specify`

```
Реализуй V-2: Elderly-Friendly Messenger (Jitsi-based separate app).

ЧТО СТРОИМ:
Отдельное Android-приложение на базе Jitsi Meet. SSO с launcher через F-4 (TASK-3) — один Google login. Universal Preset Architecture: Elderly preset (1 большая кнопка «позвонить семье», только принимать invitations) + Adult preset (полный UX: создавать chats, групповые звонки, текст). Group call invites. Reuse core/crypto/ для encrypted video stream. Cohabitation через TASK-25 P-10 chain-of-trust.

ЗАЧЕМ:
Phase 2 использует handoff в WhatsApp/Telegram/Viber как временное решение. Полноценный продукт требует собственный мессенджер с elderly UX.

SCOPE ВКЛЮЧАЕТ:
- Standalone Android app (отдельный package).
- SSO с launcher через F-4 (один Google login на оба app).
- Elderly preset (1 кнопка, только incoming + 1-tap семье).
- Adult preset (полный UX: создание, инвайт, групповые звонки, текст).
- Group call invites через Jitsi room codes.
- Encrypted media через core/crypto/ (TASK-2).
- Cohabitation: автоматическое наследование identity из launcher (TASK-25 P-10).

SCOPE НЕ ВКЛЮЧАЕТ:
- iOS messenger app (отдельная V-x в Phase 5+).
- TV preset для messenger (TASK-29 V-4 может расшириться).
- AI-based moderation / spam detection (TASK-36 L-3 если AI providers готовы).
- End-to-end encrypted text (Jitsi обычно использует TLS-to-server; E2E text — L-9).

DEPENDENCIES:
- TASK-3 (F-4 AuthProvider для SSO).
- TASK-25 (P-10 cohabitation — критично, иначе бабушка логинится заново).
- TASK-2 (F-CRYPTO для encrypted media).

ACCEPTANCE CRITERIA:
- Бабушка установила мессенджер (launcher уже стоит) → automatically logged in (cohabitation работает).
- Видит большую кнопку «Позвонить семье» → тап → видео-звонок начался.
- Дочка инвайтит бабушку в group call → бабушке прилетает push «Семья хочет вас увидеть» → принимает за 1 тап.
- Дочка-admin в Adult preset может создавать новые групповые комнаты.
- Видео-поток зашифрован (manual проверка через packet capture — никаких plaintext frames).
- Бабушка не видит chat-list / emoji / settings (elderly preset hides).

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 + второй эмулятор (или физическое устройство).
- Jitsi-meet-self-hosted для unit-tests (Docker container).
- E2E с реальным Jitsi public server для acceptance.

CONSTITUTION GATES:
- Rule 1 (domain isolation): Jitsi SDK — adapter, не domain.
- Rule 2 (ACL): Jitsi APIs не вытекают в core.
- Rule 9 (preset shareability): elderly preset = обезличенный shareable template.

EFFORT: Very Large (~4-6 months).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SSO с launcher через F-4 (один Google login)
- [ ] #2 Elderly + Adult presets
- [ ] #3 Group call invites
- [ ] #4 Encrypted media через core/crypto/
- [ ] #5 Cohabitation с launcher через P-10 chain-of-trust
- [ ] #6 Бабушка установила мессенджер (launcher уже стоит) → automatically logged in (cohabitation работает)
- [ ] #7 Видит большую кнопку 'Позвонить семье' → тап → видео-звонок начался
- [ ] #8 Дочка инвайтит бабушку в group call → бабушке прилетает push 'Семья хочет вас увидеть' → принимает за 1 тап
- [ ] #9 Дочка-admin в Adult preset может создавать новые групповые комнаты
- [ ] #10 Видео-поток зашифрован (manual packet capture — никаких plaintext frames)
- [ ] #11 Бабушка не видит chat-list / emoji / settings (elderly preset скрывает)
<!-- AC:END -->
