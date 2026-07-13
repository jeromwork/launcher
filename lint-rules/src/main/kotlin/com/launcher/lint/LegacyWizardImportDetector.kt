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
 * FF-011 (FR-015, NFR-003) — refuses imports from the legacy wizard/preset
 * API packages that are being deleted in TASK-126.
 *
 * Banned prefixes:
 *  - `com.launcher.api.wizard.*` (legacy WizardEngine and related types)
 *  - `com.launcher.api.preset.*` (per CL-4 — also being retired)
 *
 * The new preset composition namespace `com.launcher.preset.*` is intentionally
 * NOT flagged (distinct namespace, no leading `.api.` segment).
 *
 * The detector runs against production source sets under `core/` and `app/`
 * (wired by the root `build.gradle.kts`). Spec text, archived docs, `.claude/`,
 * and `specs/` are outside the detekt input source set and therefore not
 * scanned by default — no additional whitelist required.
 *
 * Files that own the legacy packages themselves are whitelisted so that the
 * `package com.launcher.api.wizard` declarations do not trigger during the
 * Phase 6a → Phase 6b window (Phase 6b physically deletes the packages).
 */
class LegacyWizardImportDetector(config: Config = Config.empty) : Rule(config) {

    override val issue: Issue = Issue(
        id = "FF011LegacyWizardImport",
        severity = Severity.Defect,
        description = "Legacy wizard/preset API import — this package is being " +
            "deleted in TASK-126. Use com.launcher.preset.* instead. " +
            "See specs/task-126-wizard-runtime-migration/spec.md.",
        debt = Debt.TWENTY_MINS,
    )

    private val bannedPrefixes = listOf(
        "com.launcher.api.wizard",
        "com.launcher.api.preset",
    )

    // Files declaring the legacy packages themselves must not self-report.
    private val ownerPackagePaths = listOf(
        "com/launcher/api/wizard/",
        "com/launcher/api/preset/",
    )

    private var scanning = true

    override fun visitKtFile(file: KtFile) {
        val path = file.virtualFilePath.replace('\\', '/')
        scanning = ownerPackagePaths.none { path.contains(it) }
        super.visitKtFile(file)
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        if (scanning) {
            val fqName = importDirective.importedFqName?.asString().orEmpty()
            val hit = bannedPrefixes.firstOrNull { prefix ->
                fqName == prefix || fqName.startsWith("$prefix.")
            }
            if (hit != null) {
                report(
                    CodeSmell(
                        issue,
                        Entity.from(importDirective),
                        "Legacy import '$fqName' — package is being deleted in " +
                            "TASK-126. Use com.launcher.preset.* instead. " +
                            "See specs/task-126-wizard-runtime-migration/spec.md.",
                    ),
                )
            }
        }
        super.visitImportDirective(importDirective)
    }
}
