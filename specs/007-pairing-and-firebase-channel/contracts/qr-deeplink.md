# Wire format: QR deep-link `launcher://pair?token=XXXXXX&v=1.0`

**Source of truth**: this document.
**Used by**: spec 007 §FR-004 (encode by Managed), FR-005 (decode by admin).
**Version**: single dotted field `v`, read as `minReaderVersion` — this is a **read-once
transport** and carries one version field, not three (docs/architecture/wire-format.md §3).
The receiver scans once and discards; it never writes the link back, so `minWriterVersion`
(the write-back gate) and `schemaVersion` (a diagnostic no reader acts on) describe states
that cannot occur. TASK-143.
**Lifetime**: 5 минут (TTL parsed-token-а).

---

## URI scheme

```
launcher://pair?token=<TOKEN>&v=<SCHEMA>
```

| Param | Type | Required | Notes |
|---|---|---|---|
| `token` | string | ✓ | 6 chars from `[A-HJ-NP-Z2-9]` (per PairingToken regex) |
| `v` | dotted string | ✓ | `minReaderVersion`, e.g. `1.0`. From `QrDeepLinkParser.MIN_READER_VERSION` |

## Examples

```
launcher://pair?token=A3KX9B&v=1.0
launcher://pair?token=ZN7P2W&v=1.0
```

## QR encoding (Managed side, FR-004)

- Library: ZXing (`com.google.zxing:core`).
- Error correction level: **M** (medium — balance reliability vs size).
- Module size: auto-fit на экран (рекомендация — minimum 4× viewing distance чтобы scanner read).
- Сопровождается **countdown timer** «истекает через 4:32».

## QR decoding (admin side, FR-005)

- Camera: CameraX preview.
- Detection: ML Kit Barcode Scanning (`barcode-scanning`); fallback на ZXing.
- After detect → parse `launcher://pair?...` → extract `token` + `v`.
- Validate: `v` parses as a dotted version and `v <= MIN_READER_VERSION`; `token.matches([A-HJ-NP-Z2-9]{6})`.

## Parser contract (Kotlin)

```kotlin
sealed interface QrParseResult {
  data class Success(val token: PairingToken) : QrParseResult
  data object InvalidScheme : QrParseResult       // не launcher://pair
  data object UnsupportedVersion : QrParseResult  // v requires a reader newer than this build (fail closed)
  data object MalformedToken : QrParseResult      // token не соответствует regex
}

fun parsePairingDeepLink(uri: String): QrParseResult
fun buildPairingDeepLink(token: PairingToken): String   // single source of the URI grammar
```

## Reader gate (compare, don't equate)

`v` is the **minimum reader version** the link requires. The reader accepts when
`v <= MIN_READER_VERSION` (this build's level) and refuses otherwise — three outcomes collapse
to two here because a read-once transport has no read-only middle state. An additive future
change keeps `v` at `1.0` and adds params; a breaking change raises `v`.

## Edge cases

- **Extra query params** — игнорируются (forward-compat additive change).
- **Missing / unparseable `v`** — `UnsupportedVersion` (fail closed §4). No legacy default: pre-MVP,
  no old links exist in the field. A bare integer `v=1` is unparseable now — the field is dotted.
- **`v` newer than this build** (e.g. `v=2.0`) — `UnsupportedVersion` → UI «обновите admin-приложение».
- **URL-encoded token** (e.g. `%41` for `A`) — decoded by parser.

## Tests (commonTest)

| Test | What it verifies |
|---|---|
| `parses_valid_link` | `…&v=1.0` → Success(A3KX9B) |
| `builder_and_parser_roundtrip` | `buildPairingDeepLink` → `parsePairingDeepLink` → original token |
| `rejects_invalid_scheme` | `https://...` → InvalidScheme |
| `rejects_invalid_token_chars` | token contains `0` → MalformedToken |
| `rejects_link_requiring_a_newer_reader` | `v=2.0` → UnsupportedVersion |
| `accepts_same_version_with_unknown_future_params` | `v=1.0&flow=fast` → Success (AC #3) |
| `rejects_malformed_version` | `v=1`, `v=abc` → UnsupportedVersion (fail closed) |
| `rejects_missing_version` | `?token=A3KX9B` без `v=` → UnsupportedVersion |

## Version policy

- `v` is a single dotted field per the read-once rule (wire-format.md §3). It rises only on a
  **breaking** change to the link shape (e.g. renaming `token`); additive changes add params and
  leave `v` unchanged, and old readers tolerate the unknown params.
- A breaking bump ships simultaneously on encode-Managed and decode-admin (synchronized rollout);
  because the link is read-once with a 5-minute TTL, there is no persisted corpus to migrate.

---

<!-- novice summary -->

## TL;DR

Когда Managed показывает QR-код на экране — внутри QR закодирована **специальная ссылка** `launcher://pair?token=A3KX9B&v=1.0`. Admin-приложение сканирует QR, парсит ссылку, достаёт токен (6 символов) и версию. Используется alphabet без `0/O/I/1` чтобы бабушка не запуталась если придётся читать вслух. Версия одна (`v`) — «минимальная версия читателя»: этот код читают один раз и не переписывают обратно, поэтому три числа не нужны, достаточно одного (см. `docs/architecture/wire-format.md` §3, TASK-143).
