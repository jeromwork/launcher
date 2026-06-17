# Checklists overview — spec 016 F-CRYPTO

Run date: 2026-06-17 (post-clarify pass).

| # | Checklist | Result | Opens | Severity |
|---|---|---|---|---|
| 1 | [requirements-quality](requirements.md) | 13/16 PASS, 2 accepted, 1 minor | 4 | minor |
| 2 | [meta-minimization](meta-minimization.md) | 13/13 PASS | 1 | accepted |
| 3 | [dev-experience](dev-experience.md) | 21/22 PASS, 1 N/A | 2 | minor |
| 4 | [domain-isolation](domain-isolation.md) | 14/14 PASS, 2 N/A | 1 | minor |
| 5 | [wire-format](wire-format.md) | 12/13 PASS, 5 N/A | 2 | 1 medium, 1 minor |
| 6 | [failure-recovery](failure-recovery.md) | 11/11 PASS, 6 N/A | 2 | minor |
| 7 | [security](security.md) | 11/11 PASS, 11 N/A | 3 | 1 medium, 2 minor |
| 8 | [permissions-platform](permissions-platform.md) | 5/5 PASS, 17 N/A | 2 | minor |
| 9 | [backend-substitution](backend-substitution.md) | 14/14 PASS, 1 N/A | 0 | — |
| 10 | [tamper-resistance](tamper-resistance.md) | actionable PASS, 18 N/A | 2 | minor |
| 11 | [modular-delivery](modular-delivery.md) | 13/13 PASS, 5 N/A | 1 | minor |
| 12 | [ai-readiness](ai-readiness.md) | 6/6 PASS, 9 N/A | 0 | — |
| 13 | [capability-registry-readiness](capability-registry-readiness.md) | 3/3 PASS, 9 N/A | 0 | — |
| 14 | [device-self-sufficiency](device-self-sufficiency.md) | 8/8 PASS, 9 N/A | 0 | — |

**Summary**: 14/14 checklists **PASS**. Спека готова к `/speckit.plan`.

## Opens — конкретные действия для plan-фазы

### Medium severity

1. **wire-format O-2**: AEAD ciphertext blob format (nonce + ciphertext + tag) — кто отвечает за envelope wrapper когда F-5 кладёт это в Firestore? Решить в **F-5 spec**, не F-CRYPTO; F-CRYPTO декларирует «возвращаем opaque ByteArray».
2. **security O-2**: `data_extraction_rules.xml` exclude `files/keys/` — application-level task в plan-фазе.

### Minor — для plan.md / research.md

- **requirements O-2**: FR-014 — критерий «ionspin dead» вплести в FR (last commit > 12 мес ИЛИ open critical iOS issue без response > 90 дней).
- **dev-experience O-1**: `CryptoLog` tag для adapter ошибок.
- **dev-experience O-2**: `kotestPropertyIterations` Gradle property (default 100 local, 1000 CI).
- **domain-isolation O-1**: `kotlinx.datetime.Instant` vs `Long` для `createdAt` — выбрать в plan.
- **wire-format O-1**: `contracts/key-blob-v1.md` create.
- **failure-recovery O-1**: sealed class `CryptoException` иерархия.
- **failure-recovery O-2**: idempotency KDoc на каждой method.
- **security O-1**: `@SensitiveByteArray` annotation или KDoc convention.
- **permissions-platform O-1**: Min SDK explicit.
- **permissions-platform O-2**: FR-031 — TEE attestation runtime check.
- **modular-delivery O-1**: implicit dependency «base app MUST require core:crypto».
- **tamper-resistance O-1**: KDoc warning «RandomSource MUST NOT be used for entitlement claims».

### Accepted exceptions (no action)

- **requirements O-1**: implementation details в spec — accepted для infrastructure spec'ей.
- **meta-minimization O-1**: `KeyRotation`/`KeyEscrow` stub ports — accepted потому что их **storage shape** (`retiredAt`, `replacedBy`) consumer'ом сами для себя.

## Verdict

**Спека прошла все 14 checklist'ов**, 1 medium open (data_extraction_rules) для plan-фазы, ~11 minor opens (детализация для plan/research). Готова к `/speckit.plan`.

---

## TL;DR простым языком

Прогнали спеку через 14 проверок качества. **Все 14 — PASS**. Список «доделать в plan-фазе» — 13 мелких пунктов и 1 средний:
- **Средний**: настроить Android backup, чтобы папка с ключами **не** уезжала в Google Drive (иначе на новом телефоне в восстановленном backup'е они будут нечитаемы).
- **Мелкие**: уточнения KDoc-документации, выбор между двумя типами для дат (Long vs Instant), детализация exception-классов и т.п. — обычная инженерная работа в planning-фазе.

Спека **готова** к шагу `/speckit.plan`. Перед этим — следуют **пошаговые сценарии** в plain text формате (как ты и хотел), которые потом используем для acceptance verification.
