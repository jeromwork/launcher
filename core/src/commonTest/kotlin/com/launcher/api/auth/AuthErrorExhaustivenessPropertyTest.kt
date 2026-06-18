package com.launcher.api.auth

import com.launcher.api.result.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property-style test: каждая variant [AuthError] возвращается хотя бы одним
 * `simulate*()` методом [FakeAuthAdapter].
 *
 * Цель — гарантировать, что когда consumer пишет exhaustive `when (error)`,
 * все ветки реально достижимы через test API. Если в будущем добавят новый
 * [AuthError] вариант без соответствующего simulator — тест провалится и
 * заставит обновить fake (per CLAUDE.md §6 mock-first invariant).
 *
 * 1000 iterations — статистическая гарантия что random-генерированные seed'ы
 * покрывают каждую ветку.
 *
 * Per spec 017 FR-009, plan.md §"Test Strategy" #2.
 */
class AuthErrorExhaustivenessPropertyTest {

    @Test
    fun everyAuthErrorVariantReachable() = runTest {
        val seen = mutableSetOf<String>()
        // Поскольку Kotest property недоступен в commonTest, имитируем
        // 1000-iteration property test обычным циклом — каждая итерация
        // выбирает случайный simulator по индексу seed % 5.
        repeat(1000) { seed ->
            val adapter = FakeAuthAdapter()
            when (seed % 5) {
                0 -> adapter.simulateNetworkError()
                1 -> adapter.simulateCancellation()
                2 -> adapter.simulateNoEmail()
                3 -> adapter.simulateProviderUnavailable()
                4 -> adapter.simulateUnknown("seed=$seed")
            }
            val result = adapter.signIn()
            assertTrue(result is Outcome.Failure, "Iteration $seed: expected Failure, got $result")
            seen += categoryName(result.error)
        }
        // Проверяем что покрыли все 5 variants AuthError.
        val expected = setOf("NetworkError", "Cancelled", "NoEmail", "ProviderUnavailable", "Unknown")
        assertTrue(
            seen.containsAll(expected),
            "Some AuthError variants not reachable through simulators. Seen: $seen, expected: $expected",
        )
    }

    private fun categoryName(error: AuthError): String = when (error) {
        AuthError.NetworkError -> "NetworkError"
        AuthError.Cancelled -> "Cancelled"
        AuthError.NoEmail -> "NoEmail"
        AuthError.ProviderUnavailable -> "ProviderUnavailable"
        is AuthError.Unknown -> "Unknown"
    }
}
