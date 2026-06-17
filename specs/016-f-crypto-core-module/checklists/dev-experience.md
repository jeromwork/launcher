# Checklist: dev-experience — spec 016 F-CRYPTO

Run date: 2026-06-17.

## Local-test path

- [x] CHK001 — `Local Test Path` секция заполнена (не placeholder), 4 subsection'а.
- [x] CHK002 — Verification commands exact: `./gradlew :core:crypto:jvmTest`, `:testDebugUnitTest`, `:connectedDebugAndroidTest`, `:dependencies`.
- [x] CHK003 — Cycle time: jvmTest на чистом JVM (без эмулятора) — ожидаемо < 5 мин. Property tests с 1000 итераций (SC-004) — потенциально 3-5 мин, на грани.
- [x] CHK004 — Pure JVM path: основная масса (commonTest + jvmTest) без эмулятора. ✓ Только `SecureKeyStore` adapter требует эмулятора.
- [x] CHK005 — Emulator preset: `pixel_5_api_34` через skill `android-emulator`, явно назван.

## Fake adapters

- [x] CHK006 — Каждый port имеет Fake (FR-017, явно перечислены 6 fake adapter'ов).
- [x] CHK007 — Тесты не требуют real Firebase / Worker. F-CRYPTO offline (Assumption «никакой сети»).
- [x] CHK008 — DI picks fakes/reals per build variant (FR-030). Detekt-правило ловит leak (FR-018).

## Fixtures

- [x] CHK009 — Test data в checked-in fixtures: `rfc-test-vectors/*.json`, `wycheproof-subset/*.json`, `cross-platform-vectors/encryption-roundtrip-v1.json`.
- [x] CHK010 — Fixtures stable: RFC vectors deterministic; FakeRandomSource seeded (FR-017, Local Test Path).
- [x] CHK011 — Cross-version fixtures: FR-026 требует blob `schemaVersion=1` fixture для backward-compat read test.

## Cannot-test-locally gaps

- [x] CHK012 — Gaps explicitly listed: iOS target build (требует macOS), OEM Keystore quirks (Xiaomi MIUI), TEE attestation (нужен реальный device).
- [x] CHK013 — Inline TODO'шки: `// TODO(physical-mac)`, `// TODO(physical-device)` явно перечислены.
- [x] CHK014 — No silent gaps: всё что нельзя тестировать локально — explicit с reason.

## Build cycle

- [x] CHK015 — Clean-build cost: новый KMP subproject + iOS targets declarations добавят возможно ~20-40 сек к clean build из-за дополнительных Kotlin compilation targets. На грани. ⚠️ Mitigation: iOS CI отключён по умолчанию (FR-003), только targets declared.
- [x] CHK016 — Manual setup: libsodium-kmp via ionspin — Gradle dependency, no console setup.
- [x] CHK017 — No new credentials для debug. F-CRYPTO offline.

## Crash + log diagnostics

- [x] CHK018 — Logcat: ⚠️ **gap** — спека не определяет log tags. F-CRYPTO как infrastructure module — мало sensitive операций для логов; но `SecureKeyStore` фейлы должны логировать причину (`KeystoreUnavailableException`, `KeystoreInvalidatedException` из Edge Cases). Inline TODO добавить в plan-фазу.
- [x] CHK019 — Failure modes не silent: все эксепшены throw (`NotImplementedError` для stubs, конкретные exception типы для adapter'а).
- [N/A] CHK020 — No runtime feature flags в F-CRYPTO.

## Cross-developer reproducibility

- [x] CHK021 — No machine-specific paths. Gradle paths relative.
- [x] CHK022 — Onboarding < 1 page: будет в `docs/dev/crypto-review.md` (FR-023).

## Open issues

| # | Issue | Severity | Action |
|---|---|---|---|
| O-1 | Log tags / structured logging не специфицированы | Minor | Plan-фаза: добавить `CryptoLog` tag для adapter ошибок (`SecureKeyStore` обязательно). |
| O-2 | Property tests 1000 итераций — на грани 5 мин | Minor | Mitigation: параметризовать `-PkotestPropertyIterations` (FR-021); default = 100 для local dev, 1000 для CI. |

## Result

**21/22 PASS, 1 N/A, 2 minor opens**.

**Verdict**: PASS. Local-test path полноценный, fake-adapters покрывают всё, gaps explicit.

---

## TL;DR простым языком

Можно ли работать с F-CRYPTO один разработчик на своём ноутбуке, без зависимости от облака, реальных устройств и т.п.? **Да**, почти полностью:
- Основная масса тестов — JVM unit tests, считают на ноутбуке за < 5 минут.
- Все port'ы имеют «заглушки» (fake adapters) — тесты потребителей не требуют libsodium / Android Keystore.
- Что нельзя тестировать локально — **явно перечислено**: iOS build (нужен Mac), OEM-специфичные баги Keystore (нужны реальные телефоны Xiaomi/Samsung), TEE attestation (нужно реальное устройство, не эмулятор).
- Два мелких замечания: тэги для логов не определены (добавим в plan-фазу) и property tests на 1000 итераций — на границе 5 минут (можно крутить меньше итераций локально, больше в CI).
