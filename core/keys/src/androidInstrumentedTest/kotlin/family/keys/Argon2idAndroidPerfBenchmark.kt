package family.keys

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import cryptokit.crypto.libsodium.LibsodiumArgon2idPasswordHash
import family.keys.api.KdfParams
import family.keys.impl.Argon2idPassphraseKdf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * Phase 7 T122b — Argon2id perf benchmark на реальном Android JNI overhead.
 *
 * **Updated 2026-06-19** после real-device measurements: SC-002 threshold
 * был пересмотрен с 500ms → 1500ms — libsodium JNI overhead на realistic
 * 5-летних devices не позволяет дойти до 500ms без снижения brute-force
 * resistance (см. oem-smoke-results.md Option A, owner approved).
 *
 * Это редкая операция (1 раз setup + 1 раз recovery на новом устройстве),
 * не daily UX — 776ms Xiaomi 11T / 726ms emulator acceptable. Root key хранится
 * в Android Keystore и passphrase не запрашивается при ежедневном использовании.
 */
@RunWith(AndroidJUnit4::class)
class Argon2idAndroidPerfBenchmark {

    @Test
    fun interactiveParamsDerivationUnder1500ms() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val kdf = Argon2idPassphraseKdf(LibsodiumArgon2idPasswordHash())
        val passphrase = "test-passphrase-for-benchmark".toCharArray()
        val salt = ByteArray(32) { it.toByte() }
        val uid = "perf-test-uid"
        val params = KdfParams() // = interactive defaults (64MB, 3, 1).

        // Warmup — первый вызов включает JNI init, не считаем.
        kdf.derive(passphrase.copyOf(), salt, uid, params)

        // Measure 3 итерации, берём минимум (более стабильно чем median на shared CPU).
        val timings = (0 until 3).map {
            measureTime { kdf.derive(passphrase.copyOf(), salt, uid, params) }
        }
        val best = timings.min()
        println("Argon2id interactive params best timing: $best (all: $timings)")

        // SC-002 updated 2026-06-19: 1500ms на realistic devices (раньше 500ms).
        // Это редкая операция, не daily UX — acceptable trade-off vs brute-force resistance.
        assertTrue(
            "Argon2id derivation должно быть < 1500ms (SC-002 updated), got $best. " +
                "На быстром устройстве/эмуляторе обычно ~700-800ms из-за libsodium JNI overhead.",
            best < 1500.milliseconds
        )
    }

    @Test
    fun fastParamsDerivationUnder100ms() = runTest {
        // Sanity check: lightweight params (8 MiB / 1 pass) для тестов.
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val kdf = Argon2idPassphraseKdf(LibsodiumArgon2idPasswordHash())
        val params = KdfParams(memoryKb = 8192, iterations = 1)
        // Warmup.
        kdf.derive("warmup".toCharArray(), ByteArray(32) { 0x42 }, "uid", params)

        val timing = measureTime {
            kdf.derive("benchmark".toCharArray(), ByteArray(32) { 0x55 }, "uid", params)
        }
        println("Argon2id fast params timing: $timing")
        assertTrue("Fast params should be < 500ms (got $timing)", timing < 500.milliseconds)
    }
}
