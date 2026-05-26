# Checklists overview — spec 012

**Spec**: [spec.md](../spec.md)
**Clarify-run**: 2026-05-26

---

## Aggregate result

| Checklist | Result | Critical opens |
|---|---|---|
| [requirements-quality](requirements-quality.md) | 12/16 ✓ | none blocking |
| [meta-minimization](meta-minimization.md) | 11/13 ✓ + 2 N/A | none |
| [domain-isolation](domain-isolation.md) | 12/16 ✓ + 4 plan-deferred | none |
| [wire-format](wire-format.md) | 13/18 ✓ + 3 plan + 1 deviation (pre-prod, Q2) | none — sunset до spec 030+ |
| [failure-recovery](failure-recovery.md) | 14/17 ✓ + 3 open | partialApplyReasons TTL — adjacent concern |
| [performance](performance.md) | 14/20 ✓ + 4 N/A + 2 open | dispatcher invariant, APK measurement |
| [security](security.md) | 19/24 ✓ + 2 N/A + 3 deviation | **🚨 CHK024 — data_extraction_rules.xml MANDATORY** |
| [permissions-platform](permissions-platform.md) | 17/22 ✓ + 5 N/A + 3 minor | none |
| [ux-quality](ux-quality.md) | 15/22 ✓ + 4 deferred + 3 open | minor: DocumentViewer error state, long-pause UX |
| [accessibility](accessibility.md) | 11/25 ✓ + 13 plan + 1 open | **TalkBack-friendly zoom альтернатива в DocumentViewer (MANDATORY add)** |
| [elderly-friendly](elderly-friendly.md) | 13/22 ✓ + 8 plan + 1 open | none |
| [localization](localization.md) | 4/20 ✓ + 15 plan + 1 open | none |
| [modular-delivery](modular-delivery.md) | 16/18 ✓ + 2 plan | none |
| [backend-substitution](backend-substitution.md) | 16/16 ✓ | none — exemplary |

---

## Verdict

**Spec 012 готов к `speckit-plan`** с двумя **mandatory plan-phase action item'ами** (выявлены checklist'ами):

1. **🚨 SECURITY-MANDATORY (CHK024 security)**: добавить `data_extraction_rules.xml` exclude для `Context.filesDir/private-media/` directory. Без этого Google Drive backup сольёт расшифрованные фото паспортов в Google account бабушки — **критическая утечка PII**, единственный точечный риск в текущей модели.

2. **🚨 ACCESSIBILITY-MANDATORY (CHK accessibility adjacent concern)**: добавить **TalkBack-friendly zoom альтернативу** в DocumentViewer — постоянные кнопки `+` / `−` (≥ 56dp) или double-tap zoom. Pinch-to-zoom перехватывается screen reader'ом → бабушка с TalkBack не сможет zoom'нуть. **Без fallback Viewer не accessible**.

Остальные open items — **plan-phase house-keeping** (typography tokens, string IDs, dispatcher invariants, contract files, roundtrip tests, APK measurement task, manual walkthrough plan).

---

## Cross-cutting decisions от clarify-сессии (зафиксированы в spec.md `## Clarifications`)

| Q | Resolution | Сspawned artifacts |
|---|---|---|
| Q1 | Implicit auto-update photoRef по нормализованному `phoneNumber` | TODO-ARCH-017 (multi-identifier), TODO-ARCH-018 (merge dialog) |
| Q2 | Additive без `schemaVersion bump` до spec 030+ | Sunset зафиксирован в Assumptions |
| Q3 | Konsist gate отложен; защита через KDoc + Article XI §8 в конституции | Constitution amendment (Article XI §8, Article XV §8) |
| Q4 | `MediaPicker` adapter с внутренним API-level dispatch, unified bytes; minSdk=26 | Подтверждено в Assumptions |
| Q5 | `DecryptCache` → `LocalMediaStore` persistent; FLAG_SECURE отключён; revoke = stop future | TODO-ARCH-019 (local quota / wipe-on-revoke) |

---

## Constitution amendments сделанные в этом spec'е

- **Article XI §8** — «Reuse before invention»: AI agent / contributor MUST verify, что нет существующего port'a, покрывающего need, перед созданием нового.
- **Article XV §8** — amendment: list of surveyed ports MUST appear in plan/commit message при определении нового port'а.

Файл: [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md).

---

## Backlog items сspawned

- **TODO-ARCH-017**: Multi-identifier contacts (phoneNumbers[] + telegram/facebook/etc.).
- **TODO-ARCH-018**: Contact merge dialog в admin UI.
- **TODO-ARCH-019**: Local storage quota / wipe для `LocalMediaStore`.

Файл: [`docs/dev/project-backlog.md`](../../docs/dev/project-backlog.md).

---

## Next step

✅ **`speckit-plan` complete** (2026-05-26):
- [plan.md](../plan.md) — full plan with architecture diagrams, dependencies, test strategy, risks, Required Context Review.
- [research.md](../research.md) — 10 research areas (R1-R10) с alternatives + regret conditions + exit ramps.
- [data-model.md](../data-model.md) — 4 new ports + 6 new types + Tile extension + envelope metadata.
- [contracts/](../contracts/) — 3 files: tile-document-kind, metadata-kind-registry, local-media-store-layout.
- [quickstart.md](../quickstart.md) — security gate + module setup + KDoc directives + recommended task order.
- [docs/dev/private-media-architecture.md](../../../docs/dev/private-media-architecture.md) — extensibility guide (FR-024).
- **Constitution Check 8/8 PASS** — все gates passed; deviations явно зафиксированы в plan.md Complexity Tracking.
- Plan-level checklist re-runs: domain-isolation 16/16 ✓, wire-format 17/18 ✓ (1 explicit deviation), meta-minimization 13/13 ✓.

**Next**: `speckit-tasks` — decomposition в actionable tasks per plan.md task order (~50 tasks ожидается).
