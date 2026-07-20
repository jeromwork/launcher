package com.launcher.lint

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Enforces the single-source rule for wire-format version values, per
 * `docs/architecture/wire-format.md` §11: each format declares its version as
 * one named constant beside the type, and no version literal appears anywhere
 * else.
 *
 * A hard-coded default (`val schemaVersion: Int = 1`) is the failure this
 * catches: the value is then a magic number with no single place to bump, and
 * the version drifts silently between the model, its fixtures, and its tests.
 *
 * ## Soft mode (current)
 *
 * This rule deliberately checks only what is already true of the codebase — a
 * gate that is red on arrival gets suppressed and stops gating. It does NOT yet
 * require the dotted-string form, `minReaderVersion`/`minWriterVersion`, or a
 * golden fixture. Those become mandatory once TASK-138 has converted the
 * existing formats; see `wire-format.md` §11 "Migration of existing formats".
 *
 * Escape hatch: `@Suppress("WireFormatHygiene")` with a justification comment.
 */
class WireFormatHygieneDetector(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = "WireFormatHygiene",
        severity = Severity.Defect,
        description = "A wire-format version must come from a named constant, " +
            "not a literal (docs/architecture/wire-format.md §11).",
        debt = Debt.TEN_MINS,
    )

    /** Test sources legitimately pin literal versions in fixtures and assertions. */
    private val testPathMarkers = listOf(
        "/test/", "/androidTest/", "/commonTest/", "/jvmTest/",
        "/androidUnitTest/", "/iosTest/", "/testFixtures/",
    )

    private var scanning = true

    override fun visitKtFile(file: KtFile) {
        val path = file.virtualFilePath.replace('\\', '/')
        scanning = testPathMarkers.none { path.contains(it) }
        if (scanning) super.visitKtFile(file)
    }

    override fun visitParameter(parameter: KtParameter) {
        if (scanning && parameter.hasValOrVar()) {
            check(parameter.name, parameter.defaultValue, parameter)
        }
        super.visitParameter(parameter)
    }

    override fun visitProperty(property: KtProperty) {
        if (scanning) {
            check(property.name, property.initializer, property)
        }
        super.visitProperty(property)
    }

    private fun check(name: String?, value: KtExpression?, element: org.jetbrains.kotlin.psi.KtElement) {
        if (name == null || !isVersionField(name)) return
        // Declaring the constant itself is the one legitimate literal.
        if (name == name.uppercase()) return
        if (value != null && isLiteral(value)) {
            report(CodeSmell(
                issue,
                Entity.from(element),
                "`$name = ${value.text}` — hard-coded version. Declare a named " +
                    "constant beside the type and reference it (wire-format.md §11).",
            ))
        }
    }

    private fun isVersionField(name: String): Boolean =
        name.contains("schemaVersion", ignoreCase = true) ||
            name.contains("minReaderVersion", ignoreCase = true) ||
            name.contains("minWriterVersion", ignoreCase = true)

    private fun isLiteral(value: KtExpression): Boolean = when (value) {
        is KtConstantExpression -> true
        is KtStringTemplateExpression -> value.entries.none { it.expression != null }
        else -> false
    }
}
