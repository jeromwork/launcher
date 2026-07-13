package family.launcher.cryptoffi

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.crypto_ffi.hello

/**
 * TASK-122 T012 — instrumented smoke test for the Rust `hello(name)` FFI export.
 *
 * Covers FR-002 (`hello(name) -> "Hello, {name}"`) and SC-002 (round-trip through
 * JNA + UniFFI-generated Kotlin binding calls into `libcrypto_ffi.so` on arm64).
 *
 * Execution is deferred to owner Verification (T015 physical Xiaomi 11T or T016
 * arm64 emulator) — AI session only proves the test APK compiles.
 */
@RunWith(AndroidJUnit4::class)
class HelloFfiTest {

    @Test
    fun hello_returnsGreeting() {
        val result = hello("world")
        Log.d(TAG, "hello('world') -> $result")
        assertEquals("Hello, world", result)
    }

    @Test
    fun hello_worksWithNonAsciiName() {
        // UTF-8 round-trip: Kotlin String -> JNA -> Rust String -> Rust format!() ->
        // JNA -> Kotlin String. Cyrillic input catches any accidental ASCII/latin1
        // narrowing in the FFI layer.
        val result = hello("мир")
        Log.d(TAG, "hello('мир') -> $result")
        assertEquals("Hello, мир", result)
    }

    companion object {
        // Logcat tag per checklist-dev-experience CHK018 — a single "CryptoFfi"
        // tag lets `adb logcat -s CryptoFfi:D` filter all crypto-ffi test output.
        private const val TAG = "CryptoFfi"
    }
}
