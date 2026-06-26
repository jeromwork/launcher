# Spec 016 (F-CRYPTO) — defense-in-depth: physically strip Fake* crypto adapters
# from release dex. Detekt rule FakeCryptoInReleaseRule catches accidental imports
# at compile time; -assumenosideeffects ensures even reflective lookup fails at runtime
# if someone bypasses the lint check.
-assumenosideeffects class cryptokit.crypto.fake.** { *; }

# Spec 016 — keep KeyBlob @Serializable shape stable for backward-compat reads
# (contracts/key-blob-v1.md mandates that future minor releases parse v1 fixtures).
-keepnames class cryptokit.crypto.api.values.KeyBlob { *; }
-keepnames class cryptokit.crypto.api.values.KeyBlob$Companion { *; }
-keepnames class cryptokit.crypto.api.values.ByteArrayBase64Serializer { *; }
