# Feature Specification: F-5 — Root Key Hierarchy + ConfigDocument Encryption + Recovery

**Feature Branch**: `018-f5-config-e2e-encryption`
**Created**: 2026-06-19
**Status**: Implemented as **F-5b envelope variant** (architecturally pivoted 2026-06-20)
**Input**: User description: «F-5: ConfigDocument E2E Encryption — production blocker для cloud release».

> ## 🔀 2026-06-20 — Architectural pivot to F-5b (envelope pattern)
>
> После mentor-сессии 2026-06-20 решено заменить symmetric-self-edit pattern
> (root key + DEK + AEAD под admin'ский UID) на **hybrid envelope pattern**
> per [`specs/011-contacts-and-e2e-encrypted-media/spec.md` §C-2/§C-3](../011-contacts-and-e2e-encrypted-media/spec.md#L125).
>
> **Что изменилось** по сравнению с исходным F-5 scope:
> - Wire format `SealedConfig` (single-recipient AEAD) → `Envelope` с
>   `recipientKeys: Map<DeviceId, sealedCEK>` (N≥1 получателей).
> - Concept `KeyRegistry` / `WrappedDek` / per-DEK lookup **удалён**. Каждый
>   blob получает свой одноразовый CEK; CEK обёрнут под каждый recipient
>   X25519 pub key через `crypto_box_seal`.
> - Появились новые ports: `RemoteStorage` (caller facade), `ConfigSaver`
>   (product-domain wrapper), `EnvelopeBootstrap` (publish my pub key after
>   sign-in), и internal SPI (`ConfigCipher2`, `RecipientResolver`,
>   `EnvelopeStorage`, `PublicKeyDirectory`, `DeviceIdentity`).
> - Cross-UID delegation **не отложен в S-2**, а реализован в этом же спайке:
>   admin может править бабушкин конфиг через access-grant в её namespace,
>   обе стороны расшифровывают (membership-agnostic envelope).
> - `RootKeyManager` + `RecoveryFlow` + `RecoveryKeyVault` сохранены — они
>   восстанавливают **root key через passphrase** (не envelope keys), это
>   отдельный концерн.
>
> **Откуда взят envelope pattern**: spec 011 §C-2 явно зафиксировал
> «envelope format с первого commit поддерживает список получателей
> произвольной длины; в спеке 011 список всегда содержит одного получателя.
> В будущих спеках список расширяется до N без изменения envelope format.»
> Цена закладывания = 0 строк сейчас, экономия = миграция всех blob'ов в
> будущем.
>
> **Удалённые legacy артефакты**: см. commit `d135216` (Batch 6 removal).
> Этот документ описывает **итоговую модель F-5b**; устаревшие FR-005,
> FR-015..017, FR-024 переписаны под envelope.

> **Scope evolved через clarify session 2026-06-19** (исторический контекст):
> исходный «multi-admin envelope encryption» оказался смешением двух разных задач.
> Декомпозиция clarify-сессии:
> - **F-5 (эта спека)** = **root key hierarchy + первый потребитель (ConfigCipher) + recovery flow**. Foundation для всех будущих cloud-фичей.
> - **S-2 (Phase 2)** = multi-admin envelope, pairing-восстановление, ghost device defence. См. [enhancement notes 2026-06-19](../../docs/product/roadmap.md#enhancement-notes-2026-06-19--multi-admin-encrypted-config-sharing-из-f-5-clarify-session).
>
> **После 2026-06-20 pivot multi-recipient envelope перешёл в F-5b scope**;
> S-2 теперь покрывает только UX-уровень (group management UI, role enum,
> server arbitration), не crypto, так как crypto уже готов через
> membership-agnostic envelope.

> **Roadmap anchor**: [`docs/product/roadmap.md` шаг 3 Phase 1](../../docs/product/roadmap.md#шаг-3--f-5-root-key-hierarchy--configdocument-encryption--recovery--🔴-production-blocker).
> **Закрывает**: [TODO-SEC-CRITICAL-024](../../docs/dev/project-backlog.md).
> **Зависит от**: F-4 (spec 017, AuthIdentity), F-CRYPTO (spec 016, primitives).
> **Создаёт foundation для**: S-2, S-4, S-5, S-8, S-9, V-2, V-3.

## Clarifications

### Session 2026-06-19

- Q: Frequency смены recipients (admin add/remove) в реальной семье? → A: 1-2 раза за всё время. Изредка 2-3 раза за 10 минут при ошибочной отмене pairing'а. **Low frequency** → допускает lookup-по-запросу, не требует push-based cache invalidation.
- Q: Pairing flow с QR + out-of-band fingerprint verification (Safety Number screen)? → A: «Чем тупее сервер, тем лучше». QR pairing уже реализован в spec 007. Safety Number screen — отложено в [`server-roadmap.md SRV-CRYPTO-005`](../../docs/dev/server-roadmap.md) как accepted risk.
- Q: Recovery при потере телефона admin'а? → A: Через двухфакторную авторизацию, не rescan QR. Расширилось до «pairing концептуально = частный случай recovery» (отдельная concept-note в memory).
- Q: Master key / cross-signing в schema F-5? → A: B — задел в SealedConfig wire-format на nullable поле `recipientMasterSignature: ByteArray?`. MVP всегда null, future feature заполняет (additive change, не breaking).
- Q: Local encryption (config на disk admin'а)? → A: A — `android:allowBackup="false"` + plaintext local. Защита от Google Drive backup leak'а, **не** от forensic acquisition.
- Q: Scope F-5 — узкая (encryption only) или расширенная (encryption + recovery)? → A: Расширенная. Encryption без recovery = technical debt without user value.
- Q: Где живёт recovery key? → A: Multi-method recovery через port-based архитектуру. MVP adapter = Firestore с passphrase-encrypted root key. Passphrase UX = Android Autofill `newPassword` hint → Google Password Manager «Suggest strong password». Без QR backup, без email, без SMS.
- Q: CEK rotation (новый ключ при каждом push'е vs один на жизнь)? → A1 — один key на жизнь. Это **состояние** (config), не **переписка** (messages). Industry pattern для cloud-config.
- Q: Смена passphrase после setup в MVP? → A: F2 — отложено. Inline TODO future-spec. В MVP смена = «сбросить F-5» (потеря cloud config).
- Q: Senior fallback при недоступности облака? → A: Three-tier cache model (memory `project_config_cache_model`) уже решает. F-5 не добавляет bundled default config.
- Q: Root key как универсальный wrapping key для **всех** persistent ключей на устройстве (Variant 3)? → A: Да. Один root key защищает все DEKs через `KeyRegistry`. F-5 регистрирует первый DEK (ConfigCipher), будущие спеки регистрируют свои (X25519 pair-keys в S-2, photo keys в S-5, messenger keys в V-2).
- Q: F-CRYPTO нужно переписывать? → A: Нет. F-CRYPTO даёт примитивы. `core/keys/` — новый KMP module-слой паттернов над F-CRYPTO, ~10 файлов. Не нарушает F-CRYPTO API.
- Q: Cross-app sharing root key с future messenger (V-2) / album (V-3)? → A: Не строим сейчас. **Но** format root key спроектирован так, чтобы future broker pattern (Microsoft Authenticator подход) или независимый Firestore access (Meta apps подход) был возможен без breaking change.
- Q: Sign-out / Sign-in поведение? → A: Sign-out НЕ wipe'ит root key из Keystore. Recovery flow только когда Keystore физически пуст (переустановка / новое устройство / factory reset).

## Сценарии использования

> Эти сценарии — концентрированный взгляд «как это будет работать в реальной жизни».
> Читая их, можно проверить, движется ли спека в правильном направлении, без необходимости погружаться в FRs.
> Каждый сценарий соответствует одному или нескольким FRs (помечено в конце).

### Сценарий 1 — Admin шифрует конфиг и отправляет в облако

**Контекст**: Admin (взрослый родственник) ставит приложение в первый раз. Хочет начать настраивать бабушкин телефон удалённо.

1. Admin запускает приложение на своём телефоне. Видит экран приветствия.
2. ★ Приложение просит войти через Google. Admin входит в свой Google-аккаунт.
3. ★ Приложение говорит: «Придумайте пароль для восстановления. Если потеряете телефон — нужно будет ввести его на новом, чтобы вернуть свои настройки». Появляется поле ввода с подсказкой над клавиатурой «Создать надёжный пароль».
4. Admin нажимает «Создать надёжный пароль». Google Password Manager (или Bitwarden, если установлен) сам генерирует случайный пароль, сохраняет в своём хранилище и подставляет в поле. **Пароль не показывается на экране** (защита от подсматривания через плечо).
5. ☆ Рядом — кнопка «Скопировать в буфер обмена». Admin может вставить во внешний менеджер паролей (Bitwarden desktop, KeePass, и т.д.). Буфер автоматически очистится через 60 секунд.
6. Приложение создаёт внутри устройства главный ключ. Этот ключ хранится в защищённой части телефона (Android Keystore). Также приложение делает зашифрованную копию этого ключа под пароль и отправляет в облако — на случай потери телефона.
7. Admin начинает редактировать конфиг бабушкиного телефона — двигает плитки, добавляет контакты, выбирает темы.
8. Когда admin сохраняет — приложение шифрует весь конфиг главным ключом и отправляет на сервер. Сервер видит только «мешок байтов», содержимое для Firebase / Google недоступно.

**Что закрывает**: US-1, FR-001..005, FR-013a, FR-015..017, FR-029, SC-001.

**Trouble case 1.b — атака на конфиг в облаке**: Кто-то с доступом к Firestore (Google инженер, утечка credentials) скачивает зашифрованный конфиг и пытается его читать. Без главного ключа — видит только случайные байты, никаких имён / телефонов / layout'а.
**Закрывает**: SC-001.

**Trouble case 1.c — слишком большой конфиг**: Admin случайно создаёт конфиг больше 256 KB (например, гигантское описание контакта). Приложение не отправляет, показывает ошибку «конфиг слишком большой».
**Закрывает**: FR-029.

---

### Сценарий 2 — Восстановление после потери телефона

**Контекст**: Admin потерял телефон. Купил новый. Хочет вернуть свой конфиг и снова управлять бабушкиным телефоном.

1. Admin ставит приложение на новый телефон. Видит экран приветствия.
2. ★ Входит через тот же Google-аккаунт, что был на старом телефоне.
3. Приложение видит: «этот UID мне знаком, в облаке лежит зашифрованная копия главного ключа». Показывает экран: «Введите пароль для восстановления».
4. Поле ввода имеет подсказку «Заполнить из сохранённых паролей». Admin тапает поле → Google Password Manager сам подставляет ранее сохранённый пароль.
5. Приложение скачивает зашифрованную копию ключа из облака, расшифровывает паролем, кладёт восстановленный главный ключ в защищённую часть нового телефона.
6. Все данные admin'а из облака становятся доступны: конфиг бабушки, его настройки, история.

**Что закрывает**: US-2, FR-003, FR-006..010, FR-013, SC-003, SC-004.

**Trouble case 2.b — забыл пароль, Autofill не сохранил**: Admin не использовал менеджер паролей и не помнит пароль наизусть. Вводит неправильно 3 раза. Приложение предлагает: «Настроить как новое устройство?». Если admin соглашается — старый зашифрованный конфиг в облаке становится мусором (никто не может расшифровать). Admin начинает с нуля: создаёт новый главный ключ, новый пароль.

**Однако** все pair-ключи к бабушкиному телефону тоже потеряны вместе со старым главным ключом. Admin теперь **чужой** для бабушкиного телефона. Никакой автоматической «переотправки» конфига нет. Для возврата управления admin **должен заново сделать pairing** с бабушкиным телефоном — физически рядом (QR), либо через future remote re-pairing flow из S-2. Только после успешного pairing'а admin снова видит её конфиг и может его редактировать.

**Закрывает**: FR-027, SC-004, явная отметка зависимости от S-2.

**Trouble case 2.c — Huawei / устройство без Google services**: Admin купил Huawei P-series без GMS. Google Sign-In недоступен. Приложение показывает «Облачные функции недоступны на этом устройстве, работаем без облака». Recovery не происходит, app работает локально без cloud config.

**Будущая поддержка Huawei**: когда переедем на свой сервер (per [SRV-RECOVERY-001](../../docs/dev/server-roadmap.md)) — Huawei поддерживается через SMS / email / собственный auth вместо Google Sign-In. Это **отдельная future-spec** — будет реализован `OwnServerIdentityProof` adapter через тот же port `IdentityProof`. F-5 spec при этом не меняется (port-based архитектура).

**Закрывает**: FR-028, SC-007.

**Trouble case 2.d — Sign-out и Sign-in под тем же Google**: Admin делает «выйти из аккаунта» и потом «войти заново» под тем же Google. Никакого recovery не запускается — главный ключ всё ещё в защищённом хранилище телефона. Приложение продолжает работать как ни в чём не бывало.
**Закрывает**: SC-005.

---

### Сценарий 3 — Sign-in под другим Google-аккаунтом

**Контекст**: У admin'а несколько Google-аккаунтов (личный и рабочий). Хочет проверить, как приложение работает под другим аккаунтом, потом вернуться.

1. Admin вышел из своего основного Google и зашёл под рабочим.
2. Приложение видит: «новый UID, не знаком». Проверяет облако — есть ли что-то для этого UID. Нет.
3. Запускается сценарий первой настройки (как Сценарий 1) — генерируется **новый** главный ключ, новый пароль, новый конфиг. **Старые** ключи личного UID остаются в защищённом хранилище отдельно, никуда не пропадают.
4. Admin тестирует под рабочим Google, потом выходит.
5. Admin снова заходит под личным Google. Приложение видит: «этот UID мне знаком, ключи уже есть в хранилище». **Никакого recovery не запускается**, никакого пароля не спрашивается. Приложение сразу работает с личными данными как раньше.

**Что закрывает**: US-2 edge case, FR-031, FR-031a, SC-006.

---

### Сценарий 4 — Будущая фича пользуется тем же главным ключом + cross-app vision

**Контекст**: Через полгода добавляется фича S-2 (multi-admin: вторая admin'ская сестра тоже хочет читать конфиг той же бабушки). Через год — фича S-5 (фотографии семейного альбома). Через два года — отдельное приложение-мессенджер (V-2).

1. Admin (с уже настроенным главным ключом) ставит обновление приложения с S-2.
2. S-2 хочет создать X25519 ключи для связи с бабушкой. **Не создаёт свой собственный «главный ключ»**, не просит admin'а ввести новый пароль. Просто говорит реестру ключей: «зарегистрируй мой новый ключ под именем pair-x25519».
3. Реестр шифрует новый ключ S-2 главным ключом, кладёт в хранилище. Admin вообще не замечает — никаких новых экранов.
4. Через год добавляется S-5 (фото). Та же история — S-5 регистрирует свой ключ для фото, не просит admin'а ничего вводить.
5. Если admin потеряет телефон сейчас — recovery (Сценарий 2) восстанавливает главный ключ. **Автоматически** восстанавливаются **все** зарегистрированные под ним ключи: и для multi-admin pair, и для фото. Admin вводит пароль один раз — всё работает.

**Что закрывает**: US-3, FR-004, FR-005, FR-023, SC-008.

**Trouble case 4.b — мессенджер хочет тот же главный ключ через границу приложений**: Через два года выпускается отдельное приложение-мессенджер (V-2, отдельная регистрация в Google Play). Хочет переиспользовать главный ключ admin'а. Технически Android **не позволяет** одному APK напрямую читать ключи другого APK — Keystore изолирован per-package.

**Owner decision 2026-06-19** (зафиксировано в [`multi-app-cohabitation.md`](../../docs/product/future/multi-app-cohabitation.md#owner-decision-2026-06-19)): **Path A — broker pattern + единый signing key**. Все приложения экосистемы (launcher, мессенджер, фото, future apps) подписываются **одним signing key** в Google Play. Launcher экспортирует свой Keystore-доступ через signature-level permission. Мессенджер, проверенный Android'ом криптографически (signature совпадает), может запросить нужный ключ через защищённый канал. Чужое приложение с другим signing key Android просто не пустит к каналу — пользователь это отключить **не может**.

**Owner-vision cross-app UX**: User устанавливает launcher → launcher предлагает «установить мессенджер и фото-альбом из той же экосистемы?». User соглашается одним кликом. Все приложения устанавливаются и **сразу** работают без отдельного входа в каждое — у всех один Google UID, один главный ключ. Дополнительно: launcher может предложить **единый wizard** для всех co-family apps — собирает их wizard manifests и проводит user через один объединённый поток настройки.

**F-5 НЕ реализует broker pattern** — это P-10 territory (см. [roadmap.md P-10](../../docs/product/roadmap.md#phase-3-mvp-preset-depth-sequential-10-p-спек--тоже-mvp)). Но формат главного ключа в F-5 спроектирован так, чтобы broker pattern был возможен без переделки.

**Закрывает**: FR-022..024, FR-024a (signing key strategy decision).

---

### Сценарий 5 — Будущая миграция криптоалгоритма

**Контекст**: Через 5-10 лет XChaCha20-Poly1305 или Argon2id могут стать устаревшими. F-5 должен быть готов к миграции, не блокировать её.

1. В облаке появляется новый клиент с новым алгоритмом.
2. Старые клиенты продолжают работать — wire-format содержит явное поле «algorithm: xchacha20poly1305-v1». Старые клиенты видят знакомое значение, используют старый алгоритм, читают данные.
3. Когда выходит обновление приложения с поддержкой нового алгоритма — admin обновляется. Приложение замечает: «мой главный ключ зашифрован старым алгоритмом, надо пере-зашифровать новым».
4. Запускается **отдельная спека migration** (за рамками F-5): просит admin'а ввести пароль один раз → расшифровывает старый главный ключ → шифрует под новый алгоритм → отправляет в облако. Все DEKs (config, pair, photo) перешифровываются под новый AEAD.
5. После миграции старый клиент перестаёт работать (в облаке только новый формат). Принудительное обновление приложения.

**Что закрывает**: FR-032, FR-033, FR-033a, готовность к [SRV-CRYPTO-008](../../docs/dev/server-roadmap.md#srv-crypto-008).

---

### Сценарий 6 — Future-readiness: переход на двухфакторку через год (проверка, что F-5 не придётся переписывать)

**Контекст**: Через год после ship'а F-5 решено: passphrase в Autofill пользователям непонятен, заменяем на двухфакторную авторизацию (Google Sign-In + SMS + push-confirmation с уже paired устройства). Проверяем гипотезу: придётся ли переписывать F-5?

1. Команда разработки добавляет три новых adapter'а к существующим port'ам:
   - `SmsIdentityProof` (новый adapter port `IdentityProof`).
   - `PushConfirmIdentityProof` (новый adapter, требует pairing'а из S-2).
   - `MultiFactorIdentityProof` (composite: требует Google + SMS + Push одновременно).
2. UI экрана setup переписывается: вместо поля «придумайте пароль» — экран «введите номер телефона», ожидание SMS, ввод 6-цифрового кода, push на старое устройство с подтверждением.
3. **Что НЕ меняется в F-5 spec / core/keys/**:
   - `RecoveryKeyVault` port (как лежал зашифрованный главный ключ в облаке — так и лежит, формат не трогается).
   - `RootKeyManager` (как генерировал и хранил главный ключ — так и продолжает).
   - `KeyRegistry` (все DEKs S-2, S-5 продолжают работать).
   - `ConfigCipher` (шифрование/расшифровка конфига — то же самое).
   - Все ConfigDocument'ы и DEKs в облаке — продолжают расшифровываться без переделки.
4. **Существующие пользователи**, кто setup'ился с passphrase — продолжают работать со своим passphrase. Их main key wrapped тем же AEAD, только KDF — Argon2id от passphrase. Coexistence через `MultiFactorIdentityProof`, который при recovery определяет, какие факторы доступны: видит старый формат vault → запрашивает passphrase. Видит новый — запрашивает SMS+push.
5. **Новые пользователи** setup'ятся через SMS+push без passphrase. Их vault имеет новый `algorithm` строку (например, `"kdf:sms-derived-v1"`), новый wrapped key.
6. Recovery vault на сервере имеет поле algorithm — клиент по нему понимает, какой adapter применить. Сосуществование версий поддерживается per FR-032.

**Закрывает**: FR-006..009 (port-based architecture), FR-010 (algorithm field), FR-032 (сосуществование версий).

**Trouble case 6.b — старый пользователь хочет перейти на новую двухфакторку**: На старого пользователя приходит push «новая защита доступна, перейти?». Admin соглашается, проходит SMS+push setup. Recovery vault перешифровывается новым форматом, старый удаляется из облака. С этого момента admin теряет старый passphrase recovery — только SMS+push работает.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Cloud-конфиг улетает зашифрованным, без plaintext в Firebase (Priority: P1)

Admin (родственник пожилого) меняет на своём телефоне раскладку плиток / контакты / labels. Конфиг улетает в облако, чтобы синхронизироваться с его другими устройствами или для recovery после потери телефона. Содержимое (имена контактов, телефоны, какие приложения открывает бабушка, какие у неё ярлыки) **никогда** не должно быть видно Firebase, нашим админам, или кому-то с доступом к Firestore backup'ам / логам.

**Why this priority**: Без P1 нельзя выпустить продукт в production. **Критическая privacy regression** (CLAUDE.md rule 1 + privacy decision 2026-05-28).

**Independent Test**: JVM-тест + Firebase Emulator: client запечатывает `ConfigDocument`, пишет в эмулятор; прямое чтение из Firestore эмулятора показывает, что payload — opaque bytes без читаемых имён/телефонов/labels. Тот же client с правильным root key расшифровывает обратно byte-equal.

**Acceptance Scenarios**:

1. **Given** admin прошёл Sign-In (F-4) и setup root key с passphrase, **When** admin пушит обновлённый `ConfigDocument` в Firestore, **Then** запись не содержит ни одного plaintext-имени / телефона / label.
2. **Given** атакующий с read-access к Firestore project, **When** он скачивает все документы `users/{uid}/config`, **Then** не может восстановить ни одного имени без passphrase.
3. **Given** Firebase logs / Firestore backup, **When** в них ищется fixture-имя (например, «Bobby Tables 555-1234»), **Then** 0 совпадений.

---

### User Story 2 — Восстановление cloud-конфига после переустановки приложения (Priority: P1)

Admin потерял телефон / переустановил приложение / купил новый. Ставит app, делает Google Sign-In под **тем же** Google account. Приложение видит, что recovery key уже когда-то сохранялся в облаке. Запрашивает passphrase. **Google Password Manager (или другой Autofill provider) автоматически подставляет** ранее сохранённый passphrase. Recovery key расшифровывается, ConfigDocument из облака становится доступен.

**Why this priority**: Без recovery «encryption only» = technical debt без user value. Owner-decision 2026-06-19: F-5 ship'ится только с recovery.

**Independent Test**: JVM-интеграционный тест:
1. Сценарий setup: новый клиент, Sign-In, generate root key, сохранить wrapped в Firestore Emulator, push ConfigDocument.
2. Сценарий восстановления: новый клиент (clean Keystore), Sign-In под тем же UID, pull recovery doc из Emulator, ввести passphrase (test fixture), расшифровать root key, pull ConfigDocument, расшифровать → byte-equal с исходным.

**Acceptance Scenarios**:

1. **Given** admin прошёл setup на старом телефоне, **When** на новом телефоне он Sign-In'ит тем же Google account и приложение находит recovery doc в Firestore, **Then** показывается экран «введите passphrase для восстановления» с полем `autofillHints="password"`, Android Autofill предлагает сохранённый passphrase.
2. **Given** admin ввёл правильный passphrase, **When** клиент расшифровывает recovery key, **Then** ConfigDocument из Firestore становится доступен, byte-equal с тем, что был на старом телефоне.
3. **Given** admin ввёл неправильный passphrase, **When** клиент пытается расшифровать, **Then** показывается ошибка «неверный passphrase, попробуйте ещё раз» (не «recovery key поврежден»).
4. **Given** admin никогда не использовал Autofill и забыл passphrase, **When** он 3 раза вводит неправильно, **Then** показывается опция «настроить заново» (потеря cloud config, бабушке admin переотправит config заново).

---

### User Story 3 — Foundation для будущих cloud-фичей (Priority: P2)

Когда позже добавляется S-2 (multi-admin envelope), S-5 (photo encryption), S-4 (SOS encryption), V-2 (messenger) — каждая из них регистрирует **свой** DEK в `KeyRegistry` поверх **того же** root key. При recovery восстанавливается root key → все DEKs автоматически становятся доступны (потому что они wrapped под root).

**Why this priority**: Это design intent, не отдельная feature MVP. Без P3 каждая cloud-спека изобретёт **свой** key management → дублирование, inconsistency, отсутствие recovery.

**Independent Test**: JVM-тест:
1. F-5 setup root key + register `ConfigCipher DEK`.
2. Симуляция S-2: register `PairX25519 DEK`.
3. Симуляция recovery: clean state, recover root key, проверить что оба DEKs доступны через KeyRegistry.

**Acceptance Scenarios**:

1. **Given** `KeyRegistry` создан в F-5 с root key и одним DEK (`ConfigCipher`), **When** future code (mock) регистрирует второй DEK через `KeyRegistry.registerDek("PairX25519", randomKey)`, **Then** оба DEK сохраняются в Keystore wrapped под root.
2. **Given** clean state (Keystore пуст) после recovery, **When** root key восстановлен через RecoveryKeyVault, **Then** все ранее зарегистрированные DEKs становятся доступны без дополнительного passphrase prompt'а.
3. **Given** F-5 wire-format `KeyRegistry` schema, **When** future spec (S-2 / S-5) добавляет новый DEK, **Then** это **additive change** — старые клиенты не падают на unknown DEK names (просто игнорируют).

---

### User Story 4 — Senior работает из app cache, не зависит от облака (Priority: P2)

Senior (бабушка) видит на launcher'е config, отправленный admin'ом. Когда облако недоступно (нет интернета, Firestore down, admin не успел push'нуть) — Senior **продолжает работать** из локального app cache (three-tier cache model, memory `project_config_cache_model`).

**Why this priority**: Это **NOT a new requirement в F-5**. Three-tier cache model уже описана в spec 016/017. F-5 **обязана не сломать** этот инвариант — расшифровка происходит при pull с сервера, app cache хранит расшифрованный config plaintext (local-only).

**Independent Test**: Integration-тест: pull config из Firestore → decrypt → app cache (plaintext). Отключить network → Senior всё ещё видит config из cache.

**Acceptance Scenarios**:

1. **Given** Senior offline > 24h, **When** Senior использует launcher, **Then** работает из последнего app cache, никаких alert'ов «нет связи».
2. **Given** Senior установил новое приложение и сделал Sign-In, **When** Firestore вернул пустой config (admin ещё не push'нул), **Then** Senior видит wizard / default (S-1 territory, не F-5).

---

### Edge Cases

- **Sign-out → Sign-in под тем же Google account** на том же устройстве: root key остаётся в Keystore (Keystore не очищается при sign-out). Никакого recovery не запускается. App продолжает работать с теми же ключами.
- **Sign-in под ДРУГИМ Google account** на том же устройстве: новый UID → `KeyRegistry.hasRoot(uid2)` → false → Firestore lookup. Если recovery doc для UID2 существует → recovery flow. Если нет → генерируется новый root key для UID2 (per FR-031). Старые ключи UID1 **остаются в Keystore изолированно** под UID1 namespace — при возврате на UID1 ключи и config снова доступны без recovery. Identity → ключи isolated 1-к-1.
- **Переустановка приложения / factory reset**: Android Keystore wiped для этого package. На следующем Sign-In — recovery flow обязателен. Если recovery doc в Firestore есть → passphrase prompt → restore. Если нет → новая установка (новый root key).
- **Огромный config (> 256 KB plaintext)**: AEAD продолжает работать, но Firestore document size limit (1 MB). Soft-cap **256 KB plaintext** — за этим клиент режектит push с понятной ошибкой.
- **Atckуs пытается заmenить wrapped recovery key в Firestore**: AEAD authentication tag не сходится → расшифровка возвращает `CryptoError.AeadAuthFailed` → UI: «не удалось расшифровать, попробуйте recovery заново, либо настроить как новое устройство».
- **Huawei / не-GMS устройство**: `NoOpRecoveryAdapter` активируется (см. F-4 AuthAdapterSelector pattern). Recovery недоступен, app работает в **local mode forever**. UI: «облачные функции недоступны на этом устройстве» (как F-4).
- **Passphrase brute-force через scraping Firestore + offline cracking**: passphrase derived ключ через **Argon2id** (memory-hard KDF) — каждая попытка cracking занимает значительное CPU. Server-side rate-limit в SRV-RECOVERY-001 (future) добавит вторую защиту. В MVP — только Argon2id.
- **Recovery key corrupted в Firestore** (битый блob, неправильный schemaVersion): доменная ошибка `RecoveryError.MalformedVault` → UI «не удалось загрузить recovery, настроить как новое устройство?».

## Requirements *(mandatory)*

### Functional Requirements

**`core/keys/` module — root key hierarchy**

- **FR-001**: System MUST экспонировать новый KMP module `core/keys/` (`lib-family-keys`) со следующими domain ports в пакете `com.launcher.api.keys` (consistent с существующей convention `com.launcher.api.crypto` из F-CRYPTO и `com.launcher.api.auth` из F-4; подпакеты `com.launcher.api.keys.api` для ports, `com.launcher.api.keys.impl` для adapters):
  - `KeyRegistry` — управление DEK'ами под root.
  - `RootKeyManager` — генерация / unwrap / wipe root key.
  - `IdentityProof` — port для подтверждения identity (MVP adapter: Google Sign-In via F-4).
  - `RecoveryKeyVault` — port для cloud-storage recovery doc (MVP adapter: Firestore).
- **FR-002**: `core/keys/` MUST использовать только примитивы из F-CRYPTO (`AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `SecureKeyStore`). Никаких прямых libsodium вызовов. F-CRYPTO API НЕ модифицируется.
- **FR-003**: `RootKeyManager` MUST поддерживать операции:
  - `getOrCreate(identity: AuthIdentity): RootKey` — если root key для identity существует в Keystore → вернуть; если нет — попытаться recovery через `RecoveryKeyVault`; если recovery doc нет → сгенерировать новый.
  - `wipe(identity: AuthIdentity)` — удалить root key + все wrapped DEKs из Keystore (для factory reset / sign-out-and-wipe flow, **в MVP опционально**).
- **FR-004**: `KeyRegistry` MUST поддерживать операции:
  - `registerDek(name: String, dekMaterial: ByteArray): Outcome<Unit, KeyRegistryError>` — зашифровать DEK под root key, сохранить в storage (Keystore wrapped + optional Firestore doc для cross-device sync).
  - `getDek(name: String): Outcome<ByteArray, KeyRegistryError>` — расшифровать и вернуть DEK plaintext (в RAM).
  - `hasDek(name: String): Boolean`.
- **FR-005**: `KeyRegistry` storage schema MUST содержать `schemaVersion: Int` с первого коммита (CLAUDE.md rule 5). Adding new DEK names — additive (backward-compatible). Removing / renaming требует `schemaVersion` bump + migration.

**Recovery flow — `IdentityProof` + `RecoveryKeyVault`**

- **FR-006**: `IdentityProof` port MUST экспонировать:
  - `currentIdentity(): AuthIdentity?` — текущая authenticated identity (или null если не подписан).
  - `identityFlow: Flow<AuthIdentity?>` — stream identity-изменений (emission при sign-in / sign-out). Нужно `RootKeyManager` для будущего реактивного wipe-handling при sign-out.
  - `requestSignIn(): Outcome<AuthIdentity, IdentityError>` — UI-triggered flow для логина.
  - `signOut(): Outcome<Unit, IdentityError>` — sign-out flow. НЕ wipe'ит RootKey / KeyRegistry (Keystore сохраняется per clarify Q «Sign-out поведение»).
- **FR-007**: MVP `IdentityProof` adapter (`GoogleSignInIdentityProof`) MUST переиспользовать `AuthProvider` из F-4 (spec 017). Никакой дублирующей Google Sign-In инфраструктуры в `core/keys/`.
- **FR-008**: `RecoveryKeyVault` port MUST экспонировать:
  - `fetchVault(uid: StableId): Outcome<RecoveryVaultBlob, VaultError>` — забрать wrapped root key для данной identity.
  - `storeVault(uid: StableId, blob: RecoveryVaultBlob): Outcome<Unit, VaultError>` — сохранить wrapped root key.
  - `deleteVault(uid: StableId): Outcome<Unit, VaultError>` — для опционального factory reset.
- **FR-009**: MVP `RecoveryKeyVault` adapter (`FirestoreRecoveryKeyVault`) MUST хранить vault в Firestore по пути `users/{uid}/recovery-key`. Firestore Security Rules MUST разрешать read/write только для owner UID (никакой third-party доступ).
- **FR-010**: `RecoveryVaultBlob` wire-format MUST содержать:
  - `schemaVersion: Int` (стартует с 1).
  - `algorithm: String` (e.g. `"argon2id-xchacha20poly1305-v1"`).
  - `kdfSalt: ByteArray` (random 16 bytes для Argon2id).
  - `kdfParams: { memory: Int, iterations: Int, parallelism: Int }` — параметры Argon2id для tuning.
  - `wrappedRootKey: ByteArray` (root key зашифрован passphrase-derived key через AEAD).
  - `nonce: ByteArray` (для AEAD).
  - `createdAt: Long` (Unix ms, для debugging).

**Passphrase UX**

- **FR-011**: UI для passphrase setup MUST использовать стандартное Android `EditText` с атрибутом `autofillHints="newPassword"` — это активирует Google Password Manager / Bitwarden / 1Password «Suggest strong password» chip над клавиатурой.
- **FR-012**: UI для passphrase recovery MUST использовать `autofillHints="password"` — Autofill сам предлагает сохранённый пароль.
- **FR-013**: System MUST НЕ хранить passphrase plaintext **нигде** (ни в RAM дольше необходимого, ни в logs, ни в analytics). Passphrase используется только для derivation Argon2id ключа, после чего обнуляется. **Zeroize responsibility**: `Argon2idPassphraseKdf` adapter обнуляет input `CharArray` (passphrase) немедленно после derivation; derived 32-byte key (`ByteArray`) MUST zeroize'аться после завершения wrap/unwrap operation (caller — `RootKeyManagerImpl`). UI Compose state НЕ хранит passphrase после передачи в KDF.
- **FR-013a**: UI passphrase setup MUST содержать **опциональную** кнопку «Скопировать в буфер обмена». Buffer обмена обнуляется автоматически: (a) Android 13+ — system auto-clear через 60 секунд; (b) при navigation away — явный `ClipboardManager.clearPrimaryClip()` вызов. Buffer обмена считается short-lived storage, accepted tradeoff для admin'ов, использующих внешние менеджеры паролей (Bitwarden desktop, KeePass и т.д., не Android Autofill). **Passphrase НЕ показывается на экране как plaintext** — только копируется в буфер.
- **FR-014**: System MUST поддерживать минимум 8 символов passphrase. Максимум — без ограничения (Argon2id принимает любую длину). **TODO(future-spec hardening 2026-06-19)**: повысить минимум до 12 символов после оценки UX impact на elderly users; либо ввести passphrase strength meter для предупреждения о слабых паролях.

**ConfigCipher — первый потребитель**

- **FR-015**: F-5 MUST регистрировать первый DEK в `KeyRegistry` с именем `"config-cipher-aead-v1"` при первом `RootKeyManager.getOrCreate(identity)`.
- **FR-016**: `ConfigCipher` port (живёт в `core/keys/api/` рядом с другими) MUST экспонировать:
  - `seal(configBytes: ByteArray, uid: String): Outcome<SealedConfig, CryptoError>` — зашифровать сериализованный config (owned by spec 008 как `ConfigDocument`) под `config-cipher-aead-v1` DEK с AAD binding к UID.
  - `open(sealed: SealedConfig, uid: String): Outcome<ByteArray, CryptoError>` — расшифровать; валидирует AAD против переданного UID (cross-identity replay defence per FR-020).
- **FR-017**: `SealedConfig` wire-format MUST содержать:
  - `schemaVersion: Int` (стартует с 1).
  - `algorithm: String` (e.g. `"xchacha20poly1305-v1"`).
  - `ciphertext: ByteArray` (AEAD-encrypted ConfigDocument JSON).
  - `nonce: ByteArray`.
  - `aad: ByteArray` (associated data — минимум `uid` + `schemaVersion`).
  - `recipientMasterSignature: ByteArray? = null` — **зарезервированное поле** под future cross-signing (per clarify Q «master key задел в schema»). MVP всегда null.
- **FR-018**: ConfigDocument на стороне сервера лежит **только в зашифрованном виде**. Локальный app cache (per three-tier cache model) хранит plaintext — это accepted (per clarify Q «local encryption» = A: `allowBackup=false` + plaintext local).
- **FR-019**: Один CEK на жизнь identity (не ротируется при каждом push'е). Per clarify Q A1.

**Identity binding и AAD**

- **FR-020**: AAD MUST включать `uid` (Google UID из F-4) — защищает от cross-identity replay (атакующий не может взять валидный ciphertext одной identity и применить к другой).
- **FR-021**: KDF info-string для derivation `ConfigCipher DEK` MUST содержать `uid` — domain separation между identities (даже при компрометации passphrase одного user'а его DEK не открывает чужой config).

**Cross-app forward-compat**

- **FR-022**: `RecoveryVaultBlob` wire-format MUST быть **app-agnostic** — не содержать `packageName` или другой APK-specific метаинформации. Future apps экосистемы (messenger V-2, album V-3) могут читать тот же blob через broker pattern или независимый Firestore access.
- **FR-023**: `KeyRegistry` storage MUST использовать **stable DEK names** в namespace, который не зависит от package'а (например, `config-cipher-aead-v1`, `pair-x25519-v1` — глобальные имена в семейной экосистеме, не `launcher.config-cipher`).
- **FR-024**: System MUST НЕ реализовывать broker pattern / AIDL infrastructure в F-5. Только обеспечивать, что format будущей реализации возможен. **inline TODO** в `RecoveryKeyVault` declaration: `// TODO(future-spec V-2/V-3/P-10): cross-app root key sharing via broker pattern (preferred per owner decision 2026-06-19) — see docs/product/future/multi-app-cohabitation.md`.
- **FR-024a**: **Owner decision 2026-06-19** (зафиксирован в [`docs/product/future/multi-app-cohabitation.md`](../../docs/product/future/multi-app-cohabitation.md#owner-decision-2026-06-19)): экосистема family apps (launcher / messenger / album / future) подписывается **одним signing key** в Google Play. Это target architecture для cross-app sharing через broker pattern (Path A). F-5 формат `RecoveryVaultBlob` и `KeyRegistry` MUST совместим с этим решением, но **не зависит** от него (Path B — independent cloud access — остаётся доступен как fallback).

**Failure handling**

- **FR-025**: All `RootKeyManager` / `KeyRegistry` / `ConfigCipher` failure modes MUST быть представлены доменными sealed types: `RootKeyError`, `KeyRegistryError`, `VaultError`, `CryptoError`. Никакие низкоуровневые libsodium-ошибки наружу не пробрасываются.
- **FR-026**: При AEAD authentication failure (ciphertext tampered) MUST вернуться `CryptoError.AeadAuthFailed`, **не** crash.
- **FR-027** (resolved 2026-06-19 — H-1 mitigation): Recovery passphrase MUST быть rate-limited через **persistent counter в Android DataStore** с UID-namespace (`recovery-attempts-${uid}`), structure: `{ attemptCount: Int, firstAttemptAt: Long, lastAttemptAt: Long }`.
  - **Policy**: 3 неудачных попытки на 1-час sliding window. При `(now - lastAttemptAt) > 1 hour` counter автоматически сбрасывается до 0 (auto-reset).
  - **Wrong passphrase** → `RecoveryError.WrongPassphrase` (отличимая от `RecoveryError.MalformedVault` для UX) + counter++.
  - **Counter >= 3** → `RecoveryError.TooManyAttempts` + UI показывает «слишком много попыток, попробуйте через 1 час, или настройте заново» с кнопками «Подождать» / «Настроить заново».
  - **Threat model — accepted risks (документировано)**:
    - Counter обнуляется через `Settings → Apps → Clear App Data`, **но** при этом обнуляется и весь app state (включая локальный cache root key) — атакующему это не даёт прогресса, он начинает с нуля.
    - Counter не выживает factory reset — accepted, factory reset стирает Google account, без которого Sign-In невозможен.
    - Root-доступ к устройству позволяет прочитать DataStore напрямую — out of scope для семейного app threat model.
  - **Defence depth context**: counter — это уровень 2 в consumer-app industry norm (consistent с WhatsApp, Signal, banking apps). Уровень 3 (Android Keystore monotonic counter) — не делаем сейчас (overhead для elderly-focused app), но wire-format совместим.
  - **Future-ready**: server-side atomic counter increment через свой сервер (per [SRV-RECOVERY-001](../../docs/dev/server-roadmap.md)) полностью закроет H-1 — defeat'ит все trivial bypass-векторы (Clear App Data, factory reset, root).
  - **Связано с FR-014**: минимум 8 символов passphrase эффективен в этой counter-policy (8 случайных символов = ~5×10^13 комбинаций, при 3/hour rate limit = ~2 миллиарда лет brute-force). При weaker passphrase (например, «1234») counter — единственная защита, и она ограничена.

**Huawei / non-GMS handling**

- **FR-028**: На non-GMS устройствах `IdentityProof.requestSignIn()` MUST вернуть `IdentityError.NoSupportedProvider` (как F-4 AuthAdapterSelector). Recovery flow недоступен. App работает в local mode forever.

**Schema-version downgrade protection** (resolved 2026-06-19 — H-2 mitigation, WhatsApp E2E backup pattern)

- **FR-028a — Server-side immutable schemaVersion (Firestore Rule)**: Firestore Security Rules MUST содержать правило для `users/{uid}/recovery-key` и `users/{uid}/config`: `allow update: if request.resource.data.schemaVersion >= resource.data.schemaVersion`. Это атомарно (на уровне Firestore) запрещает запись документа с меньшим `schemaVersion`, чем уже хранится. Атакующий с украденными Google credentials НЕ может откатить пользователя на устаревший формат через прямую запись в Firestore.

- **FR-028b — Client-side Trust On Last Use (TOLU)**: System MUST хранить `lastSeenSchemaVersion[recoveryVault | configBlob]` в DataStore per identity (`tolu-${uid}-${blobKind}`). При каждом успешном чтении vault'а / config'а — обновлять stored значение до `max(stored, fetched.schemaVersion)`. При следующем чтении: если `fetched.schemaVersion < stored` → отвергнуть как tampered, вернуть `VaultError.SchemaDowngradeDetected` / `CryptoError.SchemaDowngradeDetected`. UI показывает «обнаружено tampering данных в облаке, требуется переустановка / обращение в support».

- **FR-028c — Migration policy (defer to SRV-CRYPTO-008)**: При выпуске v2 в будущем — отдельная спека ([SRV-CRYPTO-008](../../docs/dev/server-roadmap.md)) описывает: (a) auto-migration v1 → v2 при каждом recovery; (b) deprecation v1 через 12 месяцев после релиза v2; (c) forced-update min app version. F-5 заложил wire-format + защиту, migration policy — не F-5 territory.

- **FR-028d — Defence-in-depth rationale**: трёхслойная защита (Firestore Rule + Client TOLU + Migration) — это **industry standard pattern** (WhatsApp E2E backups, TLS_FALLBACK_SCSV, iMessage CKV phased rollout). Один слой обходится — другой ловит. Closing H-2 attack vector «compromise Google credentials → read plaintext via downgrade».

- **FR-028e — Accepted residual risk**: TOLU counter обнуляется через Clear App Data (как в FR-027 H-1) — атакующий обнуляет client memory, но при этом обнуляется весь local state, атакующий начинает с нуля без прогресса. Атакующий с root-доступом + write access к Firestore + знанием v1 weakness — может обойти TOLU, но Firestore Rule остаётся последним рубежом. Полное закрытие — при переезде на свой сервер (SRV-RECOVERY-001 + atomic version validation).

**Limits**

- **FR-029**: ConfigDocument plaintext MUST НЕ превышать 256 KB. Превышение → `ConfigCipher.seal` возвращает `CryptoError.ConfigTooLarge`.
- **FR-030**: System MUST использовать **Argon2id** для passphrase derivation с **interactive** parameters (e.g., 64 MB memory, 3 iterations, 1 parallelism) — баланс между security и UX latency на mid-tier Android.

**Identity isolation**

- **FR-031**: `KeyRegistry` storage MUST быть partitioned by `AuthIdentity.stableId` (UID). Каждый UID имеет **независимый** набор ключей (root + DEKs) в Android Keystore namespace. При sign-in под другим UID на том же устройстве — старые ключи **сохраняются изолированно**, не wipe'ятся. При возврате на исходный UID — старые ключи и config снова доступны без recovery flow.
- **FR-031a**: Никаких cross-UID операций (например, «скопировать config из UID1 в UID2») в F-5. Это — territory P-5 (Config Copy Between Own Devices) или future spec. F-5 обеспечивает **strict isolation**.

**Algorithm migration (future-readiness)**

- **FR-032**: Wire-format `algorithm: String` field MUST позволять **сосуществование версий** на устройстве — клиент, читающий `SealedConfig` или `RecoveryVaultBlob`, MUST уметь определить algorithm из поля и применить правильный adapter.
- **FR-033**: System MUST вести **inline TODO** в коде `core/keys/`: `// TODO(future-spec algorithm-migration): когда XChaCha20-Poly1305 / Argon2id устаревают — отдельная спека описывает: (a) re-derive root key под новый KDF; (b) re-wrap всех DEKs под новый AEAD; (c) re-encrypt all SealedConfig in cloud через migration job; (d) backward-compat read для пользователей, которые ещё не мигрировали`. Реализация миграции — **out of scope F-5**, но точка вызова и schemaVersion + algorithm field обеспечивают возможность.
- **FR-033a**: Server-side migration job для перешифровки existing SealedConfig'ов под новый algorithm — отложено в [`server-roadmap.md SRV-CRYPTO-008`](../../docs/dev/server-roadmap.md#srv-crypto-008).

### Key Entities

- **RootKey**: random 256-bit key, живёт в Android Keystore wrapped с `setUserAuthenticationRequired(false)` для MVP (биометрический unlock — future enhancement). Защищает все DEKs.
- **KeyRegistry**: domain entity, name → wrapped DEK map. Persistent storage = Android Keystore (через `SecureKeyStore` из F-CRYPTO).
- **RecoveryVaultBlob**: wire-format envelope (FR-010). То, что лежит в Firestore `users/{uid}/recovery-key`.
- **SealedConfig**: wire-format для ConfigDocument в Firestore (FR-017).
- **AuthIdentity**: re-used из F-4 (spec 017). F-5 не создаёт свой identity тип.
- **ConfigCipher**: domain port. Первый потребитель `KeyRegistry`.
- **`IdentityProof` / `RecoveryKeyVault` / `KeyRegistry` / `RootKeyManager`** — domain ports.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 0 plaintext-имён / телефонов / labels в Firestore при включённом F-5 — автотест grep'ит по fixture-именам в скачанных blob'ах.
- **SC-002**: Roundtrip seal→open для типичного config (≤ 10 KB plaintext) занимает **< 50 ms** на эмуляторе Pixel 5 API 34 (verified 2026-06-19: 55ms на Xiaomi 11T). Argon2id-derivation passphrase (interactive params 64MB/3/1) — **< 1500 ms** на realistic 5-летних devices (verified 2026-06-19: 776ms Xiaomi 11T, 726ms emulator API 35; ранее заявленные 500ms оказались недостижимы из-за libsodium JNI overhead). Это редкая операция (один раз при setup passphrase + один раз при recovery на новом устройстве / Clear App Data), не блокирует daily UX потому что root key хранится в Android Keystore и passphrase не запрашивается при ежедневном использовании.
- **SC-003**: Recovery flow end-to-end (Sign-In → fetch vault → unwrap passphrase → restore root → get DEK → decrypt config) — **< 3 seconds** при cold start, при условии что network round-trip к Firestore < 500ms.
- **SC-004**: 100% случаев: при правильном passphrase recovery успешен; при неправильном — ошибка `WrongPassphrase`, не crash, не silent fail.
- **SC-005**: Sign-out → Sign-in на том же устройстве: **0 recovery flow triggered** (root key уже в Keystore).
- **SC-006**: Sign-in под другим Google account: 100% случаев новый KeyRegistry для новой identity; **0 cross-contamination** между identities.
- **SC-007**: На non-GMS (Huawei) устройстве: 100% случаев app **запускается** и работает в local mode; cloud features показывают «недоступно на этом устройстве» (consistency с F-4).
- **SC-008**: `KeyRegistry` поддерживает 100 sequential `getDek` operations за **< 200 ms** на эмуляторе `pixel_5_api_34` (concrete measurable target, заменяет ранее не-measurable «без degradation»).

## Assumptions

- **F-4 (spec 017) merged**: `AuthIdentity.stableId` стабильно доступен. `AuthProvider` — wrapped через `GoogleSignInIdentityProof` адаптер.
- **F-CRYPTO (spec 016) merged**: `AeadCipher`, `AsymmetricCrypto`, `KeyDerivation`, `SecureKeyStore`, `CryptoError` доступны в `com.launcher.api.crypto`.
- **`core/keys/` — новый module**, ~10 файлов. Создаётся в рамках F-5.
- Production ещё не запущен → нет существующих encrypted users, миграция не нужна.
- Android Autofill API стабильно работает на API 26+. Google Password Manager включён по умолчанию на Android 9+.
- Argon2id implementation доступна через libsodium (verified в F-CRYPTO research).
- Threat model: защита от **utечки plaintext через Google/Firebase/server backups**. **НЕ** защита от forensic acquisition разблокированного устройства, root-доступа, malware с system-level privileges.
- **Identity enumeration leak (H-6, accepted)**: атакующий с Google account UID-A может проверить существование UID-B в системе через `fetchVault(UID-B)` — Firestore Rules вернут `permission-denied`, не `not-found`, что косвенно подтверждает наличие записи. **Accepted threat** для family-scale: атакующий уже должен знать конкретный Google UID жертвы. Server-side mitigation (uniform timing на permission-denied vs not-found) отложена в SRV-RECOVERY-001.
- **Multi-admin sharing** — out of scope F-5, see S-2 enhancement notes 2026-06-19.
- **Cross-app sharing (V-2 messenger, V-3 album)** — out of scope F-5, форматы спроектированы как forward-compat.
- **Смена passphrase** — out of scope MVP, inline TODO.
- **Биометрический unlock root key** — out of scope MVP. Возможен через `setUserAuthenticationRequired(true)` flag — future enhancement.

## Local Test Path *(mandatory)*

- **Emulator / device**: преимущественно JVM unit tests в `core/keys/`; integration через **Firestore Emulator** + два fake-клиентов (старый + новый device) в одной JVM; smoke на `pixel_5_api_34` через skill `android-emulator` для проверки Autofill UX.
- **Fake adapters used**:
  - `FakeIdentityProof` — возвращает deterministic AuthIdentity для тестов.
  - `FakeRecoveryKeyVault` — in-memory вместо Firestore.
  - `FakeSecureKeyStore` — in-memory вместо Android Keystore (уже есть в F-CRYPTO).
  - `FakeAeadCipher`, `FakeKeyDerivation` — уже есть в F-CRYPTO.
- **Fixtures**:
  - `core/keys/src/commonTest/resources/fixtures/recovery-vault-v1.json` — фиксированный vault blob для backward-compat тестов.
  - `core/keys/src/commonTest/resources/fixtures/sealed-config-v1.json` — фиксированный sealed config.
- **Verification commands**:
  - `./gradlew :core:keys:jvmTest --tests *RootKeyManagerTest`
  - `./gradlew :core:keys:jvmTest --tests *KeyRegistryTest`
  - `./gradlew :core:keys:jvmTest --tests *ConfigCipherRoundtripTest`
  - `./gradlew :core:keys:jvmTest --tests *RecoveryFlowE2ETest`
  - `./gradlew :app:assembleDebug` + skill `android-emulator` smoke (passphrase UX с реальным Google Password Manager).
- **Cannot-test-locally gaps**:
  - Реальная производительность Argon2id на low-end OEM (Samsung A-series, бюджетные Xiaomi) — `TODO(physical-device)`.
  - Реальный Firestore (не Emulator) — security rules + сетевой latency — `TODO(physical-device)` smoke перед production-release.
  - Реальный Google Password Manager Autofill UX (Emulator может вести себя иначе) — `TODO(physical-device)`.

## AI Affordance *(mandatory)*

- **Exposable capabilities**: будущий AI-агент работает с **plaintext ConfigDocument в RAM после `ConfigCipher.open`** на клиенте. AI **никогда** не имеет доступа к серверным blob'ам (они opaque) и **никогда** не получает root key / passphrase.
- **Required affordances on data**: domain verbs (`updateContactName`, `rearrangeTiles`, `applyTheme`) после расшифровки клиентом.
- **Provider-agnostic shape**: F-5 не вводит provider-specific типы. `IdentityProof`, `RecoveryKeyVault`, `KeyRegistry`, `ConfigCipher` — все domain ports.
- **Out of scope**: AI provider implementation, AI-side recovery flow (никакого «AI помогает восстановить ключи»), server-side AI processing над configs (**запрещено by design** — server видит только ciphertext).
- **Inline TODO**: `// TODO(capability-registry): ConfigCipher.open exposes ConfigDocument to potential AI affordance layer — AI must run client-side only`.

## OEM Matrix

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline | — | emulator `pixel_5_api_34` |
| Samsung One UI | Аппаратный Keystore key attestation поведение отличается; Autofill UX немного другой (Samsung Pass + Google Password Manager сосуществуют) | `core/keys/` не делает Keystore wrap напрямую — это в F-CRYPTO `SecureKeyStore` adapter (inherited from spec 016 OEM matrix); Autofill — стандартные hints, оба провайдера их понимают | `TODO(physical-device)` smoke на Samsung Galaxy A |
| Xiaomi MIUI | MIUI optimizations иногда задерживают first JNI load (libsodium init 200-500ms на cold start) | Lazy-init `RootKeyManager` при первом use; не блокируем app startup | `TODO(physical-device)` smoke на Redmi Note |
| Huawei EMUI | EMUI без GMS — Google Sign-In недоступен → `IdentityProof.requestSignIn` возвращает `NoSupportedProvider` (consistency с F-4) | Conditional adapter wiring в DI: на non-GMS → `NoOpIdentityProof`, `NoOpRecoveryKeyVault`, app в local mode forever | `TODO(physical-device)` Huawei P-series при появлении flavor |

> **F-5 — это backend-substitution sensitive feature**: при переезде на собственный сервер (per [SRV-RECOVERY-001](../../docs/dev/server-roadmap.md#srv-recovery-001)), `RecoveryKeyVault` adapter меняется на `OwnServerRecoveryKeyVault`, F-5 domain layer не трогается. CLAUDE.md rule 2 (ACL) compliant.

---

## Краткое резюме (для не-разработчика)

**Что внутри**:

- Делаем **главный ключ** (root key) на устройстве. Этот ключ — корень для всего: им шифруется конфиг, потом им же будут шифроваться X25519 ключи для общения с бабушкой (S-2), потом фотографии (S-5), потом сообщения в мессенджере (V-2). **Один ключ — много данных**.
- Главный ключ хранится в **специальном аппаратном хранилище Android'а** (Android Keystore TEE). Его нельзя «вытащить» из устройства, даже если получишь root-доступ.
- **Recovery (восстановление при потере телефона)**: главный ключ зашифровывается **passphrase'ом**, который admin придумывает при первом запуске. Зашифрованная копия лежит в Firestore. При потере телефона: admin ставит app на новый, заходит в свой Google, вводит passphrase → главный ключ восстановлен → всё работает.
- **Passphrase автоматически сохраняет Google Password Manager** (или Bitwarden / 1Password — что у admin'а стоит). Admin даже **не видит** passphrase глазами — Android'овская кнопка «Suggest strong password» сгенерирует его сам и сохранит в менеджер.
- ConfigDocument (раскладки плиток, имена контактов, темы) на сервере хранится **только в зашифрованном виде**. Google / Firebase / forensics не видят имён и телефонов.
- Сервер при этом «**тупой**» — он просто хранит зашифрованные blob'ы. Никакой логики на сервере. Это просто.
- **На стороне бабушки ничего не меняется** — она работает из локального кэша. Облако только обновляет конфиг, когда admin что-то поменял.

**Что эта фича разблокирует**:

- Cloud-фичи могут безопасно ship'иться в production (privacy regression закрыта).
- S-2 (multi-admin), S-5 (фото), S-4 (SOS), V-2 (мессенджер), V-3 (альбом) — все используют **тот же** root key + добавляют свои DEKs.
- При потере телефона admin'а: восстановил passphrase → **все** последующие фичи работают сразу, без отдельных recovery flow'ов для каждой.

**Что эта фича сознательно НЕ делает**:

- НЕ строит multi-admin sharing (несколько admin'ов читают тот же конфиг бабушки) — это **S-2**.
- НЕ строит pairing с другими устройствами семьи — это **S-2 / spec 007**.
- НЕ строит cross-app sharing с messenger / album — это **V-2 / V-3 решат**, но формат root key готов к этому.
- НЕ позволяет менять passphrase после setup — отложено.
- НЕ требует биометрию для unlock — отложено (будущее enhancement).
- НЕ работает на устройствах без Google services (Huawei) — там app работает в local mode forever (как F-4 решил).
