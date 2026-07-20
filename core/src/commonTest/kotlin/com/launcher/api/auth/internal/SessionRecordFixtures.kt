package com.launcher.api.auth.internal

import family.wire.WireVersion

/**
 * Тестовые фикстуры для wire-format тестов [SessionRecord] v1.
 *
 * Фикстуры — `const val String` (а не файлы в resources), потому что
 * KMP commonTest имеет неоднородную поддержку classpath resources между
 * Android/JVM/iOS. Inline-string гарантирует одинаковое поведение на всех
 * платформах + детерминированность (никаких file I/O).
 *
 * Per spec 017 contract `session-record-v1.md` §"Test fixtures".
 */
internal object SessionRecordFixtures {

    /**
     * Канонический v1 JSON. Hardcoded значения (никакого `randomUUID()`)
     * чтобы фикстура была stable между запусками.
     */
    const val V1_CANONICAL: String = """
        {
          "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
          "stableId": "550e8400-e29b-41d4-a716-446655440000",
          "expiresAtEpochMillis": 1739456789000,
          "refreshToken": "1//04test-refresh-token-stable-fixture",
          "extra": {
            "firebase_jwt": "eyJhbGciOiJSUzI1NiIs.test.payload"
          }
        }
    """

    /**
     * v2 forward-compat fixture: hypothetical future schemaVersion=2.
     * Используется в backward-compat тесте — current parser должен
     * корректно отвергнуть его (graceful failure), а не упасть.
     */
    const val V2_HYPOTHETICAL: String = """
        {
          "schemaVersion": "2.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
          "stableId": "550e8400-e29b-41d4-a716-446655440000",
          "newFieldFromV2": "value-not-in-v1-schema"
        }
    """
}
