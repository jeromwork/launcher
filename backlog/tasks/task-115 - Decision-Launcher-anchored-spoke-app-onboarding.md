---
id: TASK-115
title: 'Decision: Launcher-anchored spoke app onboarding'
status: Discussion
assignee: []
created_date: '2026-07-08 06:16'
updated_date: '2026-07-08'
labels:
  - decision
  - crypto
  - ux
  - onboarding
  - phase-2
milestone: m-1
dependencies:
  - TASK-101
  - TASK-105
  - TASK-108
  - TASK-116
  - TASK-117
priority: high
ordinal: 115000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

У нас будет семейство приложений: **лаунчер** (сейчас разрабатывается), **мессенджер** (Phase-3+), **фотоальбом** (Phase-3+). Каждое — отдельный APK в Google Play, каждое с отдельной песочницей Android, отдельными ключами шифрования.

**Проблема**: когда бабушка теряет планшет и покупает новый — при текущей архитектуре она проходит **отдельную процедуру восстановления в каждом приложении**. Для 82-летней это неприемлемо.

**Пересмотр модели (2026-07-08)**: лаунчер это не «равное с мессенджером» приложение, а **anchor / trusted zone** — база, из которой пользователь работает. Мессенджер и фотоальбом это **spoke apps**, которые получают доверие от лаунчера. Соответствует production паттернам (Google Play Services + Gmail/Drive, Microsoft Authenticator + Outlook/Teams).

**Решение (в разработке)**: лаунчер после своего восстановления через passphrase становится anchor. Установка мессенджера идёт **через плитку внутри лаунчера** → Google Play с Install Referrer'ом, содержащим sealed handoff → мессенджер после install читает referrer + подтверждает через **iconic pairing challenge** (TASK-116) → recovery мессенджера идёт через **attestation infrastructure** (TASK-117) без повторного ввода passphrase.

**Что происходит по шагам (draft flow, будет уточняться в mentor-сессии):**

1. Бабушка в лаунчере видит плитку «Установить мессенджер» с описанием.
2. Лаунчер идёт на наш сервер (аутентифицирован своим JWT): «сохрани opaque token с id X, TTL 15 минут». Сервер записывает `{token_id, issued_by, expires_at, redeemed=false}` — не знает содержимого.
3. Лаунчер локально формирует `sealed_box(messenger_public_key, {launcher_identity_id, timestamps})` + прикладывает `token_id`.
4. Лаунчер открывает `market://details?id=com.family.messenger&referrer=base64(sealed||token_id)`.
5. Google Play устанавливает мессенджер, привязывает referrer к install (persist 90 дней).
6. Мессенджер после install читает `InstallReferrerClient.getInstallReferrer()`.
7. Мессенджер расшифровывает sealed_box своим private key → знает `launcher_identity_id`.
8. Мессенджер идёт на наш сервер: `POST /v1/opaque-tokens/redeem {token_id}` → сервер возвращает `{valid: true, issued_by}`. Мессенджер сверяет `issued_by` из ответа сервера с `launcher_identity_id` из sealed_box — должны совпадать.
9. Мессенджер показывает iconic pairing challenge (TASK-116): три иконки, бабушка тапает совпадающую с той, что лаунчер показывает как overlay (или в своём UI).
10. При успешном match — attestation infrastructure (TASK-117) выдаёт мессенджеру recovery key через лаунчер как локального attestor'а.
11. Мессенджер восстанавливает свой backup.

**Что бабушка увидела**: тап на плитке → Google Play "Установить" → возврат в лаунчер с подсказкой "выберите эту иконку в мессенджере: 🔥" → тап 🔥 в мессенджере → готово. **Без ввода passphrase**.

## Зачем

**UX для пожилых**: главная цель — **один passphrase** для всей семьи приложений. Passphrase бабушки — самый страшный шаг recovery (бумажка, глаза, память). Повторение passphrase 3 раза при установке 3 приложений блокирует adoption.

**Правильное разделение ролей**: лаунчер = anchor, spoke apps наследуют доверие. Соответствует production паттернам (Microsoft MSAL broker, Google Play Services SSO). Симметричный «sibling apps vouch for each other» — не используется в production ни у кого.

**Zero-knowledge сервер**: сервер видит только opaque tokens (single-use, TTL). Не знает содержимого handoff'а, не знает какое приложение redeem'ит, не знает социального графа семьи. Соответствует TASK-108 T0 metadata privacy tier.

## Что входит технически (для AI-агента)

**Client — launcher side**:
- Domain port `SpokeAppOnboarding` в `core/onboarding/` — генерация handoff'ов.
- Adapter `PlayStoreOnboardingAdapter` — Play Store URL construction + Install Referrer coordination.
- **Cross-app attestation key** — генерируется при первом setup лаунчера, wrapped в root_key, публичный ключ публикуется в identity-link (spec 017 F-4).
- Registry of known family apps (`app_id → public_key + display metadata`) — как part of preset (rule 9 shareability).
- Плитка в лаунчере с описанием мессенджера / фотоальбома.
- Overlay UI (SYSTEM_ALERT_WINDOW permission, graceful fallback if denied).

**Client — spoke app (messenger) side**:
- Domain port `AnchoredOnboarding` — receiver of handoff.
- Adapter `InstallReferrerAdapter` — reads referrer через `com.android.installreferrer:installreferrer` library.
- Sealed_box open с messenger's own private key.
- Fallback flow: если referrer пустой → standard social recovery через TASK-117.

**Server**:
- `POST /v1/opaque-tokens/store` — JWT-authenticated launcher создаёт token.
- `POST /v1/opaque-tokens/redeem` — public endpoint, single-use redeem.
- Full spec — [server-roadmap.md § SRV-OPAQUE-TOKENS-001](../../docs/dev/server-roadmap.md#srv-opaque-tokens-001-opaque-token-store-for-cross-app-handoff-mentor-session-2026-07-08).

**Wire format**:
- Referrer payload: `base64url(sealed_box_bytes || token_id_32bytes)`.
- Sealed_box content: `{ schemaVersion, launcher_identity_id, issued_at, valid_until }`.
- schemaVersion discipline per TASK-16.

**Consumed components**:
- **TASK-116** — iconic pairing challenge component (UI + deterministic SVG icons from seed).
- **TASK-117** — social recovery + attestor infrastructure (лаунчер выступает как local attestor для мессенджера).
- **TASK-101** — peer confirmation flow (fallback path когда лаунчер не рядом).

## Состояние

**Discussion, 2026-07-08.** Проведён предварительный research (Signal / WhatsApp / Google / Microsoft / Bitwarden / 1Password / Matrix cross-app patterns; Play Install Referrer API mechanics). Пересмотрена модель: launcher = anchor, spoke apps = subordinate.

**Ещё открыто**:
- Формальный Decision block (Choice / Rationale / Applies to / Trade-offs / Exit ramp) не написан.
- Cross-platform (Android launcher → iPhone messenger) не решён — Install Referrer только Android.
- iOS handoff mechanism — parked до iOS релиза.
- Third family app (photo album) — тот же паттерн, но не sanity-checked отдельно.
- Точная модель attestation key rotation / invalidation при compromise (взаимодействие с TASK-103 remote lock).
- UX overlay permission fallback (Xiaomi MIUI и OEM quirks).
- Preset fields для onboarding (какие app'ы известны семье, какие иконки в плитке).

---

## Пример сценария (use-case)

**Family segment**:
Бабушка Валентина потеряла планшет. Дочка Таня купила новый Xiaomi Pad, поставила лаунчер, помогла бабушке пройти recovery через passphrase (бумажка в шкатулке). Лаунчер восстановлен, бабушкины контакты видны.

Через 3 дня Таня замечает — «мам, давай тебе мессенджер поставим, у нас в семейном чате внуки фотки шлют». В лаунчере на планшете бабушки — плитка «Мессенджер для семьи. Защищённый чат.» Таня нажимает.

Google Play открывается, бабушка (Таня подсказывает) нажимает «Установить». Через минуту — установлено. Возврат в лаунчер, лаунчер показывает overlay: **«Откройте мессенджер и выберите эту иконку: 🔥»**. Таня открывает мессенджер, видит три иконки — 💧🔥📖. Тапает 🔥. Мессенджер: «Восстанавливаем... готово». Список семейного чата загрузился.

**Clinic segment (Phase-3+ hypothetical)**:
Пациент клиники восстанавливает планшет после ремонта. IT-администратор клиники pre-provisioned планшет с launcher + set of clinic apps (мессенджер для связи с врачом, расписание визитов). Attestation flow тот же, но attestor'ом выступает не «дочка», а `clinic_admin_identity` — server-side флаг на recovery.

**Self-managed segment (Phase-3+)**:
Пользователь сам себе admin. Ставит launcher, потом мессенджер. Attestor'ом выступает **сам же launcher** на том же устройстве. Iconic pairing challenge защищает от automated attack с скомпрометированным Google account.

<!-- SECTION:DESCRIPTION:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 — 2026-07-08 (mentor)

**Pre-discussion state**: обсуждение начиналось как «cross-app trust bootstrap» без чёткой модели. Первые два подхода (broker через ContentProvider AIDL / server-mediated sealed handoff) были отвергнуты владельцем как неправильные:

1. **AIDL broker (Microsoft-style)** — Android-only, требует user consent при первом использовании (не «один клик»).
2. **Server-mediated sealed handoff** — требовал passphrase в spoke app'е (мессенджер всё равно спрашивает passphrase, лаунчер только «уменьшал доп. подтверждения»).

**Пересмотр модели** (владелец, 2026-07-08):

> «Лаунчер это должна быть safety space для облегчения работы, доверенная зона. Мессенджер и лаунчер это не равноценные приложения. Установка мессенджера должна начинаться из лаунчера через плитку.»

Это фундаментально другая роль: **launcher = trusted anchor**, spoke apps subordinate. Соответствует production паттернам (Google/Microsoft/Bitwarden).

**Ключевое наблюдение владельца** — social recovery mechanism (когда доверенные пиры подтверждают recovery заявителя) **криптографически идентичен** cross-app trust: в обоих случаях уже-доверенный участник подписывает attestation о заявителе. Значит нужно **не два отдельных механизма**, а **общая attestation infrastructure** (TASK-117) с двумя use cases.

**Уточнения архитектуры**:

- **Play Install Referrer** как transport для sealed handoff — research показал что это acceptable channel (Google гарантирует «click был через Play Store», integrity payload обеспечиваем sealed_box'ом, размер до ~500 байт binary).
- **Opaque server tokens** (zero-knowledge invariant) — сервер хранит только `{token_id, issued_by, expires_at, redeemed}`, не содержимое handoff'а. TASK-108 T0 compliance.
- **Iconic pairing challenge** (3-of-N SVG иконок, seeded random rendering) — второй фактор при attestation. Защищает от automated attack с скомпрометированным Google account. Больше подходит пожилым чем PIN 6 цифр.
- **Overlay UX в launcher'е** — избегаем context switching для пользователя, показ подсказки поверх других приложений.

**Threat model** (draft):
- Google Sign-In compromise → атакующий не может выпустить valid handoff (нет доступа к cross-app-attestation-key лаунчера бабушки), не может пройти iconic challenge (не видит правильную иконку).
- Fake launcher-клон → создаёт свой sealed_box, но мессенджер не расшифрует (не знает `messenger_public_key`).
- Rooted device / screen record → sealed_box в query string не расшифровывается без private key; nonce single-use, TTL 15 мин.
- Physical device theft (launcher stolen) → закрывается TASK-103 remote lock (стирание launcher's keys → нет права выпускать attestations).

**Open questions для Session 2**:

1. **Формализация threat model** — конкретно перечислить attacker capabilities и защиту для каждого.
2. **Cross-platform strategy** — что делаем когда launcher на Android, а мессенджер устанавливают на iPhone. Fallback на standard social recovery через TASK-117?
3. **Iconic challenge scale** — сколько иконок показывать (3 vs 6), сколько base icons в library (20 vs 30), какие randomization dimensions (color + rotation достаточно, или добавляем accents).
4. **Overlay permission fallback** — как gracefully деградировать на Xiaomi MIUI / Huawei EMUI где overlay блокируется.
5. **Family app registry** — где хранить `{app_id → public_key + display_metadata}`. Preset field per TASK-16? Server-side directory?
6. **Attestation key lifecycle** — rotation policy? invalidation при TASK-103 remote lock?
7. **Preset differentiation** — какие поля per-segment (family vs clinic vs self-managed).

### Decision (English, mutable pre-implementation) 🔒

*Not yet written. Discussion Session 2 will formalize.*

<!-- SECTION:DISCUSSION:END -->
