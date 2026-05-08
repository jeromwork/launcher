---
name: procedure-add-novice-summary
description: Append a plain-Russian TL;DR summary of WHAT'S INSIDE the artifact at its bottom — not a meta-description of "what kind of document this is". The summary should let a non-Android-developer quickly grasp the actual content (decisions taken, key entities, risks identified, tasks ordered) without reading the body, and serve as a quick-reload for future AI agents. All speckit-* orchestrators must invoke this on every artifact they produce.
---

# Procedure: add-novice-summary

Adds a final section that **summarises the file's actual content** in plain Russian, suitable for both:
- a non-Android-developer human (so they can grasp the substance without reading the full file),
- a future AI agent re-loading context (so it can rebuild a working mental model from one section).

Project language is Russian (memory `feedback_language_russian.md`).

---

## What this is NOT

- ❌ NOT "what kind of document this is" ("это план", "это спек", "это финальная сверка").
- ❌ NOT "why such documents are important in general".
- ❌ NOT "what you should do with this document" (review, approve, commit).
- ❌ NOT generic spec-kit education ("плана нужны для архитектуры").

If the section can be reused unchanged in another spec — it's wrong. Each summary is **about this specific file's content**.

---

## What this IS

A **content TL;DR**: «вот о чём в этом файле договорились / что в нём решили / какие сущности появились / какие риски выявили / какой порядок задач».

A reader of the summary alone should be able to answer:
- «Что реально решили в этом файле?»
- «Какие имена / числа / флаги тут зафиксированы?»
- «Какие места требуют осторожности на ревью?»

---

## When to apply

- ✅ `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`
- ✅ Every file in `contracts/` (including `README.md`)
- ✅ `tasks.md`, `analyze-report.md`, `perf-checkpoint.md`, `smoke-checkpoint.md`
- ✅ `checklists/_overview.md` and individual checklists with meaningful open items
- ❌ Source code files (`.kt`, `.json`, `.xml`)
- ❌ Other skill files (`SKILL.md`)

---

## Format

Append at the very end:

```markdown
---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** {1–2 предложения: главное, что в файле решено / зафиксировано / описано — конкретно, с именами и числами.}

**Конкретика, которую стоит запомнить:**
- {фактический пункт 1 — имя сущности, число, флаг, решение}
- {фактический пункт 2}
- {3–6 пунктов всего; одно конкретное решение / число / имя на пункт}

**На что смотреть с осторожностью:**
- {риск/острый угол 1 — конкретный пункт из этого файла, не общая фраза}
- {риск/острый угол 2}
- {1–3 пункта максимум}
```

---

## Style rules

- **Конкретика, не абстракция.** Не «решили про обработку ошибок», а «`DispatchResult.Failure` показывается снэкбаром с кнопкой "Повторить" (T595)».
- **Имена и числа сохраняем.** `ProviderId`, `schemaVersion: 1`, «8 обработчиков», «44 задачи в 9 фазах», «≤ 600 мс». Это якоря, по которым потом ищется.
- **Без жаргона без расшифровки.** «Konsist test» → «автопроверка в CI»; «expect/actual» → не упоминать; «commonMain» → «общий код для всех платформ» при первом упоминании.
- **Без «вы», на «ты»**. Без эмодзи. Без таблиц в этой секции.
- **Длина**: вся секция 8–18 строк. Если не вмещается — выкидывай менее важное, не растягивай.
- **Если файл изменился** (повторный прогон через speckit-* после правок): обнови TL;DR, не дописывай ниже.

---

## Examples

### Хорошо — для `data-model.md`:

> ## TL;DR (по-русски, для новичка и для будущего AI)
>
> **Суть.** Описаны новые типы данных: главный — `Action(schemaVersion, providerId, payload, fallback)`. Сейчас в коде вместо него ad-hoc `ActionRequest` с тремя WhatsApp-специфичными вариантами; новый `Action` — один тип на все 7+ провайдеров.
>
> **Конкретика, которую стоит запомнить:**
> - `ProviderId` — value class над String, не enum (8 константных значений: `APP`, `WHATSAPP`, `TELEGRAM`, `PHONE`, `SMS`, `BROWSER`, `YOUTUBE`, `SYSTEM_SETTINGS`).
> - `ActionPayload` — sealed class с 9 типизированными вариантами + `Custom(key, params: Map<String,String>)` как escape-hatch.
> - `DispatchResult` — `Ok | BlockedByPolicy | ProviderUnavailable(providerId, hint) | Failure(reason)`. Никаких WhatsApp-специфичных вариантов.
> - Максимальная глубина fallback-цепочки = 2; глубже → `Failure`.
> - `ProjectEvent.ActionDispatched` — ровно 4 поля и больше никогда (защищено автотестом).
>
> **На что смотреть с осторожностью:**
> - Регекс `[a-z][a-z0-9_-]{1,31}` для `ProviderId.value` — поменять = поломать совместимость старых JSON.
> - `Custom.params` лимиты (16 ключей, 64/1024 длины) валидируются до dispatch — пропустить = риск Parcel-DoS.

### Плохо (так не делать):

> ## Что это значит (по-русски, для новичка)
>
> **О чём этот файл.** Это «словарь типов» — описание всех новых структур данных…
>
> *(Это мета-описание «что за документ». Не говорит, какие конкретно типы появились, какие у них поля, какие там цифры. Бесполезно.)*

---

## Special cases

- **`spec.md`** — Суть = 2–3 предложения о том, что фича делает + границы scope. Конкретика = ключевые US/FR номера, имена новых сущностей, имена удаляемых файлов. Опасности = the actual one-way doors из §5 (по конкретике), а не «есть критические решения».
- **`tasks.md`** — Суть = «44 задачи в 9 фазах, MVP slice = Phase 3 (handlers)». Конкретика = имена фаз, ключевые задачи (T501 = `<queries>`, T611 = grep-проверка), фитнесс-функции по номерам. Опасности = порядок (Phase 6 удаления только после Phase 3 wiring) — конкретно.
- **`research.md`** — Конкретика = по 1 строке на каждое R-решение: «выбрали X, потому что Y». Не пересказывать всю таблицу альтернатив.
- **`contracts/*.md`** — Суть = что за формат + версия. Конкретика = поля корня, варианты (по именам), правила forward/backward. Опасности = что нельзя менять без major-bump.
- **`checklists/_overview.md`** — Суть = N PASS / M PASS-WITH-CAVEATS / K FAIL. Конкретика = имена тех, что не зелёные, и почему. Опасности = открытые items, ещё не разрешённые в плане.
- **`analyze-report.md`** — Суть = вердикт + цифры (Constitution 8/8, чеклисты переведены в зелёные). Конкретика = что изменилось со времени clarify (было 4 PASS-WITH-CAVEATS → стало 0). Опасности = items, помеченные ⚠ в trace.

---

## Output

In-place append to the artifact. Caller (speckit-* orchestrator) invokes once per artifact after the body is finalised.
