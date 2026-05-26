// Symmetric link-member authorization (spec 011 FR-030..033).
//
// Difference from authorize.ts (admin-only):
//   - authorize.ts: uid MUST equal `adminId` — for FCM push (only admin pushes).
//   - this module: uid MUST equal `adminId` OR `managedDeviceFirebaseUid`
//     — for blob storage operations (both admin and managed read/write).
//
// Rationale (spec 011): blob storage симметричный — любая сторона пары может
// и залить, и прочитать blob (encrypted под peer-recipient). Не как push,
// где только admin инициирует.

import type { Env } from "./env";

export class MemberAuthorizationError extends Error {
  constructor(public readonly status: 403 | 404, message: string) {
    super(message);
    this.name = "MemberAuthorizationError";
  }
}

interface FirestoreDocument {
  readonly name: string;
  readonly fields?: Record<string, FirestoreValue>;
}

interface FirestoreValue {
  readonly stringValue?: string;
}

/**
 * Fetch `/links/{linkId}` and assert that `uid` is one of the link members
 * (either admin or managed).
 *
 * Throws [MemberAuthorizationError] with:
 *  - status 404 if the link does not exist
 *  - status 403 if it exists but the uid is neither admin nor managed
 */
export async function assertUidIsLinkMember(
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
    throw new MemberAuthorizationError(404, `link ${linkId} not found`);
  }
  if (!res.ok) {
    throw new MemberAuthorizationError(403, `firestore read failed: HTTP ${res.status}`);
  }

  const doc = (await res.json()) as FirestoreDocument;
  const adminId = doc.fields?.["adminId"]?.stringValue;
  const managedUid = doc.fields?.["managedDeviceFirebaseUid"]?.stringValue;

  if (uid === adminId || uid === managedUid) {
    return;  // Member — allowed.
  }
  throw new MemberAuthorizationError(403, "uid is neither admin nor managed");
}
