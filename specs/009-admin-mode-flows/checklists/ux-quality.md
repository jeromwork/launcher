# Checklist: ux-quality

**Spec**: `spec.md` (rev. 1 — pre-specify scope discovery 2026-05-15, post-clarify C1-C5)
**Run**: 2026-05-15 — `/speckit.clarify` post-pass before `/speckit.plan`.

Verifies UX requirements completeness, clarity, consistency, and measurability per Article VIII / §III.1 + Material Design.

---

## UX surface inventory in spec 009

| # | Screen / Surface | Type | FR coverage | Status |
|---|---|---|---|---|
| U1 | Список Managed-устройств с health-сводкой | screen | FR-001, FR-004, FR-017..022 | new в 009 (extends 003) |
| U2 | Редактор раскладки Managed (view mode = applied; edit mode = draft) | screen | FR-002..007, FR-005a, FR-014a | new в 009 |
| U3 | Редактор раскладки (edit-mode: long-press drag, «···» menu, «+») | interactions | FR-006..011, FR-046 | new в 009 |
| U4 | Форма редактирования плитки (kind picker + Call/Sms/OpenApp fields) | screen | FR-010, FR-034 | new в 009 |
| U5 | «+ контакт» → rationale → системный picker → multi-number dialog | flow | FR-023..026, FR-033c | new в 009 |
| U6 | VCard share-intent target — промежуточный экран «выбрать Managed» | screen | FR-027..031 | new в 009 |
| U7 | VCard share — entry в редактор Managed с предзаполненной формой | screen | FR-030 | new в 009 |
| U8 | «История» — список snapshot'ов | screen / sheet / modal — TBD (Q-OPEN-1) | FR-039 | new в 009 |
| U9 | History snapshot read-only preview + «Откатить» | screen | FR-040..043 | new в 009 |
| U10 | Расширенная health-сводка в редакторе (header) | UI element | FR-003, FR-017..022 | new в 009 |
| U11 | Merge UI (inherited from spec 008 FR-050) | screen | FR-016, FR-041 | inherited 008 |
| U12 | «Сохранить» vs «Опубликовать» buttons + draft banner | UI element | FR-014, FR-014a | new в 009 |
| U13 | Confirmation «вы удалили все вкладки, бабушка увидит пустой экран» | dialog | edge case L148 | mentioned, не специфицирован |
| U14 | Settings → «Добавленные контакты» (privacy minimum) | screen | FR-033a, FR-033b | new в 009 (privacy minimum) |
| U15 | `READ_CONTACTS` rationale экран с расширенным privacy текстом | screen | FR-023, FR-033c | new в 009 |
| U16 | OpenApp picker — выбор из установленных приложений / ручной ввод | screen / dialog | FR-034 | mentioned, не специфицирован |
| U17 | Empty / error / offline states редактора | UI element | FR-002 + edge cases L144-145 | partially specified |
| U18 | Snapshot too-new / transformer-missing error | UI element | FR-043, edge case L147 | mentioned |
| U19 | «Откатить» confirmation dialog | dialog | FR-041 (US-5 AS3) | mentioned, не специфицирован |
| U20 | Preset switcher (workspace / simple-launcher / launcher dropdown) | UI element | FR-012 | mentioned |

---

## Completeness — coverage of screens

- [ ] **CHK001 — Every user-facing screen of this feature listed in spec**

  ⚠️ **PARTIAL.**

  ✅ Покрыто: U1 list, U2 editor, U6 VCard target, U8 history list, U9 history preview, U11 merge (inherited), U14 privacy list.

  ⚠️ Не явно перечислены / under-specified:
  - **U8 форм-фактор** «История» — модальное окно / отдельный экран / side-sheet? Помечено как `Q-OPEN-1` в spec.md L482. **Action для plan.md**: выбрать форм-фактор + screen flow.
  - **U13 «empty flows» confirmation** — упомянут в edge cases L148 («продолжить?»), но FR отсутствует. **Watch для plan.md**.
  - **U16 OpenApp picker** — FR-034 говорит «(а) список приложений, (б) ручной ввод», но screen design не специфицирован: один dropdown или два tab'a? «Установленные» подгружаются как? **Action для plan.md**.
  - **U17 offline / error states** — edge cases L144-145 описывают два сценария («нет сети + нет кэша» vs «есть кэш»), но конкретные screen states (текст, иконка, действия) не специфицированы.
  - **U19 rollback confirmation** — US-5 AS3 упоминает «диалог подтверждения», но текст / кнопки / форм-фактор не специфицированы.
  - **U7 entry в редактор** через VCard share — `Q-OPEN-2` (L483) спрашивает: new Activity vs deep-link в existing backstack. **Action для plan.md**.

  **Recommendation для plan.md**: explicit screen flow diagram + screen states для:
  - Admin list → Managed editor → Edit mode → Publish → (conflict?) → Merge UI → Applied
  - VCard share entry → Pick Managed → Editor (предзаполнено) → Publish
  - Editor → History menu → List → Preview → Rollback confirm → Push

- [ ] **CHK002 — Every UX state per screen specified (loading, empty, success, error, partial-data)**

  ⚠️ **GAPS:**

  Per U1 (Managed list):
  - ✅ Health indicators (4) явно описаны.
  - ⚠️ **Empty state** (нет paired Managed) — не специфицирован. После unpair last device, что admin видит?
  - ⚠️ **Loading state** на cold start (Firestore SDK подгружает list) — не специфицирован.

  Per U2 (editor):
  - ✅ View mode vs edit mode (FR-005a).
  - ✅ Live draft (FR-007).
  - ✅ Offline + cache available (edge case L145).
  - ✅ Offline + no cache (edge case L144).
  - ⚠️ **Saving / publishing in progress** state — FR-014 раздельные кнопки, но states (spinner? disabled buttons? indicator?) не специфицированы. Inherits из спека 8 FR-015 spinner, но не явно сослано.
  - ⚠️ **Publish success** — какой feedback после успешного push'a? Toast «Опубликовано»? Banner исчезает? Inherits из спека 8, но не явно.

  Per U8 / U9 (history):
  - ✅ List sorted by recordedAt DESC (FR-039).
  - ✅ Preview read-only (FR-040).
  - ⚠️ **Empty history** (0 prior snapshots) — спрятать пункт меню? Показать «История пуста»?
  - ⚠️ **Snapshot incompatible** (FR-043, edge case L147) — кнопка отката заблокирована, но visual treatment (disabled grey? error icon? text «несовместимая версия»?) не специфицирован.
  - ⚠️ **Rollback in progress** — после tap «Откатить» что user видит до завершения push'a?

  Per U5 (контакт picker flow):
  - ✅ Rationale screen → picker → multi-number dialog (FR-023..025).
  - ⚠️ **Permission denied — never ask again** — что показать? «Откройте Settings → Permissions»? Не специфицирован.
  - ⚠️ **Picker cancelled** (admin тапнул back) — silent return или toast?

  Per U10 (extended health):
  - ✅ Severity colors (US-2 AS2).
  - ✅ lastSeen human-readable (FR-022).
  - ⚠️ **Health snapshot никогда не было** (свежий pairing, ещё не успел прийти) — что показать? «Загрузка health…»?

  **Action для plan.md**: state diagrams для U1, U2, U5, U8, U9, U10.

- [ ] **CHK003 — Navigation transitions between screens specified**

  ⚠️ **GAPS.**

  - ✅ Admin: список (U1) → tap → editor (U2): FR-001 явно.
  - ✅ Managed→editor через 7-tap+пароль: US-6 AS1 явно (inherited из 003).
  - ⚠️ **Back из editor с unsaved draft**: восстанавливается при возврате (US-1 AS5), но **что происходит при back**? Confirmation «у вас есть несохранённые изменения, выйти?» или silent save в draft? Spec.md US-1 AS5 implies silent save, но not explicit FR. **Action для plan.md**.
  - ⚠️ **VCard share entry** — `Q-OPEN-2` (L483): new Activity vs deep-link. **Action для plan.md** (one-way door для backstack behaviour).
  - ⚠️ **From history preview → back vs rollback**: явный exit path не специфицирован (back = cancel? rollback button = forward action?).
  - ⚠️ **Privacy list (U14) entry point**: FR-033a говорит «в admin Settings», но Settings nav не специфицирован.
  - ⚠️ **Deep-link entry** в редактор Managed (например, FCM push «battery critical → tap → open Managed»): out-of-scope per OUT-009 (`PhoneHealthCriticalEvent` без подписчика), but in future. ✅ OK to defer.

  **Action для plan.md**: navigation graph (Decompose / RootComponent), включая:
  - back stack policy для editor (draft preservation)
  - VCard share Activity backstack
  - History sheet/screen dismiss policy

- [ ] **CHK004 — Cross-cutting overlays (snackbar, toast, dialog, bottom sheet)**

  ⚠️ **GAPS.**

  Spec.md упоминает «диалог» / «баннер» / «promtejutochnij ekran» без указания overlay type:
  - **Multi-number dialog** (FR-025) — dialog ✅ implied.
  - **Rationale экран** (FR-023, FR-033c) — full screen / bottom sheet / dialog? **Action для plan.md**.
  - **VCard target picker** (FR-029) — full screen / bottom sheet? FR formulation «промежуточный экран» implies full screen, но не явно.
  - **Rollback confirmation** (FR-041, US-5 AS3) — dialog ✅ implied.
  - **«Опубликовано»** ack — toast / inline / silent? Inherits 008. **Watch**.
  - **«Несохранённые изменения» banner** (US-1 AS2/AS5) — persistent banner / chip / status bar? **Action для plan.md**.
  - **«Удалили все вкладки» warning** (edge case L148) — dialog / inline warning? **Action для plan.md**.
  - **VCard error** «контакт без номера телефона» (FR-031) — dialog / toast / inline в target picker? **Action для plan.md**.
  - **Snapshot incompatible** error (FR-043) — inline disabled state + tooltip / dialog при попытке tap? **Action для plan.md**.

  **Action для plan.md**: явная overlay-type table для каждого user feedback element.

## Clarity — terminology and rules

- [x] **CHK005 — UX terms defined unambiguously**

  Spec.md consistently uses:
  - «admin» — управляющий родственник.
  - «Managed» — телефон пожилого пользователя (consistent project-wide per `project_managed_naming_convention`).
  - «editor» — устройство с правом писать `/config` (inherited 008).
  - «draft» — локальные изменения до push'a (FR-014a).
  - «published» — pushed в `/config/current`.
  - «applied» — Managed применил (inherited 008 SC-001b).
  - «snapshot» — запись в `/config/history/{autoId}` (FR-036).
  - «rollback / откатить» — push содержимого snapshot как new current (FR-041).
  - «severity» — Info/Warning/Critical (FR-018).

  ✅ All terms consistently used. Term «editor» reused with same meaning as 008.

- [ ] **CHK006 — Vague qualifiers operationalised**

  ⚠️ **WATCH:**

  - ✅ SC-001..008 all measurable («≤ 90 секунд», «≤ 35 сек», «≤ 60 секунд», «95% сценариев», «100% покрытие»).
  - ⚠️ **«естественный UX»** в US-4 description (L78) — narrative, не FR. ✅ OK (US description, не AC).
  - ⚠️ **«критическая ситуация»** US-2 (L42) — operationalised через severity thresholds в FR-018. ✅
  - ⚠️ **«безопасным для использования»** US-5 (L95) — narrative. ✅
  - ⚠️ **«полноценно настраивать»** US-1 cover (L14) — narrative. ✅

  No unmeasurable adjectives в FR / AC. ✅ Pass.

- [ ] **CHK007 — Action vocabulary explicit: tap vs long-press vs swipe**

  ⚠️ **MOSTLY GOOD, partial gaps.**

  ✅ Явно:
  - **«Long-press на плитку → drag-and-drop»** (FR-008, US-1 AS2) — explicit.
  - **«Tap по snapshot → preview»** (FR-040) — explicit.
  - **«7 тапов на логотипе + пароль»** (US-6 AS1) — explicit (inherited).
  - **«Тап на плитку → форма редактирования»** в edit-mode (FR-006) — explicit.
  - **«Тап в edit-mode на плитку НЕ запускает Action»** (FR-006) — explicit disambiguation.

  ⚠️ Менее ясно:
  - **«Перетаскивает на новую позицию → отпускает»** (US-1 AS2) — implicit drag gesture, но cross-flow drag (FR-008 «другая flow») — как именно? Drag к BottomFlowBar tab → переключение flow? Drag к индикатору другой flow? **Action для plan.md**.
  - **«Корзина внизу экрана» drop target** (FR-008) — появляется при drag start или всегда видна в edit-mode? **Action для plan.md**.
  - **«Кнопка ··· → меню»** (FR-009) — tap, but как меню появляется (popup / bottom sheet)? **Action для plan.md**.
  - **«Picker open»** (FR-024) — system Intent, no project action vocabulary needed. ✅

  **Action для plan.md**: action vocabulary table:
  - drag start trigger: long-press how long (300ms? 500ms?)
  - drop targets visibility: always vs on-drag
  - cross-flow drag mechanism

- [ ] **CHK008 — Button labels are exact strings (or token IDs), not "Confirm-style label"**

  ⚠️ **GAP — significant.** Spec.md содержит много пользовательских строк inline, но они не вынесены в string-resources и часто немного отличаются друг от друга:

  - **«+ контакт»** (US-3, FR-023) — кнопка label.
  - **«Опубликовать»** (US-1 AS2/AS3, FR-014) — explicit.
  - **«Сохранить»** (FR-014) — explicit.
  - **«есть несохранённые изменения»** (US-1 AS2/AS5) — banner text.
  - **«Откатить к этой версии»** (FR-040, US-5 AS2) — button label.
  - **«История»** (US-5, FR-039) — menu item label.
  - **«Редактирую: <displayName Managed> / экран ~<size>" / <tiles per row>»** (FR-003) — header text template.
  - **«Добавить контакт <displayName>/<phoneNumber> в раскладку: <выпадающий список Managed>»** (FR-029) — screen text.
  - **«Контакт без номера телефона не может быть добавлен в текущей версии»** (FR-031) — error message.
  - **«Зачем нужно разрешение читать контакты»** (FR-023, US-3 AS1) — rationale title.
  - **«Контакты, которые вы добавите, сохраняются в облаке Firebase и видны на устройстве вашего родственника. Вы можете удалить их в любой момент через Settings → Добавленные контакты.»** (FR-033c) — full privacy disclosure text.
  - **«🔴 Заряд: 3% (критично)», «🔴 Звонок: выключен!», «🟢 Wi-Fi», «2 мин назад»** (US-2 AS2) — health text + emoji icons.
  - **«сейчас», «N мин назад», «N часов назад», «N дней назад», «(последняя известная)»** (FR-022) — lastSeen formatting.
  - **«Эта версия несовместима с текущим приложением, откат невозможен»** (edge case L147) — snapshot incompatibility message.
  - **«Нет соединения и нет локальной копии — попробуйте позже»** (edge case L144) — offline-no-cache message.
  - **«Офлайн, изменения применятся при появлении сети»** (edge case L145) — offline-with-cache message.
  - **«Не удалось добавить контакт: <причина>»** (FR-026) — validation error wrapper.
  - **«Не удалось прочитать контакт»** (FR-028) — VCard parse error.
  - **«вы удалили все вкладки, бабушка увидит пустой экран; продолжить?»** (edge case L148) — destructive confirmation.
  - **«Добавленные контакты»** (FR-033a) — privacy screen title.
  - **«текущая»** (FR-039, US-5 AS1) — badge label.
  - **«Изменить / Переместить в / Удалить»** (FR-009) — context menu items.

  ⚠️ **Issues:**
  - **«бабушка»** в string «бабушка увидит пустой экран» — assumes Managed is grandmother. ❌ Should be neutral: «получатель увидит пустой экран» или «на телефоне вашего родственника появится пустой экран».
  - **Emoji в health text** (🔴 🟢) — accessibility issue (TalkBack reads emoji unpredictably). **Action**: replace with proper icons + content descriptions; emoji в spec — иллюстративные, не финальный UI.
  - **«в текущей версии»** в FR-031 — implies future fix; OK as user expectation framing.

  **Action для plan.md**:
  1. Extract все строки в `res/values/strings_admin_editor.xml` (RU primary).
  2. Replace «бабушка» → neutral framing в строках, где applicable.
  3. Replace emoji icons → vector icons + contentDescription.
  4. Review/finalize tone of error messages (FR-026, FR-028, FR-031).

## Consistency

- [x] **CHK009 — In-Scope and Functional Requirements align**

  ✅ Каждый US имеет FRs:
  - US-1 → FR-001..016 (editor + publish + merge).
  - US-2 → FR-017..022 (health monitoring).
  - US-3 → FR-023..026, FR-033 (contacts picker).
  - US-4 → FR-027..033 (VCard).
  - US-5 → FR-036..043 (history + rollback).
  - US-6 → FR-005, FR-042 (symmetry для Managed editor).
  - US-7 → FR-034..035 (OpenApp + Play Store fallback).
  - Privacy minimum → FR-033a/b/c (added 2026-05-15).
  - Bug fixes → FR-046 (TileCard icon), FR-046a (backup exclusion).
  - Security rules → FR-044..045b.

  ✅ No orphan FRs. ✅ No US без FR coverage.

- [ ] **CHK010 — Confirmation policy consistent**

  ⚠️ **GAPS.** Spec.md разрозненно упоминает confirmations:

  - **Rollback** (FR-041, US-5 AS3) — explicit «диалог подтверждения». ✅
  - **«Удалили все вкладки»** (edge case L148) — explicit «продолжить?». ✅ (но не FR'ом).
  - **Discard pending draft** — НЕТ упоминания. Если admin в edit-mode → back → unsaved changes → silent save или confirm? US-1 AS5 implies silent (draft restored). **OK to be silent** since draft auto-persists per FR-014a.
  - **Delete контакт через privacy list** (FR-033b) — «no soft delete» implies immediate, но **confirmation** перед immediate delete? Spec.md не явно. **Action**: уточнить — GDPR «immediate» != «no confirmation»; confirmation OK.
  - **Delete плитка через «···»** (FR-009) — undo / confirmation? Inherits drag-to-trash без confirmation? **Action для plan.md**.
  - **Delete flow** (FR-005a `onDeleteFlowClick`) — confirmation? Особенно если flow содержит plitki. **Action для plan.md**.
  - **Rollback к snapshot, который перепишет current** — текущий confirmation FR-041 покрывает, но если current = unpublished draft → дважды confirm (lost draft + rollback)?
  - **Push при наличии конфликта** — Merge UI inherited 008 FR-050, не нужен extra confirm. ✅
  - **Publish vs Save** дисциплина (FR-014) — раздельные кнопки = пользователь явно выбирает, не нужен confirm. ✅

  **Action для plan.md**: explicit confirmation policy table:
  - Rollback ✅ YES (irreversible push)
  - Empty flows / delete flow ✅ YES (destructive)
  - Delete contact в privacy list ✅ YES (recommend)
  - Delete plitka via «···» — TBD (drag-to-trash without confirm OK?)
  - Discard draft on back — NO (auto-persist per FR-014a)

- [ ] **CHK011 — Multi-tap / accidental-double-tap protection consistent across action surfaces**

  ⚠️ **GAP** — Spec.md не специфицирует debounce policy.

  Critical surfaces:
  - **«Опубликовать»** button — double-tap могло бы создать два push'a. Idempotent через optimistic concurrency (FR-013/022 спека 8), но второй получит conflict + Merge UI confusion. **Action для plan.md**: debounce ~500ms (наследуется из спека 8 если уже введён).
  - **«Откатить»** — double-tap идёт через confirmation dialog → second tap уже на dismissed dialog. ✅ self-protected.
  - **«+» в flow** — multiple taps → multiple slot edits? Каждый tap открывает форму → форма сама protects. ✅
  - **Long-press → drag** — accidental drag start от случайного long-press. Standard Android UX. ✅

  **Action для plan.md**: explicit debounce для «Опубликовать», «Сохранить», «Откатить» (~500ms).

## Acceptance — measurability

- [x] **CHK012 — Each US has explicit Given/When/Then or numbered acceptance scenario**

  ✅ Verified: 7 US, каждая имеет 3-5 numbered Acceptance Scenarios в G/W/T form.
  - US-1: 5 scenarios (L31-36)
  - US-2: 4 scenarios (L50-53)
  - US-3: 4 scenarios (L67-70)
  - US-4: 4 scenarios (L84-87)
  - US-5: 4 scenarios (L101-104)
  - US-6: 3 scenarios (L118-120)
  - US-7: 3 scenarios (L136-138)

  Total: **27 acceptance scenarios**, all G/W/T format.

- [x] **CHK013 — Success criteria measurable per UX moment**

  ✅ SC-001..008 all measurable:
  - SC-001: «≤ 90 секунд» end-to-end (open → push).
  - SC-002: «≤ 35 сек» battery drop → Critical indicator.
  - SC-003: «≤ 60 секунд» share-flow для опытного admin'а.
  - SC-004: «≤ 60 секунд» rollback flow.
  - SC-005: «100% snapshot'ов с `schemaVersion = 1`» корректно читаются.
  - SC-006: «95% сценариев» drag-and-drop 10 подряд.
  - SC-007: «100% покрытие» «···» menu vs drag.
  - SC-008: «100% сценариев» Call dispatch на Managed без READ_CONTACTS.

  ⚠️ **Watch SC-003**: «admin'а, привычного к приложению» — subjective («привычный»). **Recommendation**: уточнить «после 3 успешных предыдущих share'ов» или подобное. Minor, не блокирует.

- [ ] **CHK014 — Returning-user UX (second-launch, resume from background) defined or excluded**

  ⚠️ **GAPS.**

  - ✅ **Draft survives process kill / app restart** — explicit в FR-014a. ✅
  - ✅ **Draft restored при возврате в editor** — US-1 AS5.
  - ⚠️ **Returning после длительного отсутствия** (app backgrounded для дней): admin видит stale Managed list? Stale health values? Auto-refresh при resume? Spec.md inherits Firestore listener cadence из FR-020, но **explicit AC отсутствует**.
  - ⚠️ **Returning в VCard share flow** — admin shared контакт, переключился на другое app, вернулся: backstack preserved? `Q-OPEN-2` (L483).
  - ⚠️ **Returning в editor с pending draft + параллельный push от другого editor'а** — admin вернулся → pull обновил applied? Conflict при следующем push? Inherited 008 FR-050, но не явно сослано в 009.

  **Action для plan.md**: returning-user explicit AC per screen:
  - List → resume → refresh health snapshots within Xс.
  - Editor → resume → draft preserved (already FR-014a); pull applied для current?
  - VCard share flow → backstack policy (Q-OPEN-2).

## Coverage — alternative paths

- [x] **CHK015 — Every primary action has its negative-path UX defined**

  ✅ Mostly covered through Edge Cases L142-153 (11 edge cases):
  - Offline + no cache → message (L144).
  - Offline + cache → banner + queued (L145).
  - Push fail after history write → accept loss (L146).
  - Snapshot incompatible → blocked + message (L147).
  - Empty flows → warning (L148).
  - Contact deleted from system → keep work (L149).
  - History overflow → housekeeping race (L150).
  - Malicious VCard → 10KB reject (L151).
  - Managed unpaired → vanish from list (L152).
  - Concurrent edits → Merge UI (L153).

  ⚠️ **Watch**:
  - **Permission denied — never ask again** (READ_CONTACTS) — не покрыт. Что admin видит? Кнопка «+ контакт» disabled? «Откройте Settings»?
  - **Picker cancelled** — silent return? Toast?
  - **VCard from app with no share — error path**? E.g., intent fired without payload.
  - **OpenApp picker — нет установленных приложений на admin'e** (edge case) → ручной ввод как fallback? FR-034 implies «(б) ручной ввод» always available. ✅

  **Action для plan.md**: explicit negative-path states для permission denied + picker cancelled.

- [x] **CHK016 — Multiple entry points yield consistent UX**

  Spec 009 entry points:
  - **Cold start admin app** → list of Managed (U1).
  - **Cold start Managed (через 7-tap)** → editor of own config (US-6).
  - **VCard share intent from external app** → target picker (U6) → editor (U7).
  - **Deep-link from FCM** — out-of-scope (`OUT-009`).

  ✅ Все consistent (одно приложение, разные конфиги, тот же editor pipeline per FR-005). ⚠️ **Watch для plan.md**: VCard entry в editor (U7) должен использовать **тот же** editor screen, что и normal entry from list (US-1) — не отдельный «add contact» screen. FR-030 implies same screen, но не явно. **Plan.md confirm**.

- [x] **CHK017 — Long-pause scenarios (user leaves app for hours) have defined return-UX**

  ✅ Largely covered:
  - Draft persists через Room (FR-014a).
  - Firestore SDK offline cache (FR-002, A-4).
  - Health listener cadence per severity (FR-020).
  - Managed list refresh inherited из спека 7.

  ⚠️ **Minor watch** (CHK014): explicit AC «admin вернулся через сутки» отсутствует, но implicit OK.

## Non-functional UX

- [ ] **CHK018 — Accessibility deferred to `checklist-accessibility` if relevant**

  ⚠️ **A11y deferred** to separate checklist run, но spec 009 имеет несколько a11y-critical surfaces:

  - **FR-008 long-press drag** — TalkBack users **не могут** делать long-press effectively → FR-009 «···» menu **обязательна** как parallel channel. ✅ explicit в spec.md (FR-009).
  - **Health emoji 🔴 🟢** в FR text — TalkBack reads unpredictably. **Plan.md**: vector icons + contentDescription.
  - **Drag-and-drop без drop target announce** — TalkBack flow при cross-flow drag — нужен announce.
  - **Tile editor «···» menu** — должен быть accessible focus order.
  - **«+» buttons** — нужны contentDescription («Добавить плитку», «Добавить вкладку»).

  **Recommendation**: запустить `checklist-accessibility` отдельно для 009 (high priority).

- [ ] **CHK019 — Localisation deferred to `checklist-localization` if relevant**

  ⚠️ **L10n deferred**. Все strings в spec.md на русском.

  - lastSeen formatting (FR-022) — Russian plurals («1 мин назад», «2 мин назад», «5 мин назад») — нужна локализация-aware форма. Russian plurals имеют 3 формы. **Plan.md**: использовать `plurals.xml`, не concat.
  - Numbers с unit («3%», «5 мин») — locale-aware (РФ percent format = `3 %` with NBSP in some style guides). Probably OK.

  **Recommendation**: запустить `checklist-localization` отдельно если EN target планируется.

- [x] **CHK020 — Diagnostic UX (how user sees that something is being tracked)**

  ✅ Privacy minimum (FR-033a/b/c) makes tracking explicit:
  - FR-033a — список «Добавленные контакты» с возможностью удаления → user видит **что** хранится.
  - FR-033c — rationale текст явно сообщает «сохраняются в облаке Firebase и видны на устройстве вашего родственника».
  - Health monitoring (FR-017..022) — admin видит health values сам; нет «hidden tracking».
  - PhoneHealthCriticalEvent (FR-021) — local event, без notification → пользователь не видит «tracking», но не tracking sensitive (свои данные о своём Managed).

  ✅ Diagnostic UX adequate для текущего scope.

## Dependencies / assumptions

- [x] **CHK021 — UX doesn't depend on out-of-scope capabilities**

  ✅ Verified:
  - Editor использует existing rendering pipeline (FR-005, A-9).
  - Health читается из существующего `/links/{linkId}/health` (FR-017, A-3).
  - Push через flow спека 8 (FR-014, A-2).
  - Pairing inherited 007 (A-1).
  - Не зависит от out-of-scope (screen mirroring `OUT-001`, iOS `OUT-002`, pixel-accurate `OUT-003`, multi-Managed `OUT-004`, preset-editor `OUT-005`, watch `OUT-006`, etc.).

  ⚠️ **Watch**: FR-005a (extend `TileCard` / `FlowScreen` / `BottomFlowBar` / `HomeScreen` with edit-mode + drag) — это **существенное расширение** существующих компонентов (per A-9 уточнение). Это **не out-of-scope**, но **значительная работа**, не «просто mode flag». Plan.md должен явно адресовать как «9-spec work», не «inherited from 003/005 with minor flag».

- [x] **CHK022 — Mock-data limitations noted explicitly if they affect rendering**

  ✅ Spec 008 FR-045 уже removed mock storage. Spec 009 строит на real Firestore data из спека 7/8.

  ⚠️ **Watch**: dev/test mode для editor (working без paired real Managed) — implied via fake adapter pattern (`CLAUDE.md` rule 6), but не явно in spec.md. **Recommendation**: plan.md confirm что dev-build имеет fake `LinkRepository` + fake `Health` source для editor isolation testing.

---

## Summary

| Status | Count | Items |
|---|---|---|
| ✅ Pass | 11 | CHK005, CHK009, CHK012, CHK013, CHK015, CHK016, CHK017, CHK020, CHK021, CHK022 |
| ⚠️ Watch / Plan.md action | 11 | CHK001 (Q-OPEN-1/2 + 5 under-specified screens), CHK002 (states), CHK003 (nav graph + back stack), CHK004 (overlay types), CHK006 (subjective «привычный admin» SC-003), CHK007 (drag mechanics), CHK008 (strings + «бабушка» → neutral + emoji → icons), CHK010 (confirmation policy table), CHK011 (debounce), CHK014 (returning-user AC), CHK018 (a11y separate run recommended), CHK019 (l10n separate run recommended) |
| ❌ Fail | 0 | — |

**Verdict: PASS at spec-level with significant UX-design action items для plan.md.**

Spec.md captures **functional** UX behavior хорошо (drag mechanics intent, history flow, health severity, VCard adapter contract, privacy minimum). Gaps — в **screen-level design detail** (overlay types, exact button strings, navigation graph, confirmation policy, debounce). Это естественная граница spec.md / plan.md.

3 Q-OPEN отмечены в spec.md (L482-484): U8 history form-factor, U7 VCard backstack, dp sizes for senior-safe — все три ждут plan.md.

---

## Top UX gaps (для уточнения spec.md vs plan.md)

1. **Q-OPEN-1 — History UI form-factor** (U8): modal / отдельный экран / side-sheet. **One-way door** (влияет на nav graph). **Recommend**: уточнить **в spec.md** (одна строка в FR-039) до /speckit.plan, чтобы plan.md не упёрся в decision.

2. **String resources + neutral framing** (CHK008): inline-строки spec.md содержат «бабушка», emoji 🔴 🟢, и not extracted. **Recommend**: extract в plan.md (это design-level decision), но **«бабушка» → neutral** — это semantic decision, лучше **уточнить в spec.md** одним bullet'ом ниже privacy section.

3. **Confirmation policy table** (CHK010): destructive actions без явной confirmation politики (delete tile via «···», delete flow, delete contact в privacy list). **Recommend**: plan.md action item.

## Recommended additions to spec.md (optional, минимальные)

Если хотите закрыть gaps в spec.md (vs deferring всё к plan.md), добавить:

> **FR-047 (Confirmation для destructive actions)**: Действия «удалить flow» (FR-005a `onDeleteFlowClick`), «удалить контакт» (FR-033a), «откатить к версии» (FR-041), «удалить плитку via «···»» (FR-009) MUST требовать confirmation dialog. Действие «удалить плитку via drag-to-trash» (FR-008) — НЕ требует confirmation (drag — сама по себе intentional gesture, undo via «···» history).

> **FR-048 (String neutrality)**: Все user-facing строки MUST использовать нейтральные термины («ваш родственник», «получатель», «телефон под управлением»), не assumptions о роли («бабушка», «дедушка»). Применимо к: FR-033c rationale text, edge case L148 confirmation, и т.д. Strings extracted в `res/values/strings_admin_editor.xml`.

> **FR-049 (Debounce)**: Buttons «Опубликовать», «Сохранить», «Откатить к этой версии» MUST debounce ≥ 500ms между tap'ами (предотвращение accidental double-push).

> **Q-OPEN-1 resolution в FR-039**: уточнить «История» — **отдельный экран** (full-screen) с back navigation, не bottom sheet / dialog (легче accessible focus + scroll для 10 items).

> **Q-OPEN-2 resolution**: VCard share entry открывает **new task / new Activity**, чтобы share flow не конфликтовал с existing backstack admin app'a (особенно если admin сейчас в editor другого Managed).

Все 5 — **optional**, plan.md может покрыть как design decision. Решение — за пользователем.

---

## TL;DR (на русском)

**Verdict: PASS на spec-level.** Spec 009 хорошо описывает функциональное UX поведение (drag intent, history flow, health severity, VCard adapter, privacy minimum, 8 measurable SC). 27 G/W/T acceptance scenarios across 7 US.

**Что нашли:**
- ✅ **11 пунктов pass**: терминология консистентна, FR↔US покрытие 1:1, SC measurable, privacy minimum (FR-033a/b/c) делает tracking прозрачным, негативные пути в edge cases L142-153.
- ⚠️ **11 пунктов watch / plan.md action**: form-factor «Истории» (Q-OPEN-1), backstack VCard share (Q-OPEN-2), exact overlay types (toast/dialog/sheet), exact строки (включая «бабушка» → нейтральный язык + emoji → icons), confirmation policy для destructive actions, debounce buttons, drag-and-drop mechanics (cross-flow specifics, корзина visibility).
- ❌ **0 fail**.

**Top-3 UX gap'a:**
1. **Q-OPEN-1 form-factor «Истории»** — one-way door, влияет на nav graph. Уточнить в spec.md одной строкой до /speckit.plan.
2. **Neutral strings + «бабушка» framing** — semantic decision, не purely design; recommend поднять в spec.md.
3. **Confirmation policy table** — какие destructive actions требуют «вы уверены?». Plan.md action item.

**Нужно ли уточнять spec.md?** Опционально. 5 предложенных мелких FR (FR-047 confirmation, FR-048 neutral strings, FR-049 debounce + Q-OPEN-1/2 inline resolutions) закроют большинство gaps в spec.md. Без них — plan.md покроет как design decisions. Решение — за пользователем.

**A11y и l10n** не запускались отдельно; для 009 **highly recommended** прогнать `checklist-accessibility` (drag-and-drop + TalkBack + emoji в health) и `checklist-localization` (Russian plurals в FR-022 lastSeen formatting).
