package family.crypto.libsodium

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lazy one-time libsodium initialization. ionspin requires `LibsodiumInitializer.initialize()`
 * before any primitive is called; we centralize that here so every Libsodium* adapter calls
 * [ensure] from its first suspend method (FR-013 + research.md §R1).
 */
internal object LibsodiumInit {
    private val mutex = Mutex()

    suspend fun ensure() {
        if (LibsodiumInitializer.isInitialized()) return
        mutex.withLock {
            if (!LibsodiumInitializer.isInitialized()) {
                LibsodiumInitializer.initialize()
            }
        }
    }
}
