# Next Steps — Spec 011 (Pending Work)

**Created:** 2026-05-21 (end of mentor + specify + plan + tasks session)
**Branch:** `011-contacts-and-e2e-encrypted-media`
**Last commit:** `74654dc docs(011): tasks-phase — 74 tasks across 12 phases with full trace`
**Status:** spec.md + plan.md + research.md + data-model.md + contracts/ + tasks.md готовы и закоммичены. PR не открыт.

Этот файл — точка возобновления работы. Содержит **что осталось доделать в pipeline спека до начала реализации** и **что важно помнить из решений сессии 2026-05-21**.

---

## ⚠️ Главный gap: checklist files не созданы

В plan-фазе прогнаны **3 чек-листа** (через subagent, results в commit message + plan.md §Open Items):
- `checklist-domain-isolation` — 14/16 ✓
- `checklist-wire-format` — 15/17 ✓
- `checklist-meta-minimization` — 11/13 ✓

**Но физические файлы `checklists/*.md` НЕ созданы** (для сравнения: у спеков 008/009/010 есть папка `checklists/` с 10-16 файлами в формате CHK-001..NNN).

**Также НЕ прогнаны** ~10 чек-листов, которые соседние спеки обычно прогоняют:
- `checklist-requirements-quality` (always-on)
- `checklist-security` ← **CRITICAL для 011**, спек крипто+privacy
- `checklist-failure-recovery` ← **CRITICAL**, есть error paths
- `checklist-permissions-platform` ← OEM-quirks упоминаются в research.md §3
- `checklist-elderly-friendly` ← по контексту проекта
- `checklist-accessibility` ← новый UI (DocumentPicker, DocumentViewer)
- `checklist-state-management` ← process death survival для DocumentViewer
- `checklist-ux-quality` ← новые UI surfaces
- `checklist-localization` ← user-facing strings + label dialog
- `checklist-performance` ← perf budgets в plan.md

### Что сделать при возобновлении

**Option A (recommended)** — прогнать недостающие чек-листы + создать файлы в `specs/011-contacts-and-e2e-encrypted-media/checklists/`:

```
# в новой Claude Code session:
запусти /speckit.analyze
# либо вручную попроси прогнать недостающие чек-листы — это закроет gap до analyze
```

**Option B (минимальный)** — сразу `/speckit.analyze`, он сам триггерит relevant чек-листы и пишет `analyze-report.md`.

Рекомендация — **Option A**, потому что 011 — самый широкий по checklist coverage спек проекта (крипто + privacy + UI + permissions + state).

---

## Pipeline status

| Phase | Status | Artifact | Commit |
|---|---|---|---|
| `/speckit.specify` | ✅ done | spec.md | 107c071 |
| `/speckit.clarify` | ✅ done (via mentor) | spec.md §Clarifications (C-1..C-8) | 107c071 |
| `/speckit.plan` | ✅ done | plan.md, research.md, data-model.md, contracts/×3, quickstart.md | 8e0f4c2 |
| `/speckit.tasks` | ✅ done | tasks.md (74 tasks, 12 phases) | 74654dc |
| **checklists/ files** | ❌ **GAP** | should exist но не созданы | — |
| `/speckit.analyze` | ❌ pending | analyze-report.md | — |
| Implementation | ❌ pending | Phase 0 → Phase 12 from tasks.md | — |

---

## Ключевые архитектурные решения (что помнить)

Все 8 закреплены в spec.md §Clarifications. Если кто-то будет читать в спешке:

### C-1: Trust model — односторонняя пара (как в спеках 7-9)
В 011 НЕ меняем модель управления. Двусторонняя/групповая/multi-device — будущие спеки 015-018.

### C-2: Multi-recipient envelope — заложить, не использовать
`recipients[]` массив произвольной длины с первого commit'a, **в 011 всегда length=1**. Зарезервирует архитектурно дешёвую миграцию на группы.

### C-3: Криптопротокол — per-device key pairs + hybrid encryption
- Каждое устройство при первом запуске → свой `(Pub_X25519, Priv_X25519)`.
- Pub публикуется в `/links/{linkId}/devices/{deviceId}`.
- Priv хранится в Android Keystore (через AES-wrap — Keystore X25519 не поддерживает).
- Blob: CEK (32 random bytes) шифрует фото; CEK seal'ится для каждого recipient pub.

### C-4: Криптобиблиотека — Lazysodium-android (libsodium binding)
- AEAD: XChaCha20-Poly1305 (extended nonce, misuse-resistance).
- Asymmetric: X25519 + `crypto_box_seal` (анонимный sender).
- Exit ramp: `cipherSuiteId` в envelope. Tink rejected (Google KMS привязка, runtime ограничения).

### C-5: Namespace `private:` — без sub-namespacing
Один namespace для любых приватных медиа. Тип (image/video/document) — в envelope `metadata.kind`.

### C-6: POST_NOTIFICATIONS отложен
Не активируется в 011. Перенесён на spec 012/013. Compliance doc обновляется (T007).

### C-7: Reference counting учитывает history snapshots
Blob удаляется только когда **ни** `/config/current` **ни** history snapshots (retention 10 из спека 009) на него не ссылаются. 24h grace period + 24h background reconciler.

### C-8: RecipientResolver — обоснованное исключение из rule 4
Single-impl interface разрешён, потому что три гарантированные будущие реализации в roadmap (015, 016, 017). Documented Article XVII §3 exception.

---

## Что зафиксировано в roadmap и backlog

### Будущие спеки (созданы 2026-05-21)
- **015** `symmetric-pairing-bidirectional-control` — двусторонние пары.
- **016** `family-group-shared-encryption` — групповые ключи (WhatsApp-style).
- **017** `multi-device-recovery` — несколько устройств у одного владельца + восстановление при потере.
- **018** `key-rotation-forward-secrecy` — периодическая ротация ключей.

См. [docs/product/roadmap.md](../../docs/product/roadmap.md) §Spec 015-018.

### Backlog tasks
- **TODO-FUTURE-SPEC-007..010** — параллельные backlog entries для 015-018.
- **TODO-DOC-001** — ADR-007 (TrustEdgeBootstrap subtypes). Draft в `docs/adr/ADR-007-*.md` будет создан в T008/T010 (Phase 0/1).

### Server-roadmap
**SRV-MEDIA-001** будет добавлен в Phase 0 (task T006) — миграция Firebase Storage на свой server при storage > 4 GB OR download > 800 MB/day.

---

## Что обязательно перепроверить ПЕРЕД началом реализации

### 1. Drift крючков из спеков 6/7/8 (валидация 2026-05-21 показала ✅)

В предыдущей сессии я валидировал 5 forward-compat hook'ов. Все ✅, но **между сейчас и стартом реализации код может уехать**:

| Крючок | Где | Проверь |
|---|---|---|
| `Contact.photoRef: String?` | [spec 008 data-model.md:64](../008-bidirectional-config-sync/data-model.md#L64) | поле всё ещё `String?` nullable |
| `IconStorage.resolve(iconId: String)` | [spec 006 IconStorage.kt:20](../../core/src/commonMain/kotlin/com/launcher/api/capability/IconStorage.kt#L20) | signature unchanged |
| `PartialReason.MediaDecryptFailed` | [spec 008 PartialReason.kt:23](../../core/src/commonMain/kotlin/com/launcher/api/link/PartialReason.kt#L23) | enum value присутствует |
| `PairingWireFormat.CURRENT_SCHEMA_VERSION = 1` | [spec 007 PairingWireFormat.kt:31](../../core/src/commonMain/kotlin/com/launcher/api/pairing/PairingWireFormat.kt#L31) | schemaVersion = 1 (additive only) |
| `Link.KNOWN_SUBCOLLECTIONS` | [spec 007 Link.kt:37-44](../../core/src/commonMain/kotlin/com/launcher/api/link/Link.kt#L37) | расширяемый list |

Если что-то уехало — обновить spec 011 references **до** T010 (Phase 1).

### 2. Plan §Open Items (4 пункта)

Зафиксированы в plan.md §Open Items for tasks-phase. Все 4 уже маппированы в tasks:
- CHK007 (PrivateKey opaque typing) → T012
- CHK003 (SUPPORTED_SCHEMA_VERSION constant) → T019 [CRIT]
- CHK014 (SQLDelight migration test) → T071 [CRIT]
- CHK002 (RecipientResolver monitoring) → no task; revisit при `/speckit.analyze` после релиза 011

### 3. Mega-tasks (отмечены trace report'ом)

3 task'a отмечены как «крупные», стоит подумать о разбиении при имплементации:
- **T034** AndroidKeystoreSecureKeystore (AES-wrap стратегия) — нетривиальная, разбить на T034a (keygen libsodium + AES-wrap), T034b (Keystore-backed AES key gen), T034c (EncryptedSharedPreferences persist).
- **T040** Extend PairingCoordinator — разбить на T040a (keygen on first launch), T040b (publishOwn after consent), T040c (integration test).
- **T081** PrivateMediaResolver — разбить на T081a (cache hit fast-path), T081b (download+unseal+decrypt slow-path), T081c (error → PartialReason mapping).

Не блокирует analyze, но при реальном кодировании эти задачи стоит расщепить, чтобы каждая жила < 1 дня.

---

## Команды для возобновления работы

```
# 1. Переключиться на ветку
git checkout 011-contacts-and-e2e-encrypted-media

# 2. Pull свежие изменения с remote (если push прошёл)
git pull

# 3. Прочитать этот файл (NEXT-STEPS.md), плюс spec.md / plan.md / tasks.md

# 4. Самый простой path forward — запустить /speckit.analyze
#    Это автоматически прогонит relevant чек-листы + cross-artifact trace + constitution re-check.
/speckit.analyze

# 5. Если analyze покажет issues — fix их, иначе начинай Phase 0 implementation от T001.
#    Per CLAUDE.md §Branching — push после каждой Phase, не пилить локально.
```

---

## Файлы спека 011 (содержимое)

| Файл | Что в нём | Размер |
|---|---|---|
| `spec.md` | 5 user stories, 26+ FR, 8 SC, 8 architectural decisions (C-1..C-8) | ~411 строк |
| `plan.md` | 12-phase implementation plan, Constitution Check 8/8 ✓, Risks, Open Items | ~580 строк |
| `research.md` | 10 design sections с альтернативами и обоснованиями | ~350 строк |
| `data-model.md` | 6 domain types, 6 ports, 2 SQLDelight schemas, lifecycle T0-T6 | ~470 строк |
| `quickstart.md` | 12-step Phase 0 setup checklist для разработчика | ~260 строк |
| `contracts/crypto-envelope.md` | CBOR envelope wire-format + cipherSuiteId registry + 8 tests | ~210 строк |
| `contracts/device-identity.md` | Firestore /devices/{deviceId} doc + deviceOwnership + Security Rules + 8 tests | ~165 строк |
| `contracts/encrypted-media-storage.md` | Firebase Storage layout + Rules + housekeeping + 12 tests | ~210 строк |
| `tasks.md` | **74 задачи в 12 фазах** с full trace на FR/US/SC | ~920 строк |
| `NEXT-STEPS.md` | этот файл | — |

---

## Контекст конструктивных моментов из mentor-сессии (для возобновления)

Пользователь — non-Android-developer, общается на русском, ведёт проект через Spec Kit.

**Главное требование пользователя**, повлиявшее на архитектуру:
> «механизм шифрования никак не должен быть завязан на механизм, как друг с другом работают телефоны»

Это привело к выбору **membership-agnostic envelope** (массив recipients произвольной длины) + per-device asymmetric keys вместо общего симметричного ключа пары. Все будущие модели доверия (двусторонние, групповые, multi-device) будут плагаться на эту инфраструктуру без миграции blob'ов.

**Решения, которые пользователь отверг:**
- «Мульти-получатель сразу» — отложили на спек 017 (хотя архитектурно envelope готов).
- «Серверный key escrow для восстановления при потере телефона» — отказались (нарушает e2e). Решение — спек 017.
- «Самописная крипта» — категорически нет.

**Решения, которые пользователь принял:**
- libsodium (через Lazysodium-android) — экосистемно-нейтральная библиотека.
- «Защита от Google + от себя самого, разработчика» — максимальный e2e.
- POST_NOTIFICATIONS — отложить.
- Фото личных документов — да, в scope 011 (US-2).
- Фото бабушки в её плитке как декорация — выкинуть из 011 (нишевый случай).
