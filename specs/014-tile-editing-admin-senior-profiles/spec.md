# Feature Specification: Tile Editing — Admin and Senior Profiles

**Feature Branch**: `014-tile-editing-admin-senior-profiles`
**Created**: 2026-05-29
**Status**: Draft
**Input**: User description: "Настройка лаунчера — добавление, перемещение, удаление плиток. Один универсальный механизм, который работает в трёх контекстах: admin self-config, admin remote-config бабушкиного телефона, senior local self-config через 7-tap. Два UX-профиля: admin (mainstream Android-конвенции — long-press, drag-to-X, snackbar undo, jiggle, виджеты в picker'е) и senior (видимые «+» кнопки, кнопки «↑/↓» вместо drag, modal confirm на удаление, нет виджетов). Профиль выбирается по target preset'у (Workspace = admin, Simple Launcher = senior). Не пиксельная мирроринг — общий ConfigDocument, рендер локально с edit-affordances в зависимости от профиля. Визуальная рамка + banner при редактировании чужого target'а. Базируется на исследовании UX-паттернов профессиональных лаунчеров 2026-05-28 (Nova, BIG, Niagara, Pixel, Microsoft, Smart Launcher) и accessibility blogs (NN/g, UXmatters, AFB AccessWorld)."

---

## Контекст и цель спека

### Зачем существует этот спек

Все feature'ы лаунчера (контакты, документы, приложения, target devices) **сводятся к плиткам** на главном экране. Без работающей **настройки плиток** — добавления, перемещения, удаления — ни одна из feature'ов не используется на полную: admin не может разместить контакт, бабушка не может убрать ненужную плитку, target-устройство не настраивается удалённо.

До сих пор настройка реализована **частично**:
- В админ-режиме спеки 009 есть `EditorScreen` для target editing (через сопряжённое managed устройство).
- Drag-and-drop существует в `TileDragAndDropModifiers.kt` / `TileDragAndDropState.kt`.
- 7-tap gate существует в `SevenTapGateModifier.kt`.
- Add slot wizard существует с типами Contact / App / Document (расширен в спеке 012).

Но **не существует**:
- Self-editing для admin (Workspace на своём устройстве).
- Локального edit-mode для senior после 7-tap (текущий 7-tap ведёт в admin devices, не в edit самого Simple Launcher'а).
- Профильно-зависимого UX (admin Workspace и Simple Launcher используют разные паттерны).
- Унифицированной концепции «один Editor, три контекста».

F-014 закрывает этот gap.

### Что НЕ строит этот спек

- **Никакой новой crypto/storage инфраструктуры** — ConfigDocument уже есть (спека 008), ConfigEditor port уже есть.
- **Никакого vCard share-target** — отложено до отдельной спеки после vCard parser infrastructure.
- **Никаких новых типов плиток** — Contact / App / Document / TargetDevice уже определены в предыдущих спеках. F-014 — про **операции** над плитками.
- **Никакого pixel-mirroring** — мы не показываем admin'у бабушкин экран точь-в-точь. Показываем **структурно**, рендерим локально из общего ConfigDocument (Figma multiplayer pattern).
- **Никакого Tutorial / Onboarding overlay** — отложено до отдельной спеки UX-polish, не блокирует F-014.
- **Никакого «Recently deleted / Trash bin» 30-day retention** — отложено до отдельной спеки (записан в backlog как future enhancement).
- **Никакого Personal vault** — это другой примитив (TODO-FUTURE-SPEC-005).
- **Никакой F-5 ConfigDocument encryption** — отдельная спека, F-014 пишет в plaintext config (пока), будет автоматически зашифрован после F-5.

### Архитектурная роль

F-014 — это **унифицированный editing layer** поверх существующего ConfigDocument. Внедряет концепцию **UX profiles** (admin / senior), выбираемых по target preset'у. Domain (core) общий, presentation двух разных видов.

Согласуется с CLAUDE.md rule 9 (preset-driven) и Article V §3 (modular delivery — admin-edit-profile / senior-edit-profile как presets-внутри-app).

### One-way doors и exit ramps

| Решение | One-way? | Exit ramp |
|---|---|---|
| Два разных UX-профиля (admin / senior) вместо одного компромисса | ✅ Yes — wire-format profile-selection становится частью ConfigDocument | Если решим унифицировать — domain не меняется, только presentation rules. Cost = переписать senior profile views. |
| Profile selection по target preset (не по user role) | ❌ Two-way | Если решим выбирать профиль по чему-то другому (user setting, time-of-day) — добавляется в presentation layer без изменений domain. |
| Operations над ConfigDocument через существующий ConfigEditor port | ❌ Two-way | Уже зафиксировано в спеке 008. |
| Структурный shared editor (не pixel-mirror) | ✅ Yes — assumes admin и senior рендерят одну и ту же структуру | Pixel-mirror добавляется как отдельная спека (mobile screen casting), не отменяет F-014. |
| 30-day retention не делается | ❌ Two-way | Может быть добавлено как future enhancement без изменений wire-format'а — добавляется новая коллекция `/deletedTiles/`. |

---

## User Scenarios & Testing *(mandatory)*

<!--
  F-014 — это infrastructure/UX spec, который работает в 3 контекстах с
  разными UX profile'ами. User stories выражены через эти контексты + два
  профиля.
-->

### User Story 1 — Admin настраивает свой Workspace на своём телефоне (Priority: P1)

Admin (взрослый родственник) только что установил app, выбрал Workspace preset. Видит home screen со своим (пока пустым) layout'ом. Хочет добавить пару плиток для быстрого доступа (любимые контакты, нужные приложения), переставить их, ненужную убрать.

**Why this priority**: без self-editing для admin'а — app для admin'а не функционален. Это **первая точка контакта** нового пользователя с editing.

**Independent Test**: запустить app на эмуляторе в Workspace preset, без сопряжения с любыми Managed устройствами. Long-press пустого места → войти в edit mode. Тап «+» → выбрать тип «Приложение» → выбрать app → плитка появляется. Long-press плитки → drag → отпустить в новой ячейке → плитка переместилась. Drag плитки в зону X сверху → плитка удалена + snackbar Undo. Тап «Готово» → выход из edit mode.

**Acceptance Scenarios**:

1. **Given** admin в Workspace preset, home screen рендерится из своего ConfigDocument, **When** admin делает long-press пустого места, **Then** появляется bottom sheet с действиями (Виджеты / Обои / Настройки) + home screen входит в edit mode (плитки слегка дрожат, появляется banner «Готово» сверху).
2. **Given** admin в edit mode, **When** тапает «+» в пустой ячейке, **Then** открывается unified picker с tabs (Приложения / Контакты / Виджеты / Документы / Действия).
3. **Given** admin в edit mode, **When** делает drag плитки в зону «×» сверху экрана, **Then** плитка удаляется, появляется snackbar «Удалено: <label>. Отменить» на 8 секунд.
4. **Given** admin в edit mode, **When** тапает «Готово» в banner'е, **Then** edit mode заканчивается, плитки перестают дрожать, banner исчезает, изменения сохранены в ConfigDocument.

---

### User Story 2 — Admin удалённо настраивает Simple Launcher бабушки через сопряжённое устройство (Priority: P1)

Admin сопряжён с Managed устройством бабушки (через спеку 007). Admin в своём Workspace проваливается в target editing — открывает Editor для бабушкиного Simple Launcher'а. **Видит структурный рендер** её layout'а (примерно 2×3 сетка, такая же как у неё), но **через UX-профиль senior** (видимые «+» в пустых ячейках, нет drag-and-drop, кнопки «↑/↓» для перемещения). Добавляет/удаляет/перемещает плитки → push'ит через ConfigEditor → изменения уходят в Firestore → бабушкин телефон получает push (спека 007) → applier применяет → бабушка видит новый layout.

**Why this priority**: основной use case продукта — admin curate'ит бабушкин telephone. Без этого spec'и 011/012 (контакты, фото, документы) повисают «в воздухе».

**Independent Test**: эмулятор admin (Workspace) + эмулятор Managed (Simple Launcher) сопряжены. Admin Editor → выбрать paired device → видна структура layout'а target'а + **рамка вокруг grid** + banner «Редактируешь телефон Маши» с кнопкой «← Назад». Все operations (add via «+», move via кнопки, remove via «×») работают **по senior profile**, не admin (длительный нажим не работает, drag отключён). Изменения push'атся в Firestore, на Managed устройстве applier применяет (через 2-5 сек), home обновляется.

**Acceptance Scenarios**:

1. **Given** admin сопряжён с Managed бабушки, **When** admin открывает Editor для этого target'а, **Then** рендерится структурный layout (2×3 grid) с senior-profile UI (видимые «+» в пустых, нет drag-affordances), + рамка вокруг grid + top banner «Редактируешь телефон Маши» + кнопка «← Назад».
2. **Given** admin в target Editor, **When** тапает «+» в пустой ячейке, **Then** открывается unified picker, но **без вкладки Виджеты** (senior profile скрывает виджеты).
3. **Given** admin в target Editor, **When** тапает «×» на плитке, **Then** появляется modal dialog «Удалить ‘Звонок маме’? [Отмена] [Удалить]» (senior profile вместо snackbar — diaalog confirm).
4. **Given** admin в target Editor, **When** тапает «Назад» в banner'е, **Then** возвращается в свой Workspace, изменения сохранены в target ConfigDocument'е, push отправлен.
5. **Given** admin изменил target ConfigDocument, **When** Managed телефон получает push и applier применяет, **Then** в течение ≤5 секунд бабушкин home screen рендерится с новым layout'ом.

---

### User Story 3 — Бабушка сама настраивает свой Simple Launcher через 7-tap (Priority: P2)

Бабушка хочет переставить плитку «Звонок маме» с правого нижнего угла на левый верхний — там ей удобнее тыкать большим пальцем. Тапает 7 раз в пустое место между BottomFlowBar и плитками. После 7-го тапа открывается challenge gate (спека 010), проходит. **Попадает в edit mode своего же Simple Launcher'а** — видит свой layout + видимые «+» в пустых + «×» на плитках + кнопки «↑/↓» на каждой плитке.

**Why this priority**: senior может настраивать сама, **если способна** (cognitive ability). Это **не основной use case** (большинство senior'ов не настраивают сами), но critical для self-determination. P2 потому что без неё ничего не ломается; admin может сделать то же самое удалённо.

**Independent Test**: эмулятор Simple Launcher preset. Тапнуть 7 раз в течение 5 секунд в пустом месте между плитками и BottomFlowBar. Проходит challenge gate (через PIN или какой механизм; уже реализовано в спеке 010). После прохождения попадает на **тот же** Editor screen что и admin remote-edit, но с **локальным** target'ом (свой ConfigDocument), без banner'а (не редактирует чужой), но с senior profile UX. Перемещение через «↑/↓», удаление через «×» + modal confirm, добавление через «+» + unified picker без виджетов.

**Acceptance Scenarios**:

1. **Given** бабушка на Simple Launcher home screen, **When** тапает 7 раз в пустое место между BottomFlowBar и плитками в течение 5 секунд, **Then** показывается challenge gate (спека 010 механизм).
2. **Given** бабушка прошла challenge, **When** challenge success, **Then** попадает в edit mode своего Simple Launcher'а — senior profile UX, без banner «Редактируешь чужое» (это её собственный target).
3. **Given** бабушка в edit mode, **When** тапает «↑» на плитке, **Then** плитка перемещается на одну позицию вверх в grid (или меняется местами с верхней соседней).
4. **Given** бабушка в edit mode, **When** тапает «×» на плитке, **Then** появляется modal dialog «Удалить ‘Звонок маме’? [Отмена] [Удалить]».
5. **Given** бабушка завершила edit, **When** тапает «Готово», **Then** edit mode заканчивается, ConfigDocument сохранён локально (зашифровано после F-5).

---

### User Story 4 — Admin переключается между своим Workspace и target editing бабушки (Priority: P2)

Admin одновременно поддерживает: (а) свой Workspace (любимые контакты + приложения для собственного использования), (б) layout бабушки Маши. Хочет быстро переключаться: посмотреть, что в его собственном workspace, потом провалиться в Машу, поправить, выйти, проверить деда Ивана.

**Why this priority**: multi-target scenario — vision из roadmap (admin curate'ит N бабушек/дедушек). P2 потому что MVP может работать без удобной навигации между target'ами (через Settings → Сопряжённые устройства).

**Independent Test**: admin Workspace + 2 paired Managed устройств. Admin должен иметь доступ к: (1) своему Workspace home, (2) Editor target #1, (3) Editor target #2 — все три как top-level navigation destinations, без длинных «провалов через Settings».

**Acceptance Scenarios**:

1. **Given** admin в Workspace home, **When** в BottomFlowBar тапает вкладку «Управление устройствами» (Flow с `templateId="admin_devices"`), **Then** видит список своих paired Managed как плитки (это уже existing behavior из спеки 009).
2. **Given** admin на вкладке «Управление устройствами», **When** тапает плитку «Маша», **Then** провалуется в Editor её target'а (с senior profile + рамкой + banner'ом).
3. **Given** admin в Editor target Маши, **When** тапает «Назад», **Then** возвращается на вкладку «Управление устройствами», не теряет место.

---

### Edge Cases

- **Empty workspace на admin'е**: первая установка, ConfigDocument пустой. Home screen рендерит пустую 2×3 сетку с видимым «+» в каждой ячейке (даже не в edit mode — потому что это явная affordance). Long-press пустого места всё равно работает.
- **Concurrent edit conflict**: admin редактирует target бабушки удалённо, в это же время бабушка через 7-tap локально тоже редактирует. Resolved через optimistic concurrency спеки 008 (`stale version → retry`). UX: admin видит snackbar «Кто-то ещё редактирует. Обновить?», бабушка — то же самое.
- **Profile mismatch при provider unavailable**: admin'ский Workspace включает widget от приложения, которое не установлено на бабушкином target. Widget не рендерится на target → admin получает warning при попытке добавить виджет в target editor.
- **Drag-and-drop в senior profile отключён**: пытается senior drag'ать → ничего не происходит (но не show error — silent ignore). Edit обновления только через «↑/↓» кнопки.
- **Last tile remove**: senior удалил последнюю плитку → home screen пустой → но всё ещё имеет видимое «+» в каждой ячейке (нечего ломать).
- **«Готово» нажата без изменений**: edit mode закрывается, ConfigEditor не вызывается (нечего push'ить).
- **Network offline при target editing**: admin меняет target бабушкин config offline → изменения сохраняются в `LocalConfigStore.pending` (спека 008) → при появлении сети push'ится автоматически.
- **Target preset изменился между сессиями**: admin когда-то редактировал бабушкин Simple Launcher; бабушка через 7-tap сменила preset на Workspace. Admin при следующем открытии target Editor видит admin-profile UX (потому что target теперь Workspace).
- **7-tap случайное срабатывание у бабушки**: попала пальцем в правильное место 7 раз случайно (это редко, но возможно). Challenge gate (спека 010) защищает от ложного входа.

---

## Requirements *(mandatory)*

### Functional Requirements — Domain (ConfigDocument operations)

- **FR-001**: System MUST expose в `core/domain/` domain verbs для операций над ConfigDocument.Flow.slots[]: `addSlot(flowId, slot)`, `removeSlot(flowId, slotId)`, `moveSlot(flowId, slotId, newPosition)`, `replaceSlot(flowId, slotId, newSlot)`. Все операции возвращают `Outcome<ConfigDocument, EditError>`.
- **FR-002**: System MUST использовать существующий `ConfigEditor` port (спека 008) для persisting изменений. `updateDraft { config -> config.copy(flows = newFlows) }`. Никаких новых wire-format'ов не вводится.
- **FR-003**: System MUST поддерживать операции над **обоими** target'ами: self-config (admin'ский linkId = "self" или derived от own pub) и remote-config (linkId сопряжённого Managed). API одинаковая — разный только linkId argument.
- **FR-004**: System MUST поддерживать операции в **обоих** профилях (admin / senior). Domain верба одинаковые; разница только в presentation rules (что можно делать через какие affordances).

### Functional Requirements — Edit mode entry

- **FR-005**: System MUST поддерживать вход в edit mode из admin Workspace через **long-press пустого места** на home screen (admin profile).
- **FR-006**: System MUST поддерживать вход в edit mode на бабушкином Simple Launcher через **7-tap gesture** (existing, спека 010 FR-021) → challenge gate → edit mode.
- **FR-007**: System MUST поддерживать вход в Target Editor (admin редактирует remote бабушкин Simple Launcher) через **тап на target плитке** в Flow с `templateId="admin_devices"` ИЛИ через Settings → Сопряжённые устройства → конкретное устройство → «Редактировать».

### Functional Requirements — Profile selection

- **FR-008**: System MUST выбирать UX profile по **target preset'у**:
  - Target preset = Workspace → admin profile UX.
  - Target preset = Simple Launcher → senior profile UX.
  - Target preset = (future presets) → fallback to admin profile (по умолчанию).
- **FR-009**: System MUST применять profile **независимо** от того, кто редактирует (admin remote vs senior local). Admin редактирует Simple Launcher → senior profile. Бабушка с продвинутым телефоном настраивает Workspace на себе → admin profile.

### Functional Requirements — Admin profile UX

- **FR-010**: System MUST в admin profile предоставлять:
  - Edit mode entry через long-press пустого места.
  - Add tile через «+» в пустой ячейке ИЛИ через long-press app drawer → drag (drawer — out of scope F-014, deferred).
  - Move tile через drag-and-drop (1.1x scale, 8dp elevation, snap-to-cell).
  - Remove tile через drag в зону «×» сверху экрана.
  - Undo через Material 3 snackbar 8 секунд после remove.
  - Edit mode indicator: jiggle (2°, 0.4с) + top banner «Готово».
  - Edit mode exit через «Готово» banner OR tap-anywhere.
  - Unified picker для add — все типы плиток (Приложения / Контакты / Виджеты / Документы / Действия) в одной модалке.
- **FR-011**: System MUST уважать `prefers-reduced-motion` в admin profile — если включено, jiggle не показывать, заменять статической рамкой вокруг плиток.

### Functional Requirements — Senior profile UX

- **FR-012**: System MUST в senior profile предоставлять:
  - Edit mode entry через 7-tap → challenge gate (existing) ИЛИ admin remote editing.
  - Add tile через видимый «+» в пустых ячейках (всегда видимый, не только в edit mode).
  - Move tile через кнопки «↑» / «↓» / «←» / «→» на плитке (в edit mode). Drag-and-drop **отключён**.
  - Remove tile через красный «×» в углу плитки → modal dialog «Удалить ‘<label>’? [Отмена] [Удалить]».
  - **Нет snackbar undo** — modal dialog уже даёт chance отменить.
  - Edit mode indicator: **top banner** «Готово» (текстовый). **Без jiggle**.
  - Edit mode exit **только** через явную кнопку «Готово» (не tap-anywhere — защита от случайного выхода).
  - Unified picker для add — типы плиток **без вкладки «Виджеты»** (Приложения / Контакты / Документы / Действия).
- **FR-013**: System MUST в senior profile использовать **tap-target ≥56dp** для всех edit-affordances («+», «×», «↑/↓»). Per Article VIII §7.

### Functional Requirements — Remote editing visual indicators

- **FR-014**: System MUST при remote target editing (admin редактирует чужой ConfigDocument) показывать:
  - **Рамку 4dp** вокруг grid'а (colored, отличает от self-editing).
  - **Top banner** с текстом «Редактируешь телефон <name>» + кнопка «← Назад».
- **FR-015**: System MUST при self-editing (admin'ское self OR senior local) **не показывать** рамку/banner — это свой target.

### Functional Requirements — Concurrent edit conflict

- **FR-016**: System MUST detect concurrent edits через `ConfigEditor.pushPending` returning `ConfigSyncError.Conflict` (спека 008). Resolution: snackbar admin'у «Кто-то ещё редактирует. [Обновить] [Перезаписать]».
- **FR-017**: System MUST при «Перезаписать» использовать `pushPending(force=true)` (спека 008 mechanism). При «Обновить» — re-fetch + merge UI.

### Functional Requirements — Tile types в picker'е

- **FR-018**: System MUST в admin profile picker предлагать следующие tile types: Application (existing OpenApp slot), Contact (existing Contact slot, спека 009/011), Document (existing Document slot, спека 012), Widget (NEW — но F-014 только определяет tile-type slot, реальные widget rendering — отдельная спека), Action (NEW — SOS, phone call, flashlight — future).
- **FR-019**: System MUST в senior profile picker предлагать: Application, Contact, Document. **Без Widget, без Action** (deferred).

### Functional Requirements — Empty state

- **FR-020**: System MUST в admin profile при пустом ConfigDocument рендерить пустую 2×3 grid с invisible «+» (видимыми только в edit mode после long-press) — **mainstream Android-конвенция**.
- **FR-021**: System MUST в senior profile при пустом ConfigDocument рендерить пустую 2×3 grid с видимыми «+» в каждой ячейке (**всегда видимыми**) — accessibility-first.

### Key Entities

- **TileEditOperation**: domain operation на ConfigDocument. `{type: Add | Move | Remove | Replace, flowId, slotId?, newSlot?, newPosition?}`.
- **EditMode**: presentation state. `{active: Boolean, target: TargetIdentity, profile: AdminProfile | SeniorProfile}`.
- **TargetIdentity**: who/what is being edited. `{linkId: String, presetId: String, isSelf: Boolean}`.
- **EditUiProfile**: UX rules selector. `sealed class { AdminProfile, SeniorProfile }`. Includes affordances available (jiggle / banner / drag / buttons), picker tabs visibility, undo mechanism.
- **PickerType**: enum для tile types в picker'е. `Application | Contact | Document | Widget | Action`.
- **EditError**: domain error variants. `{InvalidPosition, SlotNotFound, FlowNotFound, ConcurrentEditConflict, NotAuthorized}`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Admin может добавить контакт-плитку в свой Workspace за **≤ 4 тапа** от home screen (long-press → «+» → contact picker → выбрать). Подтверждается UI smoke test.
- **SC-002**: Бабушка может удалить плитку через 7-tap path за **≤ 5 тапов** (7-tap → challenge → «×» → confirm dialog «Удалить»). Подтверждается UI smoke test.
- **SC-003**: Admin remote-edit бабушкиного config'а появляется на бабушкином home screen в **≤ 5 секунд** (network-dependent: Firestore push + applier). Подтверждается integration test.
- **SC-004**: Concurrent edit conflict resolution **никогда не приводит** к split-brain или потере данных — либо admin'ский edit применяется, либо бабушкин, но не оба частично. Подтверждается integration test через Miniflare + 2 параллельных push'а.
- **SC-005**: Profile selection корректен в 100% случаев — admin'ский Workspace всегда показывает admin profile, Simple Launcher всегда senior profile. Подтверждается unit test на `EditUiProfileSelector`.
- **SC-006**: Senior profile **никогда не показывает виджеты** в picker'е (privacy: admin не должен случайно добавить tracker-widget на бабушкин экран). Подтверждается unit test + UI smoke.
- **SC-007**: Жест 7-tap корректно срабатывает в 100% случаев при правильном паттерне (7 тапов в окне 5 сек, ±48dp tolerance). Подтверждается через existing tests спеки 010.
- **SC-008**: APK size delta от F-014 ≤ **300 KB** (только presentation код, никакой новой libraries).

---

## Assumptions

- ConfigDocument как wire-format уже существует (спека 008) и поддерживает Flow + Slot + Contact wire-formats.
- ConfigEditor port (спека 008) уже даёт `updateDraft` + `pushPending` + `pendingDraft` + `appliedConfig`.
- 7-tap gesture infrastructure (спека 010 FR-021) — existing, не переписывается.
- Drag-and-drop infrastructure (`TileDragAndDropModifiers.kt`, `TileDragAndDropState.kt` спека 009/010) — existing, переиспользуется в admin profile.
- Add slot wizard (`AddSlotWizardComponent` + provider registry спека 005) — existing, расширяется в F-014 на unified picker с tabs.
- `FlowTemplate` + `Flow.templateId` (спека 003) — existing, используется для profile selection (косвенно через preset).
- Challenge gate (спека 010) — existing, переиспользуется для senior 7-tap path.
- Senior tap-target ≥56dp + accessibility constraints — existing project constants (Article VIII §7).
- Existing admin `EditorScreen` / `EditorComponent` (спека 009) — будет **расширен** F-014, не переписан с нуля.
- Plaintext ConfigDocument storage — existing (закроется F-5). F-014 пишет в plaintext, после F-5 автоматически зашифрован.

---

## Local Test Path *(mandatory)*

- **Emulator / device**: 2 эмулятора (admin Pixel 7 + Managed Pixel 4a) + Xiaomi (Managed Android 11 для OEM coverage).
- **Fake adapters used**: `FakeConfigEditor` (спека 008), `FakeProviderRegistry` (спека 005), `FakeInstalledAppsCatalog` (спека 005), `FakeContactsRepository` (спека 011).
- **Fixtures / seed data**:
  - `core/src/test/resources/fixtures/empty-workspace.json` — пустой Workspace ConfigDocument.
  - `core/src/test/resources/fixtures/simple-launcher-3-tiles.json` — Simple Launcher ConfigDocument с 3 плитками.
  - `core/src/test/resources/fixtures/concurrent-edit-conflict.json` — pair of ConfigDocument versions для conflict testing.
- **Verification commands**:
  - `./gradlew :core:test --tests *TileEditTest` — domain operations + profile selection (FR-001..FR-009, SC-005).
  - `./gradlew :app:test --tests *EditModeTest` — UI affordances per profile (FR-010..FR-013, FR-018..FR-021, SC-006).
  - `./gradlew :core:test --tests *RemoteEditTest` — visual indicators (FR-014..FR-015) + concurrent edit (FR-016..FR-017, SC-004).
  - Manual: 2-эмулятор smoke test — admin Workspace edit + remote target edit + senior 7-tap edit → assert все 3 flow'а работают.
- **Cannot-test-locally gaps**:
  - **OEM keyboard / IME interactions при text input в picker'е** — Samsung One UI, MIUI могут иметь quirks. → `TODO(physical-device)` для pre-release.
  - **Network latency real-world** при remote edit (Wi-Fi vs 3G vs offline) — Miniflare даёт approximation, но не real-world. → `TODO(production-test)`.
  - **Animations smoothness на low-end OEM devices** (Xiaomi, Vivo с MIUI/FuntouchOS limitations) — нужно тестировать на реальных устройствах. → `TODO(physical-device)`.

---

## AI Affordance *(mandatory)*

- **Exposable capabilities** (для будущих AI agents через Capability Registry, F-2):
  - `suggestTileAddition(targetId, context)` — AI может предложить «добавить плитку Звонок маме на главный экран» на основании user history. **Read-only suggestion**, не autonomous mutation.
  - `summarizeLayoutChanges(targetId, since)` — для AI «recent activity» summary — что admin или бабушка изменили. Read-only audit log access.
  - `findUnusedTiles(targetId)` — AI suggestion «эту плитку никто не нажимал 30 дней, может удалить?». Read-only analytics.
- **Required affordances on data**: read-only access к ConfigDocument для suggestion flows. **Никаких mutating operations (`addSlot`, `removeSlot`)** не могут быть инициированы AI agent'ом autonomously без user confirmation. Все mutations требуют explicit user approval через UI.
- **Provider-agnostic shape**: все exposable capabilities — domain verbs, выраженные через TileEditOperation / ConfigDocument domain types. **Нет** Gemini / OpenAI / Claude типов в signatures. Rule 1 (domain isolation).
- **Out of scope for this spec**: реальная AI provider implementation, MCP server expose, voice triggers, LLM prompt design. Всё это ship'ится в F-2 Capability Registry Foundation.

---

## OEM Matrix

- **Samsung One UI**: drag-and-drop в admin profile — должно работать standard Android API. Возможны quirks при touch-event handling — тестировать на S22/S23.
- **Xiaomi MIUI (Android 11+)**: уже тестируем на Mi 11 Lite 5G. Particular concern: MIUI имеет custom long-press handler в системе — нужно убедиться, что наш long-press не конфликтует.
- **Huawei EMUI**: out of GMS scope (не поддерживаем в MVP). N/A.
- **Pixel stock**: baseline. Должно работать как ожидается.
- **OPPO/Vivo ColorOS/FuntouchOS**: low-end OEM с aggressive battery management. Animation smoothness может страдать. Tested separately.

---

## Зависимости и cross-spec impact

### Extends

- **Спека 008** (Bidirectional Config Sync) — F-014 использует `ConfigEditor.updateDraft` + `pushPending` + `pendingDraft`. Не меняет wire-format.
- **Спека 009** (Admin Mode Flows) — F-014 расширяет existing `EditorScreen` / `EditorComponent` концепцией profile + visual indicators для remote target.
- **Спека 010** (Setup Assistant) — F-014 переиспользует 7-tap gesture FR-021 для senior local edit entry.
- **Спека 003** (UI Skeleton) — F-014 расширяет `FlowTemplate` + `Flow.templateId` для profile selection.
- **Спека 011** (Contacts) — F-014 использует Contact domain type как tile type.
- **Спека 012** (Private Documents) — F-014 использует Document slot kind как tile type.

### Updates

- **`docs/dev/project-constants.md`** — добавить запись «Edit UX profiles (admin / senior)» как новую константу.
- **`docs/dev/project-backlog.md`** — добавить future enhancements:
  - `TODO-UX-025: Tutorial / onboarding overlay для new admin'ов`.
  - `TODO-UX-026: Recently deleted / Trash bin 30-day retention`.
  - `TODO-UX-027: Widget tile-type real rendering (spec follow-up)`.
  - `TODO-UX-028: Action tile-type (SOS, phone, flashlight)`.

### Reference docs

- [docs/research/2026-05-28-contact-sharing-ux-patterns.md](../../docs/research/2026-05-28-contact-sharing-ux-patterns.md) — research artifact #1.
- [docs/research/2026-05-28-shared-editor-deep-dive.md](../../docs/research/2026-05-28-shared-editor-deep-dive.md) — research artifact #2.
- [docs/dev/project-constants.md](../../docs/dev/project-constants.md) — 7-tap, BottomFlowBar, Flow Templates, senior-safe boundaries.
- [docs/product/future/ecosystem-vision.md](../../docs/product/future/ecosystem-vision.md) — Family Group removed, multi-pair architecture context.
- [CLAUDE.md](../../CLAUDE.md) rules 1, 4, 9 (domain isolation, MVA, preset-readiness).
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) Article VIII §7 (senior-safe), Article XI (MVA).

---

## TL;DR на русском

**Что внутри**: настройка плиток (add / move / remove) для трёх контекстов — admin'ский self-Workspace, admin'ское remote editing бабушкиного Simple Launcher'а, бабушкино локальное editing через 7-tap. **Один Editor**, общий ConfigDocument, **два UX-профиля** (admin / senior), выбираемых по preset'у target'а.

**Ключевые решения** (на основе research'а Nova / BIG / Pixel / Niagara / NN/g / UXmatters):
1. **Admin profile**: long-press вход, drag-and-drop, drag-to-X удаление, snackbar undo, jiggle, виджеты в picker'е.
2. **Senior profile**: 7-tap вход, видимые «+» в пустых, кнопки «↑/↓» вместо drag, modal confirm на удаление, без jiggle, без виджетов, tap-target ≥56dp.
3. **Profile выбирается по target preset'у**, не по user role. Admin remote-editing бабушкин Simple Launcher = senior profile (потому что target = Simple Launcher).
4. **Visual frame + banner** при remote target editing — admin сразу видит, что редактирует чужое.
5. **Domain unified**, presentation двух видов. Никакого pixel mirror — структурный shared editor (Figma pattern).

**Что НЕ строится**: vCard share-target, Tutorial, Trash bin retention, new tile types (Widget/Action — placeholders), Family Group (deprecated), ConfigDocument encryption (отдельная F-5), Personal vault.

**Inherent ограничения**: drag-and-drop отключён в senior profile (compromise UX-wise vs accessibility); виджеты скрыты в senior profile (admin не может добавить виджет на бабушкин экран — это by design, privacy/safety).

**Risk**: главный — concurrent edit conflict при admin remote-edit одновременно с senior local edit через 7-tap. Resolved через optimistic concurrency спеки 008 + snackbar admin'у «Обновить / Перезаписать».
