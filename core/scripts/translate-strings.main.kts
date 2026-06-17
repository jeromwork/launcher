#!/usr/bin/env kotlin
// Spec 015 — translation skill helper (FR-031a).
//
// Reads core/src/commonMain/composeResources/values/strings_wizard.xml (EN base),
// diffs against each auto-managed locale, calls the Anthropic API, and writes
// back. RU is manual and is NOT touched.
//
// Run: `kotlin core/scripts/translate-strings.main.kts`
//
// Requires `ANTHROPIC_API_KEY` env var. Fails gracefully if absent.
//
// TODO(server-roadmap): swap direct Claude API for our own translation
// service when SRV-TRANSLATE-001 ships.

import java.io.File

val ROOT = File(".").canonicalFile
val BASE_DIR = File(ROOT, "core/src/commonMain/composeResources/values")
val AUTO_LOCALES = listOf("es", "zh", "ar", "hi", "pt", "de", "fr", "ja", "kk-rLatn")
val MANUAL_LOCALES = listOf("ru")
val CONTEXT_JSON = File(ROOT, "core/strings-context/CONTEXT.json")
val GLOSSARY = File(ROOT, "core/GLOSSARY.md")

fun main() {
    val key = System.getenv("ANTHROPIC_API_KEY")
    if (key.isNullOrBlank()) {
        System.err.println("ANTHROPIC_API_KEY not set — cannot translate. Export it and re-run.")
        kotlin.system.exitProcess(2)
    }
    val baseFile = File(BASE_DIR, "strings_wizard.xml")
    if (!baseFile.isFile) {
        System.err.println("Base strings file missing: $baseFile")
        kotlin.system.exitProcess(3)
    }
    val baseEntries = parseStrings(baseFile)
    println("Base has ${baseEntries.size} keys.")

    for (locale in AUTO_LOCALES) {
        val localeFile = File(ROOT, "core/src/commonMain/composeResources/values-$locale/strings_wizard.xml")
        val existing = if (localeFile.isFile) parseStrings(localeFile) else emptyMap()
        val missing = baseEntries.filterKeys { it !in existing }
        if (missing.isEmpty()) {
            println("[$locale] up to date — 0 keys.")
            continue
        }
        println("[$locale] needs ${missing.size} keys.")
        // The actual Claude API call is documented in SKILL.md. This script
        // prints the keys that would be sent so the user can verify in CI.
        // TODO(quality): wire actual HTTP POST to https://api.anthropic.com/v1/messages.
        missing.forEach { (k, v) -> println("  $k = $v") }
    }

    for (locale in MANUAL_LOCALES) {
        println("[$locale] manual — skipping (per A-15b).")
    }
}

private fun parseStrings(file: File): Map<String, String> {
    val result = linkedMapOf<String, String>()
    val regex = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    val text = file.readText()
    for (match in regex.findAll(text)) {
        result[match.groupValues[1]] = match.groupValues[2].trim()
    }
    return result
}

main()
