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

## Closed items (✅ historical reference)

*(пусто; добавлять по мере закрытия с датой и reference)*

---

## Workflow

**Добавление нового item'а**: append в правильную секцию, выбрать `🔴/🟡/🟢`, заполнить What/Why/How/When/Origin.

**Закрытие**: переместить в "Closed items", добавить `✅ дата + коммит-ссылка`.

**Промоция в активный спек**: если item становится частью текущего спека — копировать в `specs/<id>/tasks.md`, оставить здесь с note «In progress in spec NNN — task TXXX». После завершения спека → переместить в Closed.

**Re-review**: при каждом `/speckit.analyze` — проверять item'ы со статусом `🔴` и `🟡`, актуальны ли.
