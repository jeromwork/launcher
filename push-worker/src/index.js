// TEMPORARY placeholder for push-worker.
// Replaced in Phase 5 (T062 spec 007) with full POST /notify implementation:
//   - JWT verification via jose (T063)
//   - uid == link.adminId authorization via Firestore (T064)
//   - FCM HTTP v1 send (T065)
//   - In-memory rate-limit (T066, TODO KV upgrade)
// See specs/007-pairing-and-firebase-channel/plan.md §Module map.

export default {
  async fetch(request, env, ctx) {
    return new Response("launcher-push placeholder — implementation in Phase 5", {
      status: 200,
      headers: { "Content-Type": "text/plain" }
    });
  }
};
