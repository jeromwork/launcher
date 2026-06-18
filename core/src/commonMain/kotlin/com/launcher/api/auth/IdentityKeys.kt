package com.launcher.api.auth

/**
 * Forward declaration для F-5 (ConfigCipher spec). В F-4 это marker-интерфейс
 * без членов — все реализации в F-4 кодпасе передают `null` в [User.identityKeys].
 *
 * Реальный тип (с derive-функциями master-ключа из stableId) определит F-5
 * вместе со специей F-CRYPTO. На стороне F-4 нам важно только зарезервировать
 * поле в [User], чтобы потом не делать breaking-change consumer'ам.
 *
 * Per spec 017 FR-010.
 */
interface IdentityKeys
