# Checklist: capability-registry-readiness

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 9 ✓ / 4 ⚠ / 1 ✗ — один real gap (capability-registry-pending.md entry missing)

> **Context**: F-3 introduces 4 declared capabilities в AI Affordance section + множество SystemSettingPort entries. F-2 (Capability Registry) defer'нут до конца Phase 2 — F-3 должна leave sewing points.

---

## Sewing-point bookkeeping

- [✓] **CHK-CR-001** Inline TODO for every new action.
  - AI Affordance section имеет explicit TODO:
    ```
    // TODO(capability-registry): F-3 wizard / localization / tileSet operations
    // exposed as domain ports. F-2 (Capability Registry Foundation, end of Phase 2)
    // will wrap these into AI-callable capability descriptors. No vendor SDK now.
    ```
  - ✓

- [⚠] **CHK-CR-002** TODO at dispatcher/provider site.
  - F-3 TODO at spec-level. Code-level TODOs не exist yet (foundation, импементация в plan.md).
  - **Recommendation**: при implementation, добавить TODOs у `WizardEngine`, `ConfigSource`, `StringResolver`, `SystemSettingPort` port declarations.

- [⚠] **CHK-CR-003** Intent names stable / slug-cased.
  - F-3 AI Affordance uses dot-notation: `wizard.start`, `wizard.skipToStep`, `localization.resolve`, `tileSet.list`.
  - F-2 capability keys likely будут slug-cased (`wizard_start`, `localization_resolve`, etc.).
  - **Acceptable**: dot-notation → underscore conversion trivial. F-2 normalizer handles.

- [⚠] **CHK-CR-004** Typed params.
  - `wizard.start(appFamilyId: String)` — raw String (could be typed wrapper).
  - `wizard.skipToStep(stepId)` — typed.
  - `localization.resolve(key: String, args: Map<String, Any>)` — untyped Map.
  - `tileSet.list(deviceClass)` — typed.
  - **Recommendation**: `args: Map<String, Any>` — acceptable для StringResolver semantics (string format arguments могут быть number / string / date — heterogeneous by definition). Other capabilities — рекомендуется typed wrappers (`AppFamilyId(String)` value class).
  - **Acceptable**: foundation defer.

## Provider neutrality

- [✓] **CHK-CR-005** No specific AI provider в normative text.
  - AI Affordance pure abstract, no Gemini / OpenAI / Claude / MCP / Assistant App Actions / iOS Shortcut / Alexa mentions в capability descriptions.
  - Translation pipeline references Claude API — но это **dev-time tool** (FR-031a), не runtime capability exposure. ✓

- [✓] **CHK-CR-006** No AI SDK в dependency list.
  - F-3 не imports anthropic / openai / gemini / mcp SDKs в app build. ✓

- [✓] **CHK-CR-007** No exposure adapter implementation.
  - Только inline TODO про F-2 wrapping. Real ExposureAdapter — отдельная спека после F-2. ✓

## Voice / conversational surface

- [N/A] **CHK-CR-008** Voice-triggerable phrases.
  - F-3 capabilities — administrative (wizard control, string lookup, list options), не voice-natural.
  - **Relevant для future**: `SystemSettingPort.applyOrPrompt` capabilities (например, «дай разрешение на звонки») могли бы быть voice-natural. F-2 wrapping добавит voicePhrases когда понадобится.

- [N/A] **CHK-CR-009** Confirmation для destructive.
  - F-3 capabilities reversible / idempotent. Нет destructive.

## Auth / scope hints

- [⚠] **CHK-CR-010** Auth scope в TODO.
  - F-3 capabilities — все `device-local`. Не explicit в TODO.
  - **Recommendation**: дополнить inline TODO в AI Affordance section: «auth scope: device-local for all F-3 capabilities; no admin / paired / caregiver required».

- [⚠] **CHK-CR-011** Idempotency declared.
  - Same as ai-readiness AI-1 — recommended explicit annotation.

## F-2 collection readiness

- [✗] **CHK-CR-012** Entry в `docs/dev/capability-registry-pending.md`.
  - **VIOLATION** — F-3 не lists планируемые capabilities в `capability-registry-pending.md` (если такой файл уже существует) или не declares создание этого файла.
  - **Severity**: Medium. Без index'а F-2 archaeology task становится more painful.
  - **Fix**: добавить entry в Cross-spec impact. См. Issue CR-1.

---

## Issues & fixes

### Issue CR-1 — `capability-registry-pending.md` entry (CHK-CR-012, severity Medium)

**Fix**: добавить запись в Cross-spec impact:
```
- **docs/dev/capability-registry-pending.md** — добавить (либо создать файл если не существует) entries для F-3 capabilities:
  - `wizard.start(appFamilyId)` — read/write; idempotent; reversible; device-local auth.
  - `wizard.skipToStep(stepId)` — read/write; idempotent; reversible; device-local auth.
  - `localization.resolve(key, args)` — read-only; idempotent; pure read; device-local auth.
  - `tileSet.list(deviceClass)` — read-only; idempotent; pure read; device-local auth.
  - SystemSettingPort capabilities (per `android-pool.json` entries) — write; idempotent (повторный prompt ничего не меняет); reversible через app settings; device-local auth (some require user gesture в Android Settings).
  Plus 1-line description каждой. F-2 будет использовать этот index для enumeration.
```

---

## Резюме

**9 ✓ / 4 ⚠ / 1 ✗** — один real fix:

- **CR-1**: `capability-registry-pending.md` entry — makes F-2 collection trivial.

Applying CR-1 inline.
