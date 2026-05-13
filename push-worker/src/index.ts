// Cloudflare Worker route handler (FR-019, FR-020, T062).
//
// Single endpoint: POST /notify
//
// Body contract (contracts/worker-notify.md):
//   {
//     "schemaVersion": 1,
//     "linkId": "<opaque firestore doc id>",
//     "type": "config-changed" | "command-issued" | "revoke",
//     "payload"?: { ... }                      // type-specific extras
//   }
//
// Headers:
//   Authorization: Bearer <Firebase ID-token>
//   Content-Type: application/json
//
// Response codes (contract §Tests):
//   200 — push enqueued
//   400 — malformed body, unknown type, schemaVersion mismatch
//   401 — missing/invalid ID-token
//   403 — uid != link.adminId
//   404 — link not found
//   429 — rate limit exceeded (Retry-After header)
//   502 — FCM upstream error
//   500 — unexpected failure
//
// Pipeline order is intentional: auth before authorize before rate-limit
// before fcm-send. We want forged tokens to fail at 401 cheaply (no
// Firestore read), and successful auth but wrong link to fail at 403 also
// before paying the rate-limit slot.

import { AuthError, verifyFirebaseIdToken } from "./auth";
import { AuthorizationError, assertUidIsAdmin } from "./authorize";
import type { Env } from "./env";
import { FcmError, getAccessToken, sendFcm } from "./fcm";
import { checkAndRecord } from "./rate-limit";

const SUPPORTED_SCHEMA_VERSION = 1;
const SUPPORTED_TYPES = new Set(["config-changed", "command-issued", "revoke"]);

interface NotifyBody {
  readonly schemaVersion: number;
  readonly linkId: string;
  readonly type: string;
  readonly payload?: Record<string, unknown>;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await routeRequest(request, env);
    } catch (e) {
      console.error("unhandled error", e);
      return json(500, { error: "internal" });
    }
  },
};

async function routeRequest(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  if (request.method !== "POST" || url.pathname !== "/notify") {
    return json(404, { error: "not found" });
  }

  // ---- 1. parse body ---------------------------------------------------
  const body = await parseBody(request);
  if (!body) return json(400, { error: "malformed body" });
  if (body.schemaVersion !== SUPPORTED_SCHEMA_VERSION) {
    return json(400, { error: `unsupported schemaVersion ${body.schemaVersion}` });
  }
  if (!SUPPORTED_TYPES.has(body.type)) {
    return json(400, { error: `unknown type ${body.type}` });
  }
  if (typeof body.linkId !== "string" || body.linkId.length === 0) {
    return json(400, { error: "missing linkId" });
  }

  // ---- 2. verify Firebase ID-token (auth) ------------------------------
  let auth;
  try {
    auth = await verifyFirebaseIdToken(request.headers.get("Authorization"), env.FIREBASE_PROJECT_ID);
  } catch (e) {
    if (e instanceof AuthError) return json(401, { error: e.reason });
    throw e;
  }

  // ---- 3. service-account access-token (shared by authorize + fcm) -----
  let accessToken: string;
  try {
    accessToken = await getAccessToken(env);
  } catch (e) {
    if (e instanceof FcmError) return json(502, { error: `oauth: ${e.message}` });
    throw e;
  }

  // ---- 4. authorize: uid == link.adminId (Firestore read) --------------
  try {
    await assertUidIsAdmin(env, accessToken, body.linkId, auth.uid);
  } catch (e) {
    if (e instanceof AuthorizationError) return json(e.status, { error: e.message });
    throw e;
  }

  // ---- 5. rate limit (per linkId, sliding 60s/100 req) -----------------
  const limit = checkAndRecord(body.linkId);
  if (!limit.allowed) {
    return json(429, { error: "rate limit exceeded" }, {
      "Retry-After": String(limit.retryAfterSeconds ?? 60),
    });
  }

  // ---- 6. FCM HTTP v1 send ---------------------------------------------
  try {
    const messageName = await sendFcm(env, accessToken, body.linkId, body.type, body.payload);
    return json(200, { ok: true, messageName });
  } catch (e) {
    if (e instanceof FcmError) {
      // 4xx from FCM means our payload is bad — surface as 400; 5xx is upstream → 502.
      const status = e.upstreamStatus >= 500 ? 502 : 400;
      return json(status, { error: e.message });
    }
    throw e;
  }
}

async function parseBody(request: Request): Promise<NotifyBody | null> {
  try {
    return (await request.json()) as NotifyBody;
  } catch {
    return null;
  }
}

function json(status: number, body: unknown, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...extraHeaders,
    },
  });
}
