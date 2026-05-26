# Contract: `Tile.DocumentTile` Sealed Variant

**Version:** 1.0.0 (additive to spec 008 `/config schemaVersion = 1`) · **Status:** Draft (spec 012)
**Owner:** spec 012 extends spec 008
**Tests:** `TileWireFormatTest.roundtrip_document`, `TileWireFormatTest.backward_compat_unknown_kind`

---

## Purpose

Документ-плитка для бабушки. Содержит ссылку на расшифровываемый blob (фото документа) + подпись («Паспорт», «СНИЛС»).

---

## Format (JSON, в `/config/current.tiles[]`)

```json
{
  "id": "t1234567-1234-4321-8765-abcdefabcdef",
  "kind": "document",
  "documentRef": "private:f1111111-2222-4333-9444-555555555555",
  "label": "Паспорт"
}
```

### Field schema

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | `ElementId` (UUIDv4 string) | ✓ | Per spec 008 — stable identity для diff/merge |
| `kind` | string literal `"document"` | ✓ | Discriminator for sealed `Tile` |
| `documentRef` | string `^private:[a-f0-9-]{36}$` | ✓ | IconRef namespace `private:<uuid>` (per spec 006 icon-id-namespace.md). UUID указывает на blob в `/links/{linkId}/private-media/{uuid}` (per 011 encrypted-media-storage.md). |
| `label` | string (1..40 graphemes after sanitisation) | ✓ | Display label («Паспорт», «Медкарта»). Validation: trim, strip control chars, ≤ 40 code points. Sanitised label также шифруется внутри ciphertext envelope'а (FR-006 privacy invariant — НЕ в metadata). |

---

## Backward compatibility policy

### Pre-production (current state, до spec 030+)

**Schema version stays at 1.** No bump. Justification — Clarification Q2:
- Программа не в production у пользователей.
- "Backward compatibility constraint защищает уже-развёрнутые контракты" (CLAUDE.md rule 5) — нет развёрнутых пользователей → constraint не применяется.

Old in-progress launcher без поддержки `kind="document"`:
- Reader parses Tile array → encounters unknown `kind` → graceful skip + emit `PartialReason.UnknownSlotKind` (per [state-applied.md:67](../../008-bidirectional-config-sync/contracts/state-applied.md#L67)).
- **No crash, no abort of /config apply.** Остальные tiles применяются normally.

### Post-production (со spec 030+)

**Sunset of this deviation.** Со spec 030 любое следующее расширение `Tile` MUST go through:
1. Bump `/config schemaVersion: 1 → 2`.
2. Reader migration (Vn_To_Vn1 transformer per [TODO-ARCH-015](../../../docs/dev/project-backlog.md)).
3. Reader-side gate: `if schemaVersion > maxSupported → SchemaUnsupported`.

`DocumentTile` itself stays at v1 (additive in pre-production).

---

## Validation rules

Reader MUST handle:

| Case | Behaviour |
|---|---|
| Valid `DocumentTile` | parse and render (FR-018 — tap → DocumentViewer) |
| `documentRef` missing or empty | Reject the tile (parse error → skip + emit diagnostic) — do NOT default `documentRef` |
| `documentRef` not matching `^private:[a-f0-9-]{36}$` regex | Same — reject |
| `label` empty | Reject (sanitisation requires ≥ 1 grapheme) |
| `label` > 40 graphemes | Truncate to 40 + log warning (admin error) — НЕ reject (graceful) |
| Unknown `kind` (forward-compat) | Emit `PartialReason.UnknownSlotKind`, skip this tile, continue |

---

## Tests (commonTest)

```kotlin
class TileWireFormatTest {

    @Test
    fun roundtrip_document() {
        val tile = Tile.DocumentTile(
            id = ElementId("t1234567-1234-4321-8765-abcdefabcdef"),
            documentRef = "private:f1111111-2222-4333-9444-555555555555",
            label = "Паспорт",
        )
        val json = Json.encodeToString(tile)
        val parsed = Json.decodeFromString<Tile>(json)
        assertEquals(tile, parsed)
    }

    @Test
    fun forward_compat_unknown_kind() {
        val unknownJson = """{"id":"...","kind":"audio","audioRef":"private:..."}"""
        val parsed = Json.decodeFromString<Tile?>(unknownJson)
        // graceful: либо null, либо специальная UnknownTile branch
        // emit `PartialReason.UnknownSlotKind` через caller
    }

    @Test
    fun reject_missing_documentRef() { ... }

    @Test
    fun reject_invalid_documentRef_format() { ... }

    @Test
    fun reject_empty_label() { ... }

    @Test
    fun truncate_long_label_with_warning() { ... }

    @Test
    fun coexists_with_existing_kinds() {
        // /config.tiles[] containing ContactTile, AppTile, FlowTile, DocumentTile mixed
        // roundtrip preserves all
    }
}
```

**Fixtures**: `commonTest/resources/wire-format/`:
- `tile-v1-document.json` — minimal valid DocumentTile.
- `tile-v1-mixed-kinds.json` — `/config.tiles[]` со всеми 4 kinds.
- `tile-v1-unknown-kind.json` — forward-compat case.

---

## Cross-references

- [`Tile`](../../../core/src/commonMain/kotlin/com/launcher/api/config/Tile.kt) — sealed hierarchy (existing, owned by spec 008).
- [`IconRef` namespace](../../006-provider-capabilities-and-health/contracts/icon-id-namespace.md) — `private:` namespace.
- [`EncryptedMediaStorage` layout](../../011-contacts-and-e2e-encrypted-media/contracts/encrypted-media-storage.md) — где blob physically лежит.
- [`PartialReason.UnknownSlotKind`](../../008-bidirectional-config-sync/contracts/state-applied.md#L67) — forward-compat fallback.

---

## TL;DR (для новичка)

**Что это**: документ-плитка в раскладке бабушки. Когда admin жмёт «+ документ» и подтверждает фото из галереи — `/config` обогащается новой плиткой с `kind="document"`, ссылкой на зашифрованный blob (`documentRef`) и подписью (`label`).

**Что нового по сравнению с другими плитками**: `documentRef` указывает на расшифровываемый файл (фото паспорта), а `label` — это название для бабушки («Паспорт»). Это похоже на `ContactTile.photoRef`, но (а) тут label показывается крупно, (б) тап открывает fullscreen viewer (не звонок).

**Почему без bump'a `schemaVersion`**: программа ещё не в production до спека 030+, поэтому wire format можно менять additive (см. Clarification Q2 в spec.md).
