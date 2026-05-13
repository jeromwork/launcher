// uid == links/{linkId}.adminId authorization (FR-021).
//
// Reads `/links/{linkId}` via Firestore REST API using the service-account
// access-token (same one we use for FCM — minted by getAccessToken in fcm.ts).
// Compares the document's `adminId` field with the JWT `sub` claim. Mismatch
// → 403 at the route layer.
//
// Why REST and not Admin SDK: firebase-admin Node SDK pulls in ~5 MB of
// transitive deps that don't fit cleanly in a 1MB Cloudflare Worker bundle
// (R14 in plan.md §Risks). The two REST endpoints we need (Firestore read,
// FCM send) are trivial fetch calls.

import type { Env } from "./env";

export class AuthorizationError extends Error {
  constructor(public readonly status: 403 | 404, message: string) {
    super(message);
    this.name = "AuthorizationError";
  }
}

interface FirestoreDocument {
  readonly name: string;
  readonly fields?: Record<string, FirestoreValue>;
}

// Firestore REST values are tagged unions; we only need `stringValue` here.
interface FirestoreValue {
  readonly stringValue?: string;
  readonly integerValue?: string;
  readonly booleanValue?: boolean;
  readonly nullValue?: null;
}

/**
 * Fetch `/links/{linkId}` via Firestore REST and assert that the supplied
 * `uid` matches the document's `adminId` field.
 *
 * Throws [AuthorizationError] with:
 *  - status 404 if the link does not exist
 *  - status 403 if it exists but the uid is not the admin
 *
 * The route handler maps these directly to HTTP responses.
 */
export async function assertUidIsAdmin(
  env: Env,
  accessToken: string,
  linkId: string,
  uid: string,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const url = `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/links/${encodeURIComponent(linkId)}`;
  const res = await fetchImpl(url, {
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Accept": "application/json",
    },
  });

  if (res.status === 404) {
    throw new AuthorizationError(404, `link ${linkId} not found`);
  }
  if (!res.ok) {
    throw new AuthorizationError(403, `firestore read failed: HTTP ${res.status}`);
  }

  const doc = (await res.json()) as FirestoreDocument;
  const adminId = doc.fields?.["adminId"]?.stringValue;
  if (!adminId) {
    throw new AuthorizationError(403, `link ${linkId} missing adminId field`);
  }
  if (adminId !== uid) {
    throw new AuthorizationError(403, "uid does not match link.adminId");
  }
}
