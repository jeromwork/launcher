// T075 — KV-based idempotency dedupe. Per spec 019 FR-010.
//
// Worker stores Idempotency-Key → response в KV для 10 минут. Same key arrived
// twice → return cached response без re-dispatch к FCM.
//
// Safety: idempotency key — UUID v4 from client (NOT secret, just unique).
// Collision probability negligible. Если attacker forge key — он не получает
// чужой response (different (uid, key) keyspace).

const IDEMPOTENCY_TTL_SECONDS = 600; // 10 минут per FR-010.

export interface IdempotencyStore {
  /** Returns cached response для (uid, key) если present, else null. */
  get(uid: string, key: string): Promise<string | null>;

  /** Caches response. */
  put(uid: string, key: string, response: string): Promise<void>;
}

export class KvIdempotencyStore implements IdempotencyStore {
  constructor(private readonly kv: KVNamespace) {}

  async get(uid: string, key: string): Promise<string | null> {
    return await this.kv.get(this.buildKey(uid, key));
  }

  async put(uid: string, key: string, response: string): Promise<void> {
    await this.kv.put(this.buildKey(uid, key), response, {
      expirationTtl: IDEMPOTENCY_TTL_SECONDS,
    });
  }

  /** Compound key: per-uid namespace prevents cross-uid key collision attacks. */
  private buildKey(uid: string, key: string): string {
    return `idem:${uid}:${key}`;
  }
}

/** In-memory store для unit tests. */
export class InMemoryIdempotencyStore implements IdempotencyStore {
  private readonly store: Map<string, string> = new Map();

  async get(uid: string, key: string): Promise<string | null> {
    return this.store.get(`${uid}:${key}`) ?? null;
  }

  async put(uid: string, key: string, response: string): Promise<void> {
    this.store.set(`${uid}:${key}`, response);
  }
}
