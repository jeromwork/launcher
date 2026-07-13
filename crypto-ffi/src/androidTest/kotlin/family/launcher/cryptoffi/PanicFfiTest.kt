package family.launcher.cryptoffi

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.crypto_ffi.hello
import uniffi.crypto_ffi.panics

/**
 * TASK-122 T013 — panic-across-FFI contract fitness test.
 *
 * Covers FR-011 (Rust `panic!()` MUST convert to a Kotlin exception, NOT a
 * process abort) and SC-003. Per Clarifications Q4 this is a mandatory smoke
 * test — UniFFI docs do NOT formally document the panic contract, so we ship
 * an explicit test that would fail (as a process crash reported by the test
 * runner) if a future UniFFI bump or an exit-ramp to manual JNI silently
 * broke the invariant.
 *
 * UniFFI 0.28: for a non-throwing signature (our `panics(msg) -> String`)
 * a Rust panic surfaces as an `InternalException` in Kotlin. We assert
 * "any Throwable" here rather than pinning the exact type — the point is
 * that the process survives, not the specific class name (which UniFFI has
 * renamed across versions).
 */
@RunWith(AndroidJUnit4::class)
class PanicFfiTest {

    @Test
    fun panic_isConvertedToKotlinException() {
        var caught: Throwable? = null
        try {
            val result = panics("boom")
            // If we reach this line the panic did NOT propagate — either UniFFI
            // silently swallowed it or the Rust function stopped panicking.
            // Both are contract violations.
            throw AssertionError(
                "panics('boom') returned '$result' instead of throwing. " +
                    "FR-011 panic-across-FFI contract broken."
            )
        } catch (e: AssertionError) {
            // Re-throw our own assertion so JUnit reports it as a test failure.
            throw e
        } catch (e: Throwable) {
            caught = e
            Log.d(TAG, "panics('boom') threw ${e::class.simpleName}: ${e.message}")
        }

        // Sanity: the caught throwable should look like an exception/error class
        // (defensive — protects against UniFFI throwing something weird).
        val name = caught!!::class.simpleName ?: ""
        check(name.contains("Exception") || name.contains("Error")) {
            "Unexpected throwable type from panic: $name"
        }

        // Prove the process is still alive after the panic — a fresh FFI call
        // succeeds. If Rust panics unwound the JVM the whole process would be
        // dead by now and this line unreachable (test infra would report a
        // native crash, not a test failure).
        val alive = hello("still-alive")
        Log.d(TAG, "post-panic hello('still-alive') -> $alive")
        check(alive == "Hello, still-alive") {
            "Post-panic FFI call returned unexpected value: $alive"
        }
    }

    companion object {
        private const val TAG = "CryptoFfi"
    }
}
