// TASK-119 (2026-07-09) — Worker entrypoint. Per contracts/worker-api-v1.md.
//
// Pipeline:
//   1. routing — method + path → POST/GET/DELETE handler or 404.
//   2. auth — validate our own HS256 JWT (issued by auth-worker), read
//      `claims.sub` as stableId. NO Firebase JWKS, NO custom-claim
//      propagation dependency.
//   3. (POST) idempotency check → cached response or 409 conflict.
//   4. rate-limit per (stableId, verb).
//   5. (POST) parse + schema-check body.
//   6. KV read/write/delete on path `backup/{stableId}/v1.json`.
//
// Pre-2026-07-09 the worker validated Firebase ID tokens directly and read
// `claims.stableId` (custom claim). That path had two failure modes:
//   (a) claim propagation race — freshly-minted Firebase tokens lack the
//       custom claim for seconds-to-minutes after setCustomAttributes;
//       fetch/delete then couldn't find blobs stored under authenticated
//       stableId that changed value across the propagation boundary.
//   (b) pathStableId matching required strict equality — didn't
//       tolerate the soft-fallback path (claims.uid as stableId).
// Both eliminated by moving identity issuance out to auth-worker (which
// returns our own JWT synchronously in a single HTTP call).

import type { Claims } from "@familycare/auth-jwt";
import type { Env } from "./env.js";
import { InMemoryIdempotencyCache, sha256Hex } from "./idempotency.js";
import { InMemoryRateLimiter } from "./ratelimit.js";

const idempotency = new InMemoryIdempotencyCache();
const rateLimit = new InMemoryRateLimiter();

/**
 * Indirection so tests can inject a stub verifier without cryptographic setup.
 * TASK-119: signature now HS256(AUTH_JWT_SECRET), NOT Firebase JWKS.
 */
export type AuthVerifier = (
  token: string,
  secret: string,
) => Promise<{ ok: true; claims: Claims } | { ok: false; error: string }>;

const defaultVerifier: AuthVerifier = async (token, secret) => {
  return verifyOurJwtHS256(token, secret);
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
  /** stableId — read directly from claims.sub of our HS256-signed JWT. */
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
  const result = await verify(token, env.AUTH_JWT_SECRET);
  if (!result.ok) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: result.error });
  }
  // Our JWT's `sub` claim IS the stableId — auth-worker put it there after
  // exchanging the Firebase token. No custom-claim propagation window, no
  // uid vs stableId ambiguity. If sub is missing, the token is malformed.
  const sub = (result.claims as unknown as { sub?: string }).sub;
  if (typeof sub !== "string" || sub.length === 0) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: "missing-sub" });
  }
  return { claims: result.claims, stableId: sub };
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
  // TASK-141 Part D: the version header travels as dotted strings ("1.0"), never bare integers —
  // and the Worker treats them as opaque (rule 13): it never parses business meaning, only compares
  // ordinally. It gates on `minReaderVersion` (not the diagnostics-only `schemaVersion`, wire-format.md
  // §3), the same field firestore.rules' hasValidVersionHeader gates, so the two twins guarding this
  // blob shape never disagree. `versionOrder` mirrors the firestore.rules helper: MAJOR*100000 + MINOR.
  const minReaderVersion = blob["minReaderVersion"];
  if (typeof minReaderVersion !== "string") {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  const readerOrder = versionOrder(minReaderVersion);
  const maxOrder = versionOrder(env.MAX_SUPPORTED_SCHEMA_VERSION);
  if (readerOrder === null || maxOrder === null) {
    return jsonResponse(400, { error: "MALFORMED_BODY" });
  }
  if (readerOrder > maxOrder) {
    return jsonResponse(400, {
      error: "UNSUPPORTED_SCHEMA",
      max: env.MAX_SUPPORTED_SCHEMA_VERSION,
    });
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
  // TASK-119: pathStableId is a URL-shape convenience; the AUTHORITATIVE
  // routing key is authed.stableId (from our JWT sub). Path may drift from
  // sub (e.g. legacy client shipped Firebase uid) but we always read/write
  // the blob owned by the authenticated identity.
  void pathStableId;
  const rlDecision = rl.check(authed.stableId, "GET");
  if (!rlDecision.allowed) {
    return rateLimitResponse(rlDecision.retryAfterSeconds!);
  }
  const text = await env.RECOVERY_BLOBS.get(blobObjectKey(authed.stableId));
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
  // TASK-119: pathStableId is a URL-shape convenience; the AUTHORITATIVE
  // routing key is authed.stableId (from our JWT sub). Path may drift from
  // sub (e.g. legacy client shipped Firebase uid) but we always read/write
  // the blob owned by the authenticated identity.
  void pathStableId;
  const rlDecision = rl.check(authed.stableId, "DELETE");
  if (!rlDecision.allowed) {
    return rateLimitResponse(rlDecision.retryAfterSeconds!);
  }
  await env.RECOVERY_BLOBS.delete(blobObjectKey(authed.stableId));
  return new Response(null, { status: 204 });
}

function blobObjectKey(stableId: string): string {
  return `backup/${stableId}/v1.json`;
}

/**
 * Ordinal for a dotted `MAJOR.MINOR` wire version — the exact twin of `versionOrder()` in
 * firestore.rules (MAJOR*100000 + MINOR). Returns `null` on anything that is not the plain
 * dotted form (a pre-conversion integer, a pre-release token, junk), so the caller fails closed.
 * The Worker never interprets these numbers as business meaning (rule 13); it only orders them.
 */
function versionOrder(v: string): number | null {
  const match = /^(\d+)\.(\d+)$/.exec(v.trim());
  if (match === null) return null;
  return Number.parseInt(match[1], 10) * 100000 + Number.parseInt(match[2], 10);
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

/**
 * TASK-119 — verify our own HS256-signed JWT (issued by auth-worker).
 * Minimal implementation via WebCrypto — no external library. Returns claims
 * on success; error string on any failure (signature, expiry, malformed).
 */
export async function verifyOurJwtHS256(
  token: string,
  secret: string,
): Promise<{ ok: true; claims: Claims } | { ok: false; error: string }> {
  const parts = token.split(".");
  if (parts.length !== 3) return { ok: false, error: "malformed-header" };
  const [headerB64, payloadB64, sigB64] = parts as [string, string, string];
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"],
  );
  let sigBytes: Uint8Array;
  try {
    sigBytes = b64urlDecode(sigB64);
  } catch {
    return { ok: false, error: "malformed-signature" };
  }
  const signingInput = enc.encode(`${headerB64}.${payloadB64}`);
  const valid = await crypto.subtle.verify(
    "HMAC",
    key,
    sigBytes as unknown as ArrayBuffer,
    signingInput,
  );
  if (!valid) return { ok: false, error: "invalid-signature" };
  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(new TextDecoder().decode(b64urlDecode(payloadB64)));
  } catch {
    return { ok: false, error: "malformed-payload" };
  }
  const exp = payload["exp"];
  const nowSec = Math.floor(Date.now() / 1000);
  if (typeof exp !== "number" || exp <= nowSec) {
    return { ok: false, error: "expired" };
  }
  // Cast payload to Claims — `sub` is what backup uses, other fields are
  // ignored. Callers verify presence of `sub` in authenticate().
  return { ok: true, claims: payload as unknown as Claims };
}

function b64urlDecode(s: string): Uint8Array {
  const pad = s.length % 4 === 0 ? "" : "=".repeat(4 - (s.length % 4));
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + pad;
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}
