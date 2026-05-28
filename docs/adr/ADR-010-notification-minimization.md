# ADR-010: Notification minimization — push hygiene

**Status**: Accepted (2026-05-28)
**Date**: 2026-05-28
**Decided in**: Phase 0 vision discussion (use-case 06 — communications, use-case 13 — risks/critique).
**Linked artifacts**:
- [`CLAUDE.md`](../../CLAUDE.md) §10 (Notification minimization), refuse pattern #13
- [`docs/product/use-cases/06-communications.md`](../product/use-cases/06-communications.md)
- Skill [`checklist-notification-minimization`](../../.claude/skills/checklist-notification-minimization/SKILL.md)

---

## Context

### Проблема

Дефолт мобильных продуктов 2025-2026 — много push-уведомлений. Engagement-метрики стимулируют разработчиков превращать push в attention-grabber: каждое событие = push, каждый стрик = push, каждое «возможно вам интересно» = push.

Для нашей аудитории это убийственно:

1. **Пожилые users теряют доверие к телефону**, если он «постоянно пищит». Каждый ложный push увеличивает страх, что «что-то сломалось».
2. **Push permission (POST_NOTIFICATIONS на Android 13+) пользователь даёт один раз** — если первая неделя завалена шумом, разрешение отзывается. Тогда **критически важные** push (SOS, security incident) тоже не дойдут.
3. **OEM-killers (Samsung, Xiaomi, Huawei)** агрессивно дросселируют notification channels с высокой частотой. Гамификационный шум поднимает наш app в «спам» bucket в глазах OEM heuristics.
4. **Engagement-loop = product anti-pattern** для care ecosystem. Family member открывает app, потому что **захотел** проверить родителя, а не потому что app «пингает».

### Constraints

- Без явных правил каждая будущая фича будет тянуть push (это дефолт).
- Spec authors — будущие AI-агенты + люди — нуждаются в проверяемом гейте, не в «общем призыве к минимализму».
- Rule должен оставаться актуальным при росте feature surface (10 спеков, 50 событий, 200 событий).

### Альтернативы рассмотренные и отвергнутые

| Альтернатива | Почему отвергнута |
|---|---|
| **«Только важные push»** как общая рекомендация | Subjective; «важное» каждый автор определяет по-своему. Без criterion — компенсация в сторону шума через 6 месяцев. |
| **In-product setting «уровень уведомлений»** | Перекладывает решение на пользователя; пожилой пользователь не настраивает; default определяет реальность. |
| **Запрет push кроме SOS** | Слишком жёстко; убивает legitimate cases (incoming call, account loss). |
| **Доверие notification importance OS-channels** | Каждый channel всё равно настраивается разработчиком; OS не даёт product policy. |

---

## Decision

**Принято: иерархия предпочтений + три критерия для каждого system push.**

### Иерархия (от naturally preferred к last-resort)

1. **In-app indicator** — badge, banner, list item, dot. Видим при следующем открытии app. Default.
2. **In-app notification center** — собранный список «что произошло пока вас не было». Для accumulated low-urgency.
3. **System push notification** — **последняя инстанция**.

### Три критерия для system push (все одновременно)

Каждый push **обязан** justify своё существование:

- **Actionable**: user может (или должен) что-то сделать прямо сейчас.
- **Time-sensitive**: ждать до следующего открытия app **нельзя** (потеря value или risk).
- **User-relevant**: касается user'а лично, не aggregate state системы.

### Примеры применения

| Событие | Тир | Обоснование |
|---|---|---|
| SOS triggered | system push (admin'у) | actionable (позвонить) + time-sensitive (минуты) + relevant (свой ребёнок/родитель) |
| Incoming call | system ringtone (built-in OS) | не наш push |
| New photo from grandchild | in-app indicator | low-urgency, видим при открытии |
| Caregiver online | not a notification | aggregate state, не event |
| Permission revoked (ROLE_HOME) | system push | actionable (re-grant) + time-sensitive (launcher broken) + relevant |
| Permission revoked (not critical) | in-app banner | actionable but not time-sensitive |
| «X выполнил активность Y» (gamification) | in-app indicator (или ничего) | **никогда** push |
| Account deletion grace-period reminder | system push | actionable + time-sensitive (irreversibility) + relevant |

### Guardrails

- **Skill `checklist-notification-minimization`** активируется в `procedure-assess-spec-complexity` при упоминании push / notification.
- **Refuse pattern #13** в CLAUDE.md: push без declared severity criterion → отказ + alternative.
- **Spec template** должен содержать notification matrix (event × tier × severity criterion × destination) для любого спека, который вводит уведомления.

---

## Consequences

### Positive

- Меньше шума → выше шанс, что критический push (SOS) **дойдёт** до admin'а.
- Меньше отозванных permissions → fewer support tickets «у меня ничего не приходит».
- Меньше OEM throttling → стабильнее delivery критичных push.
- Force-function для дизайна: каждое событие осознанно классифицировано.

### Negative

- Авторам спеков нужно работать чуть больше — заполнять notification matrix. Mitigation: skill автоматизирует проверку, template даёт пример.
- Некоторые «маркетинг-приёмы» (re-engagement push) не разрешены by policy. Это **намеренный** outcome.

### Neutral

- Существующий спек 007 (push-relay через Cloudflare Worker) **в рамках правила** — он relay'ит только actionable events (pairing claim, admin push для SOS).

---

## Exit ramp

Если правило окажется слишком строгим — добавляется четвёртый тир «system push (soft)» с ослабленным criterion (только actionable, без time-sensitive). Стоимость: одна правка в CLAUDE.md §10 + skill, без миграций.

Если, напротив, окажется недостаточно строгим (продукт всё равно становится noisy) — добавляется per-event quota (например, не более 1 push на user в неделю кроме SOS/security).

---

## How to apply

1. Каждый спек, вводящий event'ы, заполняет notification matrix в секции «Requirements» или «Design Notes».
2. `procedure-assess-spec-complexity` запускает `checklist-notification-minimization` автоматически.
3. Code review (manual или automated): любой `NotificationCompat.Builder` call site должен быть прослеживаемым к declared push в спеке.
