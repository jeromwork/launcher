# User Journeys — Draft Inventory (для обсуждения)

> ⚠️ **DEPRECATED 2026-05-27** — этот документ слишком велик, чтобы обдумывать целиком. Его содержимое **разбито по доменам** в [`docs/product/use-cases/`](use-cases/README.md). Этот файл оставлен как **историческое raw research** — точка отсчёта, из которой выросла per-domain структура.
>
> **Точка входа теперь**: [`docs/product/use-cases/README.md`](use-cases/README.md) — обзорный документ со status tracker'ом и порядком прохода.

---

**Статус**: draft for review · **Дата**: 2026-05-27 · **Автор**: совместная сессия (user + mentor)

> Назначение: собрать **полный набор сценариев использования** приложения, по которым потом строится product vision и roadmap. Это не финальный документ — это материал для проверки и обсуждения. После согласования эти сценарии становятся источником требований и порядка реализации.
>
> Принцип: **пересечения сценариев — это хорошо** (как написал user — лучшее закрепление). Один компонент, обслуживающий N сценариев — это валидация компонента.

---

## 1. Frameworks (что подсмотрели, что берём)

Из ресёрча выделил то, что реально полезно для нашего кейса (companion-app launcher для пожилых):

| Framework | Что даёт | Берём? |
|-----------|----------|--------|
| **Use Case Diagram (UML)** — actor + system + use cases + relations | Понятный для user (он сам сказал «обучался по диаграммам использования»); хорошо ловит, кто из акторов что инициирует | ✅ — основа структуры |
| **User Journey Map** (NN/g) — этапы awareness → consideration → adoption → retention → advocacy | Хорошо ловит, что у одного и того же актора *разные потребности на разных стадиях жизни* с продуктом | ✅ — по acquisition + onboarding |
| **Jobs-to-be-Done (JTBD)** — «человек нанимает продукт на работу» | Срывает фиксацию на фичах; задаёт «зачем» | ✅ — сверху, как фильтр «нужна ли вообще эта спека» |
| **Service Blueprint** — frontstage UI + backstage processes + supporting systems | Полезен для нашего двухустройственного случая (admin + managed + Cloudflare Worker + Firestore) | ✅ — для cross-device сценариев |
| **Current-state vs Future-state journey map** | Помогает отделить «что сейчас сломано» от «как должно стать» | ✅ — две колонки в каждом сценарии |
| **Edge-case / failure scenarios catalog** | Системно ищем what-if'ы (потеря устройства, factory reset, нет интернета) | ✅ — отдельная секция |
| **User scenarios** (повествование от лица актора, в контексте) | Конкретика > абстракция | ✅ — каждая «карточка сценария» написана как мини-нарратив |

Источники в конце документа.

---

## 2. Оси, по которым размножаются сценарии

Каждый сценарий — это **точка в многомерном пространстве**. Чтобы не пропустить — пробегаем по осям и проверяем «а как этот сценарий выглядит на этой оси?».

### Ось A — Актор (кто инициирует)
1. **Managed user** — пожилой человек, главный потребитель.
2. **Admin** — взрослый родственник (внук / сын / дочь), настраивает удалённо. Один или несколько на одного Managed.
3. **Co-admin** — второй admin на ту же бабушку (второй внук, муж дочери). Multi-admin case.
4. **Bystander** — кто-то рядом с Managed-устройством (родственник, сосед), не админ, но может помочь нажать.
5. **Vendor / support** — внешний агент, который теоретически может быть подключён (клиника, скорая, security-центр) — будущая интеграция, спек 011 это уже учитывает.
6. **Сам разработчик / тестировщик** — может ли он локально проверить фичу без двух физических устройств? (Это и есть та боль, которую user поднял по спеку 012.)

### Ось B — Стадия жизни с продуктом
1. **Discovery / acquisition** — как узнают, откуда ставят.
2. **First-time setup** — распаковка и первичная конфигурация.
3. **Daily use** — типичный день.
4. **Re-configuration** — что-то надо поменять (новый контакт, новая плитка, изменился номер).
5. **Recovery** — что-то сломалось / потерялось / забылось.
6. **Migration** — переход на новое устройство (Managed купил новый телефон, admin сменил телефон).
7. **End-of-life** — Managed больше не пользуется (госпитализирован, переехал в дом престарелых, умер). Что с данными, с pairing'ом, с фото?

### Ось C — Режим работы
1. **Happy path** — всё работает.
2. **Degraded path** — что-то частично сломано (нет интернета, FCM не доставился, GMS отсутствует на устройстве).
3. **Empty path** — данных ещё / уже нет (пустая раскладка, нет контактов, нет фото).
4. **Loading path** — данные грузятся (≠ empty!).
5. **Error path** — что-то конкретно упало (permission revoked, account suspended, key expired).
6. **Recovery path** — после катастрофы (factory reset, потеря устройства, кража).

### Ось D — Топология устройств
1. **Single-device, Managed-only** — Managed без admin'а (возможно ли это вообще? Сейчас архитектурой запрещено — это вопрос для vision).
2. **One-admin / one-managed** — типичный случай.
3. **Multi-admin / one-managed** — несколько родственников настраивают одну бабушку (collision risk → спек 008 уже это решает на уровне config sync).
4. **One-admin / multi-managed** — один внук обслуживает бабушку **и** деда (или нескольких пожилых в семье / клинике).
5. **Admin-with-tablet** — admin держит app на телефоне + планшете (одна учётка, два устройства; спек 008 это уже учитывает).
6. **Replacement device** — новый телефон вместо старого (миграция).

### Ось E — Контекст окружающей среды
1. **Сеть**: WiFi есть / WiFi нет / только мобильный / роуминг / совсем офлайн.
2. **Питание**: норма / низкий заряд / устройство в режиме Doze / устройство выключено.
3. **OEM**: Pixel / Samsung / Xiaomi (MIUI) / Huawei (без GMS) / китайский noname без Play Services.
4. **Локация**: дома / в дороге / в больнице / у админа в гостях.
5. **Время**: первые секунды после установки / через год использования / в момент критической ситуации (SOS).
6. **Состояние пользователя**: бодрый / уставший / в стрессе / при ухудшении когнитивных способностей.

### Ось F — Частота
1. **One-time** (первичный setup).
2. **Rare-but-critical** (SOS, factory reset, потеря).
3. **Periodic** (раз в неделю — добавить контакт; раз в месяц — поменять фото).
4. **Daily** (звонок внуку, проверка фото).
5. **Continuous** (фоновая работа лаунчера).

---

## 3. Inventory сценариев

Сгруппированы по **стадии жизни**. У каждого сценария — короткая карточка. **Знак ⊕ N** = пересекается со сценарием N (общие компоненты или общие данные).

> **Discovery / acquisition** (как узнают о приложении, как ставят) — out of scope: закрывается в другом проекте (маркетинг / landing / Play Store).

### 3.1 First-time setup

**S-101. Идеальный first-time experience (Q2 от user)** ⊕ S-102, S-103
- Актор: admin + Managed (синхронно или асинхронно?).
- Сценарий: внук получил/купил телефон → ставит app → проходит pairing на своём → передаёт телефон бабушке → у бабушки на главном экране уже всё настроено.
- **Текущее состояние**: спек 007 (pairing) + 010 (setup assistant) + 008 (sync) частично закрывают, но не end-to-end протестировано.
- Вопрос: бабушка должна вообще что-то делать на первой странице или нет? Если нет — что она видит между моментом «получила телефон» и «admin настроил»? **Пустая раскладка с «загрузка...» — это то, что user поднял как баг.**

**S-102. Setup в один заход (внук сидит рядом с бабушкой)** ⊕ S-101
- Оба устройства в одной комнате. Pairing через QR — короткая дистанция, надёжная сеть.
- Сценарий проще, чем S-101, но требования те же.

**S-103. Setup remote (внук в Москве, бабушка в Хабаровске)** ⊕ S-101, S-208
- Дистанционный pairing. Бабушка должна как-то получить QR / код / link.
- **Открытый вопрос**: спек 007 не покрывает «бабушка не может сама показать QR» — кто-то рядом должен помочь? Сосед? Это значит — нужен делегат / one-time-code, передаваемый по голосу?

**S-104. Setup без admin'а вообще (self-serve)** ⊕ архитектурный вопрос
- Бабушка-«молодая старушка» сама ставит, сама настраивает.
- **Текущая архитектура это запрещает.** Это и есть тот one-way door, о котором mentor предупреждал.
- Вопрос: это feature или anti-feature? Если anti — зафиксировать ADR.

**S-105. Setup тестировщиком / разработчиком (DEV)** ⊕ S-101, S-401
- Один человек, один эмулятор (или два эмулятора). Хочет проверить фичу end-to-end локально.
- **Текущая боль user'a по спеку 012**: невозможно проверить шифрование, потому что local-admin UI нет.
- Это **dev-experience сценарий** — пропуск его означает, что developer не может работать без подмостков.

### 3.2 Daily use

**S-201. Бабушка звонит внуку через тайл WhatsApp** ⊕ S-205
- Закрыто спеком 002. Happy path.

**S-202. Бабушка нажимает SOS / экстренную плитку** ⊕ S-301
- ❌ Спека нет. Featured во всех конкурентных elderly-launcher'ах (см. ресёрч ниже).
- Требования: моментальный набор, минимум подтверждений, индикация что звонок идёт, **fallback если нет связи**.

**S-203. Бабушка случайно открывает что-то лишнее**
- Свайп от края → шторка → нажала на что-то → растерялась.
- Партиально закрыто на уровне launcher core (001), но edit-lock + home-fallback ещё не специфицированы детально.

**S-204. Бабушка читает напоминание о таблетках**
- ❌ Спека нет. Medication reminders — featured в senior launchers.
- Вопрос: это in-scope или нет? Это и есть vision-вопрос.

**S-205. Возврат из мессенджера обратно в лаунчер** ⊕ S-201
- Закрыто спеком 002 в общем, но edge case'ы (стек активити, deep links) — открытые.

**S-206. Видит фото внука на плитке контакта** ⊕ S-501
- Будет закрыто спеком 012 (visible client крипто-фундамента).

**S-207. Видит «нет связи» / «сервис недоступен»**
- ❌ Deferred → спек 013 (offline-detection). Это **must-have** — без этого бабушка молча останется без помощи.

**S-208. Бабушка показывает QR на своём экране (для повторного pairing'a или add co-admin)** ⊕ S-103, S-302
- ❌ Не вижу в спеках 007/008/009 явного UI «show my QR» **на стороне Managed**. Сейчас всё инициирует admin со своей стороны.

### 3.3 Re-configuration

**S-301. Admin добавляет новый контакт удалённо** ⊕ S-205
- Закрыто спеком 009 + 008.

**S-302. Admin добавляет co-admin'а** ⊕ S-208
- Multi-admin сценарий. Спек 008 учитывает collision при одновременной правке, но *процедура добавления второго admin* — мне не очевидна по спекам.

**S-303. Меняется номер телефона у внука**
- ❌ Edge case не вижу. Это требует апдейта контакта и переотправки фото (см. спек 012 — фото зашифровано на свободного только для пары keys).

**S-304. Admin меняет фото себя**
- Цепочка: новое фото → re-encrypt per-recipient → push → managed загружает → плитка обновляется. Спек 012 будет это покрывать.

**S-305. Admin делает rollback конфига**
- Закрыто спеком 009 (история конфигов).

**S-306. Admin меняет язык / region** (l10n trigger)
- ❌ ADR-004 говорит про i18n readiness, но процедура смены языка в Managed после initial setup — open.

### 3.4 Recovery & failure

**S-401. Бабушка случайно сделала factory reset** ⊕ S-501
- Все локальные данные потеряны. Keys ушли.
- **Спек 011 OWD-4 social recovery** должен это закрывать, но это будущая часть.
- Critical question: **что admin видит на своей стороне** в этот момент?

**S-402. Internet outage у Managed на неделю**
- ❌ Часть deferred → 013. Но *behavior* лаунчера в офлайне нужен сейчас (минимум — cached данные показываются, не «загрузка...»).

**S-403. FCM не доставился (Doze, OEM-kill)** ⊕ полностью спек 007
- Закрыто polling fallback'ом (15 мин). Acceptance criteria есть.

**S-404. Admin потерял свой телефон**
- Кража / поломка. Что с pairing'ом? Кто унаследует доступ?
- ❌ Не вижу. **Это критично для multi-admin сценария S-302.**

**S-405. Cloudflare Worker недоступен** (out of free tier, rate limit, attack)
- Server-roadmap уже фиксирует это как exit ramp. Но UX в Managed-приложении в этот момент?

**S-406. Firebase Spark лимиты превышены** (rate, storage, bandwidth)
- Spark → Blaze exit ramp в server-roadmap. UX-side: что бабушка видит, что admin видит, не «бесконечный спиннер».

**S-407. Permission revoked после OS update / OEM-clean** (POST_NOTIFICATIONS, ROLE_HOME)
- Closed спеком 010 (SetupCheck soft-checks). Hopefully end-to-end.

**S-408. Малое место на устройстве / DB ошибка**
- ❌. Edge case OS-уровня.

### 3.5 Migration

**S-501. Бабушка получает новый телефон** ⊕ S-401
- Старый сломался / купили новый. Перенос: pairing восстановить, контакты восстановить, фото восстановить.
- **Спек 011 OWD-4 social recovery** + key migration этим должны заниматься. Это сложный сценарий, конец 2026 — early 2027.

**S-502. Admin меняет телефон**
- Тот же admin Firebase-account, новое устройство. Спек 008 уже учитывает второе устройство admin'а (планшет), значит и это close.

**S-503. App update (OTA / Play Store)** — wire-format breaking change?
- CLAUDE.md rule 5 + спеки 005/006/007/011 все имеют `schemaVersion`. Закрыто в принципе, но **roundtrip-тесты на старых fixture'ах** есть не во всех спеках.

### 3.6 End-of-life

**S-601. Бабушка перестаёт пользоваться (госпитализация, переезд)**
- Что с данными? Что с pairing'ом? Что с фото внука у неё на устройстве?
- ❌. **Privacy / GDPR-уровня вопрос.** В спеках не вижу.

**S-602. Бабушка умерла, родственники нашли телефон**
- Доступ к данным. Это **legal**, не technical. Но требует upfront-решения, как ключи унаследовать (или не унаследовать).
- ❌. **Это уровня OWD-4 social recovery в спеке 011.**

**S-603. Admin перестаёт быть admin'ом** (поссорились, развод, перестал помогать)
- Бабушка должна как-то это пережить. Co-admin спасёт. Но если был один — как «отвязать» без участия admin'а?
- ❌. Связан с S-104 (self-serve mode).

### 3.7 Cross-device / dual-actor specific

**S-701. Admin и co-admin редактируют одновременно** ⊕ S-302
- Спек 008 закрывает (optimistic concurrency + merge UI).

**S-702. Admin отправляет фото с планшета, Managed получает на телефон**
- Один Firebase-account, два устройства. Спек 008 учитывает.

**S-703. Push прилетел Managed-устройству, оно офлайн → онлайн** ⊕ S-403
- Спек 007 polling fallback закрывает.

### 3.8 Dev / тест / verification

**S-801. Разработчик хочет протестировать шифрование end-to-end локально** ⊕ S-105
- **Текущая боль user'а.** Невозможно без local-admin UI или без второго эмулятора, на котором запущена admin-сторона.
- **Решение архитектурное**: либо local admin UI (debug-only), либо «admin-mode emulator» как стандартная dev-настройка, либо headless admin CLI.
- Это **fitness-function-level требование** — без него каждая будущая спека страдает от той же боли.

**S-802. QA проходит smoke-checklist на двух эмуляторах**
- Skill `android-emulator` готов. Это процедурно решено.

**S-803. Reviewer открывает спеку без знания продукта**
- ❌. Сейчас спеки опираются на context из других спек. Без vision-документа нового человека ввести очень трудно.

---

## 4. Сценарии, которых **сейчас точно нет** в спеках 001-012 (gap analysis)

| ID | Сценарий | Критичность | Куда логически попадает |
|----|----------|-------------|--------------------------|
| S-104 | Self-serve setup без admin | High (vision question) | ADR, не спека |
| S-105 / S-801 | Local dev/test of features without two devices | High (DX, блокирует velocity) | Cross-cutting fitness function |
| S-202 | SOS-плитка | High (must-have для целевой аудитории по market research) | Новая спека |
| S-208 | Managed показывает свой QR | Medium (нужен для multi-admin, social recovery) | В спек 007/009 как расширение |
| S-302 | Процедура добавления co-admin'а | Medium | В спек 009 или новая |
| S-303 | Контакт сменил номер | Medium | В спек 009 |
| S-306 | Смена локали в Managed | Low | В будущую l10n-спеку |
| S-404 | Admin потерял свой телефон | High (multi-admin/inheritance) | Новая спека или extend 011 OWD-4 |
| S-405 / S-406 | UX при недоступности backend | Medium | Cross-cutting, наверное в 010 или 013 |
| S-408 | Out-of-storage / DB error | Low | Cross-cutting |
| S-601 / S-602 / S-603 | End-of-life cases (privacy, inheritance, divorce) | High (legal/ethical, упустить нельзя) | Vision + новая спека |
| S-803 | New reviewer onboarding | Process-level | Vision-документ + introductory README |

Конкурентный анализ (см. ресёрч ниже) подсказывает ещё **must-have** для целевой аудитории:
- **Medication reminders** (S-204 в карточках). Все senior-launcher'ы это имеют.
- **Edit-lock / accidental tap protection** (S-203). У нас частично есть.
- **Tier-by-tier accessibility settings** — touch delay, scroll sensitivity. ❌ нет.
- **Health info card** на главном экране (для скорой / врача). ❌ нет.

---

## 5. Что делают популярные лаунчеры (research)

> **Ключевой принцип** (user, 2026-05-27): удалённое управление сейчас за скобки. Local-edit UI у пользователя на устройстве и remote-admin UI у админа на его устройстве — это **одна и та же поверхность редактирования** для разных акторов. Поэтому изучаем on-device patterns популярных лаунчеров — они применимы к обеим сторонам без расщепления компонентов.

### 5.1 Сводка по сравниваемым лаунчерам

| Лаунчер | Модель home screen | Edit mode | Целевая аудитория | Релевантность для нас |
|---------|---------------------|-----------|-------------------|----------------------|
| **Niagara** (#1 minimalist 2026, после смерти Nova) | Вертикальный список favorites + alphabetical jump справа | Long-press → drag в список, swipe-edit | One-handed users | Средняя — у нас curated tiles, не drawer |
| **Smart Launcher 6** | Categories grid, smart sorting | Customizable grid + app drawer | Power users | Низкая — мы не power-user продукт |
| **Microsoft Launcher** | Traditional grid + feed pane | Drag-drop, widget picker | Productivity crowd | Низкая |
| **iOS Home Screen (jiggle mode)** | Fixed grid | Long-press → wiggle → drag → Done | Mass market | **Высокая** — gold standard для edit UX |
| **Android 15 stock** | Grid | Long-press → "Add widget" picker → confirm placement | Mass market | **Высокая** — current best practice |
| **BIG Launcher** | Selectable grid 3×4 / 3×3 / 2×4 / 2×2 / 1×1 | Preferences → "Set buttons" → assign per-cell | Seniors / vision impaired | **Очень высокая** — прямой аналог |
| **Square Home** | Metro tiles 1×1 / 2×2 / 4×2 / 4×4, live data на тайле | Long-press → resize handles | Foldable, tablet | Высокая — поддержка фото/live data |
| **Grid (WP-style)** | Бесконечная вертикальная сетка | Long-press | Minimal aesthetic | Низкая |

### 5.2 Конкретные UX паттерны (bench-bank)

**Вход в edit mode (universal):**
- *Long-press empty area* → re-arrange mode. Везде, от iOS до BIG Launcher.
- Visual cue: wiggle (iOS), outline+dots (Android), коричневый/синий фон (BIG Launcher).
- Выход: tap «Done» / системная кнопка Back / tap вне.

**Добавление тайла (модерн):**
- Android 15: *picker → confirm placement*, без drag-drop. Меньше промахов.
- BIG Launcher: явное «Set buttons» → tap пустой ячейки → меню выбора (App / Contact / Call / SMS / Website / SOS / Toggle / другой экран).
- iOS: jiggle → «+» в углу → picker.

**Empty placeholder в нормальном режиме:**
- BIG Launcher: пустая ячейка по умолчанию неактивна, но есть toggle «Enable buttons with no functionality» → long-press показывает описание (a11y).
- iOS: empty cells просто пустое место.
- Android stock: empty cells пустое место, длинный тап → меню «Add».
- **Рекомендация для нас**: пустая ячейка в нормальном режиме = пустая (не «загрузка...»). В режиме редактирования = «+».

**Missing app (приложение удалили / не установлено):**
- Nova / Niagara: иконка серая + small badge, tap → Play Store / install hint.
- BIG Launcher: button показывает «App not installed», tap → инструкция.
- **Рекомендация для нас**: тот же паттерн — grayed tile + CTA «Установить» → Play Store deep link.

**First-run wizard (BIG Launcher canonical для seniors):**
1. Language
2. Text size (Default / Bigger / Biggest)
3. Theme (Light / Dark / Blue)
4. Permission delegation (опции про удаление приложений и т.п.)
5. → Home screen

**Hidden admin gate:**
- BIG Launcher: password в Preferences → «Protect menu» → «Password required to open settings». «If you forget your password, there is no recovery.» — честный disclaimer.
- Наш 7-tap из спека 010 — той же категории, но **без password по умолчанию** (rotating challenge). Совместимо.

**SOS feature (canonical у senior launchers):**
- Красная отдельная кнопка/тайл.
- Tap → confirmation delay (configurable, *настройка против случайного срабатывания*).
- Действие: одновременно SMS (с GPS координатами, если есть) + последовательный обзвон по списку контактов.
- BIG Launcher: «SMS wait» и «Call wait» отдельные.

**Photo Contacts** (visual recognition, universal для seniors):
- Фото большого размера прямо на плитке контакта. Имя — мелким шрифтом снизу или скрыто.
- Tap → действие по умолчанию (звонок, WhatsApp). Long-press → выбор действия.
- BIG Launcher и Square Home поддерживают это нативно.

**Grid configurability:**
- BIG Launcher позволяет менять plot 3×4 ↔ 2×2 на лету. Senior'у можно подобрать размер на стадии setup.
- iOS: размер фиксированный, но widgets разных размеров.
- **Рекомендация для нас**: иметь preset размеров (например, 2×3 «small», 3×4 «standard», 4×5 «dense»), выбираемых в wizard'е. Не давать произвольный grid editor — это перегрузит admin'а.

**Edit-lock (anti-accidental):**
- BIG Launcher: глобальный toggle «Lock arrangement».
- iOS 16+: «Edit Home Screen» теперь под отдельным action.
- **Рекомендация для нас**: edit-mode под админом за 7-tap gate — это и есть наш edit-lock. Дополнительный toggle не нужен.

**Empty state (CTA pattern, universal):**
- **Никогда не показывай «Загрузка...» там, где данных просто нет.**
- Структура: icon/illustration → заголовок («Тут пока пусто») → описание («Добавь первую плитку…») → CTA («+ Добавить») или объяснение, кто должен это сделать («Попроси внука настроить»).
- Универсальный совет [Smashing Magazine, NN/g]: empty state — это onboarding-возможность, не ошибка.

### 5.3 Покрытие наших сценариев готовыми паттернами

| Сценарий | Закрывается каким паттерном | Адаптация для нас |
|----------|------------------------------|---------------------|
| S-101 first-time | BIG Launcher wizard (language → size → theme → grid) | Перенять + добавить шаг pairing'а |
| S-104 self-serve | Стандартный onboarding wizard, как у всех | Если откроем — copy BIG Launcher |
| S-105 / S-801 dev test | **Local edit UI = Remote admin UI** — один компонент | Это закрывает боль user'а по 012 |
| S-202 SOS | BIG Launcher canonical: red button + delay + SMS+GPS+sequential calls | Перенять полностью |
| S-203 accidental tap | Edit-lock global toggle (BIG Launcher), плюс large hit targets | У нас уже large; edit-lock = за admin gate |
| S-204 medication | **НЕ launcher's job** — стороннее приложение делает уведомления, наш launcher просто показывает его тайл | Не делать самим |
| S-206 photo on tile | Photo Contacts (BIG Launcher, Square Home) | Спек 012 это и делает — pattern matches |
| S-207 no connectivity | Niagara/Microsoft: banner на главном экране + retry CTA | Перенять + offline-cache (deferred → 013) |
| S-208 managed shows QR | Apple TV / Wear OS pairing reverse direction | Перенять — добавить в Settings → «Связать ещё одно устройство» |
| S-301 admin adds contact | Same as local edit add tile — **один компонент** | Local edit UI = remote |
| S-302 add co-admin | Standard «invite collaborator» (Google Docs share, iCloud Family) | Перенять — invite link с TTL |
| S-303 changed phone number | **НЕ launcher's job** — система контактов Android | Полагаемся на Android Contacts |
| S-306 language switch | iOS / Android system: смена системного языка | Переюзать system locale, не свой переключатель |
| S-401 factory reset | Email apps: «restore from cloud» при first launch | Спек 011 OWD-4 social recovery + восстановление по pairing'у |
| S-403 FCM not delivered | WorkManager polling fallback (закрыто 007) | Already covered |
| S-404 admin lost device | Standard «forget device» + transfer pairing | Нужен новый flow в спеке 009 или новой |
| S-407 permission revoked | Niagara/BIG: persistent banner + CTA | Спек 010 SetupCheck это покрывает |
| S-503 app update / wire-format | Любое приложение со sync: schemaVersion + migration | CLAUDE.md rule 5 уже это требует |

**Сценарии БЕЗ готового паттерна** (требуют собственного дизайна):
- S-401 social recovery (после reset, без admin'а рядом) — нестандартный кейс.
- S-601..S-603 end-of-life (наследование ключей, отвязка после смерти) — нет аналогов в consumer-launcher'ах. Аналоги есть в LastPass / 1Password (digital legacy), но это другой UX.
- S-103 setup remote (внук в Москве, бабушка в Хабаровске) — Wear OS / Apple TV pairing рассчитаны на одну комнату. Reverse-QR через voice — придётся проектировать самим.

### 5.4 Action-level recommendations

1. **Объединить local edit и remote admin в один UI компонент.** Это закрывает D-2 (vertical slice), снимает боль S-105/S-801, и **по факту это и есть индустриальный паттерн** — нигде нет «two separate edit UIs for the same data».
2. **First-run wizard — копия BIG Launcher** (language → text size → theme → grid size → pair admin или skip → home). Не изобретать.
3. **Edit mode** — копия iOS jiggle + Android 15 picker: long-press → outlined cells → tap «+» → picker → place. Drag оставить как опцию для power case, но default — picker-based.
4. **Empty cell в normal mode** — пустая, не «Загрузка...». В edit mode — «+». Это **cross-spec rule**, надо прописать в design-system.md.
5. **Missing app placeholder** — grayed icon + «Установить» CTA → Play Store. Universal pattern, легко имплементировать.
6. **SOS** — must-have, copy BIG Launcher canonical mechanics (red button, configurable delay, SMS+GPS+calls). Новая отдельная спека.
7. **Photo Contacts** — то, что и так делает спек 012. Pattern matches → строим уверенно.
8. **Hidden admin gate** — 7-tap уже выбрали в 010. Совместимо с BIG Launcher pattern «protected settings».
9. **НЕ строить свой app drawer / categories.** Мы curated launcher. Niagara/Smart Launcher patterns не наш кейс.
10. **НЕ строить свой переключатель языка.** Использовать system locale через Android settings — стандартный UX, не нагружает Managed.

### 5.5 Что меняется в нашем roadmap'е от этого ресёрча

Прямые следствия для будущих спек:

- **Спека по local edit UI** (новая) — нужна перед или одновременно с любой visible feature, требующей конфигурации. Сейчас её НЕТ. Это **самая большая дыра** в roadmap'е.
- **Спека по SOS** (новая) — must-have для целевой аудитории, отсутствует.
- **Cross-spec design-system правило про empty/loading/error/missing-app** — добавить в `docs/dev/design-system.md`.
- **Спека по first-run wizard** — расширить спек 010 (Setup Assistant), он уже близко, но не покрывает grid-size / text-size / theme выбор по BIG Launcher паттерну.
- **Спека 011 OWD-4 social recovery** — единственный кусок, который не имеет industry-параллели в launcher'ах. Уточнить с mentor отдельно, нужен ли сейчас.

---

## 6. Доступность для пожилых: что собрать в наш лаунчер (research)

> User (2026-05-27): «BIG Launcher не самый удачный вариант». Согласен — это и не должен быть единственный референс. Ниже — раскладка по доменам доступности (зрение, моторика, когнитив, слух, голос) с конкретными решениями из разных продуктов и из academic-исследований. Идея — **собрать своё** из лучших ингредиентов, не копировать один продукт.

### 6.1 Что не так с BIG Launcher (если коротко)

Поверх его сильных сторон (canonical SOS, grid-конфигурируемость, password gate, photo contacts) — есть реальные слабости, которые мы НЕ должны повторять:

- **Визуальный язык устарел** — heavy, retro-skin, не Material You / iOS-native. Стигма «телефон для стариков», от которой пользователи бегут (см. отзывы Niagara/Square Home — даже пожилые предпочитают «нормально выглядящий» launcher).
- **Замена системных приложений** (BIG Phone, BIG SMS — отдельные платные apps вместо системного Phone/Messages). Это bloat + ломается на обновлениях ОС.
- **Password gate без recovery** — «If you forget your password, there is no recovery.» — это честный, но плохой UX. Мы уже выбрали 7-tap + rotating challenge (010) — лучше.
- **Settings — длинные многоуровневые меню**, написанные в стиле «для admin'а, не для бабушки». Admin тоже устаёт.
- **Не использует системные accessibility сервисы Android** — TalkBack, Voice Access, Magnification, Live Caption — игнорируются, всё рисуется самостоятельно крупно. Это упущенная возможность.
- **Senior-стигма везде** (название, иконки, цвета) — отталкивает «молодых пожилых» (60-70 лет, ещё активных).

**Вывод**: брать у BIG только канонические механики (SOS, photo contacts, grid sizing), всё остальное — собирать из других источников.

### 6.2 Конкуренты, кроме BIG (сильные стороны каждого)

| Лаунчер / решение | Что делает хорошо | Что взять |
|---|---|---|
| **Wiser** | 6 крупных тайлов на главном — Phone / Contacts / Gallery / Camera / Messages / Other. Жёсткое ограничение по числу — снижает cognitive load. Free, ad-free. SOS встроен. | **Принцип «6 ± 2 тайла max на экране»** — против перегрузки. Дефолтный SOS. |
| **Necta** | Extra-large fonts, Position + SOS на главной, минимум функций. | Position-sharing (GPS по запросу). |
| **GrandPad** (планшетная подписка) | Family-managed (curated), голосовая команда, упрощённая Видеосвязь, всё через семью. | **Family-curated** — родственники добавляют контакты/приложения. У нас уже есть как admin model. |
| **Doro** (вендорские телефоны) | Hardware SOS button сбоку устройства. Очень аккуратные физические кнопки. Кардинально упрощённое меню. | Mapping hardware-button (где доступно) на SOS. Идея «3-уровневое меню max». |
| **Square Home** | Live data на тайле (текущая погода / непрочитанные / счётчик). Smart sort by usage. | Live data — пропущенные звонки на тайле контакта, badge «новое фото». |
| **Niagara** | High-contrast minimalist, one-handed reach (нижняя половина экрана для управления). | **One-handed reach zone** — кнопки управления (back, home, edit) в нижней четверти. |
| **System Android (Material 3 Expressive)** | Dynamic Color, system-respecting text-size, TalkBack, Voice Access, Live Caption, Magnification. | **Уважать system accessibility settings**, не дублировать своими настройками. |
| **iOS AssistiveTouch + Dwell Control** | Триггер по hover (без tap) — для людей с сильным тремором. Триггер по triple-click side button. | **Dwell-to-activate** опция для сильного тремора. |

### 6.3 По доменам доступности — что в индустрии работает

#### Зрение (vision)

| Проблема | Industry solutions | Что берём в наш launcher |
|---|---|---|
| Снижение остроты, мелкий текст | Bold text, dynamic type, magnifier triple-tap, screen reader (TalkBack / VoiceOver) | **Уважать `fontScale` системы 1.0..1.5×**, плюс наш override до 1.5× если system < 1.2×. **Не делать свой переключатель** — это уже есть в Android Settings → Display |
| Снижение контраста, желтение хрусталика → проблемы с синим | High-contrast mode, dark mode, color inversion. Research: контрасты должны быть warm-side, а не cool-side | **2 темы**: high-contrast light + high-contrast dark. **Без сложной цветовой палитры** в управляющих элементах. Action color — оранжевый/желтый/зеленый (тёплый), не синий |
| Узкое поле зрения / макулярная дегенерация | Магнификация, увеличенный курсор, edge-aware UI (важное в центре, а не по краям) | **Размещать критические action'ы в центре экрана**, не по углам. Кнопка SOS — большая по центру, не в углу |
| Полная слепота / очень низкое зрение | TalkBack, VoiceOver, screen reader | **Полная TalkBack-совместимость** каждого тайла, описательные `contentDescription`. Это уже требование `checklist-accessibility` |

#### Моторика (motor)

| Проблема | Industry solutions | Что берём в наш launcher |
|---|---|---|
| Тремор рук (Parkinson, эссенциальный тремор) | Touch Guard (enhanced area touch + targets list — 65% fewer errors). Motion sensor (40% fewer misses). Large targets ≥12mm. Spacing between targets. | **Touch debounce** — игнорировать повторные тапы в течение 300-500ms на ту же область. **Target halo** — невидимая увеличенная hit-зона за пределами визуальной иконки. **Минимум 16dp spacing** между тайлами |
| Снижение точности тапа | Adaptive grid, padding между элементами, no fine-pointing UI | **Tap-anywhere-on-tile** — вся плитка кликабельна, не маленькая иконка внутри. **Edge-of-tile** не активен (5dp buffer) — против промахов между плитками |
| Тяжело держать длинный long-press | iOS reduce-motion / dwell control. Android увеличиваемый timeout | **Long-press timeout — configurable** (default 500ms, options 250 / 500 / 1000 / 1500ms). Опция «replace long-press with double-tap» |
| Невозможен tap вообще (полный тремор, тетраплегия) | Voice Access, eye tracking, switch control | **Voice Access integration** — вызов команд голосом, не наш voice control. Switch Access — Android system feature, мы только должны быть compatible |
| Случайные тапы (зажатая ладонь, лежащий телефон) | Edit lock (BIG), proximity-sensor (Doro hardware) | **Любая destructive операция — за edit mode** (7-tap gate). На главной — только safe действия |

#### Когнитив (cognitive)

| Проблема | Industry solutions | Что берём в наш launcher |
|---|---|---|
| Сложность нелинейной навигации | Wiser: 6 элементов на главной, нет hierarchy. Doro: 3-уровневое меню max. | **Глубина меню — 2 max**. Главный экран → действие. Никаких tab'ов внутри tab'ов |
| Перегрузка выбора | Hick's law: меньше опций = быстрее решение. Wiser ограничивает 6. | **6 ± 2 тайла на экран** (target). Configurable, но default = senior-safe |
| Снижение working memory | Familiar patterns, persistent UI (не исчезающие меню), consistent placement | **Тайл всегда на одном месте** между сессиями. Никакой smart sort by usage (Niagara/Square Home) — это сбивает |
| «Где я нахожусь?» | Breadcrumbs, всегда видимый home, persistent back | **Single back button**, всегда видим. **Home всегда возвращает на главный экран** — не на под-экран |
| Страх «сломать» что-то | Undo, reversible actions, soft confirmations не на каждый шаг | **Snackbar с Undo** для всех reversible actions (5 сек). **Confirmations — только для irreversible** (удаление, исходящий звонок при ошибке) |
| Ухудшение распознавания иконок | Photo-based recognition (BIG, Square Home, Niagara contacts) | **Photo контакта > имени**. Иконки приложений с большим текстовым лейблом снизу |

#### Слух (hearing)

| Проблема | Industry solutions | Что берём |
|---|---|---|
| Снижение слуха, плохое восприятие высоких частот | Visible+haptic вместо audio-only. Live Caption (Android). Visual ringtone (вспышка) | **Любое уведомление = visible + haptic + (optional) audio**. Не звуковой-only. Полагаемся на system Live Caption для входящих |
| Misunderstanding voice через speaker | Speakerphone toggle на видном месте, в-ear cue | **Speaker toggle крупно** в звонке (это уже в системе, мы только не мешаем) |

#### Голосовой ввод (voice)

| Проблема | Industry solutions | Что берём |
|---|---|---|
| «Не могу попасть в тайл» (тремор + плохое зрение) | Google Voice Access («Open WhatsApp», «Call Vanya») | **Не строить свой voice launcher**. Делать TalkBack / Voice Access совместимым (правильные `contentDescription`) — этого достаточно |
| «Хочу позвонить голосом» | «Hey Google, позвони …» | Полагаемся на системный Google Assistant. Не дублируем |
| Voice activation для SOS | Hardware button + voice trigger | Опционально: «Hey Google, помощь» → trigger our SOS action (через Assistant Routine) |

#### Подтверждение и обратная связь (confidence)

| Проблема | Industry solutions | Что берём |
|---|---|---|
| «Я нажал? Точно сработало?» | Haptic feedback (vibrotactile «нажатие»). Visual ripple. Audio click | **Haptic + visual ripple на каждый успешный tap**. Mandatory, не optional |
| «Я не вижу что грузится» | Skeleton loader, progress indicator, не пустой экран | **Skeleton placeholder, не «Загрузка...»** для known-coming content. **Empty state с CTA** для нет-данных. Это cross-spec правило |
| «Я случайно нажал — как отменить?» | Undo snackbar, time-limited undo | **5-секундный Undo** для всех reversible. Toast «Звонок начнётся через 3 секунды… [Отмена]» для исходящих |

### 6.4 Целостная «assembly» — что у нас в итоге

Собирая из таблиц выше — наш доступностный профиль («senior-safe launcher core»):

**Визуальные правила:**
- Базовый шрифт = `fontScale × max(1.0, system fontScale × 1.2)`, capped at 1.5×.
- 2 темы: high-contrast light / high-contrast dark. Action color — тёплый (orange/green).
- Action в центре экрана, decoration по краям.
- Photo-first для контактов; текст-label под тайлом, не вместо.
- Target ≥ 56dp (наш senior-safe override; memory)`feedback_emulator_window_placement` уже фиксирует > 48dp базовый.
- Spacing ≥ 16dp между интерактивными элементами.

**Motor правила:**
- Touch debounce 300ms (повторные тапы на ту же зону игнорируются).
- Target halo +8dp за визуальным краем.
- Edge-of-tile 5dp buffer-zone между плитками.
- Long-press timeout configurable (default 500ms).
- Опция dwell-to-activate (hover ≥ 1 sec, для сильного тремора) — впереди.

**Cognitive правила:**
- Max 2 уровня меню (главный экран → действие, никакой 3-й уровень).
- Default 6 ± 2 тайла на экран, до 12 опционально.
- Стабильное положение тайлов между сессиями — нет smart-sort.
- Single back, всегда видим. Home всегда = главный экран.
- Snackbar Undo (5 sec) для всех reversible. Confirmations только для irreversible.

**Слух / голос:**
- Notification = visible + haptic + optional audio (никогда не audio-only).
- TalkBack / Voice Access совместимость mandatory.
- Не строим свой voice launcher.

**Confidence:**
- Haptic+ripple на каждый успешный tap.
- Skeleton для loading, empty-state-with-CTA для нет-данных. **Никаких «Загрузка...» где данных просто нет.**

**Anti-patterns (явно НЕ берём):**
- Замена системных Phone/SMS своими — никогда.
- Senior-стигма в названии / иконках — нет (помечаем product `Universal Launcher`, не `Senior Launcher`).
- Password gate без recovery — нет (наш 7-tap + rotating challenge лучше).
- Cold blue accent colors — нет.
- Smart-sort by usage на главной — нет.

### 6.5 Что это значит для нашего roadmap'а

Прямые изменения, помимо уже зафиксированных в §5.5:

- **Новая спека / расширение existing**: «Accessibility Core» — собрать все правила выше в один артефакт (`design-system.md` + код в `core/`). Это **cross-cutting**, должна предшествовать UI-спекам, не следовать.
- **`checklist-elderly-friendly` обновить** — сейчас он есть, но не покрывает все 6 доменов структурированно. Добавить domain matrix как в §6.3.
- **Dwell-to-activate** — впереди, не в MVP. Inline TODO в код.
- **Touch debounce + target halo** — должны быть в каждой плитке. Это `core` уровень, не per-feature. **Должна быть отдельная спека на input pipeline.**
- **Live data на тайле** (badge непрочитанных, фото меняется при новом фото от внука) — Square Home pattern, мы пока этого не делали. Будущая спека или extension 012.
- **Voice Access compatibility audit** — отдельная задача в `project-backlog.md`. Просто проверить, что все `contentDescription` написаны как команды («Call grandson», не «Photo of grandson»).

### 6.6 Открытые вопросы для дискуссии

- **D-6.** «Universal Launcher» vs «Senior Launcher» — нэйминг продукта. Senior-стигма vs ясность позиционирования. Это маркетинг/branding decision, влияет на иконку, скриншоты, copy в Play Store.
- **D-7.** Принимаем ли **default 6-tile limit** (Wiser pattern)? Или admin может выставить до 20 тайлов без warning'а? Cognitive cost у пожилого — реальный.
- **D-8.** **Dwell-to-activate** — это нишевая фича (сильный тремор) или часть default'а? Если default — нужен smart-detection «когда включать», иначе мешает обычным пользователям.
- **D-9.** Hardware SOS button (Doro pattern) — выходит за наши рамки (мы software-launcher, не вендор устройств). Но **PowerButton triple-press → SOS** через Accessibility Service технически возможно. Делаем или нет?
- **D-10.** **Family Curated vs Self-Service** — продолжение D-1. GrandPad доказал, что family-managed model работает в США. Но мы можем сделать **opt-in self-serve** для «молодых пожилых».

---

## 7. Кейсы за пределами launcher UI/UX (другие домены)

> User (2026-05-27): «это не всё что нужно закрыть». Launcher UI — это **одна вертикаль** продукта. Ниже — остальные домены, по которым тоже нужен inventory кейсов для целостного roadmap'а. Базируется на `docs/product/context-decisions-and-open-questions.md`, `docs/dev/project-backlog.md`, `docs/dev/server-roadmap.md`, спеках 001-012 и нашей mentor-сессии.
>
> Маркеры: ✅ закрыто спекой · 🟡 частично закрыто · ❌ gap · 🔮 future (запланировано, ещё далеко) · ❓ open question.

### 7.1 Remote management (admin workflows)

Админские сценарии — то, что user попросил «оставить за скобками» в §5/§6, но они должны быть в roadmap'е.

| ID | Кейс | Статус |
|---|---|---|
| R-001 | Admin видит список paired Managed-устройств | 🟡 (ARCH-008 — empty-state stub сейчас) |
| R-002 | Admin видит health одного Managed (battery, last-seen, OS, perms) | 🟡 (spec 009) |
| R-003 | Admin открывает editor удалённо | ✅ (009) |
| R-004 | Admin push'ит новый layout, видит «доставлено / в очереди / failed» | 🟡 (007 FCM + 15min fallback) |
| R-005 | Admin видит, что Managed offline >N часов → alert | ❌ (ARCH-012 phone health critical → push admin) |
| R-006 | Admin делает rollback по истории | 🟡 (009 FR-37..46, ARCH-008 IN PROGRESS) |
| R-007 | Collision с co-admin (merge UI) | ✅ (008) |
| R-008 | Передача ownership на другого admin'а | ❌ (см. S-404) |
| R-009 | Audit log: кто/что/когда менял | ❌ (SEC-003) |
| R-010 | Один admin — N Managed (дашборд) | 🟡 |
| R-011 | N admin'ов — один Managed (multi-admin model) | ✅ (008) |
| R-012 | Admin видит, что Managed *не пользовался* плиткой неделю (usage hint) | ❌ telemetry-уровня |
| R-013 | Version mismatch (Managed на старой версии) | ❌ (ARCH-007) |
| R-014 | Admin отвязывает Managed (unlink) | 🟡 (007 FR-027 hard delete) |

### 7.2 Pairing & trust primitives

Spec 007 + project memory `qr_pairing_trust_primitive` — pairing должен переиспользоваться для всего.

| ID | Кейс | Статус |
|---|---|---|
| P-001 | Первичный pairing через QR (happy path) | ✅ (007) |
| P-002 | Re-pairing после factory reset на Managed | 🟡 (часть OWD-4 social recovery в 011) |
| P-003 | Reused token / TTL expired | ✅ (007) |
| P-004 | Добавление co-admin'a — invite link | ❌ (FUTURE-SPEC-008 group; нужно сейчас?) |
| P-005 | Revoke admin (отозвать доступ) | 🟡 (через unlink) |
| P-006 | Reverse direction — Managed показывает QR для добавления второго admin'а | ❌ (S-208) |
| P-007 | Pairing на расстоянии (внук в Москве, бабушка в Хабаровске) | ❌ (S-103) |
| P-008 | Trust edge revocation (например, удалили co-admin'a) | ❌ (FUTURE-SPEC-010 key rotation) |
| P-009 | Audit «кто сейчас paired со мной» | ❌ |
| P-010 | Reuse pairing primitive для будущих integrations (Jitsi room, vendor, hardware) | 🔮 (по 011 + future-spec 008/009) |

### 7.3 Identity & auth

Spec backlog ARCH-004, AUTH-001 — anonymous → named.

| ID | Кейс | Статус |
|---|---|---|
| A-001 | Anonymous auth на first launch (admin + Managed) | ✅ (007) |
| A-002 | Миграция на named auth (Google Sign-In / Phone / Email) | 🔮 (ARCH-004, AUTH-001) |
| A-003 | Login с нового устройства (same Firebase account) | 🟡 (008 multi-device admin) |
| A-004 | Аккаунт suspended / banned Firebase'ом | ❌ |
| A-005 | Admin забыл Google-пароль | ❌ (полагаемся на Google recovery) |
| A-006 | Admin продал/потерял **все** свои устройства, account orphaned | ❌ (см. S-404 + S-602) |
| A-007 | Identity model для будущих vendor / partner / employee admins | 🔮 |
| A-008 | Per-app identity (один user, разные роли в разных контекстах) | 🔮 (OWD-5 в 011) |

### 7.4 Communications (messenger calls / video) — **strategic must-have**

`feature-priorities.md` фиксирует это как **must-have сейчас**. Сейчас закрыт минимум (WhatsApp tile + return).

| ID | Кейс | Статус |
|---|---|---|
| C-001 | Audio-звонок через WhatsApp / Telegram / Viber tile | 🟡 (002 WhatsApp only, остальные через action arch 005) |
| C-002 | Video-звонок через те же | ❌ research-stage (feature-priorities) |
| C-003 | App not installed → install prompt с deep-link в Play Store | 🟡 (006 capabilities) |
| C-004 | Contact не зарегистрирован в данном мессенджере → fallback | ❌ |
| C-005 | Multi-messenger preference (WhatsApp → Viber → call) | ❌ |
| C-006 | Custom call confirmation (anti-mistap) | ✅ (010) |
| C-007 | Return из мессенджера обратно | ✅ (002) |
| C-008 | iOS handoff | ❌ Documented Platform Asymmetry (ADR-005) |
| C-009 | Voice message playback / send | ❌ |
| C-010 | Conference / group call | ❌ |
| C-011 | SMS fallback при нулевом интернете | ❌ (13 offline-emergency) |
| C-012 | Call quality issues — что показать бабушке | ❌ |
| C-013 | Closed messengers (LINE, WeChat, KakaoTalk) | 🔮 (FUTURE-SPEC-003) |
| C-014 | Jitsi room access (vendor / family conference) | 🔮 (по 011) |

### 7.5 Data & privacy

CLAUDE.md rule 5 + privacy compliance + GDPR/152-ФЗ.

| ID | Кейс | Статус |
|---|---|---|
| D-001 | Что хранится на устройстве (encrypted blobs) | ✅ (011) |
| D-002 | Что хранится в облаке (config, photo blobs, telemetry?) | 🟡 (008 config, 011 blobs) |
| D-003 | GDPR data export ("download my data") | ❌ (PLAY-STORE-BLOCKER) |
| D-004 | GDPR / 152-ФЗ delete (right to be forgotten) | ❌ |
| D-005 | Multi-admin privacy (admin1 видит что внёс admin2?) | ❓ |
| D-006 | Vendor / third-party (клиники) privacy boundary | 🔮 (011 OWD-5) |
| D-007 | Photo encryption per recipient | 🟡 (012 stub) |
| D-008 | Backup / restore после reset | ❌ (S-401 + 011 OWD-4 social recovery) |
| D-009 | Cross-border data residency (продукт global) | ❌ |
| D-010 | End-of-life data (S-601..S-603 — наследование, отвязка) | ❌ |
| D-011 | Photos retention (без reference — cleanup) | 🟡 (011 reference counting) |
| D-012 | Config history retention (TTL) | 🟡 (client-side housekeeping per CLAUDE.md rule 8) |
| D-013 | Contacts privacy compliance | ❌ (LEGAL-001 PLAY-STORE-BLOCKER) |

### 7.6 Backend operations & reliability

Server-roadmap.md exit ramps + free-tier limits.

| ID | Кейс | Статус |
|---|---|---|
| O-001 | Cloudflare Worker down (FCM relay) → graceful degradation | 🟡 (007 polling fallback) |
| O-002 | Spark free-tier limit hit (Firestore reads/writes, bandwidth) | 🔮 (ARCH-003 Blaze upgrade exit ramp) |
| O-003 | Firestore offline → UI behavior | 🟡 |
| O-004 | FCM down (Google outage) | 🟡 (polling) |
| O-005 | Rate-limit per uid (не только per linkId) | ❌ (SEC-002) |
| O-006 | Audit logging для critical ops | ❌ (SEC-003) |
| O-007 | Firebase App Check (anti-abuse) | 🟡 (SEC-001) |
| O-008 | Production vs dev environment | 🟡 (OPS-004, OPS-005) |
| O-009 | Backend substitution readiness (rule 1) | 🟡 (по `RemoteSyncBackend` port) |
| O-010 | Disaster recovery (Firestore data lost) | ❌ |
| O-011 | Custom domain для Worker (`*.workers.dev` → собственный) | 🔮 (ARCH-001) |
| O-012 | Worker rate-limit на KV (persistent, не in-memory) | 🔮 (ARCH-002) |
| O-013 | Cloud Functions (Blaze) для server-side cron | 🔮 (ARCH-003) |

### 7.7 Platform / OS integration (Android)

Per `checklist-permissions-platform` + constitution.

| ID | Кейс | Статус |
|---|---|---|
| PL-001 | ROLE_HOME grant flow | ✅ (010) |
| PL-002 | POST_NOTIFICATIONS Android 13+ | ✅ (010) |
| PL-003 | Custom call confirmation (intent intercept) | ✅ (010) |
| PL-004 | Foreground service types Android 14+ | ❓ (зависит от будущего monitoring) |
| PL-005 | Package visibility Android 11+ | ❓ (для capabilities query 006) |
| PL-006 | Scoped storage Android 10+ | ❓ (для photo storage 012) |
| PL-007 | Battery optimization whitelist (OEM-specific) | 🟡 (010 SetupCheck) |
| PL-008 | OEM background restrictions (Samsung / Xiaomi / Huawei) | 🟡 (FCM polling fallback) |
| PL-009 | Doze mode handling | ✅ (007) |
| PL-010 | GMS-less device hard-block | ✅ (010) |
| PL-011 | Boot completion (launcher должен подняться сразу) | ❓ |
| PL-012 | App update / wire-format migration | 🟡 (rule 5) |
| PL-013 | OEM accessibility services compat | ❌ |
| PL-014 | Lock-screen widget / notification controls | ❌ |
| PL-015 | Device Owner / DPC provisioning (strict mode) | 🔮 (senior-safe-launcher-plan.md §5.1) |

### 7.8 iOS parity & cross-platform

ADR-001 + ADR-005 + context-decisions §1, §2, §13.

| ID | Кейс | Статус |
|---|---|---|
| iOS-001 | iOS launcher mode (заменить SpringBoard) | ❌ невозможно платформенно |
| iOS-002 | iOS как admin-only device (configure Android Managed) | 🔮 |
| iOS-003 | iOS как Managed (companion app, не launcher) | 🔮 |
| iOS-004 | iOS Guided Access / Supervised + MDM | 🔮 (senior-safe-launcher-plan §3.2) |
| iOS-005 | iOS contact tiles + share-intent | 🔮 |
| iOS-006 | Cross-platform pairing (admin iOS ↔ Managed Android) | 🔮 |
| iOS-007 | Documented Platform Asymmetry — где живёт несовместимое | 🟡 (ADR-005) |
| iOS-008 | Apple App Store policy gates | ❌ |
| iOS-009 | iCloud sync vs Firebase | ❌ (one-way door — не решено) |

### 7.9 Monetization & licensing

ADR-002 + ADR-003. Сейчас **полностью открыто** — нет ни одной спеки.

| ID | Кейс | Статус |
|---|---|---|
| M-001 | Free vs paid feature split | ❌ |
| M-002 | Subscription model (monthly / yearly) | ❌ |
| M-003 | Family pack (один admin → N Managed) | ❌ |
| M-004 | Trial period | ❌ |
| M-005 | Subscription expiry behavior (что отключается) | ❌ |
| M-006 | Payment provider per platform (Play Billing / App Store / Stripe) | ❌ |
| M-007 | Refund handling | ❌ |
| M-008 | Region-based pricing (geo §6) | ❌ |
| M-009 | Anti-abuse: device binding, receipt validation | ❌ (ADR-002) |
| M-010 | Subscription transfer при смене устройства admin'ом | ❌ |
| M-011 | Entitlement после потери account | ❌ |
| M-012 | B2B / partner pricing (клиники, retirement homes) | ❌ (partner distribution) |

### 7.10 Distribution channels

ADR-001. Полностью открыто.

| ID | Кейс | Статус |
|---|---|---|
| DIS-001 | Play Store baseline | 🟡 (есть PLAY-STORE-BLOCKER'ы) |
| DIS-002 | App Store baseline | ❌ |
| DIS-003 | Alternative stores (Aptoide, AppGallery, Galaxy Store) | ❌ |
| DIS-004 | Direct APK (partner / vendor / sideload) | ❌ |
| DIS-005 | Китайский рынок (нет Play Store) | ❌ (связан с PL-010 GMS-less) |
| DIS-006 | RuStore (РФ) | ❌ |
| DIS-007 | White-label / partner-branded build | ❌ |
| DIS-008 | Update channel per source | ❌ |
| DIS-009 | Compliance per store policy | ❌ |

### 7.11 Legal / compliance

PLAY-STORE-BLOCKER — критично перед публичным релизом.

| ID | Кейс | Статус |
|---|---|---|
| L-001 | GDPR — controller/processor responsibilities | ❌ |
| L-002 | 152-ФЗ — РФ persdata localization | ❌ |
| L-003 | CCPA (California) | ❌ |
| L-004 | LGPD (Brazil), PDPA (Singapore), etc. | ❌ |
| L-005 | Contacts privacy compliance | ❌ (LEGAL-001 PLAY-STORE-BLOCKER) |
| L-006 | Privacy policy / TOS — публикация и поддержка | ❌ |
| L-007 | Children's data (бабушка ≠ child, но WhatsApp contacts могут быть с внуками <13) | ❌ |
| L-008 | Store policy compliance (Google / Apple / regional) | ❌ |
| L-009 | Tax compliance per country | ❌ |
| L-010 | Subscription regulation (auto-renewal disclosures) | ❌ |
| L-011 | End-of-life inheritance — legal aspect | ❌ |
| L-012 | Right to be forgotten implementation | ❌ |

### 7.12 Support / error / feedback ops

Constitution + context-decisions §11. Operational contour, не feature.

| ID | Кейс | Статус |
|---|---|---|
| SUP-001 | Bug report from admin | ❌ |
| SUP-002 | Bug report from Managed (бабушка не напишет — но может пожаловаться admin'у) | ❌ |
| SUP-003 | Crash collection (Crashlytics? privacy concern!) | ❌ |
| SUP-004 | Telemetry opt-in/opt-out | ❌ |
| SUP-005 | In-app support contact (email, chat) | ❌ |
| SUP-006 | FAQ / help center | 🔮 (FUTURE-SPEC-006 onboarding-and-tutorials) |
| SUP-007 | Known-issues banner | ❌ |
| SUP-008 | Beta channel | ❌ |
| SUP-009 | Update notifications (new version) | ❌ |
| SUP-010 | AI/MCP-based support workflow (как мы сами работаем) | ❓ context §11 mention |

### 7.13 Performance / resource budget

Article IX. Заложено в `checklist-performance` + constitution.

| ID | Кейс | Статус |
|---|---|---|
| PF-001 | Cold start ≤ 650ms p95 | 🟡 (PERF-001 macrobenchmark module needed) |
| PF-002 | Frame budget (Compose render) | 🟡 |
| PF-003 | Background battery (15-min poll vs FCM) | ✅ (007) |
| PF-004 | Memory limit на low-end devices | ❌ |
| PF-005 | Storage limit (photos especially) | 🟡 (011 storage adapter) |
| PF-006 | Network usage (sync size, large config) | 🟡 (ARCH-009 config size limits) |
| PF-007 | Doze + background restrictions | ✅ (007) |
| PF-008 | Macrobenchmark в CI | ❌ (TODO-PERF-001) |
| PF-009 | 24-hour wakeups trial via Battery Historian | 🔮 (DEVICE-001) |

### 7.14 Dev / process / spec sequencing

Это вопросы про **процесс**, не про продукт. Но они влияют на скорость и качество.

| ID | Кейс | Статус |
|---|---|---|
| DV-001 | Spec sequencing — сейчас linear, нужна vertical-slice ориентация | ❓ D-2 |
| DV-002 | Cross-artifact tracing | ✅ (procedure-cross-artifact-trace) |
| DV-003 | Constitution checks | ✅ (procedure-constitution-check) |
| DV-004 | Local dev experience — невозможно тестировать без двух устройств | ❌ (S-105, S-801) |
| DV-005 | Secrets handling | ✅ (memory) |
| DV-006 | Emulator workflow | ✅ (skill android-emulator) |
| DV-007 | Physical-device QA | 🟡 (SPEC010-DEV-001/002 deferred) |
| DV-008 | OEM matrix smoke (Samsung / Xiaomi / Pixel) | ❌ (SPEC010-DEV-001) |
| DV-009 | 5 elder-user testing | ❌ (SPEC010-DEV-002) |
| DV-010 | CI/CD pipeline | ❌ |
| DV-011 | Release management / changelog | ❌ |
| DV-012 | ADR maintenance | 🟡 |

### 7.15 Future verticals (long-term roadmap)

Из `project-backlog.md` Future-Specs — это **отложенные продуктовые направления**, не cross-cutting.

| ID | Future spec | Status |
|---|---|---|
| F-001 | Wearable monitor (часы — пульс, давление, шаги) | 🔮 FUTURE-SPEC-001 |
| F-002 | Security sensors (охрана, smart home) | 🔮 FUTURE-SPEC-002 |
| F-003 | Closed messengers (LINE / WeChat / KakaoTalk) | 🔮 FUTURE-SPEC-003 |
| F-004 | Shared admin contact book | 🔮 FUTURE-SPEC-004 |
| F-005 | Preset editor (full settings) | 🔮 FUTURE-SPEC-005 |
| F-006 | Onboarding and tutorials | 🔮 FUTURE-SPEC-006 |
| F-007 | Bidirectional pairing (Managed настраивает admin'а тоже) | 🔮 FUTURE-SPEC-007 |
| F-008 | Family group shared encryption | 🔮 FUTURE-SPEC-008 |
| F-009 | Multi-device recovery + multi-device per owner | 🔮 FUTURE-SPEC-009 |
| F-010 | Key rotation / forward secrecy | 🔮 FUTURE-SPEC-010 |

### 7.16 Что выявилось в этой раскладке (мета-наблюдения)

- **Самые забитые домены**: pairing/trust (✅), config sync (✅), local UI (👀 нет!), accessibility core (👀 нет!).
- **Самые пустые домены**: monetization (вообще ничего), distribution (только Play Store baseline частично), legal/compliance (только PLAY-STORE-BLOCKER), support/ops (ничего).
- **Domains с PLAY-STORE-BLOCKER**: §7.5 (D-003 GDPR export, D-013 contacts privacy), §7.11 (L-001..L-012 — все), §7.13 (PF-001 cold-start без R8 minification — ARCH-006).
- **Cross-cutting domains, которые «прячутся в спеках 001-012»**, но не имеют отдельной спеки: §7.6 backend ops, §7.13 performance, §7.14 dev/process.
- **Domains с большим долгом «по умолчанию»**: §7.11 legal (нет ни одного решения), §7.9 monetization (нет ни одного решения).
- **Domains, которые надо думать ДО launcher UI**: §7.5 privacy (что хранится / в облаке) — определяет crypto model. §7.3 identity (anonymous vs named) — определяет account model.

### 7.17 Открытые вопросы по этим доменам (дополнение к D-1..D-10)

- **D-11.** **Monetization timing** — когда вводить subscription? До MVP-релиза (рискованно, отпугнёт early adopters) или после (легко обнаружить, что архитектура entitlement сделана плохо)?
- **D-12.** **Privacy posture default** — opt-in telemetry / opt-out telemetry / no telemetry at all? Senior audience особенно чувствителен.
- **D-13.** **Multi-admin privacy model** — admin1 видит, что внёс admin2? Полностью прозрачно или privacy-segmented?
- **D-14.** **iOS — admin-only сейчас или подождать?** iOS launcher mode невозможен платформенно. Admin-app для iOS — это **большая отдельная вертикаль**. Делать впрок или отложить?
- **D-15.** **Vendor / partner integration model** — клиники / hardware сейчас в 011 как «будущее», но архитектурно повлияют на trust / data / monetization. Нужно ли rough sketch уже сейчас, чтобы не закрыть один-way door?
- **D-16.** **Crash collection vs privacy** — без crash reporting качество не поднять. Crashlytics шлёт всё в Google. Sentry self-hosted — наш собственный сервер. Какой default?

---

## 8. Что делать с этим документом дальше

1. **User проверяет** — какие сценарии «да», какие «нет», какие «не понял».
2. **Из утверждённых сценариев** строится **product vision document** — 1-3 страницы, описывающий «как выглядит готовый продукт через лет 2».
3. **Из vision'а** строится **roadmap v2** — порядок спек, в котором каждая спека закрывает 1+ сценариев, и сценарии закрываются от **самых частых + критичных** к **редким edge cases**.
4. **Cross-check со спеками 001-012**: какие сценарии они закрывают, какие нет, есть ли преждевременные части (например, OWD-4 social recovery в 011 — нужен ли он сейчас или после S-104 решения?).
5. **Открыть спеку 013 / переоценить** в свете этой картины.

### Предложения user'у для дискуссии

- **D-1.** Архитектурный one-way door: **companion-only или allow self-serve?** Сейчас выбрано companion-only без ADR. Если оставить — нужен ADR. Если открыть self-serve — это новый набор спек и переработка 007/009/010.
- **D-2.** **Vertical slice testing** — должен ли каждый спек **по умолчанию** включать «local end-to-end test path»? Это меняет структуру `tasks.md`. Это **фундаментальный change** в spec-kit workflow.
- **D-3.** **End-of-life сценарии** (S-601..S-603) — нужно ли вообще их решать в нашем продукте, или это «не наша проблема»? Этический и legal вопрос upfront.
- **D-4.** **Конкурентные must-have** (SOS, медкарта, медикаменты) — это in-scope нашего продукта или мы фокусируемся на другом? Сейчас фокус — **коммуникации с семьёй** + **управляемость через admin**. SOS вписывается, медкарта — спорно.
- **D-5.** **«Empty state vs loading state»** — стандарт по проекту. Сейчас «загрузка...» показывается там, где должно быть «пусто, добавь первую плитку». Это нужно зафиксировать как cross-spec rule, не привязанный к одной фиче.

---

## 9. Источники (research)

- [User Journey Map: Ultimate Guide](https://uxcam.com/blog/user-journey-map/) — UXCam.
- [User Journeys vs. User Flows](https://www.nngroup.com/articles/user-journeys-vs-user-flows/) — Nielsen Norman Group.
- [User Scenarios in Design](https://pixso.net/articles/scenarios/) — Pixso, 7 tips.
- [User Stories, User Scenarios, and Use Cases — разница](https://blog.yotako.io/user-stories-user-scenarios-and-use-cases-understanding-the-differences/) — Yotako.
- [Jobs-to-be-Done framework guide](https://productschool.com/blog/product-fundamentals/jtbd-framework) — Product School.
- [JTBD examples](https://strategyn.com/jobs-to-be-done/jobs-to-be-done-examples/) — Strategyn (создатель оригинальной концепции).
- [Senior Android Launchers — обзор](https://blog.biglauncher.com/best-android-launcher-for-seniors/) — BigLauncher (один из конкурентов).
- [Best Senior-Friendly Launcher Apps](https://maketecheasier.com/best-android-launchers-for-senior-visually-impaired-users/) — Make Tech Easier.
- [Personalize Android For Older People](https://techlog360.com/android-launcher-apps-for-elderly/) — Techlog360.
- [Understanding User Requirements for Senior Mobile Health App](https://pmc.ncbi.nlm.nih.gov/articles/PMC9602267/) — PMC (academic).
- [Companion device pairing — Android Developers](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing) — официальная Android docs.
- [Fast Pair Companion App Integration](https://developers.google.com/nearby/fast-pair/companion-apps) — Google.
- [Wear OS pairing edge cases](https://support.google.com/wearos/answer/6057772/) — Google Support (хорошая модель recovery flows).

**Launcher patterns research (§5):**
- [10 Best Android Launchers 2026 — Beebom](https://beebom.com/best-android-launchers/) — обзор пост-Nova рынка.
- [Best Android Launchers 2026 после Nova shutdown](https://mypitshop.com/top-10-best-android-launchers/) — современный сравнительный обзор.
- [Niagara Launcher Review — A Masterclass in Minimalism](https://thechenderson.com/niagara-launcher-review/) — design philosophy.
- [Niagara Launcher Review 2026](https://www.gogi.in/niagara-launcher-review-2026-the-best-minimalist-android-launcher-for-a-distraction-free-phone.html) — one-handed UX.
- [BIG Launcher Manual v5.9](https://biglauncher.com/manual/en/) — canonical senior-launcher patterns (full reference).
- [Square Home — Play Store](https://play.google.com/store/apps/details?id=com.ss.squarehome2) — Metro tile launcher.
- [Add widgets — Google Support](https://support.google.com/android/answer/9450271?hl=en) — stock Android edit UX.
- [Android 15 adds widgets without dragging](https://macmyths.com/android-15-lets-you-add-widgets-without-dragging-to-homescreen/) — модерн picker → place паттерн.
- [Jiggle Mode на iPhone](https://www.howtogeek.com/692818/what-is-jiggle-mode-on-iphone-and-other-apple-devices/) — iOS edit mode reference.
- [Empty State UI Design Best Practices — Setproduct](https://www.setproduct.com/blog/empty-state-ui-design) — empty state как onboarding.
- [The Role Of Empty States In User Onboarding — Smashing Magazine](https://www.smashingmagazine.com/2017/02/user-onboarding-empty-states-mobile-apps/) — фундаментальная статья.
- [Empty State Design Variants — Mobbin](https://mobbin.com/glossary/empty-state) — UX patterns inventory.
- [Customize Android Home Screen — Senior Tech Club](https://seniortechclub.com/android/customize-your-android-home-screen-to-make-it-simple-productive/) — senior-friendly customization.
- [Android missing launcher icon — Medium](https://medium.com/android-news/android-and-the-mystery-of-the-disappearing-launcher-icon-154f9267f98e) — missing-app behavior reference.

**Accessibility research для пожилых (§6):**
- [Enhancing Android Accessibility for Users with Hand Tremor — ACM W4A 2015](https://dl.acm.org/doi/10.1145/2745555.2747277) — Touch Guard, 65% error reduction via enhanced area touch.
- [Improving Input Accuracy on Smartphones for Persons with Tremor — Uni Ulm](https://www.uni-ulm.de/fileadmin/website_uni_ulm/iui.inst.100/institut/mitarbeiterbereiche/plaumann/revisedResubmitMinorRevisions_comp.pdf) — motion-sensor approach, 40% fewer misses.
- [Smartphone Strategies for Tremor & Stiffness — Shirley Ryan AbilityLab](https://www.sralab.org/sites/default/files/downloads/2021-01/android_adaptation_education_0.pdf) — clinical strategies.
- [Hand tremors and the giant-button-problem — Axess Lab](https://axesslab.com/hand-tremors/) — UX-side compendium.
- [Optimizing mobile app design for older adults — PMC 2025 systematic review](https://pmc.ncbi.nlm.nih.gov/articles/PMC12350549/) — fresh academic systematic review.
- [Touchscreen Interface Design for Home Health Management Products — 2025](https://journals.sagepub.com/doi/10.1177/00315125251345594) — button size + position effects on elderly.
- [The psychological factors for elderly using mobile apps — Frontiers Psychology 2025](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2025.1609302/full) — perceptibility / operability / comprehensibility framework.
- [UX/UI Design for Elderly Users — Comprehensive Guide](https://medium.com/design-bootcamp/ux-ui-design-for-elderly-users-a-comprehensive-guide-ee49d1870099) — практический сборник patterns.
- [UI/UX for the Elderly: Compiled Research — Molly Oberholtzer](https://medium.com/@molly.oberholtzer/ui-ui-for-the-elderly-research-fa328f863714) — компиляция research'a.
- [Designing for Elderly Patients — eSEOspace](https://eseospace.com/blog/designing-for-elderly-patients/) — healthcare UX patterns.
- [Creative Haptic Interface Design for the Aging Population — IntechOpen](https://www.intechopen.com/chapters/63955) — haptic feedback для пожилых.
- [Haptics in Healthcare UX — BoundEV](https://www.boundev.com/blog/haptics-healthcare-ux-wearable-design-guide) — haptic confirmation paradigms.
- [Voice Access — Android Accessibility Help](https://support.google.com/accessibility/android/answer/6151848?hl=en) — голосовое управление, на которое опираемся вместо своего.
- [Voice Access app — Google Play](https://play.google.com/store/apps/details?id=com.google.android.apps.accessibility.voiceaccess&hl=en_US) — официальная страница.
- [Use AssistiveTouch on iPhone — Apple Support](https://support.apple.com/guide/iphone/use-assistivetouch-iph96b21954/ios) — Dwell Control reference.
- [How to set up Dwell Control in iOS 18 — Apple World Today](https://appleworld.today/2024/10/how-to-set-up-dwell-control-in-ios-18/) — конкретные UX параметры.
- [Android Low Vision Accessibility Tools](https://www.android.com/accessibility/vision/) — Magnification, TalkBack, color correction.
- [Choosing the Right Phone for Low Vision Users — New England Low Vision](https://nelowvision.com/choosing-the-right-phone-and-communication-tools-for-low-vision-users-essential-features-and-expert-tips/) — practitioner guide.
- [Wiser — Simple Senior Launcher (Softonic)](https://wiser-simple-senior-launcher.en.softonic.com/android) — 6-icon home pattern.
- [Top apps like Wiser — Softonic](https://wiser-simple-senior-launcher.en.softonic.com/android/alternatives) — competitive landscape.
- [Best Senior Launchers — BlogsDNA](https://www.blogsdna.com/25950/5-best-simple-android-launchers-for-seniors.htm) — обзор Necta + others.
- [Wiser Launcher — MakeUseOf review](https://www.makeuseof.com/tag/wiser-launcher/) — independent review.

---

## 10. Что я не делал в этом черновике (и user должен решить, надо ли)

- Не рисовал собственно **use case diagram** (UML-картинку). Если по диаграммам user обучался — можем нарисовать в Mermaid под утверждённое подмножество сценариев.
- Не привязывал сценарии к **функциональным требованиям** (FR-XXX) спек 001-012. Это следующий шаг — сейчас цель только **inventory** сценариев.
- Не приоритизировал внутри одного раздела. Это сделаем при построении roadmap v2 после согласования inventory.
- Не разделил Android vs iPhone — фокусировался на Android (текущая платформа). iPhone-сценарии (Guided Access / MDM / supervised) — отдельный заход.
