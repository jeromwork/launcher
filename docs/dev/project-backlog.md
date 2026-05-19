# Project Backlog — Operational TODOs

Рекомендации и задачи **не блокирующие** текущую разработку, но необходимые для production-readiness, security hygiene, или эволюции feature'ов.

**Назначение этого файла**: накапливать «нужно когда-нибудь сделать» решения, чтобы они не терялись между сессиями и спеками. Сюда попадают:
- Operational рекомендации (2FA, key rotation, monitoring setup).
- Architectural exit ramps (когда мигрировать с Spark→Blaze, workers.dev→свой домен, etc.).
- Hardening и улучшения, выявленные в `/speckit.analyze` или ревью.
- Future features из roadmap.md которые ещё не привязаны к конкретному спеку.

**Не добавлять сюда**: бытовые баги (→ GitHub Issues), задачи в рамках активного спека (→ соответствующий `tasks.md`).

## Status legend

- 🔴 **Critical** — выполнить как можно скорее; риск безопасности или production-fail.
- 🟡 **Important** — обязательно до production-релиза.
- 🟢 **Nice-to-have** — когда появится потребность / время.
- ✅ **Done** — дата закрытия + reference на коммит/спек.

---

## Security & Operations

### TODO-OPS-001: Включить 2FA на Cloudflare account 🔴

- **What**: Two-Factor Authentication для `gpt1.jeromwork@gmail.com` на Cloudflare.
- **Why**: В Cloudflare Secrets хранится Firebase service-account JSON. Если злоумышленник получит доступ к аккаунту → может (а) подменить Worker на вредоносный, рассылающий спам-push'и нашим пользователям; (б) скачать service-account JSON; (в) использовать service-account для прямой отправки FCM-push'ей или Firestore-операций.
- **How**: Cloudflare Dashboard → My Profile → Authentication → Two-Factor Authentication → Set up (любой метод: TOTP-app или Security Key).
- **When**: до начала Phase 5 (Worker deploy) спека 007 идеально; не позднее production-релиза.
- **Status**: 🔴 OPEN
- **Origin**: `/speckit.analyze` 2026-05-11 recommendation #1.

### TODO-OPS-002: Включить 2FA на Firebase / Google account 🔴

- **What**: Two-Factor Authentication для `g.jeromwork@gmail.com` (owner Firebase project `launcher-old-dev`).
- **Why**: Owner Firebase project имеет полный контроль — может изменить Security Rules (открыть данные), удалить project, изменить billing. Если учётка скомпрометирована — наши пользователи теряют доступ к данным или получают подменённый конфиг.
- **How**: Google Account → Security → 2-Step Verification → Get Started.
- **When**: до начала Phase 5 идеально.
- **Status**: 🔴 OPEN
- **Origin**: `/speckit.analyze` 2026-05-11 recommendation #2.

### TODO-OPS-003: Rotate Firebase service-account JSON ✅ DONE 2026-05-11

- **Was**: Сгенерировать **новый** service-account private key в Firebase Console и обновить Cloudflare Secret.
- **Why**: 2026-05-11 текущий JSON был передан project owner'ом через chat-сообщение → ключ должен считаться potentially-compromised.
- **Resolution (2026-05-11)**:
  1. ✅ Project owner сгенерировал новый key в Firebase Console (key ID `ca55aa1f09330398cda45909fc4be92c4d03b73a`).
  2. ✅ Загружен в Cloudflare Secrets как `FIREBASE_SA_JSON` через `Get-Content sa.json -Raw | wrangler secret put` (из локального файла, не через chat).
  3. ✅ Локальный файл удалён project owner'ом.
  4. ✅ Старый key (`bf6c8bdb724bf37cc3e650aa33a9c208b3f4acd9`) удалён из Firebase через IAM REST API (`DELETE https://iam.googleapis.com/v1/projects/launcher-old-dev/serviceAccounts/firebase-adminsdk-fbsvc@launcher-old-dev.iam.gserviceaccount.com/keys/...`).
  5. ✅ Verified: только 2 ключа остались (новый user-managed + Google system-managed).

**Lesson learned (process)**: secrets никогда не передавать через chat. Saved as memory `feedback_secret_handling.md`.

**Bonus learning**: Firebase IAM REST API (`https://iam.googleapis.com/v1/...`) **работает** для key management операций (list, delete) через firebase-tools refresh-token + OAuth flow. Это можно использовать в будущем для CI/CD ключевой ротации. Saved in memory.

### TODO-OPS-004: Production Firebase project (отдельный от dev) 🟡

- **What**: Создать `launcher-old-prod` Firebase project, отдельный от `launcher-old-dev`.
- **Why**: Dev и prod на одном проекте — opasно (тесты могут зацепить prod-данные). Industry standard — разделять.
- **How**:
  1. Firebase Console → Add project → `launcher-old-prod`.
  2. Регистрировать Android app `com.launcher.app` с production signing key.
  3. Production `google-services.json` хранить в CI secret (не в репо — отличие от dev).
  4. Production Cloudflare deployment — отдельный `wrangler.toml` или environment.
- **When**: До первого production-релиза в Google Play.
- **Status**: 🟡 OPEN
- **Origin**: Spec 007 §Assumptions; analyze-report.md.

### TODO-OPS-005: Production Cloudflare deployment 🟡

- **What**: Production Worker (отдельно от dev `*.workers.dev`).
- **Why**: Dev Worker связан с dev Firebase project; production Worker — с production Firebase project. Должны быть изолированы.
- **How**: `wrangler.toml` environments или отдельный subaccount. Production Worker хранит production service-account JSON.
- **When**: До первого production-релиза.
- **Status**: 🟡 OPEN
- **Origin**: Spec 007 §Assumptions.

---

## Architecture Exit Ramps

### TODO-ARCH-001: Custom domain для Cloudflare Worker (`*.workers.dev` → собственный) 🟢

- **What**: Переехать с `launcher-push.<account>.workers.dev` на `push.<our-domain>`.
- **Why**: Bare-URL `*.workers.dev` непрезентабельный, привязан к личному аккаунту. При смене ownership проекта домен «прирос» к старому owner'у.
- **How**: См. `push-worker/README.md` §Migration to custom domain. Шаги: купить домен (~$10/год) → Cloudflare Zone → DNS → routes в wrangler.toml → deploy → обновить `WORKER_BASE_URL` env в admin-app build config.
- **When**: До первого public release.
- **Status**: 🟢 OPEN
- **Origin**: Spec 007 C10 = A (workers.dev для MVP); OWD-6 exit ramp.

### TODO-ARCH-002: Cloudflare KV для accurate rate-limiting 🟢

- **What**: Заменить in-memory rate-limit Worker'а на Cloudflare KV.
- **Why**: In-memory счётчик сбрасывается при перезапуске Worker-instance (бывает часто). Точный rate-limit нужен когда трафик растёт или появляются атаки.
- **How**: `push-worker/README.md` §Adding KV namespace. `wrangler kv namespace create RATE_LIMIT` → binding в `wrangler.toml` → заменить in-memory Map в `push-worker/src/rate-limit.ts` на KV API.
- **When**: Когда daily req на endpoint >1000 или обнаружены попытки abuse.
- **Status**: 🟢 OPEN
- **Origin**: Spec 007 C12 = A (in-memory для MVP).

### TODO-ARCH-003: Firebase Blaze plan upgrade (если нужны Cloud Functions / server-cron) 🟢

- **What**: Апгрейд `launcher-old-dev` (или production) на Blaze pay-as-you-go.
- **Why**: Cloud Functions, cron jobs, server-side data validation — все требуют Blaze. Если когда-нибудь захотим:
  - Cron-чистку expired `/pairings/{token}`.
  - Server-side spam detection.
  - Cloud Function-trigger при write в Firestore (например для analytics).
- **How**: Firebase Console → Billing → Modify plan → Blaze. Привязать карту. Установить **budget alert** ($5/month) чтобы не было сюрпризов.
- **When**: Когда появится конкретная потребность.
- **Status**: 🟢 OPEN
- **Origin**: Spec 007 OWD-1 exit ramp.

### TODO-ARCH-004: Named Firebase Auth (Google Sign-In / Phone) 🟢

- **What**: Переход с anonymous auth на named auth для admin-устройств.
- **Why**: При reinstall app — admin теряет linkId (нужно новый pairing). С named auth — `linkWithCredential` сохраняет identity между устройствами.
- **How**: Firebase Console → Authentication → Sign-in method → enable Google / Phone. В app — добавить login flow. Migration: для existing anonymous users — `linkWithCredential` сохраняет старый UID.
- **When**: При появлении real users которые жалуются на потерю pairing при reinstall.
- **Status**: 🟢 OPEN
- **Origin**: Spec 007 OWD-2 exit ramp.

### TODO-ARCH-005: Non-GMS device support (WorkManager polling) 🟢

- **What**: Реализовать periodic polling 15 минут для устройств без Google Play Services (Huawei post-2019).
- **Why**: Сейчас C13 = stub (только UI banner «уведомления недоступны»). Реальные пользователи без GMS отрезаны.
- **How**: Добавить `androidx.work:work-runtime-ktx`; `WorkManager.enqueuePeriodic` workRequest 15min interval; в worker — `backend.readDoc(LinkConfig(linkId))` + dispatch на PushReceiver.
- **When**: При появлении реальных пользователей с GMS-less устройствами.
- **Status**: 🟢 OPEN
- **Origin**: Spec 007 C13 = C stub.

### TODO-ARCH-006: Enable R8 minification on `release` buildType 🟡 🚨 PLAY-STORE-BLOCKER

- **What**: Включить `isMinifyEnabled = true` + `isShrinkResources = true` для `release` buildType в `app/build.gradle.kts`.
- **Why**: spec 007 SC-006 «realBackend APK ≤ mockBackend + 3 MB» сейчас **fails by 0.99 MB** (delta 3.99 MB SI, target 3.00 MB). Firebase Firestore/Auth/Messaging SDKs тянут много кода, который R8 ужмёт на 40-60%. Без минификации SC-006 нарушено постоянно.
- **How**: См. `specs/007-pairing-and-firebase-channel/perf-checkpoint.md` §SC-006 exit ramp. Снэпет:
  ```kotlin
  buildTypes {
      release {
          isMinifyEnabled = true
          isShrinkResources = true
          proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      }
  }
  ```
  Firebase shipping consumer ProGuard rules автоматически. Validation gate: пересобрать оба flavor'а release, пересчитать delta, обновить таблицу в perf-checkpoint.md.
- **When**: До первого signed release / Play upload. Two-way door — легко откатить если ломает Firebase reflection.
- **Status**: 🟡 OPEN (one-time follow-up for spec 007 ship-readiness).
- **Origin**: Spec 007 T108 measurement; SC-006 fail.

### TODO-ARCH-007: App version compatibility management (вынесено из 008) 🟡

- **What**: Реализовать отдельный спек `app-version-compatibility` (см. roadmap §Backlog) — detection несовместимых версий приложения admin↔Managed, поля `requiredManagedAppVersion`/`managedAppVersion`/`compatibilityError`, visibility на admin UI, remote-update mechanism.
- **Why**: В спеке 008 (Q4 clarify, 2026-05-14) решено протестировать collaborative-edit монорелизом — все editor'ы одной версии, schema mismatch by construction не возникает. Но как только мы пойдём в реальные обновления (часть пользователей на v1, часть на v2) — admin v2 пушит `/config` с новыми полями, Managed v1 не понимает. Это **обязательно** к реализации до первого update'а после релиза, иначе бабушкины телефоны получат частично-применённый или сломанный конфиг.
- **How**:
  - В 008 wire format уже стабилизирован — добавление полей будет additive (не bump schemaVersion).
  - Спек должен покрыть: detection (Managed читает schemaVersion, понимает или нет), reject-behavior (last-applied остаётся), `/state.compatibilityError`, visibility на admin UI (значок + детали), Security Rules (write `requiredManagedAppVersion` только adminId), remote update mechanism (Play Store update intent / выбор версии / force-install).
  - One-way door: UX «admin удалённо ставит версию приложения» — пользователь привыкает.
- **When**: До первого update'а после production-релиза 008 (т.е. ещё до того, как у части пользователей появятся разные версии).
- **Status**: 🟡 OPEN
- **Origin**: spec 008 `/speckit.clarify` 2026-05-14 Q4 — вынесено отдельным спеком из соображений объёма (Play Store update flows + OEM-варианты = самостоятельная глубина).

### TODO-ARCH-008: Config history + rollback (in progress — spec 009 FR-37..FR-46) 🟡 IN PROGRESS

- **What**: Реализовать в spec 009 (`admin-mode-flows`) подсистему истории конфигов и отката: subcollection `/links/{linkId}/config/history/{autoId}` с retention 10 версий + UI просмотра/предпросмотра/отката.
- **Status**: 🟡 IN PROGRESS — scope зафиксирован 2026-05-15 в mentor pre-specify session спека 9. FR-37..FR-46 в `specs/009-admin-mode-flows/spec.md` (когда будет написан).
- **Decisions taken (2026-05-15):**
  - Write strategy: **client-side** перед push в `/config/current` (без batch transaction; race condition rare loss принимаем). Migration на server-side → `SRV-CONFIG-001` в [server-roadmap.md](server-roadmap.md).
  - Housekeeping: **client-side** при каждом push (читает all snapshots → удаляет старейшие при ≥11). Migration → `SRV-CONFIG-002`.
  - Schema mismatch при rollback: **lazy transformer** `vN → vCurrent` (см. TODO-ARCH-015). При `schemaVersion = 1` (сейчас) — работы 0.
  - Editor symmetry: **оба** editor'a (admin + Managed через Settings 7-tap+пароль) могут откатывать. Симметрично спеку 8 FR-050.
  - UI: список snapshot'ов в редакторе раскладки → тап → read-only preview → кнопка «откатить» (= новый push содержимого).
  - Security Rules: read history — adminId + managedDeviceFirebaseUid (как current); write — те же (client-side; migration на server-only через `SRV-CONFIG-001`).
- **Origin**: spec 008 `/speckit.clarify` 2026-05-14 Q7. Scope-discovery 2026-05-15 mentor session для спека 9.

### TODO-ARCH-009: Config size soft-limits and proactive warnings 🟢

- **What**: Ввести client-side soft-limit на размер `/config` (например, 500 KiB = 50% от Firestore 1 MiB hard-limit) с проактивным баннером при ~80% заполнении и блокировкой save при превышении soft-limit. Сообщения на простом русском («ваш конфиг становится большим, удалите что-нибудь или вынесите фото в облако»).
- **Why**: В спеке 008 (Q10 clarify, 2026-05-14) решено НЕ вводить soft-limits — при типичном использовании (30-50 контактов) до 1 MiB далеко, и спек 011 (`contacts-and-e2e-encrypted-media`) выносит фото в Firebase Storage, что снимает основной риск. Однако: если по каким-то причинам спек 011 задержится, либо у юзера экзотический кейс (много контактов с большими подписями), он может упереться в Firestore `INVALID_ARGUMENT` без понятного объяснения. Soft-limit + баннер — UX-страховка.
- **How**:
  - Измерение размера через serialize-and-count перед save локально (Kotlin Serialization → ByteArray → size).
  - Banner в Settings при ~80% заполнении: «конфиг занимает 400 KiB из 500 KiB; уберите медиа или контакты».
  - Hard-block save при превышении soft-limit (500 KiB) с понятным сообщением.
  - Никаких изменений wire-format — это чисто client-side проверка.
- **When**: 🟢 Nice-to-have. Если в реальной эксплуатации увидим достижение 1 MiB → повышаем приоритет. До этого момента — общий error-path FR-013 спека 008 покрывает.
- **Status**: 🟢 OPEN
- **Origin**: spec 008 `/speckit.clarify` 2026-05-14 Q10. В 008 явно отложено (OUT-008), сохраняем здесь как страховка.

### TODO-ARCH-010: Phone health threshold editor 🟢

- **What**: UI в admin-Settings для редактирования полей `PhoneHealthPreset` (например, «battery critical at X%»).
- **Why**: В спеке 9 значения `PhoneHealthPreset` захардкожены (battery `<5%` Critical / `<20%` Warning, lastSeen `>24ч` / `>1ч`). Один админ хочет реагировать на 10%, другой на 3%. Пресет уже data-class'ом, готов к замене значений — нужно только UI и подгрузка из `/config.presetOverrides.phoneHealthSettings`.
- **How**: Форма редактирования в Settings → wire format `PhoneHealthSettings` внутри `presetOverrides: PresetSettings?` (additive, без bump schemaVersion) → adapter подгружает из `/config`, fallback на `DEFAULT_PHONE_HEALTH_PRESET`.
- **When**: При первой жалобе пользователя «у моей бабушки экзотический сценарий, дефолты не подходят».
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery (пункт 2 роадмапа, mentor-сессия 2026-05-15). Структура (`PhoneHealthPreset`) уже готова в спеке 9 — расширение чистое дописывание.

### TODO-ARCH-011: Phone health named presets (default / medical / minimum / ...) 🟢

- **What**: Несколько готовых наборов `PhoneHealthPreset` + UI выбора, какой применить к конкретному Managed.
- **Why**: Дефолтный preset подходит обычной бабушке. Для медицински-уязвимых сценариев (кардиостимулятор, диабет) пороги battery / lastSeen агрессивнее. Возможны minimum / maximum / silent profiles.
- **How**: Constants `DEFAULT_`, `MEDICAL_`, `MINIMUM_` рядом с data class. UI selector в admin-режиме per-Managed. Запись `presetId` health-настроек в `/config.presetOverrides.phoneHealthPresetId`.
- **When**: После TODO-ARCH-010 или вместе с ним.
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery.

### TODO-ARCH-012: Phone health critical → push admin 🟡

- **What**: Подписчик на локальное событие `PhoneHealthCriticalEvent` (эмитится в спеке 9), который через Cloudflare Worker (спек 7) отправляет FCM push на телефон админа.
- **Why**: Без push админ узнаёт о Critical health (3% батарея, 24ч без выхода, выключенный звонок) только когда заглянет в приложение. Если бабушка в опасности — это слишком поздно. В спеке 9 событие **эмитится** (поле `pushAdminOnCritical: Boolean` в `PhoneHealthPreset` готово), но подписчик отсутствует.
- **How**: Сервис в admin-приложении подписан на `PhoneHealthCriticalEvent`. При срабатывании + `preset.pushAdminOnCritical == true` → POST в Worker → FCM-push админу. Дедупликация (один push на инцидент, не на каждый snapshot). Уважение к DND админа.
- **When**: Обязательно до production-релиза с реальными пожилыми пользователями. 🟡.
- **Status**: 🟡 OPEN
- **Origin**: spec 009 pre-specify discovery (пункт 2 роадмапа).

### TODO-ARCH-013: Contact drift detection 🟢

- **What**: Авто-детект расхождения между системными контактами админа и контактами в `/config.contacts[]`. Если у Маши в системе номер поменялся — баннер «обновить?».
- **Why**: В спеке 9 контакты — снапшоты (FR-30): копия имя+номер на момент добавления. Если у Маши поменялся номер, в `/config` бабушки останется старый, она наберёт чужого человека. Сейчас (по решению Q5 спека 9) — игнорируем, админ обновляет вручную.
- **How**: Раз в N дней (например при запуске admin-приложения) — для каждого `Contact` в `/config.contacts[]` сверить с локальной адресной книгой админа по `displayName`. При расхождении — баннер с предложением «обновить номер».
- **When**: При появлении реальных жалоб «бабушка набирает чужой номер».
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery (пункт 4 роадмапа, Q5).

### TODO-ARCH-014: Contact без phone number 🟢

- **What**: Расширить `Contact` для поддержки идентификаторов **без** phone number — например LINE ID, WeChat ID, Telegram username, email.
- **Why**: В спеке 9 контакты без phone отвергаются (FR-36). Это закрывает закрытые азиатские мессенджеры (LINE / WeChat / KakaoTalk), у friend'ов которых нет публичного phone number. Когда будем интегрировать их (`TODO-FUTURE-SPEC-003`) — понадобится контакт **без** phone, только с messenger-specific ID.
- **How**: Additive поля в `Contact` (без bump schemaVersion): `lineId: String?`, `wechatId: String?`, `telegramUsername: String?`, или generic `messengerIdentifiers: Map<String, String>?`. Slot.kind расширяется на `LineCall`, `WeChatCall`, ... через провайдеры спека 6.
- **When**: Связан с `TODO-FUTURE-SPEC-003: messenger-contact-integration` — делать вместе.
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery (пункт 4 роадмапа, FR-25/FR-36).

### TODO-ARCH-016: Switch launcher home tiles to render from `/config/current` 🟡

- **What**: Сейчас launcher (`HomeComponent` → `FlowScreen` → `TileCard`) рендерит тайлы из локального `FlowRepository` (FlowDescriptor + SlotDescriptor — спек 003/005 модель). После спеков 008/009 источник истины для раскладки — `/config/current` ConfigDocument (Flow + Slot — спек 008 модель). Эти два деревья пока **независимы** — admin editor правит `ConfigDocument`, но launcher это не подхватывает.
- **Why**: Без миграции:
  - Admin editor «работает в вакууме» — push в Firestore проходит, history заполняется, но раскладка у пожилого пользователя не меняется (он всё ещё видит FlowRepository defaults / spec 005 mock data).
  - Preview-tap в editor View mode не запускает реальное действие (FR-005 не выполняется) — slot→Action mapping не существует, потому что Action живёт в FlowDescriptor, а editor работает с ConfigDocument.Slot.
- **How**:
  1. Добавить SlotToActionMapper в `core/commonMain/api/action/`: `fun Slot.toAction(contacts: List<Contact>): Action?` — мапит SlotKind+args на Action+payload.
  2. Заменить `FlowRepository` в HomeComponent на observable adapter поверх `ConfigEditor.appliedConfig` + `ConfigEditor.pendingDraft` (с draft-приоритетом для admin device, applied-only для Managed).
  3. Очистить mock `flows_mock_*.json` после переноса (spec 005 артефакты).
  4. Обновить spec 009 Phase 14 emulator smoke — admin push → Managed home reflects change.
- **When**: После того как пользователи начинают редактировать раскладку (т.е. сразу — без этого спек 9 функционально incomplete для real users). До Play Store upload — обязательно.
- **Status**: 🟡 OPEN
- **Origin**: spec 009 Phase G implementation 2026-05-16 — discovered when wiring EditorScreen preview-tap (FR-005).

### TODO-ARCH-015: Config schema transformers (lazy migration) 🟢

- **What**: При каждом breaking schema bump для `/config` (`schemaVersion: N → N+1`) — написать **транзформер** `vN → vN+1`, который применяется при чтении старых snapshot'ов из `/config/history/`. Цепочка транзформеров покрывает rollback на любую старую версию (`v1 → v2 → ... → current`).
- **Why**: Без транзформеров spec 9 FR-44 ведёт к (А) drop-incompatible — старые snapshot'ы при schema bump становятся неоткатываемы. Это **обнуляет историю** при каждом breaking change. CLAUDE.md rule 5 требует «backward-compatible reads MUST be possible for at least one major release» — это и есть транзформер.
- **How**:
  - Каждый транзформер — pure function `JsonObject (vN) → JsonObject (vN+1)`. Расположение: `core/api/config/migrations/Vn_To_Vn1.kt`.
  - При rollback читаем snapshot'a `schemaVersion = N`, текущий код — `schemaVersion = M`, где `M > N`. Применяем `Vn_To_Vn1`, потом `V(n+1)_To_V(n+2)`, ..., до `V(m-1)_To_Vm`. Результат отдаётся в rollback flow.
  - Roundtrip-тест на каждый транзформер: «синтетический snapshot vN → транзформ до vM → проверка инвариантов».
  - Server-side eager миграция — `SRV-CONFIG-003` в [server-roadmap.md](server-roadmap.md).
- **When**: При **первом** breaking schema change `schemaVersion: 1 → 2`. До этого момента — работы 0 (вся история в `v1`).
- **Status**: 🟢 OPEN (триггер: первый schema bump)
- **Origin**: spec 009 pre-specify discovery (пункт 6 роадмапа, schema invalidation Q).

---

## Security Hardening

### TODO-SEC-001: Firebase App Check 🟡

- **What**: Защита от поддельных приложений и спама.
- **Why**: Anyone can decompile our APK, see Worker URL, get Firebase ID-token via anonymous auth, and try to spam. App Check добавляет «доказательство что запрос идёт из настоящего нашего приложения» (Play Integrity на Android).
- **How**: Firebase Console → App Check → enable for Firestore + Auth. Подключить Play Integrity API. Worker валидирует App Check token (header `X-Firebase-AppCheck`).
- **When**: При появлении реальных пользователей; до первого public release.
- **Status**: 🟡 OPEN
- **Origin**: `/speckit.analyze` 2026-05-11 — security informational note.

### TODO-SEC-002: Rate-limit per uid (а не только per linkId) 🟢

- **What**: Worker rate-limit учитывает Firebase Auth uid, не только linkId. Защита от compromised account spamming множества linkId'ов.
- **Why**: Сейчас rate-limit per linkId. Если admin-аккаунт скомпрометирован — может слать push на каждый из своих linkId'ов до лимита; в сумме гораздо больше.
- **How**: Добавить in-memory Map<uid, timestamps> в `push-worker/src/rate-limit.ts` параллельно с per-linkId.
- **When**: Когда появятся реальные admin-аккаунты с >1 paired устройством.
- **Status**: 🟢 OPEN
- **Origin**: Spec 007 C11 = A research note.

### TODO-SEC-003: Audit logging для critical operations 🟢

- **What**: Лог критических действий (claim transaction, revoke, push notify) в отдельную коллекцию `/audit/{eventId}` с ограниченным retention.
- **Why**: Compliance + forensics. Сейчас C4 = hard-delete без tombstone — невозможно ответить «кто и когда отвязал устройство».
- **How**: При важных Firestore writes — параллельно писать в `/audit/{eventId}` (separate Security Rules: только append, read только service-account, retention TTL).
- **When**: До B2B / compliance-sensitive use cases.
- **Status**: 🟢 OPEN
- **Origin**: Spec 007 C4 exit ramp.

---

## UX Improvements

### TODO-UX-001: Понятные сообщения ошибок (а не `BackendError.Offline`) 🟡

- **What**: Mapping `BackendError` / `PushError` → user-friendly Russian messages.
- **Why**: Бабушка не понимает «BackendError.Offline». Должна видеть «Нет подключения к интернету. Проверьте Wi-Fi или мобильную сеть».
- **How**: `app/.../ui/.../ErrorMessages.kt` — extension function `BackendError.toUserMessage(): String`. Включить в T091 + T092 спека 007.
- **When**: Phase 8 спека 007 (T091).
- **Status**: 🟡 OPEN
- **Origin**: `/speckit.analyze` elderly-friendly note 2.

### TODO-UX-002: Plurals для countdown timer 🟡

- **What**: Russian plural rules для «осталось N минут» (1 минута / 2 минуты / 5 минут).
- **Why**: Грамматическая корректность.
- **How**: `<plurals name="qr_countdown_minutes">` в strings.xml + `getQuantityString()`.
- **When**: Phase 8 спека 007 (T086 + T092).
- **Status**: 🟡 OPEN
- **Origin**: `/speckit.analyze` localization note.

### TODO-UX-003: Visual progress bar для QR countdown 🟢

- **What**: Линейный progress bar который заполняется от 5:00 до 0:00.
- **Why**: Визуальный сигнал «время уходит» лучше для бабушки чем чтение цифр.
- **How**: Compose `LinearProgressIndicator` с прогрессом `remainingMs.toFloat() / TOTAL_MS`.
- **When**: Phase 8 спека 007 (T086 enhancement).
- **Status**: 🟢 OPEN
- **Origin**: `/speckit.analyze` elderly-friendly note (QR countdown).

---

## Reliability / Resilience

### TODO-REL-001: FCM topic subscribe retry 🟡

- **What**: При `LinkRegistry.activate()` если `subscribeToTopic("link-{linkId}")` упало (network drop) — retry с exponential backoff + persistent flag.
- **Why**: Если activate прошёл, а subscribe не успел — пользователь не получит push при следующих изменениях, пока не откроет app в foreground.
- **How**: Добавить mini-task T057.1 в Phase 4 спека 007. Persistent flag в DataStore `pending_topic_subscriptions: List<String>`; retry при `onResume` или при network online event.
- **When**: Phase 4 спека 007 (small).
- **Status**: 🟡 OPEN
- **Origin**: `/speckit.analyze` failure-recovery note.

---

## Documentation / Process

### TODO-DOC-001: ADR-007 — QR-pairing as trust primitive 🟢

- **What**: Создать `docs/adr/ADR-007-qr-pairing-as-trust-primitive.md`.
- **Why**: Зафиксировать архитектурный pattern (поднятый project owner'ом 2026-05-11) что pairing — reusable primitive, не feature 007-only. Регламенты безопасности (TTL, alphabet, idempotency).
- **How**: ADR с разделами Context, Decision, Consequences, Migration policy.
- **When**: До или в начале спека 011 (`contacts-and-e2e-encrypted-media`) — там добавится второй subtype TrustEdgeBootstrap.
- **Status**: 🟢 OPEN
- **Origin**: Spec 007 plan.md §Reusable trust primitive; memory `project_qr_pairing_trust_primitive.md`.

### TODO-DOC-002: ADR-005/006 markdown links в spec 007 🟢

- **What**: В spec.md `US-3` — конвертировать bare-text "ADR-005/006" в markdown links.
- **Why**: Article XII §7 — context docs должны быть linked.
- **How**: Когда T006 спека 007 создаст ADR-006 — обновить spec.md US-3.
- **When**: После T006 (Phase 0 спека 007).
- **Status**: 🟢 OPEN
- **Origin**: `/speckit.analyze` cross-artifact-trace note 8.

---

## Spec 008 — device-dependent verification gaps

These are tracked here (not in spec 008's `tasks.md`) because they require either physical hardware, additional Gradle scaffolding, or a separate Firebase project — work outside the spec's own scope but needed before spec 008 can be declared production-ready.

### TODO-SMOKE-001: Wire `com.google.gms.google-services` plugin для realBackend flavor 🟡

- **What**: Применить плагин `com.google.gms.google-services` к app модулю с per-flavor config: `app/src/realBackend/google-services.json` для real Firebase, либо stub/skip для mockBackend.
- **Why**: Сейчас `app/google-services.json` лежит в корне модуля, но плагин не applied (явный `TODO(spec 007 Phase 4)` в `app/build.gradle.kts:27`). При запуске realBackend APK — `FirebaseApp.initializeApp()` падает с `Default FirebaseApp failed to initialize because no default options were found`, далее Koin не может создать `FirebaseFirestore` → `LauncherApplication` крашится. Это **блокер** для T143 manual smoke на эмуляторе/девайсе и для любого тестирования спека 008 с реальным Firebase.
- **How**:
  1. Переместить `app/google-services.json` → `app/src/realBackend/google-services.json`.
  2. Создать `app/src/mockBackend/google-services.json` с stub-схемой (package `com.launcher.app.mock`, fake project_id) — плагин требует валидный JSON для каждого flavor'а.
  3. Применить плагин в `app/build.gradle.kts`: `id("com.google.gms.google-services")` (alias уже есть в `gradle/libs.versions.toml:143`).
  4. Альтернативно: применить плагин условно через `androidComponents.onVariants(selector().withFlavor("backend" to "realBackend"))` — без stub mock-config'а.
  5. Verify: `assembleRealBackendDebug` встраивает Firebase config, app не падает на старте.
- **When**: Перед T143 manual smoke. Это технически унаследованный TODO спека 007, но фактически блокирует любую end-to-end проверку 008.
- **Status**: 🟡 OPEN
- **Origin**: 2026-05-15 emulator session — попытка запустить realBackend на Medium_Phone_API_36.1, FATAL EXCEPTION при старте `LauncherApplication`.

### TODO-SMOKE-002: Firebase Emulator Suite wiring for in-process app testing 🟢

- **What**: Hook в `LauncherApplication` (или DI module) для realBackend debug-build: при наличии env-флага `FIREBASE_EMULATOR_HOST=10.0.2.2` вызывать `FirebaseFirestore.useEmulator(host, 8080)` + `FirebaseAuth.useEmulator(host, 9099)` — чтобы app на эмуляторе ходил в локальный Firebase Emulator вместо реального dev-проекта.
- **Why**: Альтернатива TODO-SMOKE-001 для local-only тестирования: позволяет прогнать T143 (US-1..US-5) без загрязнения dev Firestore данными со смок-сессий, без необходимости создавать pairing tokens вручную, и без риска hit Firestore quotas. Особенно полезно для CI и для разработчиков без доступа к боевому Firebase project.
- **How**:
  1. В `LauncherApplication.onCreate()` (только debug variant): прочитать `BuildConfig.FIREBASE_EMULATOR_HOST` (если задан в `build.gradle.kts` через `buildConfigField`).
  2. Если задан — настроить эмуляторы для `FirebaseFirestore.getInstance()` и `FirebaseAuth.getInstance()`. **Важно**: вызывать ДО первой операции с этими сервисами, иначе ошибка.
  3. Документировать в `specs/008-bidirectional-config-sync/smoke/README.md` команду запуска: `firebase emulators:start --only firestore,auth --project demo-test`, затем `./gradlew :app:installRealBackendDebug -PfirebaseEmulator=10.0.2.2`.
  4. Exit ramp inline TODO: при переходе на named auth (TODO-AUTH-* из спека 007 backlog) — заменить wiring на per-environment config.
- **When**: 🟢 Nice-to-have. Полезно когда spec 008 entry-points стабилизируются и smoke регрессии станут регулярными.
- **Status**: 🟢 OPEN
- **Origin**: 2026-05-15 emulator session — обсуждение альтернатив для T143 без real Firebase.

### TODO-INSTRUMENT-001: Instrumented (`androidTest`) test scaffolding для T091/T095/Compose UI 🟡

- **What**: Создать `core/src/androidInstrumentedTest/` (KMP) или `core/src/androidTest/` (AGP) с тестами:
  - **T091**: `ConnectivityManagerNetworkAvailabilityIntegrationTest` — toggle airplane mode через UiAutomator или TestConnectivityManager → assert Flow эмитит.
  - **T095**: `ConfigRefreshWorkerIntegrationTest` — через `WorkManagerTestInitHelper` запустить worker → assert /config read + apply.
  - **Compose UI tests** для PendingBanner, MergeScreen, DiscardConfirmDialog — через `createAndroidComposeRule()` с реальным Context (Robolectric не работает с `stringResource()` в Compose Multiplatform — см. perf-checkpoint.md §«Compose UI tests»).
- **Why**: Сейчас `tasks.md` спека 008 заявляет T091/T095/T108 готовыми, но фактически:
  - Под `core/src/androidInstrumentedTest/` или `androidTest/` нет ни одного `.kt` файла.
  - `connectedMockBackendDebugAndroidTest` отрабатывает «0 tests» (verified 2026-05-15).
  - State-machine PushIndicatorPresenter/MergeResolver покрыты юнит-тестами, но **сама Composable UI** не верифицирована — текстовое содержимое из `strings_config_sync.xml`, контентдескрипшены, focus order для TalkBack — не тестируется.
- **How**:
  1. В `core/build.gradle.kts`: `androidTest.dependencies { implementation(libs.androidx.test.runner); implementation(libs.androidx.compose.ui.test.junit4); implementation(libs.androidx.work.testing) }`.
  2. Написать 3 тест-файла (T091 + T095 + хотя бы один Compose tests file).
  3. Запуск: `./gradlew :core:connectedMockBackendDebugAndroidTest` на запущенном эмуляторе.
- **When**: До production-релиза (Article §7 fitness functions требует регрессий на UI слое).
- **Status**: 🟡 OPEN
- **Origin**: 2026-05-15 emulator session — попытка прогнать T091/T095/Compose-UI на эмуляторе обнаружила, что androidTest source set пуст. Spec 008 `analyze-report.md` уже отмечал «Compose UI tests deferred to instrumented session», но без конкретного TODO.

### TODO-PERF-001: Macrobenchmark module для T140 (cold start ≤ 650 ms p95) 🟡

- **What**: Создать `:benchmark` Gradle module с `androidx.benchmark.macro` для измерения SC-004a: cold start с last-applied config из SQLDelight ≤ 650 ms p95 (20 итераций после process kill).
- **Why**: Сейчас `settings.gradle.kts` содержит только `:app` и `:core`. Macrobenchmark требует отдельный модуль с типом `com.android.test`, package'ом и `targetProjectPath` на `:app`. Без этого спек 008 §SC-004a не имеет measurable evidence — `perf-checkpoint.md` зафиксировал «⏳ pending device measurement», но даже на эмуляторе нечего запустить.
- **How**:
  1. Создать `benchmark/build.gradle.kts` с `com.android.test` plugin + `androidx.benchmark:benchmark-macro-junit4`.
  2. Добавить в `settings.gradle.kts`: `include(":benchmark")`.
  3. Написать `ConfigSyncStartupBenchmark.kt` — `MacrobenchmarkRule` с `MeasureCriterion.StartupCriterion`, 20 iterations, `CompilationMode.Partial(BaselineProfileMode.Require)`.
  4. Запуск: `./gradlew :benchmark:connectedMockBackendBenchmarkAndroidTest`.
  5. Записать измеренные p95 в `specs/008-bidirectional-config-sync/perf-checkpoint.md` §SC-004a (Pixel 4a baseline target — на эмуляторе цифры менее надёжны, документировать как indicative).
- **When**: Перед production-релизом 008. На эмуляторе цифры indicative, но позволяют отловить регрессии порядка величины.
- **Status**: 🟡 OPEN
- **Origin**: 2026-05-15 emulator session — попытка прогнать T140 обнаружила, что `:benchmark` модуля нет.

### TODO-DEVICE-001: 24-hour wakeups trial via Battery Historian 🟢

- **What**: На реальном физическом девайсе (не эмуляторе) — установить realBackend APK, оставить на 24 часа в естественном использовании, снять Battery Historian dump, измерить wakeups/hour агрегированно по 4 trigger'ам спека 008 (FCM + NetworkCallback + WorkManager + RESUMED).
- **Why**: Спек 008 §`perf-checkpoint.md` §«Background wakeups» заявляет ожидаемое значение ~9/hour worst case (4 от WorkManager + ~5 от NetworkCallback spikes), Article IX §3 cap = 10/hour. Без реального трейса нельзя подтвердить. Эмулятор не даёт реалистичных данных: нет реального network state churn, нет doze mode цикла, нет Background-restricted OEM-вмешательства (Samsung/Xiaomi/Huawei).
- **How**:
  1. Реальный Android-девайс (желательно Pixel + Samsung — два разных OEM behaviour).
  2. Realbackend APK (после TODO-SMOKE-001) с paired admin device.
  3. `adb shell dumpsys batterystats --reset` → 24 часа активного использования → `adb bugreport` → upload в [Battery Historian](https://developer.android.com/topic/performance/power/battery-historian).
  4. Аггрегировать wakeups по `WAKE_LOCK_ACQUIRED` + `JobScheduler` + `Alarm` events.
  5. Записать в `perf-checkpoint.md`.
- **When**: 🟢 Nice-to-have, перед public Play Store release. Internal alpha без этого можно. Если у пользователей появятся жалобы на батарею раньше — приоритет ↑.
- **Status**: 🟢 OPEN
- **Origin**: 2026-05-15 — `perf-checkpoint.md` `⏳ pending 24h-trial`, эмулятор не подходит.

### TODO-DEVICE-002: T143 multi-device manual smoke (US-1..US-5) с реальными OEM 🟡

- **What**: Прогнать 5 сценариев из `specs/008-bidirectional-config-sync/smoke/README.md` на двух физических девайсах с разными OEM (минимум: Samsung + Pixel; идеально + Xiaomi/Huawei). Снять скриншоты, записать pass/fail в таблицу sign-off.
- **Why**: Эмуляторный smoke (даже после TODO-SMOKE-001) не покроет:
  - Реальную FCM-латентность (Google Play Services на эмуляторе работают локально).
  - OEM-специфичные background restrictions (Samsung Smart Manager, Xiaomi Autostart, Huawei PowerGenie) — могут блокировать `ConfigRefreshWorker` и `NetworkCallback`.
  - Реальный pairing flow с QR-сканом (камера эмулятора эмулирует, но геометрия/освещение не реальные).
- **How**: Per `smoke/README.md`. Можно частично закрыть эмуляторами (US-1, US-3, US-5 в основном завязаны на app-логику), но US-2 (merge с двумя editor'ами) и US-4 (pending warning через background restart) требуют реальные device lifecycle конкурентно.
- **When**: До production-релиза.
- **Status**: 🟡 OPEN
- **Origin**: spec 008 T143 — изначально помечен `[M]` (manual). 2026-05-15 emulator session: TODO-SMOKE-001 — блокер для эмуляторного варианта; OEM-coverage в принципе требует физических девайсов.

---

## Spec 010 — emulator-deferred tasks (defer until emulator session)

Tasks from spec 010 implementation that need an emulator or device to verify.
Code in main is complete; these only need *running* against a build.

### TODO-SPEC010-EMU-001: Wizard manual smoke на Android 8.0 emulator (T051) 🟡

- **What**: Запустить `assembleMockBackendDebug` APK на Android 8.0 (API 26) AVD, пройти wizard, проверить legacy ROLE_HOME chooser opens correctly per FR-007 / plan §11 C-6 fallback.
- **Why**: API 26-28 не имеет `RoleManager` — `RoleHomeStep` использует `Intent.CATEGORY_HOME` chooser. Это branching код, который Robolectric не покрывает: только Реальная среда.
- **How**: `./gradlew :app:assembleMockBackendDebug && adb install ... && adb shell am start -n com.launcher.app/.firstlaunch.FirstLaunchActivity`. Tap «Сделать главным» → verify chooser opens с launcher в списке.
- **When**: Emulator session — Phase 3 verification.
- **Origin**: spec 010 T051.

### TODO-SPEC010-EMU-002: POST_NOTIFICATIONS smoke на Android 13+ emulator (T052) 🟡

- **What**: Wizard walkthrough на Android 13+ (API 33+) AVD — `PostNotificationsStep` должен appear, grant/deny paths работать.
- **Why**: POST_NOTIFICATIONS runtime permission introduced API 33. На <33 step skipped автоматически — но grant flow exercise требует API 33+.
- **How**: API 33 AVD, install mockBackend debug, launch FirstLaunchActivity, tap «Разрешить» / «Позже» — verify system dialog shows, deny path не блокирует завершение wizard'а.
- **When**: Emulator session — Phase 3 verification.
- **Origin**: spec 010 T052.

### TODO-SPEC010-EMU-003: GMS-less hard-block screen smoke (T053) 🟡

- **What**: Симулировать GMS-less девайс (например, AVD без Google APIs — «Android x.x» image, не «Google APIs»), запустить launcher, verify hard-block screen shown + «Понятно» closes app affinity.
- **Why**: FR-042 hard-block нельзя проверить на стандартных Google-emulators — GMS всегда есть. Нужен no-GApps system image.
- **How**: Создать AVD с system image «Android 13.0 (Google Play)» БЕЗ Google Play Services компонента, или загрузить vendor image без GMS. Запустить launcher; verify `GmsHardBlockActivity` shows; URL link clickable; «Понятно» вызывает `finishAffinity()`.
- **When**: Emulator session — Phase 3 verification.
- **Origin**: spec 010 T053.

### TODO-SPEC010-EMU-004: Call confirmation 2-tap smoke (T064) 🟡

- **What**: Verify SC-003: with CALL_PHONE granted, tap call-tile → tap CALL button → reaches «ringing» state in **2 taps total**. Without CALL_PHONE, fallback to dialer (3 taps).
- **Why**: Per spec FR-012/FR-013 — one-tap CALL replacing dialer's two-tap. The grant-permission Step happens once on first call-tile tap; subsequent calls are 2-tap. Robolectric cannot verify the **2-tap UX** end-to-end (real dialer state observable only on device).
- **How**: Emulator session — emulate a paired link, seed a Call slot to a real test number (or to emulator's own number), exercise the flow on API 33 AVD.
- **When**: Emulator session — Phase 4 verification.
- **Origin**: spec 010 T064.

### TODO-SPEC010-EMU-005: TalkBack walkthrough — CANCEL focused first (T065) 🟡

- **What**: Enable TalkBack on AVD, open call confirmation dialog, verify TalkBack reads CANCEL first (per CHK-accessibility-011), CALL second.
- **Why**: `Modifier.semantics { traversalIndex = -1f }` enforces focus order at the platform layer — only TalkBack on real env can verify.
- **When**: Emulator session — Phase 4 verification.
- **Origin**: spec 010 T065.

### TODO-SPEC010-EMU-006: Fresh install `!N≥2` (T079) + `N==0` after grants (T080) 🟡

- **What**: SC-004 / SC-005 — fresh install на Android 13+ AVD, navigate to Settings, expect badge `[!] N` с N ≥ 2 (ROLE_HOME + POST_NOTIFICATIONS). После grant всех Required → N == 0.
- **Why**: Cold-start state + system grant integration — Robolectric не может симулировать реальные permission flows.
- **When**: Emulator session — Phase 5 verification.
- **Origin**: spec 010 T079/T080.

### TODO-SPEC010-EMU-007: Unlink while offline → Firestore eventual revoke (T093) 🟡

- **What**: Smoke FR-032 / FR-032a path (a)+(b)+(c)+(d) на AVD: unlink в offline mode → Маша disappears immediately → toggle WiFi on → verify Firestore `/links/{linkId}.revoked = true` within 60 sec.
- **Why**: WorkManager CONNECTED constraint + Firestore reconnection — emulator-only behaviour.
- **How**: AVD с realBackend flavor + dev Firestore project. Pair, then offline mode, then unlink, then verify Firestore via console.
- **When**: Emulator session — Phase 6 verification.
- **Origin**: spec 010 T093.

### TODO-SPEC010-EMU-008: TalkBack 7-tap → challenge walkthrough (T102) 🟡

- **What**: US-7 #7 — TalkBack reads challenge text aloud, CANCEL focusable first, full flow 7-tap → challenge → CANCEL returns to home.
- **Why**: Same as TODO-SPEC010-EMU-005 — accessibility verification requires real TalkBack.
- **When**: Emulator session — Phase 7 verification.
- **Origin**: spec 010 T102.

### TODO-SPEC010-EMU-009: Macrobenchmark module + SC-002 cold-start ≤ 1 sec (T040, T107) 🟡

- **What**: Создать новый Gradle module `:macrobenchmark` (`com.android.test` plugin + benchmark library), реализовать `HomeStartupBenchmark.startup()` test measuring `MeasureUnit.MEDIAN` cold-start frame timing на baseline AVD class (Pixel 4a target).
- **Why**: Macrobenchmark module needs separate APK (benchmark target + benchmark code) — significant Gradle setup that goes beyond pure-code spec 010 work. Defer to emulator session где есть AVD для измерений.
- **How**:
  1. Create `:macrobenchmark` module с `androidTest` source set;
  2. `build.gradle.kts` apply `com.android.test` plugin + `androidx.benchmark:benchmark-macro-junit4`;
  3. Add `HomeStartupBenchmark` Kotlin class с `@StartupTimingMetric` annotated test;
  4. Run via `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`;
  5. Save p95 result в `specs/010-setup-assistant/perf-checkpoint.md`.
- **When**: Emulator session — Phase 2 T040 + Phase 8 T107 final pass.
- **Origin**: spec 010 T040 / T107.

### TODO-SPEC010-DEV-001: OEM matrix smoke (Samsung One UI / Xiaomi MIUI / Pixel) (T106) 🔴 PHYSICAL DEVICE

- **What**: Smoke на 3 physical devices: Samsung One UI (CALL flow), Xiaomi MIUI (BatteryOptimization exception path FR-020b — Xiaomi sometimes throws `SecurityException` on `PowerManager.isIgnoringBatteryOptimizations`), Pixel emulator (baseline).
- **Why**: OEM quirks вокруг battery optimization, ROLE_HOME flow, and notification scheduling — нельзя поверить эмулятором. Particularly Xiaomi MIUI's «autostart» + battery-quirks layer hides standard Android behaviour.
- **When**: При наличии physical-devices. Сейчас devices недоступны — fence task.
- **Origin**: spec 010 T106.

### TODO-SPEC010-DEV-002: Senior-safe walkthrough на 5 elder users (T105) 🔴 PHYSICAL USERS

- **What**: 5 elder-user test scenarios (fresh install wizard, tile→call, accidental 7-tap+cancel, TalkBack admin entry).
- **Why**: User-research task, не tech verification.
- **When**: Pre-Play-Store gate — отдельная research session.
- **Origin**: spec 010 T105.

---

## Future Specs (отдельные spec'и)

Спеки, которые **не** делаются в текущей итерации, но имеют достаточно понятный scope, чтобы зафиксировать как «будет отдельным спеком».

### TODO-FUTURE-SPEC-001: wearable-monitor (часы — пульс, давление, шаги) 🟢

- **What**: Отдельная подсистема мониторинга для умных часов / медицинских носимых устройств (heart rate, blood pressure, SpO2, steps, sleep). Отдельный flow в раскладке бабушки + отдельный раздел health-сводки у админа.
- **Why**: В пункт 2 спека 9 явно решено НЕ обобщать `MonitorIndicator` под часы. Часы семантически отличаются — медицинский домен, event-stream (HRV alerts), pairing через Bluetooth, sync через Google Fit / Samsung Health / Apple Health bridge. Generic-абстракция = слабый UX. Лучше отдельная подсистема.
- **How**: Новый wire format `WearableSnapshot` в `/links/{linkId}/wearable/{deviceId}`. Свой adapter в admin-UI. Свой Settings flow для pairing.
- **When**: Когда появится конкретный пользователь / клинический партнёр.
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery 2026-05-15 (отвергнут вариант (В) generic `MonitorIndicator`).

### TODO-FUTURE-SPEC-002: security-sensor-monitor (охранная сигнализация, smart home) 🟢

- **What**: Отдельная подсистема мониторинга для датчиков охранной сигнализации, smart home (door opened, motion detected, smoke alarm).
- **Why**: Event-stream wire format вместо snapshot — фундаментально другая модель данных. Объединение с phone health = слабый UX.
- **How**: Новый wire format с event stream в `/links/{linkId}/securityEvents/{eventId}`. Свой adapter. Push admin'у на каждый critical event.
- **When**: Конкретная потребность.
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery 2026-05-15.

### TODO-FUTURE-SPEC-003: messenger-contact-integration (LINE / WeChat / KakaoTalk / закрытые мессенджеры) 🟢

- **What**: Интеграция с закрытыми азиатскими мессенджерами, у которых friend'ы не имеют публичного phone number и не отдают `text/x-vcard` через `ACTION_SEND`.
- **Why**: В спеке 9 покрываются мессенджеры через VCard share intent (WhatsApp, Telegram, Viber — `text/x-vcard` работает). LINE / WeChat / KakaoTalk имеют свои SDK и **не** отдают VCard. Без отдельной работы их контакты в раскладку не попадут.
- **How**:
  - LINE — LINE Login SDK + LINE share API.
  - WeChat — WeChat Open SDK (требует registered developer account).
  - KakaoTalk — Kakao Share SDK (см. `https://developers.kakao.com/docs/latest/en/kakaotalk-share/android-link`).
  - Каждый — свой OAuth/deep-link/QR flow для импорта контакта.
  - Зависит от `TODO-ARCH-014` (Contact без phone number).
  - Зависит от расширения провайдеров спека 6 (`LineCall`, `WeChatCall`, `KakaoCall` SlotKind).
- **When**: При появлении реальных пользователей из соответствующих регионов (Япония / Китай / Корея).
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery 2026-05-15 (Q про мессенджеры).

### TODO-FUTURE-SPEC-004: shared-admin-contact-book (общая адресная книга админа) 🟢

- **What**: Общая адресная книга админа (Firebase коллекция `/admins/{adminId}/contacts/`), на которую ссылаются `/config.contacts[]` у каждого Managed-устройства.
- **Why**: В спеке 9 контакты per-Managed (если админ управляет бабушкой и дедушкой, Маша добавляется дважды). Это проще и privacy-friendly («дедушке нельзя знать про Машу»). Если позже окажется неудобным — заведём общую книгу как отдельный wire format слой.
- **How**: Новая Firestore коллекция, новая модель ссылок (по UUID на shared contact). Миграция per-Managed contacts опциональная (можно оставить inline-контакты для backward-compat).
- **When**: Когда появятся жалобы «достало добавлять одного и того же контакта в раскладки разных Managed».
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery 2026-05-15 (Q4 пункта 4).

### TODO-FUTURE-SPEC-005: preset-editor (полное редактирование preset settings) 🟢

- **What**: Полный редактор preset (`PresetSettings`) — цвет фона, шрифт, размер плиток, расположение тулбара (top/bottom/none), переключение flow свайпами vs табами, кастомные пресеты.
- **Why**: В спеке 9 preset = ссылка на захардкоженный шаблон (workspace / simple-launcher / launcher). Сам шаблон редактировать нельзя. Wire format `presetOverrides: PresetSettings?` зарезервирован (FR-10 спека 9), но всегда null. Этот спек — UI + полная wire-format-схема `PresetSettings`.
- **How**: Расширить `PresetSettings` всеми настраиваемыми полями. UI редактирования. Selector кастомных пресетов. Хранение либо inline в `/config.presetOverrides`, либо в shared библиотеке пресетов админа.
- **When**: После того, как сам спек 9 устоится в production и появится реальный спрос на кастомизацию.
- **Status**: 🟢 OPEN
- **Origin**: spec 009 pre-specify discovery 2026-05-15 (пункт 3 — preset как форвард-совместимая концепция).

### TODO-FUTURE-SPEC-006: onboarding-and-tutorials (внутреннее обучение admin + Managed) 🟢

- **What**: Отдельный спек, покрывающий обучающий слой продукта целиком — несколько направлений:
  - **Admin onboarding**: как pair'иться (расширение QR-flow спека 7 с пошаговыми подсказками), что такое admin-mode и как туда зайти на бабушкином устройстве (7-tap gesture, см. спек 10 FR-021), walkthrough редактора раскладки (плитки, drag-and-drop из спека 9), типичные действия (поменять контакт, добавить плитку «Аптека»).
  - **Managed (бабушка) first-launch polish**: расширение wizard'a спека 3 (language → preset → ROLE_HOME → POST_NOTIFICATIONS из спека 10) — большие иллюстрации, voice-over, проверка понимания.
  - **In-app contextual help**: первый раз admin зашёл в editor → большая подсказка «потяни плитку чтобы переставить»; первый раз бабушка получила обновление раскладки → toast «внук обновил твой телефон».
  - **Видеоинструкции / Lottie-анимации**: ассеты для wizard'ов и contextual help.
  - **Help-screen для admin'a**: «как настроить ROLE_HOME» с скриншотами, «что делать если push'и не приходят» (battery optimization OEM-quirks), «как добавить второго admin'a» (после спека 011).
- **Why**: В clarify-сессии спека 10 (2026-05-19) US-8 (tutorial overlay для бабушки про 7-tap) был **удалён** — решено, что бабушка вообще не должна попадать в Settings (admin-only поверхность), tutorial для неё не нужен, а обучение admin'a — отдельная задача со собственной глубиной. Эта работа большая (видео-съёмка, иллюстрации, copywriting, accessibility-aware voice-over), не помещается в спек 10 «связующий».
- **How (high-level)**: После того, как admin-mode (спек 9) и Setup Assistant (спек 10) стабилизируются — собрать реальные pain points через UX walkthrough'и на 5-10 admin'ах + 5 бабушках, выделить топ-5 confusing moment'ов, под них написать спек.
- **When**: После production-релиза спеков 7-10 и сбора первой telemetry / observation data. Не раньше Q3 2026.
- **Status**: 🟢 OPEN
- **Origin**: spec 010 clarify session 2026-05-19 — изначально US-8 (tutorial overlay для бабушки про 7-tap), решено вынести в отдельный спек с большим scope.

---

## Legal & Compliance

### TODO-LEGAL-001: Contacts privacy compliance (GDPR / 152-ФЗ) 🟡 🚨 PLAY-STORE-BLOCKER

- **What**: Полная privacy compliance pipeline для контактов админа, добавленных в раскладки Managed: экран «список добавленных контактов» в admin-Settings с возможностью удалить любой; rationale-экран перед запросом `READ_CONTACTS`; политика конфиденциальности; экспорт / удаление по запросу субъекта данных (GDPR Article 17, 20; 152-ФЗ ст. 14, 21).
- **Why**: Сбор `READ_CONTACTS` и хранение PII (имя + номер) в Firebase затрагивает третьих лиц (контакты админа), которые согласия **не давали**. Без compliance flow:
  - Высокий риск Play Store reject при публикации (Google проверяет permission rationale + privacy policy).
  - Юридические риски в EU (GDPR fines) и в РФ (152-ФЗ).
- **Progress** (spec 009 implementation 2026-05-16):
  - ✅ **Минимум** — `ContactsManageScreen` + `ContactsManageComponent` + confirmation dialog. Reachable через Settings → Сопряжённые устройства → device row → Контакты. Verified on emulator (screenshot `spec009-g-11-contacts.png`). FR-031a closed.
  - ✅ **Rationale Composable** — `ContactPermissionRationaleScreen` написан (Phase 10). НЕ wired в navigation: system contact picker flow ещё не интегрирован в editor (часть TODO-ARCH-016). Когда picker подключится — rationale screen wires автоматически перед `ContextCompat.requestPermissions`.
  - ❌ **Privacy policy** (юр. документ) — не написан.
  - ❌ **GDPR Article 17/20 endpoints** — требует Cloud Functions = Spark→Blaze upgrade (TODO-ARCH-003 dependency).
  - ❌ **Subject-driven deletion** (Маша сама удаляет, без админа) — server-side endpoint, depends on TODO-ARCH-003.
- **How** для оставшейся части:
  - **Privacy policy** — markdown + статическая страница (~0.5 дня).
  - **Server-side GDPR endpoints** — Cloud Function `requestDataExport(managedDeviceUid)` + `requestDataDeletion(managedDeviceUid)` + email pipeline. Зависит от TODO-ARCH-003 Blaze upgrade.
- **When**:
  - Минимум: ✅ done 2026-05-16.
  - Privacy policy: **до публикации в Play Store** (Data Safety form требует ссылку).
  - Server-side endpoints: **до публикации в EU / РФ** (GDPR fines / 152-ФЗ).
- **Status**: 🟡 IN PROGRESS — минимум закрыт; remaining (privacy policy + server endpoints) остаётся **🚨 PLAY-STORE-BLOCKER**.
- **Origin**: spec 009 pre-specify discovery 2026-05-15 (mentor возражал, пользователь отложил). Privacy compliance — **обязательное** условие production-релиза.

---

### TODO-PHYS-001: VCard share intent — реальная проверка content-URI 🟢

- **What**: `VCardReceiveActivity` (spec 9 Phase 6) проверен эмулятором: intent-filter резолвится, Activity launches, `launchMode=singleTask` + `onNewIntent` работают, error UI рендерится. Но **реальный content-URI парс не воспроизводится через adb-shell** — MediaProvider на Android 14+ блокирует `am start` с file:// (FileUriExposed-аналог) и с `content://media/external/file/...` (адб-shell не пробрасывает URI grants, наше приложение не имеет READ_MEDIA_* permissions и не должно — compliance budget). Реальный поток: WhatsApp/Telegram создают `content://<app>.fileprovider/...` с `FLAG_GRANT_READ_URI_PERMISSION` на стороне sender'а — это работает в production, но локально невоспроизводимо.
- **Why**: единственный остающийся pre-production gate для FR-027/027a/028 — covered unit tests'ом (9 real-bytes VCard samples), не covered end-to-end.
- **How**: на реальном устройстве (или эмуляторе с установленным WhatsApp/Telegram через apkmirror) — отправить контакт из WhatsApp Share contact → выбрать наш лончер → проверить parse + display. Затем повторить из system Contacts + Telegram.
- **When**: до Play Store upload (Article VIII senior-safe gate + OEM matrix).
- **Origin**: spec 009 Phase 14 emulator smoke 2026-05-16 — adb-shell limitation discovered.
- **Status**: 🟢 OPEN

### TODO-UI-001: Заменить health-indicator icons на семантически правильные 🟢

- **What**: В спека 9 Phase 7 `PhoneHealthIndicatorRow.iconFor()` использует приблизительные icons из `material-icons-core` (Star для battery, Refresh для connectivity, Notifications для audio, Phone для lastSeen) — потому что spec 9 plan §5 запрещает новые gradle deps. Это ухудшает UX для пожилых пользователей (Article VIII).
- **Why**: Семантическая иконка → лучшее распознавание. Сейчас "Звезда" для зарядки — неинтуитивно.
- **How**: один из вариантов:
  (a) Добавить `androidx.compose.material:material-icons-extended` (~10 MiB APK delta — нарушает Article XIII budget). NOT RECOMMENDED.
  (b) Положить ~10 кастомных vector drawables в `res/drawable/` (battery_24, signal_cellular_24, volume_up_24, watch_24) — APK delta ≤ 50 KB. RECOMMENDED.
  (c) Использовать Material Symbols через downloaded SVG → vector resource.
- **When**: до Play Store upload (Article VIII senior-safe гейт).
- **Origin**: spec 009 Phase 7 implementation 2026-05-15 (deliberate trade-off, plan §5 dep budget vs Article VIII UX).
- **Status**: 🟢 OPEN

### TODO-DOC-001: Fix `/config/history/{autoId}` path notation в спека 009 contracts ✅

- **What**: `specs/009-admin-mode-flows/contracts/config-history.md` пишет путь как `/links/{linkId}/config/history/{autoId}` — это невалидно для Firestore (нельзя иметь `history` как doc и `{autoId}` сразу как doc в том же сегменте без коллекции между ними). Реализация использует sibling-collection: `/links/{linkId}/configHistory/{autoId}` (см. `firestore.rules`, `Link.KNOWN_SUBCOLLECTIONS`, `FirestoreConfigHistoryAdapter`).
- **Resolved**: 2026-05-16 в спека 009 Phase G — contract doc обновлён, path-note добавлена в `contracts/config-history.md` поясняющая historical drift. plan.md / spec.md сохраняют исторический путь — это снапшоты процесса проектирования, не source-of-truth.
- **Origin**: spec 009 Phase 5 implementation 2026-05-15 (mentor critical review).
- **Status**: ✅ Closed

---

## Closed items (✅ historical reference)

*(пусто; добавлять по мере закрытия с датой и reference)*

---

## Workflow

**Добавление нового item'а**: append в правильную секцию, выбрать `🔴/🟡/🟢`, заполнить What/Why/How/When/Origin.

**Закрытие**: переместить в "Closed items", добавить `✅ дата + коммит-ссылка`.

**Промоция в активный спек**: если item становится частью текущего спека — копировать в `specs/<id>/tasks.md`, оставить здесь с note «In progress in spec NNN — task TXXX». После завершения спека → переместить в Closed.

**Re-review**: при каждом `/speckit.analyze` — проверять item'ы со статусом `🔴` и `🟡`, актуальны ли.
