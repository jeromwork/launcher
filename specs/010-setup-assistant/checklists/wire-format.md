# Wire-Format Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Enforce wire-format & contract versioning per CLAUDE.md rule 5 + constitution Article VII §3.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## Wire formats touched by spec 010

| Format | Origin | Touch in спеке 10 | Modifies schema? |
|--------|--------|-------------------|------------------|
| `/config/current` Firestore | Спек 8 | Reads only (ARCH-016 — FR-001..FR-006) | ❌ No (FR-040 explicit) |
| `/state/applied` Firestore | Спек 8 | Not touched | ❌ No |
| `/links/{linkId}` Firestore | Спек 7 | Reads for paired-devices list (FR-029..FR-033) | ❌ No |
| QR-deeplink payload | Спек 7 | Not touched | ❌ No |
| FCM push payload | Спек 7 | Not touched | ❌ No |
| Android `IntentSpec` → `Intent` | New (spec 10) | Domain-typed intent descriptor | N/A — not a wire format (doesn't leave device, doesn't persist) |
| Challenge state | New (spec 10) | **In-memory only** (FR-025) | N/A — not persisted |
| `flows_mock_*.json` | Спек 3 dev assets | **Removed** (FR-004) | N/A — test fixtures, not wire format |

**Spec 010 explicitly does not introduce or modify any wire format** per FR-040. Все checks ниже либо проходят by-construction, либо N/A.

## Schema version

- [X] **CHK001** — Existing wire formats (`/config`, `/state`, `/links`) carry `schemaVersion` через спек 7/8. Спек 10 не вводит новый wire format → no new schemaVersion needed.
- [X] **CHK002** — N/A — spec 010 не deserializes new formats. Existing deserialization paths из спека 7/8 уже схема-version-aware.
- [X] **CHK003** — N/A — `schemaVersion` constants documented в спеке 7 (`PairingConstants`) и спеке 8 (`ConfigSyncConstants`). Спек 10 не вводит новые константы.

## Backward compatibility

- [X] **CHK004** — N/A — no schema changes.
- [X] **CHK005** — N/A — no new fields.
- [X] **CHK006** — N/A — no renames/removals.
- [X] **CHK007** — N/A — no migrations.

## Forward compatibility

- [X] **CHK008** — N/A — no new discriminators.
- [X] **CHK009** — N/A — no new sealed/discriminated unions in wire format.

## Tests

- [X] **CHK010** — Roundtrip tests existing in спеках 7/8 cover wire formats consumed by спеком 10. No new wire types → no new roundtrip tests.
- [X] **CHK011** — Backward-compat tests existing в спеках 7/8 cover read of N-1 versions. Спек 10 не invalidates.
- [X] **CHK012** — N/A — no new fixtures.

## Persistence specifics

- [X] **CHK013** — SharedPreferences/DataStore namespacing: спек 10 **умышленно удалил** persistent state design (PIN, tutorial counters). Existing persistence (спек 8 `ConfigEditor` Room DAO) — спек 10 не trogает.
- [X] **CHK014** — N/A — no SQLDelight.
- [⚠️] **CHK015** — **Removed stored types** (PIN state, tutorial overlay state) — это были **draft FR'ы в исходном спеке 10**, **никогда не были реализованы в коде**. Удалены из спека (FR-024..FR-028 переписаны на challenge; FR-034..FR-038 удалены). **Нет dangling stored types** в codebase — нечего cleanup'ить. **Observation**: если когда-то спек 010 был частично implemented (например, draft branch с PIN-кодом), нужен audit перед `/speckit.plan`. **Verify в plan:** `git log --all --grep="010" --grep="PIN" --grep="EncryptedSharedPreferences"` пустой / только текущая ветка.

## Deep-link / QR / exported config

- [X] **CHK016** — Спек 10 не вводит новые deep-link payloads. ROLE_HOME / POST_NOTIFICATIONS — platform Intents (action strings), не wire formats per definition.
- [X] **CHK017** — N/A — нет user-facing payloads.

## Contract folder

- [⚠️] **CHK018** — `specs/010-setup-assistant/contracts/` **не существует** в текущем состоянии. **Это OK**: спец явно не вводит wire formats. `/speckit.plan` может создать contracts/ если plan-level introspection обнаружит что-то stub-достойное (например, `IntentSpec` шейп). Но **обязательного контрактного файла спек 010 не требует**.

---

## Open items

1. **CHK015 audit**: до `/speckit.plan` — проверить `git log --all --grep="EncryptedSharedPreferences" -- 'specs/010-*'` чтобы убедиться, что в истории кода нет ранее закоммиченных PIN/tutorial-state файлов, которые могли бы требовать cleanup. Если есть — добавить cleanup task в `tasks.md`.

## Result

**18/18 ✓ effectively** (большая часть N/A с обоснованием — спек 010 by-design не вводит wire-format изменений). CHK015 — soft check, plan-level verification. **Самый чистый checklist для спека 010** — FR-040 explicit «no wire-format changes» закрывает всю эту область by construction.

---

## Краткое содержание (для не-разработчика)

Проверили: добавляет ли спек что-то что покидает устройство или сохраняется через перезапуск (wire format). Спек 010 **умышленно ничего не добавляет** к wire format'ам — FR-040 явно говорит «no schema changes». Challenge state — только в RAM, никогда не сохраняется и не отправляется. Поэтому почти весь checklist N/A. **Самый чистый из 13 чек-листов.**
