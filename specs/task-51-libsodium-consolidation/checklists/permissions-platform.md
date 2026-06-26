# Checklist: permissions-platform — TASK-51 libsodium consolidation

**Spec**: [`specs/task-51-libsodium-consolidation/spec.md`](../spec.md)
**Generated**: 2026-06-26
**Skill source**: [`.claude/skills/checklist-permissions-platform/SKILL.md`](../../../.claude/skills/checklist-permissions-platform/SKILL.md)

---

## Scope assessment (before walking gates)

TASK-51 — это **internal crypto-refactoring** (миграция lazysodium-android → ionspin libsodium-kmp). Спека:

- **НЕ добавляет** runtime-permissions.
- **НЕ меняет** AndroidManifest.xml `<uses-permission>` / `<uses-feature>` / `<queries>` блоки.
- **НЕ трогает** scoped storage, foreground services, exact alarms, POST_NOTIFICATIONS, predictive back.
- **НЕ меняет** HOME / ROLE_HOME behaviour.
- **НЕ добавляет** package visibility queries.
- **Затрагивает** OEM behaviour косвенно: `UnsatisfiedLinkError` crash проявлялся на arm64 (Xiaomi 11T verified, Samsung/Huawei ожидаемо). Spec **устраняет** этот crash, не создаёт новых OEM-divergence (Section "OEM Matrix" в spec.md, lines 236-247).
- **Не затрагивает** `docs/compliance/permissions-and-resource-budget.md` — нет permission delta.

Большинство CHK-items получают вердикт **N/A** именно потому что спека по своему scope'у не пересекается с permission/manifest plane'ом. Несколько items получают **PASS** там, где спека явно затрагивает OEM matrix (CHK016-019).

---

## Runtime permissions

- [N/A] CHK001 Каждый runtime-permission имеет обоснование — спека не добавляет permissions.
- [N/A] CHK002 First-launch permission flow — нет permission prompts.
- [N/A] CHK003 Re-prompt strategy — нет permissions.
- [N/A] CHK004 Settings deep-link для permanent denial — нет permissions.
- [N/A] CHK005 Pre-permission rationale screen — нет permissions.

## Manifest declarations

- [N/A] CHK006 `<uses-permission>` listed без broad scope — спека не меняет manifest permissions. Note: existing manifest sохраняется без правок (spec scope = только Kotlin + gradle).
- [N/A] CHK007 `<uses-feature>` для hardware — спека не добавляет hardware dependencies. ionspin libsodium-kmp работает на стандартном Android ABI без требований hardware (`KeyStore.AndroidKeyStore` остаётся неизменно).
- [N/A] CHK008 `<queries>` для Android 11+ package visibility — спека не инспектирует другие пакеты.

## Android version specifics

- [N/A] CHK009 Scoped storage compliance — нет file I/O changes.
- [N/A] CHK010 Foreground service `type` (Android 14+) — спека не вводит foreground service.
- [N/A] CHK011 Exact alarms (`SCHEDULE_EXACT_ALARM`) — спека не использует alarms.
- [N/A] CHK012 POST_NOTIFICATIONS (Android 13+) — спека не отправляет notifications. Note: TASK-51 не пересекается с FCM push (S-7 / spec 019), которое уже владеет своим notification flow.
- [N/A] CHK013 Predictive back (Android 14+) — спека не вводит UI screens с override back.

## HOME / launcher role (project-specific)

- [N/A] CHK014 HOME/ROLE_HOME denied behaviour — спека не взаимодействует с ROLE_HOME. PairingActivity открывается standalone (Settings → "Привязать админа"), не зависит от launcher role.
- [N/A] CHK015 Default-launcher fallback — спека не требует launcher role.

## OEM quirks

- [PASS] CHK016 Samsung KNOX restrictions on AccessibilityService — спека не использует AccessibilityService. KNOX irrelevant.
- [PASS] CHK017 Xiaomi MIUI aggressive battery saver / autostart — спека не зависит от background work. Spec011SmokeDebugActivity + PairingActivity — foreground-only flows. Crash fix верифицирован именно на Xiaomi 11T (MIUI V125, Android 11) — SC-001.
- [PASS] CHK018 Huawei EMUI protected-apps — спека не зависит от background work. Note in OEM Matrix (spec.md line 244-245): Huawei verification deferred (`TODO(physical-device)` → TASK-55) поскольку нет Huawei устройства; expected behaviour = единообразное отсутствие crash'а.
- [PASS] CHK019 OEM launcher-replacement quirks (Samsung One UI, OxygenOS) для HOME-related features — N/A in essence: спека не трогает HOME/launcher replacement. Single line in OEM Matrix покрывает Samsung One UI ожидание (crash fix применяется тождественно).

## Package visibility (Android 11+)

- [N/A] CHK020 `<queries>` для инспекции других apps — спека не инспектирует apps.
- [N/A] CHK021 `QUERY_ALL_PACKAGES` justification — спека не использует.

## Compliance docs

- [N/A] CHK022 `docs/compliance/permissions-and-resource-budget.md` delta — нет permission delta для записи. Spec не вносит ни добавлений, ни удалений permissions. Documentation update не требуется. (Если в будущем permission-delta появится из подzadач — это будет отдельный spec/PR.)

---

## Summary

- **Total CHK items**: 22
- **[x] PASS**: 4 (CHK016-019 — OEM quirks, addressed in spec.md "OEM Matrix" section)
- **[ ] FAIL**: 0
- **[N/A]**: 18 (CHK001-015, CHK020-022 — outside spec scope: pure crypto refactor)
- **Open items**: none

## Verdict

**PASS**. TASK-51 — это infrastructure-layer crypto migration без касания к permission/manifest/platform-API surfaces. Единственный permission-platform-relevant аспект — OEM crash behaviour (arm64 + JNA eager-bind), который спека **устраняет** через переход на ionspin lazy-bind, явно адресован в Section "OEM Matrix" (Xiaomi verified, Samsung/Huawei deferred с inline `TODO(physical-device)` → TASK-55). Compliance docs update не требуется (нет permission delta).

## Open items

(none)
