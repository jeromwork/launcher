package family.crypto.ports

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fitness rule (TASK-123, FR-005, SC-003): no file in package `family.crypto.ports` may import a
 * vendor / platform / transport SDK — the domain ports stay pure (rule 1 isolation). Template:
 * `NoLegacyFamilyNamespaceTest` (`:core`).
 *
 * Lives in `jvmTest` because it walks the source tree with `java.io.File` (JVM-only); it still
 * guards `commonMain` sources, which is what matters. Asserts BOTH directions:
 *  - the real `family.crypto.ports` tree is clean ([portsPackage_hasNoForbiddenImports]);
 *  - the scanner actually catches a planted forbidden import ([scanner_flagsPlantedImport]) — so a
 *    green result means "verified clean", never "scanner silently matched nothing".
 */
class PortsNoVendorImportTest {

    @Test
    fun portsPackage_hasNoForbiddenImports() {
        val dir = portsDir()
        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { idx, raw ->
                        forbiddenImport(raw)?.let { violations += "${file.name}:${idx + 1}: ${raw.trim()}" }
                    }
                }
            }
        assertTrue(
            violations.isEmpty(),
            "`family.crypto.ports` must not import vendor/platform SDKs (FR-005).\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun scanner_flagsPlantedImport() {
        val planted = listOf(
            "package family.crypto.ports",
            "import openmls.CoreCrypto",
            "import uniffi.crypto.Group",
            "import android.content.Context",
            "import okhttp3.OkHttpClient",
            "import com.google.firebase.Firebase",
            "import cryptokit.crypto.LegacyThing",
        )
        val flagged = planted.filter { forbiddenImport(it) != null }
        assertTrue(
            flagged.size == 6,
            "scanner must catch every planted forbidden import; caught: $flagged",
        )
    }

    private fun forbiddenImport(raw: String): String? {
        val line = raw.trim()
        if (!line.startsWith("import ")) return null
        val target = line.removePrefix("import ").removeSuffix(";").trim()
        return FORBIDDEN.firstOrNull { target.startsWith(it) }
    }

    private fun portsDir(): File {
        val root = locateRepoRoot()
        return File(root, "core/crypto/src/commonMain/kotlin/family/crypto/ports").also {
            assertTrue(it.isDirectory, "ports source dir not found at ${it.path}")
        }
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile ?: return@repeat
        }
        return File(System.getProperty("user.dir"))
    }

    private companion object {
        val FORBIDDEN = listOf(
            "openmls",
            "uniffi",
            "cryptokit",
            "android.",
            "okhttp",
            "com.google.firebase",
            "firebase",
        )
    }
}
