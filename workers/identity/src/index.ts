// T663 — Identity-init Worker entrypoint. Per spec task-6 Q-M variant b.
//
// Pipeline:
//   1. POST /init-claim — only route; everything else → 404.
//   2. Parse + validate body → { uid: string } or 400.
//   3. Verify Firebase ID-token via @familycare/auth-jwt → 401 on failure.
//   4. Check claims.uid === body.uid → 403 mismatch.
//   5. Lookup existing stableId for uid (FirebaseAdmin.readStableIdForUid).
//   6. If missing — generate UUID v4, call bindStableId.
//   7. Return { stableId } 200.
//
// **Idempotency**: by construction. The Firestore document is the single
// source of truth; UUID generation only happens on `null` lookup. A
// concurrent second call would race, but `bindStableId` MUST be
// idempotent (no-op on identical args, throws on mismatch) so the loser
// of the race gets the existing stableId on retry.

import { verifyFirebaseIdToken, type JwksCacheKv, type Claims } from "@familycare/auth-jwt";
import type { Env } from "./env.js";
import { type FirebaseAdmin } from "./firebase-admin.js";

export type AuthVerifier = (
  token: string,
  projectId: string,
  kv: JwksCacheKv,
) => Promise<{ ok: true; claims: Claims } | { ok: false; error: string }>;

const defaultVerifier: AuthVerifier = async (token, projectId, kv) => {
  const r = await verifyFirebaseIdToken(token, projectId, kv);
  if (r.ok) return { ok: true, claims: r.claims };
  return { ok: false, error: r.error };
};

export interface HandlerDeps {
  readonly verify?: AuthVerifier;
  readonly firebaseAdmin: FirebaseAdmin;
  readonly newUuid?: () => string;
}

export async function handle(
  request: Request,
  env: Env,
  deps: HandlerDeps,
): Promise<Response> {
  const verify = deps.verify ?? defaultVerifier;
  const newUuid = deps.newUuid ?? generateUuidV4;
  try {
    return await routeRequest(request, env, verify, deps.firebaseAdmin, newUuid);
  } catch (e) {
    const err = e as Error;
    console.error("Unhandled error", err.name, err.message, err.stack);
    return jsonResponse(500, { error: "INTERNAL" });
  }
}

async function routeRequest(
  request: Request,
  env: Env,
  verify: AuthVerifier,
  admin: FirebaseAdmin,
  newUuid: () => string,
): Promise<Response> {
  const url = new URL(request.url);
  if (request.method !== "POST" || url.pathname !== "/init-claim") {
    return jsonResponse(404, { error: "NOT_FOUND" });
  }

  const header = request.headers.get("Authorization");
  if (!header || !header.startsWith("Bearer ")) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: "missing" });
  }
  const token = header.substring("Bearer ".length).trim();
  if (token.length === 0) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: "empty" });
  }
  const kvLike = jwksKvFromEnv(env);
  const verifyResult = await verify(token, env.FIREBASE_PROJECT_ID, kvLike);
  if (!verifyResult.ok) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: verifyResult.error });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  if (typeof body !== "object" || body === null) {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  const bodyUid = (body as Record<string, unknown>)["uid"];
  if (typeof bodyUid !== "string" || bodyUid.length === 0) {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  if (verifyResult.claims.uid !== bodyUid) {
    return jsonResponse(403, { error: "UID_MISMATCH" });
  }

  const existing = await admin.readStableIdForUid(bodyUid);
  if (existing !== null) {
    return jsonResponse(200, { stableId: existing });
  }
  const fresh = newUuid();
  await admin.bindStableId(bodyUid, fresh);
  return jsonResponse(200, { stableId: fresh });
}

function jsonResponse(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function jwksKvFromEnv(env: Env): JwksCacheKv {
  if (env.JWKS_CACHE) {
    return {
      get: (k) => env.JWKS_CACHE!.get(k),
      put: (k, v, opts) => env.JWKS_CACHE!.put(k, v, opts),
    };
  }
  const map = new Map<string, string>();
  return {
    async get(k: string) {
      return map.get(k) ?? null;
    },
    async put(k: string, v: string) {
      map.set(k, v);
    },
  };
}

/** RFC 4122 §4.4 UUID v4 via crypto.getRandomValues (available in Workers). */
function generateUuidV4(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6]! & 0x0f) | 0x40;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex: string[] = [];
  for (const b of bytes) hex.push(b.toString(16).padStart(2, "0"));
  return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10, 16).join("")}`;
}
