# 03 — Billing Cloud-Only

**Status**: ACCEPTED 2026-06-15

## Принцип

> **Local mode бесплатен бессрочно. Subscription нужна только для cloud features.**

Никакого «trial 30 дней потом read-only». Никакого forced upgrade. Если юзер хочет launcher как **локальное** приложение — он его получает бесплатно.

## Тарифная модель

| Режим | Условия | Цена |
|-------|---------|------|
| **Local-only** | Без Sign-In | Бесплатно, бессрочно |
| **Cloud trial** | Первый месяц после первого Sign-In | Бесплатно |
| **Cloud subscription** | После trial | Платная подписка |
| **Cloud expired** | Subscription отменена / истекла | Автоматический downgrade в local-only |

«Downgrade в local-only» означает:
- Локальный конфиг **сохраняется**, ничего не теряется.
- Cloud features **выключаются**: pair'ы перестают синхронизироваться (помечаются как paused), push не приходит, sync остановлен.
- При возобновлении subscription cloud features включаются обратно с тем же конфигом.
- Юзеру **не показывается** «оплати или ничего не будет работать». Показывается **что именно** не работает в local mode + кнопка «возобновить».

## Зачем такая модель

- **Local launcher** — самостоятельная ценность (бабушка с обычным launcher'ом vs senior-safe layout = разница огромна, даже без облака).
- **Cloud features** — это **родственная инфраструктура** (admin удалённо помогает). За инфраструктуру справедливо платить.
- **Никакого «forced engagement»** — приложение не ставит юзера в положение «нечем пользоваться, плати».

## Защита от взлома

Cloud features всегда проверяются на сервере (server-validated entitlement). Localcomputed entitlement — атакоустойчивая дыра, **не используем**.

Конкретные механизмы (детали — в [`checklist-tamper-resistance`](../../../../.claude/skills/checklist-tamper-resistance/SKILL.md) skill, создаваемом в этом PR):

| Механизм | Цена | MVP / Post-MVP |
|----------|------|----------------|
| **Server-validated entitlement JWT** (short-lived, обновляется при каждом cloud action) | Бесплатно (Cloudflare Worker) | MVP |
| **Play Integrity API** (проверяет, что app не модифицирован) | Бесплатно (Google) | MVP |
| **R8 obfuscation** | Бесплатно | MVP |
| **Code attestation** (критические path'ы сверяются с known-good hash на сервере) | Worker compute | Post-MVP |

Если юзер пропатчит local код, чтобы «cloud features были бесплатны» — это не сработает, потому что:
- Любое cloud action ходит через Worker, который проверяет entitlement JWT.
- JWT выпускается только когда юзер активен в subscription.
- Worker не верит local flag'ам.

## Что **не** делаем

- ❌ Forced trial endemic в каждой фиче.
- ❌ Local feature gating «эта фича доступна только в premium».
- ❌ Ads в local mode.
- ❌ Watermark «free version».

## Что **делаем** для конверсии (post-MVP nudge spec'и)

- ✅ Контекстуальные подсказки «эта фича требует cloud — давайте подключим» с понятным объяснением **что появится**.
- ✅ Возможность попробовать cloud feature в trial (после Sign-In).
- ✅ Прозрачное сравнение local vs cloud в Settings.

Nudges — **отдельные** spec'и post-MVP, frequency-cap придумаем когда понадобится (per явное решение владельца «после MVP»).

## Что нужно поправить

- **`server-roadmap.md`**: добавить «license validation = server-side, server-validated entitlement JWT — обязательная инфра уже в MVP».
- **`docs/dev/project-backlog.md`**: добавить TODO-SUBSCRIPTION-001 = «Worker endpoint /api/entitlement выпускает JWT для активных subscriber'ов».
- **Новый skill `checklist-tamper-resistance`**: на любой cloud-feature спеке проверяет, что entitlement идёт через server, не через client flag.

## Open questions (не блокируют решение)

- **Платёжный провайдер**: Google Play Billing (стандарт) vs Stripe (если будут iOS / web). Решается при first billing implementation spec.
- **Pricing**: $ за месяц? Семейная подписка vs per-device? Решается отдельным product decision'ом.
- **Free tier для verified non-profit / семей с подтверждённой нуждаемостью**: возможный нюанс. Сейчас не решаем.

## Exit ramp

Если subscription модель окажется неработающей (низкая конверсия, высокий churn) — можно перейти на:
- **One-time purchase** (платишь раз, владеешь cloud навсегда).
- **Donation-supported** (бесплатно, опциональные пожертвования).
- **Hybrid** (часть cloud features free, часть premium).

Это **business model exit ramp**, не архитектурный — поэтому стоит дёшево. Архитектурно: subscription state — это просто entitlement flag в Worker, замена логики выпуска JWT.
