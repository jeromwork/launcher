package com.launcher.app.fitness

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withImport
import org.junit.Test

/**
 * Spec 016 (F-CRYPTO) FR-018 + SC-011 — fitness function: no Fake* crypto adapter
 * imports anywhere inside the production `app/src/main/` source tree.
 *
 * This is the compile-time guard that Detekt would otherwise provide. Konsist
 * traverses Kotlin AST without requiring a custom Detekt plugin, so it's the
 * lower-overhead path for this single rule. Runtime guard
 * ([com.launcher.app.di.assertNoFakeCryptoInRelease]) and R8 strip
 * (`-assumenosideeffects family.crypto.fake.**`) provide defense-in-depth.
 *
 * If this test fails: someone wired (or imported) a `family.crypto.fake.*` adapter
 * into a production-shipping source file. Move that usage to a test source set.
 */
class NoFakeCryptoInAppTest {

    @Test
    fun productionAppCodeMustNotImportFakeCryptoAdapters() {
        val violations = Konsist
            .scopeFromProduction("app")
            .files
            .withImport { it.name.startsWith("cryptokit.crypto.fake") }
            .map { it.path }
        check(violations.isEmpty()) {
            "Spec 016 SC-011 violated — production app files import cryptokit.crypto.fake.*:\n" +
                violations.joinToString("\n  - ", prefix = "  - ")
        }
    }
}
