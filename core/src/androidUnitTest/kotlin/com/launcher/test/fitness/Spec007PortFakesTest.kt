package com.launcher.test.fitness

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Spec 007 T104 / CLAUDE.md §6 «Mock-first development»: every port
 * declared under `:core/commonMain/.../api/...` MUST have a `Fake<Name>`
 * class under `:core/commonMain/.../fake/...`. If a port is added without
 * a fake, `./gradlew :core:test` fails — domain code stays testable
 * without real SDKs, and the wiring contract stays honest.
 *
 * NB: avoid putting a slash followed by two stars literally in this KDoc —
 * Kotlin block comments nest, and that sequence would open a nested KDoc
 * and break compilation.
 *
 * Heuristic for "port":
 *  - top-level `interface Foo` declared in `commonMain/.../api/{identity,
 *    link, push, sync}/Foo.kt` (the four 007-introduced packages),
 *  - file name == interface name,
 *  - excludes wire-format helpers (object declarations) and contracts
 *    (interfaces in `*WireFormat.kt` / `*Contract.kt` files).
 *
 * Allow-list:
 *  - `TransactionScope` is exposed only inside `RemoteSyncBackend.runTransaction`
 *    blocks and is fully exercised by FakeRemoteSyncBackend's inner
 *    `FakeTransactionScope` — no standalone fake is needed.
 *  - `Identity` is a sealed type (not a port); `AdminIdentity` /
 *    `ManagedIdentity` are its data subtypes.
 *  - `TrustEdgeBootstrap` is an open marker for cross-feature reuse,
 *    realised by concrete domain values (e.g. `Link`); not a port.
 */
class Spec007PortFakesTest {

    /** Packages under `commonMain/.../api/` that spec 007 introduced. */
    private val portPackages = listOf("identity", "link", "push", "sync")

    /**
     * Ports we do not require a Fake for, with a justification. Adding a
     * port to this list should be a deliberate decision documented in the
     * spec.
     */
    private val allowedWithoutFake = setOf(
        "TransactionScope", // covered by FakeRemoteSyncBackend.FakeTransactionScope (inner)
    )

    @Test
    fun every_spec007_port_has_a_fake_implementation() {
        val commonMain = locateCommonMain()
        val apiRoot = File(commonMain, "kotlin/com/launcher/api")
        val fakeRoot = File(commonMain, "kotlin/com/launcher/fake")
        check(apiRoot.isDirectory) { "api root not found at ${apiRoot.path}" }
        check(fakeRoot.isDirectory) { "fake root not found at ${fakeRoot.path}" }

        val ports = portPackages.flatMap { pkg ->
            val dir = File(apiRoot, pkg)
            if (!dir.isDirectory) emptyList() else discoverPorts(dir)
        }.toSet()

        val knownFakes = fakeRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.nameWithoutExtension }
            .toSet()

        val missing = ports
            .filterNot { it in allowedWithoutFake }
            .filterNot { "Fake$it" in knownFakes }
            .sorted()

        assertEquals(
            "Spec 007 T104 / CLAUDE.md §6: every port in :core/commonMain/api/{${portPackages.joinToString()}}\n" +
                "must have a Fake<Name> class in :core/commonMain/fake/.\n" +
                "Missing fakes for: $missing.\n" +
                "Known fakes: ${knownFakes.filter { it.startsWith("Fake") }.sorted()}.",
            emptyList<String>(),
            missing,
        )
    }

    /**
     * A "port" is a top-level interface whose file name matches the
     * interface name and whose file is not a wire-format / contract helper.
     */
    private fun discoverPorts(packageDir: File): List<String> {
        return packageDir.listFiles { f -> f.isFile && f.extension == "kt" }
            ?.filterNot { it.name.endsWith("WireFormat.kt") }
            ?.filterNot { it.name.endsWith("Contract.kt") }
            ?.mapNotNull { file ->
                val name = file.nameWithoutExtension
                val text = file.readText()
                // Match `interface Foo` or `interface Foo {` / `interface Foo :`
                // — must be at top level, so look for the line containing
                // exactly `interface <name>` without a leading `sealed `.
                val needle = Regex(
                    """(?m)^(?:public\s+)?interface\s+${Regex.escape(name)}\b"""
                )
                if (needle.containsMatchIn(text)) name else null
            }
            ?: emptyList()
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
