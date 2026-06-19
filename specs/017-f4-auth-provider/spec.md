# Feature Specification: F-4 — `AuthProvider` port + Google Sign-In adapter

**Feature Branch**: `017-f4-auth-provider`
**Created**: 2026-06-18
**Status**: Draft
**Input**: User description: «F-4 — AuthProvider port + Google Sign-In adapter. Provider-agnostic port в `core/domain/`, Google как один из возможных adapter'ов, Fake adapter (mock-first), SessionStore port + encrypted local storage, identity model. Активируется в момент первого cloud action в Phase 2 (S-5/S-8/S-4/S-2), а НЕ на первом запуске app. Local-mode работает без Sign-In бесплатно бессрочно (decision 2026-06-15-deferred-cloud/01). Anonymous Firebase Auth удалён полностью (decision 2026-05-30 D-Pair-1). MVP: единственный реальный adapter — Google. Phone / Email / Apple / SSO — additive в будущем без переписки port'а или consumer'ов.»

## Контекст и цель спека

Сейчас в проекте **нет** identity-слоя. Решения 2026-05-30 (`docs/product/decisions/2026-05-30-f4-identity/`) и 2026-06-15 (`docs/product/decisions/2026-06-15-deferred-cloud/`) изменили модель: каждый пользователь (admin и senior) — registered user с named identity, anonymous Firebase Auth удалён, Sign-In откладывается до первого cloud action.

**Что строим**: `core/domain/auth/` — provider-agnostic `AuthProvider` port + identity model + `SessionStore` port. В `app/androidMain/auth/` — единственная MVP-реализация `GoogleSignInAuthAdapter` через Credential Manager + Firebase Auth, и encrypted-local реализация `SessionStore`. В `core/commonTest/auth/` — `FakeAuthAdapter` для unit-тестов и dev (CLAUDE.md rule 6 mock-first).

**Центральное архитектурное требование** — **provider-agnostic port**. Google — лишь **один из** возможных способов авторизации. Любой будущий провайдер (Phone, Email/Password, Apple Sign-In, SAML/SSO, own-server JWT) подключается через тот же `AuthProvider` port добавлением нового adapter'а **без изменения сигнатур port'а и без переписывания consumer'ов** (F-5 ConfigCipher, S-2 pairing, S-4 SOS, S-5 photos, S-6 deletion, billing). Это применение CLAUDE.md rule 2 (ACL) к auth-провайдерам: если Firebase Auth завтра «исчезнет» / станет платным / поменяет API — переписываем **один** adapter, не весь identity-слой.

**Activation timing** (important for UX and tests). F-4 **не** запускается на первом запуске app. Активируется **только** в двух местах:

1. **В wizard (F-3) на screen 2 «Настройка приложения»** — двухкнопочный экран: «Настроить с нуля» (продолжает wizard локально без F-4) / «Войти в Google для восстановления настроек» (вызывает F-4 sign-in).
2. **Через отдельный composable `SignInTrigger`** — самостоятельная UI-единица, которая может быть встроена куда угодно (сейчас — дёргается из wizard'а; в будущем может быть встроена в Settings spec или любой другой UI). Не привязана к конкретному месту вызова.

**Никаких других триггеров sign-in не существует.** Launcher сам по себе не знает о «cloud features» и не имеет встроенных кнопок типа «получить фото от внуков». Он рендерит плитки из конфига; конфиг либо локальный (бабушка/admin настроил сам), либо синхронизируется с сервером (через отдельный config-sync сервис) после sign-in. Иконки/фото в плитках — это **данные конфига**, не отдельные cloud-features с собственным sign-in триггером.

F-4 после успешного sign-in **только**:
- Сохраняет `SessionRecord` в `SessionStore`.
- Эмитит `AuthIdentity` через `currentUser: Flow<AuthIdentity?>`.

F-4 **не** инициирует config sync, push registration, conflict resolution, восстановление конфига с сервера. Эти системы (config-sync layer, push registration service, S-8 VersionedConfigViewer) подписываются на `currentUser` flow **независимо** и работают по своим правилам (например, config-sync применяет правило «сервер всегда приоритетнее, локальный кеш мержит, push обратно на сервер» — это территория S-8, не F-4).

До sign-in (и в local-mode forever) app работает **полностью** без `AuthProvider.signIn(...)`: wizard, launcher, плитки, контакты, темы, локализованный конфиг хранятся локально (см. `docs/product/decisions/2026-06-15-deferred-cloud/01-deferred-sign-in.md`).

**Что НЕ строим**: Phone Auth adapter, Email/Password adapter, Apple Sign-In adapter, SAML/SSO adapter, own-server JWT issuer, real subscription billing flow, account deletion UI flow, ChainOfTrust / multi-app cohabitation (P-10), 2FA admin device migration (отдельная спека post-F-5). Port спроектирован под них, adapter'ы добавляются позже отдельными спеками **additive**, без переписки существующего кода.

**Dependencies (внешние)**:
- Firebase Auth project configured (admin task, не код, pre-release).
- F-3 (wizard) merged — local-mode flow работает без F-4 (это **обязательное** свойство, не nice-to-have).
- F-CRYPTO (spec 016) merged — encryption keys будут привязаны к stable identity из `AuthProvider` позже в F-5 (но F-4 **не зависит** от F-CRYPTO во время выполнения; F-CRYPTO **не зависит** от F-4 для генерации ключей — см. spec 016 §Assumptions «F-4 NOT required»).

## Clarifications

### 2026-06-18 — Pre-clarify scope confirmation

Документ написан как результат serии решений 2026-05-30 (9 файлов) и 2026-06-15 (deferred-cloud). Все ключевые архитектурные вопросы (anonymous removal, port provider-agnostic, deferred Sign-In, email REQUIRED de-facto / optional на уровне типа, billing cloud-only) **уже зафиксированы** в decision-документах. `/speckit.clarify` пройдёт по этому документу для финальной проверки grey-zone (см. список открытых вопросов в конце для возможного pre-emptive обсуждения):

| # | Вопрос | Текущая дефолтная позиция |
|---|--------|---------------------------|
| 1 | `email` в `AuthIdentity` — optional на типе или required? | **Optional на уровне типа** (потому что phone-only провайдер email не вернёт), **но в MVP де-факто заполнен** (Google всегда возвращает email; refuse Sign-In если Google не вернул email — это policy `GoogleSignInAuthAdapter`'а, не port'а). |
| 2 | `providerKind` — enum или sealed? | **Open enum** (`enum class ProviderKind { GOOGLE, /* PHONE, EMAIL_PASSWORD, APPLE, SSO — добавляются позже */ }`). Sealed был бы строже, но Kotlin sealed enum даёт `exhaustive when` без явных branch'ей для будущих providers — это нам не нужно. Open enum + сompile-warning «non-exhaustive when» — приемлемо для consumer'ов: они обрабатывают **identity**, не providerKind. |
| 3 | Email refuse — port или adapter responsibility? | **Adapter** (Google policy). Port возвращает `Outcome<AuthIdentity, AuthError>`; `GoogleSignInAuthAdapter` решает, что без email это `AuthError.NoEmail`. Будущий phone adapter не будет иметь такой policy. |
| 4 | `SessionStore` — общий port или per-provider? | **Общий** port. Хранит abstract `SessionRecord { stableId, providerKind, expiresAt?, refreshToken?, extra: Map<String, String> }`. Adapter кладёт provider-specific blob в `extra` (Firebase JWT в `extra["firebase_jwt"]`), domain не видит. |
| 5 | DI seam — build flavor или runtime config? | **Build flavor + runtime selector**. По умолчанию: `debug` / `test` source set — `FakeAuthAdapter`; `release` — `GoogleSignInAuthAdapter`. `AuthAdapterSelector` в `app/androidMain/` может при необходимости подменить (например, future non-GMS adapter) — поэтому остаётся indirection. |
| 6 | `subscription_state` в `User` — где живёт? | **Stub field** в `User` (per scope этой спеки), реальный billing — post-MVP (см. decision 2026-06-15-deferred-cloud/03). MVP-реализация: `subscription_state: SubscriptionState.UNKNOWN`. Никакого client-side computation — это будет server-validated JWT (per decision 03 §«Уровни усиления», L0 server-only). |
| 7 | Anonymous Firebase Auth wipe — migration tool или ручная очистка? | **Ручная очистка pre-release** (per decision 2026-05-30 §08 «Migration: Wipe pre-F-4 anonymous pair'ов и configs, реальных пользователей нет»). Не строим migration tool. |
| 8 | Senior-safe Sign-In UI? | **Google Sign-In UI не кастомизируется** (per decision 2026-05-30 §08); настройка проходит в Standard mode на устройстве admin'а или компетентного взрослого. Senior на своём устройстве проходит Sign-In один раз через тот же UX (помощь admin'а при необходимости). |

Если `/speckit.clarify` найдёт дополнительные grey zones — будут зафиксированы здесь отдельной подсекцией.

### 2026-06-18 — Pre-plan clarification pass (mentor mode, 9 questions)

Решения по результатам mentor-сессии 2026-06-18. Каждый ответ — owner-driven выбор после critical challenge на pre-formed defaults. Несколько изначальных рекомендаций владельца **отвергнуты как ошибочные** (отмечено явно).

| # | Question | Resolution |
|---|----------|------------|
| 1 | `AuthIdentity.stableId` — что это технически? Firebase UID? Google `sub` claim? Что-то ещё? | **Наш собственный UUID**, генерируемый при первом sign-in. Хранится в Firestore-таблице `/identity-links/{providerKind}/{providerAccountId} → stableId` (мапа провайдер-specific identifier → наш UUID). Сейчас таблица в Firestore, после own-server cutover переезжает на наш сервер **без миграции stableId** (потому что UUID наш, не Google'овский). **Опровергает мой recommended (Google `sub`)**: владелец указал, что `sub` привязывает нас к Google навсегда — не работает в странах где Google запрещён, не работает для будущего PhoneAuthAdapter (у phone-аккаунта нет `sub`), нарушает мета-правило «всё перегоняется на собственный сервер». |
| 2 | `SessionRecord.extra` visibility для consumer'ов? | **Consumer'ы не видят `SessionRecord` вообще.** `AuthProvider.currentUser: Flow<AuthIdentity?>` — единственный публичный путь к identity для consumer'ов. F-5 берёт `stableId` напрямую из `AuthIdentity`. Для подписи RPC-запросов в будущем (S-8 sync) — отдельный port `AuthorizedRequestSigner` (inline TODO). `SessionRecord` — internal contract между `AuthProvider` ↔ `SessionStore`, наружу не торчит. |
| 3 | Explicit `signOut()` — что происходит с локальным конфигом и кешами? | **Sign-out останавливает синхронизацию с сервером, локальный кеш остаётся как есть.** Это **рабочее состояние** приложения, не «вернёмся в pre-sign-in». Изменения после sign-out живут локально до следующего sign-in. При повторном sign-in — стандартный конфиг-flow: сервер всегда приоритетнее, локальный мержится в серверный, push обратно. **Опровергает мою постановку «выбор из трёх вариантов»**: владелец указал, что других реальных вариантов не существует; «full wipe» — это **delete account**, не sign-out (отдельная фича S-6). Confirmation dialog не нужен для sign-out, нужен **только** для delete account. |
| 4 | `ProviderKind` shape — sealed interface, enum, value class, или вообще выкинуть? | **Выкинуть `providerKind` из `AuthIdentity` полностью.** Каждый adapter знает у себя внутри, через что он работает (Google adapter знает что он Google). В domain — не нужно. Consumer'ы (F-5, S-2) `providerKind` не используют ни для чего. Логирование делает сам adapter. **Опровергает мой recommended (sealed interface)**: правильное решение — не выбирать форму поля, а **удалить поле**. |
| 5 | Wizard sign-in branch UX — explainer перед Google sign-in screen или нет? | **(a) Сразу Google sign-in screen без промежуточного explainer'а.** Кнопка «Войти в Google для восстановления настроек» уже сама объясняет что произойдёт. Дублирующий экран = лишний шаг для бабушки в стрессе настройки. **Опровергает мою изначальную модель «CloudFeatureGate composable для пяти cloud-features»**: cloud-feature кнопок не существует, есть **один** wizard sign-in entry point. |
| 6 | UX отмены sign-in (пользователь нажал Cancel в Google screen) — что показывается после? | **(a) Возврат на тот же wizard-экран** с двумя кнопками «Настроить с нуля» / «Войти в Google». Никаких toast'ов «вход отменён». Пользователь может снова нажать «Войти» или передумать и выбрать «Настроить с нуля». |
| 7 | После успешного sign-in F-4 должен ли сам инспектировать конфиги на сервере, разрешать конфликты, пушить локальное на сервер? | **Нет.** F-4 после sign-in **только** (a) сохраняет identity в `SessionStore`, (b) эмитит event через `currentUser` flow. **Не инициирует** config sync, push, conflict resolution. Отдельный config-sync сервис (S-8 territory) **подписан** на `currentUser` flow и реагирует независимо. F-4 даже **не знает** о существовании config-sync. Сервер всегда приоритетнее локального — это **policy config-sync'а**, не F-4. Merge conflict UI — S-8 VersionedConfigViewer. **Опровергает мой изначальный «конфиг пушится сразу после sign-in»**: владелец указал, что после sign-in сначала **забираем** с сервера (сервер priority), мержим в локальный, потом пушим. F-4 этого не делает и не должен знать. |
| 8 | Wizard recovery flow (конфиг устарел или не существует на сервере) — где живёт логика? | **F-4 этого не знает.** Это пересечение F-3 (wizard) и config-sync (S-8). Wizard слушает event «authenticated» от F-4 `currentUser` flow. Config-sync подтягивает с сервера, прогоняет через migrators (преобразователи schema versions). Wizard рекalibriрует свои шаги: что покрыто конфигом — пропускает, что не распозналось при migration — оставляет для донастройки, опциональные шаги помечает «можно пропустить». **Настройки Android (ROLE_HOME, runtime permissions) не сериализуются в наш конфиг и не пушатся на сервер** — wizard проверяет их runtime, не из конфига. Но **состояние Android-настроек должно уходить на сервер как health-report** для admin'а, чтобы он видел «бабушкин ROLE_HOME случайно сбит, приложение работает не так» — это **территория S-9 Phone Health Monitoring** (см. `project_mvp_phase_split.md`). F-4 в этом не участвует напрямую; только идентифицирует устройство, чтобы admin device мог сопоставить health-report с конкретным senior'ом. |
| 9 | Settings sign-in trigger — где живёт компонент? F-4 spec, Settings spec, wizard? | **F-4 spec владеет отдельным composable `SignInTrigger`** — самостоятельная UI-единица, **не встроенная** ни в wizard, ни в Settings, ни в какое-то конкретное место. Дёргается откуда угодно. Сейчас её дёргает wizard на screen 2. В будущем (когда появится Settings spec) дёргает из pref-item. Reusable UI unit с одной ответственностью: «дай UI для входа/выхода из Google account». Compromise между «F-4 рисует Settings UI» (rule 4 MVA нарушение) и «каждый consumer рисует свой» (divergence risk). |

**Что эти решения изменили в спеке** (weave summary):

- **Activation timing** переписан: F-4 запускается в двух местах (wizard screen 2 + standalone `SignInTrigger`), а не «при первом cloud action». Cloud-feature кнопок не существует.
- **User Story 2** заменён: вместо «senior нажимает на cloud feature → first-cloud-action prompt» — «пользователь в wizard выбирает Войти в Google для восстановления настроек».
- **AuthIdentity** упрощён: `providerKind` удалён, `email`/`displayName` остаются nullable.
- **stableId invariant**: наш UUID + `/identity-links/...` мапа. Inline TODO для country-ban exit ramp.
- **`SessionRecord`** переезжает в internal contract: consumer'ы видят только `AuthIdentity` через `currentUser` flow.
- **FR-006 `AuthProvider.signIn()`** обогащён ограничением: после success **только** save+emit, ничего больше.
- **FR-013 `SessionRecord`**: extra-blob hidden от consumer'ов; в спеке зафиксирован inline TODO для будущего `AuthorizedRequestSigner` port'а.
- **Edge cases** дополнены: конфиг устарел, merge conflict, Android settings drift → health-report.
- **Cross-references**: S-8 VersionedConfigViewer (merge), S-6 (delete account vs sign-out), S-9 Phone Health Monitoring (device health observability).

## Сценарии использования

> Эти сценарии — концентрированный взгляд «как это будет работать в реальной жизни» на **уровне поведения пользователя и взаимодействия частей приложения по смыслу**, без программного кода и без названий классов / методов / SDK. Читая их, можно проверить, движется ли спека в правильном направлении, без необходимости погружаться в FR. Каждый сценарий помечает какие FR / SC закрывает.
>
> Это **не sequence-диаграммы и не код**. Это пронумерованные шаги обычным языком: что пользователь делает, что приложение показывает, как одна часть приложения сообщает другой по смыслу (не «вызывает функцию X», а «оповещает что произошло событие»).

### Сценарий 1 — Бабушка пользуется приложением без входа в аккаунт (forever)

**Контекст**: бабушка купила телефон, установила приложение, ни разу не входила в Google.

1. Внук-admin ставит приложение на бабушкин телефон.
2. Запускается визард настройки. ★ обязательно: выбор языка, темы, размера шрифта, разрешение на роль главного экрана.
3. Визард показывает экран с двумя кнопками: **«Настроить с нуля»** / **«Войти в аккаунт»** (под второй — пояснение мелким текстом: «Чтобы настройки сохранялись и были доступны на других устройствах»). Внук выбирает «Настроить с нуля» (первый телефон бабушки).
4. Визард продолжается локально — внук добавляет 5 контактов внуков, создаёт плитки, выбирает темы.
5. Главный экран запускается. Бабушка пользуется плитками, звонит, видит свои темы.
6. Через 3 месяца использования: никаких «истёк бесплатный период», никаких просьб «войдите в Google», никаких заблокированных функций.
7. Приложение **никогда** не пыталось связаться с Google или нашим сервером для авторизации.

**Что закрывает**: US 1; FR-030; SC-004; edge case «бабушка работает forever без sign-in».

---

### Сценарий 2 — Бабушка получает новый телефон, восстанавливает свой конфиг из Google

**Контекст**: у бабушки был телефон с настроенным приложением (внук давно настроил), привязанным к её Google аккаунту. Старый телефон утонул. Внук покупает новый, ставит приложение.

1. Внук ставит приложение на новый телефон.
2. Визард настройки запускается. Внук доходит до экрана с двумя кнопками.
3. Внук нажимает **«Войти в аккаунт»**.
4. Сразу открывается **окно Google** для входа (никаких промежуточных «вы уверены?» экранов).
5. Внук вводит бабушкин Google email и пароль (или биометрию, если бабушка раньше заходила в Google на этом устройстве).
6. Окно Google закрывается. Приложение видит «вошёл как Анна Иванова».
7. **Независимо от F-4**, **другая часть приложения** (config-sync, S-8 territory) видит появление пользователя и идёт на сервер за её сохранённым конфигом.
8. Сервер возвращает конфиг (плитки, контакты, темы) — те, что внук настраивал на старом телефоне.
9. Конфиг применяется: плитки, контакты, темы появляются на главном экране. Бабушке не нужно ничего пересоздавать.
10. Визард видит «конфиг применён» и пропускает шаги выбора темы / языка / шрифта — они уже есть из конфига. Остаются только нерешённые системные шаги (роль главного экрана).

**Trouble case 2.b — конфиг устарел**: на сервере конфиг 2-летней давности. Config-sync (S-8) прогоняет через преобразователи. Что распозналось → применяется. Что не распозналось → визард оставляет для донастройки внуком.

**Trouble case 2.c — это новый Google аккаунт**: на сервере нет конфига для этого аккаунта. Внук видит «конфига не найдено», визард продолжается «настроить с нуля», но уже от имени «Анна Иванова». Первое сохранение конфига пойдёт на сервер.

**Trouble case 2.d — внук отменил окно Google**: окно закрывается. Внук возвращается на экран с двумя кнопками. Никаких сообщений «вы отменили». Может снова нажать «Войти» или выбрать «Настроить с нуля».

**Trouble case 2.e — нет интернета**: окно Google показывает свою ошибку или приложение видит «сетевая ошибка». Возврат на экран с двумя кнопками. Можно попробовать снова или настроить локально.

**Что закрывает**: US 2; FR-006; FR-016a; FR-033; edge cases cancelled / network error / новый аккаунт.

---

### Сценарий 3 — Бабушка передумала, хочет войти в Google уже после визарда

**Контекст**: бабушка месяц пользуется локально (без Google). Внук говорит: «давай я смогу удалённо помогать тебе с настройкой плиток». Для этого нужно войти в аккаунт.

1. Внук открывает настройки приложения.
2. В настройках есть отдельный блок «Учётная запись» — переиспользуемый компонент, тот же что визард использовал на экране 2.
3. Блок показывает: «Не вошли» + кнопка **«Войти в аккаунт»** + пояснение «Чтобы настройки сохранялись и были доступны на других устройствах».
4. Внук нажимает кнопку. Открывается то же окно Google.
5. Внук входит. Окно закрывается. Блок теперь показывает: **«Вошли как Анна Иванова»** + кнопка **«Выйти»**.
6. Параллельно, независимо: config-sync видит появление пользователя, идёт на сервер. Сервер пустой для этого аккаунта (бабушка впервые входит). Config-sync ничего не загружает — текущий локальный конфиг бабушки остаётся.
7. Когда внук в следующий раз меняет конфиг — config-sync пушит конфиг на сервер. Теперь конфиг есть на сервере, и внук со своего телефона может управлять удалённо (это уже S-2 / S-8).

**Trouble case 3.b — внук позже жмёт «Выйти»**:
1. Блок «Учётная запись» показывает кнопку «Выйти». Внук нажимает.
2. Приложение перестаёт синхронизироваться с сервером. **Локальный кеш** (плитки, контакты, темы) — остаётся как есть. Бабушка продолжает пользоваться.
3. Внук не видит окон «вы уверены?» — выход простой и безопасный.
4. Если позже снова нажмёт «Войти» — стандартный путь: окно Google, потом config-sync мержит локальный конфиг с серверным (сервер приоритетнее, локальные изменения после выхода доливаются — детали merge в S-8).

**Что закрывает**: US 3 (косвенно — F-5 / S-2 используют identity); FR-033; FR-006 signOut семантика; clarification Q3.

---

### Сценарий 4 — Час спустя, токен авторизации устарел, обновляется незаметно

**Контекст**: внук вошёл в Google. Через час он редактирует плитки на бабушкином телефоне через удалённое управление (S-2 / S-8).

1. Внук сохраняет изменённый конфиг. Приложение должно отправить запрос на сервер.
2. Перед отправкой приложение проверяет: «токен авторизации ещё свежий?»
3. Токен истёк (Google JWT живёт ~1 час). Приложение **внутри себя** запрашивает у Google новый токен через refresh token (хранится зашифрованным локально).
4. Google возвращает новый токен. Приложение сохраняет.
5. Запрос на сервер уходит с новым токеном. Внук видит «сохранено».
6. Бабушка и внук ничего не заметили.

**Trouble case 4.b — обновить токен не удалось** (пользователь удалил аккаунт в Google, или поменял пароль): запрос обновления возвращает ошибку. Приложение **тихо разлогинивает** пользователя. Блок «Учётная запись» показывает «Не вошли». При следующем открытии приложения внук видит, что нужно войти снова. Локальный кеш — остаётся.

**Trouble case 4.c — нет интернета во время обновления**: приложение оставляет текущий токен (даже истёкший), помечает действие «отправить когда появится интернет», показывает индикатор «оффлайн». Бабушка пользуется плитками локально. Когда интернет вернётся — обновление токена и отправка запроса автоматически.

**Что закрывает**: US 4; FR-017; edge case «token expired offline».

---

### Сценарий 5 — Бабушка перезагрузила телефон — приложение запускается мгновенно из локального кеша

**Контекст**: внук вошёл в Google месяц назад. Бабушка пользуется приложением.

1. Бабушка выключила телефон.
2. Включает обратно. Открывает приложение.
3. Приложение **читает локальный кеш конфига с диска** (плитки, контакты, темы — это файл на устройстве).
4. **Главный экран рисуется сразу** из локального кеша. Бабушка видит работающие плитки в первые мгновения. Никаких «войдите снова», никаких ожиданий сервера.
5. **В фоне, не задерживая UI**, приложение:
   - Читает зашифрованный файл сессии → восстанавливает идентификацию пользователя.
   - Если сессия валидна — config-sync (S-8) идёт на сервер за свежим конфигом.
   - Если на сервере появились изменения (внук-admin поменял плитку с другого телефона) — config-sync применяет изменения к **уже работающему** UI: плитка тихо обновляется в **режиме использования** (бабушка не редактирует — приложение может тихо обновить под капотом).
   - Если интернета нет / сервер не отвечает — ничего не происходит. Бабушка продолжает работать с локальным кешем.
6. Бабушка ничего не заметила, кроме того что новая плитка появилась через 2 секунды (если внук что-то изменил).

**Важный архитектурный инвариант** (FR-035): UI рисуется **до** восстановления сессии и **до** обращения к серверу. Никакой блокировки на F-4 в критическом пути запуска приложения.

**Trouble case 5.b — файл сессии повредился** (редкий сбой Android при выключении, или ручная очистка кеша): приложение видит «не могу прочитать файл сессии». Никаких падений. Делает вид что пользователь не вошёл. Бабушка пользуется плитками локально. При следующей надобности cloud — внук снова входит. Файл сессии перезаписывается.

**Trouble case 5.c — приложение долго не открывали (месяц)**: при открытии локальный кеш отрисовался сразу. В фоне приложение пытается обновить токен (сценарий 4). Если рефреш получился — плитки могут тихо обновиться свежим серверным конфигом. Если нет (нет интернета, аккаунт удалён) — блок «Учётная запись» в настройках покажет «Не вошли». Локальный кеш на месте.

**Trouble case 5.d — пользователь сейчас редактирует**: если config-sync получает обновления с сервера **в момент**, когда внук в режиме редактирования конфига бабушки — config-sync **не должен** резко перерисовать UI под ним (внук потеряет правки). Политика merge в режиме редактирования — это **S-8 territory**, не F-4. F-4 только эмитит «authenticated» — S-8 сама решает когда применить.

**Что закрывает**: US 5; FR-020; FR-023; **FR-035 cold-start invariant**; edge case «session blob corrupted».

---

### Сценарий 6 — Через год Google решил, что Firebase Auth теперь платный

**Контекст**: архитектурная эволюция, не пользовательский сценарий. Через год Google объявляет: Firebase Auth для нашего проекта = $1000 / месяц. Хотим перейти на свой сервер.

1. Команда (1-2 разработчика) переписывают **один файл** — адаптер Google входа. Вместо «обмен Google ID Token у Firebase» → «обмен Google ID Token у нашего сервера».
2. Наш сервер делает то же что Firebase: проверяет подпись Google, выдаёт собственный токен.
3. Развёртывается новая версия приложения.
4. Пользователь обновляет приложение. **Ничего не замечает**. Его UUID — тот же. Плитки, контакты, темы — те же.
5. При следующем входе обмен токенами идёт через наш сервер. Всё работает.

**Trouble case 6.b — миграция таблицы identity-links** (сопоставление Google аккаунт → наш UUID): эта таблица лежит на Firestore. При переходе на собственный сервер — копируется одним скриптом. UUID не меняются. Никаких изменений в данных пользователей.

**Trouble case 6.c — Google запретили в стране**: вместо переписки одного адаптера — добавляем новый, например, вход по телефону. Он использует **ту же** таблицу identity-links (строка `phone/+7...` вместо `google/12345`). Существующие пользователи продолжают входить через Google, новые пользователи в той стране — через телефон. Их UUID работают одинаково.

**Что закрывает**: US 6; FR-005, FR-016a inline TODOs; backend-substitution exit ramp; CLAUDE.md rule 2 ACL применённый к auth.

---

### Сценарий 7 — Бабушка случайно сбила «роль главного экрана» — внук должен узнать удалённо

**Контекст**: бабушка случайно нажала «отключить» на разрешении «быть главным экраном». Теперь приложение работает не как нужно (открывается обычный Android launcher). Внук должен узнать, чтобы помочь.

1. На бабушкином телефоне (S-9 Phone Health Monitoring spec, **не** F-4) приложение проверяет состояние Android-настроек **по событиям**, не периодическим опросом батарею не сажая:
   - При cold start приложения (один раз при запуске).
   - При получении системного broadcast'а от Android о смене permissions или роли.
   - При resume приложения из фонового состояния.
2. Приложение видит «роль главного экрана **не активна**».
3. Приложение собирает health-report: «телефон бабушки, время X, статус: главный экран не активен, остальное OK».
4. Для отправки health-report нужно знать **чьё** это устройство. Приложение спрашивает F-4: «кто залогинен на этом устройстве?» F-4 отвечает: «UUID Анны».
5. Health-report с UUID Анны уходит на сервер.
6. На телефоне внука: внук видит уведомление или красную метку в админ-приложении: «у бабушки сбита настройка главного экрана».
7. Внук звонит бабушке: «нажми, пожалуйста, на главном экране на эту кнопку, чтобы вернуть приложение».

**F-4 в этом сценарии**: только пункт 4 (предоставила identity). Весь остальной flow — S-9 spec.

**Что закрывает**: cross-reference на S-9 (clarification Q8 + FR-034); edge case «Android-настройки сбиты у удалённого senior'а».

---

### Сценарий 8 — Внук-admin потерял свой телефон. Что восстановится, что нет

**Контекст**: внук-admin (Сергей) потерял свой телефон. Покупает новый. Ставит приложение. Делает Google sign-in. Это сценарий, поясняющий **границы F-4**: что одного sign-in достаточно, а где нужны другие механизмы.

У Сергея есть **четыре слоя identity-information**, каждый восстанавливается своим путём:

**Слой 1 — «Кто я» (UUID на сервере)**

1. Сергей на новом телефоне делает Google sign-in.
2. Приложение находит запись в таблице identity-links для его Google аккаунта → возвращает UUID Сергея.
3. ✅ Восстановлено через F-4 само.

**Слой 2 — «Мои настройки на сервере» (мой конфиг)**

1. После того как UUID известен, config-sync (S-8) идёт на сервер за конфигом этого UUID.
2. Сервер возвращает конфиг Сергея.
3. Локальный кеш заполняется. UI рисуется с восстановленным конфигом.
4. ✅ Восстановлено через config-sync (S-8 spec). F-4 не участвует — только дала UUID.

**Слой 3 — «Кем я управляю» (делегирования бабушек / дедушек)**

1. На сервере есть записи «UUID Сергея может управлять UUID Анны (бабушки)».
2. Когда Сергей со своего нового телефона делает запросы к бабушкиному конфигу — Firestore Security Rules видят его UUID и пропускают.
3. ✅ Восстановлено автоматически — записи делегирования на сервере, UUID совпадает.

**Слой 4 — «Ключи шифрования» (E2E encryption)**

Здесь начинается проблема.

1. F-5 спека (будущая, не F-4) будет шифровать конфиг ключом, **привязанным к устройству Сергея**.
2. Старый телефон Сергея утонул вместе с ключом. На новом телефоне ключа **нет**.
3. Google sign-in **не** возвращает ключ — потому что ключ **никогда** не был на сервере (E2E encryption invariant: только устройства пользователей могут расшифровать).
4. Без ключа — Сергей может войти, видеть «есть зашифрованный конфиг», но **не расшифровать** его.
5. ❌ **F-4 не помогает**. Нужен отдельный механизм recovery.

**Что делает recovery** (P-6 spec, будущая):

- **Social recovery + 2FA email + passphrase**. Ключ Сергея заранее (когда у него ещё был старый телефон) был разбит на части или зашифрован так, что для восстановления нужно подтверждение от **доверенного помощника** (например, его жены) + знание **парольной фразы** Сергея + подтверждение **email-кодом**.
- Сергей на новом телефоне после sign-in запускает recovery: «помоги восстановить ключ».
- Recovery flow собирает три фактора. Если все сошлись — ключ собирается из частей, сохраняется на новый телефон.
- Теперь Сергей может расшифровать свой конфиг.

**F-4 в этом сценарии**: предоставила identity (UUID) — необходимое условие, чтобы recovery понимал «для кого восстанавливать ключ». Но **сам ключ** через F-4 не приходит. Это **другая спека**.

**Главный вывод сценария**: «полное восстановление» = четыре независимых механизма. F-4 закрывает первый слой. Второй — S-8. Третий — автоматически (Firestore rules + UUID). Четвёртый — P-6.

**Что закрывает**: cross-reference на P-6 recovery spec; объяснение **границы** F-4; новый блок в FR-034.

---

### Что не покрыто сценариями (намеренно)

**Сценарий «двойной tap на кнопку Войти»** (двойной запуск Google bottom-sheet): техническая защита (дедупликация в адаптере). Не user-facing flow, остаётся в Edge Cases секции.

**Сценарий «pairing двух устройств через QR»**: это S-2 spec territory целиком. F-4 в pairing не участвует, только даёт UUID. Полноценные сценарии — в S-2 spec, когда будет написана. (US 7 в этой спеке остаётся как P3 integration test marker.)

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Senior устанавливает app, проходит wizard, пользуется launcher'ом **без** Sign-In (Priority: P1)

Бабушка (или admin за неё) ставит app. Wizard (F-3) спрашивает язык, тему, размер шрифта, выбор preset'а, ROLE_HOME permission. **Никакого Sign-In screen.** Launcher запускается, плитки работают, локальные контакты редактируются, темы применяются. Конфиг хранится в локальном DataStore. Cloud features (pair, sync, push, SOS, photos) — невидимы или показаны как opt-in. App работает **бесплатно бессрочно**.

**Why this priority**: это **базовое требование** деferred-cloud architecture (decision 2026-06-15-deferred-cloud/01). Без него F-4 нарушает принцип self-sufficiency устройства. Это **обязательная** негативная проверка: F-4 НЕ должна активироваться automatically.

**Independent Test**: установить app свежо, пройти wizard, использовать launcher 10 минут. Проверить: `AuthProvider.currentUser` остаётся `null`, `SessionStore` пустой, нет network calls к Firebase Auth.

**Acceptance Scenarios**:

1. **Given** F-4 merged, app свежо установлен, **When** пользователь проходит wizard и попадает на главный экран, **Then** Sign-In screen НЕ показывается, `AuthProvider.currentUser.first() == null`, `SessionStore.current() == null`, в logs нет Firebase Auth calls.
2. **Given** local mode, **When** пользователь open / edit / save локальный конфиг, **Then** конфиг сохраняется в DataStore без вызова `AuthProvider`.
3. **Given** local mode, **When** пользователь делает ACTION_DIAL звонок локальному контакту, **Then** звонок работает без `AuthProvider`.
4. **Given** local mode forever (пользователь никогда не нажимает «Войти в Google» в wizard или в `SignInTrigger`), **When** app работает любое время, **Then** subscription state не запрашивается, billing nudge не показывается (per decision 03 §«Что не делаем — Local feature gating»).

---

### User Story 2 — Пользователь в wizard выбирает «Войти в Google для восстановления настроек» (Priority: P1)

В wizard (F-3) на screen 2 «Настройка приложения» показаны два варианта: «Настроить с нуля» и «Войти в Google для восстановления настроек». Пользователь (бабушка с помощью внука-admin'а, или admin на своём устройстве, или senior сам если конфиг создавался ранее на другом устройстве) нажимает «Войти в Google». **Сразу открывается Google sign-in screen** (Credential Manager bottom-sheet) — без промежуточного explainer-экрана. После выбора аккаунта и подтверждения F-4 сохраняет identity в `SessionStore`, эмитит `AuthIdentity` через `currentUser` flow, и **завершает свою работу**.

Дальше **независимо** от F-4 работают другие системы:
- **Config-sync сервис** (S-8 territory) подписан на `currentUser` flow. Получив событие «authenticated», он запрашивает существующий конфиг с сервера. Если конфиг есть и его schema-version старая — прогоняет через migrators. Если конфликтов нет — применяет. Если конфликты есть — VersionedConfigViewer UI (S-8).
- **Wizard** (F-3) тоже подписан на `currentUser` flow. После того как config-sync применил/частично применил конфиг, wizard рекalibriрует свои шаги: что покрыто конфигом → пропускает; что не распозналось при migration → оставляет для донастройки; OS-level (ROLE_HOME) → проверяет runtime независимо от конфига.

**Why this priority**: это **основной cloud user flow F-4**. Без него identity-слой бесполезен.

**Independent Test**: эмулятор `pixel_5_api_34` с Google Play Services. В wizard screen 2 нажать «Войти в Google». Verify: Google sign-in screen открывается без explainer'а, после success `AuthProvider.currentUser.first()` возвращает non-null `AuthIdentity`. Verify (negative): F-4 **не** вызывает config-sync, не пушит конфиг сам — это делает отдельный сервис (mock-out в F-4 tests).

**Acceptance Scenarios**:

1. **Given** wizard screen 2 показан, **When** пользователь нажимает «Войти в Google», **Then** Google sign-in screen открывается **сразу**, без промежуточного explainer-экрана.
2. **Given** Google sign-in screen открыт, **When** Credential Manager возвращает credential success, **Then** `AuthProvider.currentUser` обновляется до `AuthIdentity { stableId=<наш UUID>, email=<google-email>, displayName=<google-name> }` (без `providerKind` field — он удалён из `AuthIdentity` per clarification Q4). Адаптер внутри сохраняет `SessionRecord` через `SessionStore` (internal — consumer'ы этого не видят, per clarification Q2; test'ы проверяют это через adapter-internal harness, не публичный API).
3. **Given** успешный sign-in, **When** F-4 завершает работу, **Then** F-4 **не вызывает** config-sync, **не пушит** локальный конфиг, **не запрашивает** конфиг с сервера. Любая попытка такого вызова в коде F-4 — bug (verified property test).
4. **Given** успешный sign-in, **When** wizard и config-sync подписаны на `currentUser` flow, **Then** они получают event независимо и реагируют **независимо** друг от друга. F-4 их не знает.
5. **Given** Google sign-in screen открыт, **When** пользователь нажимает «Отмена» (или закрывает окно жестом), **Then** `signIn(...)` возвращает `Outcome.Failure(AuthError.Cancelled)`, wizard остаётся на screen 2 с двумя кнопками без toast'ов и сообщений; пользователь может снова нажать «Войти» или выбрать «Настроить с нуля».
6. **Given** Google не вернул email (rare edge case с corporate-restricted accounts), **When** adapter обрабатывает credential, **Then** `Outcome.Failure(AuthError.NoEmail)` с UI message «используйте личный Google-аккаунт» (per decision 2026-05-30/03 §«User без null-полей»). Wizard остаётся на screen 2.
7. **Given** новый Google-аккаунт (никогда не входил ранее), **When** sign-in success, **Then** `GoogleSignInAuthAdapter` проверяет `/identity-links/google/{sub}` в Firestore → не находит → генерирует новый UUID → создаёт запись `/identity-links/google/{sub} → UUID` → создаёт `/users/{UUID}` → возвращает `AuthIdentity` с этим UUID как `stableId`.
8. **Given** существующий Google-аккаунт (раньше входил на другом устройстве), **When** sign-in success, **Then** adapter находит существующий UUID в `/identity-links/google/{sub}` и возвращает тот же `stableId` (cross-device identity stability).

---

### User Story 3 — Consumer (F-5 / S-2) пишет код против `AuthProvider` port **без знания Google / Firebase** (Priority: P1)

Разработчик F-5 пишет `ConfigCipher` deriving keys from `AuthIdentity.stableId`. Разработчик S-2 пишет pairing flow используя `AuthIdentity.stableId` как owner UID для delegation document. **Ни один из них** не импортирует Firebase / Google SDK, не знает, что под капотом Google. Если завтра Google заменён на Phone Auth — их код **не меняется**.

**Why this priority**: это **главный** архитектурный test F-4 — provider-agnostic port. Без него весь смысл rule 2 ACL для auth теряется.

**Independent Test**: проверяется через fitness function:
- Detekt-правило: ни один файл вне `app/androidMain/auth/` не импортирует `com.google.firebase.auth.*` / `com.google.android.libraries.identity.googleid.*` / `androidx.credentials.*`.
- Gradle dependency check: `core/domain/auth/` не имеет dependency на Firebase / Google SDK.

**Acceptance Scenarios**:

1. **Given** F-5 разработчик пишет `ConfigCipher`, **When** компилирует файл `core/config/cipher/ConfigCipher.kt`, **Then** grep по файлу не находит `firebase`, `google`, `oauth`, `credential` (case-insensitive).
2. **Given** S-2 разработчик пишет `PairingFlow`, **When** компилирует файл `core/pairing/PairingFlow.kt`, **Then** grep не находит vendor имён.
3. **Given** maintainer F-4 в будущем добавляет `PhoneAuthAdapter`, **When** заменяется DI binding в `app/androidMain/auth/AuthAdapterSelector.kt`, **Then** ни один файл в `core/config/`, `core/pairing/`, `app/sos/`, `app/photos/` **не требует** изменений.
4. **Given** `core/domain/auth/AuthProvider.kt`, **When** grep его контента, **Then** не находит слова `Google`, `Firebase`, `OAuth`, `Apple`, `Phone`, `Email`. Допустимы только `AuthIdentity`, `AuthError`, `Outcome`, `Flow` (no `ProviderKind` — он удалён per clarification Q4).

---

### User Story 4 — Token refresh при expired session (Priority: P2)

Пользователь Sign-In один раз. Через час consumer-сервис (S-8 config-sync, S-4 push register, S-5 media upload) делает server interaction. Firebase JWT мог истечь. `EncryptedLocalSessionStore.current()` (internal) возвращает session с `expiresAt < now`. `AuthProvider` adapter автоматически инициирует refresh через Firebase Auth SDK refresh token flow. На уровне consumer'а это **прозрачно** — `currentUser` flow продолжает эмитить ту же identity, server operations продолжают работать.

**Why this priority**: P2 — это **обязательно для production** (без refresh пользователь будет видеть «войдите снова» каждый час), но не блокирует первый sign-in.

**Independent Test**: `FakeAuthAdapter` + `FakeSessionStore` тест: предварительно вставить expired session в FakeSessionStore, trigger refresh through adapter-internal API, verify new session saved.

**Acceptance Scenarios**:

1. **Given** valid session с `expiresAt > now + 5min`, **When** consumer-сервис делает server interaction, **Then** adapter использует session как есть, без refresh.
2. **Given** session с `expiresAt < now`, **When** adapter получает trigger (внутренний — periodic check / on-demand), **Then** adapter автоматически вызывает refresh, новая session сохраняется в `SessionStore`, `currentUser` flow продолжает эмитить ту же identity.
3. **Given** refresh failed (network error / revoked refresh token), **When** refresh attempt завершается, **Then** `AuthProvider.currentUser` эмитит `null`, `SessionStore.clear()` вызывается. Consumer'ы видят transition through `currentUser` flow.

---

### User Story 5 — Session persistence через app restart (Priority: P2)

После Sign-In пользователь закрывает app. Через час открывает снова. `SessionStore` хранит session encrypted локально. `AuthProvider` инициализируется → читает session → `currentUser` сразу возвращает `AuthIdentity` без повторного Sign-In.

**Why this priority**: P2 — UX-критично; без этого каждый app open = Sign-In.

**Independent Test**: instrumentation test: Sign-In, kill process, restart → `AuthProvider.currentUser.first()` non-null. На JVM: `FakeSessionStore` сохраняет state, новый instance `AuthProvider` поднимает state.

**Acceptance Scenarios**:

1. **Given** успешный Sign-In, **When** process killed и restarted, **Then** `AuthProvider.currentUser.first()` возвращает same `AuthIdentity` без UI prompt.
2. **Given** session expired между restart'ами, **When** app поднимается, **Then** срабатывает refresh flow (User Story 4); если refresh failed — `currentUser = null`.
3. **Given** session blob в `SessionStore` corrupted (например, manual edit), **When** app поднимается, **Then** session игнорируется, `currentUser = null`, без crash.

---

### User Story 6 — Provider-swap fitness function (Priority: P2)

Maintainer F-4 (или будущий разработчик) хочет проверить, что provider-agnostic дизайн работает. В DI swap'ит `GoogleSignInAuthAdapter` на `FakeAuthAdapter` (или гипотетический `FakePhoneAuthAdapter`). Прогон тестов F-5, S-2, S-4 проходит без изменений в этих модулях.

**Why this priority**: это **fitness function** для rule 2 ACL применённого к auth. Без неё дизайн «provider-agnostic» — слова, не свойство.

**Independent Test**: специальный integration test в `app/src/test/java/.../ProviderSwapFitnessTest.kt`: prepare DI graph A с Google adapter, prepare DI graph B с Fake adapter. Прогнать набор consumer-тестов на обоих graph'ах. Результаты должны быть идентичны (с точностью до identity-values, конечно).

**Acceptance Scenarios**:

1. **Given** DI graph A (Google adapter), F-5 / S-2 consumer-тесты проходят, **When** DI graph B (Fake adapter) подставляется, **Then** те же consumer-тесты проходят без code changes.
2. **Given** новый hypothetical `EmailAuthAdapter` (заглушка), **When** включён в DI, **Then** все consumer'ы продолжают работать, требуются изменения только в `AuthAdapterSelector` (DI wiring).

---

### User Story 7 — Two-emulator pairing через QR (Priority: P3)

Admin emulator Sign-In через Google → admin Sign-In. Senior emulator Sign-In через другой Google account. Admin emulator показывает QR. Senior emulator scans QR. Pairing завершается, оба эмулятора показывают связку (per S-2). Это **integration test** для F-4 + S-2 совместной работы (S-2 живёт в отдельной спеке).

**Why this priority**: P3 — потому что детали pairing flow живут в S-2; F-4 предоставляет только identity. Тест помечается как P3 в этой спеке и переезжает на полноту в S-2 spec.

**Independent Test**: два эмулятора через skill `android-emulator`, оба с Google Play Services. Manual orchestration — pre-MVP уровень.

**Acceptance Scenarios**:

1. **Given** admin emulator Sign-In и senior emulator Sign-In, **When** admin показывает QR и senior scans, **Then** делегация создаётся в Firestore с `request.auth.uid` обоих устройств (но конкретный flow — в S-2).

---

### Edge Cases

- **Google account без email** (corporate-restricted) → `AuthError.NoEmail` (FR-016, decision 2026-05-30/03). UI message «используйте личный Google-аккаунт». Wizard остаётся на screen 2.
- **Google Play Services недоступен** (e.g., Huawei устройство без GMS) → `AuthAdapterSelector.pickAdapter()` возвращает error `NoSupportedAuthProvider`. UI показывает «вход через Google недоступен на этом устройстве» (Huawei adapter — out of scope MVP). App **продолжает работать в local mode** — это **критически важно**.
- **Network error во время Sign-In** → `Outcome.Failure(AuthError.NetworkError)`. UI показывает retry. App остаётся в local mode.
- **Пользователь отменил Sign-In bottom-sheet** → `Outcome.Failure(AuthError.Cancelled)`. **Никакого** error toast (per clarification Q6). Wizard остаётся на screen 2; пользователь снова может выбрать «Войти» или «Настроить с нуля».
- **Sign-In trap (второй Google account)**: пользователь раньше Sign-In под account A, sign-out, теперь Sign-In под account B → это **новый чистый аккаунт** (per decision 2026-06-15/01 §«Sign-In trap»). `GoogleSignInAuthAdapter` находит/создаёт **новый** `stableId` для account B в `/identity-links/google/{sub_B}`. Локальный кеш (плитки, контакты, темы) **остаётся как был** — это рабочее состояние app'а (per clarification Q3); config-sync (S-8) разрулит конфликт при следующем reconnect (попытается merge серверного конфига account B с локальным кешем).
- **Конфиг на сервере устарел** (sign-in после долгого перерыва — год, два): F-4 этого не видит. Config-sync (S-8 territory) подтягивает с сервера, прогоняет через migrators, помечает не распознанные части. Wizard (F-3) рекalibriрует свои шаги по результату migration. F-4 в этом не участвует.
- **Конфликт серверного и локального конфига** (например, бабушка две недели работала локально после sign-out, потом нажала «Войти снова», а admin за это время менял конфиг на сервере): F-4 этого не видит. Config-sync применяет policy «сервер всегда приоритетнее, локальный мержится в серверный, push обратно». UI разруливает S-8 VersionedConfigViewer (детали merge'а — там).
- **Android-настройки сбиты у удалённого senior'а** (бабушка случайно сбросила ROLE_HOME, или OEM update инвалидировал permission): F-4 этого не видит. S-9 Phone Health Monitoring spec строит health-report flow: периодически проверяет состояние Android-настроек устройства и пушит на сервер; admin device получает оповещение и может позвонить бабушке. F-4 только идентифицирует устройство для health-report ассоциации (через `currentUser` flow).
- **Token expired offline** (refresh невозможен) → `currentUser` остаётся `AuthIdentity` (last known), любой server-bound action falls back на retry-when-online. UI индикатор «оффлайн» (детали — в `checklist-failure-recovery`).
- **App uninstall** → `SessionStore` encrypted storage уничтожается с app data → при reinstall пользователь Sign-In заново. Identity-links запись на сервере **сохраняется** — повторный sign-in с того же Google аккаунта вернёт тот же `stableId` (cross-device stability).
- **`SessionStore` encrypted blob corrupted** → игнорируем, `currentUser = null`, не crash.
- **Multi-account на одном устройстве** (Credential Manager поддерживает) → MVP **не строит** per-account switching UX (per decision 2026-05-30/08 §«Что не входит»). Пользователь Sign-In под account A, чтобы переключиться — sign-out через `SignInTrigger` → sign-in заново.
- **Firebase Auth project deleted / API key revoked** на стороне admin'а → `AuthError.ProviderUnavailable`. App остаётся в local mode. Документировать в admin runbook (post-MVP).
- **Anonymous user попытка** (legacy code path после удаления anonymous) → compile-time impossible. Detekt-правило ловит import `signInAnonymously` в любом файле.
- **`AuthProvider.signIn` вызван дважды параллельно** (двойной tap, race) → adapter MUST deduplicate (только один Credential Manager bottom-sheet активен в момент времени). Property test ловит.
- **`subscription_state` запрос в local-only mode** → `SubscriptionState.Unknown` (не `Active`, не `Expired` — мы honestly не знаем, **не должны** запрашивать). Сloud-feature gating не зависит от этого поля (per decision 03 — server-validated JWT).
- **F-4 после sign-in пытается «помочь» и вызвать config sync** — это **bug**. Любое такое поведение в коде F-4 нарушает clarification Q7 boundary. Property test проверяет: при mock-out config-sync во время `signIn()` нет ни одного call'а на mock'е.

## Requirements *(mandatory)*

### Functional Requirements

**Module structure & build:**

- **FR-001**: `core/domain/auth/` MUST содержать `AuthProvider`, `AuthIdentity`, `AuthError`, `SessionStore`, `User`, `SubscriptionState`, `Outcome` (если ещё не существует в `core/domain/common/`). **`SessionRecord` — internal type** (находится в `core/domain/auth/internal/` или эквивалентном package; не экспонируется consumer'ам). **`ProviderKind` отсутствует в `core/domain/auth/`** — он удалён из доменной модели (per clarification Q4). Adapter знает свой kind у себя внутри для логирования и метрик. Никаких других файлов в этом package.
- **FR-002**: `core/domain/auth/` MUST НЕ импортировать никаких vendor SDK (`com.google.*`, `com.google.firebase.*`, `androidx.credentials.*`, `com.apple.*`) — проверяется Detekt-правилом + Gradle dependency check.
- **FR-003**: `app/androidMain/auth/` MUST содержать `GoogleSignInAuthAdapter`, `AuthAdapterSelector`, `EncryptedLocalSessionStore`, и **composable `SignInTrigger`** (`app/androidMain/auth/ui/SignInTrigger.kt` — самостоятельная UI-единица для входа/выхода, см. FR-033). Никакого custom Sign-In screen — Credential Manager рисует свой bottom-sheet (per decision 2026-05-30/08 §«Senior-safe UI»).
- **FR-004**: `core/commonTest/auth/` MUST содержать `FakeAuthAdapter` и `FakeSessionStore`. Помечены `@VisibleForTesting`. Detekt-правило ловит их import в `app/src/main/` / `:app:release`.
- **FR-005**: `GoogleSignInAuthAdapter` MUST содержать inline comment:
  ```
  // TODO(auth-provider-extensions): Phone / Email-Password / Apple / SSO
  // adapters add through this port without changing the port shape.
  // TODO(server-roadmap): After own-server cutover — replace Firebase Auth
  // exchange step (Step 2) with POST ID Token → /auth/google on our server,
  // server verifies Google signature, issues own JWT. Port unchanged.
  // Per decision 2026-05-30-f4-identity/05-own-server-migration-strategy.md.
  ```

**Domain ports & types:**

- **FR-006**: `AuthProvider` port MUST содержать:
  - `val currentUser: Flow<AuthIdentity?>` — null = не залогинен. Эмитит обновления при sign-in / sign-out / refresh-failed. **Единственный публичный путь к identity для consumer'ов.**
  - `suspend fun signIn(): Outcome<AuthIdentity, AuthError>` — без параметров (per clarification Q4: `providerKind` удалён). Adapter знает свой kind у себя. Future Phone/Email/Apple adapter'ы реализуют этот же port — DI seam выбирает, какой adapter активен (FR-019).
  - `suspend fun signOut()` — очищает `SessionStore`, эмитит `null` в `currentUser`. **Не трогает** локальный кеш конфигов, плиток, контактов, тем (per clarification Q3). Поведение sign-out = «остановить синхронизацию с сервером, кеш остаётся как рабочее состояние app'а».
  - **Сигнатуры используют только** Kotlin stdlib + coroutines + типы из `core/domain/auth/`. Никаких `String token`, `Map<String, Any>`, JSON containers, Firebase / Google типов.
  - **Constraint после success sign-in**: F-4 после success **только** (a) сохраняет `SessionRecord` в `SessionStore`, (b) эмитит `AuthIdentity` через `currentUser` flow. **НЕ инициирует** config sync, push registration, conflict resolution, восстановление конфига с сервера (per clarification Q7). Любая такая логика в коде F-4 — bug. Consumer-системы (config-sync, push-registration, S-8 VersionedConfigViewer) подписываются на `currentUser` независимо.
  - **`AuthorizedRequestSigner` port — НЕ часть F-4**. Когда S-8 sync понадобится подписывать RPC-запросы серверным токеном, port для этого добавляется отдельной спекой. Inline TODO в `EncryptedLocalSessionStore`: `// TODO(authorized-request-signer): future port для подписи RPC — adapter будет читать SessionRecord internal, consumer'ы не видят токен`.
- **FR-007**: `AuthIdentity` data class содержит **только** эти поля:
  - `stableId: String` — **наш собственный UUID**, не Firebase UID и не Google `sub` claim (per clarification Q1). Генерируется при первом sign-in нового аккаунта; стабилен через own-server cutover; не привязан к Google identity system. Маппинг провайдер-specific → UUID живёт в `/identity-links/{providerKind}/{providerAccountId}` Firestore коллекции (см. FR-016a). Consumer'ы используют `stableId` как primary key для своих документов.
  - `displayName: String?` — nullable.
  - `email: String?` — nullable на уровне типа (FR-016 уточняет policy для MVP).
  - **`providerKind` — отсутствует в `AuthIdentity`** (per clarification Q4). Если adapter'у нужно отличать провайдеров для своих внутренних целей (логи, метрики) — хранит у себя.
- **FR-008**: ~~`ProviderKind` shape~~ — снято per clarification Q4. `ProviderKind` отсутствует в `core/domain/auth/`. Будущие провайдеры (Phone / EmailPassword / Apple / SSO) добавляются как **новые adapter-классы**, реализующие `AuthProvider` port; DI seam (FR-019) выбирает активный adapter. **Domain не знает** о множественности провайдеров — port имеет `signIn()` без параметра kind.
- **FR-009**: `AuthError` sealed: `object NetworkError`, `object Cancelled`, `object NoEmail`, `object ProviderUnavailable`, `data class Unknown(val message: String)`. Расширяется через новые sealed cases additive.
- **FR-010**: `User` data class — domain-level identity представление с subscription state:
  - `id: String` (= `AuthIdentity.stableId`, наш UUID).
  - `identityKeys: IdentityKeys?` (forward declaration для F-5; в F-4 = `null`, populated F-5; компилируется потому что nullable).
  - `email: String?`, `displayName: String?`.
  - `subscriptionState: SubscriptionState`.
  - **`providerKind` отсутствует** (per clarification Q4).
- **FR-011**: `SubscriptionState` — sealed: `object Unknown`, `object LocalOnly`, `object Trial`, `object Active`, `object Expired`. MVP-реализация: всегда возвращается `Unknown` (per Clarification Q6). **Никакого** client-computed `Active` — это **server-validated** в post-MVP billing spec, нарушение = client-side bypass attack (per decision 03 + checklist-tamper-resistance).
- **FR-012**: `SessionStore` port — **internal к F-4** (не экспонируется consumer'ам):
  - `suspend fun save(session: SessionRecord)`.
  - `suspend fun current(): SessionRecord?`.
  - `suspend fun clear()`.
  - `val sessionChanges: Flow<SessionRecord?>`.
  - Используется **только** между `AuthProvider` adapter'ом и `EncryptedLocalSessionStore`. Consumer'ы (F-5, S-2, S-8, etc.) **не имеют доступа** — они работают через `AuthProvider.currentUser: Flow<AuthIdentity?>` (per clarification Q2).
- **FR-013**: `SessionRecord` value class — **internal type**:
  - `stableId: String` (= identity, наш UUID).
  - `expiresAt: Instant?` — null = unknown/never.
  - `refreshToken: String?` — opaque, adapter-internal.
  - `extra: Map<String, String>` — adapter-specific blob (Firebase JWT для GoogleSignInAuthAdapter). **Только adapter** читает; **consumer'ы не видят `SessionRecord` вообще** (per clarification Q2).
  - Wire-format включает `schemaVersion: 1` (per CLAUDE.md rule 5).
  - **`providerKind` отсутствует** в `SessionRecord` (per clarification Q4); adapter знает свой kind у себя.

**Google Sign-In adapter:**

- **FR-014**: `GoogleSignInAuthAdapter` MUST использовать **Credential Manager API** (не deprecated Google Sign-In SDK) для Step 1 (Google ID Token). Per decision 2026-05-30/08.
- **FR-015**: Step 2: ID Token exchange через Firebase Auth (`signInWithCredential`). Firebase JWT хранится в `SessionRecord.extra["firebase_jwt"]`. Inline TODO(server-roadmap) обязателен (FR-005).
- **FR-016**: Email policy: если Google не вернул email → `Outcome.Failure(AuthError.NoEmail)`. Это **adapter-level** policy, не port-level (per Clarification Q3 от 2026-06-18 pre-clarify). Будущий phone adapter не будет иметь такой policy.
- **FR-016a**: **Identity-links Firestore collection** (per clarification Q1):
  - Структура: `/identity-links/{providerKind}/{providerAccountId}` → документ `{ stableId: String, createdAt: Timestamp }`. Пример: `/identity-links/google/108572394857283745728 → { stableId: "550e8400-e29b-41d4-a716-446655440000" }`.
  - `GoogleSignInAuthAdapter` после Google sign-in success:
    1. Извлекает `sub` claim из Google ID Token (это идентификатор Google account, стабильный per account).
    2. Lookup `/identity-links/google/{sub}` в Firestore.
    3. Если документ есть → возвращает `AuthIdentity` с этим `stableId` (cross-device identity stability — пользователь на втором устройстве получает тот же UUID).
    4. Если документа нет → генерирует новый UUID v4 → создаёт `/identity-links/google/{sub} → { stableId: <UUID>, createdAt: now }` → создаёт `/users/{UUID}` → возвращает `AuthIdentity` с новым `stableId`.
  - Firestore Security Rules: чтение `/identity-links/.../{accountId}` — только если `request.auth.uid == accountId` (Firebase auth UID должен совпадать с providerAccountId для данного providerKind=google).
  - **Country-ban exit ramp** inline TODO в `GoogleSignInAuthAdapter`:
    ```
    // TODO(country-ban-exit-ramp): when adding NonGoogleAuthAdapter for
    // jurisdictions where Google is restricted (China, Iran, потенциально
    // others) — the new adapter performs the same identity-links lookup
    // by its own providerKind (e.g. /identity-links/phone/+86...).
    // stableId UUID stays. No data migration. Per discussion 2026-06-18.
    ```
  - **Own-server cutover exit ramp**: при cutover `/identity-links/...` коллекция мигрирует **первой** на наш сервер; она authoritative source of truth для маппинга. Все `/users/{stableId}` документы продолжают работать без изменений `stableId`. Записано в server-roadmap как `SRV-AUTH-IDENTITY-001`.
  - **Data residency готовность**: `stableId` генерируется нашим кодом (UUID v4), значит может жить на сервере любой страны без зависимости от Google identity system.
- **FR-017**: Token refresh: adapter MUST автоматически refresh при internal trigger (periodic check / on-demand при consumer server interaction) если `expiresAt < now + 5min`. Refresh использует Firebase SDK refresh token flow. Refresh failure → `currentUser` → `null`. `currentSession()` API **не существует** в port (clarification Q2 + Q7: consumer'ы видят identity через `currentUser` flow, не через session API).
- **FR-018**: `AuthAdapterSelector` MUST runtime-detect capabilities:
  - Если `GooglePlayServicesAvailability.isAvailable()` → `GoogleSignInAuthAdapter`.
  - Иначе → error `NoSupportedAuthProvider` (Huawei / non-GMS adapter — future spec).
- **FR-019**: DI seam: `debug` / `test` source set → `FakeAuthAdapter`; `release` source set → `AuthAdapterSelector.pickAdapter()`. Build-config gate ловит accidental `FakeAuthAdapter` в release.

**SessionStore implementation:**

- **FR-020**: `EncryptedLocalSessionStore` (`app/androidMain/auth/`) MUST хранить session blob encrypted **локально** (DataStore / file в app sandbox). Шифрование — через Android EncryptedSharedPreferences (Jetpack Security `androidx.security:security-crypto`) **на этапе F-4**. **Inline TODO** для будущего перехода на `SecureKeyStore` из F-CRYPTO когда F-5 готов (additive change, не rewrite).
- **FR-021**: Wire-format session blob содержит `schemaVersion: Int = 1`, `stableId`, `expiresAtEpochMs: Long?`, `refreshToken: String?`, `extra: Map<String, String>`. Сериализация JSON через `kotlinx.serialization.json` (consistency с F-CRYPTO §FR-016 decision). **`providerKind` отсутствует** в SessionRecord (per clarification Q4: providerKind удалён из доменной модели; adapter знает свой kind у себя).
- **FR-022**: Backward-compat read test: положить blob `schemaVersion=1`, прочитать тем же кодом — pass. Имитировать будущую миграцию: handler для `schemaVersion=2` корректно мигрирует `v1` blob (per CLAUDE.md rule 5).
- **FR-023**: Corrupted blob handling: parse failure → `current()` возвращает `null`, не crash. Log warning, не crash.

**Fake adapter:**

- **FR-024**: `FakeAuthAdapter` MUST принимать предустановленный список users в конструкторе (`fakeUsers: List<AuthIdentity>`). `signIn()` (без параметров per clarification Q4) возвращает первого пользователя из списка как `Outcome.Success`; если список пуст — `AuthError.ProviderUnavailable`.
- **FR-025**: `FakeAuthAdapter.simulateRefreshFailure()`, `simulateNoEmail()`, `simulateCancellation()` — test API для триггера error path'ов.
- **FR-026**: `FakeSessionStore` — in-memory `HashMap`-based, deterministic. **Не** использует Android `EncryptedSharedPreferences` — это `commonTest`, no Android Context dependency.

**Constitution gates & rules:**

- **FR-027**: Никакой код в `core/domain/auth/` MUST НЕ содержать слова `Google`, `Firebase`, `OAuth`, `Apple`, `Phone`, `Email` (case-insensitive) — Detekt rule. Исключение: `AuthError.NoEmail` sealed name (legitimately uses word «Email» as part of error variant name) — Detekt allowlist по sealed type member names.
- **FR-028**: Никакой код в `app/sos/`, `app/photos/`, `core/config/`, `core/pairing/` MUST НЕ импортировать `com.google.firebase.auth.*` / `com.google.android.libraries.identity.googleid.*` / `androidx.credentials.*` — Detekt rule. Эти модули работают **только** через `AuthProvider` / `SessionStore` ports.
- **FR-029**: Anonymous Firebase Auth полностью удалён: Detekt rule ловит import `signInAnonymously` или `FirebaseAuth.getInstance().signInAnonymously` в любом файле.
- **FR-030**: Activation timing fitness: integration test `LocalModeNoSignInTest` запускает app cold start через wizard, проверяет что `AuthProvider.signIn(...)` не вызван. Прогоняется в CI.
- **FR-031**: `subscription_state` MUST НЕ быть client-computable как `Active` / `Expired`. Любое отображение «вы premium» MUST требовать server-validated JWT (per decision 03 + `checklist-tamper-resistance`). В F-4 поле существует только как `Unknown` placeholder (stub).
- **FR-032**: Privacy Policy update + Play Console Data Safety form + OAuth Consent Screen — pre-release tasks, документированы в spec.md, but **не блокируют** F-4 code merge (они блокируют **release** к Phase 2 cloud features).

**SignInTrigger composable (UI):**

- **FR-033**: `app/androidMain/auth/ui/SignInTrigger.kt` — самостоятельный Compose composable, **не встроенный** ни в wizard, ни в Settings (per clarification Q9). Reusable UI unit с одной ответственностью: «дать UI для входа / выхода из Google account».
  - Signature: `@Composable fun SignInTrigger(modifier: Modifier = Modifier, onSignedIn: (AuthIdentity) -> Unit = {}, onSignedOut: () -> Unit = {})`.
  - **UI structure** — два UI-элемента вместе (per owner discussion 2026-06-18):
    - **Кнопка действия**: короткое лаконичное название.
      - В состоянии «Не вошли»: **«Войти в аккаунт»** (5 слов RU, не навязывает причину, хорошо локализуется на DE / EN — короче 25 символов в любой целевой локали).
      - В состоянии «Вошли»: **«Выйти»** + рядом статус «Вошли как `<email>`».
    - **Поясняющий текст** (мелким шрифтом, информационный, **только** в состоянии «Не вошли»): «Чтобы настройки сохранялись и были доступны на других устройствах». Этот текст объясняет use cases (restore старых настроек, sync между устройствами, удалённое управление admin'ом) **без навязывания одного конкретного use case** на кнопке.
  - Поведение:
    - Подписан на `AuthProvider.currentUser` flow через injected `AuthProvider`.
    - Если `currentUser == null` — показывает структуру «кнопка Войти в аккаунт + поясняющий текст». При нажатии кнопки → `AuthProvider.signIn()` → callback `onSignedIn` при success / молчит при `Cancelled` (per US 2 acceptance scenario 5) / показывает message при `NoEmail` / `NetworkError` / `ProviderUnavailable`.
    - Если `currentUser != null` — показывает «Вошли как `<email>`» + кнопку «Выйти». При нажатии «Выйти» → `AuthProvider.signOut()` → callback `onSignedOut`. **Без confirmation dialog** (per clarification Q3: sign-out простой, локальный кеш остаётся, реальной потери данных нет).
  - **Sources of invocation**: сейчас дёргается **только** из wizard screen 2 (F-3 territory). В будущем может дёргаться из Settings spec, debug-screen, или любой другой UI без изменений в F-4.
  - Локализация: строки externalized в `core/src/commonMain/composeResources/values/strings_auth.xml` (одно место, не дублируется по consumer'ам). Кнопка должна автоматически переноситься на 2-3 строки при длинных локалях (DE expansion ~30-50%, AR — taller glyphs).

**Cold-start latency invariant:**

- **FR-035**: F-4 MUST **не блокировать** критический путь cold-start приложения (per owner discussion 2026-06-18):
  - Показ главного экрана из локального кеша конфига MUST произойти **до** прохождения F-4 session-restore и **до** любого обращения к серверу.
  - F-4 при инициализации app: чтение зашифрованного файла сессии, обновление токена (если истёк), оповещение consumer'ов (config-sync, push-registration) — всё происходит **асинхронно в фоне**, не блокируя first paint UI.
  - `AuthProvider.currentUser` flow в момент cold-start эмитит **`null`** первым (default состояние) → UI subscriber'ы (`SignInTrigger`) рисуются в state «Не вошли» → когда сессия восстановлена из диска, flow эмитит `AuthIdentity` → UI recompose (showing «Вошли как X»).
  - Любая блокирующая операция в критическом пути cold-start (синхронное чтение SessionStore, network call в Application.onCreate, ожидание Firebase Auth initialization перед UI render) — **bug**.
  - **Background config-sync** (S-8 territory) — также не блокирующий: подписан на `currentUser` flow, при появлении identity идёт на сервер за свежими данными, применяет к работающему UI **в режиме использования** тихо (политика merge в режиме редактирования — S-8 concern).
  - **Verification**: integration test `ColdStartLatencyTest`: app cold-start → assert главный экран отрисовался в ≤ X ms; assert F-4 `SessionStore.current()` ещё не завершился к этому моменту. (Концrete X ms — plan.md decision; recommendation: ≤ 500ms perceived latency на pixel_5_api_34.)

**Accessibility (TalkBack / screen reader):**

- **FR-036**: `SignInTrigger` composable MUST провайдить explicit content descriptions для assistive technologies (per constitution check Gate 5 recommendation, Article VIII §2 screen reader semantics):
  - State «Не вошли»: кнопка `contentDescription = "Войти в Google аккаунт. Чтобы настройки сохранялись и были доступны на других устройствах"` (полная фраза, объединяющая label + поясняющий текст, чтобы screen reader произносил один semantic blob).
  - State «Вошли»: статус `contentDescription = "Вошли как $email"`; кнопка `contentDescription = "Выйти из учётной записи $email"`.
  - Loading state: `contentDescription = "Выполняется вход" / "Выполняется выход"` с `role = ProgressIndicator`.
  - Error state: error message MUST имeть `liveRegion = Polite` чтобы TalkBack озвучил появление ошибки автоматически.
  - **Verification**: Compose UI tests с `composeTestRule.onNodeWithContentDescription(...)` для каждого state. Manual TalkBack walk-through pre-release.

**Cross-references to other specs:**

- **FR-034**: F-4 предоставляет identity для будущих consumer'ов через `currentUser: Flow<AuthIdentity?>`. Конкретные consumer'ы — отдельные спеки:
  - **S-8 VersionedConfigViewer + Config Sync**: подписывается на `currentUser`. Получив event «authenticated», сам забирает конфиг с сервера (сервер всегда приоритетнее), применяет migrators по schema-version, мержит с локальным кешем, пушит обратно. F-4 этого не знает. **Three-tier config cache model** (server / local app cache / edit cache; edit cache для своего = 1 кэш, для управляемых = N кэшей; политика «закрытие явно спрашивает push на сервер») — фиксирована в memory `project_config_cache_model.md` и будет полностью описана в S-8 spec. F-4 на эту модель **не влияет** — только эмитит identity event.
  - **S-6 Account Deletion**: использует `AuthProvider.signOut()` + дополнительный `AuthProvider.deleteAccount()` API (детали — в S-6 spec). Sign-out ≠ delete account: первое оставляет локальный кеш, второе — стирает identity-links запись и серверный namespace.
  - **S-9 Phone Health Monitoring**: подписывается на `currentUser` для **идентификации устройства** (чтобы admin device мог сопоставить health-report с конкретным senior'ом). S-9 spec строит health-report flow: проверяет Android-настройки устройства (ROLE_HOME granted? runtime permissions активны? Google Play Services доступен? wizard завершён или прерван?) **по событиям** (cold-start приложения, system broadcast о смене permissions, resume из background) — **не** периодическим polling'ом батарею не сажая. Пушит health-report на сервер для admin'а. **Если admin удалённо настраивает телефон senior'а и видит, что какие-то Android-настройки сбиты или wizard не завершён — admin получает оповещение через health-report.** F-4 этого **не делает**, только идентифицирует устройство. Inline TODO в `EncryptedLocalSessionStore`: `// TODO(s9-health-monitoring): identity нужна S-9 для health-report ассоциации; S-9 подписывается на AuthProvider.currentUser независимо.`
  - **F-5 ConfigCipher (E2E encryption)**: использует `AuthIdentity.stableId` (наш UUID) как seed для key derivation. F-5 не знает про Google / Firebase.
  - **S-2 Admin App + QR Pairing**: использует `AuthIdentity.stableId` как owner UID в delegation document. S-2 не знает про Google / Firebase.
  - **P-6 Account Recovery Flow + 2FA escrow**: при потере устройства Google sign-in возвращает UUID (F-4 alone достаточно), но **не возвращает ключи шифрования E2E**. F-4 покрывает **только первый слой** identity-information. Полное восстановление требует четырёх независимых механизмов (см. Сценарий 8): **(1)** UUID — F-4; **(2)** конфиг — S-8; **(3)** делегирования — автоматически через Firestore Security Rules + UUID; **(4)** ключи шифрования — **P-6** через social recovery (доверенный peer + passphrase + email 2FA). F-4 предоставляет identity, на основе которой P-6 recovery flow знает «для кого восстанавливать ключ». Сам ключ через F-4 **не** проходит — это нарушило бы E2E invariant (only user devices may decrypt).

### Key Entities

- **`AuthProvider`**: domain port, vendor-agnostic, для всех методов авторизации.
- **`AuthIdentity`**: domain value возвращаемый Sign-In. `stableId` (наш UUID), `email?`, `displayName?`. **`providerKind` отсутствует** (per clarification Q4).
- **`ProviderKind`**: open enum, MVP = `{ GOOGLE }`. Расширяется additive.
- **`AuthError`**: sealed type ошибок. `NetworkError`, `Cancelled`, `NoEmail`, `ProviderUnavailable`, `Unknown`.
- **`User`**: domain identity-с-subscription view. Включает `identityKeys` (forward declaration для F-5).
- **`SubscriptionState`**: sealed type, MVP всегда `Unknown`. Server-validated в post-MVP billing.
- **`SessionStore`**: domain port, hides session storage.
- **`SessionRecord`**: persisted wire-format value (`schemaVersion`, `stableId`, `expiresAt?`, `refreshToken?`, `extra`). **`providerKind` отсутствует** (per clarification Q4). Internal type — consumer'ы не видят (per clarification Q2).
- **`GoogleSignInAuthAdapter`**: Android adapter, единственная реальная реализация в MVP.
- **`FakeAuthAdapter` / `FakeSessionStore`**: для тестов.
- **`EncryptedLocalSessionStore`**: Android adapter `SessionStore` поверх EncryptedSharedPreferences.
- **`AuthAdapterSelector`**: runtime device-capability dispatch.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: F-5 разработчик пишет `ConfigCipher` поверх `AuthProvider.currentUser` без единого импорта `com.google.*` или `com.firebase.*` в файле `ConfigCipher.kt`. Verification: grep по `core/config/cipher/ConfigCipher.kt` — нет таких импортов.
- **SC-002**: S-2 разработчик пишет `PairingFlow` без импортов vendor SDK. Verification: grep по `core/pairing/PairingFlow.kt`.
- **SC-003**: `core/domain/auth/AuthProvider.kt` не содержит слов `Google` / `Firebase` / `OAuth` / `Apple` / `Phone` / `Email`. Verification: grep + Detekt rule passes.
- **SC-004**: Cold-start app integration test (`LocalModeNoSignInTest`) проходит **зелёным**: wizard → main screen → 10s idle, без `AuthProvider.signIn(...)` calls. Verification: CI зелёный.
- **SC-005**: First-cloud-action test проходит на эмуляторе: tap cloud-feature → explainer-prompt → Google Sign-In → identity bound. Verification: instrumentation test.
- **SC-006**: Token refresh test (User Story 4) проходит на FakeAdapter и на эмуляторе с Firebase emulator suite. Verification: оба зелёные.
- **SC-007**: Session persistence test (User Story 5) проходит. Verification: instrumentation test killing process.
- **SC-008**: Provider-swap fitness test: prepare DI graph A (Google), prepare DI graph B (Fake) → run consumer-test suite → identical pass rate. Verification: специальный `ProviderSwapFitnessTest`.
- **SC-009**: Wire-format roundtrip test для `SessionRecord` проходит: serialize v1 → deserialize → equal. Verification: `SessionRecordRoundtripTest`.
- **SC-010**: Detekt-правила: (a) `core/domain/auth/` чист от vendor SDK imports; (b) `core/config/` / `core/pairing/` / `app/sos/` / `app/photos/` чисты от Firebase Auth / Credential Manager imports; (c) `FakeAuthAdapter` не импортируется в `:app:release`; (d) `signInAnonymously` не существует. Verification: Detekt CI passes.
- **SC-011**: Two-emulator pairing smoke test (User Story 7) проходит на двух эмуляторах. Verification: instrumentation test через skill `android-emulator`.
- **SC-012**: F-4 merged до **активации** любой Phase 2 cloud-feature (S-2/S-4/S-5/S-8). Effort estimate: Medium (~2 weeks для port + adapter + tests; **исключая** rewrite специй 007-012 — это входит в их собственные спеки).
- **SC-013**: Privacy Policy section про email/displayName/profile photo URL добавлен, Play Data Safety form prepared. Verification: pre-release checklist.
- **SC-014**: `subscription_state` всегда = `Unknown` в F-4; нет ни одного места в коде, где `SubscriptionState.Active` / `.Expired` устанавливается client-side. Verification: grep на присваивание + property test.

## Assumptions

- **Firebase Auth project configured** (admin task, не код). Если admin не настроил — `GoogleSignInAuthAdapter` упадёт с `ProviderUnavailable`, app остаётся в local mode.
- **F-3 wizard merged** — local-mode работает без F-4 (это **предусловие**, не consequence).
- **F-CRYPTO merged** — не runtime dependency F-4, но F-5 будет deriving keys from `AuthIdentity.stableId`, и `EncryptedLocalSessionStore` может позже мигрировать на `SecureKeyStore` (additive, не rewrite).
- **Google Sign-In via Credential Manager** — рекомендованный Google путь (deprecated Google Sign-In SDK не используется).
- **OAuth scopes**: только `openid email profile` (per decision 2026-05-30/08). Не больше — иначе Google verification required.
- **No anonymous Firebase Auth** — полностью удалён (decision 2026-05-30/02). MVP-пользователи pre-release не имеют real data, wipe не строится.
- **Credential Manager bottom-sheet — нативный Google UI**, не кастомизируется. Senior accessibility ограничена тем, что даёт Google. Настройка через admin device — рекомендуемый flow.
- **Subscription billing — post-MVP**, F-4 содержит только stub field `subscription_state`.
- **Server-validated entitlement JWT** (для будущей billing) — Worker endpoint, F-4 не строит, но `SessionRecord.extra` поддерживает хранение этого JWT.
- **Owner-developer (g.jeromwork)** — solo-dev; privacy / security review — это decision-документы и skill `checklist-security`.
- **Никакой telemetry / analytics** в `core/domain/auth/`. Module работает offline (кроме Sign-In moment).

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `android-emulator` для instrumentation тестов; `tv_4k_api_34` для ATV smoke (отложен — `// TODO(physical-device): ATV smoke`). Pure JVM unit tests — основная масса.
- **Fake adapters used**:
  - `FakeAuthAdapter` (pre-seeded `AuthIdentity` list, deterministic).
  - `FakeSessionStore` (in-memory HashMap).
  - Consumer'ы (F-5 placeholder, S-2 placeholder) используют их.
- **Fixtures / seed data**:
  - `core/commonTest/resources/auth-fixtures/user-google-v1.json` — sample `AuthIdentity` для тестов.
  - `core/commonTest/resources/auth-fixtures/session-record-v1.json` — sample wire-format для roundtrip / backward-compat.
  - Firebase Auth Emulator (опционально, для integration тестов adapter'а; **не обязателен** для merge — `// TODO(local-dev): Firebase Auth Emulator wiring`).
- **Verification commands**:
  - `./gradlew :core:domain:jvmTest --tests *AuthProviderContractTest` — port contract tests на Fake.
  - `./gradlew :core:domain:jvmTest --tests *SessionRecordRoundtripTest` — wire-format roundtrip.
  - `./gradlew :app:testDebugUnitTest --tests *ProviderSwapFitnessTest` — DI swap test.
  - `./gradlew :app:connectedDebugAndroidTest --tests *GoogleSignInAdapterInstrumentationTest` — Sign-In flow на эмуляторе (требует Google Play Services).
  - `./gradlew :app:connectedDebugAndroidTest --tests *EncryptedLocalSessionStoreTest` — SessionStore persistence.
  - `./gradlew :app:connectedDebugAndroidTest --tests *LocalModeNoSignInTest` — negative test: no Sign-In в local mode.
  - `./gradlew detekt` — все vendor-import / anonymous-import / fake-in-release правила.
- **Cannot-test-locally gaps**:
  - **Real Google account** Sign-In smoke — `// TODO(physical-device): real Google account smoke test (manual pre-release)`.
  - **OAuth Consent Screen verification** — Google review process, manual, pre-release task.
  - **Cross-Google-account migration** (Sign-In trap edge) — manual real-device test, `// TODO(physical-device)`.
  - **Firebase Auth Emulator** integration — `// TODO(local-dev): configure Firebase Auth Emulator для adapter integration tests без real Firebase project`.
  - **non-GMS device** behavior (`AuthAdapterSelector` error path) — `// TODO(physical-device): Huawei P-series без GMS — capability detect smoke`.
  - **Token expiry edge in production timing** — Firebase JWT expiry = 1 hour; soak test невозможен в CI; `// TODO(soak-test): manual 2-hour idle test pre-release`.

## AI Affordance *(mandatory)*

**Limited AI affordance — read-only identity surface only.**

`AuthProvider` exposes identity, but **never** authentication credentials к AI agent'у. Future capability registry (F-2) может expose следующие **read-only** capabilities:

- **Exposable capabilities**:
  - `getCurrentUserIdentity() → AuthIdentity?` — read-only, для AI «понять, кто пользователь» (например, отвечать «Привет, Анна»).
  - **Никаких** `signIn(...)`, `signOut(...)`, `getRefreshToken(...)`, `currentSession()` capabilities. AI agent **не должен** инициировать Sign-In или иметь доступ к токенам.
  - **`getProviderKind()` capability отсутствует** — `providerKind` удалён из доменной модели per clarification Q4 (2026-06-18). AI agent не должен знать, через какой провайдер залогинен пользователь — это adapter-internal detail.
- **Required affordances on data**: read-only `email`, `displayName`. **Запрет** на чтение `refreshToken`, `extra` (Firebase JWT).
- **Provider-agnostic shape**: `AuthIdentity` уже vendor-agnostic (per FR-007). No Gemini/OpenAI/Claude/MCP типы в signatures (per CLAUDE.md rule 1).
- **Out of scope for this spec**: no provider implementation, no LLM prompt design, no AI capability exposure surface — это ship'ится в F-2 (отложен Phase 3+, per memory `project_deferred_cloud_architecture`).
- **Tamper consideration**: even read-only capability exposure MUST NOT leak server-validated entitlement JWT to AI context (per decision 03 §«Защита от взлома»).

## OEM Matrix *(mandatory if feature touches device behavior)*

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---------------|------------------|-------------------------|---------------------|
| Stock Android (Pixel) | baseline; Credential Manager API works natively | — | emulator `pixel_5_api_34` |
| Samsung One UI | Credential Manager parity with stock; **Samsung Pass** не используется (per decision 2026-05-30/08 — Credential Manager рекомендованный путь) | Adapter использует только Credential Manager API, не Samsung-specific | `TODO(physical-device): Samsung S-series Sign-In smoke` |
| Xiaomi MIUI | Aggressive background cleanup может убить EncryptedSharedPreferences cache → adapter reads from disk on next access, нет проблемы | Wire-format roundtrip test ловит corrupted blob handling (FR-023) | `TODO(physical-device): MIUI cleanup soak test` |
| Huawei EMUI (без GMS) | **No Google Play Services** → Credential Manager Google provider недоступен | `AuthAdapterSelector` детектит, возвращает `NoSupportedAuthProvider` error; app продолжает работать в **local mode forever** | `TODO(physical-device): Huawei P-series без GMS — capability detect` |
| Android TV / Google TV | Часто **нет lock screen** + remote-only input → Credential Manager UX может быть неудобен | Setup через admin Standard mode на phone, делегация на TV (per decision 2026-05-30/07); F-4 само технически работает на ATV если есть GMS | `TODO(physical-device): ATV emulator `tv_4k_api_34` cold sign-in` |
| Android 13+ | Credential Manager — recommended | Adapter использует Credential Manager (FR-014) | emulator `pixel_5_api_34` (API 34) |
| Android 11-12 | Credential Manager backported через AndroidX | Adapter использует `androidx.credentials:credentials` | `TODO(physical-device): Android 11 device smoke` |

---

## Статус по «Открытым вопросам» — все закрыты в clarify pass 2026-06-18

Pre-formed list из 5 вопросов перед clarify-pass был **переразвёрнут** во время mentor-session 2026-06-18. Финальный статус:

1. **`SessionRecord.extra` visibility** → закрыт clarification Q2: `SessionRecord` целиком **internal**; consumer'ы работают только через `AuthIdentity` (см. FR-012, FR-013). `AuthorizedRequestSigner` для будущих RPC — отдельный port в S-8 territory (inline TODO).
2. **`stableId` стабильность через cutover** → закрыт clarification Q1: **наш собственный UUID** + `/identity-links/...` Firestore map (см. FR-007, FR-016a). Country-ban exit ramp inline TODO в `GoogleSignInAuthAdapter`. `SRV-AUTH-IDENTITY-001` в server-roadmap.
3. **`SubscriptionState.Unknown` vs `LocalOnly`** → MVP всегда `Unknown` (см. FR-011). Семантика `LocalOnly` зарезервирована для post-MVP billing spec и не используется в F-4. Cloud-feature gating — server-validated JWT, не клиентский флаг.
4. **`refreshToken` encryption tier** → EncryptedSharedPreferences для MVP (см. FR-020). Inline TODO для будущего перехода на `SecureKeyStore` из F-CRYPTO когда F-5 готов — additive change, не rewrite.
5. **Sign-In retry policy** → **не retry автоматически**. `AuthError.NetworkError` возвращается в UI; UI решает (per decision 03 §«никакого forced engagement»).

### Новые вопросы, найденные при свежем чтении (закрыты в той же сессии)

6. **`ProviderKind` shape** → **удалён из домена полностью** (clarification Q4). Adapter знает свой kind у себя.
7. **explainer-prompt ownership / CloudFeatureGate** → **отброшен как ошибочный** (clarification Q5). Cloud-feature кнопок не существует. Sign-in запускается из wizard screen 2 (один путь) и из reusable composable `SignInTrigger` (см. FR-033).
8. **signOut семантика** → закрыт clarification Q3: stop syncing, локальный кеш как рабочее состояние (см. FR-006).
9. **device health observability** → закрыт clarification Q8: F-4 идентифицирует устройство, S-9 Phone Health Monitoring строит health-report flow (см. FR-034).

**Все вопросы закрыты.** Спека готова к `/speckit.plan` после прохождения чек-листов (Step 5 clarify).

---

## Связанные документы

- **Decisions 2026-05-30** (9 файлов в `docs/product/decisions/2026-05-30-f4-identity/`) — переосмысление identity модели.
- **Decisions 2026-06-15** (deferred-cloud, файлы 01 + 03) — deferred Sign-In + billing cloud-only.
- **Roadmap** §F-4 — обновлённый scope с provider-agnostic требованием.
- **Use cases** `docs/product/use-cases/05-pairing-identity-trust.md` §Identity (D-Pair-1).
- **Backlog** `docs/dev/project-backlog.md` §AUTH-001.
- **Server-roadmap** `docs/dev/server-roadmap.md`:
  - `SRV-AUTH-001` (own JWT issuer post-cutover).
  - `SRV-AUTH-IDENTITY-001` (нов., добавить: identity-links collection мигрирует **первой** на own-server, она authoritative source of truth маппинга providerAccountId → stableId; UUID stableId не меняется).
- **Memory**:
  - `project_auth_provider_architecture.md` — F-4 mega-block context (старая модель, скорректирована).
  - `project_deferred_cloud_architecture.md` — переопределяет 2026-05-30 в части activation timing.
  - `project_decisions_2026_05_30.md` — 9 файлов решений.
  - `project_mvp_phase_split.md` — Phase 2 порядок спек, S-9 Phone Health Monitoring как обязательная.
- **CLAUDE.md** — rules 1 (domain isolation), 2 (ACL), 3 (one-way doors + exit ramps), 4 (MVA), 5 (wire-format), 6 (mock-first), 8 (server migration tracking).
- **Cross-references на consumer-спеки** (ещё не написаны, но архитектурно опираются на F-4):
  - **F-5 ConfigCipher**: использует `AuthIdentity.stableId` (UUID) как seed для key derivation. F-4 не зависит от F-5.
  - **S-2 Admin App + QR Pairing**: использует `stableId` как owner UID в delegation document.
  - **S-6 Account Deletion**: будущий `AuthProvider.deleteAccount()` API + cascade удаление identity-link записи. Sign-out ≠ delete (per clarification Q3).
  - **S-8 VersionedConfigViewer + Config Sync**: подписан на `currentUser` flow, сам забирает конфиг с сервера, применяет migrators, мержит, пушит. F-4 этого не знает.
  - **S-9 Phone Health Monitoring**: подписан на `currentUser` для идентификации устройства; **строит health-report flow**, который периодически проверяет состояние Android-настроек (ROLE_HOME, runtime permissions, wizard completion) и **оповещает admin'а** удалённо когда настройки сбиты. F-4 не строит health-report, только идентифицирует устройство (per clarification Q8).
- **F-CRYPTO** (spec 016) — primitives для будущей F-5 derivation; F-4 не зависит от F-CRYPTO во время выполнения. После F-5 merge — inline TODO миграции `EncryptedLocalSessionStore` с EncryptedSharedPreferences на `SecureKeyStore` из F-CRYPTO (additive change).
- **F-3 wizard** (spec 015) — local-mode predecessor, должен быть merged до F-4. F-3 территория: wizard screen 2 двухкнопочный UI, recovery flow рекalibriрование шагов после восстановления конфига.

---

## TL;DR для не-разработчика

**Что строим**: «дверь входа в аккаунт» (`AuthProvider`) — программный интерфейс, через который app узнаёт, кто пользователь. Сейчас за этой дверью стоит **только Google Sign-In**, но дверь сделана так, что в будущем можно поставить за ней любую другую авторизацию (по телефону, по email, через Apple, или вход через наш собственный сервер) **без переделки** всего, что от двери зависит.

**Когда дверь открывается** (clarified 2026-06-18): **не** при первом запуске app. App работает локально (плитки, контакты, темы) **бесплатно бессрочно**. Дверь открывается **только** в двух местах:
1. В wizard на втором экране — «Войти в Google для восстановления настроек».
2. Через отдельный переиспользуемый компонент `SignInTrigger`, который сейчас дёргает wizard, а в будущем сможет дёрнуть любой другой UI (например, кнопка «Войти» в Settings).

«Кнопок типа получить фото от внуков», которые требуют sign-in при первом тапе, **не существует**. Launcher не знает про cloud features — он рендерит плитки из конфига, а конфиг либо локальный, либо синхронизируется с сервером после sign-in отдельным сервисом.

**Чей идентификатор внутри** (clarified 2026-06-18): **наш собственный UUID**, не Firebase UID и не Google account ID. Маппинг «Google account → наш UUID» живёт в отдельной Firestore таблице. Это означает: завтра в стране запретят Google → мы добавляем вход по телефону через тот же UUID, никаких миграций. После переезда на собственный сервер UUID **не меняется** — все данные продолжают работать.

**Что значит «Выйти»** (clarified 2026-06-18): останавливает синхронизацию с сервером. **Локальные данные** (плитки, контакты, темы) **остаются на месте** — пользователь работает дальше как обычно. При повторном «Войти» — стандартный merge с сервером (сервер приоритетнее). «Полное удаление аккаунта» — это отдельная фича S-6 (не sign-out).

**Что F-4 НЕ делает** (clarified 2026-06-18): после sign-in F-4 **только** сохраняет identity и оповещает остальные системы через event. F-4 **не** забирает конфиг с сервера, **не** мержит конфликты, **не** регистрирует push, **не** проверяет настройки Android. Эти системы (config-sync S-8, push-registration, S-9 health monitoring) подписаны на event «authenticated» и работают независимо. F-4 их даже не знает по имени.

**Что НЕ строим** в F-4: вход по телефону, email, Apple, через собственный сервер. Это всё — будущие отдельные спецификации, и они **встанут на место** нашей двери без переделки. Платный billing — тоже не сейчас (только заглушка). UI настроек Android (ROLE_HOME) — wizard проверяет runtime, в конфиг не уходит; но **состояние** Android-настроек уходит на сервер как health-report (S-9) для admin'а, чтобы он удалённо видел, что у бабушки приложение работает не так как нужно.

**Главный архитектурный приз**: если завтра Google или Firebase «исчезнут» — мы переписываем **один файл** (adapter), а не весь identity-слой. Это применение правила CLAUDE.md №2 («ACL для каждой внешней зависимости») к авторизации.

**Тест успеха**: разработчик F-5 (шифрование конфига) и S-2 (связка устройств) пишут свой код **не зная**, что под капотом Google или Firebase — они работают только через абстрактный `AuthProvider`. Это проверяется автоматически (Detekt-правила + provider-swap test).
