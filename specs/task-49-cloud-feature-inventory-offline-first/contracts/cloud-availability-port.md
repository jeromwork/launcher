# Contract: `CloudAvailability` port

**Module**: `core/cloud/commonMain`
**Package**: `com.launcher.cloud.api`

## Interface

```kotlin
interface CloudAvailability {
    suspend fun isCloudAvailable(): Boolean
    val isCloudAvailableFlow: Flow<Boolean>
}
```

## Invariants

| # | Invariant | Test name |
|---|---|---|
| INV-1 | `isCloudAvailable()` returns current boolean из persistent storage (< 10ms typical). | `read_returns_current_value` |
| INV-2 | После `setAvailable(true)` (внутреннее API impl'а — через AuthProvider event) — `isCloudAvailable()` returns `true` synchronously. | `write_then_read_consistent` |
| INV-3 | `isCloudAvailableFlow` emits initial value на subscribe. | `flow_emits_initial_value` |
| INV-4 | `isCloudAvailableFlow` emits на каждое change флага. | `flow_emits_on_change` |
| INV-5 | `isCloudAvailableFlow` НЕ emits duplicates (distinct-until-changed). | `flow_distinct_until_changed` |
| INV-6 | Default value на свежем install / clear data = `false`. | `default_is_false` |
| INV-7 | Persistence: после destroy + recreate instance (имитация app kill) — последнее записанное value доступно. | `persistence_survives_recreate` |

## Implementations

| Name | Module | Behaviour |
|---|---|---|
| `CloudAvailabilityImpl` | `androidMain` | DataStore-backed, subscribes to `AuthProvider.currentUser`. |
| `FakeCloudAvailability` | `commonMain/fake` | In-memory `MutableStateFlow`, manual `set(value)` для тестов. |

## Boundary

`CloudAvailability` НЕ предоставляет:
- Sync write API из caller'а (только `AuthProvider` events двигают флаг через impl internals).
- Network probes.
- Token validity checks.
- GMS availability checks.

## Roundtrip / regression test

```kotlin
@Test
fun `auth provider sign-in event sets flag true`() = runTest {
    val fakeAuth = FakeAuthProvider()
    val impl = CloudAvailabilityImpl(testDataStore, fakeAuth, scope)

    fakeAuth.emitIdentity(testIdentity)
    advanceUntilIdle()

    assertTrue(impl.isCloudAvailable())
}

@Test
fun `auth provider sign-out event sets flag false`() = runTest {
    val fakeAuth = FakeAuthProvider()
    val impl = CloudAvailabilityImpl(testDataStore, fakeAuth, scope)
    fakeAuth.emitIdentity(testIdentity); advanceUntilIdle()

    fakeAuth.emitIdentity(null)
    advanceUntilIdle()

    assertFalse(impl.isCloudAvailable())
}
```

## Plain Russian summary

Контракт «спросить — работаем ли с облаком». Два метода: один синхронный (быстрое булево чтение) и один Flow (реактивная подписка для UI). Внутри подписан на `AuthProvider` — меняется только когда пользователь вошёл или вышел. Никаких сетевых проверок.
