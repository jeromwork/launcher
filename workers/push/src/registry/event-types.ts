// T071 + T100 — EventTypeRegistry. Per spec 019 contracts/event-type-registry.md,
// FR-004, FR-040.
//
// **Central whitelist** of allowed event types. Worker не пропустит триггер
// arbitrary event — только entries здесь.
//
// Adding нового event type — single-file change here + parallel changes в:
//   • core/push/commonMain/api/EventType.kt (sealed variant).
//   • consumer's PushHandler impl + DI registration.
// Per FR-060: 3-place pattern, no foundation modification.

import type { Claims } from "@familycare/auth-jwt";
import type { Env } from "../env.js";
import { hasActiveWriteGrant, isOwnerByIdentityLink } from "../auth/event-authorisation.js";
import type { PushTriggerRequest } from "../contract/wire-format.js";

export interface EventTypeRegistryEntry {
  /** Returns true if caller authorised для this event type на ownerUid namespace. */
  readonly authorise: (
    callerClaims: Claims,
    ownerUid: string,
    env: Env,
  ) => boolean | Promise<boolean>;

  /** Per-UID per-event rate limit. */
  readonly rateLimit: { readonly perUid: number; readonly windowSeconds: number };

  /**
   * FCM collapse_key formula. null = no collapse (every message unique).
   * Used as `android.collapse_key`. Same key → FCM coalesces.
   */
  readonly collapseKey: (payload: PushTriggerRequest) => string | null;

  /** FCM message priority. */
  readonly priority: "normal" | "high";

  /** Optional: FCM TTL override в секундах. Default — FCM default 4 weeks. */
  readonly ttlSeconds?: number;
}

export const EVENT_TYPES: Record<string, EventTypeRegistryEntry> = {
  // T100 — config-updated entry. Per spec 019 FR-040.
  "config-updated": {
    // ownerUid в payload — это наш stableId UUID (из F-4 AuthIdentity).
    // caller.uid из JWT — это Firebase Auth UID. Они НИКОГДА не равны.
    // Проверка владения идёт через identity-link join (mirror of firestore.rules
    // isOwner function): /identity-links/google_{firebaseUid}.stableId == ownerUid.
    authorise: async (caller, ownerUid, env) =>
      (await isOwnerByIdentityLink(caller.uid, ownerUid, env)) ||
      (await hasActiveWriteGrant(caller.uid, ownerUid, env)),
    rateLimit: { perUid: 60, windowSeconds: 60 },
    collapseKey: (payload) => {
      const configName = payload.payload["configName"] ?? "default";
      return `config-updated:${payload.ownerUid}:${configName}`;
    },
    priority: "normal",
  },

  // Future event types add here. Examples (NOT в F-5c scope):
  //
  // "sos-triggered": {
  //   authorise: (caller, ownerUid) => caller.uid === ownerUid,
  //   rateLimit: { perUid: 10, windowSeconds: 60 },
  //   collapseKey: () => null,             // each SOS unique
  //   priority: "high",
  //   ttlSeconds: 300,                     // 5 min
  // },
  //
  // "album-photo-added": {
  //   authorise: (caller, ownerUid, env) => isFamilyMember(caller.uid, ownerUid, env),
  //   rateLimit: { perUid: 60, windowSeconds: 60 },
  //   collapseKey: (payload) => `album-photo-added:${payload.payload.albumId}`,
  //   priority: "normal",
  // },
};

/** Lookup helper. Returns null если event type не зарегистрирован. */
export function lookupEventType(
  eventType: string,
): EventTypeRegistryEntry | null {
  return EVENT_TYPES[eventType] ?? null;
}
