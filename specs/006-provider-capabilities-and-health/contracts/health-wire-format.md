# Contract: `Health` Wire Format

**Version:** 1.0.0 · **Status:** Stable from спек 006 · **Owner:** spec 006
**Type:** [`Health`](../data-model.md#2-health) · **Test:** `HealthWireFormatTest`
**Fixtures:** `core/src/commonTest/resources/fixtures/health-wire-format/`

---

## Purpose

JSON serialization of per-device health snapshot. Primary consumer в спеке 006 — local DataStore + UI banners derivation. Future consumers (спек 007): Firestore export to `/links/{linkId}/health`.

---

## Schema (v1)

```json
{
  "schemaVersion": 1,
  "batteryPercent": 78,
  "charging": false,
  "connectivity": "Wifi",
  "ringerVolumePercent": 60,
  "audioStreamMuted": false,
  "lastSeen": 1746780123456,
  "appVersion": "1.4.2"
}
```

### Field reference

| Field | Type | Required | Default | Constraint |
|-------|------|----------|---------|------------|
| `schemaVersion` | Int | ✓ | 1 | ≥ 1 |
| `batteryPercent` | Int | ✓ | — | 0..100 (clamped); 0 if unknown |
| `charging` | Bool | ✓ | false | false if unknown |
| `connectivity` | enum | ✓ | — | `Wifi` \| `Mobile` \| `None`; unknown → `None` (FR-44) |
| `ringerVolumePercent` | Int | ✓ | — | 0..100 (normalised, NOT raw STREAM_RING units) |
| `audioStreamMuted` | Bool | ✓ | — | true ⟺ `STREAM_RING == 0` OR DND active suppressing ringer |
| `lastSeen` | Long | ✓ | — | epoch millis of most recent RESUMED |
| `appVersion` | String | ✓ | — | launcher's own `BuildConfig.VERSION_NAME` |

---

## Forward / backward compatibility policy

Same as [Capability wire format](./capability-wire-format.md#forward--backward-compatibility-policy).

**Connectivity enum extension** (anticipated в схеме v2 или v1.x via additive enum value):
- Adding `Vpn`, `Ethernet` is non-breaking — readers using `Connectivity.fromWireOrNone()` map unknown to `None`.

---

## Roundtrip test contract

`HealthWireFormatTest` MUST cover:
1. Roundtrip every variant: connectivity ∈ {Wifi, Mobile, None} × charging × audioStreamMuted.
2. Boundary values: batteryPercent=0, batteryPercent=100, ringerVolumePercent=0 (with audioStreamMuted=true), ringerVolumePercent=100.
3. Forward-compat fixture с `schemaVersion: 999` + `"connectivity": "Vpn"` (unknown enum) → parses without crash, connectivity becomes `None` (SC-015 + FR-44).
4. Fixture files in `fixtures/health-wire-format/`.

---

## Breaking-change policy

Same as [Capability](./capability-wire-format.md#breaking-change-policy).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Контракт wire-format `Health` v1.0.0 — JSON со снапшотом устройства: 8 полей (`schemaVersion`, `batteryPercent`, `charging`, `connectivity`, `ringerVolumePercent`, `audioStreamMuted`, `lastSeen`, `appVersion`). Все обязательные. Сейчас локальный DataStore + источник для баннеров; в 007 — Firestore.

**Конкретика, которую стоит запомнить:**
- `connectivity` — string enum `Wifi|Mobile|None`; **неизвестные значения = `None`** (через `Connectivity.fromWireOrNone()`, FR-44). Это позволит спеку с `Vpn` без миграции.
- `ringerVolumePercent` — **нормализованные 0..100**, не raw Android STREAM_RING units (которые 0..15 на большинстве девайсов).
- `audioStreamMuted` — **effective state**, не volume value: true может быть и при volume>0 если DND блокирует ringer.
- `lastSeen` — epoch millis (Long), время последнего RESUMED события.

**На что смотреть с осторожностью:**
- `appVersion: String` — это **launcher's own** version, не version таргетного приложения. Не путать с `Capability.versionCode`.
- При парсинге **никогда не выбрасывать exception на unknown `connectivity`** — обязательно прогнать через `fromWireOrNone()`. Фитнесс-тест на это нет, но roundtrip-тест должен покрыть «schemaVersion=999 + connectivity=Vpn → парсится, connectivity=None».
