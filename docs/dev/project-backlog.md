# Project Backlog — Operational TODOs

Рекомендации и задачи **не блокирующие** текущую разработку, но необходимые для production-readiness, security hygiene, или эволюции feature'ов.

**Назначение этого файла**: накапливать «нужно когда-нибудь сделать» решения, чтобы они не терялись между сессиями и спеками. Сюда попадают:
- Operational рекомендации (2FA, key rotation, monitoring setup).
- Architectural exit ramps (когда мигрировать с Spark→Blaze, workers.dev→свой домен, etc.).
- Hardening и улучшения, выявленные в `/speckit.analyze` или ревью.
- Future features из Backlog (`backlog overview`) которые ещё не привязаны к конкретному спеку.

**Не добавлять сюда**: бытовые баги (→ GitHub Issues), задачи в рамках активного спека (→ соответствующий `tasks.md`).

## Status legend

- 🔴 **Critical** — выполнить как можно скорее; риск безопасности или production-fail.
- 🟡 **Important** — обязательно до production-релиза.
- 🟢 **Nice-to-have** — когда появится потребность / время.
- ✅ **Done** — дата закрытия + reference на коммит/спек.

---

## Security & Operations

### TODO-SEC-CRITICAL-024: ConfigDocument E2E encryption ✅ DONE 2026-06-20 (F-5b envelope variant)

- **What**: Сейчас `ConfigDocument` хранится в Firestore в **plaintext**. Firebase / Google / любой с доступом к Firestore project видит: имена контактов, телефоны (E.164), labels слотов («Внук Петя», «Скорая помощь»), package names приложений, структуру layout. Зашифрованы (через спеки 011/012) **только** photo и document blob'ы — но **сам конфиг — нет**.
- **Why**: Это **критическая privacy regression**. Решено 2026-05-28: F-5 в Phase 1 Foundation roadmap'а, **production blocker** (не можем выпустить в работу пока не закрыто), но **не блокирует development** S-features (можем разрабатывать с plaintext config'ом в dev environment).
- **How** (требования к будущей спеке F-5):
  1. **Транспарентность через ACL** — `ConfigCipher` port в `core/domain/` с verbs `seal(ConfigDocument): SealedConfig` и `open(SealedConfig): ConfigDocument`. Реализация (libsodium AEAD под капотом) скрыта в adapter. **Никакого другого кода в проекте не должно знать**, что конфиг шифруется/расшифровывается. CLAUDE.md rule 1 + 2.
  2. **Server side comparisons исчезают** — сервер видит opaque bytes, не может diff'ить, не может field-level merge'ить. Все merge / conflict resolution / diff операции переезжают **на клиент**. Это переписывает часть спеки 008 (`ConfigEditor.pushPending` optimistic concurrency остаётся, но на уровне document hash, не field-by-field).
  3. **Wire-format migration**: ConfigDocument schemaVersion bump → 2 (или новый wire-format SealedConfigDocument). Backward-compat read v1 (plaintext) → v2 (sealed) во время transition.
  4. **Key management**: ключ для шифрования config'а — пара-зависимый (admin ↔ Managed пара имеет общий symmetric ключ, выведенный из X25519 ECDH). Multi-admin случай: каждый admin'ский pair имеет свой ключ → каждый admin шифрует config своим pair-ключом → server хранит N зашифрованных копий или **wrapped key envelope** (как FR-025 в архивированной спеке 013).
  5. **Search invariant**: search по контактам в admin Editor (FR в спеке 009) переезжает **полностью на клиент** — admin'ский телефон расшифровывает config, ищет локально, повторно отправляет на сервер уже отфильтрованную операцию. Server search **исчезает**.
  6. **Backup / export**: bulk export ConfigDocument для пользователя становится возможным **только** для аутентифицированного admin'а — он расшифровывает локально + экспортирует.
- **When**: после F-4 (AuthProvider) и до production release. Не блокирует S-features в development.
- **Status**: 🟢 DONE 2026-06-20 — implemented as **F-5b envelope variant** (architectural pivot 2026-06-20 from symmetric self-edit to hybrid envelope per spec 011 §C-2/§C-3). Branch `018-f5-config-e2e-encryption` ready for PR. Состояние:
  - **core/keys/** KMP module: RemoteStorage facade + Envelope wire format (XChaCha20-Poly1305 для контента + libsodium `crypto_box_seal` X25519 для CEK) + ConfigSaver + EnvelopeBootstrap caller API + multi-recipient cross-user delegation (membership-agnostic).
  - **Адаптеры**: FirestoreRemoteStorage, FirestorePublicKeyDirectory, AndroidDeviceIdentity (X25519 keypair + DataStore, `allowBackup=false`), GoogleSignInIdentityProof, WorkManagerAsyncConfigPushQueue.
  - **Firestore Security Rules**: owner + access-grant model, schemaVersion monotonic (H-2 downgrade defence), 22/22 rules unit tests в [rules.f5b.envelope.test.ts](../../firestore-tests/rules.f5b.envelope.test.ts).
  - **Recovery vault path (root key)** сохранён рядом: RootKeyManager + Argon2id + Firestore-backed RecoveryKeyVault + 3-strike lockout + DataStore H-1 mitigation + 20/20 rules tests в rules.f5.recovery.test.ts. Используется для восстановления **root key** (отдельный концерн от envelope data encryption).
  - **Tests**: 68 JVM в `:core:keys:jvmTest`; instrumented [CloudConfigEncryptionE2ETest](../../app/src/androidTest/java/com/launcher/app/data/envelope/CloudConfigEncryptionE2ETest.kt) — 🟢 2/2 PASSED на Xiaomi 11T через Firebase Emulator (path A') и 2/2 PASSED против real `launcher-old-dev` cloud (path B). SC-001 acceptance закрыт на реальном TEE Keystore + MIUI quirks.
  - **Legacy removal**: AeadConfigCipher, KeyRegistry, SealedConfig, WrappedDek + контракты — удалены в Batch 6 (`d135216`).
- **Origin**: User raised 2026-05-28 — обнаружено при обсуждении spec 014 (Contact Sharing UX). Зафиксировано как F-5 в roadmap.md.

### TODO-RULES-TESTS-REGRESSION-001: Pre-existing baseline failures в rules.auth.test.ts + rules.test.ts ✅ DONE 2026-06-20

- **What**: При запуске `cd firestore-tests && npm test` падают **14 тестов** в двух файлах:
  - `rules.auth.test.ts` (spec 017 F-4 identity-links): **все 12 тестов FAILED**.
    Ошибка: `Invalid document reference. Document references must have an even number of segments, but identity-links/google/108547295013826509471 has 3.`
    Тесты пишут в path `identity-links/google/{sub}` — Firestore интерпретирует
    этот 3-segment path как **collection reference**, не document. Чтобы был
    document — нужно 2 сегмента (`identity-links/{sub}`) или 4
    (`identity-links/google/{sub}/something`).
  - `rules.test.ts` (spec 007 pairings):
    - `only_managed_can_delete_link` — ожидает что admin не может delete link,
      но rules позволяют. Либо тест устарел после rules change, либо rules
      регрессировали.
    - `user doc create fails if identity-link missing` — связан со spec 017
      path bug выше.
- **Why**: Pre-existing baseline regression. **Не блокирует F-5b** — spec 018
  F-5b tests (rules.f5b.envelope.test.ts: 22/22) и rules.f5.recovery.test.ts
  (20/20) **passes**. Но указывает на тo, что либо path schema в spec 017
  изменилась без обновления тестов, либо rules для spec 007 link delete'а
  ослаблены.
- **How**:
  1. `git log -p -- firestore.rules firestore-tests/rules.auth.test.ts` — найти
     commit где path schema split'нулся между rules и tests.
  2. Решить: исправить tests (4-segment path) или rules (2-segment path).
     Identity-link concept имеет `{provider, sub}` ключ — natural fit для
     4 сегментов (`identity-links/{provider}/{sub}/main`) или 2 с composite
     id (`identity-links/{provider}-{sub}`).
  3. `only_managed_can_delete_link` — проверить FR-033 в spec 007 contracts
     (revoke semantics), посмотреть в rules.test.ts что именно ожидает.
- **When**: before next sync of dev project rules (т.е. при следующем
  изменении rules.auth.test.ts или rules.test.ts). **Не блокирует F-5b PR**.
- **Status**: ✅ DONE 2026-06-20
- **Origin**: Обнаружено 2026-06-20 при запуске rules tests после deploy F-5b
  rules на launcher-old-dev. F-5b сам зелёный (22/22 + 20/20); baseline
  regression в pre-existing spec 007 / 017 tests существовал ДО F-5b и не
  затронут F-5b commits.
- **Resolution (2026-06-20)**:
  - **rules.auth.test.ts** path fix: тесты писали в `identity-links/google/{sub}`
    (3 сегмента — Firestore интерпретирует как collection reference), а
    production code (`GoogleSignInAuthAdapter.kt:254`) пишет в
    `identity-links/google_{sub}` (single document). Обновлены пути в тестах.
  - **firestore.rules** tightened: rules для `/identity-links/{linkId}` и
    `/users/{stableId}` переписаны из "open to any authenticated" на
    production-strict per spec 017 contract: identity ownership check
    (`linkId == "google_" + request.auth.uid`), UUIDv4 regex, immutability,
    cross-collection verification (`user create` требует existing identity-link
    с matching stableId). Helper functions `isGoogleLinkIdForCaller`,
    `isValidUuidV4`, `ownerIdentityLink` extracted. Deployed на
    launcher-old-dev 2026-06-20.
  - **rules.test.ts** `only_managed_can_delete_link` → переименован на
    `both_admin_and_managed_can_delete_link`. Rule был намеренно расширен
    на admin'а (см. firestore.rules L125-127 — для admin "prune stale link"
    button); тест устарел. Добавлен новый тест `stranger_cannot_delete_link`
    как defence-in-depth (третья сторона не имеет права).
  - Full rules tests suite: **84/84 passes** (rules.test.ts 29 + rules.auth.test.ts 13
    + rules.f5.recovery.test.ts 20 + rules.f5b.envelope.test.ts 22).

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

## Future Products (ecosystem)

### TODO-RESEARCH-014: Compositable preset architecture for F-2 Capability Registry 🟡

- **What**: Research и design **compositable preset model** — переход от monolithic preset enum (Workspace / Simple Launcher) к compositable units of capability, которые комбинируются в финальную конфигурацию лаунчера.
- **Why**: User vision 2026-05-29 — preset = compositable unit (1 aspect: tremor-fix, notification-shutter-lock, yellow-accent, tile-shadows, etc.). Bundled presets (Simple Launcher / Workspace) становятся pre-packaged compositions. Преимущества: marketplace-ready, A/B testing, accessibility-friendly, cross-platform consistency.
- **How (research targets)**:
  1. **Capability primitive design** — что такое "atomic capability"? Toggleable boolean? Composable settings? Strategy pattern?
  2. **Composition resolution** — как разрешаются конфликты (2 capabilities имеют overlapping effect)? Priority? Override semantics?
  3. **Wire-format** — `ExportablePreset` schema с schemaVersion, identity-strip rules.
  4. **Cross-platform availability** — как capability помечает supported platforms; fallback при mismatch.
  5. **Migration path** — backward-compat с current `FlowPreset` enum; existing spec 014 references на Workspace / SimpleLauncher продолжают работать.
  6. **UI для composition** — пользователь видит/собирает свою конфигурацию (TODO: какой UI pattern — checklist? marketplace? wizard?).
  7. **Performance** — composition resolution не должен влиять на startup time или frame rate.
- **Research patterns**:
  - **Tailwind CSS utility-first** — каждый class atomic, композиция через concat.
  - **VS Code Settings Sync packs** — shareable settings collections.
  - **iOS Accessibility Shortcuts** — independent toggles, composable.
  - **Home Assistant Themes** — composable presets с inheritance.
- **When**: F-2 Capability Registry Foundation (roadmap.md Phase 1). До F-2 — capability model **не существует**; spec 014 + другие специки используют current monolithic enum как placeholder.
- **Status**: 🟡 OPEN — primary research input для F-2 spec phase.
- **Origin**: User vision 2026-05-29 при обсуждении F-014 clarify Q2. Записано подробно в [docs/product/future/ecosystem-vision.md §Compositable Presets](../product/future/ecosystem-vision.md#compositable-presets--long-term-architectural-vision).

### TODO-UX-025: Tutorial / onboarding overlay для new admin'ов 🟡

- **What**: Onboarding overlay (highlight + tip cards) для **первого запуска** admin'ского Workspace — учит long-press входу в edit mode, tap на empty «+» tile, и как работает paired-device editing.
- **Why**: F-014 long-press entry — discovery problem (см. spec accessibility.md CHK014). Без onboarding admin'у в первый раз будет непонятно как настраивать. Текущий compromise — empty-state «+» tile (FR-020a) даёт direct entry для нулевого state, но дальше admin застрянет.
- **How** (when implementing):
  1. Tip card sequence — 3-4 шага maximum (overload counter-productive).
  2. Skip / dismiss всегда доступен (Article XV — user dignity).
  3. Trigger: только на самой первой загрузке (track flag в DataStore).
  4. Russian + EN copy.
- **When**: Post F-014.0 ship, перед public release. Полезно для бета-тестеров.
- **Status**: 🟡 OPEN — referenced from F-014 §"Что НЕ строит этот спек".
- **Origin**: F-014 deferral 2026-05-29.

### TODO-UX-026: Recently deleted / Trash bin 30-day retention 🟡

- **What**: «Корзина» для удалённых tiles — undo доступен в течение 30 дней после deletion. Сейчас (F-014.0) только 8-секундный snackbar undo.
- **Why**: Admin может accidentally удалить tile и не успеть undo. 30-day retention даёт safety net. Mainstream pattern (Gmail / iOS Photos).
- **How** (when implementing):
  1. Wire-format change: добавить `/config/deletedTiles/` collection (separate from current `/config/current`).
  2. Schema: `{ tileId, originalFlow, deletedAt, expiresAt }`.
  3. UI entry: Settings → «Корзина» (показывается только если non-empty).
  4. Auto-cleanup на read (client-side, no cron нужен).
  5. Restore action → re-add в original Flow (если ещё существует) или новый Flow.
- **When**: Post F-014.0 ship. **Не блокер MVP**.
- **Status**: 🟡 OPEN — referenced from F-014 §"Что НЕ строит этот спек".
- **Origin**: F-014 deferral 2026-05-29.

### TODO-UX-027: Widget tile-type real rendering 🟡

- **What**: Реализация **functional rendering Android widgets** как plate type на admin home screen. Закрывает placeholder вкладку «Виджеты» в admin picker'е (F-014 FR-018).
- **Why**: F-014 определяет Widget как visible placeholder вкладка с «В разработке» screen — admin понимает roadmap, но functional widget hosting deferred. Этот TODO закрывает gap.
- **How** (когда настанет время):
  1. Add Android `AppWidgetHost` integration в launcher app.
  2. Permission handling — `BIND_APPWIDGET` (system permission, requires LauncherApps).
  3. Widget picker UI (выбор виджета из installed apps).
  4. Plate slot type `WidgetSlot` в ConfigDocument wire-format (additive, schemaVersion bump на consumer side).
  5. Lifecycle handling (widget update broadcasts, view recycling).
  6. Edit mode interaction (resize, remove).
  7. Senior profile: widget вкладка остаётся **скрытой** (privacy/safety per F-014 FR-019).
- **When**: Post-MVP UX polish phase, после F-014 lands.
- **Status**: 🟡 OPEN — referenced from F-014 FR-018.
- **Origin**: F-014 spec scope decision 2026-05-29 — Widget visible placeholder только, no implementation.

### TODO-UX-028: Action tile-type (SOS, phone, flashlight, etc.) 🟡

- **What**: Реализация **functional Action plates** — быстрые кнопки одного нажатия (SOS, фонарик, прямой звонок, погода, и т.д.). Закрывает placeholder вкладку «Действия» в admin picker'е (F-014 FR-018).
- **Why**: F-014 определяет Action как visible placeholder вкладка с «В разработке» screen. Реальная implementation требует отдельной спеки — Action concept уже частично присутствует в спеке 005 (Action Architecture), но specific Actions (SOS, фонарик) не реализованы.
- **How** (когда настанет время):
  1. Определить set предустановленных Actions: SOS (emergency call + location share), Phone (direct dial), Flashlight, Camera, Weather widget, Volume mute, и т.д.
  2. Action registry pattern (extends спека 005 ActionDispatcher).
  3. Plate slot type `ActionSlot` в ConfigDocument wire-format (additive).
  4. Per-action permissions handling (CALL_PHONE для phone, CAMERA для flashlight, etc.).
  5. Action picker UI (browseable list с descriptions и preview).
  6. Senior profile: Action вкладка остаётся **скрытой** (per F-014 FR-019).
- **Cross-link**: Связан с TODO-UX-027 (Widget); связан со спекой 005 (Action Architecture).
- **When**: Post-MVP UX polish phase, после F-014 lands. SOS Action имеет особо высокий приоритет для senior product fit.
- **Status**: 🟡 OPEN — referenced from F-014 FR-018.
- **Origin**: F-014 spec scope decision 2026-05-29 — Action visible placeholder только, no implementation.

### TODO-FUTURE-UX-012: First multi-config creation toast/hint copy 🟢

- **What**: Дизайн **microcopy** для subtle toast при transition State 0 → State 2 в named configs (F-014 FR-003d). Когда admin впервые создаёт второй named config через push dialog — показывается toast «Конфиг "X" создан. Управление — в настройках» (3 sec, не overlay, не tutorial).
- **Why**: First-time encounter с multi-config concept — нужна короткая подсказка где найти Settings entry. Не overlay (агрессивно), не tutorial (читать никто не будет), но и не silent (admin не поймёт что произошло). Subtle toast — sweet spot.
- **How** (when implementing):
  1. Write 2-3 copy alternatives и user-test через 5 senior-adjacent admin'ов.
  2. Test on actual device (timing 3 sec adequate? auto-dismiss vs swipe?).
  3. Include locale variants (ru / en).
- **When**: Параллельно F-014.1 (когда multi-config UI implemented).
- **Status**: 🟢 OPEN.
- **Origin**: User decision 2026-05-29 — «появление multi-config UI — explicit moment, без tutorial overlay».

### TODO-FUTURE-DESIGN-PRINCIPLE-013: Apply progressive disclosure to other multi-X features 🟢

- **What**: Применить **«Progressive disclosure: multi-X UI hidden until X count > 1»** (зафиксировано в `docs/dev/project-constants.md`) к другим multi-X features в проекте: Flow tabs (BottomFlowBar), paired devices list, admin-managed bondings.
- **Why**: Текущее implementation BottomFlowBar показывает tab bar **всегда** (даже когда 1 flow). Это **нарушение** только что зафиксированного principle. Аналогично paired devices list — показывается даже когда 1 paired (мы видели это в device-testing спеки 012).
- **How**:
  1. **BottomFlowBar**: hide tab bar если `flows.count == 1`. Single flow renders full-screen, no bottom navigation.
  2. **Paired devices**: simplified UI for 1 paired device (direct entry into target, no list picker). 2+ → list picker.
  3. **Admin-managed bondings**: 1 ↔ упрощённый UI, 2+ → расширенный.
  4. Refactor existing code в conditional rendering pattern (Compose `derivedStateOf`).
- **When**: Параллельно общему UX-polish phase после F-014, до production release.
- **Status**: 🟢 OPEN — applies after F-014 lands.
- **Origin**: F-014 spec FR-003d + `docs/dev/project-constants.md` architectural principle 2026-05-29.

### TODO-FUTURE-PRODUCT-006: Professional Configurator (B2B) 🟢

- **What**: Расширение продукта на B2B рынок — профессиональные настройщики (Geek Squad-style сервис, IT helpdesk для пожилых, операторские помощь типа «МТС Любимая»), которые удалённо настраивают раскладку 100+ клиентов.
- **Why**: Реальный рынок (Geek Squad ~$500M revenue/year в US). Vision shift — потенциальный pivot direction если семейный MVP не получит traction. Сейчас архитектура **не блокирует** future support — pair primitive расширяется на N clients additive way.
- **How** (когда настанет время):
  1. New role «Configurator» (отличная от admin/senior/caregiver) с своей trust model — temporary trust, explicit termination, может быть revoked at any moment by client.
  2. Multi-tenancy в server-side data layer — `/configurators/{configuratorUid}/clients/{clientUid}/config/...`.
  3. Tenant isolation (configurator видит только своих clients, никогда другого configurator).
  4. Billing model — per-client subscription или per-setup fee.
  5. Compliance — GDPR / российские требования к обработке PII client'ов.
- **When**: Post-MVP v2 (V-1..V-5 уровень). Только если MVP подтвердит interest, и market analysis покажет B2B opportunity.
- **Status**: 🟢 VISION — никаких current consumers, не блокирует MVP.
- **Origin**: User raised 2026-05-29 при обсуждении F-014 clarify Q1. Зафиксировано как possibility, не запланировано.

### TODO-FUTURE-SPEC-007: Named config export/import as shareable preset 🟢

- **What**: Возможность admin'у exportнуть named config (например, "home") как **shareable preset** — другой пользователь может imported его, получить ту же раскладку плиток / theme / structure, но **без identity-bound данных** (без contacts, без phone numbers, без photo URLs).
- **Why**: CLAUDE.md rule 9 (preset-readiness): user-facing non-identity-bound config должен быть shareable. Family может «расшарить» successful Simple Launcher template между друзьями. Senior community обмен наработками.
- **How** (когда настанет время):
  1. Strip identity-bound fields из ConfigDocument при export (Contact entries, Document refs, custom photos).
  2. Wire-format `ExportablePreset` с явным `schemaVersion`, signature, anonymized fields.
  3. Import flow — `ConfigSource` adapter (additive над BundledSource из CLAUDE.md rule 9).
  4. UI — «Поделиться» в My Configs screen, «Импорт» как одна из опций при создании нового config.
- **When**: Post-MVP, когда community grow и попросят.
- **Status**: 🟢 OPEN — записан для preservation, не блокирует MVP.
- **Origin**: User vision 2026-05-29 — «можно делиться конфигом, ну, не теми данными типа номеров телефонов, а абстрактные».

### TODO-FUTURE-SPEC-008: Auto-GC orphan named configs (server-side) 🟢

- **What**: Автоматическое **удаление через 30 дней** named configs со статусом ORPHAN (activeDeviceIds empty + orphanedAt > 30 days ago). Сейчас в F-014.1 ORPHAN configs **помечаются** но **не удаляются** автоматически — admin может восстановить навсегда.
- **Why**: Garbage collection storage on server. При scale (10K+ admin'ов × 5 configs × deprecated ones) — лишние данные. Также cost containment для Firebase Spark plan limits.
- **How** (когда настанет время — own-server):
  1. Server-side cron job (Cloud Functions OR Cloudflare Worker cron OR own-server scheduler).
  2. Iterate over ORPHAN configs где `orphanedAt < now - 30 days`.
  3. Delete document atomically.
  4. Notify admin через push «Конфиг X удалён».
- **When**: При переходе на собственный сервер (TODO-ARCH-001 dependency) ИЛИ при Blaze upgrade (TODO-ARCH-003) — что наступит раньше.
- **Status**: 🟢 OPEN — DEFERRED IN MVP. F-014.1 markу ORPHAN, не удаляет.
- **Origin**: User decision 2026-05-29 — «если есть сложности с удалением через 30 дней, оставлять метку, удаление перенесём на свой сервер».

### TODO-RESEARCH-009: Stable device identity strategy (no extra permissions) 🟡

- **What**: Определить **стабильный device identifier** для tracking `activeDeviceIds` в named configs (F-014.1). Identifier должен переживать app reinstall, **не требовать extra permissions от пользователя**, не нарушать Google Play policy.
- **Why**: F-014.1 needs persistent per-device ID для tracking «какие устройства используют какой config». Без stable ID — false ORPHAN detection (admin переустановил app, config думает что устройство «ушло»).
- **How (research targets)**:
  1. **Firebase Installations API** (`FirebaseInstallations.getId()`) — стабильный within app install, **сбрасывается при uninstall + reinstall**. Pro: zero permissions. Con: reset on reinstall.
  2. **Android ID** (`Settings.Secure.ANDROID_ID`) — сбрасывается при factory reset; **разный per app signing key** на Android 8.0+. Pro: zero permissions. Con: per-signing-key inconsistency.
  3. **Custom UUID в SharedPreferences + Android Auto Backup** (`android:allowBackup="true"` + backup_rules.xml inclusion) — UUID кладётся в cloud backup, восстанавливается при reinstall если у пользователя включён cloud backup. Pro: zero permissions, переживает reinstall если backup on. Con: дополнительная сложность, fallback нужен если backup off.
  4. **Combination**: FID для primary identity + Auto Backup UUID как fallback — industry recommendation для 2025.
- **Constraints (user-specified 2026-05-29)**:
  - НИКАКИХ дополнительных permission requests (no READ_PHONE_STATE, no privileged permissions).
  - НИКАКИХ запросов разрешений у Google / Android system.
  - Только что предоставляет system по умолчанию.
- **When**: BLOCKER для F-014.1 (server backup phase). Не блокирует F-014.0 (local-only).
- **Status**: 🟡 OPEN RESEARCH — обязательно решить до начала F-014.1 implementation.
- **Origin**: User raised 2026-05-29 при обсуждении F-014 named configs activeDeviceIds tracking.

### TODO-RESEARCH-010: Local→server config migration UX при first Google Sign-In 🟡

- **What**: Дизайн UX flow при первом Google Sign-In admin'а, у которого уже есть local-only named configs (F-014.0 → F-014.1 transition).
- **Why**: Admin использовал local-only (F-014.0), накопил configs. F-4 (Google Sign-In) реализуется. Admin first time logs in. Что показывается?
- **How** (per user decision 2026-05-29):
  1. После Google Sign-In success → pull server configs для этого account.
  2. Compare с local state.
  3. Если local **empty** → apply server default → silent.
  4. Если local **non-empty** → modal dialog с вариантами:
     - «Заменить серверным default» (потеря локальной работы — confirmation needed).
     - «Сохранить локальное как новый named config» → prompt for name.
     - «Игнорировать сервер, продолжать локально» (skip server backup — opt-out privacy mode).
  5. После выбора — sync continues, остальные server configs доступны для apply.
- **When**: Параллельно F-014.1 implementation.
- **Status**: 🟡 OPEN — design phase before F-014.1 plan.
- **Origin**: User decision 2026-05-29.

### TODO-FUTURE-RESEARCH-011: Concurrent edit merge UI/UX 🟢

- **What**: Дизайн UX для **merge dialog** при concurrent edit conflict в named configs / pair configs. Сейчас optimistic concurrency (спека 008) детектит конфликт, но UX для merging еще не designed.
- **Why**: Без хорошего merge UX admin будет терять работу при concurrent edits. Это **не блокер MVP** (rare scenario), но обязательно до production release.
- **How (research targets)**:
  1. Git-style merge UI — visual side-by-side diff.
  2. Notion-style — keep both versions as branches.
  3. Google Docs — automatic merge без UI (CRDT).
  4. Per-field selection — пользователь выбирает «my version / their version» per field.
- **When**: Параллельно F-014.1, до production release.
- **Status**: 🟢 OPEN RESEARCH.
- **Origin**: Deferred from F-014 clarify 2026-05-29.

### TODO-FUTURE-PRODUCT-001: Family Messenger 🟢

- **What**: Отдельное Android-приложение для family communication (group chat, direct messages, voice messages, location sharing).
- **Why**: Vision (см. [docs/product/future/ecosystem-vision.md](../product/future/ecosystem-vision.md)). Конкурентное преимущество launcher'а — пресеты, **не** messenger; разделение продуктов сохраняет focus.
- **How** (когда настанет время):
  1. Скопировать verbatim из `specs/013-family-group-foundation/spec.md` (archived) разделы: Group/Membership domain model, Envelope encryption (N>1 recipients), Server arbitration via Cloudflare Worker, Audit log Tier 1/Tier 2, Edge cases.
  2. Reuse из launcher'а: Pair primitive (spec 007), EncryptedMediaStorage (spec 011), AeadCipher / AsymmetricCrypto (spec 011).
  3. Новое: GroupRepository, MembershipRepository, envelope с N>1 recipients, signed membership operations.
- **When**: post-MVP, после market validation launcher'а.
- **Status**: 🟢 VISION — не в текущем roadmap'е.
- **Origin**: User vision 2026-05-28, ecosystem-vision.md §Family Messenger.

### TODO-FUTURE-PRODUCT-002: Family Album / Content Viewer 🟢

- **What**: Отдельное Android-приложение для shared family media (photos, videos, care notes) с role-based access (medical worker sees CareContent но не FamilyContent).
- **Why**: Vision. Альтернатива — интеграция с Google Photos SDK; **build-vs-integrate** decision отложен до validation.
- **How** (когда настанет время):
  1. Group primitive из ecosystem-vision.md (повторно используется messenger'ом).
  2. Decision: build from scratch (reuse envelope encryption) vs integrate Google Photos (faster, less control).
- **When**: post-MVP.
- **Status**: 🟢 VISION.
- **Origin**: User vision 2026-05-28, ecosystem-vision.md §Family Album.

### TODO-FUTURE-PRODUCT-003: Family Flow / activity dashboard 🟢

- **What**: Отдельное Android-приложение — family activity timeline, upcoming events (birthdays, doctor appointments), health summaries из managed devices.
- **Why**: Vision. Тип контента, который не вписывается ни в launcher (это не tile), ни в messenger (это не conversation).
- **How**: TBD.
- **When**: post-MVP.
- **Status**: 🟢 VISION.
- **Origin**: User vision 2026-05-28.

### TODO-FUTURE-RESEARCH-004: Cross-app ecosystem identity / group sync 🟢

- **What**: Когда пользователь устанавливает пресет, который **bundles** launcher + messenger + album, должен ли он регистрироваться / Google Sign-In / создавать family group **один раз** или **в каждом app отдельно**?
- **Why**: UX friction при separate registration vs architectural complexity при shared identity. Privacy trade-off (больше cross-app integration = больше attack surface).
- **How** (research questions to answer when ≥2 ecosystem apps exist):
  1. Single identity vs per-app identity.
  2. Centralized "group manager" service vs first-installed-app-becomes-manager.
  3. IPC story между launcher и messenger (Bound services? Content provider? Share intents?).
  4. Version compatibility (launcher v2 + messenger v1).
- **When**: когда launcher MVP стабилен И есть signal на adjacent product (messenger или album).
- **Status**: 🟢 OPEN RESEARCH.
- **Origin**: User raised 2026-05-28, ecosystem-vision.md §Cross-app group sync.

### TODO-FUTURE-SPEC-005: Personal vault для admin'ского долгосрочного storage 🟢

- **What**: Личный архив admin'а на сервере (документы, workflow config), зашифрованный pre-derived ключом из strong password admin'а. PBKDF2 / Argon2id key derivation. Восстановление при смене устройства через ввод пароля.
- **Why**: Admin теряет / меняет телефон → его personal workflow / documents восстанавливаются. **Это другой примитив**, не group и не pair. Решение 2026-05-28: in MVP.
- **How** (когда настанет время для спеки):
  1. `PersonalVault` domain port.
  2. PBKDF2 key derivation с salt (per-admin).
  3. Strong password validation (минимум 12 символов, complexity rules).
  4. Server-side rate-limiting на попытки login (защита от brute-force).
  5. Fail-closed на forgot password (никакого recovery — данные потеряны).
- **When**: после F-1 (Multi-pair admin management) и F-5 (ConfigDocument encryption), до production release. Может быть параллельно с F-4 (AuthProvider).
- **Status**: 🟢 OPEN — MVP-critical per user 2026-05-28.
- **Origin**: User vision 2026-05-28.

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

### TODO-ARCH-007: Persistent flow storage (`FlowRepository.addFlow` сейчас in-memory) 🟡

- **What**: `MockFlowRepository.addFlow()` хранит созданные runtime flows в `mutableListOf` поверх JSON-ассетов. После перезапуска процесса добавленные пользователем вкладки исчезают.
- **Why**: Минимально жизнеспособный путь для интерактивного теста pairing'а в спеке 007 (нужно «Управление телефонами» как inline-flow с FAB). Полноценное хранилище flows было в плане спека 005, но в имплементацию не попало.
- **How**: Добавить SQLDelight-таблицу `flow_descriptors` (или DataStore JSON-list), миграционно загружать дефолтные flows из JSON-ассетов один раз, дальше — write-through. Wire-format спека 005 должен иметь `schemaVersion`.
- **When**: Когда понадобится: (а) персистентный admin-flow со списком привязанных Managed-устройств, либо (б) пользовательское создание flow в спеке 008/009.
- **Status**: 🟡 OPEN
- **Origin**: Спек 007 inline-test session 2026-05-13 — minimal addFlow API введён в `FlowRepository` interface без write-through; см. коммит с `MockFlowRepository.runtimeFlows`.

### TODO-ARCH-008: Real admin-side device list (replace `admin_devices` empty-state stub) 🟡

- **What**: Сейчас `FlowScreen` при `templateId == "admin_devices"` рендерит фиксированный «Нет привязанных устройств» + FAB «Сканировать QR». Реального списка из Firestore (`/links where adminId == currentUid`) нет.
- **Why**: Минимально достаточный путь для тестирования камеры/сканера. Полноценный список — это admin-mode (спек 009).
- **How**: Добавить `ManagedDevicesRegistry` port в `:core/api/link/` с `observeAll(): Flow<List<Link>>`. Firebase adapter: `firestore.collection("links").where("adminId", "==", currentUid)`. Реализовать в FlowScreen `templateId == "admin_devices"` рендеринг списка `Link` + revoke на свайп.
- **When**: Спек 009 (admin-mode-flows).
- **Status**: 🟡 OPEN
- **Origin**: Спек 007 inline-test session 2026-05-13.

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
- **Status**: ✅ DONE 2026-05-19 (spec 010 Phase 2 + T029-T040). Implementation commits:
  - `5659664` feat(010): Phase 2 ARCH-016 closure (T029..T040) — SlotToActionMapper,
    ConfigBackedFlowRepository, deletion of MockFlowRepository + `flows_mock_*.json`,
    HomeComponent observable flow.
  - Spec 010 Phase 6 (commit `408d9f0`) added LocalLinkRevocationStore filter so
    locally-revoked links stop emitting через ConfigBackedFlowRepository.
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

### TODO-ARCH-017: Push foundation extraction в отдельные repos 🟢

- **What**: Вынести `core/push/` (KMP module) в отдельный Maven artifact `com.familycare:push-client` и `workers/push/` (TypeScript Worker) в отдельный git repo `github.com/familycare/push-worker`. **auth-jwt module** (`workers/_shared/auth-jwt/`) вынести **отдельно** — он independent от push, см. TODO-ARCH-018.
- **Why**: Push foundation спроектирован в спеке 019 (F-5c) как **reusable infrastructure** для 9+ known consumers (config-updated, sos-triggered, battery-critical, message-arrived, call-incoming, album-photo-added, pairing-accepted, entitlement-expired, caregiver-invited). Сейчас (Phase 1) живёт в monorepo — правильно для единственного consumer (launcher). При появлении второго независимого приложения (V-2 Messenger) — extraction избавит от двойного maintenance.
- **How**:
  - `workers/push/` уже extraction-ready: independent `package.json`, `wrangler.toml`, deploy через `wrangler deploy`. Один command: `git subtree split --prefix=workers/push -b push-worker` → push в новый repo.
  - `core/push/` extraction: `mv core/push/ ../push-client/` + настроить Maven publishing (publish to Maven Central или GitHub Packages) + заменить `implementation(project(":core:push"))` на `implementation("com.familycare:push-client:1.0.0")`.
  - Pre-extraction hygiene (поддерживать с первого commit, чтобы extraction остался ~1 день работы):
    - НЕ добавлять в `core/push/` зависимости от launcher-specific модулей (`core/launcher/*`, `feature/*`).
    - НЕ позволять Android-specific типам утекать в public API (`core/push/api/` package).
    - Любое расширение wire-format — через `schemaVersion` bump.
- **When**: Триггер — начало работы над спекой **V-2 Elderly-Friendly Messenger** (Phase 4), которая будет отдельным приложением. До этого — design discipline поддерживает extraction-readiness без работы.
- **Status**: 🟢 OPEN (триггер: V-2 spec start)
- **Origin**: Spec 019 F-5c architectural discussion 2026-06-20 evening. Полная планировка в [server-roadmap.md SRV-PUSH-EXTRACTION](server-roadmap.md#srv-push-extraction-push-foundation--отдельный-repo-spec-019-f-5c-future).

### TODO-ARCH-018: Auth-JWT module extraction в отдельный package 🟢

- **What**: Вынести `workers/_shared/auth-jwt/` (TypeScript module для Firebase ID-token verification — jose, JWKS cache, claims validation) в отдельный npm package `@familycare/auth-jwt` (private или published).
- **Why**: Auth-jwt module spec 019 F-5c намеренно spроектирован как **separate concern** от push transport. Сейчас живёт в `workers/_shared/auth-jwt/` внутри monorepo, importable любыми Worker'ами через relative path. При появлении второго Worker (V-3 album metadata, или any future Worker который нужно auth) — extraction в standalone package избавит от relative imports + позволит independent versioning.
- **Critical for own-server migration**: при переезде на own backend (per [SRV-PUSH-FOUNDATION](server-roadmap.md)) auth-jwt module — **первый кандидат на портирование**. Чёткий API без push-coupling позволяет: «возьмите эту библиотеку, замените `iss === securetoken.google.com/...` на ваш auth provider, остальное работает».
- **How**:
  1. Add `workers/_shared/auth-jwt/package.json` с `"name": "@familycare/auth-jwt"`, `"private": true` (или `"private": false` если решим публиковать).
  2. Configure `workers/push/package.json` to depend on `@familycare/auth-jwt` через `file:../_shared/auth-jwt` (current setup) or `workspace:*` (если уйдём в npm workspaces).
  3. При extraction в отдельный repo: setup CI для publishing + Worker depends on published version.
- **When**: 
  - **Now (Phase 1)**: module создаётся как `workers/_shared/auth-jwt/` в monorepo — это уже **первый шаг extraction** (clear boundary, own package.json).
  - **Future trigger**: появление второго Worker который тоже imports auth-jwt → consider extraction в отдельный repo.
- **Status**: 🟢 OPEN (initial scaffolding делается в Spec 019 F-5c; final extraction — future trigger).
- **Origin**: Spec 019 F-5c clarify pass + scenarios discussion 2026-06-21. Принято что JWT verification — НЕ push concern, должно жить отдельно с первого commit.

### TODO-ARCH-019: F-5c pull-fallback hook — explicit `onAppForeground()` nomenclature 🟢

- **What**: F-5c FR-038 говорит «recipient converges via existing pull-on-app-open path (`ConfigSaver.loadOwn(...)` invoked at app foreground per F-5b)». Но в F-5b (spec 018, merged) **нет явного hook'а** `ConfigSaver.onAppForeground()` / `onAppStart()` — `loadOwn` invoked где попало (ConfigSaverImpl callers). F-5c полагается на mechanism который semantically существует, но не имеет first-class API.
- **Why**: Если в будущем `ConfigSaver.loadOwn` consumers расходятся (несколько call-sites, разные lifecycle moments) — push-failure-fallback может молча сломаться. Сейчас "работает because all call-sites cover the path", но без явного контракта это easy to break.
- **How** (когда возникнет необходимость):
  1. Добавить в `ConfigSaver` (или сопутствующий port) метод `onAppForeground()` или `refreshAll(): Outcome<Unit, _>` с явной семантикой «catch-up after possible push miss».
  2. Single call-site: `MainActivity.onResume()` / `LauncherApp.processLifecycleObserver` → `coroutineScope.launch { configSaver.onAppForeground() }`.
  3. F-5c FR-038 пере-formulate'ся: «recipient converges via `ConfigSaver.onAppForeground()` invoked at process foreground per V-1».
- **When**: Триггер — V-1 «activity hub / launcher resume policy» spec; ИЛИ когда push reliability deteriorates and need observable refresh path.
- **Status**: 🟢 OPEN (F-5c works without this; nomenclature gap, не functional gap).
- **Origin**: User feedback на F-5c implementation 2026-06-21 — "F-5c должен явно сказать «я делегирую catch-up на ConfigSaver.onAppStart()», без этого юзер увидит stale config после force-stop, не поймёт почему".

### TODO-ARCH-020: V-2 messenger — hybrid FCM + persistent connection architecture 🟢

- **What**: V-2 Family Messenger потребует real-time presence (typing indicators, online status, delivered receipts) — это **за пределами F-5c push** (which is delivery-without-presence). Решение в индустрии: hybrid FCM (background notifications) + WebSocket/LiveQuery (foreground active session). WhatsApp/Signal используют именно эту модель.
- **Why**:
  - F-5c `BackgroundDispatcher` (WorkManager-based) **не подходит** для long-lived WebSocket: WorkManager constraint violations, Doze kills socket, foreground service для socket = постоянная иконка в статусбаре (UX expensive).
  - Гибрид: FCM wakes app for "new message" notifications + при app в foreground открывается WebSocket для realtime presence + foreground service (с visible notification) **только** когда active call/typing.
  - F-5c решение НЕ закрывает дорогу (additive): `LiveQuery` port вводится отдельно от `PushTrigger`, существуют параллельно.
- **How** (когда дойдёт до V-2 messenger spec):
  1. Добавить port `LiveQuery` (или `RealtimeChannel`) — separate от `BackgroundDispatcher`. NOT replacement, **additive**.
  2. Спроектировать гибридную модель: FCM-only path (default), FCM + WebSocket promotion при active session.
  3. Foreground service policy: visible notification «{N} active chats» только когда WebSocket держится; никогда «Launcher активен» permanent.
  4. Battery budget: измерить FCM-only baseline vs hybrid, document accepted overhead в спеке.
- **When**: V-2 «Elderly-Friendly Messenger» spec start. До этого — никакого preemptive abstraction (rule 4).
- **Status**: 🟢 OPEN (out of F-5c scope; not blocked by F-5c design).
- **Origin**: User architectural concern на F-5c implementation 2026-06-21 — "WhatsApp и Signal делают именно гибрид — FCM в background, WebSocket только в foreground".

### TODO-ARCH-021: PushTrigger naming reconsideration at extraction trigger 🟢

- **What**: Port `family.push.api.PushTrigger` назван по EFFECT'у («trigger push»). Слово «push» — semi-transport-leaky (FCM concept, хотя и applies к APNs / future protocols). Альтернативы для рассмотрения при extraction: `EventNotifier` (too vague), `RemoteEventDispatch` (verbose), `BackgroundEventChannel` (channel = WebSocket vibes — confusing).
- **Why**: Сейчас наименование embedded в 15+ files (`PushTrigger`, `PushTriggerError`, `PushTriggerRequest`, `PushHandler`, `PushHandlerRegistry`, `PushPayload`) и в callers (ConfigSaver, future SOS/album/messenger). Rename СЕЙЧАС = no functional benefit + diff noise. Rename в TODO-ARCH-017 (extraction trigger) — extraction уже breaking change, naming pass идёт one go.
- **How** (когда extraction начнётся):
  1. Bikeshed proposal: `PushTrigger` → `EventNotifier` или `EventDispatch`. Принять перед `git subtree split`.
  2. Soft-rename: keep old name as `@Deprecated typealias` в первой extracted version → consumers migrate.
  3. Hard-remove typealias на schemaVersion bump.
- **When**: TODO-ARCH-017 trigger (V-2 spec start).
- **Status**: 🟢 OPEN (defer until extraction).
- **Origin**: User feedback на F-5c implementation 2026-06-21 — "port назван по тому что снаружи (плохо) vs port назван по тому что внутри хочет caller (хорошо)".

### TODO-ARCH-022: V-2 FakeLiveQuery contract — eventual consistency simulation 🟢

- **What**: Когда добавим `LiveQuery` port (TODO-ARCH-020), его fake adapter ОБЯЗАН симулировать eventual consistency Firestore (snapshot latency, write→listener propagation delay 100ms-2s). Иначе test suite green но prod багает на race conditions.
- **Why**: F-5c fakes (`FakePushTrigger`, `FakeBackgroundDispatcher`) — synchronous (no eventual consistency concern — push fire-and-forget). Но `LiveQuery` semantically отличается: подписка возвращает stream snapshots, между write и snapshot есть latency. Tests с synchronous fake → false confidence. CLAUDE.md rule 6 (mock-first) требует чтобы fake adequately моделировал real behavior.
- **How** (когда LiveQuery port появится):
  1. `FakeLiveQuery` принимает `simulatedWriteLatency: Duration` constructor parameter (default 200ms).
  2. `subscribe(query): Flow<Snapshot>` emits initial snapshot immediately, subsequent snapshots — after configured latency relative к `write()` calls.
  3. Test helpers: `advanceTimeBy(...)` для kotlinx-coroutines-test integration.
  4. Property test: write→subscribe в любом order'е → eventual consistent state observed within `latency × 2` window.
- **When**: V-2 messenger spec start (когда `LiveQuery` port вводится).
- **Status**: 🟢 OPEN (forward-looking design discipline).
- **Origin**: User architectural concern на F-5c implementation 2026-06-21 — "FakeLiveQuery должен правильно эмулировать eventual consistency Firestore (не возвращать данные мгновенно), иначе тесты будут зелёными, а прод будет багать".

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

## Localization

### TODO-LOCALE-002: Refactor `RootContent` legacy hardcoded Russian strings → string-table 🟡

- **What**: `core/src/commonMain/kotlin/com/launcher/ui/RootContent.kt` содержит ≥ 3 hardcoded Russian string literals в Composable вызовах (FR-039 violation pattern):
  - `title = "Здоровье устройства"` (PhoneHealthIndicatorScreen invocation, спек 009 era).
  - `text = "Реальный QR будет здесь после реализации pairing (spec 007)."` (placeholder dialog).
  - Various `"Закрыть"` / `"Понятно"` fallback labels в SettingsScreen et al.

  Спек 010 закрыл свою часть (`ChallengeGateLabels` data class threaded via `RootContent` parameter; HomeActivity resolves Android `stringResource(R.string.challenge_gate_cancel)` / `challenge_gate_sequence_instruction`). Остальные violations — pre-existing pattern, требует separate refactor pass.

- **Why**: CLAUDE.md rule 1 + спек 010 FR-039 запрещают hardcoded user-facing strings в commonMain UI. Каждый violation увеличивает cost'а добавления второго языка (en, kk, uk и т.д.) и нарушает Article XII §3 Required Context.

- **How**:
  1. Найти все `text = "<кириллица>"` / `title = "<кириллица>"` в `RootContent.kt` + downstream Composables через `Grep -E '"[А-Яа-я]+[^"]*"' core/src/commonMain`.
  2. Для каждого: добавить ресурс в `app/src/main/res/values/strings.xml` + `values-ru/strings.xml`.
  3. Расширить `RootContent` parameter list по `ChallengeGateLabels` pattern: `screenLabels: ScreenLabels` data class содержит все необходимые localized strings.
  4. Host (HomeActivity) resolves через `getString(R.string.…)`.
  5. Konsist gate: добавить `RootContentLocalizationTest` который grep'ит RootContent.kt на Cyrillic literals в `Text(text = ...)` / `Text(...)` invocations.

- **When**: До onboarding'а второй локали (en) для Play Store global rollout. **Не блокирует** спек 010 ship — current launcher single-locale ru-RU production target.

- **Status**: 🟡 OPEN

- **Origin**: спек 010 `/speckit.analyze` post-implementation (2026-05-20) Scan F — 3 net-new violations introduced by спека 010 closed via `ChallengeGateLabels` parameter; pre-existing RootContent legacy strings escalated to this entry для proper codebase-wide refactor.

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

### TODO-SPEC010-PHYS-001: Full physical-device QA pass перед публичным релизом 🔴 PHYSICAL DEVICE

- **What**: Прогнать спек 010 на **реальном устройстве** (минимум Pixel 4a-class + один Samsung + один Xiaomi). Это umbrella-задача — конкретные сценарии описаны в [smoke-checkpoint.md](../../specs/010-setup-assistant/smoke-checkpoint.md), [perf-checkpoint.md](../../specs/010-setup-assistant/perf-checkpoint.md), [senior-safe-walkthrough.md](../../specs/010-setup-assistant/senior-safe-walkthrough.md).
- **Why**: Phase 8 спека 010 закрыт code-complete, но 6+ smoke-задач (T052/T053/T065/T093/T102/T106) + macrobenchmark SC-002 (T107) + APK delta SC-009 (T108) + senior-safe walkthrough (T105) **deferred** из-за отсутствия физических устройств. Эмулятор покрывает не всё: 2-tap call UX, TalkBack focus order, OEM-specific battery quirks (Xiaomi `SecurityException`), real cold-start timing, физический haptic feedback при 7-tap — всё это observable только на реальном железе.
- **Checklist** (когда появится устройство):
  - [ ] T052/T053 — wizard end-to-end Android 13+ + GMS-less fallback (см. TODO-SPEC010-EMU-002/003)
  - [ ] T064/T065 — call 2-tap UX + TalkBack CANCEL-first (см. TODO-SPEC010-EMU-004/005)
  - [ ] T079/T080 — fresh install `!N≥2` + grants → `N=0` (см. TODO-SPEC010-EMU-006)
  - [ ] T093 — unlink offline → reconnect → Firestore revoke ≤ 60 sec (см. TODO-SPEC010-EMU-007)
  - [ ] T102 — TalkBack 7-tap → challenge walkthrough (см. TODO-SPEC010-EMU-008)
  - [ ] T106 — OEM matrix Samsung/Xiaomi/Pixel (см. TODO-SPEC010-DEV-001)
  - [ ] T107 — macrobenchmark SC-002 cold-start ≤ 1 sec p95 на Pixel 4a (см. TODO-SPEC010-EMU-009)
  - [ ] T108 — APK delta SC-009 release build ≤ +500 KB vs спек 9
  - [ ] T105 — senior-safe walkthrough на 5 elder users (см. TODO-SPEC010-DEV-002)
  - [ ] Update [smoke-checkpoint.md](../../specs/010-setup-assistant/smoke-checkpoint.md) — заменить inline-TODO `physical-device:*` на actual smoke log entries с device model + Android version + дата.
  - [ ] Update [perf-checkpoint.md](../../specs/010-setup-assistant/perf-checkpoint.md) — записать измеренные p95 cold-start + APK delta.
- **When**: До first public release. Pre-Play-Store gate.
- **Status**: 🔴 OPEN
- **Origin**: спек 010 Phase 8, post-impl analyze 2026-05-20 (`/speckit.analyze` deferred items), memory `reference_testing_environment.md`.

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

### TODO-FUTURE-SPEC-006: onboarding-and-tutorials (внутреннее обучение admin + Managed) ✅ ЧАСТИЧНО ЗАКРЫТО спеком 015 (F-3)

- **Status update 2026-06-17**: Managed (бабушка) first-launch wizard + tutorial-hint subsystem (FR-023..FR-025 spec 015) **закрывают** один из четырёх направлений этого TODO — Managed onboarding с extensible TutorialHintManager. Остальные три (admin onboarding, in-app contextual help для admin'a, видеоинструкции / Lottie-ассеты, help-screen) остаются открытыми и здесь.

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

### TODO-FUTURE-SPEC-007: symmetric-pairing-bidirectional-control (двусторонний pairing) 🟢

- **What**: После `consent.allow` оба устройства пары получают возможность стать управляющими по взаимному согласию. Сейчас (спеки 7-9 + 011) модель строго односторонняя admin→Managed.
- **Why**: В discussion-сессии спека 011 (2026-05-21) пользователь зафиксировал видение «все телефоны равноценны, любой может управлять любым после pairing'а». В 011 это вынесено отдельным спеком (см. spec 011 §Clarifications C-1), потому что требует изменения Security Rules + UX consent flow + расширения pairing semantics — затрагивает 3 уже смерженных спека.
- **How**: Firestore Security Rules расширяются на симметричный case; UI «разреши мне видеть твою раскладку»; crypto-инфраструктура из 011 (per-device key pairs, `RecipientResolver`) уже поддерживает — менять её не нужно.
- **When**: После production-стабилизации 011. Не ранее реальных user requests на двустороннее управление.
- **Status**: 🟢 OPEN
- **Origin**: spec 011 mentor discussion 2026-05-21.
- **Roadmap entry**: [Spec 015](../product/roadmap.md#spec-015--symmetric-pairing-bidirectional-control).

### TODO-FUTURE-SPEC-008: family-group-shared-encryption (групповые ключи в стиле WhatsApp) 🟢

- **What**: Понятие «семейная группа» — N≥3 устройств с общим членством. Любое зашифрованное медиа в группе грузится один раз; envelope содержит `recipients` для всех N членов. Управление членством через приглашения от существующих членов; key rotation при выходе участника.
- **Why**: В discussion-сессии 011 пользователь чётко высказал: «доверенное устройство = семья, не пара». В 011 это вынесено отдельным спеком, но crypto-инфраструктура 011 уже membership-agnostic (envelope `recipients` — массив произвольной длины, see [spec 011 C-2](../../specs/011-contacts-and-e2e-encrypted-media/spec.md)). Этот спек добавляет `GroupRecipientResolver` (вторая реализация интерфейса из 011), management UX и Firestore схему групп.
- **How**: Новый Firestore namespace `/groups/{groupId}/`. Group key — общий симметричный, обновляется при выходе участника. Crypto-протокол шифрования blob'ов остаётся прежним из 011 (только меняется содержимое `recipients`).
- **When**: После production-стабилизации 011 и появления реального user request на семейный pooling.
- **Status**: 🟢 OPEN
- **Origin**: spec 011 mentor discussion 2026-05-21.
- **Roadmap entry**: [Spec 016](../product/roadmap.md#spec-016--family-group-shared-encryption).

### TODO-FUTURE-SPEC-009: multi-device-recovery (восстановление при потере телефона + multi-device для одного владельца) 🟢

- **What**: Понятие `ownerId` — несколько физических устройств одного владельца имеют общий identity. При шифровке envelope содержит `recipients` для **всех** устройств владельца. При потере одного устройства — re-pairing нового устройства, добавление в `ownerId`; старые медиа доступны через оставшиеся устройства.
- **Why**: В 011 при потере одного из устройств пары без revoke медиа становятся недоступны (зашифрованы только для потерянного устройства). Это accepted trade-off для 011 — настоящий e2e. Этот спек закрывает кейс «у одного человека телефон+планшет», а также «бабушка получила новый телефон — внук вернул её конфиг».
- **How**: `MyDevicesRecipientResolver` (третья реализация интерфейса из 011). Optional Android Backup Service для owner-key (восстанавливаемый при re-installation). Альтернатива — Shamir secret sharing across trusted contacts.
- **When**: После 011, по приоритету выше чем ~016 если user feedback покажет частое «потерял телефон».
- **Status**: 🟢 OPEN
- **Origin**: spec 011 mentor discussion 2026-05-21.
- **Roadmap entry**: future multi-device-recovery spec (TBD number; spec 017 was reassigned to F-4 AuthProvider 2026-06-18 — see ADR-008 numbering note).

### TODO-FUTURE-SPEC-010: key-rotation-forward-secrecy (защита от компрометации устройств) 🟢

- **What**: Периодическое обновление ключей пары/группы; forward secrecy для новых сообщений; manual key rotation при подозрении на компрометацию устройства.
- **Why**: В discussion 011 пользователь поднял вопрос «компрометация одного устройства = компрометация всей цепи». В 011 принята модель single-key per device без rotation — accepted trade-off. Этот спек добавляет periodic rotation и forward secrecy (ratchet-style schemes, например Signal's Double Ratchet или X3DH).
- **How**: envelope `cipherSuiteId` (из 011) уже допускает версионирование. Новые blob'ы шифруются новыми ключами; старые остаются доступны через deprecated ключи (не удаляются сразу). Полный ratchet — серьёзная сложность, стартовая модель — periodic rotation без ratchet.
- **When**: После 011 + 016 (групповая ротация требует group-aware rotation).
- **Status**: 🟢 OPEN
- **Origin**: spec 011 mentor discussion 2026-05-21.
- **Roadmap entry**: [Spec 018](../product/roadmap.md#spec-018--key-rotation-forward-secrecy).

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

## Spec 011 mentor session 2026-05-23 — architecture follow-ups

Эти 8 entries появились в одной mentor-сессии 2026-05-23 при обсуждении распределения крипто-фундамента между лаунчером и будущим Jitsi-мессенджером. Все они вне scope текущего спека 011 (фундамент), но **должны быть зафиксированы**, чтобы будущие спеки строились на согласованных архитектурных решениях.

Подробный transcript решений — в [ADR-008](../adr/ADR-008-social-recovery-architecture.md) (recovery) и в обновлённом spec 011 §Clarifications.

### TODO-SPEC011-C1-CLARIFY: уточнить trust model в spec 011 §Clarifications C-1 🟡

- **What**: Текущая формулировка C-1 «односторонняя пара admin→Managed» неоднозначна. В mentor-сессии 2026-05-23 уточнено: роль admin/managed это **per-pairing**, не **per-device**. Одно устройство может участвовать в нескольких pairings с разными ролями. Двусторонний контроль = две независимые pairings в обе стороны.
- **Why**: Без уточнения Phase 1 (T010-T028) рискует определить API под неверной model assumption. `DeviceIdentity` per-link, `RecipientResolver` per-link уже корректны — нужно только обновить **формулировку**, не код.
- **How**: Отредактировать `specs/011-contacts-and-e2e-encrypted-media/spec.md` §Clarifications C-1: заменить «односторонняя модель» на «one-way per pairing; device can hold multiple pairings with different roles; bidirectional control = two pairings».
- **When**: **До старта Phase 1** (T010).
- **Status**: 🟡 OPEN — закрывается одновременно с этим backlog-entry (см. коммит того же mentor-session block'а).
- **Origin**: spec 011 mentor session 2026-05-23.

### TODO-AUTH-001: AuthProvider port + Firebase Email/Password adapter 🟡

- **What**: Ввести `AuthProvider` port в `core/commonMain/api/auth/` + `FirebaseEmailAuthProvider` adapter в `androidMain`. Port абстрагирует identity provider; следующие adapters (SMS gateway, Telegram OAuth, own backend) добавляются без изменения domain-кода.
- **Why**: Без named identity невозможен recovery (TODO-RECOVERY-001) и cross-app SSO (TODO-SSO-001). В mentor-сессии 2026-05-23 пользователь подтвердил переход на email+password как baseline named auth. Abstraction нужна, чтобы переезд с Firebase Auth на свой backend / SMS / Telegram не требовал переписывания (CLAUDE.md rule 1+4).
- **How**:
  - Port: `AuthProvider { signIn(Credentials): Result<UserIdentity, AuthError>; signOut; observeIdentity: Flow<UserIdentity?> }`
  - `sealed interface Credentials { EmailPassword; PhoneOtp; TelegramToken; ... }`
  - `UserIdentity(externalId, displayId, providerKind)` — externalId используется как корень всех wire-formats (recovery backup, pairing identity proof)
  - Firebase Email/Password — бесплатно на Spark plan (не требует Blaze)
- **When**: Отдельный спек **TBD** — prerequisite для future multi-device-recovery spec (TBD; spec 017 reassigned to F-4 AuthProvider 2026-06-18 — see ADR-008 numbering note).
- **Status**: 🟡 OPEN
- **Origin**: spec 011 mentor session 2026-05-23.
- **Exit ramp**: переход на свой backend через новый `OwnBackendAuthProvider` adapter; existing wire-formats используют `externalId`, не email напрямую — миграция users через delegation-flow или silent re-auth. Refs `server-roadmap.md` SRV-CRYPTO-001.

### TODO-RECOVERY-001: Social recovery (password + peer_nonce → HKDF → AEAD backup) 🟡

- **What**: Криптографическая схема восстановления ключей при потере устройства. Concretizes high-level TODO-FUTURE-SPEC-009 с конкретным дизайном.
  - Setup phase (при первом pairing): бабушка задаёт passphrase (PIN 4-6 цифр); приложение генерирует `peer_nonce` 32 байта; `recovery_key = HKDF(passphrase, peer_nonce, "recovery-v1")`; `encrypted_backup = AEAD(recovery_key, priv_keys_bundle)`; `encrypted_backup` → сервер, `peer_nonce` → encrypted для trusted peer через 011 envelope, `passphrase` → в голове бабушки.
  - Recovery phase: новое устройство → email+password login → server initiates 2FA push к trusted peer → peer тапает «подтвердить» → peer device пере-шифровывает `peer_nonce` для freshly-generated Pub нового устройства → новое устройство просит passphrase → derives `recovery_key` → decrypts `encrypted_backup` → получает старые priv keys → доступ к старым blob'ам восстановлен.
- **Why**: Без recovery потеря телефона = безвозвратная потеря всех зашифрованных blob'ов (включая медкарты). Pure E2E + named auth + peer-based 2FA даёт восстановление **без** server-side key escrow и **без** compromise privacy (peer не видит plaintext данных, только участвует как 2FA factor).
- **How**: см. [ADR-008](../adr/ADR-008-social-recovery-architecture.md). MVP — 1-of-N peer authorization (любой trusted peer достаточен). Future — N-of-M через Shamir Secret Sharing (2-of-3 для устойчивости к single-peer loss).
- **When**: Future multi-device-recovery spec (TBD number; spec 017 reassigned to F-4 AuthProvider 2026-06-18). Зависит от TODO-AUTH-001 (named identity prerequisite) **и** F-CRYPTO 016 (`KeyDerivation`, `AeadCipher`, `AsymmetricCrypto.sealCEK`/`unsealCEK`).
- **Status**: 🟡 OPEN — supersedes high-level TODO-FUTURE-SPEC-009 с конкретным crypto design.
- **Origin**: spec 011 mentor session 2026-05-23.

### TODO-SSO-001: Cross-app SSO via intent delegation 🟢

- **What**: Когда лаунчер и Jitsi-мессенджер (отдельное приложение) оба установлены и в одном залогинен пользователь — второе приложение может получить auth token делегацией через intent, без повторного ввода email+password.
- **Why**: UX improvement для пользователей с обоими приложениями. Baseline SSO-A (re-enter credentials) работает всегда; SSO-B убирает лишний ввод когда один app уже залогинен. Не критично, но приятно для бабушек.
- **How**:
  - Лаунчер экспортирует AIDL service (или Activity с intent action) `com.launcher.action.DELEGATE_AUTH`
  - Мессенджер при первом запуске проверяет `PackageManager.getPackageInfo("com.launcher.app")` + signature check (same dev key)
  - Если лаунчер найден и залогинен — мессенджер запрашивает delegation token через intent
  - Лаунчер показывает диалог пользователю «Мессенджер запрашивает доступ. Разрешить?»
  - При согласии → лаунчер обращается на сервер `POST /auth/delegate` с current session + target package
  - Сервер возвращает one-time delegation_token (TTL 5 минут)
  - Лаунчер возвращает токен через intent result → мессенджер обменивает на свою session
- **When**: Отдельный спек **TBD** (post-renumerации) — после того как Jitsi-мессенджер реально стартует разработкой. Зависит от TODO-AUTH-001.
- **Status**: 🟢 OPEN
- **Origin**: spec 011 mentor session 2026-05-23.
- **Notes**: Никогда не передавать plaintext password через IPC — только короткоживущий delegation token. Signature pinning обязателен (защита от подделки package name).

### TODO-VENDOR-001: Vendor integration security pattern (TLS pinning + JWT) 🟢

- **What**: Reference-архитектура для будущих vendor integrations (клиники, центры поддержки пожилых, health бракелеты-как-API, network of caregivers). Эти integrations используют **REST/gRPC API**, **не** наш envelope.
- **Why**: Vendor channels — B2B, не e2e. Используются: TLS pinning (защита от MITM на vendor cloud), JWT с Ed25519 signing (request authentication; primitive из 011 годится), optional mTLS (если vendor требует client cert — отдельный X.509 adapter через Bouncy Castle, **не** наш envelope per spec.md §Out-of-scope).
- **How**:
  - OkHttp CertificatePinner с pinned SHA-256 fingerprints per vendor
  - Ed25519 sign из 011 `DigitalSignature` port для JWT claims signing
  - X.509 client cert (если vendor требует) — отдельный adapter в `core/androidMain/adapters/vendor-pki/` через Bouncy Castle / Conscrypt
  - Per-vendor adapter module (см. CLAUDE.md rule 2 — ACL для каждого внешнего API)
- **When**: При появлении первого vendor integration спека (TBD).
- **Status**: 🟢 OPEN — концепт-задача, не имеет триггера сейчас.
- **Origin**: spec 011 mentor session 2026-05-23 §«Будущие клиенты фундамента 011».

### TODO-LEGAL-002: Legal review для медицинских данных (152-ФЗ + 323-ФЗ + Play Store medical) 🟡

- **What**: В mentor-сессии 2026-05-23 пользователь подтвердил, что «значимые приватные документы, мед карты» — целевой use-case spec 012 (личные документы). Это поднимает регуляторную планку.
- **Why**: Медданные в РФ — **специальная категория** персональных данных (152-ФЗ ст. 10). Требуется:
  - **Локализация** (152-ФЗ ст. 18 ч. 5) — серверы в РФ. Firebase Storage за пределами РФ — потенциальный риск, **но** E2E шифрование снимает большую часть требований (server не имеет plaintext доступа)
  - **Explicit informed consent** для медданных (не общее)
  - **323-ФЗ** ограничения на медицинскую документацию (ст. 22) — мы **не оператор медучреждения**, поэтому формально не подпадаем под все требования, но spirit закона требует осторожности
  - **Play Store** — Google требует privacy policy + Data Safety form для health-related apps; отдельный review process для категории Medical
- **How**:
  - Получить юр-консультацию (РФ-юрист по 152-ФЗ + Google Play лицензированию)
  - Уточнить формулировку UI: называть «значимые личные документы», **не** «медкарта» — снижает юр. экспозицию
  - Privacy policy с явным указанием категории данных и их защиты
  - Подготовить ответы на Google Play Data Safety form
  - **НЕ** хранить metadata, явно указывающие на медицинский характер blob'ов (наш envelope opaque — это уже хорошо)
- **When**: До публикации UI с упоминанием «медкарт» (spec 012); **до** публикации в Play Store.
- **Status**: 🟡 OPEN
- **Origin**: spec 011 mentor session 2026-05-23.

### TODO-DESIGN-001: Продуктовая модель «приватные vs shared» документы 🟡

- **What**: В mentor-сессии 2026-05-23 уточнено: «приватные документы доступны только владельцу; если документами поделились, они доступны всем с кем поделились». Этот product-level pattern требует UX-дизайна для spec 012 (visible feature).
- **Why**: От ответа зависит envelope `recipients[]` для каждого blob'а:
  - **Приватные** — `recipients = [только владелец]` — никто кроме владельца не видит, при потере без recovery = потеря данных
  - **Shared** — `recipients = [владелец + получатели]` — все из recipients видят
- **How**: UX-design.md для spec 012 должен покрывать:
  - При загрузке документа — UI выбора «приватный / поделиться с (кем)»
  - Default — приватный
  - UI «кто видит этот документ» (список recipients) + возможность revoke share (требует пере-шифровки blob'а без revoked recipient — это **отдельная** complexity, см. ниже)
  - Warning при выборе «приватный»: «При потере телефона эти документы будут потеряны, если не настроен recovery»
  - Special category — `medical/health` documents должны быть приватными по default (legal hygiene per TODO-LEGAL-002)
- **Tricky case — revoke share**: если бабушка поделилась документом с внуком и хочет отозвать — blob уже у внука на устройстве, **нельзя «удалить» расшифрованную копию** retrospectively. Можно только пере-зашифровать blob на сервере без recipient'а внука + локально удалить (но если внук уже скачал — оффлайн копия остаётся). Это надо честно отразить в UX: «отзыв share не удаляет уже скачанные копии».
- **When**: При работе над spec 012 (visible feature — фото контактов и личных документов).
- **Status**: 🟡 OPEN
- **Origin**: spec 011 mentor session 2026-05-23.

### TODO-PRIVACY-001: Server-side metadata minimization 🟢

- **What**: Даже при E2E шифровании content'a, сервер видит **metadata**: IP-адреса при auth, timestamps Storage uploads, размеры blob'ов, pairing graph (кто с кем спарен через Pub-key references). Это **inevitable cost** server-mediated архитектуры, но можно минимизировать.
- **Why**: В mentor-сессии 2026-05-23 surface'нуто как 20%-ный остаточный privacy concern. Не катастрофа, но **hygiene matters** — особенно при компрометации сервера.
- **How**:
  - **Не сохранять IP-адреса** дольше 24h в server logs (Cloudflare Worker logs + Firebase audit logs)
  - **Не индексировать** pairing graph в queryable виде (если злоумышленник попадёт на сервер — let it be tedious to extract)
  - **Future**: anonymous credentials (Privacy Pass, anonymous tokens) — позволяют auth без раскрытия user identity на каждый request. **Сложная архитектура**, не для текущего scope
  - **Future**: Tor-onion routing или P2P для metadata-free connectivity — несовместимо с FCM push на Android, не для нас
- **When**: Server-roadmap — при переезде на свой backend (SRV-CRYPTO-001 / 015+). До тех пор — ограничено Firebase/Cloudflare retention policies.
- **Status**: 🟢 OPEN — не блокер, hygiene improvement.
- **Origin**: spec 011 mentor session 2026-05-23.

---

## Closed items (✅ historical reference)

*(пусто; добавлять по мере закрытия с датой и reference)*

---

## Workflow

**Добавление нового item'а**: append в правильную секцию, выбрать `🔴/🟡/🟢`, заполнить What/Why/How/When/Origin.

**Закрытие**: переместить в "Closed items", добавить `✅ дата + коммит-ссылка`.

**Промоция в активный спек**: если item становится частью текущего спека — копировать в `specs/<id>/tasks.md`, оставить здесь с note «In progress in spec NNN — task TXXX». После завершения спека → переместить в Closed.

**Re-review**: при каждом `/speckit.analyze` — проверять item'ы со статусом `🔴` и `🟡`, актуальны ли.


### TODO-PREFS-001: UserPreferences ContentProvider для care family ecosystem 🟢

- **What**: F-3 (spec 015) ships `UserPreferences` (theme, fontScale, languageOverride) persisted локально. Care family ecosystem (messenger app, photos app, voice-call app) тоже должны respect-ить эти настройки — сейчас каждое приложение хранит свои дубликаты.
- **Why**: Per FR-051 + memory `project_managed_naming_convention.md` — ecosystem apps share Managed identity. Senior-friendly UX требует, чтобы шрифт/тема/язык были консистентны через все приложения.
- **How**: Когда вторая ecosystem app shipps — добавить Android `ContentProvider` (`com.launcher.preferences.provider`) на `UserPreferencesStore`. Authority namespaced на app-family. Read permission shared между apps signed одним keystore. Write — только launcher. Other apps subscribe via `ContentObserver` для live theme changes.
- **When**: Когда вторая ecosystem app (messenger) materializes. Не блокирует F-3 ship.
- **Status**: 🟢 OPEN
- **Origin**: spec 015 FR-051 + C-31 (UserPreferencesStore as future cross-app surface).


### TODO-WIZARD-001: Translation skill — Claude API HTTP wiring 🟢

- **What**: `core/scripts/translate-strings.main.kts` сегодня печатает list keys-to-translate но **не** делает HTTP POST к https://api.anthropic.com/v1/messages. Need to wire the actual call с anthropic-version header, prompt assembly из CONTEXT.json + GLOSSARY.md, response parsing, XML write-back.
- **Why**: Skill SKILL.md describes the workflow, но без working HTTP call manual translation остаётся the only way.
- **How**: kotlin-stdlib `HttpURLConnection` (zero deps). System prompt instructing strict XML escaping. Body: list of `(key, EN value, per-key context)` per locale.
- **When**: До first speckit-tasks run after F-3 implementation, когда новые strings потребуют translation.
- **Status**: 🟢 OPEN
- **Origin**: spec 015 FR-031a + .claude/skills/procedure-translate-spec-strings/SKILL.md.


### TODO-WIZARD-002: AI-translation quality review для AR/HI/ZH/JA/KK 🟢

- **What**: F-3 ships Claude-generated stubs для 9 не-base локалей. Human review pending per OUT-005a.
- **Why**: AI translation качество для AR/HI/ZH/JA/KK может быть variable; senior persona expects natural phrasing in native script.
- **How**: native-speaker contractor review pass; capture corrections в CONTEXT.json как pinned overrides.
- **When**: Phase 4 (per spec.md Effort estimate). Не блокирует F-3 ship.
- **Status**: 🟢 OPEN
- **Origin**: spec 015 OUT-005a.
