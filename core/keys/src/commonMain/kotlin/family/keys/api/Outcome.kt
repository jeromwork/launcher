package family.keys.api

/**
 * Typed result with explicit error channel. Same shape как
 * `com.launcher.api.result.Outcome` в `:core` модуле, локальная копия чтобы
 * `:core:keys` оставался slim (без Compose / SQLDelight transitive deps от `:core`).
 *
 * TODO(refactor-when-3rd-consumer): когда третий потребитель `Outcome` появится
 * за пределами `:core` (сейчас :core:keys = второй) — extract в slim модуль
 * `:core:common` или эквивалент, и удалить эту локальную копию.
 *
 * Named [Outcome] (не `Result`) чтобы не конфликтовать со stdlib `kotlin.Result`.
 */
sealed interface Outcome<out T, out E> {
    data class Success<out T>(val value: T) : Outcome<T, Nothing>
    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}
