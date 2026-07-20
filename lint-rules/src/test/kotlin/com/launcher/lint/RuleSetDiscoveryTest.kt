package com.launcher.lint

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import org.junit.Test
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TASK-140 — guards the wiring, not the rules.
 *
 * Detekt finds custom rules through `ServiceLoader`, which reads
 * `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`. When that
 * file is missing (the state from TASK-65 until 2026-07-20) every detector still
 * compiles and still passes its own unit tests while checking nothing at all.
 * No test caught it because every test instantiated its rule directly.
 *
 * These tests exercise the discovery path itself.
 */
class RuleSetDiscoveryTest {

    @Test
    fun serviceLoaderFindsOurProvider() {
        val providers = ServiceLoader
            .load(RuleSetProvider::class.java, javaClass.classLoader)
            .toList()

        assertTrue(
            providers.any { it is LauncherRuleSetProvider },
            "ServiceLoader did not find LauncherRuleSetProvider. Detekt discovers " +
                "rules the same way, so it would silently run none of them. " +
                "Check META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider. " +
                "Found: ${providers.map { it.ruleSetId }}",
        )
    }

    @Test
    fun ruleSetIdMatchesTheConfigKey() {
        assertEquals(
            "launcher",
            LauncherRuleSetProvider().ruleSetId,
            "config/detekt.yml keys its rule section on this id; a mismatch makes " +
                "Detekt report \"Property 'launcher' is misspelled or does not exist\".",
        )
    }

    @Test
    fun everyDetectorIsRegistered() {
        val registered = LauncherRuleSetProvider()
            .instance(Config.empty)
            .rules
            .map { it.ruleId }
            .toSet()

        // These are the exact keys `config/detekt.yml` must use under `launcher:`.
        // Detekt resolves per-rule config through `Rule.ruleId`, which for these
        // rules is the `issue.id` — NOT the class name. Key the config on the class
        // name and the rule is silently inactive, with no error anywhere.
        val expected = setOf(
            "PresetIdBranching",
            "ExtractionReadiness",
            "FF011LegacyWizardImport",
            "WireFormatHygiene",
        )

        assertEquals(
            expected,
            registered,
            "A detector missing from the provider never runs; a config key that " +
                "does not match a ruleId leaves that rule inactive. Keep " +
                "LauncherRuleSetProvider and config/detekt.yml in step with this set.",
        )
    }

    @Test
    fun ruleIdIsTheIssueIdNotTheClassName() {
        val rule = WireFormatHygieneDetector()
        assertEquals("WireFormatHygiene", rule.ruleId)
        assertEquals(rule.issue.id, rule.ruleId, "config keys follow issue.id")
    }
}
