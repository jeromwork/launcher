# Checklist: ux-quality

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 15/22 ✓ + 4 deferred-to-plan + 3 open

---

## Inventory of UX surfaces в спеке 012

| # | Surface | Owner | Audience |
|---|---|---|---|
| S-1 | Admin: «+ документ» button в редакторе раскладки | new | admin |
| S-2 | Admin: label entry form (после picker'а) + thumbnail preview | new | admin |
| S-3 | System Photo Picker (3 ветки API-dispatch) | system / new adapter | admin |
| S-4 | Admin: upload progress (loader + «нет сети, попробую снова» retry) | new | admin |
| S-5 | Admin: failed-decrypt indicator у контакта / документа («фото не отрисовалось у …» + re-add / re-pair hint) | new | admin |
| S-6 | Managed: плитка контакта с аватаром (placeholder OR фото) | new | бабушка |
| S-7 | Managed: плитка-документ (thumbnail OR placeholder) | new | бабушка |
| S-8 | Managed: DocumentViewer fullscreen | new | бабушка |
| S-9 | Admin: VCard share intent handler — receives WhatsApp/Telegram share | shared with spec 009 | admin |

---

## Completeness — coverage of screens

- [x] **CHK001** — every user-facing screen listed: ✓ (см. inventory выше).
- [ ] **CHK002** — every UX state per screen
  - **Status**: ⚠️ partial. Specified в спеке:
    - S-6 (плитка контакта): фото OR placeholder с инициалом (FR-014) ✓
    - S-7 (плитка-документ): thumbnail OR placeholder (FR-014 by extension) — **не явно проговорено для документа**.
    - S-8 (DocumentViewer): loading (FR-020), success (FR-018), error → не описан явно (placeholder в viewer?).
    - S-4 (upload progress): success (плитка появляется), error («нет сети, попробую снова» — edge case 3) ✓
    - S-5 (failed-decrypt indicator): success (нет indicator'а) / failure (indicator + hint) ✓
  - **Действие**: plan-phase явно проговорить error state для S-8 (что показывает DocumentViewer, если файл удалили из LocalMediaStore между tap'ом и open'ом — placeholder + retry button или просто закрытие?).
- [ ] **CHK003** — navigation transitions
  - **Status**: ⚠️ deferred-to-plan.
  - Forward: tap плитки-документа → DocumentViewer. ✓ FR-018.
  - Back: «Закрыть» button ≥ 56dp (FR-018) + system back. ✓
  - Deep-link entry: N/A (нет deep-link'ов в 012).
  - Recreation (rotation, process death): **не покрыто явно** (после Q5 — открытие из LocalMediaStore мгновенное, recreation не проблема).
  - **Действие**: plan-phase зафиксировать «DocumentViewer state = `documentRef` через `rememberSaveable`; bytes никогда не в parcel'е».
- [x] **CHK004** — overlays (snackbar/toast/dialog/bottom sheet): ✓
  - Upload progress — embedded loader, не overlay.
  - «Нет сети» — embedded retry control, не overlay (edge case 3 — формулировка «retry button», implementation как Snackbar OR inline — plan-phase).
  - Никаких dialog'ов «обновить или добавить» (после Q1).

## Clarity — terminology and rules

- [x] **CHK005** — UX terms unambiguous: ✓
  - «плитка», «аватар», «placeholder», «DocumentViewer», «admin indicator», «label» — использованы последовательно.
- [x] **CHK006** — vague qualifiers operationalised: ✓
  - «крупный шрифт» — quantified через ≥ 56dp tap-target (FR-014, FR-018) + ≥ 4.5:1 контраст.
  - «мгновенно» — ≤ 100 мс (FR-004, SC-003).
  - «работоспособна» — tap target ≥ 56dp, не crash.
- [x] **CHK007** — action vocabulary explicit: ✓
  - «admin тапает», «бабушка тапает», «admin жмёт +документ», «pinch-to-zoom», «pan». Все actions явные.
- [ ] **CHK008** — button labels exact strings
  - **Status**: ⚠️ deferred-to-localization-checklist + plan-phase.
  - Кнопки упомянуты семантически («Закрыть», «Понятно», «+ документ»), но точные строки + i18n ключи не зафиксированы.
  - **Действие**: see checklist-localization.

## Consistency

- [x] **CHK009** — In-Scope ↔ FR alignment: ✓
  - Каждая US имеет FR'ы (FR-011..014 → US-1, FR-015..020 → US-2, FR-021..022 → US-3, FR-023 → US-4).
  - Out-of-Scope items не имеют FR (verified).
- [ ] **CHK010** — confirmation policy
  - **Status**: ⚠️ open.
  - **Confirm action**: admin подтверждает «+ документ» после picker + label entry (FR-016 «Подтверждение → upload»).
  - **No-confirm**: implicit auto-update photoRef при повторном контакте (FR-013) — это **намеренный** no-confirm policy (см. Clarification Q1, future merge dialog → `TODO-ARCH-018`).
  - **Действие**: явно зафиксировать в plan-phase: «implicit-update НЕ требует confirm — это design decision Q1, не gap».
- [x] **CHK011** — multi-tap / accidental double-tap protection: ✓
  - «+ документ» — после tap'а launchится picker (system intent), double-tap defended natively.
  - DocumentViewer pinch-to-zoom — gesture, не tap.
  - «Закрыть» в viewer — one-tap, повторный tap = no-op (viewer уже закрыт).

## Acceptance — measurability

- [x] **CHK012** — каждая US имеет GWT: ✓ (см. CHK010 из requirements-quality — все 4 US с 3+ scenarios).
- [x] **CHK013** — SC measurable per UX moment: ✓
  - Entry-to-first-tap: SC-001 / SC-002 (≤ 30s от admin action до видимости у бабушки).
  - Tap-to-feedback: SC-003 (≤ 100 мс repeat, ≤ 3s first download).
  - Action-to-result: SC-005 (≤ 5 min cleanup после delete).
- [ ] **CHK014** — returning-user UX (second-launch, resume from background)
  - **Status**: ⚠️ open.
  - Resume from background → бабушка открывает приложение → видит плитки. Если фото уже в LocalMediaStore → мгновенно. Если нет (новые добавления pushed в офлайне) → download начнётся при первом show.
  - **Действие**: plan-phase явно описать «при возврате в foreground — нет принудительного re-fetch; download lazy per-tile».

## Coverage — alternative paths

- [x] **CHK015** — negative-path UX per primary action: ✓
  - Upload negative-path: «нет сети, попробую снова» retry button (edge case 3).
  - Decrypt negative-path: placeholder + admin indicator (US-3).
  - Picker negative-path: user cancelled = no-op (native).
  - Picker MIME mismatch: rejected без crash (FR-009; **wording не указан**).
- [x] **CHK016** — multiple entry points consistent: ✓
  - VCard share intent (entry 1) и `ACTION_PICK` системной книжки (entry 2) → одинаковый downstream (`PrivateMediaUploader`). ✓
- [ ] **CHK017** — long-pause scenarios
  - **Status**: ⚠️ implicit.
  - Бабушка ушла на 3 дня → возвращается → новые pushed контакты с фото → первый show → download. ✓ (covered through normal Resolver flow).
  - **Действие**: plan-phase зафиксировать «long-pause UX = normal lazy resolver flow, без bulk re-fetch на foreground».

## Non-functional UX

- [x] **CHK018** — accessibility deferred to checklist-accessibility: ✓
- [x] **CHK019** — localization deferred to checklist-localization: ✓
- [x] **CHK020** — diagnostic UX
  - **S-5 admin indicator** — это и есть diagnostic UX для бабушка-стороны. ✓
  - Внутренних app-side metrics для admin'а (storage usage у бабушки) — out of scope ([`TODO-ARCH-019`](../../docs/dev/project-backlog.md)).

## Dependencies / assumptions

- [x] **CHK021** — UX doesn't depend on out-of-scope: ✓
  - Не зависит от cross-app control (только system picker).
  - Не зависит от embedded other-app UI.
- [x] **CHK022** — mock-data limitations noted: ✓ N/A (нет mock в production UX).

---

## Summary

| Status | Count |
|---|---|
| ✓ | 15 |
| deferred-to-plan / другому-checklist | 4 (CHK003 recreation, CHK008 strings → localization, CHK018/019) |
| ⚠️ open | 3 (CHK002 DocumentViewer error state, CHK010 confirm policy doc, CHK014 long-pause UX) |
| ✗ violations | 0 |

**Open items для plan-phase**:
1. **CHK002**: DocumentViewer error state — что показывается, если файл пропал из LocalMediaStore между tap'ом и open'ом.
2. **CHK010**: явно задокументировать «implicit-update photoRef без confirm = намеренный Q1 design», не gap.
3. **CHK014**: long-pause UX явное правило: lazy resolver flow, без bulk re-fetch на foreground resume.

**Verdict**: UX-story в спеке 012 покрывает 9 surfaces, 4 user stories с GWT-scenarios, явные negative paths. Visual design — plan-phase (Compose-конкретика). Все open items — minor doc-clarifications.

**Constitution alignment**: Article VIII (Accessibility and Elderly-First UX) — defer to `checklist-accessibility` + `checklist-elderly-friendly`.
