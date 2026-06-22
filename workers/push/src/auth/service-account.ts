// Service Account credentials parsing + OAuth2 access-token minting.
// Per spec 019 T077 §"acquire SA access-token" — adapted from legacy
// push-worker/src/fcm.ts (battle-tested флоу).
//
// FCM HTTP v1 endpoint требует OAuth2 Bearer token, выпущенный для
// Service Account. Flow:
//   1. Подписываем RS256 JWT приватным ключом SA.
//   2. POST к oauth2.googleapis.com/token (jwt-bearer grant) → access_token.
//   3. Кэшируем в module-scope на 50min (token TTL 1h, оставляем запас).
//
// SA JSON хранится как Cloudflare secret FIREBASE_SA_JSON. Никогда не
// логировать содержимое — приватный ключ.

import { SignJWT, importPKCS8 } from "jose";

const OAUTH_SCOPES = [
  "https://www.googleapis.com/auth/cloud-platform",
  "https://www.googleapis.com/auth/firebase.messaging",
  "https://www.googleapis.com/auth/datastore",
].join(" ");

const ACCESS_TOKEN_TTL_REUSE_MS = 50 * 60 * 1_000;

export interface ServiceAccount {
  readonly client_email: string;
  readonly private_key: string;
  readonly project_id: string;
  readonly token_uri: string;
}

interface CachedToken {
  readonly token: string;
  readonly expiresAtMs: number;
}

let cachedSa: ServiceAccount | null = null;
let cachedAccessToken: CachedToken | null = null;

export class ServiceAccountError extends Error {
  constructor(public readonly upstreamStatus: number, message: string) {
    super(message);
    this.name = "ServiceAccountError";
  }
}

export function parseServiceAccount(saJson: string | undefined): ServiceAccount {
  if (cachedSa) return cachedSa;
  if (!saJson) {
    throw new ServiceAccountError(
      500,
      "FIREBASE_SA_JSON secret missing — set via `wrangler secret put FIREBASE_SA_JSON`",
    );
  }
  let sa: Partial<ServiceAccount>;
  try {
    sa = JSON.parse(saJson) as Partial<ServiceAccount>;
  } catch (e) {
    throw new ServiceAccountError(500, `FIREBASE_SA_JSON not valid JSON: ${(e as Error).message}`);
  }
  if (!sa.client_email || !sa.private_key || !sa.project_id) {
    throw new ServiceAccountError(
      500,
      "FIREBASE_SA_JSON missing required fields (client_email/private_key/project_id)",
    );
  }
  const resolved: ServiceAccount = {
    client_email: sa.client_email,
    private_key: sa.private_key,
    project_id: sa.project_id,
    token_uri: sa.token_uri ?? "https://oauth2.googleapis.com/token",
  };
  cachedSa = resolved;
  return resolved;
}

export async function getAccessToken(
  saJson: string | undefined,
  now: () => number = Date.now,
  fetchImpl: typeof fetch = fetch,
): Promise<string> {
  if (cachedAccessToken && now() < cachedAccessToken.expiresAtMs) {
    return cachedAccessToken.token;
  }

  const sa = parseServiceAccount(saJson);
  const iat = Math.floor(now() / 1000);
  const exp = iat + 3600;

  const privateKey = await importPKCS8(sa.private_key, "RS256");

  const assertion = await new SignJWT({ scope: OAUTH_SCOPES })
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
    throw new ServiceAccountError(
      tokenRes.status,
      `oauth token exchange failed: ${tokenRes.status} ${body}`,
    );
  }

  const json = (await tokenRes.json()) as { access_token: string; expires_in: number };
  const expiresAtMs = now() + ACCESS_TOKEN_TTL_REUSE_MS;
  cachedAccessToken = { token: json.access_token, expiresAtMs };
  return json.access_token;
}

export function _resetCachesForTests(): void {
  cachedSa = null;
  cachedAccessToken = null;
}
