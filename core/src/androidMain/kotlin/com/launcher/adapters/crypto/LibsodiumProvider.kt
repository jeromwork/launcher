package com.launcher.adapters.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid

// Singleton Lazysodium instance — loads libsodium .so once per process.
// Все Lazysodium-based adapters делят одну инстанцию. Vendor type confined here
// (CLAUDE.md rule 1 — никаких com.goterl.* imports вне `adapters/crypto/`).
internal object LibsodiumProvider {
    val sodium: LazySodiumAndroid by lazy { LazySodiumAndroid(SodiumAndroid()) }
}
