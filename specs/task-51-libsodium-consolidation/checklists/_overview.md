# Checklist Overview — TASK-51 libsodium consolidation

**Created**: 2026-06-26
**Run**: `/speckit.clarify` step 5 (11 checklists)
**Spec**: [spec.md](../spec.md)

## Verdict per checklist

| # | Checklist | Score | Verdict | Open items |
|---|---|---|---|---|
| 1 | requirements-quality | 11/16 | ⚠️ FAIL (fixable) | 5 — [details](requirements-quality.md) — patched 2026-06-26 |
| 2 | meta-minimization | 13/13 ✓ | ✅ PERFECT | 0 |
| 3 | dev-experience | 19/22 | ✅ PASS | 2 minor (logging gaps, addressed by FR-017/FR-018) |
| 4 | domain-isolation | 16/16 ✓ | ✅ PERFECT | 0 |
| 5 | wire-format | 4/18 | ⚠️ PASS-WITH-CAVEATS | 14 (mostly N/A — schema not changing) |
| 6 | failure-recovery | 6/17 | ⚠️ PASS-WITH-CAVEATS | 11 (mostly N/A for crypto — no retries/fallbacks) |
| 7 | performance | 13/20 | ⚠️ PASS-WITH-CAVEATS | 7 (perf-checkpoint deferred to plan) |
| 8 | security | 17/24 (7 N/A) | ✅ PASS | 0 fails |
| 9 | permissions-platform | 4/22 | ⚠️ N/A heavy | 18 N/A for refactor scope |
| 10 | modular-delivery | 18/18 ✓ | ✅ PERFECT | 0 |
| 11 | backend-substitution | 14/16 | ✅ PASS | 2 N/A |

**Architecture score**: 3 perfect + 5 pass + 3 pass-with-caveats + 0 hard-fail → **READY for /speckit.plan**.

## Spec patches applied 2026-06-26 (post-checklists)

5 patches applied to address critical open items before scenarios:

1. **Removed last [NEEDS CLARIFICATION] marker** in §Local Test Path (Test fakes destination resolved per Q1 deep migration).
2. **FR-004 extended**: serialization compatibility note (require `@SerialName` annotations on wire-format types).
3. **FR-017 added**: uniform CryptoException logging contract (Logcat tag `cryptokit`, structured fields, PII gates).
4. **FR-018 added**: `CryptoException` sealed hierarchy with 5 subclasses (Aead/KeyStore/KeyDerivation/NativeLink/Serialization).
5. **SC-013 / SC-014 added**: golden-vector roundtrip test + Logcat tag negative test.

## Deferred to /speckit.plan

The following plan-time decisions are listed without blocking spec sign-off:

- ionspin lazy-bind verification (assertion in `Application.onCreate`)
- DI module consolidation shape (`cryptokitModule` location + flavor override)
- Fitness test exact patterns + file paths (Konsist rules)
- Smoke test class names (`PairingActivitySmokeTest`, `Spec011RoundtripSmokeTest`)
- APK size + cold-start baseline measurement (`perf-checkpoint.md`)
- `@SerialName` audit on existing wire-format types

## Deferred to TASK-55 (verification aggregator)

Physical-device gates that cannot be verified in our lab (only Xiaomi 11T `17f33878` available):

- Samsung One UI PairingActivity smoke
- Huawei EMUI PairingActivity smoke
- Real 2-device pairing handshake (depends on TASK-8 admin-app stub)
- Cold-start regression on non-Xiaomi arm64 devices

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** 11 чек-листов прогнаны 2026-06-26 на spec.md TASK-51. Архитектура зелёная: 3 perfect (meta-minimization 13/13, domain-isolation 16/16, modular-delivery 18/18), 5 pass, 3 pass-with-caveats (wire-format / failure-recovery / permissions-platform — низкие баллы потому что это **рефакторинг**, не feature). 0 hard-fail. Spec READY for `/speckit.scenarios`.

**Конкретика, которую стоит запомнить:**
- **Применены 5 patch'ей в spec.md после checklists**: убрали последний `[NEEDS CLARIFICATION]` marker, добавили FR-017 (logging contract), FR-018 (CryptoException sealed hierarchy с 5 подклассами), SC-013 (golden vectors roundtrip), SC-014 (Logcat tag negative test).
- **Logcat tag для всех CryptoException**: `cryptokit`. Поля: `operation`, `exceptionClass`, `messageHash` (SHA-256, 8 байт). Запрещено: raw bytes, hex >8 байт, PII.
- **5 подклассов CryptoException**: `AeadException`, `KeyStoreException`, `KeyDerivationException`, `NativeLinkException`, `SerializationException`. Sealed class — exhaustive when возможен.
- **Wire-format compatibility критически зависит от `@SerialName`** — при namespace rename без них Kotlin-сериализатор сломает byte-equal roundtrip. Plan-time verify через grep.
- **6 пунктов отложены на /speckit.plan** (ionspin lazy-bind verify, DI module shape, fitness rules, smoke test class names, perf-checkpoint, @SerialName audit).
- **4 пункта отложены на TASK-55** (Samsung One UI, Huawei EMUI, real 2-device handshake, non-Xiaomi cold-start) — нет физических устройств.

**На что смотреть с осторожностью:**
- **wire-format 4/18 и failure-recovery 6/17** выглядят как fail-scores, но это **архитектурно ожидаемо** — мы не меняем wire format (schema 1 сохраняется) и не добавляем retry/fallback paths. Большинство open items — N/A для refactor scope.
- **permissions-platform 4/22** — то же самое (это не permissions-feature). Не путать с реальным гэпом.
- **Logging contract в FR-017** — новое требование, появилось из dev-experience checklist. Без логирования невозможно дебажить production crash'и → не пропустить в имплементации.
