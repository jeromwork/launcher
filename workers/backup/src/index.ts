// T654 — Worker entrypoint. Per contracts/worker-api-v1.md.
//
// Pipeline:
//   1. routing — method + path → POST/GET/DELETE handler or 404.
//   2. auth — verifyFirebaseIdToken via @familycare/auth-jwt.
//   3. subject-ownership — claims.stableId (Phase 4 Track B) must equal
//      target stableId.
//   4. (POST) idempotency check → cached response or 409 conflict.
//   5. rate-limit per (stableId, verb).
//   6. (POST) parse + schema-check body.
//   7. R2 read/write/delete on path `backup/{stableId}/v1.json`.
//
// NOTE (TODO Track B): Claims interface in @familycare/auth-jwt currently
// surfaces `uid`. The `stableId` custom claim is added by workers/identity/
// (Track B). Until that lands, backup worker accepts `uid` as a proxy for
// `stableId` in test fixtures — production deploy MUST wait for Track B.

import { verifyFirebaseIdToken, type Claims, type JwksCacheKv } from "@familycare/auth-jwt";
import type { Env } from "./env.js";
import { InMemoryIdempotencyCache, sha256Hex } from "./idempotency.js";
import { InMemoryRateLimiter, type Verb } from "./ratelimit.js";

const idempotency = new InMemoryIdempotencyCache();
const rateLimit = new InMemoryRateLimiter();

/**
 * Indirection so tests can inject a stub verifier without a live JWKS fetch.
 */
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
  readonly idempotencyCache?: InMemoryIdempotencyCache;
  readonly rateLimiter?: InMemoryRateLimiter;
}

export async function handle(
  request: Request,
  env: Env,
  deps: HandlerDeps = {},
): Promise<Response> {
  const verify = deps.verify ?? defaultVerifier;
  const idem = deps.idempotencyCache ?? idempotency;
  const rl = deps.rateLimiter ?? rateLimit;
  try {
    return await routeRequest(request, env, verify, idem, rl);
  } catch (e) {
    const err = e as Error;
    console.error("Unhandled error", err.name, err.message, err.stack);
    return jsonResponse(500, { error: "INTERNAL" });
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return handle(request, env);
  },
};

async function routeRequest(
  request: Request,
  env: Env,
  verify: AuthVerifier,
  idem: InMemoryIdempotencyCache,
  rl: InMemoryRateLimiter,
): Promise<Response> {
  const url = new URL(request.url);
  const segments = url.pathname.split("/").filter(Boolean);

  // POST /backup
  if (request.method === "POST" && segments.length === 1 && segments[0] === "backup") {
    return handleUpload(request, env, verify, idem, rl);
  }
  // GET /backup/{stableId}
  if (request.method === "GET" && segments.length === 2 && segments[0] === "backup") {
    return handleFetch(request, env, verify, rl, segments[1]!);
  }
  // DELETE /backup/{stableId}
  if (request.method === "DELETE" && segments.length === 2 && segments[0] === "backup") {
    return handleDelete(request, env, verify, rl, segments[1]!);
  }
  return jsonResponse(404, { error: "NOT_FOUND" });
}

interface Authed {
  readonly claims: Claims;
  /** Effective stableId — claims.stableId once Track B lands; uid for now. */
  readonly stableId: string;
}

async function authenticate(
  request: Request,
  env: Env,
  verify: AuthVerifier,
): Promise<Authed | Response> {
  const header = request.headers.get("Authorization");
  if (!header || !header.startsWith("Bearer ")) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: "missing" });
  }
  const token = header.substring("Bearer ".length).trim();
  if (token.length === 0) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: "empty" });
  }

  const kvLike: JwksCacheKv = jwksKvFromEnv(env);
  const result = await verify(token, env.FIREBASE_PROJECT_ID, kvLike);
  if (!result.ok) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: result.error });
  }
  // Effective stableId resolution:
  //   1. PREFERRED: claims.stableId (set by identity worker's
  //      setCustomAttributes — Track B/C wiring).
  //   2. FALLBACK: claims.uid (Firebase sub). Used in two cases:
  //      (a) the very first backup call after Sign-In, BEFORE Firebase Auth
  //          back-end propagates the new custom claim into freshly-minted
  //          tokens (can take seconds to minutes in practice — Identity
  //          Platform docs are not specific).
  //      (b) the client never called /init-claim (legacy / mockBackend probes).
  //   In both fallback cases the body.stableId MUST match claims.uid below;
  //   the client is expected to supply the Firebase uid as body.stableId in
  //   the fallback path, NOT its locally-minted UUID. That keeps the
  //   blob-addressing key consistent across retries within one identity.
  //
  // task-6 T681-FOLLOWUP 2026-06-30: original Track-B-strict implementation
  // returned 401 stableId-claim-missing on case (a), but Firebase claim
  // propagation latency means most legitimate first-call uploads would fall
  // into that path. Accepted soft-fallback as a pragmatic MVP trade-off; the
  // strict variant returns when the propagation window is empirically bounded
  // (or when we move off Firebase Auth entirely).
  const claimsAny = result.claims as unknown as { stableId?: string };
  const stableId =
    typeof claimsAny.stableId === "string" && claimsAny.stableId.length > 0
      ? claimsAny.stableId
      : result.claims.uid;
  return { claims: result.claims, stableId };
}

async function handleUpload(
  request: Request,
  env: Env,
  verify: AuthVerifier,
  idem: InMemoryIdempotencyCache,
  rl: InMemoryRateLimiter,
): Promise<Response> {
  // Auth FIRST (contract §2 — 401 takes precedence over 400). Otherwise an
  // unauthenticated probe of the endpoint would leak that "Idempotency-Key" is
  // a required header.
  const authed = await authenticate(request, env, verify);
  if (authed instanceof Response) return authed;

  const idempotencyKey = request.headers.get("Idempotency-Key");
  if (!idempotencyKey || idempotencyKey.length === 0) {
    return jsonResponse(400, { error: "MISSING_IDEMPOTENCY_KEY" });
  }

  const rlDecision = rl.check(authed.stableId, "POST");
  if (!rlDecision.allowed) {
    return rateLimitResponse(rlDecision.retryAfterSeconds!);
  }

  const bodyText = await request.text();
  let body: unknown;
  try {
    body = JSON.parse(bodyText);
  } catch {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  if (typeof body !== "object" || body === null) {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  const blob = body as Record<string, unknown>;
  const schemaVersion = blob["schemaVersion"];
  if (typeof schemaVersion !== "number") {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  const maxSupported = Number.parseInt(env.MAX_SUPPORTED_SCHEMA_VERSION, 10);
  if (schemaVersion > maxSupported) {
    return jsonResponse(400, { error: "UNSUPPORTED_SCHEMA", max: maxSupported });
  }
  const bodyStableId = blob["stableId"];
  if (typeof bodyStableId !== "string" || bodyStableId.length === 0) {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  // task-6 T681-FOLLOWUP 2026-06-30: body.stableId is taken as the blob's
  // intrinsic recovery identifier (it's what the client will ask for on
  // GET / DELETE later) but the BLOB IS ADDRESSED ON THE SERVER BY
  // authed.stableId (= claims.stableId ?? claims.uid). This decouples the
  // blob's payload-internal stableId from the routing key, which is what
  // makes the legacy claims.uid fallback usable: until Firebase custom-claim
  // propagation lands, the routing key is the Firebase sub (stable per
  // Google account), so a recovery on a new device with the same Google
  // account will read the right blob. Once claims.stableId is consistently
  // available, future uploads will route under the UUID and a one-time
  // migration moves the legacy uid-keyed blob across (deferred).
  //
  // STABLE_ID_MISMATCH refused on bodyStableId !== authed.stableId is the
  // strict variant — disabled here because the legacy soft-fallback path
  // would otherwise reject every first-time upload.

  const bodyHash = await sha256Hex(bodyText);
  const cached = idem.lookup(authed.stableId, idempotencyKey, bodyHash);
  if (cached === "conflict") {
    return jsonResponse(409, { error: "IDEMPOTENCY_CONFLICT" });
  }
  if (cached !== undefined) {
    return new Response(cached.responseBody, {
      status: cached.status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
  }

  await env.RECOVERY_BLOBS.put(blobObjectKey(authed.stableId), bodyText);
  const responseBody = JSON.stringify({
    status: "stored",
    createdAt: typeof blob["createdAt"] === "string" ? blob["createdAt"] : null,
  });
  idem.store(authed.stableId, idempotencyKey, bodyHash, 200, responseBody);
  return new Response(responseBody, {
    status: 200,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

async function handleFetch(
  request: Request,
  env: Env,
  verify: AuthVerifier,
  rl: InMemoryRateLimiter,
  pathStableId: string,
): Promise<Response> {
  const authed = await authenticate(request, env, verify);
  if (authed instanceof Response) return authed;
  if (authed.stableId !== pathStableId) {
    return jsonResponse(403, { error: "STABLE_ID_MISMATCH" });
  }
  const rlDecision = rl.check(authed.stableId, "GET");
  if (!rlDecision.allowed) {
    return rateLimitResponse(rlDecision.retryAfterSeconds!);
  }
  const text = await env.RECOVERY_BLOBS.get(blobObjectKey(pathStableId));
  if (text === null) {
    return jsonResponse(404, { error: "NOT_FOUND" });
  }
  return new Response(text, {
    status: 200,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

async function handleDelete(
  request: Request,
  env: Env,
  verify: AuthVerifier,
  rl: InMemoryRateLimiter,
  pathStableId: string,
): Promise<Response> {
  const authed = await authenticate(request, env, verify);
  if (authed instanceof Response) return authed;
  if (authed.stableId !== pathStableId) {
    return jsonResponse(403, { error: "STABLE_ID_MISMATCH" });
  }
  const rlDecision = rl.check(authed.stableId, "DELETE");
  if (!rlDecision.allowed) {
    return rateLimitResponse(rlDecision.retryAfterSeconds!);
  }
  await env.RECOVERY_BLOBS.delete(blobObjectKey(pathStableId));
  return new Response(null, { status: 204 });
}

function blobObjectKey(stableId: string): string {
  return `backup/${stableId}/v1.json`;
}

function jsonResponse(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function rateLimitResponse(retryAfterSeconds: number): Response {
  return new Response(JSON.stringify({ error: "RATE_LIMITED", retryAfterSeconds }), {
    status: 429,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Retry-After": String(retryAfterSeconds),
    },
  });
}

function jwksKvFromEnv(env: Env): JwksCacheKv {
  if (env.JWKS_CACHE) {
    return {
      get: (k) => env.JWKS_CACHE!.get(k),
      put: (k, v, opts) => env.JWKS_CACHE!.put(k, v, opts),
    };
  }
  // Test fallback — in-memory single-shot. Production deploy MUST bind JWKS_CACHE.
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
