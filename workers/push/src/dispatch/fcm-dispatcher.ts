// T074 — FCM HTTP v1 dispatcher с bounded retry. Per spec 019 FR-008, FR-009,
// FR-011, FR-012.
//
// FCM HTTP v1 API endpoint: POST /v1/projects/{projectId}/messages:send
// Authorization: Bearer <SA OAuth2 access-token>
// Body: { message: { token, data, android: { priority, collapse_key, ttl } } }

import type { Env } from "../env.js";
import type { EventTypeRegistryEntry } from "../registry/event-types.js";
import type {
  PushTriggerRequest,
} from "../contract/wire-format.js";
import type { RecipientDevice } from "../recipient/resolver.js";

const RETRY_DELAYS_MS = [500, 2_000, 8_000]; // FR-009 — 3 attempts.

export interface DispatchResult {
  readonly delivered: number;
  readonly failedRecoverable: number;
  readonly failedPermanent: number;
  readonly staleTokens: string[];
}

export interface FcmDispatcher {
  dispatch(
    recipients: readonly RecipientDevice[],
    request: PushTriggerRequest,
    triggerId: string,
    entry: EventTypeRegistryEntry,
  ): Promise<DispatchResult>;
}

export class FirebaseFcmDispatcher implements FcmDispatcher {
  constructor(
    private readonly env: Env,
    private readonly accessToken: string,
  ) {}

  async dispatch(
    recipients: readonly RecipientDevice[],
    request: PushTriggerRequest,
    triggerId: string,
    entry: EventTypeRegistryEntry,
  ): Promise<DispatchResult> {
    let delivered = 0;
    let failedRecoverable = 0;
    let failedPermanent = 0;
    const staleTokens: string[] = [];

    for (const recipient of recipients) {
      const outcome = await this.dispatchOne(recipient, request, triggerId, entry);
      if (outcome.delivered) delivered++;
      if (outcome.staleToken) staleTokens.push(recipient.fcmToken);
      if (outcome.permanentFailure) failedPermanent++;
      if (outcome.recoverableFailure) failedRecoverable++;
    }

    return { delivered, failedRecoverable, failedPermanent, staleTokens };
  }

  private async dispatchOne(
    recipient: RecipientDevice,
    request: PushTriggerRequest,
    triggerId: string,
    entry: EventTypeRegistryEntry,
  ): Promise<{
    delivered: boolean;
    staleToken: boolean;
    permanentFailure: boolean;
    recoverableFailure: boolean;
  }> {
    const collapseKey = entry.collapseKey(request);
    const body = {
      message: {
        token: recipient.fcmToken,
        data: buildFcmData(request, triggerId),
        android: {
          priority: entry.priority,
          ...(collapseKey ? { collapse_key: collapseKey } : {}),
          ...(entry.ttlSeconds ? { ttl: `${entry.ttlSeconds}s` } : {}),
        },
      },
    };

    const url = `https://fcm.googleapis.com/v1/projects/${this.env.FIREBASE_PROJECT_ID}/messages:send`;

    let attempt = 0;
    while (attempt < RETRY_DELAYS_MS.length + 1) {
      attempt++;
      let response: Response;
      try {
        response = await fetch(url, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${this.accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(body),
        });
      } catch {
        // Network failure — retry if budget remains.
        if (attempt > RETRY_DELAYS_MS.length) {
          return retryFailed();
        }
        await sleep(RETRY_DELAYS_MS[attempt - 1] ?? 0);
        continue;
      }

      if (response.ok) {
        return {
          delivered: true,
          staleToken: false,
          permanentFailure: false,
          recoverableFailure: false,
        };
      }

      // FCM error mapping per FR-012:
      //   404 UNREGISTERED / 400 INVALID_ARGUMENT (на token) → stale token.
      if (response.status === 404) {
        return staleTokenResult();
      }
      if (response.status === 400) {
        const body = await safeReadJson(response);
        if (isStaleTokenError(body)) return staleTokenResult();
        return permanentFailureResult();
      }
      if (response.status === 401 || response.status === 403) {
        // SA access-token expired or revoked — каждый retry будет fail.
        return permanentFailureResult();
      }
      // 429 + 5xx — retryable.
      if (response.status === 429 || response.status >= 500) {
        if (attempt > RETRY_DELAYS_MS.length) {
          return retryFailed();
        }
        await sleep(RETRY_DELAYS_MS[attempt - 1] ?? 0);
        continue;
      }
      // Other 4xx — permanent.
      return permanentFailureResult();
    }

    return retryFailed();
  }
}

function buildFcmData(
  request: PushTriggerRequest,
  triggerId: string,
): Record<string, string> {
  // FR-011 — no content в payload; только pointers (eventType + ownerUid +
  // configName etc.). PushTriggerRequest.payload — flat string map by design.
  // Use field_-prefix per Kotlin PushPayloadWireFormat encoding.
  const data: Record<string, string> = {
    schemaVersion: "1",
    eventType: request.eventType,
    ownerUid: request.ownerUid,
    triggerId,
  };
  for (const [key, value] of Object.entries(request.payload)) {
    data[`field_${key}`] = value;
  }
  return data;
}

async function safeReadJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function isStaleTokenError(body: unknown): boolean {
  if (typeof body !== "object" || body === null) return false;
  const err = (body as { error?: { details?: Array<{ errorCode?: string }> } })
    .error;
  if (!err || !Array.isArray(err.details)) return false;
  return err.details.some(
    (d) =>
      d.errorCode === "UNREGISTERED" ||
      d.errorCode === "INVALID_ARGUMENT" ||
      d.errorCode === "SENDER_ID_MISMATCH",
  );
}

function staleTokenResult() {
  return {
    delivered: false,
    staleToken: true,
    permanentFailure: false,
    recoverableFailure: false,
  };
}

function permanentFailureResult() {
  return {
    delivered: false,
    staleToken: false,
    permanentFailure: true,
    recoverableFailure: false,
  };
}

function retryFailed() {
  return {
    delivered: false,
    staleToken: false,
    permanentFailure: false,
    recoverableFailure: true,
  };
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
