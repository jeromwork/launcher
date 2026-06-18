# Checklist: capability-registry-readiness — spec 016 F-CRYPTO

Run date: 2026-06-17.

F-CRYPTO **не** вводит new user-facing action (call, send, message, etc.). Это infrastructure. Чек-лист **в основном N/A**, но проверим, что нет неявных capabilities.

## Sewing-point bookkeeping

- [N/A] CHK-CR-001..004 — F-CRYPTO не вводит actions, требующих capability declaration. `encrypt`, `sign`, `deriveSharedSecret`, `store` — это **infrastructure verbs**, не user-facing actions. AI agent **не** должен dispatch'ить их (см. ai-readiness CHK001-004).

## Provider neutrality

- [x] CHK-CR-005 — No specific AI/voice/MCP provider mentioned в spec body.
- [x] CHK-CR-006 — No AI/voice SDK imports.
- [x] CHK-CR-007 — No exposure adapter implementation.

## Voice / conversational surface

- [N/A] CHK-CR-008..009 — F-CRYPTO не имеет voice-triggerable actions.

## Auth / scope hints

- [N/A] CHK-CR-010..011 — F-CRYPTO без action scope.

## F-2 collection readiness

- [N/A] CHK-CR-012 — F-CRYPTO не добавляет entries в `docs/dev/capability-registry-pending.md`.

## Refusal triggers

- ❌ Trigger 1 (action without TODO): **не triggered**, F-CRYPTO не вводит actions.
- ❌ Trigger 2 (specific provider mentioned): **не triggered**, никаких MCP/AI provider names.
- ❌ Trigger 3 (exposure adapter shipped): **не triggered**.
- ❌ Trigger 4 (untyped params): **не triggered** — F-CRYPTO использует value classes и data classes.

## Дополнительно

- [x] **F-CRYPTO явно excludes self из Capability Registry** (AI Affordance секция): «capability registry foundation (F-2, отложен в Phase 4+) НЕ будет включать `aeadEncrypt`/`x25519DeriveSharedSecret`. F-CRYPTO остаётся infrastructure layer».
- [x] **Cross-reference**: AI Affordance ↔ Capability Registry consistent — оба declare F-CRYPTO как internal-only.

## Open issues

None.

## Result

**3/3 actionable PASS, 9 N/A**.

**Verdict**: PASS. F-CRYPTO правильно excluded из Capability Registry scope.

---

## TL;DR простым языком

Capability Registry — это будущая система, которая позволит AI-агенту дёргать «команды приложения» (типа «позвони бабушке», «отправь SOS»). F-CRYPTO **не добавляет таких команд** — это просто библиотека математики для шифрования. Поэтому никаких TODO для будущего Capability Registry не нужно — спека явно говорит «нас в Registry не включать».
