uniffi::setup_scaffolding!();

/// Trivial smoke test that the Kotlin ↔ Rust FFI round-trip works.
///
/// Called from androidTest `HelloFfiTest.hello_returnsGreeting`.
#[uniffi::export]
pub fn hello(name: String) -> String {
    format!("Hello, {name}")
}

/// Always panics with the given message.
///
/// Fitness function for FFI panic contract (FR-011): UniFFI is expected
/// to catch this panic and convert it to a Kotlin exception (InternalException
/// for non-throwing signature, RuntimeException for throwing) — NOT process abort.
///
/// Called from androidTest `PanicFfiTest.panic_isConvertedToKotlinException`.
/// The `-> String` return is unreachable but keeps the signature non-throwing,
/// which per UniFFI issue #485 makes UniFFI produce a Kotlin exception (not abort).
///
/// See docs/architecture/crypto.md for panic contract details.
#[uniffi::export]
pub fn panics(msg: String) -> String {
    panic!("{msg}")
}
