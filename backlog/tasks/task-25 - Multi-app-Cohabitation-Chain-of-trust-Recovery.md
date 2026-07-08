---
id: TASK-25
title: Multi-app Cohabitation + Chain-of-trust Recovery
status: Done
assignee: []
created_date: '2026-06-23 05:39'
updated_date: '2026-07-08'
labels:
  - phase-3
  - p-spec
  - p-10
  - multi-app
  - crypto
  - recovery
  - one-way-door
  - superseded
milestone: m-2
dependencies:
  - TASK-2
  - TASK-3
  - TASK-6
  - TASK-21
priority: high
ordinal: 25000
superseded-by: TASK-115
---

> **⚠️ SUPERSEDED 2026-07-08 by [TASK-115](task-115%20-%20Decision-Launcher-anchored-spoke-app-onboarding.md)**.
>
> After research session 2026-07-08 (cross-app trust patterns in Signal / WhatsApp / Google / Microsoft / Bitwarden / 1Password / Matrix + Play Install Referrer API mechanics) the model evolved:
> - "Three variants B/C/hybrid" (this task) → **chain of symmetric trusted anchors via Play Install Referrer + sealed_box handoff + opaque server tokens** (TASK-115).
> - "Launcher = anchor, spoke apps = subordinate" (interim) → **any recovered family app can invite the next family app** (final).
> - Universal attestation mechanism split out into [TASK-117](task-117%20-%20Social-recovery-attestor-infrastructure.md).
> - Iconic pairing challenge for visual confirmation split out into [TASK-116](task-116%20-%20Iconic-pairing-challenge-component.md).
>
> Downstream tasks (TASK-6/12/21/27/32/57/101/102/103/112) still reference "TASK-25 multi-app cohabitation" in Decision blocks and Applies-to sections — those are historical immutable per CLAUDE.md rule 11. On next non-Decision touch, prose references should be updated to "TASK-115 (superseded TASK-25)".
>
> Historical content below preserved for context.

---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Когда у семьи на устройстве будут установлены **3 наших приложения** (launcher + мессенджер + фотоальбом), мы хотим чтобы пользователь **один раз** подтвердил «это моё новое устройство», и все 3 приложения автоматически получили доступ. Не три раза по отдельности.

**Что происходит сейчас (без P-10):**
1. Бабушка сменила телефон.
2. Поставила launcher → восстановление через Google + пароль.
3. Поставила мессенджер → опять Google + пароль.
4. Поставила фотоальбом → опять Google + пароль.
5. **Три раза одно и то же** — раздражает.

**Что происходит с P-10:**
1. Бабушка сменила телефон.
2. Поставила launcher → восстановление через Google + пароль (TASK-6).
3. Поставила мессенджер → launcher автоматически подтверждает «это та же бабушка, что и в launcher» → мессенджер сразу получает доступ.
4. Поставила фотоальбом → тоже самое автоматически.
5. **Один раз пароль, всё работает.**

**Технически:**
- Каждое приложение — отдельный Android-пакет с собственным sandbox (Android не разрешает читать друг друга по умолчанию).
- Нужен «chain-of-trust» механизм: launcher подтверждает messenger, messenger подтверждает album.

**Три варианта реализации (выбирается перед спекой):**
- **B**: Через Android ContentProvider + custom permission (одно приложение явно даёт доступ другому).
- **C**: Через сервер (приложение проверяет identity через серверный handoff).
- **B+C гибрид**.

**Защита от компрометации:**
- Можно отозвать доверие к приложению («удалить из доверенной семьи»).
- Если установлен только мессенджер (без launcher) — мессенджер делает свой полный recovery flow.

## Зачем

К моменту запуска мессенджера (TASK-27 V-2 в Phase 4) chain-of-trust **должна быть готова** — иначе UX «логиниться в каждое приложение отдельно» ухудшается обратно. Building раньше — преждевременно (нет 2-го потребителя `core/crypto/`).

## Что входит технически (для AI-агента)

- Выбор технического варианта B / C / гибрид — записать в ADR.
- Wire format для encrypted-pending-handoff (если C / гибрид) — `schemaVersion: 1` с первого commit'а.
- `ChainOfTrustVerifier` port в `core/crypto/` (rule 2 ACL). Adapters — Android (ContentProvider) + iOS (App Groups + shared Keychain).
- Standalone-install fallback: если установлен только messenger (без launcher) — messenger делает свой recovery независимо.
- Reverse trust: messenger может подтверждать launcher (а не только наоборот).
- Trust revocation: «удалить app X из доверенного семейства» (на случай compromise / give-away).

## Состояние

**Planned, ВАЖНАЯ.** Перед TASK-27 V-2 messenger обязательно. Зависит от TASK-2 (core/crypto), TASK-3, TASK-6 (root key), TASK-21 (P-6 single-app recovery).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-10: Multi-app Cohabitation + Chain-of-trust Recovery.

ЧТО СТРОИМ:
Когда у семьи 3 наших Android-приложения на одном телефоне — один клик подтверждения восстанавливает доступ ко всем same-family apps. Chain-of-trust: launcher подтверждает messenger, messenger подтверждает album. Выбор технического варианта B (ContentProvider + custom permission) / C (Server-mediated handoff) / B+C гибрид — решается перед спекой через ADR.

ЗАЧЕМ:
К моменту запуска messenger (TASK-27 V-2) cohabitation должна быть готова, иначе UX «логиниться в каждое приложение отдельно» возвращает раздражение. Building раньше — преждевременно (нет 2-го потребителя core/crypto/).

SCOPE ВКЛЮЧАЕТ:
- ADR с обоснованием выбора варианта B / C / гибрид.
- Wire format для encrypted-pending-handoff (если C / гибрид) — schemaVersion=1.
- ChainOfTrustVerifier port в core/crypto/ (rule 2 ACL).
- Android adapter (ContentProvider) + iOS adapter (App Groups + shared Keychain).
- Standalone-install fallback: messenger без launcher делает свой recovery flow.
- Reverse trust: messenger может подтверждать launcher (не только наоборот).
- Trust revocation: «удалить app X из доверенного семейства» UI.

SCOPE НЕ ВКЛЮЧАЕТ:
- Сам messenger MVP (TASK-27 V-2 в Phase 4).
- Сам photo album MVP (TASK-28 V-3 в Phase 4).
- Cross-platform handoff Android↔iOS (если выбран вариант C — MVP только Android↔Android; iOS↔Android отдельным шагом).
- Key rotation cascade (TASK-41 L-8).

DEPENDENCIES:
- TASK-2 (F-CRYPTO готов, KeyStore + primitives) — done.
- TASK-3 (F-4 identity), TASK-6 (F-5 root key) — нужны для chain.
- TASK-21 (P-6 single-app recovery) — расширяет до multi-app.
- Хотя бы один второй потребитель core/crypto/ existing или scheduled — к моменту P-10 messenger MVP scheduled (TASK-27).

ACCEPTANCE CRITERIA:
- Бабушка сменила телефон → восстановила launcher → установила messenger → messenger сразу работает без второго ввода пароля.
- Установила photo album → также автоматически работает.
- Установила только messenger (без launcher) → messenger предложил свой полный recovery flow.
- Установила сначала messenger, потом launcher → reverse trust: messenger подтверждает launcher.
- Открыла Settings → «удалить app X из доверенного семейства» → revocation сработал, app X требует свой recovery.
- ADR с обоснованием выбора варианта зафиксирован в docs/adr/ перед началом implementation.

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 с тремя test-apps (launcher + 2 mock apps).
- Unit-tests ChainOfTrustVerifier с fake adapters.
- E2E install/uninstall sequence тестирование.

CONSTITUTION GATES:
- Rule 1 (domain isolation): ChainOfTrustVerifier — port.
- Rule 2 (ACL): ContentProvider / App Groups API не вытекает в domain.
- Rule 3 (one-way door): выбор B/C/гибрид — one-way door, ADR обязателен с exit ramp.
- Rule 5 (wire format): handoff blob schemaVersion=1.
- Rule 14 (security): revocation обязательна, trust transitive только на 1 уровень.

EFFORT: Medium (~2-3 weeks). Самая большая неизвестная — выбор B / C / гибрид.
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ChainOfTrustVerifier port в core/crypto/
- [ ] #2 Android adapter (ContentProvider) + iOS adapter (App Groups + shared Keychain)
- [ ] #3 Standalone-install fallback (messenger без launcher работает независимо)
- [ ] #4 Reverse trust + trust revocation
- [ ] #5 Бабушка сменила телефон → восстановила launcher → установила messenger → messenger сразу работает без второго ввода пароля
- [ ] #6 Установила photo album → также автоматически работает
- [ ] #7 Установила только messenger (без launcher) → messenger предложил свой полный recovery flow
- [ ] #8 Установила сначала messenger, потом launcher → reverse trust: messenger подтверждает launcher
- [ ] #9 Открыла Settings → 'удалить app X из доверенного семейства' → revocation сработал, app X требует свой recovery
- [ ] #10 ADR с обоснованием выбора варианта B/C/гибрид зафиксирован в docs/adr/ перед началом implementation
<!-- AC:END -->
