// T070 — TypeScript DTOs mirroring Kotlin `core/push/commonMain/internal/PushTriggerRequest.kt`
// + `core/push/commonMain/api/PushPayload.kt`. Per spec 019 contracts/.
//
// Versions follow docs/architecture/wire-format.md: dotted "MAJOR.MINOR" strings, three fields,
// and the reader gates on minReaderVersion rather than schemaVersion (§3).
//
// These constants MUST equal Kotlin `family.push.api.WireFormatVersion`. The "T402 fitness
// function" referenced by the previous comment here does not exist — it was never committed, so
// nothing currently catches drift between the two sides. Tracked in TASK-138.

/** What this Worker writes. Mirror of Kotlin `WireFormatVersion.SCHEMA_VERSION`. */
export const SCHEMA_VERSION = "1.0";

/** Minimum reader this Worker's payloads require. Mirror of Kotlin `MIN_READER_VERSION`. */
export const MIN_READER_VERSION = "1.0";

/** Minimum writer required to rewrite one. Mirror of Kotlin `MIN_WRITER_VERSION`. */
export const MIN_WRITER_VERSION = "1.0";

/** Parses a dotted "MAJOR.MINOR" version into a numerically comparable value. */
export function versionOrder(v: string): number | null {
  const m = /^(\d+)\.(\d+)$/.exec(v);
  if (m === null) return null;
  return Number(m[1]) * 100000 + Number(m[2]);
}

/** Wire-format target scope enum values. */
export const TARGET_SCOPES = ["own-devices", "own-and-grants"] as const;
export type TargetScope = (typeof TARGET_SCOPES)[number];

export function isValidTargetScope(value: string): value is TargetScope {
  return (TARGET_SCOPES as readonly string[]).includes(value);
}

/**
 * HTTP body для POST /push. Mirror of Kotlin `PushTriggerRequest`.
 */
export interface PushTriggerRequest {
  readonly schemaVersion: string;
  readonly minReaderVersion: string;
  readonly minWriterVersion: string;
  readonly eventType: string;
  readonly targetScope: TargetScope;
  readonly ownerUid: string;
  readonly payload: Record<string, string>;
}

/**
 * Successful response shape.
 */
export interface PushTriggerSuccessResponse {
  readonly ok: true;
  readonly triggerId: string;
  readonly recipientCount: number;
}

/**
 * Error response shape.
 */
export interface PushTriggerErrorResponse {
  readonly ok: false;
  readonly error: string;
  readonly message?: string;
}

export type PushTriggerResponse =
  | PushTriggerSuccessResponse
  | PushTriggerErrorResponse;

/**
 * Validates incoming JSON shape против PushTriggerRequest. Returns null on
 * any violation (caller maps to 400).
 */
export function parsePushTriggerRequest(
  body: unknown,
): PushTriggerRequest | null {
  if (typeof body !== "object" || body === null) return null;
  const b = body as Record<string, unknown>;

  // Gate on minReaderVersion, not schemaVersion (§3): a client from a newer build whose extra
  // fields this Worker can ignore must not be rejected. Worker stays fail-CLOSED on anything it
  // genuinely cannot interpret, unlike the receiver which fails soft.
  const schemaVersion = b["schemaVersion"];
  const minReaderVersion = b["minReaderVersion"];
  const minWriterVersion = b["minWriterVersion"];
  if (typeof schemaVersion !== "string") return null;
  if (typeof minReaderVersion !== "string") return null;
  if (typeof minWriterVersion !== "string") return null;
  const ourLevel = versionOrder(SCHEMA_VERSION);
  const required = versionOrder(minReaderVersion);
  if (ourLevel === null || required === null) return null;
  if (ourLevel < required) return null;
  if (typeof b["eventType"] !== "string" || (b["eventType"] as string).length === 0) {
    return null;
  }
  if (
    typeof b["targetScope"] !== "string" ||
    !isValidTargetScope(b["targetScope"] as string)
  ) {
    return null;
  }
  if (typeof b["ownerUid"] !== "string" || (b["ownerUid"] as string).length === 0) {
    return null;
  }

  const payloadRaw = b["payload"];
  if (typeof payloadRaw !== "object" || payloadRaw === null) return null;
  const payload: Record<string, string> = {};
  for (const [k, v] of Object.entries(payloadRaw as Record<string, unknown>)) {
    if (typeof v !== "string") return null; // FCM constraint — strings only.
    payload[k] = v;
  }

  return {
    schemaVersion,
    minReaderVersion,
    minWriterVersion,
    eventType: b["eventType"] as string,
    targetScope: b["targetScope"] as TargetScope,
    ownerUid: b["ownerUid"] as string,
    payload,
  };
}
