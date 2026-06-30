# Quickstart: F-5 Root Key Hierarchy & Owner Recovery

**Created**: 2026-06-28
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

## What this gets you (TL;DR)

The F-5 dev loop runs **entirely locally** — JVM contract tests, Android emulators, and a `wrangler dev` Worker — with no paid Firebase services required. You can iterate on the root key hierarchy, recovery flows, and the backup Worker without touching production cloud resources. Owner-managed secrets (service-account JSON for custom-claim Worker) stay on the local machine.

## Prerequisites

- **Android Studio** (latest stable), **JDK 21**.
- **Android emulator AVD API 34** — required for UI tests. **NOT API 35+** because `composeUiTest 1.7.x` fails with `InputManager.getInstance` exception on API 35/36 (see memory `reference_compose_ui_test_api_mismatch.md`).
- **Two emulators** for cross-device recovery testing: `pixel_5_api_34_A` and `pixel_5_api_34_B`, launched via the [`android-emulator`](../../.claude/skills/android-emulator/SKILL.md) skill.
- **Node.js 18+** and **npm** — for the `workers/backup/` Worker.
- **`wrangler` CLI** installed locally: `npm install -g wrangler`.
- **Firebase project access**:
  - Read-only client SDK config (`google-services.json`) — committed for dev.
  - Service-account JSON for the custom-claim Worker — owner-managed via the [`secrets-cloudflare-worker`](../../.claude/skills/secrets-cloudflare-worker/SKILL.md) skill, **never committed**.

## 1. JVM-only fast path (Phase 1 — domain tests, no emulator)

```bash
./gradlew :core:keys:test
```

**What runs**: contract tests covering roundtrip serialization, backward-compat reads, provider-agnostic key derivation, derivation determinism, and per-identity isolation.

**Fakes used**: `FakeAuthAdapter`, `FakeKeyRegistry`, `FakeRootKeyManager`, `FakeRecoveryKeyBackup`, `FakeAuthAvailability`.

**Expected runtime**: < 30 seconds.

## 2. Worker local dev path (Phase 4)

```bash
cd workers/backup
npm install
wrangler dev --port 8787
```

The Worker is now serving at `http://localhost:8787`. Test it with curl:

```bash
# POST a recovery blob
curl -X POST http://localhost:8787/backup \
  -H "Authorization: Bearer <dev-jwt-from-fake-issuer>" \
  -H "Idempotency-Key: 00000000-0000-4000-8000-000000000001" \
  -H "Content-Type: application/json" \
  -d @core/keys/src/commonTest/resources/fixtures/recovery-blob-v1-sample.json
# Expected: 200 {"status":"stored","createdAt":"..."}
```

**Dev JWT**: `wrangler dev` includes a test-only fake JWT issuer that accepts any signature. Toggle is in `wrangler.toml` under `[env.dev]` — **never enable this in production**.

## 3. Worker unit tests (vitest + miniflare)

```bash
cd workers/backup
npm test
```

**What runs**: JWT verification tests, idempotency dedup, rate-limit budget, R2 round-trip — all via miniflare mock (no real Cloudflare resources touched).

## 4. Android integration test path (Phase 2)

```bash
# Make sure wrangler dev is running on localhost:8787 (see step 2)
./gradlew :app:connectedDebugAndroidTest --tests *KeyRegistryMigration*
```

The Android build wires `BuildConfig.RECOVERY_BACKUP_WORKER_URL=http://10.0.2.2:8787` for the emulator (`10.0.2.2` is the host machine from the emulator's perspective).

## 5. UI tests (Phase 3)

```bash
./gradlew :app:connectedDebugAndroidTest --tests *Recovery*Screen*
```

**Important**: Requires AVD **API ≤ 34** (see Prerequisites). API 35+ will fail with `InputManager.getInstance` exception — known `composeUiTest 1.7.x` issue. If your AVD is API 35+, mark the relevant tasks as `[deferred-local-emulator]` and wait for an API 34 AVD.

## 6. Cross-device recovery test (SEQ-2 manual smoke)

```bash
# Start two emulators using the android-emulator skill
# On emulator A: trigger US-1 (setup with passphrase)
# On emulator B: install app, sign-in with same Firebase identity
# Verify: same encrypted config decrypted byte-equal
```

This is a **manual** smoke. The automated equivalent is `CrossDeviceRecoveryTest`, which uses `FakeRecoveryKeyBackup` with a shared in-memory `Map` between two test instances.

## 7. Performance benchmark (SC-010 Argon2id timing)

```bash
./gradlew :app:connectedBenchmarkAndroidTest --tests *Argon2BenchmarkTest*
```

Reports **P50 / P95 / P99**. Target: **≤ 3s P95** on `pixel_5_api_34`. Real-device timing on Xiaomi 11T is marked `[deferred-physical-device]`.

## 8. Common pitfalls

- **JWT expired during long-running test session** → Firebase SDK should auto-refresh; if a test pauses > 1h, restart Firebase auth.
- **`wrangler dev` port collision** with `workers/push/` dev session — both default to 8787. Use `--port 8788` for the secondary.
- **AVD name collisions** when running two emulators — use unique names per the `android-emulator` skill recipe (`pixel_5_api_34_A`, `pixel_5_api_34_B`).
- **Composable test failures with `InputManager` exception** → AVD API too high; downgrade to API 34.

## 9. Where to look when something breaks

- **Domain test failure** → check recently changed files in `core/keys/src/commonMain/`.
- **Worker test failure** → check `workers/backup/src/__tests__/` and `wrangler dev` logs.
- **Cross-device test failure** → check `stableId` consistency between emulators; Firebase identity-link should yield the same UUID.
- **Argon2 timing failure** → adjust benchmark hardware, or move to "moderate" parameters per `research.md` R4 exit ramp.

<!-- NOVICE-SUMMARY:BEGIN -->
## Объяснение для владельца (что запускать руками)

F-5 (корневые ключи и восстановление) проверяется локально, без оплачиваемых Firebase-сервисов. Вот по шагам:

1. **Быстрая проверка (логика, без эмулятора, ~30 секунд)**:
   ```
   ./gradlew :core:keys:test
   ```
   Прогоняет все доменные тесты на fake-адаптерах. Если зелёное — логика ключей и восстановления работает.

2. **Запустить локальный Worker (бэкап recovery-blob'ов)**:
   ```
   cd workers/backup
   npm install
   wrangler dev --port 8787
   ```
   Это локальный сервер на `http://localhost:8787`, имитирующий Cloudflare Worker. Не тратит ничего на проде.

3. **Тесты Worker'а (отдельно, в другом терминале)**:
   ```
   cd workers/backup
   npm test
   ```

4. **Интеграционные тесты Android** (нужен запущенный эмулятор API 34 и `wrangler dev` из шага 2):
   ```
   ./gradlew :app:connectedDebugAndroidTest --tests *KeyRegistryMigration*
   ```

5. **UI-тесты восстановления** (тоже API 34, НЕ 35+):
   ```
   ./gradlew :app:connectedDebugAndroidTest --tests *Recovery*Screen*
   ```

6. **Кросс-устройство (вручную)**: запустить два эмулятора через skill `android-emulator`, на одном сделать setup с passphrase, на втором — войти под тем же Firebase identity и убедиться что конфиг расшифровывается байт-в-байт.

7. **Замер скорости Argon2id** (должно укладываться ≤ 3 секунды на P95):
   ```
   ./gradlew :app:connectedBenchmarkAndroidTest --tests *Argon2BenchmarkTest*
   ```

Если что-то падает — смотри секцию «Common pitfalls» и «Where to look when something breaks» выше. Главное правило: эмулятор только **API 34**, не выше — иначе UI-тесты падают из-за известного бага в `composeUiTest 1.7.x`.
<!-- NOVICE-SUMMARY:END -->
