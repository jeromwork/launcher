package com.launcher.lint

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Registers our architecture fitness rules with Detekt.
 *
 * Without this provider (plus the `META-INF/services` entry that points at it)
 * Detekt never discovers the detectors — they compile and pass their own unit
 * tests while checking nothing in the codebase. That was the state from TASK-65
 * until TASK-16 (2026-07-20).
 *
 * The ruleset id `launcher` is what `config/detekt.yml` keys off.
 */
class LauncherRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "launcher"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            PresetIdBranchingDetector(config),
            ExtractionReadinessDetector(config),
            LegacyWizardImportDetector(config),
            WireFormatHygieneDetector(config),
        ),
    )
}
