package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 009 accessibility-related fitness tests (FR-A11Y-001, FR-A11Y-005).
 *
 * Static gate substitute for the full Android Accessibility Scanner (which
 * requires `espresso-accessibility` gradle dep — plan §5 forbids new deps).
 * Catches the most common regression: an Icon without a contentDescription
 * (TalkBack reads "image" or skips entirely).
 *
 * Scope: scanned over `ui/admin/`, `ui/contacts/`, `ui/health/` — the
 * spec-009 screens. Existing pre-spec-009 surfaces would also benefit but
 * are intentionally not in scope here (not regression we own).
 *
 * Heuristic: every `Icon(` invocation must have either `contentDescription
 * = null` (informational icon — TalkBack skips, screen-reader semantics
 * inherited from parent) OR `contentDescription = "..."` (explicit).
 * Catches the *implicit-default* bug where someone forgot the param
 * entirely (Compose default has changed across versions and behaves
 * subtly differently).
 */
class Spec009AccessibilityTest {

    @Test
    fun icons_have_explicit_contentDescription() {
        val dirs = listOf(
            "ui/admin",
            "ui/contacts",
            "ui/health",
            "ui/components/TileCard.kt",  // FR-046b — spec 9 modifies; scan it too.
        )
        val root = locateCommonMain().resolve("kotlin/com/launcher")
        val violations = mutableListOf<String>()
        for (rel in dirs) {
            val target = root.resolve(rel)
            if (!target.exists()) continue
            target.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    // Match Icon( ... ) blocks. Crude but enough — flag if
                    // an Icon call doesn't mention contentDescription at
                    // all within the surrounding 200 chars.
                    // Match `Icon(` only — NOT `IconButton(` (which is a
                    // surface affordance with its own onClick semantics).
                    // `\bIcon\(` — word-boundary anchor stops at letters
                    // following Icon (so IconButton excluded).
                    val iconCalls = Regex("\\bIcon\\(").findAll(text)
                    for (call in iconCalls) {
                        // 500 char window to tolerate multi-line Icon(...)
                        // calls with comments / heavily-indented params.
                        val end = (call.range.last + 500).coerceAtMost(text.length - 1)
                        val window = text.substring(call.range.first, end)
                        if (!window.contains("contentDescription")) {
                            val lineNum = text.substring(0, call.range.first).count { it == '\n' } + 1
                            violations.add("${file.path}:${lineNum} Icon(...) without contentDescription")
                        }
                    }
                }
        }
        val joined = violations.joinToString("\n")
        assertTrue(
            "Spec 009 surfaces must set contentDescription on every Icon(...) " +
                "(FR-A11Y-001/005). Violations:\n" + joined,
            violations.isEmpty(),
        )
    }

    private fun locateCommonMain(): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/commonMain"),
            File(cwd, "core/src/commonMain"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("commonMain not found from cwd=$cwd among: ${candidates.map { it.path }}")
    }
}
