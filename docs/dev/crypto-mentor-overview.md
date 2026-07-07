# Крипто — novice glossary + rationale archive

> **⚠️ COMPACT ARCHIVE (shrunk 2026-07-07 from 1034 → ~150 lines).**
>
> **Start here for fresh AI**: [`crypto-status.md`](crypto-status.md) → [`docs/architecture/crypto.md`](../architecture/crypto.md) → decision tasks `backlog/tasks/task-100..114`.
>
> **This file kept ONLY** для novice onboarding (glossary + аналогии) + preserved rationales, которые не помещаются в короткие Decision block'и. Всё operational содержимое (Блоки 1-10, Части Θ/Ξ/Π/Ρ) удалено — оно в decision-task'ах.

---

## Terminology mapping (old → current)

| Old | Current | Where |
|---|---|---|
| `stableId` | `identity_id = hash(root_public)` | TASK-106 |
| `mls-rs` (main choice) | `openmls` (main), `mls-rs` (exit ramp) | crypto.md frontmatter |
| Firestore paths `/users/{stableId}/*` | opaque `OwnerRef` via adapter | TASK-108 |
| Google Sign-In at first launch | LOCAL identity, cloud upgrade lazy at pairing | TASK-106 |
| Peer-admin MLS Remove kick | bab's device sole executor + profile reconciliation | TASK-102 |
| Access-grants + envelope-per-recipient | MLS group membership (whole pattern superseded) | crypto.md Сценарий 4 |
| Firebase ID token (hardcoded) | Generic JWT verification | TASK-105 |
| Noise XXpsk3 | Noise_XX | TASK-67 |

---

## Novice glossary (kept from Часть 0)

### Аналогии для базовых понятий

- **Паспорт человека** = **identity_id** — уникальный номер, детерминистически = `hash(root_public)`, не меняется.
- **Пароль от сейфа** = **passphrase** — что человек помнит.
- **Домашний ключ** = **root key** — сам сейф, из которого рождаются все остальные ключи.
- **Личный автограф** = **identity key** — доказывает «это я как личность».
- **Ключ от квартиры** = **device key** — принадлежит конкретному телефону.
- **Общая комната с замком** = **MLS group** — у всех членов свои пропуска, ключ комнаты общий.
- **Список членов комнаты** = **MLS roster** — кто внутри.
- **Ритуал знакомства** = **handshake** — два устройства впервые встречаются.
- **Личная записная книжка** = **TrustEdge** — мои имена для знакомых.
- **Сейф с храповиком** = **ratcheting** — ключ шифрования после каждого сообщения необратимо меняется. Даёт **forward secrecy** (прошлое защищено при компрометации сегодняшнего ключа).
- **Дерево ключей** = **TreeKEM** — MLS основа. Каждый member = лист, промежуточные узлы содержат промежуточные ключи, корень = групповой ключ. `O(log N)` обновлений при add/remove вместо `O(N)` у Sender Keys.
- **Версия ключа** = **epoch** — каждый add/remove/update повышает. Между epoch ключ полностью новый = **post-compromise security**.
- **Одноразовые пакеты** = **KeyPackage** — публичные ключи, публикуемые заранее для добавления вас пока вы офлайн.
- **Стартовое сообщение** = **Welcome** — для новичка при add, полный snapshot group state, зашифрован на его init_key.

### Наши инструменты (что делают)

Мы не пишем крипту сами — склеиваем три готовых инструмента:

| Инструмент | Что делает | Кто ещё использует |
|---|---|---|
| **libsodium** (ionspin KMP) | Крипто-примитивы (шифрование, ключи, KDF) | Signal, WhatsApp, Wire |
| **snow** (Rust via UniFFI) | Handshake Noise_XX | WireGuard, WhatsApp companion |
| **openmls** (Rust via UniFFI) | Групповая крипта MLS RFC 9420 | Wire, Cisco Webex, Discord DAVE (March 2026) |

**Наш код (~10%)**: domain ports, UniFFI wrapper, wire format + roundtrip тесты, storage adapter, UI wizard'ов.

---

## Preserved rationales

### История backup при recovery (Блок 20)

**Current model (per [TASK-100](../../backlog/tasks/task-100%20-%20Decision-History-backup-strategy-for-MVP.md), Done)**:

- **MVP: Signal-style** — history не восстанавливается после recovery. Восстанавливается **только current Profile snapshot** (contacts + tiles + themes) через MLS bucket sync (TASK-66).
- **Phase-3+: WhatsApp-style E2E backup** — отдельная задача alongside messenger (TASK-27) + family album (TASK-28).
- **Exit ramp**: `HIST-BACKUP-001` в server-roadmap. Estimated 4-6 weeks impl + 2 weeks UX. Additive — no migration of existing MLS/bucket code.
- **Wizard warning** при onboarding: «На новом устройстве история чатов не восстанавливается. Контакты и настройки — сохранятся.» Точная формулировка в `/speckit.clarify` TASK-67.

**Rationale**: Article XX (Pre-MVP no-migration override) — HKDF context strings + wire format schema + retention policies остаются modifiable без cost до первых реальных пользователей. Ничего не добавляется в код сегодня ради Phase-3 backup'а.

### Recovery на новом устройстве — что видит

**Concept from Δ.7** (updated to current model per [TASK-101](../../backlog/tasks/task-101%20-%20Decision-Peer-confirmation-on-recovery.md) + [TASK-100](../../backlog/tasks/task-100%20-%20Decision-History-backup-strategy-for-MVP.md)):

- **Сообщения группы** (MLS application messages) — только отправленные **после Welcome**. Старые не расшифровываются (post-compromise security как побочный эффект).
- **Buckets** (Profile, contacts) — последнюю версию, хранится как один blob (перезаписывается целиком под текущий exporter_key).
- **История правок** — не расшифровывается. MVP не хранит history (только current state).

Аналогия: MLS-группа = комната с автозамком. Ключ старой epoch был у устройства N1. N1 умерло — N2 получает Welcome с текущим ключом. Старые сообщения (до Welcome) на N2 недоступны.

### Portability на свой сервер (Δ.8 rationale)

JWT verify + roster membership check — **чистая математика без state'а**. Оба обязательны (rule 12 zero-trust per [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md)):

- JWT verify — готовые библиотеки любого языка (`github.com/coreos/go-oidc`, `jsonwebtoken` для Rust).
- Roster fetch — обычный HTTP или БД query.

**Стоимость миграции Worker → Go/Rust microservice: ~1 неделя**. Two-way door (обратимо).

### Push для больших групп (Δ.9)

**MVP** — не проблема. Наши группы = 3-10 человек. FCM topic делает fan-out серверно, sender = **1 API call** на Worker → 1 FCM topic message.

**Ограничения**: payload ≤ 4KB (для SOS inline'им ciphertext, для больших — wake-up + fetch, см. [TASK-10 SOS wire format decision](../../backlog/tasks/task-10%20-%20SOS-Capability-Wizard-Step.md)); 1000 push/sec per project (hits at 10 000 events/sec при 100 members/event — 3+ года запас); per-identity 500 push/hour ([TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md) quotas).

**Exit ramp для scale**: clinic с 3000 patients → own push service (Go microservice + APNs/FCM SDKs), в `server-roadmap.md`.

### Топ-7 способов взорвать систему нашим кодом (Δ.11)

Timeless developer principles — актуальны через любые технологические изменения:

1. **Nonce reuse в AEAD** — используем только `libsodium.secretbox` / `crypto_secretstream` (random nonce), никогда свой counter. Fitness: «encrypt два раза одинаковое → выход разный».
2. **Wrong Server Rules / Worker validation** — attacker с валидным JWT становится admin. Митигация: rules tests + Worker unit tests + negative-path + 2-глазный review. См. [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md).
3. **Argon2id iteration count слишком низкий** — brute-force за часы вместо десятилетий. Митигация: hardcoded константа + roundtrip test `assert iterations >= MIN`.
4. **QR wire format без `schemaVersion`** — сломали всех на v1 при добавлении поля. Rule 5. Митигация: обязательное поле + [TASK-16 fitness rule](../../backlog/tasks/task-16%20-%20Preset-Schema-v2-Wizard-Engine.md).
5. **`android:allowBackup="true"` по умолчанию** — root_key утечёт в Google Cloud Backup. Митигация: `allowBackup="false"` + `dataExtractionRules.xml` + CI check.
6. **KeyPackage reuse** — forward secrecy теряется. Митигация: openmls соблюдает одноразовость + test on marked-used → refuse.
7. **Trust JWT для authorization вместо MLS group membership** — attacker с чужим JWT получит доступ. Митигация: Worker всегда verify JWT **И** roster membership (rule 12 zero-trust).

**Как не сделать**: `checklist-security`, `checklist-wire-format`, `checklist-domain-isolation`, `checklist-server-hardening` — обязательны для каждого крипто-спека. Fitness functions + independent review + explicit trade-offs в ADR / decision-task.

---

**Everything else** — was superseded by TASK-100..114 Decision blocks + `docs/architecture/crypto.md`. Removed 2026-07-07.
