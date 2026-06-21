package family.push.android

/**
 * T113 — Worker URL constant. Per spec 019 Q5 resolution, FR-025.
 *
 * Current: free-tier `*.workers.dev` subdomain.
 *
 * TODO(server-roadmap SRV-PUSH-FOUNDATION / TODO-ARCH-001): migrate to custom
 * domain (e.g. `push.<our-domain>/push`). Exit ramp documented в
 * docs/dev/project-backlog.md TODO-ARCH-001. Change requires:
 *   1. wrangler.toml — add [[routes]] block.
 *   2. This constant — update URL.
 *   3. DNS — CNAME push.<domain> → workers.dev account.
 *
 * Note: this is the NEW push-worker endpoint (workers/push/), parallel к legacy
 * push-worker/ /notify endpoint. Phase 4 migration will eventually retire
 * legacy worker.
 */
object WorkerBaseUrl {
    /** Production endpoint URL. */
    const val URL: String = "https://launcher-push-v2.<account>.workers.dev/push"

    // TODO(setup T420): replace `<account>` placeholder с your Cloudflare
    // account subdomain. Currently a placeholder — DefaultPushTrigger will
    // return Outcome.Failure(NetworkFailure) until updated.
}
