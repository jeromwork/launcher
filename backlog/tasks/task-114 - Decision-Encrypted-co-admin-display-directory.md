---
id: TASK-114
title: 'Decision: Encrypted co-admin display directory'
status: Draft
assignee: []
created_date: '2026-07-07'
updated_date: '2026-07-07'
labels:
  - decision
  - crypto
  - privacy
  - ux
  - phase-2
milestone: m-1
dependencies:
  - TASK-102
  - TASK-108
priority: medium
ordinal: 114000
decision-supersedes: []
superseded-by: TASK-152
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Когда в семье (или клинике / компании) несколько `remote administrator`'ов, они должны **видеть друг друга** в UI:
- «Мама Таня редактирует профиль» вместо «identity_id abcd1234 редактирует профиль».
- Список всех admin'ов в Settings со знакомыми именами.
- При revoke — «Удалить админа "Мама Таня"» вместо «Удалить admin abcd1234».

**Проблема**: если display names лежат в profile plaintext на сервере — сервер видит **социальный граф** («у бабушки в family group Таня Иванова и Петя Иванов»), это **metadata leak** (нарушение TASK-108 T0 baseline).

**Решение**: display names хранятся в **encrypted directory** внутри profile, шифруются через MLS group `exporter_key`. Сервер видит только opaque bytes. Клиенты группы (все admins + primary user) расшифровывают и видят имена в UI.

**Что происходит по шагам:**
1. `Primary user` добавляет нового admin'а через QR pairing (TASK-67).
2. Во время pairing новый admin **вводит своё display name** («Мама Таня»).
3. Клиент primary user (bab's device — sole MLS Commit signer per TASK-102) добавляет запись в encrypted directory: `{ identity_id: abcd1234, display_name: "Мама Таня", added_at: timestamp }`.
4. Encrypted directory синхронизируется через MLS group как часть profile.
5. Другие admins расшифровывают → видят «Мама Таня» в своих Settings.
6. Server видит только encrypted blob.

## Зачем

**UX без leak**: multi-admin UI требует human-readable имена (rule 10 notification hygiene + senior-safe UX). Plaintext directory ломает T0 metadata privacy (TASK-108). Отдельная encrypted structure = compromise.

**Blocking для**:
- **TASK-102** implementation — UI для revoke использует display name («Удалить админа "Мама Таня"»).
- **TASK-46** (Shared admin book) — orthogonal, но display names могут переиспользоваться.
- **TASK-32** (Audit log) — log записи «Мама Таня изменила config» требуют display names.
- Multi-admin UX in Phase 3 (clinic use case — «Dr. Ivanov added a patient»).

## Что входит технически (для AI-агента)

**Wire format**:
- `AdminDisplayDirectory` — encrypted bucket через TASK-66 Generic Encrypted Bucket Registry.
- Bucket key = derived from MLS group `exporter_key` (per TASK-112 KeyVault `Purpose.CONFIG` или new `Purpose.DISPLAY_DIRECTORY`).
- Wire format follows TASK-16 discipline (inband schemaVersion, Bitwarden first-byte prefix).
- Structure (plaintext, after decryption):
  ```json
  {
    "schemaVersion": "1-alpha.1",
    "entries": [
      { "identity_id": "hash-of-root-public", "display_name": "Мама Таня", "role": "admin", "added_at": "2026-07-07T14:32:00Z" },
      ...
    ]
  }
  ```

**Domain ports**:
- `AdminDisplayDirectoryPort` в `core/keys/` или `core/crypto/`:
  ```kotlin
  interface AdminDisplayDirectoryPort {
      @Throws(VaultException::class)
      fun getEntries(groupId: GroupRef): List<AdminEntry>

      @Throws(VaultException::class)
      fun addEntry(groupId: GroupRef, identity: IdentityId, displayName: String): AdminEntry

      @Throws(VaultException::class)
      fun removeEntry(groupId: GroupRef, identity: IdentityId): Unit
  }
  ```
- Uses `KeyVault.aeadSeal / aeadOpen` (TASK-112) для encryption/decryption.

**Server-side**:
- Directory bucket stored в profile blob storage (per TASK-108 opaque `OwnerRef` / `BucketKey`).
- Server sees только opaque bytes + timestamps for TTL / retention (per TASK-108).
- Never decodes — no keys.

**Preset field** (per rule 11 preset vs invariant):
- `directory.displayNameMaxLength: int` (family default: 50; clinic default: 100 для «Dr. Ivanov, Cardiology»).
- `directory.allowEmojiInDisplayName: boolean` (family: true; clinic: false).

**Migration path**:
- Existing profile (без display directory) → lazy-create on first multi-admin operation.
- No data migration — new feature, additive.

## Состояние

**Draft** — Decision block готов, requires `/speckit.specify`.

## Trade-offs

- Adds one bucket per group (marginal server storage overhead — display strings are small, <1KB per group).
- Requires all admins to decrypt directory when opening Settings — cost dominated by MLS group state load (already amortized).
- Display name conflicts («две Тани в одной группе») — resolved by UI (append last name suffix or role tag). Not architectural.

## Exit ramp

- If encrypted directory too heavy for tiny groups (2-3 admins) → collapse into main profile blob (single encryption, no separate bucket). Additive rewrite, ~1 day.
- If clinic requires role-based display («Dr.» prefix), extend entry structure additively. Non-breaking per TASK-16 discipline.

## Non-goals

- Real-time typing indicators («Мама Таня печатает…») — no. This is display directory, not presence.
- Cross-group display names (same person in family + clinic groups) — no. Directory scoped to one MLS group.
- Search / autocomplete UI — out of scope. Directory feeds UI, doesn't provide search.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 AdminDisplayDirectoryPort defined in domain
- [ ] #2 Encrypted bucket integration through TASK-66 registry
- [ ] #3 Wire format schemaVersion="1-alpha.1" with TASK-16 discipline (inband first-byte)
- [ ] #4 addEntry / getEntries / removeEntry operations working in test
- [ ] #5 Server sees only opaque bytes (verified via test — server cannot decode)
- [ ] #6 Preset fields displayNameMaxLength / allowEmojiInDisplayName in profile schema
- [ ] #7 UI integration in Settings (multi-admin list shows display names)
- [ ] #8 UI integration in revoke flow (TASK-102 — "Удалить админа Мама Таня")
<!-- AC:END -->
