// T073 — Recipient resolution. Per spec 019 FR-007.
//
// Reads Firestore directory + grants для:
//   • TargetScope.OwnDevices: только own devices (devices каждого uid'а === ownerUid).
//   • TargetScope.OwnAndGrants: own + grant-holders' devices.
//
// Returns list of recipient devices с FCM tokens.

import type { Env } from "../env.js";
import type { TargetScope } from "../contract/wire-format.js";

export interface RecipientDevice {
  readonly uid: string;
  readonly deviceId: string;
  readonly fcmToken: string;
}

export interface RecipientResolver {
  resolveRecipients(
    ownerUid: string,
    targetScope: TargetScope,
  ): Promise<RecipientDevice[]>;
}

/**
 * Firestore-backed resolver. Reads `/users/{uid}/devices/*` для каждого uid'а
 * scope'а.
 */
export class FirestoreRecipientResolver implements RecipientResolver {
  constructor(
    private readonly env: Env,
    private readonly accessToken: string,
  ) {}

  async resolveRecipients(
    ownerUid: string,
    targetScope: TargetScope,
  ): Promise<RecipientDevice[]> {
    const uids = new Set<string>([ownerUid]);

    if (targetScope === "own-and-grants") {
      const grantHolders = await this.listGrantHolders(ownerUid);
      for (const uid of grantHolders) uids.add(uid);
    }

    const devices: RecipientDevice[] = [];
    for (const uid of uids) {
      const userDevices = await this.listDevices(uid);
      devices.push(...userDevices);
    }
    return devices;
  }

  /**
   * Lists uids of users that hold write-grant на ownerUid namespace. Reads
   * `/users/{ownerUid}/grants/*`.
   */
  private async listGrantHolders(ownerUid: string): Promise<string[]> {
    const url = `https://firestore.googleapis.com/v1/projects/${this.env.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${encodeURIComponent(ownerUid)}/grants`;
    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${this.accessToken}` },
    });
    if (!response.ok) return [];
    const data = (await response.json()) as { documents?: FirestoreDoc[] };
    return (data.documents ?? [])
      .filter(isActiveWriteGrant)
      .map(extractGrantHolderUid);
  }

  /**
   * Lists devices с FCM tokens для конкретного uid'а. Reads
   * `/users/{uid}/devices/*`.
   */
  private async listDevices(uid: string): Promise<RecipientDevice[]> {
    const url = `https://firestore.googleapis.com/v1/projects/${this.env.FIREBASE_PROJECT_ID}/databases/(default)/documents/users/${encodeURIComponent(uid)}/devices`;
    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${this.accessToken}` },
    });
    if (!response.ok) return [];
    const data = (await response.json()) as { documents?: FirestoreDoc[] };
    return (data.documents ?? [])
      .map((doc) => extractDevice(doc, uid))
      .filter((d): d is RecipientDevice => d !== null);
  }
}

interface FirestoreDoc {
  readonly name?: string;
  readonly fields?: Record<string, FirestoreFieldValue>;
}
type FirestoreFieldValue =
  | { stringValue: string }
  | { integerValue: string }
  | { timestampValue: string }
  | { booleanValue: boolean };

function isActiveWriteGrant(doc: FirestoreDoc): boolean {
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

function extractGrantHolderUid(doc: FirestoreDoc): string {
  // doc.name === "projects/.../documents/users/{ownerUid}/grants/{holderUid}"
  // We extract last segment.
  const name = doc.name ?? "";
  const segments = name.split("/");
  return segments[segments.length - 1] ?? "";
}

function extractDevice(
  doc: FirestoreDoc,
  uid: string,
): RecipientDevice | null {
  const fields = doc.fields ?? {};
  const fcmTokenField = fields["fcmToken"];
  if (!fcmTokenField || !("stringValue" in fcmTokenField)) return null;
  const fcmToken = fcmTokenField.stringValue;
  if (!fcmToken) return null;

  const name = doc.name ?? "";
  const segments = name.split("/");
  const deviceId = segments[segments.length - 1] ?? "";
  if (!deviceId) return null;

  return { uid, deviceId, fcmToken };
}

/**
 * Test fake: in-memory recipient resolver. Use в integration tests + Worker
 * unit tests.
 */
export class InMemoryRecipientResolver implements RecipientResolver {
  constructor(
    private readonly devices: Record<string, RecipientDevice[]>,
    private readonly grants: Record<string, string[]> = {},
  ) {}

  async resolveRecipients(
    ownerUid: string,
    targetScope: TargetScope,
  ): Promise<RecipientDevice[]> {
    const uids = new Set<string>([ownerUid]);
    if (targetScope === "own-and-grants") {
      for (const holder of this.grants[ownerUid] ?? []) uids.add(holder);
    }
    const result: RecipientDevice[] = [];
    for (const uid of uids) {
      result.push(...(this.devices[uid] ?? []));
    }
    return result;
  }
}
