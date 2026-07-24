package family.crypto.mls

/**
 * One device's MLS ports, sharing a single [MlsSnapshotStore] (TASK-124, T010).
 *
 * The three ports are facets of the same crypto state: a group created through [group] is the group
 * [crypto] encrypts into, and a KeyPackage generated through [keyPackages] is openable by the same
 * storage when its Welcome comes back. Wiring them with separate stores is a bug, so DI (and the
 * tests) construct this instead of the ports individually.
 *
 * In-memory only — a new instance is a factory-fresh device (TASK-125 makes it survive a reboot).
 */
class OpenMlsStack {

    private val store = MlsSnapshotStore()

    val group: OpenMlsGroupPort = OpenMlsGroupPort(store)
    val crypto: OpenMlsCryptoPort = OpenMlsCryptoPort(store)
    val keyPackages: OpenMlsKeyPackagePort = OpenMlsKeyPackagePort(store)
}
