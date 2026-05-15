package com.launcher.test.fitness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 009 isolation fitness tests (Phase 13 T100..T104).
 *
 * Five Konsist gates protecting boundaries introduced by spec 009.
 * Plain file-system import scan (pattern from [Spec008IsolationTest]) —
 * resilient to gradle cwd variations across androidUnitTest runners.
 *
 *  - **T100**: `commonMain/api/contacts/` MUST NOT import `android.*` /
 *    `androidx.*` (plan.md §6 gate 1).
 *
 *  - **T101**: `commonMain/api/history/` MUST NOT import
 *    `com.google.firebase.*` (plan.md §6 gate 2).
 *
 *  - **T102**: `commonMain/api/apps/` MUST NOT import
 *    `android.content.pm.*` (plan.md §6 gate 3).
 *
 *  - **T103**: `Contact.fromRaw` return type MUST be `Outcome<Contact,
 *    ValidationError>` — no exceptions thrown for validation
 *    (plan.md §6 gate 4).
 *
 *  - **T104**: `ui/components/TileCard` MUST vary icon by `SlotKind` —
 *    no single hardcoded `Icons.Filled.Call` (plan.md §6 gate 5;
 *    spec 9 FR-046 fixes existing bug).
 */
class Spec009IsolationTest {

    @Test
    fun T100_api_contacts_does_not_import_android_or_androidx() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/contacts")
        if (!dir.isDirectory) return  // package not yet created — pass (defensive).
        val forbidden = listOf("android.", "androidx.")
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "api/contacts/ must NOT import android.* or androidx.*.\n" +
                "Violations:\n${'$'}{violations.joinToString(\"\\n\")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun T101_api_history_does_not_import_firebase() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/history")
        if (!dir.isDirectory) return
        val forbidden = listOf("com.google.firebase.")
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "api/history/ must NOT import com.google.firebase.*.\n" +
                "Violations:\n${'$'}{violations.joinToString(\"\\n\")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun T102_api_apps_does_not_import_packagemanager() {
        val dir = locateCommonMain().resolve("kotlin/com/launcher/api/apps")
        if (!dir.isDirectory) return
        val forbidden = listOf(
            "android.content.pm.",
            "android.graphics.",
            "android.net.Uri",
        )
        val violations = scanImports(dir, forbidden)
        assertTrue(
            "api/apps/ must NOT import android.content.pm.* / android.graphics.*.\n" +
                "Violations:\n${'$'}{violations.joinToString(\"\\n\")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun T103_contact_fromRaw_returns_outcome_not_throws() {
        val file = locateCommonMain().resolve("kotlin/com/launcher/api/config/Contact.kt")
        assertTrue("Contact.kt must exist", file.isFile)
        val text = file.readText()
        // The factory must declare Outcome<Contact, ValidationError> as
        // its return type AND must not contain `throw` inside its body
        // for validation failures. Soft check: search the file for both
        // a `fun fromRaw(...): Outcome<Contact, ValidationError>` signature
        // и отсутствие `throw IllegalArgumentException` непосредственно
        // в обработке имени/телефона.
        val hasOutcomeReturn = text.contains("Outcome<Contact, ValidationError>")
        assertTrue(
            "Contact.fromRaw must return Outcome<Contact, ValidationError>; " +
                "found neither in Contact.kt.",
            hasOutcomeReturn,
        )
        // Soft anti-throw check: validation should not use throw.
        val throwsInValidation = Regex(
            "fun fromRaw\\b[\\s\\S]*?throw ",
        ).containsMatchIn(text)
        assertTrue(
            "Contact.fromRaw body must not throw for validation; " +
                "use Outcome.Failure(ValidationError.*) instead.",
            !throwsInValidation,
        )
    }

    @Test
    fun T104_tile_card_varies_icon_by_slot_kind() {
        val file = locateCommonMain().resolve("kotlin/com/launcher/ui/components/TileCard.kt")
        assertTrue("TileCard.kt must exist", file.isFile)
        val text = file.readText()
        // The fix is: an icon-resolver function that takes SlotKind
        // (or a `when (slotKind)` arm) so the icon varies. The bug
        // pattern is a single hardcoded `imageVector = Icons.Filled.Call`
        // without any kind-based switch.
        val hasKindParameter = text.contains("slotKind: SlotKind?") ||
            text.contains("slotKind: SlotKind ")
        assertTrue(
            "TileCard must accept slotKind: SlotKind parameter (FR-046 fix).",
            hasKindParameter,
        )
        val hasIconResolver = text.contains("iconForSlotKind") ||
            text.contains("when (slotKind)") ||
            text.contains("when (slot.kind)")
        assertTrue(
            "TileCard must vary icon by SlotKind (FR-046 fix); " +
                "found no iconForSlotKind / when(slotKind) in TileCard.kt.",
            hasIconResolver,
        )
    }

    // ─── helpers (copied from Spec008IsolationTest pattern) ───────────────

    private fun scanImports(
        root: File,
        forbiddenPrefixes: List<String>,
    ): List<String> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").removeSuffix(";").trim() }
                    .filter { importName ->
                        forbiddenPrefixes.any { importName.startsWith(it) }
                    }
                    .map { "${'$'}{file.path}: import ${'$'}it" }
            }
            .toList()

    private fun locateCommonMain(): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "src/commonMain"),
            File(cwd, "core/src/commonMain"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("commonMain not found from cwd=${'$'}cwd among: ${'$'}{candidates.map { it.path }}")
    }
}
