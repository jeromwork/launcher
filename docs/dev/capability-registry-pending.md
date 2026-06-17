# Capability Registry — Pending Actions Index

> **Цель этого файла**: список всех actions, объявленных в S-1..S-8 (Phase 2), которые F-2 (Capability Registry Foundation, последний шаг Phase 2) должен собрать в Capability declarations.
>
> **Создан**: 2026-06-15 при roadmap reorder, который отложил F-2 в конец Phase 2.
>
> **Как пополняется**: каждая S-спека, добавляющая новый action / intent / external-callable surface, обязана добавить строку в таблицу ниже. Проверяется через `checklist-capability-registry-readiness` skill.
>
> **Когда читается**: при сборке F-2 — этот файл становится input'ом для генерации Capability declarations.

---

## Pending actions

| Action intent name | Source spec | Description (1 line) | Auth scope | Idempotent | Confirmation? | Voice-triggerable? |
|--------------------|-------------|----------------------|------------|------------|---------------|---------------------|
| `wizard.start` | 015 (F-3) | Re-runs the first-run wizard for the active app-family. | device-local | true | true | false |
| `wizard.skipToStep` | 015 (F-3) | Jumps the running wizard to a specific step refId. | device-local | true | false | false |
| `localization.resolve` | 015 (F-3) | Look up a string by key in current locale. | device-local | true | false | false |
| `tileSet.list` | 015 (F-3) | List bundled tile sets the wizard exposes. | device-local | true | false | false |
| `systemSettings.applyOrPrompt` | 015 (F-3) | Apply / prompt for an Android system setting (ROLE_HOME, POST_NOTIFICATIONS, etc.). | device-local | false | true (for permission asks) | false |

---

## Conventions

- **intent name**: stable slug-cased (`call_contact`, `trigger_emergency`, `open_app`). F-2 будет использовать verbatim как capability key. **Не переименовывать** после того, как action вышел в релиз.
- **auth scope**: `device-local` / `admin-only` / `pair-authorised` / `caregiver-allowed`.
- **idempotent**: `true` для безопасно-повторяемых (call, navigate, open). `false` для destructive (delete, trigger_emergency, payment).
- **confirmation**: `true` для destructive / irreversible.
- **voice-triggerable**: если `true` — F-2 потребует `voicePhrases: Map<Locale, List<String>>` при сборке declaration.

## Related

- Roadmap: F-2 Capability Registry Foundation (`docs/product/roadmap.md` Часть IV).
- Skill: `.claude/skills/checklist-capability-registry-readiness/SKILL.md`.
- Skill: `.claude/skills/checklist-ai-readiness/SKILL.md`.
- CLAUDE.md rule 4 (Minimum Viable Architecture).
