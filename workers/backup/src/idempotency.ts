// T656 — In-memory idempotency cache for POST /backup (contracts/worker-api-v1.md §3).
//
// Key = `${stableId}:${idempotencyKey}`. Stores SHA-256 of canonical body bytes
// + cached response status. TTL 24h (eviction on Worker restart — acceptable
// per MVP per contract §3 "in-memory limitation acceptable").
//
// TODO(server-roadmap): persistent KV / DB at own-server cutover.

const TTL_MS = 24 * 60 * 60 * 1_000;

interface CachedResponse {
  readonly bodyHash: string;
  readonly status: number;
  readonly responseBody: string;
  readonly storedAt: number;
}

export class InMemoryIdempotencyCache {
  private readonly entries = new Map<string, CachedResponse>();
  constructor(private readonly now: () => number = Date.now) {}

  /**
   * Look up an existing entry; returns the cached response if the keys + body
   * match, returns `"conflict"` if same key but different body, returns
   * `undefined` if no entry (caller should proceed and call {@link store}).
   */
  lookup(
    stableId: string,
    idempotencyKey: string,
    bodyHash: string,
  ): CachedResponse | "conflict" | undefined {
    const key = `${stableId}:${idempotencyKey}`;
    const existing = this.entries.get(key);
    if (!existing) return undefined;
    if (this.now() - existing.storedAt > TTL_MS) {
      this.entries.delete(key);
      return undefined;
    }
    if (existing.bodyHash !== bodyHash) return "conflict";
    return existing;
  }

  store(
    stableId: string,
    idempotencyKey: string,
    bodyHash: string,
    status: number,
    responseBody: string,
  ): void {
    const key = `${stableId}:${idempotencyKey}`;
    this.entries.set(key, {
      bodyHash,
      status,
      responseBody,
      storedAt: this.now(),
    });
  }
}

/**
 * SHA-256 hash of UTF-8 encoded body — used as idempotency body fingerprint.
 * Hex lowercase.
 */
export async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  const bytes = new Uint8Array(digest);
  let out = "";
  for (let i = 0; i < bytes.length; i++) {
    out += bytes[i]!.toString(16).padStart(2, "0");
  }
  return out;
}
