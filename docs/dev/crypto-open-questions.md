# Crypto Open Questions Register (⚠️ deprecated — migrated to backlog tasks 2026-07-02)

> **⚠️ Этот файл замороженная историческая записка**. Новая модель (CLAUDE.md rule 11 revised) — каждый Q-NN становится отдельным `TASK-N` в статусе `Discussion` (или `Done` если уже decided). Register больше не поддерживается активно.
>
> **Где смотреть открытые вопросы теперь**: `backlog task list --status Discussion` или Kanban board «Discussion» колонка.
>
> **Где смотреть decided архитектурные решения**: `backlog task list --status Done --label decision` — каждая содержит `### Decision (English, immutable) 🔒` sub-блок.
>
> **Migration mapping** (по мере touch'а):
> - Q-01, Q-02, Q-03, Q-04, Q-18 (🟢 decided) → секции в `crypto-mentor-overview.md` (пока не разбиты, миграция по touch).
> - Q-09 (history backup) → **TASK-100** (Done, Decision block).
> - Q-12 (peer confirmation on recovery) → **TASK-101** (Discussion, session 1 in progress).
> - Q-05..Q-08, Q-10, Q-11, Q-13..Q-17, Q-19, Q-20, Q-21 — остаются в этом файле как queue до создания соответствующих `TASK-N` в статусе `Discussion` (по приоритету).

**Связанные файлы**:
- `backlog/tasks/task-100 - Decision-History-backup-strategy-for-MVP.md` — Q-09 migrated
- `backlog/tasks/task-101 - Decision-Peer-confirmation-on-recovery.md` — Q-12 migrated
- [crypto-mentor-overview.md](crypto-mentor-overview.md) — legacy SoT (замораживается по мере миграции decisions в backlog tasks).
- [crypto-topics-handoff.md](crypto-topics-handoff.md) — историческая записка о разбитии на темы.
- `.claude/skills/procedure-decision-drift-check/SKILL.md` — замена retired `procedure-crypto-alignment-sweep`.

---

## Формат записи

Каждый вопрос — блок:

```markdown
### Q-NN: <короткое имя>

- **Status**: 🔴 open | 🟡 in-discussion | 🟢 decided → link
- **Context**: где всплыло (сессия YYYY-MM-DD, mentor-overview секция, backlog task N).
- **Blocks tasks**: TASK-M, TASK-K.
- **Priority**: critical | high | medium | low.
- **Session-tag**: тема для будущей сессии (может быть shared у нескольких вопросов).

<Одно-двух-абзацное описание проблемы, включая варианты если известны.>
```

Правила:
- Q-номер **never reused** (даже если вопрос закрыт — remains).
- Закрытый вопрос переводится в 🟢 + link на секцию mentor-overview, **не удаляется**.
- Новый вопрос всплыл посреди сессии → добавляется **сразу** сюда, не «в конце запишу».

---

## Session-tags (группировка вопросов для одной сессии)

- `theme-4-revoke` — MLS Remove механика + policy enforcement.
- `theme-5-multi-device` — governance между устройствами одной identity.
- `theme-6-metadata` — что server знает vs zero-knowledge tiers.
- `theme-7-mls-scale` — MLS overhead для 100+ member clinic groups.
- `theme-8-push` — push payload > 4KB, Huawei без GMS, MQTT.
- `theme-9-recovery-propagation` — peer confirmation + history restoration.
- `theme-10-key-rotation` — identity rotation + cross-group implications.
- `theme-12-history-backup` — encrypted-history-backup pattern (WhatsApp-style local encrypted DB). **Новая тема из сессии 2026-07-02.**
- `theme-13-cross-platform-adapters` — IdentityVault на iOS App Groups / Huawei / Android TV / Google TV. **Новая тема из сессии 2026-07-02.**
- `theme-14-quota-durable-objects` — Cloudflare Durable Objects concrete design для quota counters. **Новая тема из сессии 2026-07-02.**

---

## Открытые вопросы

### Q-01: Хранение "списка моих групп" на сервере — recovery через опубликованный mls-state

- **Status**: 🟢 decided → [crypto-mentor-overview.md Δ.2](crypto-mentor-overview.md#δ2)
- **Context**: сессия 2026-07-02, вопрос новичка «где хранится список моих групп?».
- **Decided**: подход A — mls-state сериализуется, шифруется через HKDF(root_key, "mls-state"), хранится в bucket на сервере. Peer-restore path (подход B) отвергнут (ломается когда все peer'ы offline).
- **Affected tasks**: TASK-6, TASK-58.

### Q-02: Payload фото и E2E — client-side compression before encryption

- **Status**: 🟢 decided → **[TASK-110](../../backlog/tasks/task-110%20-%20Decision-Client-side-media-transformation.md)** (Draft, 2026-07-06).
- **Context**: сессия 2026-07-02, вопрос «в WhatsApp видео сжимается — Meta видит?».
- **Decided**: WhatsApp pattern — compress client-side → encrypt client-side → upload ciphertext. Server transcoding невозможен принципиально.
- **Affected tasks**: TASK-11, TASK-28.

### Q-03: Server enforce'ит квоту не читая содержимое — signed upload tokens

- **Status**: 🟢 decided → **[TASK-111](../../backlog/tasks/task-111%20-%20Decision-Signed-upload-tokens-quotas-abuse-response.md)** (Draft, Deferred 2026-07-06).
- **Context**: сессия 2026-07-02, вопрос «если сервер не знает что внутри — как запретить залить terabyte?».
- **Decided**: Cloudflare R2 presigned URL с `max_size`, Cloudflare Durable Object counter per (pseudonym, resource). 100 MB per identity quota.
- **Affected tasks**: TASK-11, TASK-28, TASK-67.

### Q-04: Metadata privacy tier T0 → T1 через adapter swap

- **Status**: 🟢 decided → **[TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md)** (Draft, 2026-07-06).
- **Context**: сессия 2026-07-02, вопрос «как сделать server тупее?».
- **Decided**: T0 в MVP (identity_id + group roster + timing visible). Opaque `OwnerRef`/`BucketKey`/`PushTopic`/`GroupRef` port'ы → T1 (HMAC pseudonym) = adapter swap ~2-3 недели. T2 (VOPRF sealed sender) не строим.
- **Affected tasks**: TASK-57, TASK-66, TASK-67.

### Q-05: Устройства-зомби (6+ месяцев не активны) — auto-cleanup?

- **Status**: 🔴 open
- **Context**: `crypto-topics-handoff.md` Тема 5.
- **Blocks tasks**: TASK-40, TASK-24.
- **Priority**: low.
- **Session-tag**: `theme-5-multi-device`.

Если у бабушки было 3 устройства, одно она давно потеряла — MLS group имеет мёртвый leaf, forward secrecy pinches. Нужен ли auto-Remove после N дней inactivity? Кто решает — owner или сервер по heuristic? Что если бабушка думала что телефон утерян, потом нашла — как reactivate?

### Q-06: Editing lock document — 20 мин TTL, force-override

- **Status**: 🟡 in-discussion (CANDIDATE-4 в handoff)
- **Context**: `crypto-topics-handoff.md` Тема 3 (Profile sync).
- **Blocks tasks**: TASK-70.
- **Priority**: medium.
- **Session-tag**: `theme-5-multi-device`.

Кто держит lock: user session? device? identity? Что если Танин планшет и телефон одновременно берут lock? Какой UX force-override — «Петя редактирует до 15:20, продолжить?». Хранение lock document — Firestore или Durable Object?

### Q-07: Preset bundle — исключить platform-specific (Google TV, iOS)

- **Status**: 🟡 in-discussion (upgrade к Блоку 11)
- **Context**: сессия 2026-07-02, вопрос владельца «bundle исключает и platform-specific?».
- **Blocks tasks**: TASK-20, TASK-16.
- **Priority**: medium.
- **Session-tag**: `theme-13-cross-platform-adapters`.

`PersonalPresetBundle` должен иметь `platformScope: [android_phone, android_tv, ios]` вместо только device-specific. Как обрабатывать features которые есть только на одной платформе (например tile для Google TV remote control)? Fallback UI на других платформах или явный skip?

### Q-08: IdentityVault на iOS / Huawei / Android TV / Google TV

- **Status**: 🟡 split → migrated 2026-07-07 (see below).
- **Original framing** (2026-07-02): один вопрос про «cross-platform adapter'ы IdentityVault на iOS / Huawei / Android TV / Google TV», слепивший port boundary + cross-app sharing + platform-specific storage + form-factor bootstrap.
- **Post-split ownership** (2026-07-07):
  - **Port boundary** (что port возвращает, где граница operation-on-vault vs export) → **[TASK-112](../../backlog/tasks/task-112%20-%20Decision-Cross-platform-IdentityVault.md)** Discussion.
  - **Cross-app sharing** между разными Android package'ами (launcher + messenger + album) → **[TASK-25](../../backlog/tasks/task-25%20-%20Multi-app-Cohabitation-Chain-of-trust-Recovery.md)** Draft (B / C / hybrid ADR).
  - **iOS Keychain + Secure Enclave adapter** → **[TASK-26](../../backlog/tasks/task-26%20-%20iOS-Admin-Preset.md)** Draft.
  - **Android TV form factor** (не отдельный vault-адаптер — тот же Android adapter; pairing через QR/RemoteCode) → **[TASK-29](../../backlog/tasks/task-29%20-%20Android-TV-Preset.md)** Draft.
  - **HarmonyOS NEXT** — тот же port, реализация постфактум без изменений domain (в scope TASK-112 как forward-compat requirement).
- **Original reasoning** (для истории): Android ContentProvider работает на Huawei EMUI (не требует GMS). Android TV — тот же Android. iOS — другой механизм (App Groups + Shared Keychain). HarmonyOS NEXT — HUKS. Вопрос «что делает port обязательным контрактом» отделён от «какие platform storage adapter'ы». Первое — TASK-112. Второе — per-platform task'и.

### Q-09: Расшифровка истории после recovery — WhatsApp-style encrypted-history-backup pattern

- **Status**: 🟢 decided → [crypto-mentor-overview.md Блок 20](crypto-mentor-overview.md#блок-20--history-backup-при-recovery-q-09-decision)
- **Context**: сессия 2026-07-02.
- **Decided**: MVP = Signal-style (нет истории после recovery, wizard явно предупреждает). Phase-3+ = WhatsApp-style E2E backup будет продуман отдельной задачей вместе с messenger'ом (TASK-27) и full family album (TASK-28). Никаких exit-ramp lock'ов сегодня — Article XX даёт свободу до первых юзеров. Restore Profile state — уже работает через Δ.2.
- **Follow-up**: Q-21 (setup wizard формулировка про потерю истории).
- **Affected tasks**: TASK-6, TASK-27, TASK-28, TASK-32, TASK-70 → `crypto-alignment: aligned` с source `[Блок 20]`.

### Q-21: Setup wizard formulation про потерю истории при recovery

- **Status**: 🔴 open (тактический)
- **Context**: сессия 2026-07-02, follow-up of Q-09.
- **Blocks tasks**: TASK-67 (setup wizard step).
- **Priority**: low (тактика UX, разрешается в /speckit.clarify).
- **Session-tag**: `theme-12-history-backup`.

Как пожилому пользователю в wizard'е сказать «при recovery история чатов не сохраняется» так, чтобы (а) он понял, (б) не испугался и не бросил onboarding, (в) мы не пообещали «backup будет в будущем» (может не сдержим). Три черновика формулировок:
- **Direct**: «Если сменишь телефон, контакты и настройки сохранятся, а переписка — нет.»
- **Reassuring**: «На новом устройстве твои настройки вернутся автоматически. Историю чатов можно сохранить только сейчас — попроси родственника скопировать вручную» (не совсем правда).
- **Silent + FAQ**: не упоминать в wizard'е, объяснить в справке при попытке recovery.

Разрешается в /speckit.clarify TASK-67 когда wizard шаги будут проектироваться.

### Q-10: Root_key rotation — вылетает из групп и теряет историю

- **Status**: 🟡 in-discussion (частично Блок 17)
- **Context**: сессия 2026-07-02.
- **Blocks tasks**: TASK-41.
- **Priority**: low (MVP не поддерживает).
- **Session-tag**: `theme-10-key-rotation`.

Если root_key меняется — identity в MLS становится другой (identity_pub меняется) → все группы «не знают этого человека». Значит: (a) full re-onboarding, (b) новый identity + импорт истории (если Q-09 решён с backup'ом). MVP выбор — **не поддерживать**. Что записать в UX «забыл passphrase → создай новый аккаунт, история потеряна»?

### Q-11: Право на MLS Remove (revoke) — только owner или policy-based

- **Status**: 🟢 decided → **[TASK-102](../../backlog/tasks/task-102%20-%20Decision-Revoke-policy.md)** (Draft, 2026-07-02).
- **Context**: сессия 2026-07-02.
- **Decided**: three-tier role model (owner/admin/other). MVP flat + admin, identity-level revoke, no blacklist. `primary user`'s device = sole MLS Commit signer; admins не могут issue Add/Remove напрямую — только через profile edit + reconciliation. Post-compromise security через MLS epoch change automatic.
- **Affected tasks**: TASK-42, TASK-46, TASK-58, TASK-67.

### Q-12: Peer confirmation при recovery peer'а — automatic trust или UX confirm

- **Status**: 🟢 decided → **[TASK-101](../../backlog/tasks/task-101%20-%20Decision-Peer-confirmation-on-recovery.md)** (Draft, 2026-07-02).
- **Context**: `crypto-topics-handoff.md` Тема 9.
- **Decided**: **Chrome/Google Account model** — auto-add нового device в MLS group + **post-facto notification** peers. Multi-device теперь **first-class** (unparked TASK-40). Reasoning: recovery = self-add собственного нового device_keypair, не peer-adds-peer; поэтому UX confirmation не нужен peer'ам, они узнают post-facto.
- **Affected tasks**: TASK-6, TASK-25, TASK-40, TASK-67.

### Q-13: FCM недоступен (Huawei без GMS) — fallback push channel

- **Status**: 🔴 open
- **Context**: `crypto-topics-handoff.md` Тема 8.
- **Blocks tasks**: TASK-58 (Huawei smoke gates), TASK-5.
- **Priority**: high.
- **Session-tag**: `theme-8-push`.

На Huawei без GMS Firebase FCM не работает. Варианты:
- **A**. **HMS Push Kit** (Huawei native) — доп adapter, регистрация в Huawei console. Работает только на Huawei.
- **B**. **MQTT через наш Cloudflare Worker** — universal, но battery cost выше (persistent connection).
- **C**. **WebSocket через Worker** — то же.
- **D**. Polling (500 мс - 5 сек) — простой, но battery убивает.

Как это скрывается за `PushChannel` port'ом? MVP — только FCM (Huawei без GMS = MVP не supported). Phase-3 — HMS. Phase-4 — MQTT.

### Q-14: Cloudflare Durable Objects — concrete design для quota counters

- **Status**: 🟡 split → **[TASK-109](../../backlog/tasks/task-109%20-%20Decision-Durable-Objects-concrete-design-security-critical-endpoints.md)** (Paused, 2026-07-06) + **[TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md)** baseline.
- **Context**: сессия 2026-07-02, `Π.3` предлагает DO но без деталей.
- **Partial resolution**: TASK-105 установила ladder RATE_LIMITER (normal) → DO (security-critical). TASK-109 остался Paused — нужен owner input по which endpoints classify security-critical + concrete DO schema для recovery attempts counter / unlock attempt tracker.
- **Affected tasks**: TASK-67, TASK-11, TASK-28.

### Q-15: Blob deduplication по content hash — приемлемая утечка?

- **Status**: 🟢 decided → **[TASK-110](../../backlog/tasks/task-110%20-%20Decision-Client-side-media-transformation.md)** (Draft, 2026-07-06) реализует WhatsApp pattern (client-side transform → encrypt → upload), что снимает вопрос — сервер видит только encrypted blob, deduplication невозможна by design.
- **Context**: сессия 2026-07-02, Π.1.
- **Affected tasks**: TASK-11, TASK-28, TASK-38.

### Q-16: Group ID visible серверу как «граф связей»

- **Status**: 🔴 open
- **Context**: сессия 2026-07-02, Ξ.1 упомянуто, разбор — Тема 6.
- **Blocks tasks**: TASK-57, TASK-58.
- **Priority**: medium.
- **Session-tag**: `theme-6-metadata`.

MLS Group ID = shared identifier между членами. Server видит: кто в какой group_id участвует (через commits + KeyPackage lookups + FCM topics). Это тот же «граф связей», что мы пытались устранить упразднив access-grants. Варианты:
- **A**. Принимаем в T0/T1 (нет решения без SNARK-level мат).
- **B**. Rotate group_id periodically (каждый epoch commit) — server не может корреляцию тайминга-в-тайминга, но может сессионно.
- **C**. Sealed sender pattern (Signal) — server не видит sender_id в MLS commit'е.

### Q-17: Abuse response mechanism — legal compliance для E2E

- **Status**: 🟡 migrated → **[TASK-107](../../backlog/tasks/task-107%20-%20Decision-Abuse-response-mechanism-legal-minimum.md)** (Paused, 2026-07-06).
- **Context**: сессия 2026-07-02, Π.6.
- **Post-migration state**: TASK-107 paused — post-MVP scope (arbitration + open/closed groups + auto-detection). Requires legal + product perspective, не чисто technical. False-positive attack threshold + human review pipeline questions остались на TASK-107.
- **Affected tasks**: TASK-11, TASK-28.

### Q-19: Verification при pairing — SAS emoji policy

- **Status**: 🟡 in-discussion (CANDIDATE-2 в handoff)
- **Context**: `crypto-topics-handoff.md` Тема 2.
- **Blocks tasks**: TASK-67.
- **Priority**: medium.
- **Session-tag**: `theme-4-revoke` (частично overlaps).

`SasRequirement = Off | Optional | Mandatory`. Family = Optional (6 emoji verify), clinic/B2B = Mandatory через preset-level policy. Кто определяет policy — preset или user? Как обрабатывать если user хочет skip mandatory SAS?

### Q-20: Fitness rule — `Clock.System.now()` запрещён в crypto-flows

- **Status**: 🟡 in-discussion (CANDIDATE-3 в handoff)
- **Context**: `crypto-topics-handoff.md` Тема 2.
- **Blocks tasks**: TASK-67.
- **Priority**: medium.
- **Session-tag**: `theme-6-metadata` (частично).

Все timestamps в crypto через Firestore serverTimestamp (защита от time-skew attacks). Как enforce'ить — import-lint rule? Custom Detekt правило? Что делать с offline flows (нет server clock'а)?

---

## Priority queue (updated 2026-07-02 после TASK-100..103 closure)

**Migration in progress**: Q-NN вопросы постепенно превращаются в decision-task'и в backlog (rule 11). Register остаётся для не-мигрированных вопросов.

### Closed (мигрированы в backlog decision-tasks)

Полная таблица — см. **[crypto-status.md § Recently decided](crypto-status.md#recently-decided-sessions-2026-07-02--2026-07-07)**.

Migration snapshot 2026-07-07:
- **Q-02** → TASK-110 (Draft). Client-side media transformation.
- **Q-03** → TASK-111 (Draft, Deferred). Signed upload tokens + quotas.
- **Q-04** → TASK-108 (Draft). Metadata privacy T0 → T1 adapter swap.
- **Q-08** → **split** TASK-112 (port boundary, Discussion) + TASK-25 (cross-app) + TASK-26 (iOS) + TASK-29 (TV).
- **Q-09** → TASK-100 (Done). Signal-style history recovery.
- **Q-11** → TASK-102 (Draft). Three-tier revoke policy.
- **Q-12** → TASK-101 (Draft). Chrome-model auto-add.
- **Q-14** → **split** TASK-105 (baseline ladder) + TASK-109 (concrete DO design, Paused).
- **Q-15** → TASK-110 (Draft) — dedup невозможна by design.
- **Q-17** → TASK-107 (Paused). Abuse response umbrella.

Также появились без Q-NN precursor:
- **TASK-103** (Draft) remote app lock.
- **TASK-104** (Draft) KeyPackage rate limit.
- **TASK-105** (Draft) server-side abuse defense baseline.
- **TASK-106** (Draft) Sybil resistance / signup gate.

### Open questions (не мигрированные, ждут mentor-сессии)

**High priority**:
- **Q-13** Huawei без GMS push fallback — блокирует TASK-58 Huawei smoke gates. Physical device dependent.

**Medium priority**:
- **Q-06** Editing lock design (20 min TTL, force-override) — TASK-70 dependency.
- **Q-07** Preset bundle platform-scope — TASK-20, TASK-16.
- **Q-16** Group ID visible серверу — Тема 6 metadata. Может быть частью future TASK-108 T2 extension.
- **Q-19** SAS emoji policy при pairing — TASK-67.
- **Q-20** Clock.System.now() fitness rule — TASK-67 crypto flows.

**Low priority / deferred**:
- **Q-05** Zombie devices auto-cleanup — edge case, Phase-3+.
- **Q-10** Root_key rotation — MVP не поддерживает.
- **Q-21** Setup wizard formulation про потерю истории — тактический для /speckit.clarify TASK-67.

### Recommended next mentor session

**TASK-112 IdentityVault port boundary** (Discussion, awaiting owner Decision Session 2 by 2026-07-07). После — TASK-16 preset schema evolution (integrate TASK-103/104/108/110 preset fields).

---

## Как этот файл обновляется (2026-07-02+)

**Файл в migration status**: постепенно каждый Q-NN мигрируется в backlog decision-task. Регистр остаётся для не-мигрированных вопросов.

**Приоритетный queue выше** — актуальный. Ниже (Q-01..Q-21 записи) — исторические, некоторые могут ссылаться на устаревшие модели (SoT-файл, alignment-sweep skill).

**Для новых архитектурных вопросов**: **не** добавляй в этот файл. Создавай новый decision-task в статусе Discussion (см. CLAUDE.md rule 11).

**Не удалять** старые записи Q-NN — remain для истории.

**Session-tags** — историческая группировка. Retired skill `procedure-crypto-alignment-sweep` заменён на **`procedure-decision-drift-check`** — walks `dependencies:` graph, flags downstream tasks с superseded upstream Decision.
