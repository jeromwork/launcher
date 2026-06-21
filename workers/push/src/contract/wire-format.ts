// T070 — TypeScript DTOs mirroring Kotlin `core/push/commonMain/internal/PushTriggerRequest.kt`
// + `core/push/commonMain/api/PushPayload.kt`. Per spec 019 contracts/.
//
// T402 fitness function (CI script) verifies MAX_SUPPORTED_SCHEMA_VERSION
// constant matches Kotlin `family.push.api.WireFormatVersion`. Manual sync
// для DTO fields — drift catches via integration tests (T082).

/**
 * Maximum supported wire-format schema version. Mirror Kotlin
 * `family.push.api.WireFormatVersion.MAX_SUPPORTED_SCHEMA_VERSION`.
 *
 * Worker fail-CLOSED policy: incoming schemaVersion > MAX_SUPPORTED → 400.
 * (Different from receiver-side fail-SOFT — атомарный deploy у Worker'а.)
 */
export const MAX_SUPPORTED_SCHEMA_VERSION = 1;

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
  readonly schemaVersion: 1;
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

  if (b["schemaVersion"] !== 1) return null;
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
    schemaVersion: 1,
    eventType: b["eventType"] as string,
    targetScope: b["targetScope"] as TargetScope,
    ownerUid: b["ownerUid"] as string,
    payload,
  };
}
