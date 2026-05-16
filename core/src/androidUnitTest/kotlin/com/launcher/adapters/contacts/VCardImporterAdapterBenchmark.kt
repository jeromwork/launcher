package com.launcher.adapters.contacts

import com.launcher.api.contacts.RawVCard
import com.launcher.api.result.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 009 NFR-002 microbenchmark — VCard parser p95 < 100 ms on 10 KB
 * payload (plan §6, research R-008).
 *
 * This is a **JVM-side microbenchmark substitute** for the formal
 * `androidx.benchmark` microbench (which requires connected hardware and
 * is therefore deferred to physical device — see backlog TODO-PHYS-001).
 * It exercises the same parser instance, same `Dispatchers.Default`
 * coroutine, same 10 KB-cap payload, and asserts a generous p95 budget
 * (300 ms — 3× target). The real frame-budget gate is the connected
 * benchmark on Pixel 4a class; this CI-runnable test catches catastrophic
 * regressions (e.g. accidental quadratic blowup, regex backtrack).
 *
 * 100 iterations after a 10-iteration warm-up. Sorted timings; p95 read
 * at index 94.
 */
class VCardImporterAdapterBenchmark {

    private val parser = VCardImporterAdapter()

    @Test
    fun parse_10kb_payload_p95_under_300ms_jvm_substitute() = runBlocking {
        val payload = build10kbPayload()
        // 10 KB is the parser's cap; we aim for a payload just under it so
        // the cap doesn't truncate. ASCII line filler keeps byte == char.
        require(payload.size in 9_000..10_240) { "payload size out of range: ${payload.size}" }

        // Warm-up — JIT / class-init / inline-cache resolution.
        repeat(10) { parser.parse(payload) }

        val timings = LongArray(100)
        for (i in 0 until 100) {
            val start = System.nanoTime()
            val result = parser.parse(payload)
            timings[i] = System.nanoTime() - start
            check(result is Outcome.Success) { "parse failed: $result" }
        }
        timings.sort()
        val p50Ns = timings[49]
        val p95Ns = timings[94]
        val p99Ns = timings[98]
        val p50Ms = p50Ns / 1_000_000.0
        val p95Ms = p95Ns / 1_000_000.0
        val p99Ms = p99Ns / 1_000_000.0
        println("VCardImporter 10KB parse: p50=${"%.2f".format(p50Ms)}ms p95=${"%.2f".format(p95Ms)}ms p99=${"%.2f".format(p99Ms)}ms")

        assertTrue(
            "p95 must be < 300ms (3x NFR-002 target as JVM substitute budget); got ${'$'}p95Ms ms",
            p95Ms < 300.0,
        )
    }

    @Test
    fun parse_minimal_payload_p95_under_5ms() = runBlocking {
        val payload = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Маша
            TEL:+71234567890
            END:VCARD
        """.trimIndent().toByteArray(Charsets.UTF_8)

        repeat(10) { parser.parse(payload) }

        val timings = LongArray(100)
        for (i in 0 until 100) {
            val start = System.nanoTime()
            parser.parse(payload)
            timings[i] = System.nanoTime() - start
        }
        timings.sort()
        val p95Ms = timings[94] / 1_000_000.0
        println("VCardImporter minimal parse p95=${"%.2f".format(p95Ms)}ms")
        assertTrue(
            "Minimal payload p95 must be < 5ms; got ${'$'}p95Ms ms",
            p95Ms < 5.0,
        )
    }

    /**
     * Build a near-10 KB vCard payload: 1 FN + 1 TEL + ~150 N fields
     * (whitelisted but ignored — exercises the line-scanner without
     * triggering MissingFn). Caps at 10 KB so the parser doesn't bail on
     * PayloadTooLarge.
     */
    private fun build10kbPayload(): ByteArray {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Maria Ivanova\r\nTEL:+71234567890\r\n")
        // Pack until ~9.5 KB so we stay under the 10 KB cap with the
        // END:VCARD trailer. Use ASCII filler so byte-count == char-count
        // (no UTF-8 surprise — parser cap is byte-based).
        while (sb.length < 9_500) {
            sb.append("ADR;TYPE=HOME:;;Granite Lane 8;Moscow;;125000;Russia\r\n")
        }
        sb.append("END:VCARD\r\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}
