package com.launcher.lint

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * FR-021 — keeps foundation packages (core/api/preset, profile, pools,
 * switchstrategy, wizard) extractable into a standalone module by refusing
 * imports from the app layer.
 */
class ExtractionReadinessDetector(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = "ExtractionReadiness",
        severity = Severity.Defect,
        description = "Foundation packages must not depend on app-layer code. " +
            "Move the shared abstraction down or invert the dependency.",
        debt = Debt.TWENTY_MINS,
    )

    private val scope = listOf(
        "com/launcher/api/preset/",
        "com/launcher/api/profile/",
        "com/launcher/api/pools/",
        "com/launcher/api/switchstrategy/",
        "com/launcher/api/wizard/",
    )

    private val forbiddenPrefixes = listOf(
        "com.launcher.app.tiles",
        "com.launcher.app.home",
        "com.launcher.app.contacts",
    )

    private var active = false

    override fun visitKtFile(file: KtFile) {
        val path = file.virtualFilePath.replace('\\', '/')
        active = scope.any { path.contains(it) }
        super.visitKtFile(file)
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        if (active) {
            val fqName = importDirective.importedFqName?.asString().orEmpty()
            if (forbiddenPrefixes.any { fqName.startsWith(it) }) {
                report(CodeSmell(
                    issue,
                    Entity.from(importDirective),
                    "Foundation package imports forbidden app-layer code: $fqName",
                ))
            }
        }
        super.visitImportDirective(importDirective)
    }
}
