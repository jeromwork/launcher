package com.launcher.lint

import io.github.detekt.test.utils.compileContentForTest
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireFormatHygieneDetectorTest {

    private val rule = WireFormatHygieneDetector()

    private fun lint(fileName: String, content: String) =
        rule.lint(compileContentForTest(content, fileName))

    @Test
    fun flagsHardCodedIntVersionDefault() {
        val findings = lint(
            "com/launcher/api/Bad.kt",
            """
                package com.launcher.api
                data class SessionRecord(val schemaVersion: Int = 1)
            """.trimIndent(),
        )
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun flagsHardCodedStringVersionDefault() {
        val findings = lint(
            "com/launcher/api/Bad.kt",
            """
                package com.launcher.api
                data class Profile(val schemaVersion: String = "2.0")
            """.trimIndent(),
        )
        assertTrue(findings.isNotEmpty(), "expected a finding, got none")
    }

    @Test
    fun flagsReaderAndWriterVersionsToo() {
        val findings = lint(
            "com/launcher/api/Bad.kt",
            """
                package com.launcher.api
                data class Doc(
                    val minReaderVersion: String = "2.0",
                    val minWriterVersion: String = "2.0",
                )
            """.trimIndent(),
        )
        assertEquals(2, findings.size, "expected one finding per field: $findings")
    }

    @Test
    fun allowsReferenceToNamedConstant() {
        val findings = lint(
            "com/launcher/api/Good.kt",
            """
                package com.launcher.api
                const val SCHEMA_VERSION: String = "2.0"
                data class Profile(val schemaVersion: String = SCHEMA_VERSION)
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "constant reference is the intended shape: $findings")
    }

    @Test
    fun allowsRequiredFieldWithoutDefault() {
        val findings = lint(
            "com/launcher/api/Good.kt",
            """
                package com.launcher.api
                data class Blob(val schemaVersion: String)
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "no default means the caller supplies it: $findings")
    }

    @Test
    fun allowsTheConstantDeclarationItself() {
        val findings = lint(
            "com/launcher/api/Good.kt",
            """
                package com.launcher.api
                const val SCHEMA_VERSION: String = "2.0"
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "the constant is the one legitimate literal: $findings")
    }

    @Test
    fun ignoresTestSources() {
        val findings = lint(
            "com/launcher/api/commonTest/FixtureTest.kt",
            """
                package com.launcher.api
                val fixture = Profile(schemaVersion = "2.0")
                data class Profile(val schemaVersion: String = "2.0")
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "fixtures legitimately pin literal versions: $findings")
    }

    @Test
    fun ignoresUnrelatedFields() {
        val findings = lint(
            "com/launcher/api/Good.kt",
            """
                package com.launcher.api
                data class Tile(val label: String = "home", val size: Int = 2)
            """.trimIndent(),
        )
        assertEquals(0, findings.size)
    }
}
