package family.push.api

/**
 * Typed result with explicit error channel. Same shape как
 * `family.keys.api.Outcome` (которая, в свою очередь, копия из `:core` модуля).
 *
 * F-5c push — **третий** consumer Outcome (после :core launcher и :core:keys).
 * Per TODO в [family.keys.api.Outcome], при появлении третьего consumer'а
 * Outcome должна была быть extracted в slim `:core:common` модуль. Это
 * remains TODO outside spec 019 scope.
 *
 * TODO(refactor-when-3rd-consumer-confirmed):
 *   extract `:core:common` с Outcome + map/flatMap + удалить локальные копии в
 *   :core:keys/api/Outcome.kt и :core:push/api/Outcome.kt. Триггер настал —
 *   но requires cross-module refactor вне F-5c scope. Tracked в project-backlog.md.
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
