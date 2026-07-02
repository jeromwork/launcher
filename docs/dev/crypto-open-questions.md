# Crypto Open Questions Register

**Роль**: живой реестр открытых вопросов по крипто-архитектуре. Обновляется после каждой mentor-сессии.

**Не документ для чтения от корки до корки** — это **queue**, из которого владелец / AI выбирает следующую тему для focused mentor-сессии.

**Связанные файлы**:
- [crypto-mentor-overview.md](crypto-mentor-overview.md) — **SoT архитектуры** (Source of Truth). Все **решённые** вопросы живут там как секции с якорями `Δ.1`, `Π.2`, `Ρ.4` и т.д.
- [crypto-topics-handoff.md](crypto-topics-handoff.md) — историческая записка о разбитии на темы (Темы 1-11). Постепенно deprecated в пользу этого файла.
- `.claude/skills/procedure-crypto-alignment-sweep/SKILL.md` — процедура применения решённого к backlog.

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
- `theme-11-post-quantum` — 🟢 закрыт (см. Блок 19 mentor-overview).
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

- **Status**: 🟢 decided → [crypto-mentor-overview.md Ρ.2](crypto-mentor-overview.md#ρ2), [Ρ.4](crypto-mentor-overview.md#ρ4)
- **Context**: сессия 2026-07-02, вопрос «в WhatsApp видео сжимается — Meta видит?».
- **Decided**: WhatsApp pattern — compress client-side → encrypt client-side → upload ciphertext. Server transcoding невозможен принципиально. Для family album достаточно.
- **Affected tasks**: TASK-11, TASK-28.

### Q-03: Server enforce'ит квоту не читая содержимое — signed upload tokens

- **Status**: 🟢 decided → [crypto-mentor-overview.md Π.2](crypto-mentor-overview.md#π2), [Π.3](crypto-mentor-overview.md#π3)
- **Context**: сессия 2026-07-02, вопрос «если сервер не знает что внутри — как запретить залить terabyte?».
- **Decided**: Cloudflare R2 presigned URL с `max_size`, Cloudflare Durable Object counter per (pseudonym, resource) для strong-consistency check-and-increment.
- **Affected tasks**: TASK-11, TASK-28, TASK-67.

### Q-04: Metadata privacy tier T0 → T1 через adapter swap

- **Status**: 🟢 decided → [crypto-mentor-overview.md Ξ.2](crypto-mentor-overview.md#ξ2), [Ξ.5](crypto-mentor-overview.md#ξ5)
- **Context**: сессия 2026-07-02, вопрос «как сделать server тупее?».
- **Decided**: T0 (Google UID visible) в MVP. Готовим opaque port'ы (OwnerRef, BucketKey, PushTopic) чтобы T1 (HMAC pseudonym) был adapter swap ~2-3 недели. T2 (VOPRF) не строим.
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

- **Status**: 🔴 open
- **Context**: сессия 2026-07-02, вопрос «ContentProvider на Huawei/AndroidTV/iOS?».
- **Blocks tasks**: TASK-25, TASK-26, TASK-29, TASK-3.
- **Priority**: high (влияет на F-4 AuthProvider).
- **Session-tag**: `theme-13-cross-platform-adapters`.

Android ContentProvider работает на Huawei (не требует GMS). Android TV — тот же Android. iOS — **другой механизм** (App Groups + Shared Keychain). Google TV — Android. Нужны разные adapter'ы `IdentityVault` per platform, но что делает port обязательным контрактом? Как тестируем cross-platform шаринг identity? Как это связывается с Rust UniFFI (Blок Θ)?

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

- **Status**: 🟡 in-discussion (частично Δ.10)
- **Context**: сессия 2026-07-02.
- **Blocks tasks**: TASK-42, TASK-46, TASK-58.
- **Priority**: high.
- **Session-tag**: `theme-4-revoke`.

Δ.10 предлагает 4-tier application-rule: client hides UI, Firestore Rules reject write, peer verifies commit signer, Worker rejects role-change push. Всё это — application layer. Что если clinic use case требует «главврач + head nurse оба могут revoke»? Как параметризовать policy per profile (family = only owner, clinic = role-based)?

### Q-12: Peer confirmation при recovery peer'а — automatic trust или UX confirm

- **Status**: 🟡 in-discussion
- **Discussion file**: [crypto-discussions/Q-12-peer-confirmation-on-recovery.md](crypto-discussions/Q-12-peer-confirmation-on-recovery.md)
- **Context**: `crypto-topics-handoff.md` Тема 9.
- **Blocks tasks**: TASK-6, TASK-25.
- **Priority**: high (security decision).
- **Session-tag**: `theme-9-recovery-propagation`.

Когда бабушка recovered на новом устройстве, Танин телефон должен добавить это устройство в MLS group. Варианты:
- **A**. Automatic (Танин app сам детектит нового device_pub у бабушки → делает MLS Add). Быстро, но опасно — attacker украл бабушкин passphrase → recovered → Танин app auto-trust'ит.
- **B**. Confirmation UX (Танин app показывает «бабушка сменила устройство. Fingerprint новый: XXXX. Позвони ей и сверь. Подтвердить?»). Безопасно, но требует UX + пожилые пользователи путаются.
- **C**. Hybrid: automatic для recovery через известный Google account + confirmation при подозрительных сигналах.
- **D**. Time-delayed automatic — auto-add через 24h с окном отмены (гипотеза после Q-09).

Связано с Q-09 (Блок 20 закрыт — истории нет после recovery, что снижает риск: attacker не читает прошлое).

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

- **Status**: 🔴 open
- **Context**: сессия 2026-07-02, `Π.3` предлагает DO но без деталей.
- **Blocks tasks**: TASK-67, TASK-11, TASK-28.
- **Priority**: high (нужно перед implementation Π.2).
- **Session-tag**: `theme-14-quota-durable-objects`.

Design questions:
- Один DO instance = один (`pseudonym`, `resource_type`) или один пользователь всё сразу?
- Как handle rate limit windows (sliding vs fixed)?
- Как persist state — DO storage или в KV с DO cache?
- Как billing rate (Cloudflare charges per invocation) — что если Durable Object hot?
- Migration path на свой сервер (Redis vs PostgreSQL SERIAL).

### Q-15: Blob deduplication по content hash — приемлемая утечка?

- **Status**: 🟡 in-discussion (Π.1 отвергает в MVP)
- **Context**: сессия 2026-07-02, Π.1.
- **Blocks tasks**: TASK-11, TASK-28, TASK-38.
- **Priority**: low.
- **Session-tag**: `theme-14-quota-durable-objects` (относится к blob layer).

Если два юзера загрузили одинаковый файл (same ciphertext hash) — сервер знает. Экономия storage vs privacy leak «эти два юзера имеют одинаковый файл». Отвергли в MVP. Стоит ли пересматривать когда storage costs станут проблемой (Phase-5)?

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

- **Status**: 🔴 open
- **Context**: сессия 2026-07-02, Π.6.
- **Blocks tasks**: TASK-11, TASK-28.
- **Priority**: high (legal MVP requirement).
- **Session-tag**: `theme-8-push` (частично) + новая `theme-15-abuse-response`.

Обязательный legal minimum:
- Abuse report UI в приложении.
- Server delete blob по `blobId` при report.
- Hash blocklist (нельзя upload'нуть тот же ciphertext).
- Rate limit reports.
- Log reports для legal audit.

Что если false-positive attack (Таня report'ит все бабушкины фото — все удаляются)? Threshold — N reports от разных reporters? Human review pipeline (нет staff'а в MVP)?

### Q-18: MLS PQ ciphersuite trigger — когда мигрировать

- **Status**: 🟢 decided → [crypto-mentor-overview.md Блок 19](crypto-mentor-overview.md#блок-19--post-quantum-readiness)
- **Context**: сессия 2026-07-02.
- **Decided**: watch mls-rs PQ ciphersuite release (~2027-2028). Migration через MLS ciphersuite change commit. MVP — X25519. Записать в server-roadmap.

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

## Priority queue (что брать в следующую сессию)

По приоритету (updated 2026-07-02 после Q-09 closure):

1. **Critical** — все закрыты (Q-09 → 🟢).
2. **High**:
   - Q-08 (cross-platform IdentityVault) — блокирует TASK-3, TASK-25, TASK-26, TASK-29.
   - Q-11 (revoke policy) — блокирует TASK-42, TASK-46, TASK-58.
   - Q-12 (peer confirmation on recovery) — security decision.
   - Q-13 (Huawei без GMS push fallback).
   - Q-14 (Durable Objects concrete design).
   - Q-17 (abuse response mechanism).
3. **Medium** — Q-06, Q-07, Q-16, Q-19, Q-20.
4. **Low** — Q-05, Q-10, Q-15, Q-21 (тактический для /speckit.clarify).

**Рекомендация владельцу**: следующая mentor-сессия — **Q-12** (peer confirmation on recovery). Естественное продолжение Q-09 (обе про recovery security), + разблокирует важные security decisions для TASK-6, TASK-25.

---

## Как этот файл обновляется

**После каждой mentor-сессии**:
1. Закрытые вопросы: статус меняется на 🟢, добавляется link на секцию mentor-overview.
2. Новые всплывшие вопросы: добавляются с новым Q-номером в порядке возникновения.
3. Приоритеты пересматриваются в конце файла.

**Не удалять** записи Q-NN, даже если вопрос устарел — remain для истории. Устаревшие помечаются 🟢 → `deprecated: reason`.

**Session-tags** используются `procedure-crypto-alignment-sweep` skill'ом чтобы фильтровать какие вопросы «блокируют» какие задачи.
