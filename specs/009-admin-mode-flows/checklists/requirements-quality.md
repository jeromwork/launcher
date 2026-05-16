# Checklist: requirements-quality

**Spec**: `spec.md` (rev. 1, 2026-05-15 — pre-specify discovery 16 Q + post-clarify 5 Q + code review)
**Run**: 2026-05-15 — pre-`/speckit.plan` quality gate.

---

## Content Quality

- [ ] **CHK001 — No implementation details (programming languages, frameworks, vendor APIs in `spec.md`)**
  **Finding: PARTIAL FAIL (repository-convention N/A).** spec.md содержит vendor/framework/platform-API leak'и:
  - `Firestore`, `Firestore offline persistence`, `Firestore listener` (FR-002, FR-017, FR-020) — vendor SDK.
  - `Room` / `PendingLocalChanges` table (FR-014a) — framework + table name.
  - `Compose 1.6+`, `Modifier.dragAndDropSource/Target`, `Modifier.pointerInput`, `Composable экраны HomeScreen/FlowScreen/BottomFlowBar/TileCard`, `Icons.Filled.Call/Sms/Apps` (FR-005, FR-005a, FR-008, FR-046) — UI framework.
  - `ContactsContract.CommonDataKinds.Phone`, `Intent.ACTION_PICK`, `ACTION_SEND`, `LAUNCHER` intent, `<intent-filter>`, `<queries>` манифест, `market://`, `https://play.google.com/...` (FR-024, FR-027, FR-034, FR-035) — Android platform.
  - `FCM push` (FR-021 implicit via TODO-маршрут).
  - Exact file paths `core/src/commonMain/kotlin/com/launcher/api/config/Contact.kt`, `core/.../components/TileCard.kt:73` — implementation locations.
  - `READ_CONTACTS` permission name — Android-specific identifier.
  - `epoch millis`, `Result<Contact>` — language/idiom-specific.
  **Severity**: Medium. **Resolution**: same convention as spec 008 — repository-wide pattern (vendor names в wire-format / OS-integration specs допустимы, чтобы спек был грунтованным). **Action**: N/A — закрыто как repository-convention. Если хотим строгости — переписать FR-002 → «pull from cloud config store with fallback to local cache»; FR-024 → «system contact picker»; etc. Не блокирующее для перехода в `/speckit.plan`.

- [x] **CHK002 — Focus is on user value and business need, not on technical "how"**
  Контекст-секция объясняет «мотор спека 8 → кузов спека 9»; user stories — все от лица admin/Managed («Бабушка Honor», «Маша внучка», «случайно удалил важную плитку»); Why-this-priority каждый US объясняет business value («без P1 admin не может реально влиять», «без отката одна ошибка → бабушка в панике»). Technical leak'и (см. CHK001) обоснованы wire-format / OS-integration природой фичи.

- [x] **CHK003 — Written so a non-technical stakeholder can read and validate**
  Есть TL;DR на русском в конце («Что внутри»). 7 user stories на простом языке, конкретные примеры («бабушка Honor», «контакт Маша из WhatsApp»). Edge cases — сценарные, не технические. Pre-specify session pre-translates Q1-Q16 на «человеческом языке».

- [x] **CHK004 — All mandatory sections present**
  ✅ User Scenarios & Testing (7 US + Edge Cases), ✅ Functional Requirements (FR-001..046 в 11 группах), ✅ Success Criteria (SC-001..008), ✅ Out of Scope (OUT-001..020 — exhaustive), ✅ Clarifications (post-clarify C1-C5 + pre-specify Q1-Q16), ✅ Assumptions (A-1..A-10), ✅ Key Entities, ✅ Domain validation contract, ✅ Implementation hints, ✅ Open Questions (deferred to /speckit.clarify).

## Requirement Completeness

- [x] **CHK005 — No `[NEEDS CLARIFICATION]` markers remain**
  Verified via grep: 0 occurrences of `[NEEDS CLARIFICATION]`. Все 16 pre-specify Q + 5 post-clarify C resolved. Open Questions section содержит 3 Q-OPEN — но они отмечены как «may emerge in /speckit.clarify», не блокирующие для текущего gate.

- [x] **CHK006 — Every requirement is testable**
  Каждый FR имеет observable assertion:
  - FR-001 — список Managed с 4 индикаторами (UI test).
  - FR-002 — pull из `/config/current` с fallback (integration test с offline Firestore).
  - FR-008 — drag-and-drop через built-in API (instrumented test).
  - FR-014a — draft в Room survives process kill (integration test).
  - FR-015/037 — copy current → history перед обновлением (Firestore Emulator test).
  - FR-020 — severity-based cadence (timing test).
  - FR-026/028 — adapter ACL contract (unit test адаптера с моком validator'а).
  - FR-033 — dedup по phoneNumber (unit test).
  - FR-036/043 — schema versioning + lazy transformer (roundtrip + forward-compat test).
  - FR-046 — иконка варьируется по SlotKind (UI baseline test).
  Каждый требованный verifiable assertion.

- [x] **CHK007 — Every requirement is unambiguous**
  Жёсткие числа везде, где «может быть subjective»: 100 символов max имени, regex `^\+?\d{5,20}$` для phone, 10 KB VCard payload, 10 snapshot retention, severity threshold'ы (`<5%` Critical, `<20%` Warning, `>24ч` Critical, `>1ч` Warning), 30 сек pull cadence Info, 5 сек listener update Critical. Слов «fast / intuitive / smooth» — нет. «Декоративная рамка без точного pixel-масштаба» (FR-003) — определено через отрицание (что **не** делается).

- [x] **CHK008 — Success criteria are measurable**
  SC-001 — «≤ 90 секунд», SC-002 — «≤ 35 сек», SC-003 — «≤ 60 секунд», SC-004 — «≤ 60 секунд», SC-005 — «100% snapshot'ов с `schemaVersion = 1` корректно читаются», SC-006 — «95% сценариев / 10 переmещений подряд без падений», SC-007 — «100% покрытие», SC-008 — «100% сценариев». Все измеримы числами/процентами/timebounds.

- [ ] **CHK009 — Success criteria are technology-agnostic**
  **Finding: PARTIAL FAIL (repository-convention N/A).** SC-002 упоминает «Firestore listener». SC-005 упоминает «schemaVersion = 1» (поле wire format — допустимо как domain concept). Остальные SC — vendor-neutral («push», «redirect», «open editor»). Same root cause as CHK001 — repository convention. **Action**: N/A — accepted as convention. Strict alternative: SC-002 → «within 35s after the underlying value changes».

- [x] **CHK010 — All acceptance scenarios for each User Story are explicit (Given/When/Then)**
  US-1: 5 G/W/T scenarios. US-2: 4 scenarios. US-3: 4 scenarios. US-4: 4 scenarios. US-5: 4 scenarios. US-6: 3 scenarios. US-7: 3 scenarios. Total 27 G/W/T scenarios — все three-clause форма соблюдена.

- [x] **CHK011 — Edge cases are identified — at minimum: empty state, error state, retry, double-action**
  Edge Cases section: 11 пунктов.
  - **Empty state**: «Admin удалил все flow» + предупреждение.
  - **Error state**: «Push в `/config/current` падает после write history», «Snapshot имеет старую schemaVersion без транзформера», «VCard от вредоносного приложения с гигантским payload».
  - **Retry / offline**: «Admin офлайн, есть локальный кэш» — баннер + auto-merge при появлении сети; «Admin офлайн, нет кэша» — graceful failure.
  - **Double-action / race**: «История достигла 10 snapshot'ов» — housekeeping race acceptable; «Парные edit'ы плитки и flow» — merge UI спека 8.
  - **Permission / drift**: «Контакт удалён из системной адресной книги admin'a» — `TODO-ARCH-013`.
  - **Unpair**: «Managed-устройство удалено» — список + локальный кэш cleanup.
  Покрытие adequate.

- [x] **CHK012 — Scope is clearly bounded — In Scope and Out of Scope are exhaustive**
  In Scope: 7 user stories с priorities (P1×2, P2×3, P3×2). Out of Scope: 20 явных OUT-блоков (screen mirroring, iOS, pixel-accurate render, multi-select, preset-editor, wearables, security sensors, LINE/WeChat, push на closed app, configurable thresholds, named presets, contact drift, shared book, privacy compliance, server-side history, app version compat, runtime commands, transformers, complex retention, diff-view). Каждый OUT ссылается на конкретный backlog TODO/spec.

- [x] **CHK013 — Dependencies and assumptions are explicit**
  Assumptions section: A-1..A-10 — зависимости от спека 7 (pairing), спека 8 (sync mechanism), спека 6 (Health), Firestore offline persistence, Android runtime permissions, `<intent-filter>` поддержка, Android 11+ `<queries>`, WhatsApp/Telegram/Viber VCard support, existing composable extensibility, schemaVersion=1 stable. A-9 явно уточнена post-code-review C5 с reality check.

## Feature Readiness

- [x] **CHK014 — All functional requirements have clear acceptance criteria (mapped to a US or independent)**
  Трассировка:
  - FR-001..004 → US-1 + US-2 (managed list with health).
  - FR-005..007, FR-005a → US-1 + US-6 (editor rendering + edit mode).
  - FR-008..012, FR-046 → US-1 (editing operations + iconography fix).
  - FR-013 → forward-compat (no US, justified — wire-format extension).
  - FR-014..016, FR-014a → US-1 + US-6 (save/publish).
  - FR-017..022 → US-2 (phone health).
  - FR-023..026 → US-3 (system contact picker).
  - FR-027..031 → US-4 (VCard share).
  - FR-033 → US-3 + US-4 (deduplication, cross-source).
  - FR-034, FR-035 → US-7 (open-app tiles).
  - FR-036..043 → US-5 + US-6 (history + rollback).
  - FR-044, FR-045 → US-5 (security rules for history).
  Никаких dangling FR. FR-013 (forward-compat) — единственный без US, но обоснован как wire-format readiness (CLAUDE.md rule 5 — schema versioning).

- [x] **CHK015 — User scenarios cover primary flows — not just happy path; at minimum one error path per US**
  - US-1: scenario 4 = parallel-edit conflict (error path).
  - US-2: scenario 2 = critical battery + audio muted; scenario 4 = Managed offline (degraded state).
  - US-3: scenario 3 = multi-number contact (alternative path); scenario 4 = dedup hit (alternative path).
  - US-4: scenario 4 = VCard без TEL (error/rejection path).
  - US-5: scenario 3 = parallel edit during rollback (conflict path).
  - US-6: scenario 3 = parallel admin+Managed → Merge UI (conflict path).
  - US-7: scenario 3 = app not installed → Play Store fallback.
  Каждый US имеет ≥1 non-happy-path scenario.

- [x] **CHK016 — Feature meets measurable outcomes defined in Success Criteria (no SC without an FR producing the measurement)**
  SC-001 ← FR-001 (list) + FR-005..016 (editor + publish).
  SC-002 ← FR-017 + FR-020 (listener cadence) + FR-021 (event emit).
  SC-003 ← FR-027..030 (VCard intent + flow).
  SC-004 ← FR-039..041 (history UI + rollback push).
  SC-005 ← FR-036 + FR-043 (schemaVersion + lazy transformer).
  SC-006 ← FR-008 (drag-and-drop primary).
  SC-007 ← FR-009 (parallel menu for accessibility).
  SC-008 ← FR-033 (contacts в `/config` self-contained на Managed).
  Каждый SC backed by FR.

---

## Open items

| # | Issue | Severity | Action |
|---|---|---|---|
| O1 | CHK001/CHK009 — vendor/framework names в spec.md (Firestore, Room, Compose, Android Contacts*) | Low | Accept as repository convention (consistent с 007/008); если хотим строгости — переписать в vendor-neutral как follow-up чек-листа, не блокирует /speckit.plan. |
| O2 | FR-013 (forward-compat `presetOverrides: null`) — single-implementation interface без current need | Low | Justified explicitly via CLAUDE.md rule 5 (wire format additive readiness) + ссылка на TODO-FUTURE-SPEC-005. Sanity-check в checklist-meta-minimization. |
| O3 | Q-OPEN-1..3 в Open Questions section (UX-форма history list, новая Activity vs backstack, точные dp размеры) | Medium | Deferred to /speckit.clarify (next phase) — формально не блокирует quality gate, но скорее всего поднимутся как 1-3 clarification questions. |
| O4 | FR-005 ссылается на «существующие компоненты» (TileCard, FlowScreen, BottomFlowBar, HomeScreen) с file paths | Low | OK — это reality check от code review (A-9). Не contracted behavior, а reuse declaration. Допустимо. |

Spec.md правки **не требуются** для перехода в /speckit.plan. Все open items — informational / deferred.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 14 | CHK002, CHK003, CHK004, CHK005, CHK006, CHK007, CHK008, CHK010, CHK011, CHK012, CHK013, CHK014, CHK015, CHK016 |
| ⚠️ Local-convention N/A | 2 | CHK001, CHK009 — vendor/SDK/platform names (Firestore, Room, Compose, Android Contacts*, intent constants). Consistent с pattern спеков 007/008. Не блокирующее. |
| ❌ Fail | 0 | — |

**Verdict: PASS** (с двумя `repository-convention N/A`, идентично 008).

**Рекомендации для /speckit.plan:**
- Перенести vendor/framework leaks (точные имена Composable, Modifier API, ContactsContract URI, Firestore SDK calls) в plan.md / research.md.
- В plan.md research зафиксировать **two-way door на FR-008** (Compose built-in drag-and-drop vs ручной `pointerInput`) — это explicit choice от C4, требует proof-of-concept в research phase.
- Constitution Check (Article XVI) — separate gate в speckit-plan, не здесь.
- procedure-cross-artifact-trace — после speckit-tasks проверит FR-013 (forward-compat без US) и FR-046 (existing bug fix) на полноту trace в tasks.md.

---

## Что внутри (TL;DR на русском)

Этот документ — **quality gate** для spec 9 («admin-mode-flows») перед фазой /speckit.plan.

**Что проверялось:** 16 критериев spec-kit baseline checklist'a — содержание (CHK001-004), полнота требований (CHK005-013), готовность фичи (CHK014-016).

**Результат:**
- ✅ **14 из 16 критериев — PASS.** Спек хорошо структурирован, все mandatory секции на месте, все 7 user stories имеют Given/When/Then сценарии (27 штук), каждый функциональный requirement traceable до user story, success criteria измеримы числами (≤90 сек, ≤35 сек, ≤60 сек, 95-100% покрытие), edge cases покрывают empty/error/retry/race/offline, dependencies явные (A-1..A-10).
- ⚠️ **2 критерия — repository-convention N/A** (CHK001, CHK009). Спек упоминает vendor имена (Firestore, Room, Compose, Android Contacts*, intent constants). Это противоречит spec-kit canon («vendor — в plan.md»), но **тот же паттерн в спеках 007/008**, которые уже merged в main. Принято как convention; альтернатива — переписать в vendor-neutral wording (не блокирующее).
- ❌ **0 критериев — FAIL.**

**Open items (informational):** 4 пункта — все низкой/средней важности, формально не блокируют /speckit.plan. Q-OPEN-1..3 в самом спеке (UX-форма history list, navigation pattern, точные dp размеры) переходят в /speckit.clarify естественно.

**Вердикт:** **PASS.** Spec.md менять не нужно — можно идти в /speckit.plan. План должен сам разнести vendor-specific implementation details (Compose API, Firestore SDK calls, ContactsContract URI) из спека в plan.md/research.md, а в research зафиксировать two-way door на drag-and-drop API (FR-008 C4).
