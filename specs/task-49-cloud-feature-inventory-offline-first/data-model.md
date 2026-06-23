# Data Model: TASK-49

## Entities

### 1. DataStore Preference

Один булев preference в Android DataStore.

| Field | Type | Default | Notes |
|---|---|---|---|
| `cloud_available` | `Boolean` | `false` | Set by `CloudAvailabilityImpl` через subscription на `AuthProvider.currentUser`. `true` если identity != null, `false` если null. |

**Storage**: `Preferences DataStore` (Android), file `cloud_settings.preferences_pb` (или podобное), под app private storage.

**Persistence**: переживает app kill / device reboot. Удаляется только при app uninstall / clear data.

**Schema versioning**: **не требуется**. Single boolean, не wire format, не leaves device. Per CLAUDE.md rule 5 — это internal preference, не public contract.

### 2. ActionContext (runtime data type)

Используется `LocalAlternative.executeLocally(context: ActionContext)`.

```kotlin
data class ActionContext(
    val callerId: String,            // Кто вызвал (e.g., "sos-button")
    val parameters: Map<String, String> = emptyMap()  // Опциональные параметры
)
```

**Lifecycle**: runtime only. Не persistent, не wire format.

### 3. ActionResult (runtime data type)

Возврат `LocalAlternative.executeLocally()`.

```kotlin
sealed class ActionResult {
    data class Success(val message: String? = null) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
}
```

**Lifecycle**: runtime only.

## Что НЕ data model в этой спеке

- AuthIdentity (existing — TASK-3 / spec 017 territory).
- FCM token (existing — TASK-5 / spec 019 territory).
- Recovery backup blob (TASK-6 territory, on Paused).

## Migration

**Не требуется.** Свежее install → `cloud_available` default = `false`. Existing users (post-upgrade с уже-registered FCM token) → preference будет `false` до первого emit от `AuthProvider.currentUser` после рестарта app, потом `true` (если identity есть). Existing FCM token остаётся в Firestore — FR-014 explicit.

## Plain Russian summary (для не-разработчика)

В TASK-49 хранится **одна вещь** — булев флаг «работаем с облаком или нет» в стандартном Android-файле настроек (DataStore). Этот флаг переживает перезагрузку телефона, исчезает только при удалении приложения. Никаких других данных TASK-49 не вводит — все остальные «структуры» это runtime-значения для передачи между функциями.
