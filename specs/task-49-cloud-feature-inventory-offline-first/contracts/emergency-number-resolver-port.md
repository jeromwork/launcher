# Contract: `EmergencyNumberResolver` port

**Module**: `core/cloud/commonMain`
**Package**: `com.launcher.cloud.api`

## Interface

```kotlin
interface EmergencyNumberResolver {
    suspend fun getEmergencyNumber(): String
}
```

## Invariants

| # | Invariant | Test name |
|---|---|---|
| INV-1 | Возвращает non-empty string. | `returns_non_empty_string` |
| INV-2 | Returned string — valid telephone number format (digits, possibly `+`, `*`, `#`). | `returns_valid_phone_format` |
| INV-3 | На устройстве с RU locale (если не overridden TelephonyManager) → возвращает один из `[102, 103, 112]`. | `russia_locale_returns_russia_number` |
| INV-4 | На устройстве с US locale → возвращает `911`. | `us_locale_returns_911` |
| INV-5 | На устройстве с EU locale → возвращает `112`. | `eu_locale_returns_112` |
| INV-6 | На API < 29 (где `getCurrentEmergencyNumberList` недоступен) → fallback на hardcoded map по country code. | `fallback_uses_hardcoded_map` |
| INV-7 | Не требует runtime permissions для fallback path. | `fallback_no_permissions` |

## Implementations

| Name | Module | Behaviour |
|---|---|---|
| `EmergencyNumberResolverImpl` | `androidMain` | API 29+: `TelephonyManager.getCurrentEmergencyNumberList()`. Fallback: hardcoded map. |
| `FakeEmergencyNumberResolver` | `commonMain/fake` | Returns constructor-provided number. |

## Hardcoded fallback map

| Country (ISO 3166-1) | Number |
|---|---|
| `RU`, `BY`, `KZ` | `102` (police primary) |
| `US`, `CA` | `911` |
| `GB`, EU (DE, FR, IT, ES, NL, PL, ...) | `112` |
| `IN` | `112` |
| `JP` | `110` |
| `AU` | `000` |
| `CN` | `110` |
| (default) | `112` (worldwide) |

## Boundary

НЕ предоставляет:
- Полный список emergency numbers (только первый).
- Differentiation по типу emergency (police / fire / medical) — out of scope для TASK-49.
- Permission requests.

## Roundtrip / regression test

```kotlin
@Test
fun `russia locale resolves to russian number`() = runTest {
    val impl = EmergencyNumberResolverImpl(
        telephonyManager = mockTelephonyManager(getCurrentEmergencyNumberList = null), // simulate API < 29
        localeProvider = { Locale("ru", "RU") },
    )

    val number = impl.getEmergencyNumber()

    assertEquals("102", number)
}

@Test
fun `api 29 plus uses TelephonyManager when available`() = runTest {
    val impl = EmergencyNumberResolverImpl(
        telephonyManager = mockTelephonyManager(getCurrentEmergencyNumberList = listOf("112")),
        localeProvider = { Locale.getDefault() },
    )

    val number = impl.getEmergencyNumber()

    assertEquals("112", number)
}
```

## Plain Russian summary

Контракт «дай номер службы спасения для текущей страны». На современных Android (API 29+) спрашиваем у системы (она знает точный список для SIM-карты / страны). На старых Android — fallback на нашу hardcoded таблицу (РФ=102, США=911, ЕС=112, и т.д.). Один метод, не требует разрешений.
