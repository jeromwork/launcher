// T076 — KV-based per-UID per-event rate-limiter. Per spec 019 FR-006.
//
// Каждый event type объявляет `rateLimit: { perUid, windowSeconds }` в
// EVENT_TYPES registry. Worker increments counter в KV с windowSeconds TTL.
// При превышении — 429.
//
// Note: existing push-worker/src/rate-limit.ts использует in-memory Map.
// KV variant — для cross-instance correctness. TODO-ARCH-002 уже наблюдает
// этот переход.

export interface RateLimitResult {
  readonly allowed: boolean;
  readonly retryAfterSeconds?: number;
}

export interface RateLimiter {
  checkAndRecord(
    uid: string,
    eventType: string,
    perUid: number,
    windowSeconds: number,
  ): Promise<RateLimitResult>;
}

export class KvRateLimiter implements RateLimiter {
  constructor(private readonly kv: KVNamespace) {}

  async checkAndRecord(
    uid: string,
    eventType: string,
    perUid: number,
    windowSeconds: number,
  ): Promise<RateLimitResult> {
    const key = `rl:${uid}:${eventType}`;
    const current = await this.kv.get(key);
    const count = current ? Number.parseInt(current, 10) : 0;

    if (count >= perUid) {
      return {
        allowed: false,
        retryAfterSeconds: windowSeconds,
      };
    }

    // Increment + reset TTL window. KV doesn't have atomic increment — best
    // effort. На concurrent calls — slightly over-limit possible (acceptable
    // per ACL hygiene; defence-in-depth via per-IP CF rate-limit rules).
    await this.kv.put(key, String(count + 1), {
      expirationTtl: windowSeconds,
    });

    return { allowed: true };
  }
}

/** In-memory variant — для unit tests. */
export class InMemoryRateLimiter implements RateLimiter {
  private readonly counts: Map<string, { count: number; resetAt: number }> =
    new Map();

  async checkAndRecord(
    uid: string,
    eventType: string,
    perUid: number,
    windowSeconds: number,
  ): Promise<RateLimitResult> {
    const key = `${uid}:${eventType}`;
    const now = Date.now();
    const entry = this.counts.get(key);

    if (!entry || entry.resetAt <= now) {
      this.counts.set(key, { count: 1, resetAt: now + windowSeconds * 1000 });
      return { allowed: true };
    }

    if (entry.count >= perUid) {
      return {
        allowed: false,
        retryAfterSeconds: Math.ceil((entry.resetAt - now) / 1000),
      };
    }

    entry.count++;
    return { allowed: true };
  }
}
