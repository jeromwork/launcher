// T072 — per-event-type authorisation helpers. Per spec 019 FR-005.
//
// Каждый event-type объявляет `authorise(callerClaims, ownerUid, env): boolean`
// в EVENT_TYPES registry. Этот файл предоставляет переиспользуемые primitives.

import type { Env } from "../env.js";
import { getAccessToken } from "./service-account.js";

/**
 * Active write-grant check. Reads Firestore `/users/{ownerUid}/grants/{callerUid}`
 * (extension к F-5b grant model — same shape).
 *
 * Returns true iff:
 *   • grant document exists,
 *   • grant.role === "write" OR grant.role === "admin",
 *   • grant.expiresAt > now (or absent — never-expires).
 *
 * TODO(F-5b extension): caching в KV per (caller, owner) с 30-sec TTL — acceptable
 * staleness vs Firestore read budget. Сейчас each call = Firestore read.
 */
export async function hasActiveWriteGrant(
  callerUid: string,
  ownerUid: string,
  env: Env,
): Promise<boolean> {
  if (callerUid === ownerUid) return true; // owner всегда authorised.

  const accessToken = await acquireServiceAccountToken(env);
  if (!accessToken) return false;

  const url = `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${encodeURIComponent(ownerUid)}/grants/${encodeURIComponent(callerUid)}`;
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (response.status === 404) return false; // no grant doc.
  if (!response.ok) return false; // network / auth failure — fail closed.

  const doc = (await response.json()) as FirestoreDocument;
  return isActiveWriteGrant(doc);
}

interface FirestoreDocument {
  readonly fields?: Record<string, FirestoreValue>;
}
type FirestoreValue =
  | { stringValue: string }
  | { integerValue: string }
  | { timestampValue: string }
  | { booleanValue: boolean };

function isActiveWriteGrant(doc: FirestoreDocument): boolean {
  const fields = doc.fields ?? {};
  const roleField = fields["role"];
  const role =
    roleField && "stringValue" in roleField ? roleField.stringValue : null;
  if (role !== "write" && role !== "admin") return false;

  const expiresAtField = fields["expiresAt"];
  if (expiresAtField && "timestampValue" in expiresAtField) {
    const expiresAt = Date.parse(expiresAtField.timestampValue);
    if (!Number.isNaN(expiresAt) && expiresAt <= Date.now()) return false;
  }
  return true;
}

/**
 * Acquires Service Account access-token via OAuth2 (JWT bearer flow).
 * Delegates to shared service-account.ts helper (cached 50min per isolate).
 */
async function acquireServiceAccountToken(env: Env): Promise<string | null> {
  try {
    return await getAccessToken(env.FIREBASE_SA_JSON);
  } catch {
    return null;
  }
}

/**
 * Identity-link join: проверяет что callerUid (Firebase Auth UID) — это
 * тот же владелец, что и ownerUid (наш stableId UUID). Зеркалирует
 * Firestore Rules `isOwner(uid)` (firestore.rules L446-457): читает
 * `identity-links/google_{firebaseUid}` и сравнивает `stableId` поле
 * с ownerUid.
 *
 * Used by event-types где `caller.uid === ownerUid` не работает потому что
 * каноничный owner — наш stableId, а Firebase Auth выдаёт другой UID.
 */
export async function isOwnerByIdentityLink(
  callerUid: string,
  ownerUid: string,
  env: Env,
): Promise<boolean> {
  const accessToken = await acquireServiceAccountToken(env);
  if (!accessToken) return false;

  const url = `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents/identity-links/${encodeURIComponent("google_" + callerUid)}`;
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) return false;

  const doc = (await response.json()) as FirestoreDocument;
  const stableIdField = doc.fields?.["stableId"];
  if (!stableIdField || !("stringValue" in stableIdField)) return false;
  return stableIdField.stringValue === ownerUid;
}
