// T061 — KV-backed JWKS cache с dynamic TTL parsing из Cache-Control header.
// Per spec 019 FR-003, Q4 resolution.
//
// Google rotates JWKS keys периодически (~weekly). Caching без honoring
// Cache-Control → stale keys → auth failures during rotation window.
// Dynamic TTL parsing aligns cache lifetime с Google's signed window.

import type { JwksCacheKv } from "./types.js";

const FIREBASE_JWKS_URL =
  "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

const JWKS_CACHE_KEY = "firebase-jwks-v1";

/**
 * Минимальный TTL — safety net против Google misconfig (zero max-age).
 * Если Cache-Control absent или max-age=0 → используем этот fallback.
 */
const MIN_TTL_SECONDS = 60;

/**
 * Максимальный TTL — safety net против infinite-cache misconfig.
 * Google по факту rotates ~weekly, larger cache useless.
 */
const MAX_TTL_SECONDS = 86_400; // 24 hours

export interface JwksRaw {
  /** Mapping kid → PEM-encoded x509 cert (Firebase convention). */
  readonly keys: Record<string, string>;
  /** Seconds until cache entry should be considered stale. */
  readonly cachedAt: number;
}

/**
 * Returns JWKS, fetching from Google if cache miss или expired. Honors
 * Cache-Control max-age на response (FR-003 dynamic TTL).
 */
export async function getJwks(kv: JwksCacheKv): Promise<JwksRaw> {
  const cached = await kv.get(JWKS_CACHE_KEY);
  if (cached) {
    try {
      const parsed = JSON.parse(cached) as JwksRaw;
      // KV expirationTtl handles eviction, но we double-check defensively.
      if (parsed.keys && typeof parsed.keys === "object") {
        return parsed;
      }
    } catch {
      // Malformed cache entry → refetch.
    }
  }
  return await fetchAndCache(kv);
}

/**
 * Force-refresh cache — invoked при kid-not-found case (key may have rotated
 * since last fetch).
 */
export async function forceRefreshJwks(kv: JwksCacheKv): Promise<JwksRaw> {
  return await fetchAndCache(kv);
}

async function fetchAndCache(kv: JwksCacheKv): Promise<JwksRaw> {
  const response = await fetch(FIREBASE_JWKS_URL);
  if (!response.ok) {
    throw new JwksFetchError(`JWKS fetch failed: ${response.status}`);
  }
  const keys = (await response.json()) as Record<string, string>;
  const ttl = parseCacheControlMaxAge(response.headers.get("Cache-Control"));
  const raw: JwksRaw = { keys, cachedAt: Math.floor(Date.now() / 1000) };
  await kv.put(JWKS_CACHE_KEY, JSON.stringify(raw), { expirationTtl: ttl });
  return raw;
}

/**
 * Exported для unit tests (T061 acceptance: «test mocks fetch, asserts TTL
 * parsing»).
 */
export function parseCacheControlMaxAge(header: string | null): number {
  if (!header) return MIN_TTL_SECONDS;
  const match = header.match(/max-age\s*=\s*(\d+)/i);
  if (!match) return MIN_TTL_SECONDS;
  const value = Number.parseInt(match[1]!, 10);
  if (Number.isNaN(value) || value <= 0) return MIN_TTL_SECONDS;
  return Math.min(Math.max(value, MIN_TTL_SECONDS), MAX_TTL_SECONDS);
}

export class JwksFetchError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "JwksFetchError";
  }
}
