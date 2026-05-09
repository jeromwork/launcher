# Checklist: requirements-quality — spec 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 (speckit-clarify pre-plan pass) · **Score:** 12 ✓ / 4 ◐ / 0 ✗

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## Content Quality

- [~] **CHK001** No implementation details in `spec.md`.
  - **Finding:** spec упоминает `WorkManager`, `AudioManager.STREAM_RING`, `ConnectivityManager.NetworkCallback`, `ProcessLifecycleOwner.RESUMED`, `DataStore`, `kotlinx.serialization`, `Coil`, `composeResources`, `Settings.Global.AIRPLANE_MODE_ON`, `PACKAGE_*` broadcast names, `ACCESS_NETWORK_STATE`.
  - **Disposition:** known systemic departure — spec 005 уже принят с тем же стилем (см. spec 005 §4.1.2 «AndroidActionDispatcher : ActionDispatcher»). Проект сознательно держит технические якоря в spec.md для AI-агента, чтобы plan.md/tasks.md строились без потери семантики. Нарушает букву spec-kit шаблона, но соответствует принятой проектной конвенции. Не блокирует.
- [x] **CHK002** Focus is on user value and business need.
  - §0 («Зачем»), §3 (US от admin/пожилого), §10 (TL;DR без технического жаргона) покрывают.
- [x] **CHK003** Non-technical stakeholder может прочитать.
  - §0 + §10 + US формулировки на русском, без жаргона.
- [x] **CHK004** All mandatory sections present.
  - User Stories (§3), Scope In (§1+§4), Scope Out (§7), FR (§4), SC (§5).

## Requirement Completeness

- [~] **CHK005** No `[NEEDS CLARIFICATION]` markers.
  - Markers'ов нет, но §8 содержит Q1–Q5 на разрешение. Будет PASS после Step 4 clarify (weave + удаление §8).
- [x] **CHK006** Every requirement testable.
  - Все 19 FR + 5 NFR имеют либо SC-связь, либо очевидную unit-test проверку.
- [x] **CHK007** No vague qualifiers.
  - "fast/simple/intuitive" не использованы. NFR использует числа.
- [x] **CHK008** SC measurable.
  - SC-001..008 имеют числа/percentages/binary observable assertions.
- [~] **CHK009** SC technology-agnostic.
  - **Finding:** SC-008 упоминает «Compose recomposition ≤16 ms», SC-002 упоминает «broadcast path / RESUMED fallback path».
  - **Disposition:** же systemic departure, что CHK001. Не блокирует — измеримость сохранена.
- [x] **CHK010** All US acceptance scenarios explicit (Given/When/Then).
  - US1: 3, US2: 2, US3: 3, US4: 4. Все в формате G/W/T.
- [x] **CHK011** Edge cases identified.
  - 7 edge cases в §3: переустановка с другой подписью, DataStore corruption, iOS, fast RESUMED, race watcher, offline iconRef, idempotency watcher.
- [x] **CHK012** Scope clearly bounded.
  - In Scope: §1 + §4. Out of Scope: §7 — 10 пунктов, exhaustive для области.
- [x] **CHK013** Dependencies/assumptions explicit.
  - §6 Assumptions: 6 пунктов с явными dependencies (spec 005 done, CMP, DataStore/WorkManager, iOS deferred, brand assets).

## Feature Readiness

- [~] **CHK014** All FR have clear acceptance criteria.
  - **Finding:** FR-003 (debounce 1s) не имеет explicit SC; FR-008 (no base64) проверяется через fitness-test, но fitness-test не упомянут в §5.
  - **Disposition:** добавить SC-009 «debounce 1s verified by snapshot rebuild rate test ≤ 1/sec» и SC-010 «base64 absence verified by grep-fitness test» во время clarify Step 4 weave.
- [~] **CHK015** Primary flows + at least one error path per US.
  - **Finding:** error paths частично есть в US2 scenario 2 (offline iconRef) и US4 scenario 4 (DND). US1 и US3 — error paths переехали в §3 Edge Cases вместо acceptance scenarios.
  - **Disposition:** acceptable — Edge Cases в spec-kit шаблоне это валидное место для error path. Не блокирует.
- [x] **CHK016** SC have FR sources.
  - Mapping verified: SC-001→FR-001, SC-002→FR-002, SC-003→FR-006/FR-009, SC-004→FR-019, SC-005→FR-017/FR-018, SC-006→NFR-005/FR-015, SC-007→FR-013..016, SC-008→NFR-003.

---

## Open items для speckit-clarify Step 4

1. После принятия Q1–Q5 удалить §8 Open questions из spec.md → CHK005 → PASS.
2. Добавить SC-009 «debounce verified» и SC-010 «no base64 verified» → CHK014 → PASS.

CHK001 / CHK009 / CHK015 — accepted systemic departures, не блокируют переход к speckit-plan.

## Itog

- 12 PASS, 4 PARTIAL (3 accepted departures + 1 fixable в Step 4), 0 hard FAIL.
- **Verdict:** spec годится для speckit-plan после Step 4 weave.

---

## TL;DR для нетехнического читателя *(добавит procedure-add-novice-summary)*

(Будет дописано на Step 5b.)
