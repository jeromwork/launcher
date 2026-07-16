---
name: checklist-server-hardening
description: Verifies that any new or modified server endpoint (Cloudflare Worker, future own-server microservice) meets the zero-trust baseline per CLAUDE.md rule 12 — authentication, rate limiting, input validation, observability, explicit failure modes. Triggered whenever a spec introduces or modifies an HTTP endpoint / Worker route / API handler. Concrete baseline values defined in TASK-105 Decision.
---

# Skill: checklist-server-hardening

## When to invoke

- Any spec that introduces a **new** server-facing endpoint (Cloudflare Worker route, future Go microservice handler, HTTP API).
- Any spec that **modifies** an existing endpoint's contract (new field, new method, changed auth).
- Any spec that references `push-worker/`, `workers/`, `POST /`, `GET /`, `endpoint`, `route`, `handler`, `API`, `Worker`.

Called automatically by `procedure-assess-spec-complexity`. Manual invocation also valid.

## Rationale (why zero-trust)

Per CLAUDE.md rule 12 — every server endpoint treats every request as potentially hostile, even from authenticated clients with valid JWT. Authenticated ≠ trusted:
- Rogue device inside legitimate identity can spam under valid JWT.
- Compromised credential (leaked token) grants valid auth to attacker.
- Buggy retry loops from honest clients look identical to abuse until throttled.

Client-side hygiene is insufficient. Server must protect itself.

## Checklist items

Emit red-only summary in chat per ADR-011 §5: `checklist-server-hardening: N/Total ✓, FAIL: CHK-XXX (short why)`. Chat line = only artefact. Do NOT create `specs/<NNN>/checklists/server-hardening.md`; scratch buffer permitted, must be deleted before returning. Grey items land as edits to `spec.md` / `plan.md`.

### Authentication

- **CHK-001**: Every endpoint declares authentication mode explicitly (JWT-required / `[public]` with reasoning). `[public]` requires justification why the endpoint cannot use JWT (e.g. JWT issuance itself, health check).
- **CHK-002**: JWT verification approach specified — library (jose / equivalent), JWKS URL, cache TTL, expiration handling, clock skew tolerance.
- **CHK-003**: JWT claims validated — `iss`, `aud`, `exp`, custom claims relevant to endpoint (identity_id, device_id).

### Rate limiting

- **CHK-004**: Rate limit dimension declared explicitly (per-identity / per-device / per-IP / combo). Justified against threat model.
- **CHK-005**: Concrete rate limit numbers in spec (or reference to preset field if segment-dependent).
- **CHK-006**: Rate limit storage tier declared (in-memory Worker isolate / Workers KV / Durable Object) with exit ramp to persistent per rule 8 (server-roadmap).
- **CHK-007**: Rate limit algorithm declared (token bucket / sliding window / fixed window) with reasoning.

### Input validation

- **CHK-008**: Input schema referenced (`data-model.md`, `contracts/*.md`, or inline). Not «trust the client to send valid data».
- **CHK-009**: Request size limit specified (defense against payload flooding — Cloudflare Worker default is generous, needs tightening for our endpoints).
- **CHK-010**: Malformed / unexpected fields policy declared (reject vs ignore-and-log).

### Observability

- **CHK-011**: Structured log format specified per endpoint (fields: identity_id / device_id / endpoint / result / latency / rate-limit-hit).
- **CHK-012**: Abuse-candidate metric wired (rate-limit-hit counter, auth-failure counter, malformed-payload counter).
- **CHK-013**: Alert threshold specified (e.g. `rate_limit_hit_rate > X per hour = alert`).

### Failure modes

- **CHK-014**: Failure mode enumerated for: rate-limit exceeded (429 + Retry-After), auth failure (401 + error code), malformed payload (400 + error code), storage tier down (503 + retry hint).
- **CHK-015**: Idempotency approach declared for state-modifying endpoints (POST/PUT/DELETE) — idempotency-key header, dedup key, or reasoning why not needed.

### Non-goals + exit ramps

- **CHK-016**: Explicit non-goals section in spec — what defense is NOT applied at MVP + exit ramp per each (per rule 8 server-roadmap.md destination).
- **CHK-017**: Cross-reference to TASK-105 Decision block for baseline values (dimensions, algorithms, thresholds).

## Interaction with other checklists

- **checklist-security** — MASVS-level app security. Server-hardening = server-side counterpart.
- **checklist-wire-format** — schema versioning of request/response bodies. Server-hardening cares about validation, not versioning.
- **checklist-backend-substitution** — server-hardening baseline must be portable to own-server migration (rule 8).

## Refuse pattern

If any CHK-001..015 fails, refuse per CLAUDE.md refuse pattern 20:
- Surface which CHK-N failed and why.
- Propose corrected shape (add the missing property with concrete value or `[public]` reasoning).
- Do not proceed to implementation phase.

## Reference

- CLAUDE.md rule 12 — universal principle (server endpoints untrusted by default).
- CLAUDE.md refuse pattern 20 — refuse spec без baseline properties.
- TASK-105 Decision block — concrete baseline values (dimensions, algorithms, storage tiers, thresholds).
- `docs/dev/server-roadmap.md` § SRV-BASELINE-* — exit ramps per property.
- `docs/dev/server-requirements.md` — existing Tier-based JWT posture (predates TASK-105 baseline).
