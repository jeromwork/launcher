---
id: TASK-115
title: 'Decision: Family app onboarding chain via Install Referrer'
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

У нас будет семейство приложений: **лаунчер** (сейчас разрабатывается), **мессенджер** (Phase-3+), **фотоальбом** (Phase-3+). Каждое — отдельный APK в Google Play, отдельная песочница Android, отдельные ключи шифрования.

**Проблема (research-established)**: standalone установка каждого family app **всегда** требует отдельного passphrase (или отдельного social recovery flow). Каждое приложение независимо, у него нет доступа к состоянию другого через стандартные Android механизмы (`sharedUserId` deprecated, IPC требует user consent). Значит бабушка на новом устройстве при трёх приложениях = **три раза passphrase**. Для 82-летней — блокер adoption.

**Единственный acceptable канал** для «one-click» recovery = передача handoff'а через **Google Play Install Referrer** с sealed encrypted payload от уже-trusted family app'а. Research (2026-07-08) подтвердил: это работающий transport (persistent 90 дней, ~500 байт binary payload, verified через Google Play), safety обеспечивается client-side crypto (sealed_box), не server trust.

**Модель — chain of trusted anchors**:

Любое уже-восстановленное family app может **пригласить установку** следующего family app'а через Install Referrer с sealed handoff. Иерархии между app'ами нет — **любой уже-trusted app = anchor** для установки следующего.

```
Passphrase → Launcher (trusted anchor)
                ↓ (Install Referrer chain)
             Messenger (trusted anchor)
                ↓ (Install Referrer chain)
             Photo Album (trusted anchor)
                ↓ (Install Referrer chain)
             Future family app N ...
```

**Порядок установки — flexible**:
- Обычный путь: launcher первый (passphrase) → мессенджер через launcher → фотоальбом через мессенджер / launcher.
- Alternative: мессенджер первый (passphrase) → launcher через мессенджер → фотоальбом через любой.
- Каждое app при recovery через passphrase становится trusted anchor для остальных.

**Что происходит по шагам (chain link)**:

1. Пользователь в уже-trusted app (пусть launcher) видит плитку «Установить мессенджер» с описанием.
2. Launcher идёт на наш сервер (аутентифицирован своим JWT): «сохрани opaque token с id X, TTL 15 минут». Сервер записывает `{token_id, issued_by, expires_at, redeemed=false}` — **не знает содержимого**.
3. Launcher локально формирует `sealed_box(messenger_public_key, {issuer_identity_id, timestamps})` + прикладывает `token_id`.
4. Launcher открывает `market://details?id=com.family.messenger&referrer=base64(sealed||token_id)`.
5. Google Play устанавливает мессенджер, привязывает referrer к install (persist 90 дней).
6. Мессенджер после install читает `InstallReferrerClient.getInstallReferrer()`.
7. Мессенджер расшифровывает sealed_box своим private key → знает `issuer_identity_id`.
8. Мессенджер идёт на наш сервер: `POST /v1/opaque-tokens/redeem {token_id}` → сервер возвращает `{valid: true, issued_by}`. Мессенджер сверяет — должны совпадать.
9. Мессенджер показывает **iconic pairing challenge** (TASK-116): три иконки, пользователь тапает совпадающую с той что launcher показывает как overlay.
10. При успешном match — attestation infrastructure (TASK-117) выдаёт мессенджеру recovery key через launcher как attestor'а.
11. Мессенджер восстанавливает свой backup **без passphrase**.

Мессенджер теперь **сам** — trusted anchor. Если пользователь захочет установить фотоальбом — та же chain link, но issuer = мессенджер (уже unlocked, знает своё issuer_identity_id).

## Зачем

**Единственный способ добиться one-click recovery** для family app cluster. Standalone app без chain = passphrase на каждое приложение. Research показал: это не тот вопрос где «отложить», это единственная работающая архитектура для нашего UX target'а.

**Симметричный механизм — не иерархия**. Любой trusted app может быть anchor. Значит:
- Порядок установки flexible.
- Не нужно спецкода «только launcher может приглашать».
- Установка N приложений семейства = N-1 chain links (первое через passphrase).

**Правильные production references** — Play Install Referrer используется Branch, AppsFlyer, Adjust для deferred deep-linking с payload. Firebase Dynamic Links (deprecated 2025) официально заменяются на Install Referrer. Google рекомендует этот путь.

**Zero-knowledge сервер**: сервер видит только opaque tokens (single-use, TTL). Не знает содержимого handoff'а, не знает какое приложение redeem'ит, не знает социального графа семьи. Соответствует TASK-108 T0 metadata privacy tier.

## Что входит технически (для AI-агента)

**Client — trusted anchor side (любое family app в этой роли)**:
- Domain port `FamilyAppInviter` в `core/onboarding/` — генерация handoff'ов через сервер + Play Store URL.
- Adapter `PlayStoreInviterAdapter` — Play Store URL construction + Install Referrer coordination.
- **Cross-app attestation key** — генерируется при первом setup, wrapped в root_key, публичный ключ публикуется в identity-link (spec 017 F-4).
- Registry of known family apps (`app_id → public_key + display metadata`) — как part of preset (rule 9 shareability).
- Плитка в UI с описанием family app'а которое можно установить.
- Overlay UI (SYSTEM_ALERT_WINDOW permission, graceful fallback if denied).

**Client — installee side (только что установленное family app)**:
- Domain port `FamilyAppOnboarding` — receiver of handoff.
- Adapter `InstallReferrerAdapter` — reads referrer через `com.android.installreferrer:installreferrer` library.
- Sealed_box open с own private key.
- Attestation request к issuer через TASK-117 mechanism.
- Fallback flow: если referrer пустой → standard recovery (passphrase или social recovery).
- **После successful recovery — installee сам становится anchor** (может приглашать других family apps).

**Server**:
- `POST /v1/opaque-tokens/store` — JWT-authenticated anchor создаёт token.
- `POST /v1/opaque-tokens/redeem` — public endpoint, single-use redeem.
- Full spec — [server-roadmap.md § SRV-OPAQUE-TOKENS-001](../../docs/dev/server-roadmap.md#srv-opaque-tokens-001-opaque-token-store-for-cross-app-handoff-mentor-session-2026-07-08).

**Wire format**:
- Referrer payload: `base64url(sealed_box_bytes || token_id_32bytes)`.
- Sealed_box content: `{ schemaVersion, issuer_identity_id, issuer_app_id, issued_at, valid_until }`.
- schemaVersion discipline per TASK-16.

**Consumed components**:
- **TASK-116** — iconic pairing challenge component (UI + deterministic SVG icons from seed).
- **TASK-117** — universal attestation mechanism (issuer выступает как attestor для installee).
- **TASK-101** — peer confirmation flow (fallback path когда issuer не рядом).

## Состояние

**Discussion, 2026-07-08.** Проведён предварительный research (Signal / WhatsApp / Google / Microsoft / Bitwarden / 1Password / Matrix cross-app patterns; Play Install Referrer API mechanics). Модель уточнена (2026-07-08): не иерархия launcher-anchor + spoke, а **chain of symmetric trusted anchors** — любое recovered family app может пригласить следующее.

**Ещё открыто**:
- Формальный Decision block (Choice / Rationale / Applies to / Trade-offs / Exit ramp) не написан.
- Cross-platform (Android → iPhone) не решён — Install Referrer только Android.
- iOS handoff mechanism — parked до iOS релиза.
- Точная модель attestation key rotation / invalidation при compromise (взаимодействие с TASK-103 remote lock).
- UX overlay permission fallback (Xiaomi MIUI и OEM quirks).
- Preset fields для onboarding (какие app'ы известны семье, какие иконки в плитке).
- Как installee узнаёт public keys других family apps (чтобы стать anchor для них) — статический registry в коде vs dynamic fetch из сервера vs shared через identity-link.

---

## Пример сценария (use-case)

**Family segment — chain**:

Бабушка Валентина потеряла планшет. Дочка Таня купила новый Xiaomi Pad, поставила launcher, помогла бабушке пройти recovery через passphrase. Launcher restored, contacts восстановлены.

Через 3 дня Таня в launcher'е видит плитку «Мессенджер для семьи. Защищённый чат.» Тапает. Google Play → «Установить» → через минуту мессенджер восстановлен через chain link (iconic challenge на планшете, бабушка выбрала иконку). Passphrase не вводили.

Через неделю — плитка «Семейный фотоальбом» **в мессенджере** (или в launcher'е — оба уже trusted). Тапает. Google Play → «Установить» → через минуту фотоальбом восстановлен. Passphrase не вводили.

Итого: **один passphrase** для трёх приложений. Каждое приложение стало trusted anchor после своего recovery.

**Alternative starting point**:

Пользователь-энтузиаст сначала ставит мессенджер (услышал в новостях). Passphrase через social recovery. Потом решает установить launcher — плитка в мессенджере «Полноценная замена главного экрана». Тапает. Launcher устанавливается через chain link из мессенджера — тот же механизм, роли поменяны.

**Sideload / первое app сегмент**:
Первое устанавливаемое family app всегда через passphrase (или через standalone social recovery). Chain link работает только для второго и последующих.

<!-- SECTION:DESCRIPTION:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 — 2026-07-08 (mentor)

**Pre-discussion state**: обсуждение начиналось как «cross-app trust bootstrap» без чёткой модели. Первые два подхода (broker через ContentProvider AIDL / server-mediated sealed handoff требующий passphrase в spoke) были отвергнуты владельцем.

**Iteration 1 — «launcher-as-anchor» model (superseded)**:
Владелец сначала предложил модель «launcher = trusted zone, мессенджер и фотоальбом = subordinate». Design частично отражал это (иерархия anchor → spoke).

**Iteration 2 — «chain of symmetric anchors» (current model, 2026-07-08)**:
Владелец уточнил (правильно): **любое recovered family app** может быть anchor для установки следующего. Не только launcher.

> «Если мы будем устанавливать друг другу — то есть мессенджер установили, а фотоальбом мы устанавливаем через ссылку из мессенджера — тогда там тоже работает тот же механизм.»

Это фундаментально симметричный механизм — не иерархия. Причины принятия:
- Симметрия упрощает архитектуру (один mechanism, не two special cases).
- Гибкость установки (пользователь может начать с любого app).
- Reuse (chain работает для N приложений через одну механику).

**Ключевое наблюдение владельца** — social recovery mechanism (когда доверенные пиры подтверждают recovery заявителя) **криптографически идентичен** cross-app trust: в обоих случаях уже-доверенный участник подписывает attestation о заявителе. Значит нужна **общая attestation infrastructure** (TASK-117), не два отдельных механизма.

**Research inputs**:

- **Cross-app trust patterns research (2026-07-08)** — Signal / WhatsApp / Google / Microsoft / Bitwarden / 1Password / Matrix. Все production патерны используют **anchor** (broker app, Play Services). Симметричный «sibling apps vouch for each other» не используется. НО — в нашем случае anchor меняется (не всегда launcher), так что мы адаптируем pattern.
- **Play Install Referrer research (2026-07-08)** — mechanism работает, ~500 байт binary payload, sealed_box для confidentiality, Google Play гарантирует «click был через Play Store» (не sideload).

**Threat model** (draft):
- Google Sign-In compromise → атакующий не может выпустить valid handoff (нет доступа к cross-app-attestation-key issuer'а), не может пройти iconic challenge (не видит правильную иконку).
- Fake anchor-клон → создаёт свой sealed_box, но installee не расшифрует (не знает `installee_public_key`).
- Rooted device / screen record → sealed_box в query string не расшифровывается без private key; nonce single-use, TTL 15 мин.
- Physical device theft → закрывается TASK-103 remote lock (стирание keys → нет права выпускать attestations).

**Priority reasoning (why high)**:
- Research established: **no other channel** для one-click recovery. Без TASK-115 = passphrase на каждое family app при recovery. Это блокер adoption для elderly users.
- One-way door: архитектура identity-link `cross_app_attestation_key` должна быть в **первой версии** launcher'а которая идёт в production. Retrofit после = breaking change для installed base.
- Значит TASK-115 blocks messenger release, не «optional enhancement».

**Open questions для Session 2**:

1. **Формализация threat model** — конкретно перечислить attacker capabilities и защиту для каждого.
2. **Cross-platform strategy** — что делаем когда issuer на Android, а installee на iPhone. Fallback на standard social recovery через TASK-117?
3. **Iconic challenge scale** — сколько иконок показывать (3 vs 6), сколько base icons в library (20 vs 30), какие randomization dimensions.
4. **Overlay permission fallback** — как gracefully деградировать на Xiaomi MIUI / Huawei EMUI где overlay блокируется.
5. **Family app registry** — где хранить `{app_id → public_key + display_metadata}`. Preset field per TASK-16? Server-side directory? Static в коде?
6. **Attestation key lifecycle** — rotation policy? invalidation при TASK-103 remote lock?
7. **Preset differentiation** — какие поля per-segment (family vs clinic vs self-managed).
8. **First app path** — первое устанавливаемое family app всегда через passphrase. UX ожидание установлено правильно?

### Decision (English, mutable pre-implementation) 🔒

*Not yet written. Discussion Session 2 will formalize.*

<!-- SECTION:DISCUSSION:END -->
