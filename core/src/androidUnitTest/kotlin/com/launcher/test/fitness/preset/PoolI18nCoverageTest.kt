package com.launcher.test.fitness.preset

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * TASK-129 (FR-001..FR-007) — every text key declared in bundled `pool.json` has a
 * string in EN and RU.
 *
 * Why this gate exists: pool keys come from JSON, so the compiler never sees them.
 * The normal Android path (`R.string.foo`) fails the build on a typo; our
 * `AndroidLocalizedResources` resolves names at runtime via `getIdentifier` and
 * falls back to echoing the key. A missing string is therefore invisible until a
 * user reaches that component and reads "pool.foo.description" off the screen.
 * Sibling fitness test `WireFormatI18nKeysTest` checks key *shape*; this one checks
 * key *coverage*.
 *
 * Scope (FR-003): text-bearing fields only. `iconKey` and `layoutKey` are
 * identifiers, not prose — `iconKey` is passed to `iconRef` without going through
 * `LocalizedResources` at all (see spec.md § "Замеченное, но не исправленное здесь").
 */
class PoolI18nCoverageTest {

    /** Fields whose values are shown to a human and therefore need translating. */
    private val textKeyFields = setOf("labelKey", "titleKey", "descriptionKey")

    private val json = Json { ignoreUnknownKeys = true }

    // <string name="pool_font_description">Font size</string>
    private val stringEntry = Regex("""<string\s+name="([^"]+)"\s*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun everyPoolTextKey_hasEnAndRuString() {
        val poolJson = locate("app/src/main/assets/preset/pool.json")
            ?: return fail("FR-005: pool.json not found — the gate cannot verify anything, which is a defect, not a pass.")

        val declaredKeys = collectTextKeys(json.parseToJsonElement(poolJson.readText()))
        assertTrue(
            "FR-005: no text keys found in ${poolJson.path} — the gate would pass vacuously.",
            declaredKeys.isNotEmpty(),
        )

        val missing = mutableListOf<String>()
        for (locale in listOf(Locale("EN", "app/src/main/res/values"), Locale("RU", "app/src/main/res/values-ru"))) {
            val strings = readStrings(locale.resDir)
                ?: return fail("FR-005: ${locale.tag} resources not found at ${locale.resDir}.")

            for (key in declaredKeys) {
                // Must mirror AndroidLocalizedResources.resolve(): dots -> underscores (FR-002).
                val resourceName = key.replace('.', '_')
                val value = strings[resourceName]
                when {
                    value == null -> missing += "${locale.tag}: $key -> <string name=\"$resourceName\"> is missing"
                    // FR-004: an empty string resolves to nothing on screen — same defect as absent.
                    value.isBlank() -> missing += "${locale.tag}: $key -> <string name=\"$resourceName\"> is empty"
                }
            }
        }

        assertTrue(
            "TASK-129: every text key declared in pool.json needs an EN and a RU string.\n" +
                "RU is not optional — it is the product's primary market.\n" +
                "Add the missing entries to app/src/main/res/values{,-ru}/strings_preset_task120.xml.\n" +
                missing.joinToString("\n"),
            missing.isEmpty(),
        )
    }

    /**
     * TASK-69 T069-027 (SC-007) — every `settingsMap[].categoryKey` declared in the
     * bundled presets has an EN + RU string. Same coverage gate as
     * [everyPoolTextKey_hasEnAndRuString], extended per plan.md §7 to the new
     * Settings-screen consumer of preset JSON (`categoryKey` lives on the
     * *preset*, not on `pool.json`, so it needs its own key collection).
     */
    @Test
    fun everyBundledPresetCategoryKey_hasEnAndRuString() {
        val presetsDir = locate("app/src/main/assets/preset/bundled-presets")
            ?: return fail("T069-027: bundled-presets dir not found — the gate cannot verify anything.")
        val presetFiles = presetsDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?: return fail("T069-027: bundled-presets dir has no JSON files.")
        assertTrue("T069-027: no bundled preset JSON files found.", presetFiles.isNotEmpty())

        val declaredKeys = presetFiles
            .flatMap { collectCategoryKeys(json.parseToJsonElement(it.readText())) }
            .toSet()
        assertTrue(
            "T069-027: no categoryKey found in bundled presets — the gate would pass vacuously.",
            declaredKeys.isNotEmpty(),
        )

        val missing = mutableListOf<String>()
        for (locale in listOf(Locale("EN", "app/src/main/res/values"), Locale("RU", "app/src/main/res/values-ru"))) {
            val strings = readStrings(locale.resDir)
                ?: return fail("T069-027: ${locale.tag} resources not found at ${locale.resDir}.")
            for (key in declaredKeys) {
                val resourceName = key.replace('.', '_')
                val value = strings[resourceName]
                when {
                    value == null -> missing += "${locale.tag}: $key -> <string name=\"$resourceName\"> is missing"
                    value.isBlank() -> missing += "${locale.tag}: $key -> <string name=\"$resourceName\"> is empty"
                }
            }
        }

        assertTrue(
            "TASK-69: every settingsMap categoryKey declared in a bundled preset needs an EN and a RU string.\n" +
                "Add the missing entries to app/src/main/res/values{,-ru}/strings_preset_task120.xml.\n" +
                missing.joinToString("\n"),
            missing.isEmpty(),
        )
    }

    private fun collectCategoryKeys(element: JsonElement): Set<String> = buildSet {
        if (element !is JsonObject) return@buildSet
        val settingsMap = element["settingsMap"] as? JsonArray ?: return@buildSet
        settingsMap.forEach { entry ->
            val categoryKey = (entry as? JsonObject)?.get("categoryKey") as? JsonPrimitive
            if (categoryKey != null && categoryKey.isString) add(categoryKey.content)
        }
    }

    /**
     * FR-003 / SC-004 — pins the decision that `iconKey` and `layoutKey` are identifiers.
     * Without this, adding them to [textKeyFields] looks like a tightening of the gate
     * but would demand a "translation" of `whatsapp` into every locale. Icons resolve
     * through the `IconStorage` port (`bundled:whatsapp` -> drawable), never through
     * `LocalizedResources`.
     */
    @Test
    fun iconAndLayoutKeys_areNotTreatedAsTranslatableText() {
        val poolJson = locate("app/src/main/assets/preset/pool.json")
            ?: return fail("FR-005: pool.json not found.")
        val text = poolJson.readText()
        assertTrue(
            "Fixture drift: pool.json no longer declares an iconKey — this test guards nothing.",
            text.contains("\"iconKey\""),
        )
        assertTrue(
            "Fixture drift: pool.json no longer declares a layoutKey — this test guards nothing.",
            text.contains("\"layoutKey\""),
        )

        val declared = collectTextKeys(json.parseToJsonElement(text))
        val leaked = declared.filter { it.endsWith(".icon") || it.startsWith("layout.") }
        assertTrue(
            "FR-003: iconKey / layoutKey are identifiers, not prose — they must not require translation.\n" +
                "Leaked into the text set: $leaked",
            leaked.isEmpty(),
        )
    }

    private data class Locale(val tag: String, val resDir: String)

    /** Walks the whole tree: `descriptionKey` sits on the blueprint, `labelKey` / `titleKey` inside `component`. */
    private fun collectTextKeys(element: JsonElement): Set<String> = buildSet {
        when (element) {
            is JsonObject -> element.forEach { (name, value) ->
                if (name in textKeyFields && value is JsonPrimitive && value.isString) {
                    add(value.content)
                } else {
                    addAll(collectTextKeys(value))
                }
            }
            is JsonArray -> element.forEach { addAll(collectTextKeys(it)) }
            else -> Unit
        }
    }

    /** name -> value across every strings*.xml in the locale dir. */
    private fun readStrings(relativeDir: String): Map<String, String>? {
        val dir = locate(relativeDir) ?: return null
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.extension == "xml" }
            ?: return null
        if (files.isEmpty()) return null
        return buildMap {
            for (file in files) {
                stringEntry.findAll(file.readText()).forEach { m ->
                    put(m.groupValues[1], m.groupValues[2].trim())
                }
            }
        }
    }

    /** Test cwd is the module dir under Gradle, but not under every IDE runner — try both. */
    private fun locate(relativePath: String): File? {
        val cwd = File(System.getProperty("user.dir") ?: return null)
        return listOf(File(cwd, "../$relativePath"), File(cwd, relativePath))
            .firstOrNull { it.exists() }
    }
}
