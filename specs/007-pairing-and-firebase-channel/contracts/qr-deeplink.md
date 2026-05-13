# Wire format: QR deep-link `launcher://pair?token=XXXXXX&v=1`

**Source of truth**: this document.
**Used by**: spec 007 §FR-004 (encode by Managed), FR-005 (decode by admin).
**Schema version**: `v=1` query param (отличается от `schemaVersion: Int` в JSON).
**Lifetime**: 5 минут (TTL parsed-token-а).

---

## URI scheme

```
launcher://pair?token=<TOKEN>&v=<SCHEMA>
```

| Param | Type | Required | Notes |
|---|---|---|---|
| `token` | string | ✓ | 6 chars from `[A-HJ-NP-Z2-9]` (per PairingToken regex) |
| `v` | int | ✓ | Schema version; `1` currently |

## Examples

```
launcher://pair?token=A3KX9B&v=1
launcher://pair?token=ZN7P2W&v=1
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
- Validate: `v == 1`; `token.matches([A-HJ-NP-Z2-9]{6})`.

## Parser contract (Kotlin)

```kotlin
sealed interface QrParseResult {
  data class Success(val token: PairingToken) : QrParseResult
  data object InvalidScheme : QrParseResult      // не launcher://pair
  data object UnsupportedVersion : QrParseResult  // v != 1
  data object MalformedToken : QrParseResult     // token не соответствует regex
}

fun parsePairingDeepLink(uri: String): QrParseResult
```

## Edge cases

- **Extra query params** — игнорируются (forward-compat).
- **Missing `v=` param** — treat as `v=1` (legacy compat, will be tightened in future).
- **`v=2`+ scan'ит на старом app — `UnsupportedVersion`** → UI «обновите admin-приложение».
- **URL-encoded token** (e.g. `%41` for `A`) — decoded by parser.

## Tests (commonTest)

| Test | What it verifies |
|---|---|
| `QrDeepLinkParser.parses_valid` | `launcher://pair?token=A3KX9B&v=1` → Success(A3KX9B) |
| `QrDeepLinkParser.rejects_invalid_scheme` | `https://...` → InvalidScheme |
| `QrDeepLinkParser.rejects_invalid_token_chars` | token contains `0` → MalformedToken |
| `QrDeepLinkParser.rejects_unsupported_version` | `v=2` → UnsupportedVersion |
| `QrDeepLinkParser.tolerates_extra_params` | `&foo=bar` доп. param → still Success |
| `QrDeepLinkParser.missing_v_defaults_to_1` | `?token=A3KX9B` без `v=` → Success (legacy) |
| `QrEncode.roundtrip` | Encode token → decode QR image bytes (через ZXing test helper) → original token |

## Backward compatibility policy

- `v=1` остаётся forever-supported для существующих generated QR (5 min TTL, но decode logic должен работать).
- Future `v=2` будет добавлять параметры; parser `v=1` игнорирует unknown params.
- Если когда-либо потребуется breaking change (rename `token`) — выкатываем v2 одновременно на encode-Managed и decode-admin (синхронизированный rollout).

**TODO в `parsePairingDeepLink`**: «при добавлении `v=2` — добавить case в when и не падать на legacy `v=1`».

---

<!-- novice summary -->

## TL;DR

Когда Managed показывает QR-код на экране — внутри QR закодирована **специальная ссылка** `launcher://pair?token=A3KX9B&v=1`. Admin-приложение сканирует QR, парсит ссылку, достаёт токен (6 символов) и версию схемы. Используется alphabet без `0/O/I/1` чтобы бабушка не запуталась если придётся читать вслух. Защита от старых/новых версий — через `v=` параметр.
