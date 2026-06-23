# Contract: `LocalAlternative` port

**Module**: `core/cloud/commonMain`
**Package**: `com.launcher.cloud.api`

## Interface

```kotlin
/**
 * Opt-in pattern для критических фич с локальным fallback.
 * Реализуется только теми фичами, которые ДОЛЖНЫ работать без cloud.
 * НЕ обязательный для всех cloud-фич.
 */
interface LocalAlternative {
    suspend fun executeLocally(context: ActionContext): ActionResult
}

data class ActionContext(
    val callerId: String,
    val parameters: Map<String, String> = emptyMap()
)

sealed class ActionResult {
    data class Success(val message: String? = null) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
}
```

## Invariants

| # | Invariant | Test name |
|---|---|---|
| INV-1 | `executeLocally()` НЕ делает cloud-проверок — работает независимо от `CloudAvailability`. | `no_cloud_dependency` |
| INV-2 | `executeLocally()` deterministic для одинакового `ActionContext` (в рамках одной session). | `deterministic_for_same_context` |
| INV-3 | Возвращает `Success` или `Failure` — никаких exceptions кроме `CancellationException`. | `no_exception_thrown` |
| INV-4 | Время выполнения < 1 секунды для UI-triggered actions (SC-002 для SOS). | `completes_within_1s` |

## Implementations

| Name | Module | Behaviour |
|---|---|---|
| `SOSDialerAlternative` | `androidMain` | Opens `Intent.ACTION_DIAL` с emergency number от `EmergencyNumberResolver`. |
| `FakeLocalAlternative` | `commonMain/fake` | Returns predefined `ActionResult` для тестов consumers. |

## Boundary

`LocalAlternative` НЕ:
- Знает про CloudAvailability state.
- Делает network calls.
- Persistит state.
- Запрашивает permissions (per impl — SOSDialerAlternative использует ACTION_DIAL не ACTION_CALL).

## Roundtrip / regression test

```kotlin
@Test
fun `SOS dialer alternative opens Intent with emergency number`() {
    val fakeResolver = FakeEmergencyNumberResolver(number = "112")
    val impl = SOSDialerAlternative(fakeResolver, mockContext)

    val result = runBlocking {
        impl.executeLocally(ActionContext(callerId = "sos-button"))
    }

    assertTrue(result is ActionResult.Success)
    verify(mockContext).startActivity(argThat { intent ->
        intent.action == Intent.ACTION_DIAL &&
        intent.data == Uri.parse("tel:112")
    })
}
```

## Plain Russian summary

Контракт «выполни локально». Используется только критическими фичами (SOS, в будущем — возможно, аварийные уведомления). Каждая такая фича сама решает что делать в local mode. В TASK-49 реализован один пример — SOS открывает обычный Android dialer.
