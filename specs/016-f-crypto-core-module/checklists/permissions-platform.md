# Checklist: permissions-platform — spec 016 F-CRYPTO

Run date: 2026-06-17.

F-CRYPTO — infrastructure module без user-facing UI. Большинство гейтов **N/A**, но Android-specific Keystore / OEM behaviour критичен.

## Runtime permissions

- [N/A] CHK001..005 — F-CRYPTO не requests runtime permissions.

## Manifest declarations

- [N/A] CHK006 — F-CRYPTO не declares permissions в manifest.
- [N/A] CHK007 — No `<uses-feature>` нужен (Keystore — стандартный API).
- [N/A] CHK008 — No `<queries>` (F-CRYPTO не inspects других apps).

## Android version specifics

- [x] CHK009 — Scoped storage: F-CRYPTO пишет в `/data/data/<pkg>/files/keys/` — это **app sandbox**, не external storage. Scoped storage rules не применяются (sandbox always own).
- [N/A] CHK010 — Foreground service не используется.
- [N/A] CHK011 — Exact alarms не используются.
- [N/A] CHK012 — POST_NOTIFICATIONS не нужно.
- [N/A] CHK013 — Predictive back gesture — UI-level concern, F-CRYPTO без UI.

## HOME / launcher role

- [N/A] CHK014..015 — F-CRYPTO не interacts с HOME role.

## OEM quirks

- [x] CHK016 — **Samsung Knox StrongBox** (OEM Matrix): adapter use `setIsStrongBoxBacked(true)` fallback автоматически. **TODO(physical-device)**: Samsung Knox StrongBox attestation test (in spec).
- [x] CHK017 — **Xiaomi MIUI** Keystore alias invalidation: adapter detects via exception, rethrows `KeystoreInvalidatedException` (Edge Cases + OEM Matrix). **TODO(physical-device)**: MIUI cleanup soak test.
- [x] CHK018 — **Huawei EMUI**: Keystore standard, no GMS impact. **TODO(physical-device)**: Huawei P-series Keystore test.
- [N/A] CHK019 — F-CRYPTO без UI, launcher-replacement quirks неприменимы.

## Package visibility (Android 11+)

- [N/A] CHK020..021 — F-CRYPTO не inspects других apps.

## Compliance docs

- [N/A] CHK022 — `docs/compliance/permissions-and-resource-budget.md` — F-CRYPTO не добавляет permissions, не нужен update. ⚠️ Plan-phase mention в commit message «no permissions added».

## Дополнительно для F-CRYPTO

- [x] **Android Keystore API availability** — API 6+ (Android 6.0 Marshmallow). Project target SDK 35+, min SDK presumably 23+. ✓
- [x] **TEE availability** — на устройствах без TEE (старые emulator, rooted ROM) — fail-fast `KeystoreUnavailableException`. Spec явно: «production app должен показать ясное сообщение» (Edge Cases).
- [x] **Android TV / Google TV** — часто нет lock screen, биометрия disabled. Spec: F-CRYPTO admin keys **не требуют** `setUserAuthenticationRequired(true)` (OEM Matrix). ✓

## Open issues

| # | Issue | Severity |
|---|---|---|
| O-1 | Min SDK не явно зафиксирован в spec | Minor (plan-phase) |
| O-2 | TEE attestation `KeyInfo.isInsideSecureHardware` runtime check упомянут как TODO, но без FR | Minor — plan-phase add FR-031: «Adapter SHOULD verify TEE attestation at init, log warning if software-backed». |

## Result

**5/5 actionable PASS, 17 N/A, 2 minor opens**.

**Verdict**: PASS. OEM quirks покрыты OEM Matrix секцией; Android-specific permissions/services не нужны.

---

## TL;DR простым языком

F-CRYPTO — это «библиотечка» в коде, она **не требует никаких разрешений** (не камера, не контакты, не интернет). Единственное, что Android-специфическое — использование защищённого чипа (TEE) для хранения ключей. Это работает «из коробки» на всех Android 6+. На некоторых телефонах есть **специфические баги**: Xiaomi иногда удаляет ключи при «очистке памяти», Samsung имеет более сильный чип StrongBox — спека покрывает это в отдельной таблице **OEM Matrix**. Все случаи имеют пометки `TODO(physical-device)` — это надо проверить на реальных телефонах потом, на эмуляторе не воспроизвести.
