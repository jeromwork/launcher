# Checklist: ai-readiness — spec 016 F-CRYPTO

Run date: 2026-06-17.

F-CRYPTO **явно declares** `no AI affordance — internal capability only`. Это чек-лист проверяет корректность отказа.

## Capability shape

- [N/A] CHK001..004 — F-CRYPTO **не должен** быть AI-callable. AI agent с capability `encrypt(...)` или `deriveSharedSecret(...)` может exfiltrate plaintext через side channel. F-CRYPTO остаётся infrastructure layer.

## Affordance contract

- [N/A] CHK005..008 — No AI capabilities exposed.

## PII / privacy boundary

- [x] CHK009 — No capability returns PII — F-CRYPTO работает с byte arrays, не PII.
- [x] CHK010 — No PII в signatures.
- [x] CHK011 — Local-only by default (F-CRYPTO offline).
- [x] CHK012 — No silent telemetry (F-CRYPTO no logging beyond exceptions).

## Provider-agnosticism

- [x] CHK013 — Spec не упоминает специфические AI providers.
- [x] CHK014 — No Gemini Nano / OpenAI / Anthropic SDK dependencies.
- [N/A] CHK015 — On-device inference не applicable.

## Out-of-scope discipline

- [x] CHK016 — Spec явно: `no AI affordance — internal capability only` (AI Affordance секция).
- [x] CHK017 — Spec не designs prompts / function-call schemas.
- [x] CHK018 — Spec не ships demo AI integration.

## Acceptance evidence

- [N/A] CHK019 — No AI capability — нет sample.
- [x] CHK020 — Spec explicitly states `no AI affordance — internal capability only` в AI Affordance секции.

## Дополнительно

- [x] **Корректность отказа от AI**: F-CRYPTO — infrastructure. **Domain-level capabilities** (`pairWithSenior`, `sendCommandToBabushka`) — это потребители F-CRYPTO, **они** описывают AI affordance в своих spec'ах (S-1..S-8). Это и есть архитектурно правильное разделение.
- [x] **Capability Registry exclusion**: F-2 (deferred to Phase 3+) **не** будет включать `aeadEncrypt`/`x25519DeriveSharedSecret`. Spec явно declares это.

## Open issues

None.

## Result

**6/6 actionable PASS, 9 N/A**.

**Verdict**: PASS. F-CRYPTO правильно declares "no AI affordance" — это infrastructure layer, AI capabilities живут в layer выше (S-spec'и).

---

## TL;DR простым языком

Спека явно говорит: **AI к крипто-модулю напрямую дёргать никогда не должен**. Это правильно — если бы AI имел доступ к функции «зашифруй эту строку», он мог бы «случайно» отправить секретные данные через побочный канал. Крипто-функции — **внутренние**, ими пользуются другие модули. А вот **те модули** (например, «позвонить бабушке») — могут быть AI-callable, и они опишут это в своих спеках. Архитектурно правильное разделение.
