package family.keys.perf

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.api.SealedConfig
import family.keys.impl.AeadConfigCipherImpl
import family.keys.impl.KeyHierarchy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase 7 perf benchmark (T128, SC-002).
 *
 * Goal: 10 KB ConfigDocument seal + open в сумме укладываются в < 50 ms на
 * baseline dev JVM (Hotspot, no JNI inflicted slowdown).
 *
 * JVM-only — Android JNI / libsodium native overhead покрывается
 * `Argon2idAndroidPerfBenchmark` в androidInstrumentedTest (T122b).
 *
 * Метод измерения:
 *  1. Warmup: 50 seal+open циклов (JIT и libsodium prime).
 *  2. Measurement: 200 seal+open циклов; берём median latency, чтобы исключить
 *     outlier'ы из GC и context switch'ей.
 *  3. Assert median < 50 ms.
 */
class ConfigCipherBenchmark {

    private val uid = "bench-uid"
    private val identity = AuthIdentity(stableId = uid, displayName = null, email = null)

    private suspend fun makeCipher(): AeadConfigCipherImpl {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val keystore = SecureKeyStore(KeyStoreContext())
        val aead = LibsodiumAeadCipher()
        val random = LibsodiumRandomSource()
        val hierarchy = KeyHierarchy(uid, keystore, aead, random)
        assertIs<Outcome.Success<*>>(hierarchy.bootstrap(identity))
        return AeadConfigCipherImpl(aead, hierarchy.keyRegistry)
    }

    @Test
    fun tenKbSealOpenMedianUnder50ms() = runTest {
        val cipher = makeCipher()
        val payload = ByteArray(10 * 1024) { ((it * 31) % 251).toByte() }

        // Warmup — 50 циклов.
        repeat(WARMUP_ITERATIONS) {
            val sealed = (cipher.seal(payload, uid) as Outcome.Success<SealedConfig>).value
            val opened = (cipher.open(sealed, uid) as Outcome.Success<ByteArray>).value
            assertContentEquals(payload, opened)
        }

        // Measurement — 200 циклов.
        val latenciesNanos = LongArray(MEASUREMENT_ITERATIONS)
        for (i in 0 until MEASUREMENT_ITERATIONS) {
            val start = System.nanoTime()
            val sealed = (cipher.seal(payload, uid) as Outcome.Success<SealedConfig>).value
            val opened = (cipher.open(sealed, uid) as Outcome.Success<ByteArray>).value
            val end = System.nanoTime()
            latenciesNanos[i] = end - start
            assertContentEquals(payload, opened)
        }
        latenciesNanos.sort()
        val medianNanos = latenciesNanos[MEASUREMENT_ITERATIONS / 2]
        val medianMillis = medianNanos / 1_000_000.0
        val p95Nanos = latenciesNanos[(MEASUREMENT_ITERATIONS * 95) / 100]
        val p95Millis = p95Nanos / 1_000_000.0

        println(
            "ConfigCipher 10 KB seal+open — median ${"%.2f".format(medianMillis)} ms, " +
                "p95 ${"%.2f".format(p95Millis)} ms (n=$MEASUREMENT_ITERATIONS)"
        )
        assertTrue(
            medianMillis < SC_002_THRESHOLD_MS,
            "SC-002: median 10 KB seal+open MUST be < $SC_002_THRESHOLD_MS ms; got ${"%.2f".format(medianMillis)} ms"
        )
    }

    companion object {
        private const val WARMUP_ITERATIONS = 50
        private const val MEASUREMENT_ITERATIONS = 200

        /** Spec 018 SC-002 — 10 KB config: < 50 ms. */
        private const val SC_002_THRESHOLD_MS = 50.0
    }
}
