// REST-backed FirebaseAdmin — production implementation.
//
// Workers runtime is incompatible with the npm `firebase-admin` SDK (it bundles
// Node streams + uses fs/http modules that do not exist in the V8 isolate).
// Instead, we hit the same Google Cloud REST endpoints the SDK calls
// underneath, signing a service-account JWT via Web Crypto + jose (same
// pattern as workers/push/).
//
// Endpoints used:
//   - Firestore Documents
//       GET    /v1/projects/{pid}/databases/(default)/documents/identity-links/{uid}
//       PATCH  /v1/projects/{pid}/databases/(default)/documents/identity-links/{uid}
//     The PATCH path uses field-mask + currentDocument.exists=false on first
//     write to guard against TOCTOU races (two concurrent init-claim calls
//     for the same uid).
//   - Identity Toolkit
//       POST   https://identitytoolkit.googleapis.com/v1/projects/{pid}/accounts:update
//     Sets `customAttributes` claim (JSON-stringified). Once set, every
//     subsequent ID-token Firebase issues to this user will carry
//     claims.stableId.
//
// Idempotency contract (verified by InMemoryFirebaseAdmin + test):
//   - readStableIdForUid returns existing or null.
//   - bindStableId is no-op on identical (uid, stableId); throws on mismatch.
//
// Cost model:
//   - 1 Firestore read + (on first call only) 1 write per identity per install.
//   - 1 Identity Toolkit setCustomClaims per first call only.
//   - Access token cached 50min — most init-claim invocations hit cache.

import type { FirebaseAdmin } from "./firebase-admin.js";
import { getAccessToken, parseServiceAccount, ServiceAccountError } from "./service-account.js";

export interface RestFirebaseAdminDeps {
  readonly saJson: string | undefined;
  readonly fetchImpl?: typeof fetch;
  readonly now?: () => number;
}

export class RestFirebaseAdmin implements FirebaseAdmin {
  private readonly fetchImpl: typeof fetch;
  private readonly now: () => number;

  constructor(private readonly deps: RestFirebaseAdminDeps) {
    this.fetchImpl = deps.fetchImpl ?? fetch;
    this.now = deps.now ?? Date.now;
  }

  async readStableIdForUid(uid: string): Promise<string | null> {
    const sa = parseServiceAccount(this.deps.saJson);
    const token = await getAccessToken(this.deps.saJson, this.now, this.fetchImpl);
    const url = this.firestoreDocUrl(sa.project_id, uid);
    const res = await this.fetchImpl(url, {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.status === 404) return null;
    if (!res.ok) {
      const body = await res.text().catch(() => "");
      throw new ServiceAccountError(
        res.status,
        `firestore read identity-links/${uid} failed: ${res.status} ${body}`,
      );
    }
    const doc = (await res.json()) as FirestoreDocument;
    const stableId = doc.fields?.stableId?.stringValue;
    return typeof stableId === "string" && stableId.length > 0 ? stableId : null;
  }

  async bindStableId(uid: string, stableId: string): Promise<void> {
    const sa = parseServiceAccount(this.deps.saJson);
    const token = await getAccessToken(this.deps.saJson, this.now, this.fetchImpl);

    // 1) Write Firestore doc with `currentDocument.exists=false` — refuses if
    //    the document already exists, which is what makes the call safe under
    //    concurrent first-write races. The caller (identity worker) already
    //    short-circuits when readStableIdForUid returned non-null, so this
    //    guard catches the rare race where two requests land between the read
    //    and the write.
    const docUrl =
      this.firestoreDocUrl(sa.project_id, uid) +
      `?currentDocument.exists=false`;
    const docBody: FirestoreDocument = {
      fields: {
        stableId: { stringValue: stableId },
        createdAt: { timestampValue: new Date(this.now()).toISOString() },
      },
    };
    const docRes = await this.fetchImpl(docUrl, {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(docBody),
    });
    if (docRes.status === 409 || docRes.status === 412) {
      // Race lost — somebody else wrote the binding first. Read what is there;
      // if it matches our stableId, treat as idempotent success; if not,
      // surface the contract violation.
      const existing = await this.readStableIdForUid(uid);
      if (existing === stableId) return;
      throw new Error(
        `Firebase Admin invariant violated: uid=${uid} already bound to ${existing}, refusing rebind to ${stableId}`,
      );
    }
    if (!docRes.ok) {
      const body = await docRes.text().catch(() => "");
      throw new ServiceAccountError(
        docRes.status,
        `firestore write identity-links/${uid} failed: ${docRes.status} ${body}`,
      );
    }

    // 2) Set the Firebase Auth custom claim. `customAttributes` is the field
    //    Identity Toolkit accepts (it's the serialised JSON the
    //    `firebase-admin` SDK builds internally). Once set, the next ID-token
    //    Firebase issues to this user carries claims.stableId.
    const claimsUrl = `https://identitytoolkit.googleapis.com/v1/projects/${sa.project_id}/accounts:update`;
    const claimsRes = await this.fetchImpl(claimsUrl, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        localId: uid,
        customAttributes: JSON.stringify({ stableId }),
      }),
    });
    if (!claimsRes.ok) {
      const body = await claimsRes.text().catch(() => "");
      throw new ServiceAccountError(
        claimsRes.status,
        `identity toolkit setCustomAttributes for uid=${uid} failed: ${claimsRes.status} ${body}`,
      );
    }
  }

  private firestoreDocUrl(projectId: string, uid: string): string {
    // Documents are addressed by full resource name. uid is opaque (Firebase
    // sub claim) — we encode it for safety even though Firebase uids are
    // alphanumeric.
    const encoded = encodeURIComponent(uid);
    return `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/identity-links/${encoded}`;
  }
}

/** Minimal Firestore wire-format types for this Worker's two operations. */
interface FirestoreDocument {
  readonly name?: string;
  readonly fields?: Record<string, FirestoreValue>;
  readonly createTime?: string;
  readonly updateTime?: string;
}

interface FirestoreValue {
  readonly stringValue?: string;
  readonly timestampValue?: string;
}
