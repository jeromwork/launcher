# Checklist: notification-minimization — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass

> **Big picture upfront**: F-5c использует FCM **строго для data-only messages** — system-level cache invalidation transport, не user-facing notifications. FR-018 + FR-045 explicitly state «no user-visible notification». Therefore F-5c фактически NOT in any tier (indicator / center / push) — это **transport layer**, не presentation layer. Большинство критериев этого checklist'а — N/A для F-5c by design. Notification-minimization compliance — perfect.

## Inventory

- [x] **CHK001** Spec lists every notification event.
  - **F-5c introduces ONE event type to user-facing flow**: `config-updated`.
  - **Tier**: NONE. Data-only FCM message, не user-visible alert, не in-app indicator, не in-app notification center entry.
  - **User-visible effect**: silent — UI на recipient device автоматически refresh'нётся с новой версией конфига (как если бы пользователь сделал pull-to-refresh).

- [x] **CHK002** Tier declared, no event ambiguous.
  - `config-updated` event tier = **«not in any tier»** (transport-only).
  - FR-018: «Push receipt MUST NOT pop user-visible notification — data-only message, no user-facing alert (per CLAUDE.md rule 10)».
  - FR-045: same.
  - **Future event types** (например SOS) могут require user-visible tier — определяется в их spec'ах (S-4 etc.), не F-5c.

## System push justification

- [N/A] **CHK003-008** System push severity/destination/time-sensitivity/relevance/no-gamification/no-digest.
  - F-5c НЕ routes any event к system push notification (visible alert).
  - Все checks про justification system push не applicable.

## In-app indicators

- [N/A] **CHK009** Low-urgency events route to in-app indicator.
  - `config-updated` event — настолько low-urgency, что даже indicator не нужен. UI автоматически refresh'нётся при receipt. Пользователь видит новую версию config'а без отдельного «изменилось!» indicator.
  - Если spec 008 rewrite (Шаг 4b) захочет показывать banner «admin поменял config, refresh» — это S-008 territory, не F-5c.

- [N/A] **CHK010-011** Indicator placement / dismissibility.

## In-app notification center

- [N/A] **CHK012-013** Accumulated low-urgency events в in-app center.
  - F-5c не accumulates events — каждый `config-updated` push triggers immediate refresh, no history.
  - History of config changes — это spec 009 (config history rollback) territory.

## Edge handling

- [x] **CHK014** Push fails → feature works via in-app path.
  - **FR-022 (graceful degradation)**: «If push delivery fails... recipient MUST converge to fresh state via existing pull-on-app-open path».
  - Local cache (F-5b three-tier) ensures senior continues working offline.

- [N/A] **CHK015** Critical events non-push fallbacks.
  - `config-updated` is NOT critical event. Cache invalidation only.
  - Future SOS (S-4) is critical — its non-push fallback (e.g. phone call) — S-4 spec territory, не F-5c.

- [x] **CHK016** No notification depends on POST_NOTIFICATIONS.
  - F-5c data-only FCM messages delivered **без** POST_NOTIFICATIONS permission (per security CHK015, permissions-platform CHK012).
  - Feature works на Android 13+ devices regardless of notification permission state.

## Privacy / lock-screen

- [N/A] **CHK017** Push payloads не reveal PII на lock screen.
  - F-5c data-only messages **не displayed** на lock screen (no `notification` key в FCM payload). No lock screen visibility concern.

- [N/A] **CHK018** Sensitive flows lock-screen visibility.
  - Не applicable.

## Acceptance evidence

- [N/A] **CHK019** Notification matrix (event × tier × severity).
  - F-5c has one event (`config-updated`), one tier (transport-only). Trivial matrix. Not needed as separate artifact.

- [N/A] **CHK020** Tests cover «push permission denied» path.
  - F-5c data-only push not affected by POST_NOTIFICATIONS denial.
  - **Однако**: test покрытие в US-3 «graceful degradation» includes «Worker 503 → save still works, pull fallback» — функционально equivalent test (push delivery failure path).

## Future-event-types guidance

Когда future spec adds event type который **IS** user-visible push (SOS, security alert), его spec MUST:

1. **Declare severity criterion** (actionable + time-sensitive + user-relevant) — все три одновременно.
2. **Specify EventTypeRegistry entry** с `priority: 'high'` + `notification: true` flag (расширение registry для future event types).
3. **Add POST_NOTIFICATIONS permission** request + denial fallback.
4. **Document lock-screen behaviour** + privacy implications.
5. **Pass notification-minimization checklist** для своего spec'а.

F-5c foundation поддерживает оба mode (data-only + notification-bearing), но F-5c **сам** не activates notification mode.

## Summary

- **Pass**: 4/20
- **Partial/Warning**: 0/20
- **Fail**: 0/20
- **N/A**: 16/20

**Big picture**: F-5c — **exemplary** notification-minimization compliance. Push используется **строго для transport** (data-only FCM messages для system cache invalidation), нет user-visible alerts вообще. Это самый сильный возможный compliance с rule 10 — «no push exists unless justified» — у нас просто нет push, есть только background transport.

Будущие event types (SOS, security alerts) — будут проходить этот checklist в своих специфических spec'ах. F-5c foundation поддерживает оба модальности через EventTypeRegistry (priority + collapse + future notification flag).

## Action items

**None** для F-5c. F-5c сам по себе compliant.

**Для future specs** (S-4 SOS, etc.): обязательно пройти этот checklist при addition event type который IS user-visible push.

---

## Заметка для новичка (TL;DR)

Проверено: не пихаем ли мы спам-уведомления пользователю. Правило проекта: **«push — это последняя инстанция, не первая»**. Сначала try in-app indicator (значок на иконке), потом in-app notification center (список «что произошло»), и только потом system push (которая выезжает на lock screen и звонит).

**F-5c — идеальный случай**: мы вообще **не показываем** пользователю никаких alert'ов. FCM используем только как «системный канал» — телефон получает сигнал «обнови данные», UI сам перерисовывается, пользователь видит результат как при ручном pull-to-refresh. Никакой звон, никаких banners, никаких lock-screen уведомлений.

Это **самый сильный compliance** с правилом «notification minimization» — у нас просто нет push'ей пользователю. Все 16 критериев про оправдание push — N/A, потому что нет push'а который надо оправдывать.

**Будущие event types** (например SOS в spec S-4) — будут показывать visible alert. Для них этот checklist обязательно прогонять, и они должны доказать что push **необходим** (actionable + time-sensitive + user-relevant). F-5c foundation позволяет это через настройку per-event-type в EventTypeRegistry, но **сам** F-5c этой возможностью не пользуется.

**Не блокирует** ничего — F-5c compliant. Никаких action items.
