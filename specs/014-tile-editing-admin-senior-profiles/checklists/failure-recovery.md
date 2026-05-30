# Failure Recovery — spec 014

Generated: 2026-05-29.

## Error categories

- [x] **CHK001** Failure modes на FR-уровне:
  - FR-001 ops → `EditError` variants (InvalidPosition, SlotNotFound, FlowNotFound, ConcurrentEditConflict, NotAuthorized, ProfileSelectionRequiresCapabilityRegistry).
  - FR-002 `ConfigEditor` → существующие `ConfigSyncError` variants (per спека 008).
  - FR-016 Concurrent conflict → explicit Q7 resolution (profile-aware).
  - FR-003c 5-config limit → explicit refuse error.
  - FR-018 Widget/Action placeholder → "В разработке" screen (not crash).
- [x] **CHK002** User-visible behaviors specified: snackbar (FR-010 undo, FR-016 admin conflict), modal dialog (FR-003c limit prompt), full-screen "В разработке" (FR-018a), bottom sheet (US1 AS1).
- [x] **CHK003** No silent failures для user-initiated. **Exception**: senior-side concurrent conflict — silent last-local-write-wins per Q7. **Это deliberate** (Article VIII §7 cognitive load) — не "silent failure", а silent **success** на senior-side; admin получает explicit notification.

## Fallbacks

- [x] **CHK004** Profile selector fallback: built-in unknown → AdminProfile (FR-008b), custom preset → explicit error. Bounded.
- [x] **CHK005** Fallback в data shape (FR-008b — selector function), не hardcoded дispatch.
- [x] **CHK006** Terminal behavior: `ProfileSelectionRequiresCapabilityRegistry` → presentation shows "Custom presets появятся в будущих обновлениях" screen. Defined.

## Retries

- [x] **CHK007** Retry для conflict: explicit user action ("Обновить" / "Перезаписать"). Auto-retry для offline push — existing ConfigEditor mechanism (per спека 008 `pendingDraft` queue).
- [x] **CHK008** No infinite loops. Conflict resolution требует explicit user choice (admin) или silent succeed (senior local).
- [x] **CHK009** Idempotency: `addSlot/removeSlot/moveSlot/replaceSlot` — idempotent ops at ConfigDocument level (apply same op twice = same result), `pushPending` уважает version vector per спека 008.

## Offline / degraded modes

- [x] **CHK010** Offline: Edge Cases раздел явно — "admin меняет target бабушкин config offline → изменения сохраняются в `LocalConfigStore.pending` (спека 008) → при появлении сети push'ится автоматически". PASS.
- [x] **CHK011** Stale data TTL: ConfigDocument cached locally, sync best-effort per F-014.1 (after F-4). TTL не специфицирован (always-fresh при network available, last-known offline). Acceptable.

## Permissions denied

- [⚠️] **CHK012** F-014 не вводит новых runtime permissions. Existing dependencies (CONTACTS per спека 011, etc.) handled там. **Improvement**: edge case "permission denied" для existing dependencies упомянут в одной строке Edge Cases ("missing app"), но не expanded. Acceptable, governed parent specs.
- [N/A] **CHK013** No new permissions in F-014, "don't ask again" не applicable.

## Recovery from invalid state

- [⚠️] **CHK014** Corrupt ConfigDocument: existing ConfigEditor handles per спека 008 (`stale version → retry`). F-014.1 schema v1→v2 migration mid-flight failure — не специфицирован. **Improvement** для plan.md: define если v1 parse fails в F-014.1, fallback to empty (with logging).
- [x] **CHK015** No "crash and restart" — placeholder "В разработке" screens, snackbars, dialogs — все handled gracefully.

## Diagnostics

- [⚠️] **CHK016** Diagnostic events не явно specified в spec. ConfigEditor existing emits per спека 008. F-014 specific events (profile selection, edit mode entry/exit, picker tab change) — должны иметь Logcat tags. Plan.md.
- [⚠️] **CHK017** Aggregation: `EditError` variants — discrete categories (not unique strings). PASS conceptually. Logcat aggregation depends on tagging discipline.

## Concurrent edit conflict — detailed coverage

Особое внимание (per Q7 + FR-016/FR-017):
- Admin push pendingDraft → Firestore optimistic concurrency rejects → `ConfigSyncError.Conflict`.
- Admin profile UI: snackbar "[Обновить] [Перезаписать]". Force push через `pushPending(force=true)`.
- Senior profile UI: NO conflict dialog. Бабушкин local edit applies first, admin'у возвращается conflict, admin видит snackbar **post-hoc** "Бабушка только что изменила. [Обновить]" — **без "Перезаписать"**.
- Edge case "Concurrent edit conflict" updated per Q7.
- **PASS** — coverage complete после Q7 resolution.

## Open items

1. **CHK014**: v1→v2 mid-flight parse failure recovery — plan.md.
2. **CHK016**: Diagnostic event taxonomy + Logcat tags — plan.md.

**Verdict**: PASS с 2 open items для plan. Failure modes well-covered после Q7 resolution.
