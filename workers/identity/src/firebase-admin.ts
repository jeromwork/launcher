// Minimal Firebase Admin port — abstracts over the two operations this Worker
// needs:
//   1. Read/write `/identity-links/{uid}` document in Firestore.
//   2. Set custom claim `stableId` on the Firebase Auth user.
//
// Default impl talks to the Identity Toolkit REST + Firestore REST APIs
// directly (the npm `firebase-admin` SDK is not Workers-runtime-compatible
// — it bundles Node.js streams). Tests inject a fake instead.
//
// **Not implemented in this commit** — owner provisioning needed for the
// service-account JWT signing path. See TODO at the bottom.

export interface FirebaseAdmin {
  /**
   * Look up the `stableId` already bound to this uid, or `null` if none.
   * Atomic-read semantics: caller decides whether to allocate a fresh one
   * via {@link bindStableId} on `null`.
   */
  readStableIdForUid(uid: string): Promise<string | null>;

  /**
   * Atomically write `(uid → stableId)` and set the custom claim. MUST be
   * idempotent — calling twice with the same arguments is a no-op (allows
   * client retries).
   */
  bindStableId(uid: string, stableId: string): Promise<void>;
}

/**
 * In-memory adapter used by tests. Production deploy uses a REST-backed
 * impl that owner finishes after Cloudflare credentials land.
 */
export class InMemoryFirebaseAdmin implements FirebaseAdmin {
  private readonly bindings = new Map<string, string>();

  async readStableIdForUid(uid: string): Promise<string | null> {
    return this.bindings.get(uid) ?? null;
  }

  async bindStableId(uid: string, stableId: string): Promise<void> {
    const existing = this.bindings.get(uid);
    if (existing !== undefined && existing !== stableId) {
      throw new Error(
        `Firebase Admin invariant violated: uid=${uid} already bound to ${existing}, refusing rebind to ${stableId}`,
      );
    }
    this.bindings.set(uid, stableId);
  }

  /** Test helper: pre-seed an existing binding (e.g. for idempotency tests). */
  seed(uid: string, stableId: string): void {
    this.bindings.set(uid, stableId);
  }
}

// TODO(server-roadmap SRV-IDENTITY-001 / owner-provisioning):
//   Implement REST-backed FirebaseAdmin: signed service-account JWT →
//   exchange for OAuth access token → call
//   `https://identitytoolkit.googleapis.com/v1/accounts:update` (custom
//   claims) + Firestore `/v1/projects/{id}/databases/(default)/documents/identity-links/{uid}`.
//   Worker can sign via Web Crypto (RS256 with the PEM private key from
//   FIREBASE_SA_JSON). Adapter not written here because owner has to
//   provision the service account first and we do not need it for vitest.
