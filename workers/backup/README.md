# workers/backup — Recovery-key-backup Worker (Spec task-6 F-5)

R2-backed storage for `RecoveryKeyBackupBlob`. Implements the contract in
[../../specs/task-6-root-key-hierarchy-recovery/contracts/worker-api-v1.md](../../specs/task-6-root-key-hierarchy-recovery/contracts/worker-api-v1.md).

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/backup` | Upload blob (first-time setup OR Fallback re-setup). |
| `GET` | `/backup/{stableId}` | Fetch blob (cross-device recovery). |
| `DELETE` | `/backup/{stableId}` | Wipe blob (Fallback flow). |

Auth: `Authorization: Bearer <Firebase ID-token>`. `claims.stableId` must match
the body / path stableId. Until workers/identity/ (Track B) lands, the Worker
falls back to `claims.uid` so the surface can be exercised in tests.

## Quick start

### Local dev

```bash
cd workers/backup
npm install
npm test                # vitest — uses in-memory R2 + idempotency + rate-limit mocks
```

### Wrangler dev (against AVD)

```bash
wrangler dev            # exposes :8787; Android emulator hits via 10.0.2.2
```

For a physical Android device:

```bash
adb reverse tcp:8787 tcp:8787
# then build debug APK — BuildConfig.RECOVERY_BACKUP_WORKER_URL points at
# http://10.0.2.2:8787 which now reaches the host.
```

### Deploy

```bash
# Owner has Cloudflare credentials. Provision R2 bucket + KV namespace first:
wrangler r2 bucket create launcher-recovery-blobs
# (KV — reuse JWKS_CACHE namespace from workers/push/.)
wrangler deploy
```

## Env vars

| Name | Where | Notes |
|---|---|---|
| `FIREBASE_PROJECT_ID` | wrangler.toml `[vars]` | shared with workers/push/. |
| `MAX_SUPPORTED_SCHEMA_VERSION` | wrangler.toml `[vars]` | mirrors Kotlin `RecoveryKeyBackupBlob.SCHEMA_VERSION_V1`. |
| `RECOVERY_BLOBS` | wrangler.toml `[[r2_buckets]]` | R2 binding. |
| `JWKS_CACHE` | wrangler.toml `[[kv_namespaces]]` | optional — falls back to in-memory in tests. |

No secrets (no API keys / service accounts) — auth comes from the user's
Firebase ID-token verified against public JWKS.

## Server roadmap

This Worker is the **free-tier interim** before we own a domain + dedicated
server. See [`docs/dev/server-roadmap.md`](../../docs/dev/server-roadmap.md)
`SRV-RECOVERY-001` for the eventual migration target.
