# Contracts — Spec 005

Launcher-owned contracts for Action dispatch architecture.

| Document | Version | Purpose |
|----------|---------|---------|
| [action-wire-format.md](./action-wire-format.md) | 1.0.0 | Canonical wire format for `Action` — used by mock JSON assets, future backend sync (spec 007), future QR share (spec 010). |
| [diagnostics-events-v2.md](./diagnostics-events-v2.md) | 2.0.0 | Diagnostic event taxonomy emitted by `AndroidActionDispatcher`. Replaces `CommunicationDiagnostics` from spec 002. |

## Compatibility policy

- Contracts use **launcher-owned semantic versions** (independent from app version).
- **Patch** (`1.0.x`): clarifications, doc fixes — no code change required.
- **Minor** (`1.x.0`): additive change (new payload variant, new optional field). Older readers ignore unknown fields.
- **Major** (`2.0.0`): breaking change. Requires migration written before the breaking change ships per CLAUDE.md §5.
- Each major bump ships with: a migration function (`migrateLegacyAction` style), a backward-compat test fixture, and an explicit removal-deadline-spec for the migration bridge.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** В этой папке два контракта от spec 005: `action-wire-format` v1.0.0 (формат `Action` в JSON) и `diagnostics-events-v2` v2.0.0 (что эмитится в `EventRouter` при dispatch). Оба используют semver, независимый от версии приложения.

**Конкретика, которую стоит запомнить:**
- Patch (`1.0.x`) = только правки документации, код не трогаем.
- Minor (`1.x.0`) = добавили поле или вариант, старые читатели игнорируют новое — не ломаются.
- Major (`2.0.0`) = ломающее изменение; **обязательно** написать миграцию до того, как breaking change уйдёт в релиз.
- Каждый major-bump поставляется с миграционной функцией стиля `migrateLegacyAction`, бэкомпат-фикстурой и явным «спеком-дедлайном» удаления моста.

**На что смотреть с осторожностью:**
- Любое изменение полей в этих файлах = bumpa-up версии. Если кто-то правит без bump — это автоматическая причина отклонить PR.
