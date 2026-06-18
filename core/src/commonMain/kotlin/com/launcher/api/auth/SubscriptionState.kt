package com.launcher.api.auth

/**
 * Состояние подписки/тарифа пользователя. В F-4 MVP всегда [Unknown]
 * (per spec 017 FR-031): client-side **не** имеет права вычислять [Active]
 * или [Expired] — эти состояния должны приходить server-validated JWT
 * (это S-10 territory).
 *
 * Detekt-правило `NoClientComputedSubscriptionActive` (T798, Phase 8)
 * запретит присваивание [Active] / [Expired] в любом client-side коде
 * кроме fake-адаптеров для тестов.
 *
 * Per clarification Q6.
 */
sealed class SubscriptionState {
    object Unknown : SubscriptionState()
    object LocalOnly : SubscriptionState()
    object Trial : SubscriptionState()
    object Active : SubscriptionState()
    object Expired : SubscriptionState()
}
