# Handoff: Оставшиеся темы обсуждения крипто-архитектуры TASK-67

> **⚠️ HISTORICAL — 2026-07-02 handoff snapshot**. Этот файл — исторический слепок, **не source of truth**.
>
> **Актуальный source of truth**: [`docs/architecture/crypto.md`](../architecture/crypto.md) + [`docs/dev/crypto-status.md`](crypto-status.md) + Decision blocks в `backlog/tasks/task-100..112`.
>
> **Ключевые правки против содержимого ниже** (не редактировать содержимое — читать через эту таблицу):
> | В этом файле | Актуально |
> |---|---|
> | `mls-rs` (AWS) через UniFFI | **openmls** (Rust, MIT, SRLabs audit 2024). `mls-rs` — exit ramp. |
> | Noise **XXpsk3** через snow | **Noise_XX** через snow (см. TASK-67). |
> | `Identity = stableId + identity_pub` | `identity_id = hash(root_public)` (TASK-106). `stableId` naming retired. |
> | Cloudflare KV recovery-blob + envelope-per-recipient | MLS group membership (envelope pattern упразднён). Recovery blob — да, envelope-per-recipient — нет. |
> | `TASK67-DISCUSSION-001..004` в project-backlog.md | Стал `backlog/tasks/task-N` через Backlog.md migration. |
> | «Пейринг v1 (spec/007)» | В процессе замены на v2 через TASK-67 Decision + TASK-102 + TASK-108. |
> | CANDIDATE-N list | Часть материализовалась в TASK-100..112, часть stale. Не полагаться. |
>
> **Что этот файл всё ещё даёт полезное**: инструкция для новых mentor-сессий (mentor-режим, простые слова, sequences), CANDIDATE-list как отправная точка, вопросы по темам 4-10 (частично закрыты TASK-100..108, частично остались открытыми — см. crypto-open-questions.md).
>
> **Continuation**: fresh AI session → читать [`crypto-status.md`](crypto-status.md) TL;DR, потом brand-new decision-tasks. Не полагаться на этот файл как authoritative.

**Дата:** 2026-07-02
**Контекст:** обсуждение крипто-архитектуры для TASK-67 (Pairing Feature) в mentor-режиме. Две темы уже разобраны, стек финализирован. Этот документ передаёт контекст новому агенту.

---

## Инструкция для нового агента

1. **Прочитай сначала:** [crypto-mentor-overview.md](crypto-mentor-overview.md) — self-contained объяснение архитектуры с диаграммами.
2. **Работай в mentor-режиме** (`.claude/skills/mentor/SKILL.md`) — простыми словами, без жаргона, критический stance, sequences.
3. **Язык общения:** русский, code/identifiers как есть.
4. **Пользователь — новичок** в крипто. Не соглашаться рефлекторно, критиковать выбор, surface adjacent concerns.
5. **Формат ответов:** пользователь просил краткость. Sequences + короткие списки вопросов лучше walls of text.
6. **Одна тема за раз.** Не смешивать.
7. Пользователь предпочитает **последовательности + примеры** для объяснения (наш пример + market leaders в подобных ситуациях).

---

## Что уже решено (не пере-обсуждать)

### Стек (финализирован после исследований)

- **libsodium-kmp (ionspin) 0.9.5** — крипто-примитивы. Уже в проекте.
- **snow (Rust)** через UniFFI — Noise XXpsk3 handshake для pairing.
- **mls-rs (AWS)** через UniFFI — MLS RFC 9420 для групповой криптографии и document sync.
- **Cloudflare Worker + Firestore** — delivery service (транспорт, без knowledge содержимого).

**Escape ramps** (записать когда пойдёт в код):
- swap `mls-rs` ↔ `OpenMLS` = 3-5 дней если `GroupCryptoPort` инкапсулирует их обоих
- Watch: Nostr/Marmot (Quartz KMP) Q4 2026 — единственный KMP-native MLS если созреет
- Watch: Iroh + p2panda-encryption — для future decentralized transport (замена Cloudflare/Firestore)
- Fallback для Noise XX если snow deprecates: hand-roll на libsodium (~150 строк, cacophony test vectors)

### Ключевые архитектурные решения

- **Identity** = `stableId` + `identity_pub` (derived из root_key через HKDF). Recovery на новом устройстве восстанавливает identity byte-in-byte.
- **Device** = per-install X25519 keypair. Public часть публикуется в Firestore directory.
- **Pairing** = Noise XXpsk3 handshake + MLS Add member в group. **Не** access-grant + envelope pattern (это была старая модель до MLS pivot).
- **Group encryption** = один MLS group на owner (бабушка + все её admin'ы). Все шифрование через group exporter key.
- **Document sync** = MLS application messages. Editable через LWW/CAS на updatedAt.
- **Recovery** = Cloudflare KV recovery-blob + Argon2id. Все buckets восстанавливаются через recovery-mode envelope.
- **Server видит:** ciphertext blobs, MLS commit messages (routing), KeyPackage directory (public), device pub-keys (public). **НЕ видит:** содержимое, ключи.
- **Zero-knowledge target:** ✅ на content level. ⚠️ metadata (кто чей admin, MLS group membership size, push frequency) остаётся видна серверу. Blind grants / anonymous credentials — Phase-5+.

### Trade-offs, которые пользователь принял

1. **Никакого external audit'а pre-ship** (budget $15k total). Митигация: fitness tests + threat model docs + post-launch bug bounty + академические review.
2. **iOS pairing deferred** — не MVP. TASK-26 Phase-4+.
3. **Snow / mls-rs — Rust toolchain в CI.** Один раз настроить, потом прозрачно.
4. **MLS require ordered commit delivery** — Firestore serverTimestamp даёт. При миграции на свой сервер помнить про очередь.

---

## Что уже разобрано (не пере-обсуждать)

### Тема 1 — Личность и корневой ключ

**Разобрано:** first-run, ежедневная работа, recovery, забыл passphrase. Signal PIN+SVR2, Apple iCloud Keychain HSM, Matrix SSSS, WhatsApp — сравнение.

**Решения:**
- SGX enclave — **не строим никогда** (Cloudflare не поддерживает + не нужно нашему масштабу).
- Наш подход = **Matrix-tier: Argon2id client-side + Cloudflare rate-limit + сильная passphrase floor**.
- Q1 (2FA hooks): пока не добавляем код-абстракцию (Article XI premature abstraction). Inline-TODO + server-roadmap SRV-2FA-001.
- Q2 (passphrase strength): floor в wizard'е 4 слова или 12+ знаков. TODO записан в project-backlog.
- Q3 (смена passphrase): consistent с Signal/Matrix — root key не меняется, KEK меняется.
- Q4 (physical access): BiometricPrompt re-auth для sensitive operations. TODO записан.
- Q5 (concurrent recovery): Firestore transaction lock 15 мин с force-override. Signal-style без 7-day холда.
- Q6 (SVR-tier): не строим. Matrix-tier достаточно для нашей аудитории.

**TODO'шки записаны в:** [project-backlog.md](project-backlog.md) как TASK67-DISCUSSION-001..004.

### Тема 2 — QR-рукопожатие

**Разобрано:** наша v1 (spec/007 без крипты), наша v2 план, Signal linked device, Matrix MSC1544, WhatsApp companion, Apple Continuity.

**Решения:**
- **Handshake:** Noise XXpsk3 через snow (не свой ECDH).
- Q1 (SAS emoji): opt-in, показываем 6 emoji, если расхождение — regenerate. Для clinic/B2B — mandatory через preset-level policy (CANDIDATE-2).
- Q3 (TTL): 90 сек для QR, 5 мин для manual code fallback.
- Q3 bonus (crash посреди handshake): всё заново, ephemeral_priv потерян = forward secrecy.
- Q5 (fallback без QR): manual code (8-12 цифр) через server-rendezvous. Admin набирает, senior только показывает.
- Q6 (`PairingChannel` port): узкий сейчас, extensible на additive не breaking.
- Q7 (session cleanup): cron on Cloudflare Worker, потом Redis EXPIRE.
- AC-4 (session-fixation): claimToken генерится client-side.
- AC-5 (time skew): всё timestamp'ы через serverTimestamp Firestore. Fitness rule: запретить `Clock.System.now()` в crypto-flows (CANDIDATE-3).

### Тема 3 — Profile как synced document (важный pivot!)

**Ключевое:** старая модель «команды через config-inbox» — забыть. Правильная модель — **whole document sync** через MLS group.

**Разобрано:** правильный glossary (Preset/Pool/Profile/ProfileStoreState), правильные Firestore пути, sequences edit/apply/conflict, multi-Admin, recovery.

**Решения:**
- **С pivot на MLS все access-grant / envelope-with-recipient-set упраздняются.** Заменяются MLS group membership.
- Q1 (recovery-mode envelope в коде): **проверка отложена**. RecipientResolver есть в коде (envelope-per-device pattern), но конкретно recovery-envelope duplication не подтвердил. С pivot на MLS этот вопрос трансформируется — теперь group state восстанавливается через MLS Welcome при recovery, не через envelope duplication.
- Q2 (conflict resolution): CAS с UI merge (TASK-70 scope, флаг).
- Q3 (grant graph открытый): MVP принимаем открытый, blind credentials → Phase-5+.
- Q4 (device pub-key revocation): mechanism через delete `/users/{stableId}/devices/{deviceId}` + MLS Remove commit — совмещённая операция.
- Q5 (sender-anonymity): Phase-5+ через blind tokens.
- Q6 (multi-Admin): да, с MVP через MLS group semantics.
- Editing lock (feature от пользователя): CANDIDATE-4. 20-мин TTL, per-session, force-override.
- Multi-device same identity: **НЕ второй pairing**. Recovery + MLS Add моего же нового устройства в все мои groups.

---

## Session-scoped CANDIDATE list (для backlog когда будем закрывать сессию)

Эти кандидаты возникли по ходу обсуждения. **Не решать сейчас**, но surface при финальном review:

1. **CANDIDATE-1:** Recovery notification + Old-device invalidation. Push старым устройствам «recovery на новом X, это ты?» + опция «kick старое устройство».
2. **CANDIDATE-2:** Profile-level SAS verification policy (`SasRequirement = Off | Optional | Mandatory`). Clinic/B2B forces Mandatory.
3. **CANDIDATE-3:** Fitness rule запретить `Clock.System.now()` в crypto-flows.
4. **CANDIDATE-4:** Editing lock document (20 мин TTL, per-session, force-override).
5. **CANDIDATE-5:** Encrypted co-admin directory (display names для UI multi-admin).
6. **CANDIDATE-6:** ~~External crypto audit~~ **заменено:** post-launch community bug bounty + академические review requests.
7. **CANDIDATE-7:** Noise XXpsk3 через snow (Rust) через UniFFI. Один Rust source для Android + iOS.
8. **CANDIDATE-8:** mls-rs (AWS) через UniFFI как основа group encryption + document envelope.
9. **CANDIDATE-9:** Watch tasks — Nostr/Marmot Q4 2026 + Iroh для future decentralized transport.

---

## Оставшиеся темы для обсуждения (4-11)

Разбирать по одной, в mentor-режиме, с последовательностями и market leader comparisons.

### Тема 4 — Отзыв связи (Revoke)

**Кратко:** owner отзывает admin'а (Таня Петю или наоборот). Механизм в MLS.

**Вопросы для разбора:**
- MLS Remove commit vs делать access-grant delete отдельно — что первично?
- Post-compromise security: старые данные — Таня уже видела; новые — не может расшифровать (MLS epoch key rotation). Это OK по threat model?
- «Мягкий revoke» (просто убрать из UI без крипто-invalidation) vs «жёсткий revoke» (MLS Remove) — UX категории.
- Что показывать отозванному admin'у в UI? Local cache остаётся, но данные stale.
- Revoke моего собственного устройства (украли телефон) — sequence.
- Group без admin'ов (owner отозвал всех) — что происходит с group?

**Sequences нужны:** owner отзывает admin, admin отзывает связь (peer symmetry?), device stolen self-revoke.

**Adjacent concerns:**
- Как узнать что связь была отозвана если ты offline долго?
- Audit trail revoke — публично видно в MLS commits, что зафиксировано?
- Recovery отозванного admin'а — новое pairing или permanent block?

### Тема 5 — Multi-device одной личности

**Кратко:** Таня добавляет планшет. Уже частично разобрано в блоке 3 mentor-overview файла. Углубить в вопросах governance между устройствами одной identity.

**Вопросы для разбора:**
- Который из моих устройств «первичный» (в MLS) или все равны?
- Что если я потерял «первичный» — planшет продолжает работать?
- Как добавить новое устройство когда старое офлайн (Signal требует primary online)?
- Ordering изменений: телефон и планшет одновременно edit'ают bab's Profile — как MLS это трактует?
- Push notifications на все мои устройства или только на «активное»?

**Sequences нужны:** добавление планшета через recovery + MLS Add, потеря первичного, конкурентная работа с одного identity двух устройств.

**Adjacent concerns:**
- Как проверить что новое устройство — реально моё, не подменённое?
- Устройство-зомби (не использовал 6 месяцев) — auto-cleanup?
- Bandwidth: MLS Welcome для каждого нового устройства — размер?

### Тема 6 — Dumb server + Zero-knowledge

**Кратко:** что видит сервер и почему только это. Наш Cloudflare Worker + Firestore + FCM. Article XX target из constitution.

**Вопросы для разбора:**
- **Metadata leak** — server видит: MLS group size, commit frequency, KeyPackage exchanges, push routing. Пользователь этого не видит. Правильно ли это?
- Blind tokens / anonymous credentials — Phase-5+. Что теряем оставив открытым в MVP?
- Sealed sender pattern (Signal) — стоит ли думать сейчас?
- Access-grants (теперь упразднены с MLS) → **но MLS Group ID тоже visible!** Server знает который user состоит в каких groups. Это тот же граф связей.
- Firebase Security Rules достаточно ли ограничивают? Или нужен свой Worker в каждый write path?
- Cloudflare Analytics видит трафик метаданные — приемлемо?
- Migration on own server: PostgreSQL + Redis vs S3-compatible — детали.

**Sequences нужны:** что видит сервер при pairing, при edit, при revoke. Сравнение с Signal (SVR2), Matrix (homeserver), WhatsApp (device list).

**Adjacent concerns:**
- Government legal request — что можно выдать?
- Backup для нас самих (случайно потеряли Cloudflare account)?
- Log retention policy на нашей стороне?

### Тема 7 — 1-к-1 vs групповое (MLS TreeKEM vs Sender Keys)

**Кратко:** мы уже выбрали MLS. Эта тема теперь становится validation темой: почему MLS правильно и как масштабируется до clinic (20 patients × 5 caregivers = 100 members).

**Вопросы для разбора:**
- MLS overhead для группы 100 членов — реалистичен?
- Signal Sender Keys как альтернатива — почему хуже для нашего use-case?
- Federated groups (Матрица federation-style) — не нужны, но каких свойств лишаемся?
- MLS Welcome bandwidth — размер при join в большую группу.
- Epoch counter overflow — не проблема, но осведомлённость.

**Adjacent concerns:**
- Group migration — если стек crypto изменится через 5 лет, как перенести группы?
- Cross-group operations (например, «выбрать контакт из моей семейной группы для добавления в клиническую группу»)?

### Тема 8 — Push-payload E2E при 4KB FCM constraint

**Кратко:** уже частично в блоке 7 mentor-overview. Углубить про SOS и large payloads.

**Вопросы для разбора:**
- Что делать когда encrypted payload > 4KB? Разбить на chunks + assemble на клиенте?
- SOS latency — критическая. FCM high-priority + inline encrypted payload — достаточно?
- FCM alternatives: MQTT (наш сервер), APNs (iOS), WebSockets. Что выбираем для non-Google TV, non-GMS Android?
- Silent push для sync trigger vs visible push для SOS — разные API.
- Battery cost polling vs push — измеримо?
- Что видит FCM про наш trafic (event types в plaintext поле)?

**Sequences нужны:** SOS end-to-end, обычный sync trigger, chunked large payload.

**Adjacent concerns:**
- Huawei без GMS — no FCM, что делаем?
- Google удалил приложение из FCM registry — что дальше?
- Push token rotation — Firebase changes token, мы должны detect?

### Тема 9 — Recovery propagation между сторонами

**Кратко:** когда Таня recovered, бабушкин телефон должен «узнать». Как это работает через MLS.

**Вопросы для разбора:**
- MLS Remove old device + Add new device — но кто это делает? Только Таня сама на своём новом? Или бабушка тоже должна подтвердить?
- Detect новое устройство peer'а: lazy (при следующей операции) или eager (push notification about identity change)?
- Между recovery Тани и обнаружением бабушкой — окно рассинхрона. Что происходит в это время?
- Trust на новое устройство — automatic (по MLS Add commit) или требует человеческого re-confirmation?

**Sequences нужны:** Таня recovered → она делает MLS commit на своих группах → бабушка (и Петя) обновляют view. Что если бабушка offline на момент commit'а?

**Adjacent concerns:**
- Attacker украл passphrase Тани → recovered на своём телефоне → делает MLS commits от её имени. Detect механизм?
- Recovery как forensics event — should we log это в audit?

### Тема 10 — Key rotation + Long-term forward secrecy

**Кратко:** что делать если identity_pub Тани утёк через 3 года. Как ротировать без потери связей.

**Вопросы для разбора:**
- Ротация identity vs ротация device keys — разные механизмы.
- MLS уже даёт per-epoch key rotation (для сообщений). Identity rotation — отдельно.
- Signal identity rotation flow — переиспользуем pattern?
- Что происходит с прошлыми MLS commits после identity rotation? — они signed старой identity, но verifiable если сохранили старый identity_pub в audit log.
- TASK-41 связка.

**Sequences нужны:** compromised identity workflow, planned rotation (юбилей рода).

**Adjacent concerns:**
- Cross-group implications — ротируешь identity, ты в 5 группах, все должны узнать.
- User UX для «сменил ключ, скажи peer'ам сверить фингерпринт».

---

## Ключевые файлы и локации

**Основной документ:**
- [docs/dev/crypto-mentor-overview.md](crypto-mentor-overview.md) — self-contained explanation с диаграммами.

**Правки внесены:**
- [docs/dev/project-backlog.md](project-backlog.md) — добавлены TASK67-DISCUSSION-001..004.

**Backlog task:**
- [backlog/tasks/task-67 - Pairing-Feature-And-Bucket.md](../../backlog/tasks/task-67%20-%20Pairing-Feature-And-Bucket.md) — статус `Draft` (не флипнули в `In Progress`, так как обсуждение архитектуры до speckit.specify).

**Существующая пейринг-инфраструктура (v1, будет заменена):**
- [core/src/commonMain/kotlin/com/launcher/api/pairing/PairingService.kt](../../core/src/commonMain/kotlin/com/launcher/api/pairing/PairingService.kt)
- [app/src/main/java/com/launcher/app/ui/pairing/PairingActivity.kt](../../app/src/main/java/com/launcher/app/ui/pairing/PairingActivity.kt)
- [specs/007-pairing-and-firebase-channel/spec.md](../../specs/007-pairing-and-firebase-channel/spec.md) — legacy design, будет полностью заменена v2.

**Recovery + envelope pattern (уже в коде):**
- [core/keys/src/commonMain/kotlin/family/keys/impl/RecoveryFlow.kt](../../core/keys/src/commonMain/kotlin/family/keys/impl/RecoveryFlow.kt)
- [core/keys/src/commonMain/kotlin/family/keys/api/RecoveryKeyBackup.kt](../../core/keys/src/commonMain/kotlin/family/keys/api/RecoveryKeyBackup.kt)
- [core/keys/src/commonMain/kotlin/family/keys/api/internal/RecipientResolver.kt](../../core/keys/src/commonMain/kotlin/family/keys/api/internal/RecipientResolver.kt) — access-grant + envelope-per-recipient model (будет заменена MLS)

**Related backlog:**
- **TASK-6** — Root Key Hierarchy (`Verification`, готово)
- **TASK-65** — Profile Composition Foundation v2 (`Verification`, готово)
- **TASK-66** — Generic Encrypted Bucket Registry (`Done`)
- **TASK-51** — libsodium consolidation (`Done`)
- **TASK-70** — Profile Sync and State Cache (`Draft`, зависит от TASK-67)
- **TASK-58** — Signal Sender Keys vs MLS research (`Draft`) — теперь решено, MLS выбран
- **TASK-57** — Zero-Knowledge Server Architecture audit (`Draft`) — связано с Темой 6

**Constitution + rules:**
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — Article XX (Pre-MVP no-migration override) — позволяет breaking changes до первого релиза.
- [CLAUDE.md](../../CLAUDE.md) — engineering rules 1-16.

---

## Инструкция для пользователя (владельца проекта)

Когда запустишь новый agent для продолжения:

1. Скажи ему **прочитать этот файл** (`docs/dev/crypto-topics-handoff.md`) и **основной документ** (`docs/dev/crypto-mentor-overview.md`).
2. Скажи какую тему из 4-11 берём следующей.
3. Проси **mentor-режим** и **краткость + sequences**.
4. Напоминай — пользователь новичок, не соглашаться рефлекторно.

Каждая новая тема — новый focused разговор. Не пытаться сожрать все 8 тем за одну сессию.

---

## Мета: почему такой формат

- **Файл — не чат.** Чат теряет контекст через compaction. Файл — persistent.
- **Ссылки на код.** Другой agent сможет прочитать нужное сам.
- **CANDIDATE list.** Не потерять идеи которые всплыли по ходу.
- **Decisions log.** Что решено — не пере-обсуждать.
- **Explicit questions for each remaining topic.** Другой agent имеет starter kit.
