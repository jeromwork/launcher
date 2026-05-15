# Cross-Artifact Trace — Spec 009

**Date**: 2026-05-15
**Audited**: spec.md (76 FR/NFR/A11Y), plan.md (11 sections, Constitution Check 8/8 PASS), data-model.md (14 types),
research.md (13 R-entries), contracts/ (3 files: config-history, config-current-additions, vcard-incoming),
tasks.md (108 tasks T001-T167 across 14 phases), checklists/ (17 files: 13 spec-level + 3 plan-level + _overview.md).

---

## Check Results

### 1. Spec → Tasks coverage: PASS-WITH-WARN (74/76 explicit, 2 implicit)

Все 67 FR + 7 FR-A11Y + 2 NFR имеют запись в `tasks.md` §«Coverage verification» table (lines 1031-1087). Spot-check 10 FRs (FR-001, FR-008, FR-014b, FR-022a, FR-027a, FR-035a, FR-036, FR-043, FR-045a, FR-A11Y-003) — каждая task в Trace field явно ссылается на соответствующий FR.

**Implicit coverage (2 WARN, не FAIL):**

| FR | Status | Tasks claiming coverage | Issue |
|----|--------|------------------------|-------|
| FR-012 (preset selector dropdown — workspace/simple-launcher/launcher) | **WARN — implicit** | T095 (`TileEditForm`) | tasks.md self-flags: «covered via TileEditForm T095 (preset selector — implicit; if missing, расширить T095 за пределы данной декомпозиции)». Task T095 acceptance не упоминает preset selector явно — только SlotKind dropdown + contact selector + packageName field. |
| FR-046b (distinct hue severity colors light/dark theme) | **WARN — implicit** | T081 (vector icon tinting) | tasks.md self-flags: «covered via theme/severity-colors (plan.md §2) — implicit в T081 vector icon tinting; explicit задача отсутствует в данной декомпозиции (можно extend T081 при имплементации)». Plan.md §2 указывает файл `app/.../theme/severity-colors.kt` но создание этого файла не имеет своей task. |

**Note on FR-032**: упомянут как «перенесён в FR-028» — merged-in, не отдельный FR, поэтому отсутствие task'а корректно.

**Verdict**: PASS с двумя explicit-flagged WARN. Tasks.md уже честно отмечает обе implicit-задачи; рекомендация в Punch List.

---

### 2. User stories → acceptance evidence: PASS (7/7)

| US | Priority | Covering tasks | Test type |
|----|----------|---------------|-----------|
| US-1 (admin edits layout) | P1 | T094 (EditorScreen), T100 (drag) | UI test + manual edit-to-publish (SC-001 ≤90s) |
| US-2 (admin sees health) | P1 | T080 (AdminDevicesScreen), T082 (listener), T160 (TalkBack walkthrough) | UI test + manual SC-002 ≤35s |
| US-3 (add contact via picker) | P2 | T110 (picker launcher), T111 (permission rationale) | Integration |
| US-4 (VCard share) | P2 | T117 (VCardReceiveActivity), T163 (smoke walkthrough) | Manual smoke + microbench (NFR-002) |
| US-5 (rollback) | P2 | T125 (HistoryScreen), T128 (rollback button) | End-to-end manual flow (SC-004 ≤60s) |
| US-6 (Managed via 7-tap) | P3 | T132 (symmetry), T161 (elderly walkthrough) | Manual elderly smoke |
| US-7 (OpenApp tile) | P3 | T140 (selector), T143 (dispatch), T144 (toast) | OEM matrix (T162) |

---

### 3. Plan → Spec ground: PASS

Per Article XII §1 anti-smuggling — каждая plan abstraction grounded в spec FR/US:

| Plan element | Spec ground |
|-------------|-------------|
| `ConfigHistoryRepository` port | FR-036, FR-037, FR-038, FR-040 (history subcollection + retention) |
| `InstalledAppsCatalog` port | FR-034 (variant a — выбор из списка) |
| `SystemContactPicker` port | FR-024, FR-026 (picker → Contact.fromRaw) |
| `VCardImporter` port | FR-027, FR-028, FR-031 (VCard share intent) |
| `OpenAppDispatcher` extended | FR-034, FR-035, FR-035a (fallback chain + queries) |
| package `api/contacts/` | FR-023..033 |
| package `api/history/` | FR-036..043 |
| package `api/apps/` | FR-034, FR-035 |
| package `api/admin/` | FR-001, FR-005, US-1 |
| package `ui/health/` | FR-017..022a (phone health UI) |
| §11 C-1 (NO EventBus) | FR-021 (single emit site, no subscriber) |
| §11 C-2 (NO TransformerRegistry) | FR-043 + TODO-ARCH-015 |
| §11 C-3 (inline-TODO exit-ramps) | FR-013, FR-021 (forward-compat structures) |
| §11 C-4 (PhoneHealthIndicator placement) | resolves spec.md §Key Entities ambiguity (`app/health-ui/` vs `:core/.../ui/health/`) |
| §11 C-5 (SeverityWire vs PhoneHealthSeverity split) | implementation detail of FR-018 |
| Phase 0..8 roadmap | maps 1:1 to FR groupings in spec.md |

**Discrepancy noted**: §11 C-4 explicitly resolves a placement conflict between spec.md and plan.md (spec said `app/health-ui/`, plan says `:core/commonMain/ui/health/`). This is acknowledged in tasks.md T026 — not anti-grounding, but a documented one-way-door decision in plan.

---

### 4. Contracts → tests: PASS (3/3 contracts have roundtrip + back-compat)

| Contract | Roundtrip task | Back-compat task |
|----------|---------------|------------------|
| `contracts/config-history.md` | T036 (`ConfigSnapshotRoundtripTest`) | T037 (forward fail-closed via SnapshotMigrator), T038 (v1 reader reads v1) |
| `contracts/config-current-additions.md` | T039 (null), T040 (non-null `PresetSettings`) | T041 (`Spec008ReaderIgnoresPresetOverridesTest` — old reader does not crash) |
| `contracts/vcard-incoming.md` | T042 (5 real samples: WhatsApp/Telegram/Viber/Android export/emoji-FN) | T043 (5 malformed: oversized/non-UTF8/missing-FN/missing-TEL/truncated) |

All three contracts have ≥1 roundtrip + ≥1 backward-compat test per CLAUDE.md rule 5. Fixture paths committed (commonTest/resources/wire-format/ + commonTest/resources/vcard/).

---

### 5. Checklists → spec citations: PASS

Sampled all 17 checklist files for FR/Spec citation density:

| Checklist | FR/spec refs | CHK items | Citation density |
|-----------|-------------|-----------|------------------|
| _overview.md | 19 | 8 | High |
| wire-format.md | 50 | 33 | High |
| meta-minimization.md | 46 | 20 | High |
| meta-minimization-plan.md | 23 | 12 | High |
| wire-format-plan.md | 18 | 34 | Medium |
| domain-isolation-plan.md | 3 | 17 | **Low** (plan-level cites plan.md sections, OK) |
| ux-quality.md | 123 | 27 | Very high |
| core-quality.md | 25 | 37 | High |
| domain-isolation.md | 30 | 19 | High |
| accessibility.md | 48 | 40 | High |
| requirements-quality.md | 45 | 23 | High |
| failure-recovery.md | 59 | 33 | High |
| elderly-friendly.md | 92 | 41 | Very high |
| state-management.md | 55 | 42 | High |
| security.md | 42 | 46 | High |
| permissions-platform.md | 52 | 31 | High |
| performance.md | 61 | 33 | Very high |

Total: 791 FR/spec citations across 496 CHK items — average ~1.6 citations per item. Plan-level checklists (`*-plan.md`) cite plan.md sections instead of spec FRs, which is correct semantics for plan-level audit.

**Note**: Checklists use heading format `### CHK###` (not `[CHK-NNN]` brackets). All checked items include either a `Finding:` narrative with FR references, or an explicit citation in body. No orphan CHKs found in sampled files.

---

### 6. Deleted-file references: PASS-WITH-NOTE

`AdminDevicesFragment` (mock из спека 7) removal is properly handled:

| Location | Reference | Status |
|----------|-----------|--------|
| `specs/009-admin-mode-flows/tasks.md` | T080 (replaces), T155 (deletes), T156 (grep verifies) | Expected — describes removal |
| `specs/009-admin-mode-flows/spec.md` | line 12 context | Expected — historical context |
| `specs/009-admin-mode-flows/checklists/meta-minimization.md` | reference | Expected — checklist context |
| `docs/product/roadmap.md` | reference | Expected — roadmap context |
| `specs/008-bidirectional-config-sync/checklists/meta-minimization.md` | reference | Expected — historical |
| `specs/003-ui-skeleton/tasks.md`, `spec.md`, `plan.md` | references | Expected — origin spec |

No dangling live-code references. T156 explicitly grep-verifies `app/` + `core/` для no lingering mock references at implementation time. Doc-only references whitelisted.

---

### 7. Task ordering: PASS

Sampled all 66 `Dependencies: requires T###` lines in tasks.md (lines 111-1019). Every dependency references a **strictly lower** T-ID:

- T008 → requires T003-T007 ✓
- T014 → requires T012 ✓
- T020 → requires T014 ✓
- T026 → requires T025, T016 ✓
- T055 → requires T050-T054 ✓
- T067 → requires T060/T062/T063/T064/T065 ✓
- T094 → requires T090/T091/T092/T093/T086 ✓
- T117 → requires T060, T073 ✓
- T132 (no explicit deps listed but logically after T094) — consistent
- T156 → requires T155 ✓
- T165 → requires T106, T121 ✓
- T167 → requires T166 ✓

Zero forward-dependencies found. tasks.md §Overview line 15 explicitly states the invariant («only backward refs»), and the body conforms.

---

### 8. Required context links: PASS

Spec.md and plan.md context references audited:

**Spec.md** — 2 doc references, both markdown links:
- Line 6: `[docs/product/roadmap.md:192](../../docs/product/roadmap.md#L192)` ✓
- Line 410: `[server-roadmap.md](../../docs/dev/server-roadmap.md)` ✓
- Zero ADR mentions in spec.md (intentional — spec is product-level)

**Plan.md** — 16+ doc references in §8 Required Context Review, all markdown links:
- Constitution, CLAUDE.md, ADR-001-*, roadmap.md, senior-safe-launcher-plan.md, permissions-and-resource-budget.md, store-policy-register.md, project-backlog.md, server-roadmap.md, upstream specs 007/008/006, source files Contact.kt/Health.kt/TileCard.kt/FlowScreen.kt/firestore.rules/AndroidManifest.xml ✓
- Line 294: ADR-001-*, ADR-005, ADR-004 — referenced as bare text inside a parenthetical of a link target (`[docs/adr/ADR-001-*.md](../../docs/adr/) — ... ADR-005 (Compose Multiplatform UI stack), ADR-004 (i18n strategy)`). Acceptable: the file containing ADRs has a link; the inline mentions are referring to that linked target. **MINOR WARN**: ADR-005 / ADR-004 не имеют individual file links (можно было бы линковать `[ADR-005](../../docs/adr/ADR-005-compose-multiplatform.md)`).
- Constitution Check table (line 337) and §11 C-4 (line 432) cite ADR-005 as bare text — acceptable since the §8 entry establishes the linked context.

---

## Punch List

1. **[WARN]** FR-012 (preset selector dropdown) — add an explicit acceptance line to T095 covering preset selector UI (`presetId` field switch among workspace/simple-launcher/launcher), or split off a new task T095a. Currently flagged as implicit by tasks.md itself.
2. **[WARN]** FR-046b (distinct hue light/dark severity colors) — add an explicit task for `app/.../theme/severity-colors.kt` creation, or extend T081 acceptance to include creating that file with the FR-046b hex values (green-600/amber-600/red-700 light; green-300/amber-300/red-400 dark). Currently flagged as implicit by tasks.md itself.
3. **[MINOR WARN, optional]** Plan.md §8 line 294 — promote ADR-005 / ADR-004 bare-text mentions to individual markdown links (e.g. `[ADR-005](../../docs/adr/ADR-005-*.md)`) for IDE/preview discoverability. Not blocking — directory link `docs/adr/` already provides ground.
4. **[NOTE — informational, not actionable]** Spec.md FR-014 says `app/health-ui/`; plan.md §11 C-4 reverses to `:core/commonMain/ui/health/`. T026 acceptance correctly follows the plan decision and notes "Spec.md обновится в tasks.md фазе". Either update spec.md FR-014 to match plan, or accept this as a documented one-way-door in C-4. Currently treated as "implementation detail, not API contract" per C-4 — acceptable.

---

## Verdict

**OVERALL: 7/8 PASS, 1 PASS-WITH-WARN, 0 FAIL**

- Check 1 (Spec → Tasks coverage): PASS-WITH-WARN (74/76 explicit, 2 implicit — already self-flagged by tasks.md).
- Checks 2, 3, 4, 5, 6, 7, 8: PASS.

**Spec 009 is READY for `/speckit.analyze`**. The two WARN items (FR-012, FR-046b) are pre-flagged by tasks.md as implicit-via-extension, are not blocking for the cross-artifact audit, and can be either (a) resolved by extending T095/T081 acceptance text or (b) acknowledged as «implementation detail covered at coding time» in the analyze report. No FAILs identified.

---

## Что внутри (TL;DR на русском)

Это **8-check audit traceability** между всеми артефактами спека 9 (spec.md ↔ plan.md ↔ data-model.md ↔ research.md ↔ contracts/ ↔ tasks.md ↔ checklists/).

**Что проверялось:**
1. Покрыты ли все 76 FR/NFR/FR-A11Y задачами в tasks.md.
2. Есть ли у каждой из 7 user stories test evidence (UI/contract/smoke/e2e).
3. Заземлены ли все абстракции plan.md в spec FR (anti-smuggling per Article XII §1).
4. Имеют ли все 3 contract'a (config-history, config-current-additions, vcard-incoming) roundtrip + backward-compat тесты.
5. Цитируют ли пункты checklist'ов конкретные FR/секции спека.
6. Нет ли «висячих» ссылок на файлы, помеченные на удаление (mock `AdminDevicesFragment` из спека 7).
7. Все ли task'и в tasks.md имеют только backward dependencies (T020 → T012, никогда T012 → T020).
8. Сделаны ли упоминания ADR / docs/ markdown-ссылками (а не голым текстом).

**Результат: 7/8 PASS + 1 PASS-WITH-WARN, 0 FAIL.**

**Что найдено:**
- 2 FR (FR-012 preset selector, FR-046b distinct hue severity colors) покрыты **неявно** — tasks.md сам это честно отмечает. Не блокер, но рекомендация — расширить acceptance text у T095 и T081 / создать explicit task.
- 1 минорное предложение — поднять упоминания ADR-005/ADR-004 в plan.md §8 до individual markdown-ссылок (сейчас линкуется директория `docs/adr/`).
- 1 информативная нота — расхождение между spec FR-014 (`app/health-ui/`) и plan §11 C-4 (`:core/commonMain/ui/health/`); plan-decision принят, spec можно обновить или оставить как documented one-way door.

**Вердикт**: спек 9 **готов для `/speckit.analyze`**. FAIL'ов нет; WARN'ы заранее self-flag'нуты в tasks.md и могут быть устранены либо парой строк в acceptance text, либо проигнорированы как implementation detail.

**Следующий шаг**: `/speckit.analyze` (финальный pre-implementation audit с re-run всех checklist'ов).
