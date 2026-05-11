// In-memory sliding-window rate limiter (FR-025, C12).
//
// State is per-isolate (Cloudflare may run multiple isolates per Worker, so
// the limit is approximate, not global). For MVP this is acceptable:
//
//   - traffic is bounded by the number of paired devices (~5 in first month);
//   - the goal is "block obvious abuse", not perfect fairness.
//
// TODO(scale, project-backlog TODO-ARCH-002): migrate to Cloudflare KV when
// daily traffic exceeds 1k requests or we observe abuse attempts. See
// README §Adding KV namespace.

const WINDOW_MS = 60_000; // 60s sliding window
const MAX_REQS_PER_WINDOW = 100; // FR-025

// linkId → ring buffer of timestamps (ms) within the window.
const buckets = new Map<string, number[]>();

export interface RateLimitCheck {
  readonly allowed: boolean;
  readonly retryAfterSeconds?: number;
}

/**
 * Record a request and decide whether to allow it.
 *  - allowed=true: request proceeds.
 *  - allowed=false: caller should return 429 with `Retry-After: <retryAfterSeconds>`.
 */
export function checkAndRecord(linkId: string, now: number = Date.now()): RateLimitCheck {
  const cutoff = now - WINDOW_MS;
  const existing = buckets.get(linkId);
  const recent = existing ? existing.filter((ts) => ts > cutoff) : [];

  if (recent.length >= MAX_REQS_PER_WINDOW) {
    const oldest = recent[0]!;
    const retryAfterMs = WINDOW_MS - (now - oldest);
    return {
      allowed: false,
      retryAfterSeconds: Math.max(1, Math.ceil(retryAfterMs / 1000)),
    };
  }

  recent.push(now);
  buckets.set(linkId, recent);
  return { allowed: true };
}

// Test hook — clear all buckets between tests.
export function _resetForTests(): void {
  buckets.clear();
}
