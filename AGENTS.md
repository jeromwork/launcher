# launcher Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-06-19

## Active Technologies
- [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION] + [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION] (018-f5-config-e2e-encryption)
- [if applicable, e.g., PostgreSQL, CoreData, files or N/A] (018-f5-config-e2e-encryption)
- Kotlin Multiplatform 2.0+ (соответствует существующим F-CRYPTO / F-4 модулям) + ionspin libsodium-kmp (через F-CRYPTO, не напрямую); F-CRYPTO ports (`AeadCipher`, `AsymmetricCrypto`, `SecureKeystore`, `CryptoError`); F-4 ports (`AuthProvider`, `AuthIdentity`); Firebase Android SDK (Firestore + Auth) — **только в app-слое** через `FirestoreRecoveryKeyVault`; AndroidX Credentials (autofill metadata) (018-f5-config-e2e-encryption)
- Android Keystore TEE (root key + wrapped DEKs через `SecureKeystore` adapter); Firestore `users/{uid}/recovery-key` (encrypted RecoveryVaultBlob); Firestore `users/{uid}/config` (SealedConfig — touched, но владение spec 008) (018-f5-config-e2e-encryption)

- Kotlin 2.0.21, JDK 17, Android Gradle Plugin 8.7.3 + AndroidX Core/AppCompat/Activity/Lifecycle, Kotlin coroutines/Flow, existing `:core` launcher contracts (002-whatsapp-tile-return)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Kotlin 2.0.21, JDK 17, Android Gradle Plugin 8.7.3

## Code Style

Kotlin 2.0.21, JDK 17, Android Gradle Plugin 8.7.3: Follow standard conventions

## Recent Changes
- 018-f5-config-e2e-encryption: Added Kotlin Multiplatform 2.0+ (соответствует существующим F-CRYPTO / F-4 модулям) + ionspin libsodium-kmp (через F-CRYPTO, не напрямую); F-CRYPTO ports (`AeadCipher`, `AsymmetricCrypto`, `SecureKeystore`, `CryptoError`); F-4 ports (`AuthProvider`, `AuthIdentity`); Firebase Android SDK (Firestore + Auth) — **только в app-слое** через `FirestoreRecoveryKeyVault`; AndroidX Credentials (autofill metadata)
- 018-f5-config-e2e-encryption: Added Kotlin Multiplatform 2.0+ (соответствует существующим F-CRYPTO / F-4 модулям) + ionspin libsodium-kmp (через F-CRYPTO, не напрямую); F-CRYPTO ports (`AeadCipher`, `AsymmetricCrypto`, `SecureKeystore`, `CryptoError`); F-4 ports (`AuthProvider`, `AuthIdentity`); Firebase Android SDK (Firestore + Auth) — **только в app-слое** через `FirestoreRecoveryKeyVault`; AndroidX Credentials (autofill metadata)
- 018-f5-config-e2e-encryption: Added Kotlin Multiplatform 2.0+ (соответствует существующим F-CRYPTO / F-4 модулям) + ionspin libsodium-kmp (через F-CRYPTO, не напрямую); F-CRYPTO ports (`AeadCipher`, `AsymmetricCrypto`, `SecureKeystore`, `CryptoError`); F-4 ports (`AuthProvider`, `AuthIdentity`); Firebase Android SDK (Firestore + Auth) — **только в app-слое** через `FirestoreRecoveryKeyVault`; AndroidX Credentials (autofill metadata)


<!-- MANUAL ADDITIONS START -->
## Project Identity Override

This project MUST be treated as a universal Android application with launcher capabilities,
not as a launcher-only product. Launcher behavior is one supported use case and operating
mode; future specs, plans, and tasks must leave room for non-launcher app features.

<!-- MANUAL ADDITIONS END -->
