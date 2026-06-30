// T655 — In-memory rate-limit per stableId (POST 10/5min, GET 5/5min, DELETE 5/5min)
// per contracts/worker-api-v1.md §5.
//
// Why in-memory: Cloudflare Worker free tier without persistent KV in the hot path.
// Acceptable for MVP — rate-limit bypass risk is bounded by the single-stableId
// addressing surface (no enumeration, no cross-user damage) and the Argon2id
// cost on the client side (≥1.5s per attempt by SC-010 floor).
//
// TODO(server-roadmap SRV-RECOVERY-001 d): swap to persistent counter (KV or
// own-server DB) at the own-server cutover. Interface (RateLimiter) stays.

const WINDOW_MS = 5 * 60 * 1_000;

export type Verb = "POST" | "GET" | "DELETE";

interface Counter {
  readonly attempts: number[];
}

export interface RateLimitDecision {
  readonly allowed: boolean;
  /** Seconds the client should wait before retrying when not allowed. */
  readonly retryAfterSeconds?: number;
}

const LIMITS: Record<Verb, number> = {
  POST: 10,
  GET: 5,
  DELETE: 5,
};

export class InMemoryRateLimiter {
  private readonly buckets = new Map<string, Counter>();
  constructor(private readonly now: () => number = Date.now) {}

  check(stableId: string, verb: Verb): RateLimitDecision {
    const key = `${verb}:${stableId}`;
    const t = this.now();
    const cutoff = t - WINDOW_MS;
    const recent = (this.buckets.get(key)?.attempts ?? []).filter((ts) => ts > cutoff);
    if (recent.length >= LIMITS[verb]) {
      const oldest = recent[0]!;
      const retryAfterSeconds = Math.max(1, Math.ceil((oldest + WINDOW_MS - t) / 1000));
      this.buckets.set(key, { attempts: recent });
      return { allowed: false, retryAfterSeconds };
    }
    recent.push(t);
    this.buckets.set(key, { attempts: recent });
    return { allowed: true };
  }
}
