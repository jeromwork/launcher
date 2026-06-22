// T077 — Worker entrypoint. Per spec 019 FR-001..FR-013.
//
// Pipeline (per contract):
//   1. parse body → PushTriggerRequest или 400.
//   2. verify Firebase ID-token via @familycare/auth-jwt → 401.
//   3. lookup eventType в EVENT_TYPES → 400 если unknown.
//   4. authorise (per-event rule) → 403.
//   5. check idempotency cache → return cached если hit.
//   6. check rate-limit → 429 если exceeded.
//   7. resolve recipients (Firestore directory + grants) → list.
//   8. dispatch FCM (с retry) → returns DispatchResult.
//   9. cache response в idempotency store + return 200.

import { verifyFirebaseIdToken } from "@familycare/auth-jwt";
import type { Env } from "./env.js";
import { getAccessToken, ServiceAccountError } from "./auth/service-account.js";
import {
  parsePushTriggerRequest,
  MAX_SUPPORTED_SCHEMA_VERSION,
  type PushTriggerResponse,
} from "./contract/wire-format.js";
import { lookupEventType } from "./registry/event-types.js";
import {
  FirebaseFcmDispatcher,
  type DispatchResult,
} from "./dispatch/fcm-dispatcher.js";
import { FirestoreRecipientResolver } from "./recipient/resolver.js";
import { KvIdempotencyStore } from "./dedupe/idempotency.js";
import { KvRateLimiter } from "./ratelimit/rate-limiter.js";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await routeRequest(request, env);
    } catch (e) {
      // Log full error для диагностики через `wrangler tail`. Cloudflare скрывает
      // stack traces в production responses (мы не возвращаем их клиенту).
      const err = e as Error;
      console.error("Unhandled error", err.name, err.message, err.stack);
      return json(500, { ok: false, error: "internal" });
    }
  },
};

async function routeRequest(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  if (request.method !== "POST" || url.pathname !== "/push") {
    return json(404, { ok: false, error: "not-found" });
  }

  // Step 1 — parse body.
  const bodyRaw = await safeReadJson(request);
  if (bodyRaw === null) {
    return json(400, { ok: false, error: "malformed-body" });
  }
  // Schema version fail-fast (Worker fail-closed — see WireFormatVersion.kt).
  const schemaVersion = (bodyRaw as { schemaVersion?: unknown }).schemaVersion;
  if (
    typeof schemaVersion === "number" &&
    schemaVersion > MAX_SUPPORTED_SCHEMA_VERSION
  ) {
    return json(400, {
      ok: false,
      error: "unsupported-schema-version",
      message: `MAX_SUPPORTED=${MAX_SUPPORTED_SCHEMA_VERSION}`,
    });
  }
  const requestBody = parsePushTriggerRequest(bodyRaw);
  if (!requestBody) {
    return json(400, { ok: false, error: "invalid-request-shape" });
  }

  // Step 2 — verify ID-token.
  const authHeader = request.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return json(401, { ok: false, error: "missing-bearer-token" });
  }
  const token = authHeader.slice("Bearer ".length);
  const verify = await verifyFirebaseIdToken(
    token,
    env.FIREBASE_PROJECT_ID,
    env.JWKS_CACHE,
  );
  if (!verify.ok) {
    return json(401, { ok: false, error: verify.error });
  }
  const callerClaims = verify.claims;

  // Step 3 — lookup event type.
  const entry = lookupEventType(requestBody.eventType);
  if (!entry) {
    return json(400, {
      ok: false,
      error: "unknown-event-type",
      message: requestBody.eventType,
    });
  }

  // Step 4 — authorise.
  const allowed = await entry.authorise(
    callerClaims,
    requestBody.ownerUid,
    env,
  );
  if (!allowed) {
    return json(403, { ok: false, error: "forbidden" });
  }

  // Step 5 — idempotency cache.
  const idempotencyKey = request.headers.get("Idempotency-Key");
  if (!idempotencyKey) {
    return json(400, { ok: false, error: "missing-idempotency-key" });
  }
  const idempotencyStore = new KvIdempotencyStore(env.IDEMPOTENCY_CACHE);
  const cached = await idempotencyStore.get(callerClaims.uid, idempotencyKey);
  if (cached !== null) {
    return new Response(cached, {
      status: 200,
      headers: { "Content-Type": "application/json", "X-Idempotency-Hit": "1" },
    });
  }

  // Step 6 — rate limit.
  const rateLimiter = new KvRateLimiter(env.RATE_LIMIT);
  const limit = await rateLimiter.checkAndRecord(
    callerClaims.uid,
    requestBody.eventType,
    entry.rateLimit.perUid,
    entry.rateLimit.windowSeconds,
  );
  if (!limit.allowed) {
    return json(
      429,
      { ok: false, error: "rate-limit-exceeded" },
      { "Retry-After": String(limit.retryAfterSeconds ?? 60) },
    );
  }

  // Step 7 — acquire SA OAuth2 access-token (T077). Requires FIREBASE_SA_JSON
  // secret (Service Account private key JSON downloaded from Firebase Console).
  // Cached for 50min per Cloudflare isolate.
  let accessToken: string;
  try {
    accessToken = await getAccessToken(env.FIREBASE_SA_JSON);
  } catch (e) {
    if (e instanceof ServiceAccountError) {
      return json(500, {
        ok: false,
        error: "fcm-credentials-error",
        message: e.message,
      });
    }
    throw e;
  }

  // Step 8 — resolve recipients + dispatch.
  const resolver = new FirestoreRecipientResolver(env, accessToken);
  const recipients = await resolver.resolveRecipients(
    requestBody.ownerUid,
    requestBody.targetScope,
  );
  if (recipients.length === 0) {
    const response: PushTriggerResponse = {
      ok: true,
      triggerId: idempotencyKey,
      recipientCount: 0,
    };
    const responseJson = JSON.stringify(response);
    await idempotencyStore.put(callerClaims.uid, idempotencyKey, responseJson);
    return new Response(responseJson, {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  const dispatcher = new FirebaseFcmDispatcher(env, accessToken);
  const dispatchResult: DispatchResult = await dispatcher.dispatch(
    recipients,
    requestBody,
    idempotencyKey,
    entry,
  );

  // Step 9 — cache response + return 200.
  const response: PushTriggerResponse = {
    ok: true,
    triggerId: idempotencyKey,
    recipientCount: dispatchResult.delivered,
  };
  const responseJson = JSON.stringify(response);
  await idempotencyStore.put(callerClaims.uid, idempotencyKey, responseJson);
  return new Response(responseJson, {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

async function safeReadJson(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

function json(
  status: number,
  body: unknown,
  extraHeaders: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...extraHeaders,
    },
  });
}
