---
id: TASK-11
title: Contact Photos (Family Album foundation)
status: Planned
assignee: []
created_date: '2026-06-23 05:37'
labels:
  - phase-2
  - s-spec
  - s-5
  - photos
  - family-album
  - blob-storage
milestone: m-1
dependencies:
  - TASK-9
priority: high
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Phase 2 шаг 4. Family Album foundation. Admin загружает photos для contacts (envelope-encrypted blobs в Cloudflare R2 / Backblaze B2). Managed рендерит фото в ContactTile. Pre-fetch + cache + offline rendering. Per-recipient envelope per CLAUDE.md rule 9 (shareable artefact). Effort: ~3 weeks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Admin upload UI: photo per contact, ≤2MB after compression
- [ ] #2 Envelope encrypt: per-recipient Curve25519 wrap + AEAD blob
- [ ] #3 Storage adapter (BlobStorage port + R2/B2 adapter — backend-substitution-ready)
- [ ] #4 Managed pre-fetch on config update (через F-5c push)
- [ ] #5 Cache + offline rendering в ContactTile
<!-- AC:END -->
