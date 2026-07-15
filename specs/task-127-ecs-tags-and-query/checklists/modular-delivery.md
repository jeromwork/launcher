# Checklist: modular-delivery

Applied: 2026-07-15
Spec: `specs/task-127-ecs-tags-and-query/spec.md`

## Scope of the feature

- [x] CHK001 Form-factor-agnostic — Tag enum + Query API + Repository swap works on any form factor (handheld MVP, future TV/voice inherit same Profile).
- [x] CHK002 N/A — not form-factor-specific.
- [x] CHK003 NFR-001: pure Kotlin, no Android imports on new types. No form-factor assumptions leak.

## Module placement

- [x] CHK004 No new vendor SDK.
- [x] CHK005 No new Gradle module introduced.
- [x] CHK006 No "form-factor-specific in Core for now" pattern applied.

## Profile / preset declaration

- [ ] CHK007 PARTIAL — spec adds `Component.tags` field to Profile but does not touch `requiredModules`/`optionalModules`. Not applicable to this spec (additive field, not new profile). N/A.
- [x] CHK008 Profile schema bump v2→v3 with migration writer (FR-004). Backward-compat plan present.
- [x] CHK009 Existing profiles must still load: FR-004 migration ensures v2 fixtures read cleanly with tag defaults. Empty tags produce empty query result — degradation is "component not findable by query" (valid per fitness function in Edge Cases).

## Form-factor expansion

- [x] CHK010-012 N/A — no non-handheld form factor introduced.

## One-way doors raised

- [x] CHK013 Additive change: new field + new methods. Reversible in days if needed (drop tags field, revert DI binding). Tag enum additive-only per rule 5, no removal migration needed.
- [x] CHK014 No new external SDK.
- [x] CHK015 FR-010 mandates SRV-CONFIG-DEPRECATION entry in `docs/dev/server-roadmap.md` for future ConfigDocument removal — explicit tracking per CLAUDE.md rule 8.

## Anti-bloat sanity

- [x] CHK016 No new Gradle module. Types live in existing `core/preset/model/`.
- [x] CHK017 No pre-emptive split.
- [x] CHK018 SRV-CONFIG-DEPRECATION is a regret condition (recorded, not implemented today).

**Result**: 17/18 passed, 1 N/A (CHK007 requiredModules not applicable to additive field). Modular hygiene solid.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Проверили что фича form-factor-agnostic + не bloat'ит core + one-way doors записаны в roadmap. 17/18 pass. Всё чисто: `Tag` enum + Query API + `ProfileBackedFlowRepository` работают на любом form factor'е (handheld MVP, future TV/voice). Никаких новых Gradle модулей, никаких vendor SDK.

**Конкретика, которую стоит запомнить:**
- CHK015 — `FR-010` mandates SRV-CONFIG-DEPRECATION запись в `docs/dev/server-roadmap.md` per CLAUDE.md rule 8.
- CHK013 — additive change, revertable за дни (drop tags field + revert DI binding). Tag enum additive-only per rule 5.
- CHK007 N/A — новое поле, не новый profile.

**На что смотреть с осторожностью:**
- SRV-CONFIG-DEPRECATION — regret condition, не implementation today. Не забыть создать соответствующую future task когда `ConfigDocument` действительно готов удалить.
