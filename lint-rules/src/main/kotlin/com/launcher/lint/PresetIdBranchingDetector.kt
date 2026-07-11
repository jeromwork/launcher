package com.launcher.lint

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * FR-020 — refuses equality / when branching on the preset identity field
 * (presetId) outside the whitelisted packages.
 *
 * Whitelist: source files under com/launcher/api/preset/ (identity owners)
 * or com/launcher/architecture/ (fitness tests that legitimately reference
 * the patterns).
 */
class PresetIdBranchingDetector(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = "PresetIdBranching",
        severity = Severity.Defect,
        description = "Do not branch on presetId outside the preset identity " +
            "layer. Use polymorphism or config-driven dispatch.",
        debt = Debt.TWENTY_MINS,
    )

    private val triggerNames = setOf("presetId")

    private val whitelist = listOf(
        "com/launcher/api/preset/",
        "com/launcher/architecture/",
    )

    private var scanning = true

    override fun visitKtFile(file: KtFile) {
        val path = file.virtualFilePath.replace('\\', '/')
        scanning = whitelist.none { path.contains(it) }
        if (scanning) super.visitKtFile(file)
    }

    override fun visitIfExpression(expression: KtIfExpression) {
        if (scanning) {
            val condText = expression.condition?.text.orEmpty()
            if (triggerNames.any { name ->
                    condText.contains("$name ==") || condText.contains("$name==")
                }) {
                report(CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Equality branch on identity field: $condText",
                ))
            }
        }
        super.visitIfExpression(expression)
    }

    override fun visitWhenExpression(expression: KtWhenExpression) {
        if (scanning) {
            val subj = expression.subjectExpression?.text.orEmpty().trim()
            if (subj in triggerNames) {
                report(CodeSmell(
                    issue,
                    Entity.from(expression),
                    "when($subj) — switch over identity field is forbidden",
                ))
            }
        }
        super.visitWhenExpression(expression)
    }
}
