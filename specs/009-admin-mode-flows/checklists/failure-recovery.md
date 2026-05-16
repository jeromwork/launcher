# Checklist: failure-recovery

**Spec**: `spec.md` (rev. 1 post-clarify batch 1, 2026-05-15)
**Run**: 2026-05-15 — `/speckit.clarify` follow-up before `/speckit.plan`.

Verifies Article III §4 (deterministic fallback) — what happens when things go wrong.
Reference report: [`specs/008-bidirectional-config-sync/checklists/failure-recovery.md`](../../008-bidirectional-config-sync/checklists/failure-recovery.md).

---

## Error mode inventory

| #   | Failure mode                                                                                  | Covered by                                                 | Recovery path                                                                  |
| --- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------ |
| F1  | Admin offline, no local cache for selected Managed                                            | Edge Cases bullet 1                                        | Message «нет соединения и нет локальной копии»; no destructive actions allowed |
| F2  | Admin offline, local cache exists                                                             | FR-002, Edge Cases bullet 2                                | Editor opens from cached config; banner «офлайн»; auto-merge on reconnect (FR-013 of spec 8) |
| F3  | Push of `/config/current` fails after history snapshot has already been written               | FR-015, FR-037, Edge Cases bullet 3                        | **Accepted** (race-condition rare loss); orphan snapshot stays; migration → SRV-CONFIG-001 |
| F4  | History snapshot has older `schemaVersion`, no transformer yet                                | FR-043, Edge Cases bullet 4                                | Snapshot stays visible; rollback button **disabled** with explanation; TODO-ARCH-015 |
| F5  | Admin deletes all flows → empty layout                                                        | Edge Cases bullet 5                                        | UI confirmation dialog «вы удалили все вкладки, бабушка увидит пустой экран; продолжить?» |
| F6  | System contact deleted from admin's phonebook after adding to layout                          | Edge Cases bullet 6                                        | Contact keeps working (name/number live in `/config`); drift detection → TODO-ARCH-013 |
| F7  | History reaches 10 snapshots, push 11                                                         | FR-038, Edge Cases bullet 7                                | Same-session housekeeping deletes oldest; race-condition accepted              |
| F8  | VCard share intent with > 10 KB payload (DoS attempt or malicious app)                        | FR-028 bullet 1, Edge Cases bullet 8                       | Adapter rejects «слишком большой / не похоже на контакт»                       |
| F9  | VCard not UTF-8 encoded                                                                       | FR-028 bullet 2                                            | Adapter rejects «не удалось прочитать контакт»                                 |
| F10 | VCard without TEL field (LINE / WeChat / KakaoTalk contact)                                   | FR-031, US-4 scenario 4                                    | Adapter rejects «контакт без номера телефона не может быть добавлен в текущей версии» |
| F11 | Managed device unpaired during admin session                                                  | FR-004, Edge Cases bullet 9                                | Managed disappears from list; cached snapshots wiped on next admin app launch  |
| F12 | Parallel edits to tile + flow (admin moves tile into a flow another editor just deleted)      | Edge Cases bullet 10                                       | Merge UI of spec 8 FR-050 — diff shown, user resolves                          |
| F13 | Optimistic-concurrency conflict on publish                                                    | FR-016, US-1 scenario 4, US-5 scenario 3                   | Merge UI of spec 8 FR-050 (no senior-safe variant)                             |
| F14 | `READ_CONTACTS` permission **denied first time** on admin device                              | FR-023 (rationale screen) — implicit                       | ⚠️ **GAP**: spec says «после rationale → grant», но не покрывает «admin отказал». См. CHK012 |
| F15 | `READ_CONTACTS` permanently denied («don't ask again»)                                        | **NOT EXPLICITLY COVERED**                                 | ⚠️ **GAP**: spec не описывает «открыть Settings» fallback. См. CHK013          |
| F16 | Contact picker returns URI but record can't be read (revoked permission, deleted between picker and read) | **NOT EXPLICITLY COVERED**                       | ⚠️ **GAP**. FR-026 ValidationError covers malformed data, не «row gone»        |
| F17 | Domain validation rejects raw contact (name > 100, control chars, phone regex fail)           | FR-026 bullet 2-3, FR-028 bullet 4, Domain validation contract | Toast «Не удалось добавить контакт: <причина>», typed ValidationError      |
| F18 | OpenApp tile dispatched on Managed, app not installed and no Play Store (Huawei / non-GMS)    | FR-035 (b)(c), Implementation hint OpenAppDispatcher.kt   | Fallback `market://` → fallback web `https://play.google.com/store/apps/details?id=...` |
| F19 | OpenApp tile, package not declared in `<queries>` manifest block (Android 11+)                | A-7                                                        | ⚠️ **GAP**: spec only states assumption. Что показать бабушке если intent resolve returns null *and* `<queries>` missing? Plan.md TODO |
| F20 | Firestore listener for `/links/{linkId}/health` fails (permission revoked, network)           | FR-020 (listener-only когда экран открыт)                  | ⚠️ **GAP**: spec не описывает, что admin видит при listener-failure. См. CHK002 |
| F21 | `Health` snapshot stale > 3h (Managed offline)                                                | US-2 scenario 4, FR-022                                    | UI shows «3 часа назад (последняя известная)», lastSeen indicator = Warning   |
| F22 | Local draft (Room) corrupted / DB unreadable on admin device                                  | **NOT EXPLICITLY COVERED**                                 | ⚠️ **GAP**: same as spec 8 CHK014. План.md inherited action item               |
| F23 | Process death / app restart with unsaved draft                                                | FR-014a, US-1 scenario 5                                   | Draft restored from Room, banner «есть несохранённые изменения»               |
| F24 | Push from rollback (FR-041) hits conflict because someone edited in parallel                  | FR-041 (через спек 8 conflict-check)                       | Merge UI; rollback becomes a normal edit subject to merge                      |
| F25 | Snapshot `schemaVersion > current code support` (future schema)                               | FR-043                                                     | Snapshot rejected «слишком новая версия»; rollback disabled                    |
| F26 | Disk full when persisting local draft / housekeeping                                          | **NOT EXPLICITLY COVERED**                                 | ⚠️ **GAP**. Inherited from spec 8 plan.md action item                          |

---

## Error categories

### CHK001 — Each FR involving an external action lists at least one failure mode

- [x] **PASS (mostly).**

  Coverage of FR external actions:
  - FR-001/002 (list + pull `/config`): F1, F2 explicit.
  - FR-015 (write history → write current): F3 explicit + accepted.
  - FR-017/020 (read `/links/{linkId}/health`, listener): F20, F21 partially — listener-failure not explicit.
  - FR-024 (Intent.ACTION_PICK): F16 not covered (URI but row gone).
  - FR-026 (`SystemContactPickerAdapter`): F17 explicit via ValidationError.
  - FR-028 (`VCardImportAdapter`): F8, F9, F10 explicit.
  - FR-034/035 (OpenApp dispatcher): F18 explicit, F19 only via assumption A-7.
  - FR-037/038 (history write + housekeeping): F3, F7 explicit.
  - FR-041 (rollback push): F24 implicit via reuse of spec 8 flow.
  - FR-043 (schema validation): F4, F25 explicit.

  ⚠️ Gaps:
  - **FR-020 listener failure** — нет описания, что admin видит, если health listener рвётся mid-session.
  - **FR-024 picker URI read failure** (F16) — не покрыто.
  - **FR-014a Room I/O failure** (F22) — наследуется от спека 8, но в спеке 9 explicit упоминания нет, хотя Room используется для draft.

### CHK002 — For each failure mode: user-visible behaviour specified — not "show error"

- [ ] **PARTIAL PASS.**

  ✅ Well-specified user-visible behaviour:
  - F1: «нет соединения и нет локальной копии — попробуйте позже».
  - F2: банер «офлайн, изменения применятся при появлении сети».
  - F4: snapshot visible, rollback button **disabled** + explanation.
  - F5: confirmation dialog с явным текстом про «бабушка увидит пустой экран».
  - F8: «слишком большой / не похоже на контакт».
  - F9: «не удалось прочитать контакт».
  - F10: «контакт без номера телефона не может быть добавлен в текущей версии».
  - F17: «Не удалось добавить контакт: <причина>».
  - F18: graceful Play-Store fallback.
  - F21: «N часов назад (последняя известная)».
  - F25: «эта версия несовместима с текущим приложением, откат невозможен».

  ⚠️ Under-specified user-visible behaviour:
  - **F14** (READ_CONTACTS denied first time): spec говорит про rationale-экран *до* prompt'а, но не описывает branch «admin отказал». «+ контакт» становится disabled? Toast? Возврат к редактору?
  - **F15** (READ_CONTACTS permanent denial): полностью отсутствует. Стандартный паттерн — deep-link в Settings → нужен FR или AC.
  - **F16** (picker URI read failure): ничего.
  - **F20** (health listener failure): индикаторы серые? «недоступно»? exception silently dropped?
  - **F22** (Room corruption — draft DB): нет user-visible behaviour.

### CHK003 — No silent failures of user-initiated actions

- [x] **PASS** for happy-path FRs (publish, picker, share, rollback — каждый имеет visible UI transition).

  ⚠️ Watch: F20 (health listener) и F22 (Room corruption) — потенциально silent. Plan.md обязан явно contractировать «failure → diagnostic event + UI indicator».

## Fallbacks

### CHK004 — Fallback chains: maximum depth defined (cycle protection)

- [x] **PASS.**

  - **OpenApp dispatcher** (F18): installed → `market://` → web fallback. **3 steps, deterministic, terminal = web Play Store page**. ✅
  - **Pull `/config`**: server → local cache → "нет копии" message. **2 steps, terminal**. ✅
  - **Rollback conflict loop** (F24): theoretical unbounded merge loops, но каждая итерация требует user action — natural bound (inherited from spec 8 analysis). ✅
  - **History snapshot schema**: future schemaVersion → reject; old → transformer or disable. No cascading. ✅

### CHK005 — Fallback specified by data, not hardcoded in dispatch

- [x] **PASS.**

  OpenApp fallback chain — структурный (per Action.kind), не data-driven `Action.fallback` (то спек 5 actions). Это **OK** для этого спека — fallback не нуждается в data-level конфигурации, поведение универсально для всех `OpenApp` плиток.

### CHK006 — Terminal fallback behaviour defined

- [ ] **PARTIAL PASS.**

  ✅ Terminal states defined:
  - F18: web Play Store URL = terminal.
  - F1: «попробуйте позже» message = terminal until user retries with network.
  - F4 / F25: rollback button disabled = terminal until transformer ships.

  ⚠️ Gaps:
  - **F22 (Room corruption)**: spec 9 inherits Room from spec 8, но spec 8 failure-recovery checklist уже flagged это как gap для plan.md. Для спека 9 это означает «при невозможности прочитать draft → что показать admin'у?». **Action для plan.md**.
  - **F19 (Managed без `<queries>`)**: assumption A-7, но если manifest всё же ошибочный — что Managed показывает бабушке? Toast «приложение недоступно»? Тишина?
  - **F20 (health listener fail)**: terminal state не определён.

## Retries

### CHK007 — Retry behaviour explicit

- [ ] **GAP.**

  Spec 9 не специфицирует retry policy. Inherits spec 8 baseline (Firebase SDK exponential backoff) для `/config` pushes, но:
  - **Health listener reconnect** (FR-020): кто retry'ит когда listener падает? Firestore SDK auto-reconnect? Сколько раз?
  - **History snapshot write retry** (FR-037): если write упал — retry? User action? Or accepted loss (это explicit F3, но retry strategy unspecified).
  - **Housekeeping retry** (FR-038): если delete-oldest failed — retry next push? Skip?
  - **Permission deletion retry** (FR-033b): «pending action, применяется при первом online» — но retry policy не описана.

  **Action для plan.md** (mandatory): per-operation retry table:
  - `/config` push, `/config/history` write, housekeeping delete, `/state` write → Firebase SDK auto-retry (~30s exponential).
  - Health listener → Firestore SDK auto-reconnect.
  - User-initiated retry для merge resolution second push.
  - Permission-deletion pending — single replay on next online, then surfaced as error if still failing.

### CHK008 — No infinite retry loops without user intervention point

- [x] **PASS.** Все loops в спеке требуют user action (merge resolution, rollback confirmation, повторный publish). Firebase SDK auto-retry имеет встроенный cap.

### CHK009 — Idempotency: retry-safe actions

- [x] **PASS.**

  - `/config/current` write — idempotent через optimistic concurrency (наследуется от спека 8).
  - `/config/history/{autoId}` write — auto-ID → повторная запись создаст **новый** snapshot (дубликат). ⚠️ Watch: client retry в случае «не получили ack но write прошёл» = duplicate snapshot. Не критично (housekeeping срежет), но стоит упомянуть в plan.md.
  - Housekeeping delete — idempotent (delete deleted = no-op).
  - VCard adapter parse — idempotent (re-parse same input = same Contact).
  - `Contact.fromRaw()` — pure function, idempotent.

## Offline / degraded modes

### CHK010 — Offline behaviour defined for network reads

- [x] **PASS.**

  - FR-002: pull `/config` → fallback на Firestore offline persistence cache.
  - F1/F2 покрывают obe ветки (cache present / cache absent).
  - FR-014a: draft в Room, survives offline.
  - FR-033b: contact deletion as pending action when offline.
  - Health UI: cached snapshot с пометкой «N часов назад (последняя известная)».

### CHK011 — Stale data: TTL / freshness defined

- [x] **PASS.**

  - **Health update cadence** (FR-020): Info → pull 30s; Warning/Critical → realtime listener. Listener закрывается при закрытии экрана.
  - **History retention** (FR-038): максимум 10 snapshots, freshness = last 10 pushes.
  - **lastSeen freshness** (FR-022): human-readable; «(последняя известная)» если connectivity = None.
  - **Local draft** (FR-014a): «живёт сколь угодно долго» (inherited pattern from спека 8 FR-043).
  - **Cached `/config`** (FR-002): Firestore SDK default policy — fresh on connection.

## Permissions denied

### CHK012 — Each permission required: behaviour when denied first time

- [ ] **GAP.**

  FR-023 описывает **rationale screen → grant flow**, но не **denial branch**:
  - Что admin видит, если он закрыл rationale без grant?
  - Что admin видит, если system permission prompt → "Deny"?
  - «+ контакт» disabled? Toast «без разрешения не могу читать контакты»? Возврат к editor с pending tile?

  **Action для spec.md**: добавить **FR-023a** или AC к US-3:
  > **FR-023a**: Если admin отказал в `READ_CONTACTS` (denied first time, "don't ask again" не выбрано) — кнопка «+ контакт» остаётся доступной, тап показывает inline toast «Без разрешения не могу читать контакты. Нажмите ещё раз чтобы попробовать снова». Альтернатива: ручной ввод имени/номера через FR-026 contract.

### CHK013 — Permanent denial: explicit recovery path

- [ ] **GAP.**

  Spec не описывает «don't ask again» branch. Стандартный Android pattern: deep-link в `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` через `Intent`.

  **Action для spec.md**: добавить AC или FR-023b:
  > **FR-023b**: При permanent denial `READ_CONTACTS` (Android вернул `shouldShowRequestPermissionRationale == false` после denial) — UI показывает persistent baseline «Разрешение запрошено перманентно отклонено» с кнопкой «Открыть настройки приложения» (deep-link через `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`). Альтернативный flow — ручной ввод contact data через manual form (если будет добавлен в plan.md).

  В качестве «exit ramp за минимум»: можно решить «picker без permission — нельзя; ручной ввод не предлагаем; admin должен дать permission». Но это **должно быть explicit** в spec.md, не подразумеваться.

## Recovery from invalid state

### CHK014 — Recovery path for corruptible persistent state

- [ ] **GAP** (inherited from spec 8).

  Spec 9 добавляет **новые** persistent surfaces, которые могут стать corrupt:
  - Local draft в Room (FR-014a) → corruption случай.
  - Cached snapshots в Firestore offline persistence → SDK обрабатывает, обычно OK.
  - History snapshots в Firestore — readonly с точки зрения корруптности (server-side data).

  ⚠️ Особый случай: **F25 (snapshot newer than code)** — это форма **invalid state** при перекрёстных версиях admin/Managed. FR-043 покрывает: «отвергаем как слишком новая версия» — это deterministic fallback ✅.

  ⚠️ Не покрыто:
  - Что если history snapshot имеет corrupt JSON (не parseable)? FR-043 покрывает schema mismatch, но не unparseable bytes. **Action для plan.md**.
  - Что если draft Room becomes unreadable? Inherited от спека 8 plan.md action — но spec 9 должен явно подтвердить наследование (т.к. draft = новая Room table).

### CHK015 — No "crash and restart" as recovery

- [x] **PASS.**

  - F25 (newer schema): graceful reject + disabled button.
  - F4 (older schema, no transformer): graceful disable.
  - F22 (Room corruption): не специфицировано, но spec inherits спек-8 pattern (catch SQLiteException → wipe → fresh state). Plan.md должен это явно подтвердить.

## Diagnostics

### CHK016 — Failures observable: diagnostic events with category

- [ ] **GAP.**

  Spec 9 не специфицирует diagnostic events для:
  - VCard parse rejections (F8, F9, F10) — rate measurement важна (есть ли attacks, есть ли LINE/WeChat users просящих feature).
  - History rollback conflicts (F24) — частота полезна для UX assessment.
  - Schema rejection events (F4, F25) — track migration urgency.
  - Health listener failures (F20).
  - Permission denial rates (F14, F15) — критично для privacy compliance.
  - OpenApp fallback usage (F18) — track «сколько % бабушек не имеют приложения».

  **Action для plan.md** (mandatory): добавить категории для спек-9 events в общую sealed class иерархию `ConfigSyncError` (если применимо) или новую `AdminFlowDiagnosticEvent`:
  ```kotlin
  sealed class AdminFlowDiagnosticEvent {
    data class VCardRejected(val reason: VCardRejectReason) : ...
    data class ContactPermissionDenied(val permanent: Boolean) : ...
    data class HistoryRollbackConflict : ...
    data class SnapshotSchemaIncompatible(val direction: SchemaDirection) : ...
    data class HealthListenerFailed(val errorCategory: String) : ...
    data class OpenAppFallbackToPlayStore(val packageName: String) : ...
  }
  ```

### CHK017 — Failures aggregated by category

- [x] **PASS (with action).** Inherits sealed-class pattern from спека 7 (`BackendError`) и спека 8 (`ConfigSyncError`). Spec 9 needs to declare его own categories — see CHK016 action.

---

## Summary

| Status         | Count | Items                                                                                              |
| -------------- | ----- | -------------------------------------------------------------------------------------------------- |
| ✅ Pass        | 9     | CHK001 (mostly), CHK003, CHK004, CHK005, CHK008, CHK009, CHK010, CHK011, CHK015                    |
| ⚠️ Watch / Gap | 8     | CHK002 (F14/F15/F16/F20/F22), CHK006 (F19/F20/F22), CHK007, CHK012, CHK013, CHK014, CHK016, CHK017 |
| ❌ Fail        | 0     | —                                                                                                  |

**Verdict: PASS with significant action items для spec.md and plan.md.**

Spec 9 наследует strong failure-recovery foundation от спека 8 (offline persistence, optimistic concurrency, conflict UI, pending draft). Edge Cases section покрывает 10 ключевых сценариев. Новые failure-surface'ы спека 9 (VCard parser, contact picker, schema validation, OpenApp dispatcher, rollback) **хорошо** покрыты для «known unknowns» (DoS payload, missing TEL, schema mismatch, no Play Store). **Под-покрыты** runtime permission denial branches и diagnostic observability — стандартные plan.md follow-ups.

---

## Top 3 missing failure paths (priority order)

1. **READ_CONTACTS denial branches (F14, F15)** — самый user-visible gap. Spec описывает rationale → grant happy path, но не «denied» / «don't ask again». Recommend: добавить **FR-023a / FR-023b** в spec.md явно.

2. **Health listener failure user-visible behaviour (F20)** — FR-020 описывает success cadence, но не failure. Admin может думать что батарея OK, когда listener давно умер. Recommend: AC в US-2 или новый FR об visual «listener disconnected» indicator.

3. **OpenApp dispatcher when Managed manifest `<queries>` missing or fails (F19)** — assumption A-7 не failure-recovered. Что бабушка видит на Managed? Critical: бабушка тапает плитку и НИЧЕГО не происходит — silent failure против CHK003. Recommend: AC в US-7 «если intent unresolvable — toast / soft visual feedback».

---

## Recommended spec.md edits (optional)

### FR-023a (new — READ_CONTACTS denial UX)

> При отказе в `READ_CONTACTS` (denied first time, без "don't ask again") — кнопка «+ контакт» остаётся доступной; повторный тап показывает inline toast «Без разрешения не могу читать контакты» и повторно запрашивает permission через стандартный prompt.

### FR-023b (new — READ_CONTACTS permanent denial UX)

> При permanent denial `READ_CONTACTS` (Android API вернул `shouldShowRequestPermissionRationale == false` после отказа) — UI показывает persistent message «Разрешение отклонено перманентно» с кнопкой «Открыть настройки приложения» (deep-link через `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).

### FR-020a (new — Health listener failure UX)

> При сбое Firestore listener для `/links/{linkId}/health` (network drop mid-session, permission revoked, etc.) — все 4 индикатора у соответствующего Managed переключаются в visual state "недоступно" (нейтральный серый, символ "—"); diagnostic event `HealthListenerFailed` эмитится. Восстановление — auto через Firestore SDK reconnect; UI обновляется при первом успешном snapshot после reconnect.

### US-7 AC-4 (new — OpenApp silent-failure prevention)

> **Given** бабушка тапает плитку `OpenApp`, package не установлен и Play Store недоступен, web fallback также failed, **When** dispatcher exhausts fallback chain, **Then** показывается toast «Не удалось открыть приложение, попросите помощи у админа» — НЕ silent failure.

---

## Mandatory action items для plan.md

1. **Retry policy table** (CHK007): per operation — health listener reconnect, history write, housekeeping, permission-deletion pending replay.

2. **Terminal fallback behaviour** (CHK006): для каждой fallback chain (especially F22 Room corruption, F19 manifest missing, F20 listener fail) определить «всё перепробовано» state.

3. **Room corruption recovery for draft** (CHK014): inherit спек-8 pattern (catch SQLiteException → wipe draft table → fresh state without draft).

4. **Diagnostic events sealed class** (CHK016, CHK017): `AdminFlowDiagnosticEvent` с categories для VCard reject reasons, permission denial, history schema mismatch, OpenApp fallback usage.

5. **Picker URI read failure** (F16, CHK001): explicit error handling в `SystemContactPickerAdapter` для случая «URI valid, row gone между selection и read».

6. **History idempotency edge** (CHK009): document что retry write на `/config/history/{autoId}` может создать duplicate snapshot (auto-ID → нет dedup key); housekeeping eventually cleans up. Acceptable trade-off.

7. **Schema rejection diagnostic** (CHK016): при F4/F25 эмитить diagnostic event с категорией `SchemaIncompatible.Older` или `SchemaIncompatible.Newer` для tracking migration urgency.

---

## TL;DR (на русском)

**Verdict**: ✅ **PASS с action items** — спек 9 в целом надёжен по failure-recovery, наследует прочный фундамент от спека 8 (offline persistence, optimistic concurrency, pending draft в Room). Edge Cases section покрывает 10 ключевых сценариев. Новые failure-modes (VCard parser, schema validation, history rollback, OpenApp fallback) хорошо описаны для «известных неизвестных».

**Слабые места**:

1. **READ_CONTACTS denial UX** не описан — spec показывает только happy path «rationale → grant». Что если admin отказал? Permanent denial? Это самый видимый gap — рекомендую добавить FR-023a/FR-023b в spec.md прямо сейчас (2 короткие правки).
2. **Health listener failure UI** — FR-020 описывает успех, но не сбой listener. Admin может думать что всё ок, пока listener давно мёртв → рекомендую FR-020a (visual indicator «недоступно»).
3. **OpenApp silent failure на Managed** — если все 3 fallback'а упали (нет app, нет Play Store, нет web), бабушка тапает плитку и ничего не происходит. Это нарушает CHK003 «no silent failures» → AC-4 в US-7.

**Подсчёт**: 9 ✅ / 8 ⚠️ / 0 ❌. Большинство ⚠️ — стандартные plan.md follow-ups (retry policy, diagnostic events, Room corruption — те же что в спеке 8). Уникальные для спека 9 — 3 правки в spec.md по permissions UI и silent-failure prevention.

**Рекомендация**: внести 3 предложенные FR/AC правки в spec.md перед `/speckit.plan` (5 минут работы); остальное закрепить в plan.md как action items.
