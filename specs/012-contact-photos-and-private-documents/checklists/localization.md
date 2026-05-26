# Checklist: localization

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 4/20 ✓ + 15 deferred-to-plan + 1 open

---

## Strings

- [x] **CHK001** — все user-facing strings externalised: ✓ spec-level commitment
  - **SC-011**: 100% строк UI (DocumentViewer header, indicator у admin'а, error messages, picker entry button) локализованы ru + en через `strings.xml` (ADR-004 compliance).
- [ ] **CHK002** — string ID naming convention: deferred-to-plan
  - Suggested: `private_media_doc_viewer_close`, `private_media_admin_indicator_decrypt_failed`, `private_media_add_document_button`, etc.
- [ ] **CHK003** — single-use strings externalised: deferred-to-plan (covered by SC-011).
- [ ] **CHK004** — translator notes: deferred-to-plan
  - **Особо важно** для: «Пере-добавить» (re-add) vs «Повторить pairing» (re-pair) — два разных recovery action, бабушка-friendly translation required.

## Plurals

- [x] **CHK005** — count-dependent strings via plurals: ✓ N/A в спеке 012 (нет count-dependent UI на спек-уровне).
- [x] **CHK006** — plural categories для русского: ✓ N/A.

## Format

- [x] **CHK007** — date formatting locale-aware: ✓ N/A (spec 012 не показывает даты).
- [x] **CHK008** — number formatting: ✓ N/A (spec 012 не показывает чисел).
- [x] **CHK009** — currency/unit: ✓ N/A.

## RTL (right-to-left)

- [ ] **CHK010** — start/end vs left/right: deferred-to-plan
  - DocumentViewer label position, кнопка «Закрыть» позиция — нужно использовать `Modifier.align(Alignment.End)` etc.
- [ ] **CHK011** — directional drawables auto-mirror: deferred-to-plan
  - Back arrow в admin upload progress retry, если есть.
- [x] **CHK012** — custom layouts RTL: ✓ N/A
  - DocumentViewer — fullscreen photo, pinch/pan — RTL-agnostic.
  - **Pinch-to-zoom + pan** работает одинаково в LTR/RTL (gesture coordinates absolute).

## Images / non-text content

- [x] **CHK013** — no language-baked images: ✓
  - Все imagery — user-provided (фото контактов, фото документов). Текст добавляется через label, отдельно от image.
- [x] **CHK014** — per-locale image resources: ✓ N/A.

## Truncation / expansion

- [ ] **CHK015** — ~30% text expansion test: deferred-to-plan
  - **Особо**: label плитки-документа в немецком / русском может быть длинным («Versicherungspolice», «Страховой полис», «Медицинская карта»). Layout должен wrap, не truncate.
- [ ] **CHK016** — no fixed-width buttons clipping labels: deferred-to-plan.

## Locale change

- [ ] **CHK017** — runtime locale change behaviour: deferred-to-plan
  - Стандартный Activity recreation pattern; spec 012 ничего specific не вводит.
- [ ] **CHK018** — in-app language switcher: ✓ N/A
  - Spec 012 не вводит switcher; наследует foundational settings.

## Accessibility-localisation overlap

- [ ] **CHK019** — contentDescription localised: deferred-to-plan
  - Обязательно: contentDescription плитки контакта = displayName (уже локализованный input).
  - contentDescription плитки-документа = label (уже локализованный input).
  - contentDescription «+ документ» button = localised string.
- [ ] **CHK020** — TalkBack reads localised correctly: deferred-to-plan
  - No string concatenation в спеке 012; полные предложения уходят в strings.xml.

---

## Adjacent concern — user-provided text (label / displayName) не локализуется

**Что важно понять** для plan-phase:
- `Contact.displayName` (имя контакта) — user input на admin language. **Не локализуется** автоматически. Если admin записал «Маша» — у бабушки тоже «Маша».
- `Tile.label` (подпись документа, «Паспорт») — user input. Тот же rationale.
- Локализации подлежат **только app chrome** (button labels, error messages, indicator text), не user content.

Это **правильно и ожидаемо**, но стоит явно документировать в `strings.xml` translator notes.

---

## Open item

**CHK004 + CHK019**: при формировании финальных строк в plan-phase убедиться, что:
- `private_media_admin_indicator_decrypt_failed` использует placeholder `%1$s` для display name бабушки («фото не отрисовалось у %1$s»), сам display name не локализуется.
- Recovery action labels «Пере-добавить» / «Повторить pairing» — короткие, понятные бабушке-friendly слова. **Translator note**: «эти кнопки видит admin (не бабушка), но язык — простой».

---

## Summary

| Status | Count |
|---|---|
| ✓ | 4 (CHK001 SC-level commitment + 3 N/A) |
| deferred-to-plan | 15 (string IDs, RTL implementation, contentDescription strings) |
| ⚠️ open | 1 (CHK004 translator notes for ambiguous strings) |
| ✗ violations | 0 |

**Verdict**: спек 012 имеет **явный commitment** через SC-011 (100% ru + en externalised). Реальная работа — plan-phase (определение string IDs, translator notes, content descriptions, RTL-readiness layout). Эта checklist — план работ для plan-phase, без spec-level violations.

**Constitution alignment**: ADR-004 ✓.
