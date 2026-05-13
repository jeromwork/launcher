// Service-account OAuth + FCM HTTP v1 send (FR-023, FR-024).
//
// Two-step flow:
//
//   1. getAccessToken(env)
//      - Sign an RS256 JWT with the service-account private key.
//      - POST to oauth2.googleapis.com/token with grant_type=jwt-bearer
//        and the JWT as `assertion`.
//      - Returns access_token (TTL 1h). We cache for 50min in module scope.
//
//   2. sendFcm(env, accessToken, linkId, type, extra)
//      - POST fcm.googleapis.com/v1/projects/{pid}/messages:send
//      - Body: { message: { topic: "link-X", android: { priority: HIGH },
//                          data: { schemaVersion: "1", type, linkId, ... } } }
//      - 200 → ok. 5xx → propagate to caller (route maps to 502).
//
// Same access_token is reused for the authorize.ts Firestore read — both
// need the `https://www.googleapis.com/auth/cloud-platform` scope OR more
// specific firestore + firebase.messaging scopes. We use cloud-platform to
// keep the JWT body trivial.

import { SignJWT, importPKCS8 } from "jose";
import { parseServiceAccount, type Env } from "./env";

const OAUTH_SCOPES = [
  "https://www.googleapis.com/auth/cloud-platform",
  "https://www.googleapis.com/auth/firebase.messaging",
  "https://www.googleapis.com/auth/datastore",
].join(" ");

const ACCESS_TOKEN_TTL_REUSE_MS = 50 * 60 * 1_000;

interface CachedToken {
  readonly token: string;
  readonly expiresAtMs: number;
}

let cachedAccessToken: CachedToken | null = null;

export class FcmError extends Error {
  constructor(public readonly upstreamStatus: number, message: string) {
    super(message);
    this.name = "FcmError";
  }
}

/**
 * Mint or reuse a service-account OAuth access-token. Cached per Cloudflare
 * isolate for 50 minutes.
 */
export async function getAccessToken(
  env: Env,
  now: () => number = Date.now,
  fetchImpl: typeof fetch = fetch,
): Promise<string> {
  if (cachedAccessToken && now() < cachedAccessToken.expiresAtMs) {
    return cachedAccessToken.token;
  }

  const sa = parseServiceAccount(env);
  const iat = Math.floor(now() / 1000);
  const exp = iat + 3600; // 1h — max allowed by Google's token endpoint

  const privateKey = await importPKCS8(sa.private_key, "RS256");

  const assertion = await new SignJWT({
    scope: OAUTH_SCOPES,
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience(sa.token_uri)
    .setIssuedAt(iat)
    .setExpirationTime(exp)
    .sign(privateKey);

  const tokenRes = await fetchImpl(sa.token_uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  if (!tokenRes.ok) {
    const body = await tokenRes.text().catch(() => "");
    throw new FcmError(tokenRes.status, `oauth token exchange failed: ${tokenRes.status} ${body}`);
  }

  const json = (await tokenRes.json()) as { access_token: string; expires_in: number };
  const expiresAtMs = now() + ACCESS_TOKEN_TTL_REUSE_MS;
  cachedAccessToken = { token: json.access_token, expiresAtMs };
  return json.access_token;
}

/**
 * Send an FCM HTTP v1 data-message on topic `link-{linkId}`.
 *
 *  - All values in `data` are strings (FCM API constraint, see
 *    contracts/fcm-payload.md).
 *  - `android.priority = "HIGH"` so the message wakes Managed in Doze.
 *  - No `notification` field — silent push per FR-024.
 *
 * Returns the FCM message name (`projects/{pid}/messages/{id}`) on success;
 * throws [FcmError] on upstream non-2xx.
 */
export async function sendFcm(
  env: Env,
  accessToken: string,
  linkId: string,
  type: string,
  extra: Record<string, unknown> | undefined,
  fetchImpl: typeof fetch = fetch,
): Promise<string> {
  const data: Record<string, string> = {
    schemaVersion: "1",
    type,
    linkId,
  };
  if (extra) {
    for (const [k, v] of Object.entries(extra)) {
      if (typeof v === "string") data[k] = v;
      else if (typeof v === "number" || typeof v === "boolean") data[k] = String(v);
      // Drop nested objects — contract requires flat data-map (see fcm-payload.md).
    }
  }

  const body = {
    message: {
      topic: `link-${linkId}`,
      data,
      android: { priority: "HIGH" },
    },
  };

  const url = `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`;
  const res = await fetchImpl(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const errBody = await res.text().catch(() => "");
    // TODO(retry): exponential backoff on 5xx FCM errors. For MVP we surface
    // upstream-error and let the admin app retry at the application layer.
    throw new FcmError(res.status, `FCM send failed: ${res.status} ${errBody}`);
  }

  const json = (await res.json()) as { name: string };
  return json.name;
}

// Test hooks — vitest resets between tests to avoid cache poisoning.
export function _resetAccessTokenCacheForTests(): void {
  cachedAccessToken = null;
}
