// Backblaze B2 (S3-compatible) blob storage proxy (spec 011 FR-030..033).
//
// Maps Worker HTTP requests → S3 v4 signed requests to B2.
// Path layout: /links/{linkId}/private-media/{uuid}
//
// Endpoints exposed to client:
//   PUT    /blobs/{linkId}/{uuid}    — upload encrypted envelope bytes
//   GET    /blobs/{linkId}/{uuid}    — download
//   DELETE /blobs/{linkId}/{uuid}    — delete (idempotent: 404 → 204)
//   GET    /blobs/{linkId}/          — list all uuids в link'е (returns JSON array)
//
// Authorization (caller's responsibility — handled in routes):
//   Bearer Firebase ID-token валидируется через verifyFirebaseIdToken
//   uid должен быть link member (admin OR managed) — assertUidIsLinkMember
//
// Why aws4fetch: standard S3 v4 signing library, ~30 KB, тривиально упаковывается
// в Worker bundle. Backblaze B2 принимает same S3 signature.

import { AwsClient } from "aws4fetch";
import type { Env } from "./env";

export class B2Error extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "B2Error";
  }
}

// Cache AwsClient per isolate — same pattern as cached service account.
let cachedClient: AwsClient | null = null;
let cachedClientKey: string | null = null;

function getClient(env: Env): AwsClient {
  // Re-create если credentials changed (тесты могут менять между runs).
  const credKey = env.B2_KEY_ID + ":" + env.B2_APPLICATION_KEY;
  if (cachedClient && cachedClientKey === credKey) return cachedClient;
  cachedClient = new AwsClient({
    accessKeyId: env.B2_KEY_ID,
    secretAccessKey: env.B2_APPLICATION_KEY,
    service: "s3",
    // Region derived из endpoint: s3.eu-central-003.backblazeb2.com → eu-central-003
    region: extractRegion(env.B2_ENDPOINT),
  });
  cachedClientKey = credKey;
  return cachedClient;
}

function extractRegion(endpoint: string): string {
  // endpoint format: s3.{region}.backblazeb2.com
  const m = endpoint.match(/^s3\.([^.]+)\.backblazeb2\.com$/);
  return m ? m[1]! : "us-east-005";
}

function objectKey(linkId: string, uuid: string): string {
  // S3 object key = path внутри bucket'a. Совпадает с прежним layout в Firebase.
  return `links/${linkId}/private-media/${uuid}`;
}

function objectUrl(env: Env, linkId: string, uuid: string): string {
  return `https://${env.B2_ENDPOINT}/${env.B2_BUCKET_NAME}/${objectKey(linkId, uuid)}`;
}

function listUrl(env: Env, linkId: string): string {
  // S3 list-objects-v2 с prefix.
  const prefix = `links/${linkId}/private-media/`;
  return `https://${env.B2_ENDPOINT}/${env.B2_BUCKET_NAME}/?list-type=2&prefix=${encodeURIComponent(prefix)}`;
}

/** PUT bytes — upload. */
export async function uploadBlob(
  env: Env,
  linkId: string,
  uuid: string,
  body: ArrayBuffer,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const client = getClient(env);
  const signed = await client.sign(objectUrl(env, linkId, uuid), {
    method: "PUT",
    body,
    headers: {
      "Content-Type": "application/cbor",
      "Content-Length": String(body.byteLength),
    },
  });
  const res = await fetchImpl(signed);
  if (!res.ok) {
    throw new B2Error(res.status === 403 ? 502 : 502, `B2 upload failed: HTTP ${res.status}`);
  }
}

/** GET bytes — download. */
export async function downloadBlob(
  env: Env,
  linkId: string,
  uuid: string,
  fetchImpl: typeof fetch = fetch,
): Promise<ArrayBuffer> {
  const client = getClient(env);
  const signed = await client.sign(objectUrl(env, linkId, uuid), { method: "GET" });
  const res = await fetchImpl(signed);
  if (res.status === 404) {
    throw new B2Error(404, "blob not found");
  }
  if (!res.ok) {
    throw new B2Error(502, `B2 download failed: HTTP ${res.status}`);
  }
  return await res.arrayBuffer();
}

/** DELETE — idempotent (404 → silent success). */
export async function deleteBlob(
  env: Env,
  linkId: string,
  uuid: string,
  fetchImpl: typeof fetch = fetch,
): Promise<void> {
  const client = getClient(env);
  const signed = await client.sign(objectUrl(env, linkId, uuid), { method: "DELETE" });
  const res = await fetchImpl(signed);
  if (res.status === 404 || res.ok || res.status === 204) {
    return;  // idempotent — already gone counts as success.
  }
  throw new B2Error(502, `B2 delete failed: HTTP ${res.status}`);
}

/** List uuids в link'е. Returns array строк (raw uuid string без префикса). */
export async function listBlobs(
  env: Env,
  linkId: string,
  fetchImpl: typeof fetch = fetch,
): Promise<string[]> {
  const client = getClient(env);
  const signed = await client.sign(listUrl(env, linkId), { method: "GET" });
  const res = await fetchImpl(signed);
  if (!res.ok) {
    throw new B2Error(502, `B2 list failed: HTTP ${res.status}`);
  }
  const xml = await res.text();
  return extractKeysFromListXml(xml, `links/${linkId}/private-media/`);
}

// Parse S3 ListObjectsV2 XML response. Simple regex-based parser — no XML DOM
// in Workers без overhead. Format:
//   <ListBucketResult>
//     <Contents><Key>links/L/private-media/UUID</Key>...</Contents>
//     ...
//   </ListBucketResult>
function extractKeysFromListXml(xml: string, prefix: string): string[] {
  const out: string[] = [];
  const re = /<Key>([^<]+)<\/Key>/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(xml)) !== null) {
    const key = m[1]!;
    if (key.startsWith(prefix)) {
      out.push(key.substring(prefix.length));
    }
  }
  return out;
}
