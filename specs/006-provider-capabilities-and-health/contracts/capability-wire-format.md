# Contract: `Capability` Wire Format

**Version:** 1.0.0 · **Status:** Stable from спек 006 · **Owner:** spec 006
**Type:** [`Capability`](../data-model.md#1-capability) · **Test:** `CapabilityWireFormatTest`
**Fixtures:** `core/src/commonTest/resources/fixtures/capability-wire-format/`

---

## Purpose

JSON serialization of per-provider availability snapshot. Primary consumer in спеке 006 — local DataStore persistence. Future consumers (спек 007): Firestore export to `/links/{linkId}/capabilities`.

---

## Schema (v1)

```json
{
  "schemaVersion": 1,
  "providerId": "whatsapp",
  "displayName": "WhatsApp",
  "iconId": "bundled:whatsapp",
  "iconSha256": null,
  "available": true,
  "versionCode": 241800
}
```

### Field reference

| Field | Type | Required | Default | Constraint |
|-------|------|----------|---------|------------|
| `schemaVersion` | Int | ✓ | 1 | ≥ 1; reader accepts > SUPPORTED with best-effort (FR-043) |
| `providerId` | String | ✓ | — | matches spec 005 `[a-z][a-z0-9_-]{1,31}` |
| `displayName` | String | ✓ | — | 1..64 chars, user-facing label |
| `iconId` | String | ✓ | — | namespace-prefixed, see [icon-id-namespace.md](./icon-id-namespace.md) |
| `iconSha256` | String? | ✗ | null | 64-char lowercase hex if present; null for `bundled:` icons |
| `available` | Bool | ✓ | — | true ⟺ provider can dispatch right now |
| `versionCode` | Long? | ✗ | null | Android `versionCode` if installed; null if iOS or not installed |

---

## Forward / backward compatibility policy

- **Adding fields:** allowed, must have `@Serializable` default. Old readers ignore (kotlinx-serialization `ignoreUnknownKeys = true` shared from спека 005).
- **Removing fields:** requires schemaVersion bump + migration written before breaking change ships (CLAUDE.md rule 5).
- **Renaming fields:** same as removal.
- **`schemaVersion > SUPPORTED`:** parse-best-effort, no exception (FR-043). Consumers may downgrade behaviour.
- **Unknown enum values** (where applicable): documented per-field fallback.

---

## Roundtrip test contract

`CapabilityWireFormatTest` MUST cover:
1. Roundtrip every field combination: `available=true|false × versionCode=null|123 × iconSha256=null|hex`.
2. Serialise + parse identical = original.
3. Fixture files in `fixtures/capability-wire-format/` parse to expected objects.
4. Forward-compat fixture `forward-compat-v999.json` (with `schemaVersion: 999` and extra unknown fields) parses without crash, known fields populate (SC-015).

---

## Breaking-change policy

- **Major bump (v2):** required for field rename, field removal, semantic change of existing field.
- **Minor changes (no version bump):** new optional field with default value.
- **Migration plan** (if v2 ever ships): dual-write для one major release, then dual-read can be removed.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Контракт wire-format `Capability` v1.0.0 — JSON со снапшотом одного провайдера: `{schemaVersion, providerId, displayName, iconId, iconSha256?, available, versionCode?}`. Сейчас сохраняется в локальный DataStore; в спеке 007 уйдёт в Firestore без переделки.

**Конкретика, которую стоит запомнить:**
- `schemaVersion: 1` обязателен с первого коммита.
- 5 обязательных полей (`schemaVersion`, `providerId`, `displayName`, `iconId`, `available`), 2 nullable (`iconSha256`, `versionCode`).
- `iconSha256` для `bundled:` иконок = null (нет смысла в checksum для in-APK assets).
- Reader **не падает** на `schemaVersion > 1` — best-effort parse; неизвестные поля игнорируются.
- Roundtrip-тест: write → read → assertEquals для каждого варианта; forward-compat-тест: fixture с `schemaVersion: 999` парсится.

**На что смотреть с осторожностью:**
- Переименование/удаление поля = breaking change (major bump). Перед изменением — миграция written first.
- `versionCode` — `Long?`, не `Int`. Долгая история PackageInfoCompat: на Android было Int, в новом API стало Long.
