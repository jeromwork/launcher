# Phase 0 Research — F-5 Root Key Hierarchy + Owner Recovery

- **Status:** draft
- **Created:** 2026-06-28
- **Spec:** [spec.md](./spec.md)
- **Plan:** [plan.md](./plan.md)
- **Constitution:** [.specify/memory/constitution.md](../../.specify/memory/constitution.md) (Article XIV §7 server-side data minimization, added 2026-06-28)
- **Server roadmap entry:** [SRV-RECOVERY-001](../../docs/dev/server-roadmap.md)

## Purpose

This document records the **one-way door decisions** (per CLAUDE.md rule 3) for the F-5 Root Key Hierarchy + Owner Recovery feature. Every architectural decision flagged as non-trivially reversible carries: chosen option, alternatives rejected with rationale, **regret conditions** (signals that the decision was wrong), and an **exit ramp** (concrete plan to reverse). Two-way doors are not documented here — they live in plan.md.

Decision numbering R1–R6 matches plan.md cross-references.

---

## R1: Storage backend for `RecoveryKeyBackup` MVP adapter

### Decision

**Cloudflare Worker + R2 object storage.** The MVP adapter `WorkerRecoveryKeyBackup` writes and reads a single encrypted blob per device-stable identity at object path `backup/{stableId}/v1.json` inside an R2 bucket. The Worker authenticates each request by verifying a Firebase ID token (JWT) in the `Authorization: Bearer …` header and authorizes the request by matching the JWT's `stableId` custom claim against the URL path parameter.

This was the outcome of **owner pushback round 2 (2026-06-28)**, which rejected an earlier Drive App Data folder draft.

### Alternatives Rejected

- **(a) Google Drive App Data folder.** Initial draft. Rejected because it ties recovery storage to GMS (Google Mobile Services). Non-GMS devices (Huawei post-2019, Aurora OS, future de-Googled forks) lose all recovery capability — which breaks F-4 (Provider-Agnostic Auth) intent of treating identity provider and storage backend as independent capability surfaces.
- **(b) Firestore document at `users/{uid}/recovery-key`.** Legacy origin from spec 020 branch. Rejected because: (i) creates vendor lock-in on Firestore — `SRV-RECOVERY-001` explicitly tracks the goal of moving recovery storage off Firestore; (ii) rate-limit can only be expressed via Firestore Security Rules, which are bypassable by an attacker holding a valid token because Security Rules cannot count requests across documents at low cost; (iii) per-document cost model scales poorly if recovery rotates frequently.
- **(c) Cloudflare Workers KV.** Chosen against R2 because KV is eventually consistent — reads following a recent write may return stale data for up to ~60 seconds. The recovery flow on a new device needs **strong read-after-write** semantics: after a user sets up recovery on device A, device B's restore flow must read the latest blob. R2 provides strong consistency for object PUT/GET, KV does not.

### Regret Conditions

- Cloudflare deprecates the R2 free tier (10 GB / 1M Class A ops free).
- Daily request volume exceeds the Workers free-tier limit (100k requests/day per account, shared with `workers/push/`).
- GDPR or regional data residency requirements force EU-only or RU-only storage, which R2's geographic distribution does not guarantee per-object.
- R2 region outage causes recovery to be unavailable globally for hours.

### Exit Ramp

`SRV-RECOVERY-001` (own-server PostgreSQL recovery store) is the documented destination. Migration path:
1. Stand up own-server endpoint with same wire contract (`POST /backup/{stableId}`, `GET /backup/{stableId}`).
2. Implement `HttpRecoveryBackupStorage` adapter (constructor-swap with `WorkerRecoveryKeyBackup`, identical port).
3. Dual-write window — Worker writes both to R2 and forwards to own-server; reads prefer own-server, fall back to R2.
4. After 1 release cycle (≥ 4 weeks), retire R2 path; Worker becomes thin proxy or is removed.
5. Wire format (JSON blob with `schemaVersion`) is unchanged — no client-side migration needed.

### References

- [plan.md §R1](./plan.md)
- [docs/dev/server-roadmap.md → SRV-RECOVERY-001](../../docs/dev/server-roadmap.md)
- [workers/backup/](../../workers/backup/) (to be created during implementation)
- Constitution Article XIV §7 (server-side data minimization)

---

## R2: JWT custom claim `stableId` mechanism

### Decision

**Variant (i) — Firebase Admin SDK `setCustomUserClaims` invoked by the Worker at first sign-in.** When a device authenticates against the Worker for the first time with a Firebase ID token that lacks a `stableId` custom claim, the Worker:
1. Generates (or accepts from the client) a stable UUID.
2. Calls Firebase Admin SDK `auth().setCustomUserClaims(uid, { stableId })`.
3. Returns a signal to the client that the token must be refreshed.
4. On the next request, the refreshed JWT carries `stableId` inside its cryptographically-signed claims envelope.

All subsequent requests authorize by comparing `request.path.stableId` against `jwt.claims.stableId` — equality required.

This was clarified during spec clarification round Q-M (2026-06-28).

### Alternatives Rejected

- **(ii) Worker verifies JWT signature, then reads `stableId` from request body / header, trusting the client.** Rejected because: an attacker who obtains a valid Firebase ID token for user A (e.g. via session hijack, leaked refresh token, compromised secondary device) could craft a request to `GET /backup/{stableIdOfB}` while sending their own valid JWT for A — the Worker would have no cryptographic way to detect the mismatch. Variant (i) binds `stableId` into the JWT signature envelope, so the only way to lie about `stableId` is to also forge the JWT, which is computationally infeasible.

### Regret Conditions

- Firebase Admin SDK quota error or service-account configuration error blocks first-time setup for new users.
- Custom claim propagation delay (typically < 1 second but documented up to 1 hour in edge cases) creates UX where user signs in, sets up recovery, but the token still doesn't carry `stableId` for several seconds — leading to one extra refresh round-trip.
- Firebase increases pricing on `setCustomUserClaims` (currently free / included in Auth).

### Exit Ramp

Fallback to variant (ii) **with an additional server-side identity-link table**:
1. On own-server, maintain a `device_identity_links` table mapping `firebase_uid → stableId`.
2. Each authorized request: verify JWT → look up `stableId` from server table (one read, ~50ms latency) → compare against path parameter.
3. During migration from Firebase claim-based to server-table-based: the Worker can write both, and clients continue to use whichever is present.
4. Persistent claim mapping table on own-server PostgreSQL during `SRV-RECOVERY-001` cutover handles this naturally.

### References

- [plan.md §R2](./plan.md)
- [spec.md Clarifications Q-M](./spec.md)
- Firebase Admin SDK docs — `setCustomUserClaims`

---

## R3: KDF primitive for `KeyRegistry.derive(stableId, purpose)`

### Decision

**HKDF-SHA256** (RFC 5869) implemented via libsodium `crypto_kdf_hkdf_sha256_extract` and `crypto_kdf_hkdf_sha256_expand`. The Root Key (256-bit, generated once per device) acts as the input keying material (IKM). The `(stableId, purpose)` tuple is encoded as the `info` parameter, giving deterministic per-purpose 32-byte derived keys.

### Alternatives Rejected

- **(a) HKDF-SHA512.** Larger digest, slightly more computation. Rejected because we only need 32-byte output; SHA-256 provides ample security margin per RFC 5869 ("the output length of the underlying hash function is generally adequate"). No measurable security gain for double the digest size.
- **(b) BLAKE2b in libsodium's `crypto_kdf_*` mode.** Non-standard outside libsodium ecosystem; harder to audit against published cryptographic literature; harder to reimplement on a future server (Go / Rust own-server implementations would prefer standard HKDF). Note: libsodium's `crypto_kdf_*` is itself a BLAKE2b-based construction — we choose to expose the more recognized HKDF-SHA256 API to keep cryptographic surface auditable.

### Regret Conditions

- SHA-256 suffers a cryptographic break that affects HKDF (the construction has provable security reductions, so a generic SHA-256 weakness would have to be severe to affect HKDF). Considered extremely unlikely before 2035.

### Exit Ramp

Wire-format schema versioning makes this swappable:
1. Bump `RecoveryKeyBackupBlob.schemaVersion` (e.g. v1 → v2).
2. New devices use HKDF-SHA512 or HKDF-SHA-3.
3. Old blobs read with their original (v1) KDF; lazy re-encrypt on first key rotation after upgrade.
4. Domain port `KeyRegistry.derive(...)` API is unchanged; adapter implementation swaps the primitive.

### References

- [plan.md §R3](./plan.md)
- RFC 5869 — HMAC-based Key Derivation Function (HKDF)
- libsodium docs — `crypto_kdf_hkdf_sha256`

---

## R4: Argon2id parameters

### Decision

**OWASP 2024 "interactive" profile:**
- `iterations` (time cost) = **3**
- `memoryKb` (memory cost) = **65536** (64 MiB)
- `parallelism` = **1**
- `outputBytes` = **32**

Used to derive the recovery-blob encryption key from the user's recovery passphrase. Parameters are stored **per-blob** in the `RecoveryKeyBackupBlob.kdfParams` field so each blob can be read with its original parameters even after the recommended profile is updated.

### Alternatives Rejected

- **(a) "Moderate" profile** (`iterations=2`, `memoryKb=65536`). Stronger than OWASP minimum but weaker than chosen. Reserved as a fallback if measured P95 derivation time on slow OEMs (Xiaomi low-end, older Huawei) exceeds 5 seconds — degrades UX of recovery flow. Will be revisited during emulator + physical-device performance verification.
- **(b) "Sensitive" profile** (`iterations=4`, `memoryKb=524288` = 512 MiB). Would OOM-kill on low-end devices (1–2 GB RAM total). Rejected for this MVP.

### Regret Conditions

- OWASP raises the recommended baseline before 2028 (the project's next review cadence).
- Measured P95 derivation time on real devices exceeds 5 seconds — bad UX.
- A practical Argon2id weakness is discovered (no such weakness known as of 2026-06).
- Moore's-law analog for memory-hard cost forces roughly 2x memory every 5 years — by 2030, 64 MiB may be the new "moderate" baseline.

### Exit Ramp

Per-blob parameter storage makes migration painless:
1. New recovery setups use updated parameters (e.g. v2 = `iterations=4`, `memoryKb=131072`).
2. Old blobs continue to be read with their original `kdfParams`.
3. Background re-encrypt job at the own-server cutover (per `SRV-CRYPTO-008` algorithm migration job) lazily upgrades blobs when the user next changes their recovery passphrase.
4. **Review cadence:** parameters are reviewed every 2 years per `SRV-CRYPTO-PARAMS-REVIEW`. **Next review: 2028-06.**

### References

- [plan.md §R4](./plan.md)
- OWASP Password Storage Cheat Sheet (2024 revision) — Argon2id section
- [docs/dev/server-roadmap.md → SRV-CRYPTO-008, SRV-CRYPTO-PARAMS-REVIEW](../../docs/dev/server-roadmap.md)

---

## R5: Wire-format serialization for `RecoveryKeyBackupBlob`

### Decision

**JSON via kotlinx-serialization.** Single text-based wire format for the recovery blob, with `schemaVersion` as the first field. Encoded UTF-8 bytes are uploaded to R2 directly (no further wrapping). Approximate uncompressed size: ~500 bytes per blob (ciphertext + KDF params + version + nonce).

### Alternatives Rejected

- **(a) CBOR (Concise Binary Object Representation).** ~30% smaller, binary. Rejected because: (i) human-readable JSON aids debugging via R2 console / curl; (ii) at ~500-byte blob size, the 30% saving (~150 bytes) is negligible against typical mobile request overhead; (iii) kotlinx-serialization JSON path is already used elsewhere in the project (config history, presets) — adding CBOR doubles the maintenance surface.
- **(b) Protocol Buffers.** Strong typing, code generation. Rejected because: (i) Protobuf's schema-evolution rules (field tags, reserved tags, oneof) conflict with our additive-fields-only policy declared in CLAUDE.md rule 5; (ii) binary format same debugging downside as CBOR; (iii) adding `protoc` toolchain dependency for a single 500-byte message type fails CLAUDE.md rule 4 (Minimum Viable Architecture).
- **(c) Apache Avro.** Schema registry + binary. Overkill for single message type; introduces operational dependency on a schema registry service. Rejected.

### Regret Conditions

- Blob size grows past ~10 KB (extremely unlikely — ciphertext + small KDF params have a fixed upper bound).
- A future feature needs to ship many recovery-related records per device (we currently expect exactly 1 blob per device-identity).

### Exit Ramp

Wire-format schema-version bump enables a clean migration:
1. Bump `schemaVersion` v1 (JSON) → v2 (CBOR).
2. Adapter reads both v1 and v2 during a dual-read window (≥ 1 release).
3. Writes default to v2; v1 blobs are rewritten on first read after upgrade.
4. After dual-read window, drop v1 read path.
5. No domain-port API change — `RecoveryKeyBackup.upload(blob)` / `download(stableId)` are agnostic.

### References

- [plan.md §R5](./plan.md)
- CLAUDE.md rule 5 (wire-format versioning)
- kotlinx-serialization JSON docs

---

## R6: `NoOpRecoveryKeyBackup` adapter for non-GMS devices

### Decision

**Rejected / removed** (owner pushback round 2, 2026-06-28). The plan no longer ships a `NoOpRecoveryKeyBackup` adapter or a capability-detection selector that picks between Worker and NoOp.

A single `WorkerRecoveryKeyBackup` adapter is used universally. Recovery's availability on any given device is now expressed downstream — it is conditioned on the presence of an `AuthIdentity` (i.e. a working `AuthProvider` adapter). On a device with no identity provider (e.g. Huawei post-2019 with no GMS and no email-password adapter yet shipped), the flow never reaches `RecoveryKeyBackup` at all, so a NoOp is moot.

### Original Plan (before pushback)

Ship `NoOpRecoveryKeyBackup` adapter for Huawei and other non-GMS devices, with a capability-detection selector at startup choosing between `WorkerRecoveryKeyBackup` and `NoOpRecoveryKeyBackup`.

### Why Rejected

- **(a) Selector unnecessary.** The recovery flow is gated on having an `AuthIdentity` (the user must be signed in to know whose blob to upload/download). If no auth provider works on the device, the user never reaches the recovery setup screen, and the selector layer is dead code.
- **(b) NoOp created a misleading capability surface.** "Recovery backup adapter present" was easily misread as "recovery is configured." NoOp returned success on `upload(...)` and empty on `download(...)`, which silently broke the recovery-after-reinstall scenario without surfacing the underlying cause ("you have no auth identity on this device").
- **(c) Worker adapter works on any network-reachable device.** A future `EmailPasswordAuthProvider` or `PhoneAuthProvider` adapter on Huawei would deliver an `AuthIdentity` → recovery becomes immediately available on Huawei via the same Worker adapter. No special-casing per OEM.
- **(d) Capability detection moves entirely to `AuthAvailability` port.** The "can this device recover?" question reduces to "does any `AuthProvider` adapter return a working `AuthIdentity` on this device?" — already a concern owned by F-4.

### Regret Conditions

- A future device class arrives with identity providers (e.g. local-only biometric identity, Passkeys without cloud sync) **but no network connectivity** to reach Cloudflare. Unlikely — recovery inherently needs cloud storage to be useful.

### Exit Ramp

Re-introduce a `RecoveryKeyBackup` selector additively if such a device class appears:
1. Define a `RecoveryStorageAvailability` port mirroring `AuthAvailability`.
2. Inject a selector that picks between `WorkerRecoveryKeyBackup` and a new offline-aware adapter.
3. Existing UI flow is unchanged — the selector is invisible above the port boundary.

Cost: ~1 day, no wire-format change, no migration. Lower regret cost than shipping unused NoOp upfront.

### References

- [plan.md §R6](./plan.md)
- [spec.md owner pushback round 2 notes](./spec.md)
- F-4 Provider-Agnostic Auth (related capability separation)

---

<!-- NOVICE-SUMMARY:BEGIN -->

## Краткое объяснение для владельца (что мы здесь решили)

Этот документ фиксирует **шесть архитектурных развилок**, каждая из которых — «дверь в одну сторону» (откатить решение дорого / требует миграции данных). Для каждой записано: что выбрали, что отвергли, при каких сигналах поймём что ошиблись, и как именно будем отступать.

**R1 — Где хранить recovery-блоб (зашифрованный ключ восстановления).** Выбрали **Cloudflare Worker + R2** (object storage). Это бесплатно, провайдер-агностично (работает на Huawei), и не привязывает нас к Firebase Firestore. Отступление — на свой сервер с PostgreSQL, формат блоба не меняется, миграция через окно двойной записи.

**R2 — Как сервер понимает, что запрос «GET блоб user-X» действительно от user-X.** Выбрали вариант, где **Firebase кладёт `stableId` в JWT-токен** при первом входе через серверный SDK. Это значит — даже если кто-то украл валидный токен пользователя A, он не сможет запросить блоб пользователя B (потому что `stableId` в токене подписан криптографически). Альтернатива (доверять клиенту прислать свой stableId в теле запроса) — отвергнута из-за дыры в безопасности.

**R3 — Какой алгоритм деривации ключей (KDF).** Выбрали стандартный **HKDF-SHA256** (RFC 5869). Это общепринятый, аудируемый, и достаточный для 32-байтных ключей. Версионирование схемы блоба позволит сменить на SHA-512 или SHA-3, если понадобится.

**R4 — Параметры Argon2id (для деривации ключа из пароля восстановления).** Взяли **OWASP 2024 рекомендацию «interactive»** — 3 итерации × 64 МБ памяти. Это компромисс: безопасно для текущего железа, не вешает дешёвые телефоны. Параметры хранятся **внутри каждого блоба**, так что при их пересмотре старые блобы продолжают читаться. Следующая ревизия параметров — **2028-06**.

**R5 — Формат сериализации блоба.** Выбрали **JSON** (а не CBOR, Protobuf, Avro). Блоб маленький (~500 байт), JSON удобнее дебажить в консоли R2, и проект уже использует kotlinx-serialization JSON в других местах. Если когда-то понадобится бинарный формат — версия схемы позволит мигрировать без боли.

**R6 — Адаптер для устройств без Google Services (Huawei).** Изначально планировали `NoOpRecoveryKeyBackup` (заглушку для Huawei). **Отвергли** после второго раунда обсуждения с владельцем: на Huawei без GMS recovery не работает не потому что storage недоступен, а потому что нет identity-провайдера (нечего восстанавливать). Заглушка создавала ложное ощущение что recovery «есть, но не работает». Теперь — один универсальный Worker-адаптер, а вопрос «доступно ли восстановление на этом устройстве» полностью решается на уровне F-4 (auth-провайдеры).

**Главный паттерн всех решений:** каждое связано с конкретным **exit ramp'ом** — точкой назначения в `docs/dev/server-roadmap.md`. Мы выбираем самые дешёвые/бесплатные пути сейчас (Cloudflare Worker, Firebase Auth, R2 free tier), но каждый из них имеет документированный маршрут на свой сервер, когда мы туда поедем.

<!-- NOVICE-SUMMARY:END -->
