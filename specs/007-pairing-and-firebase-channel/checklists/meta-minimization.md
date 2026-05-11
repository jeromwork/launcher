# Checklist: meta-minimization — spec 007

**Generated**: 2026-05-11 by `/speckit.plan` Step 5.
**Per**: Article XI + CLAUDE.md §4 (MVA).

## Result: 13/13 PASS (3 N/A)

### New abstractions

| # | Check | Result |
|---|---|---|
| CHK001 | Каждый new port имеет concrete consumer в этом спеке | **PASS** |
| CHK002 | Single-impl interface justified by port-shape need | **PASS** (5 ports × 2 impls + TrustEdgeBootstrap justified) |
| CHK003 | Orchestrator justified data transformation | **PASS** (PairingService 5+ steps) |
| CHK004 | No custom DSL/registry без простой композиции | **PASS** |

### New modules / packages

| # | Check | Result |
|---|---|---|
| CHK005 | New gradle module satisfies Article V §3 | **N/A** (no new gradle modules) |
| CHK006 | New module "Why is package not enough?" | **N/A** |
| CHK007 | No utils/common/helpers dumping ground | **PASS** |

### New configuration

| # | Check | Result |
|---|---|---|
| CHK008 | New config field имеет current FR consuming it | **PASS** |
| CHK009 | Defaults + backward-compat + migration documented | **PASS** |

### CLAUDE.md rule 4 self-test

| # | Check | Result |
|---|---|---|
| CHK010 | Test 1 (inline check) documented | **PASS** |
| CHK011 | Test 2 (swap cost) documented | **PASS** |

### Removal validation

| # | Check | Result |
|---|---|---|
| CHK012 | Removed abstractions audited | **N/A** (no removals) |
| CHK013 | "Deprecated, will remove later" → concrete task | **N/A** |

## Borderline case analysis

### `TrustEdgeBootstrap` (sealed interface, 1 current subtype `LinkBootstrap`)

**Apparent concern**: sealed type с одним subtype выглядит как «абстракция без consumer'а» (CHK001/CHK002 risk).

**Resolution**: проходит через CLAUDE.md §4 Test 1:
- **Inline-check**: если убрать sealed и сделать `PairingService.claim(): Result<Link, _>` — все будущие use case'ы (спек 011 contacts, future jitsi calls, future multi-admin) требуют **rewrite signature** PairingService (breaking change), не **addition**.
- Per CLAUDE.md §4: «add abstraction today only if not adding it would force a future *rewrite*». Здесь — да, force rewrite.

**Documented in**:
- [plan.md §Reusable trust primitive](../plan.md)
- [research.md §QR-pairing как reusable trust primitive](../research.md)
- [data-model.md](../data-model.md) — `TrustEdgeBootstrap` sealed interface
- [contracts/pairing-token.md](../contracts/pairing-token.md) — wire-format `pairingType` discriminator
- [docs/product/roadmap.md](../../../docs/product/roadmap.md) — entry для спека 007 явно упоминает «reusable trust primitive»
- Project memory `project_qr_pairing_trust_primitive.md`

**Explicit user-driven**: project owner отметил это как важное архитектурное решение 2026-05-11 при review domain-isolation checklist.

**Future ADR-007** запланирован при имплементации спека 011 (или раньше).

### `push-worker/` (TypeScript subproject)

**Apparent concern**: новый top-level subproject — это «новый модуль»; нужно ли обоснование.

**Resolution**: CHK005 N/A т.к. это **не gradle module** — другой язык (TypeScript), другая runtime (Cloudflare Workers). Альтернатив на Spark plan нет (см. research.md §History: три ревизии C1). Документировано в plan.md §Structure Decision и Complexity Tracking.

## Re-run trigger

Пере-запускается в `/speckit.analyze` (Step 5) с финальным состоянием артефактов и кода.
