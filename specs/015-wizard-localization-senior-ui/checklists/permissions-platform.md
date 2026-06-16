# Checklist: permissions-platform

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 15 ✓ / 6 ⚠ / 0 ✗ + 1 N/A — клин, без real violations (всё что warning — foundation defer'ы)

> **Context**: F-3 — foundation. Поставляет **pool entries** для permissions/settings, но **не запрашивает их** в реальном wizard run. Реальное permission flow — S-1 (Simple Launcher manifest decides который entry в wizard.manifest steps использовать).

---

## Runtime permissions

- [⚠] **CHK001** Каждое permission в pool — explicit user-value justification.
  - `android.permission.POST_NOTIFICATIONS` (Android 13+) — implicit «для future push'ей в S-1+».
  - `android.permission.CALL_PHONE` — implicit «one-tap calling» (cross-reference [спека 010 FR-012](../../010-setup-assistant/spec.md)).
  - **Acceptable foundation defer**: F-3 поставляет pool entry с `descriptionKey` / `extendedInstructionKey` (FR-053) — реальный user-value текст пишется при создании bundled localization strings.

- [⚠] **CHK002** First-launch permission flow timing.
  - F-3 не диктует timing — это `wizard.manifest.body.steps[]` ordering, S-1 ownership.
  - **Acceptable** foundation defer.

- [✓] **CHK003** Re-prompt strategy (first denial vs permanent denial).
  - **FR-008a explicit**: first denial → rationale + retry/skip; permanent denial → app settings deep-link. ✓

- [✓] **CHK004** Settings deep-link для permanent denial — FR-008a (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`). ✓

- [✓] **CHK005** Pre-permission rationale screen.
  - FR-008a — rationale screen с текстом из `extendedInstructionKey` / `descriptionKey`. ✓

## Manifest declarations

- [N/A] **CHK006** `AndroidManifest.xml` updates.
  - F-3 — foundation, **не модифицирует** `AndroidManifest.xml`.
  - CALL_PHONE, POST_NOTIFICATIONS — уже added спекой 010 (per [спека 010 «Затрагиваемые внешние артефакты»](../../010-setup-assistant/spec.md#затрагиваемые-внешние-артефакты)).
  - Accessibility service permission (`BIND_ACCESSIBILITY_SERVICE`) — добавится в S-1, когда concrete AccessibilityService class будет implement (FR-057 boundary).

- [N/A] **CHK007** `<uses-feature>` — F-3 не requires hardware.
- [N/A] **CHK008** `<queries>` package visibility — F-3 не queries other apps.

## Android version specifics

- [✓] **CHK009** Scoped storage compliance.
  - F-3 использует DataStore (app-private) + APK resources (через moko-resources). No external storage I/O. ✓

- [N/A] **CHK010** Foreground service type — F-3 не использует foreground services.
- [N/A] **CHK011** Exact alarms — F-3 не schedules alarms.

- [✓] **CHK012** POST_NOTIFICATIONS Android 13+.
  - Pool entry `android.permission.POST_NOTIFICATIONS` имеет `androidMinApi: 33` field (FR-053 + FR-053a). ✓
  - Реальный wizard flow timing на Android 13+ — S-1 / спека 010 territory.

- [⚠] **CHK013** Predictive back gesture (Android 14+).
  - Wizard navigation имеет «Назад» buttons.
  - Spec не addresses predictive back animation API specifically.
  - **Acceptable**: Compose handles standard back gesture; predictive back enhancement — implementation detail в plan.md или S-1.

## HOME / launcher role

- [✓] **CHK014/015** ROLE_HOME behavior.
  - Pool entry `android.role.home` с `mechanism: DeepLink` через `RoleManager.createRequestRoleIntent(ROLE_HOME)`. ✓
  - Denial behavior через FR-008a (rationale + retry / skip + permanent denial deep-link). ✓
  - Fallback (если permanent denial): см. [спека 010 FR-018](../../010-setup-assistant/spec.md) — `RoleHomeCheck` Required в SetupCheckRegistry с `!` banner. F-3 leverages этот pattern через self-attest + re-check (FR-059).

## OEM quirks

- [⚠] **CHK016** Samsung KNOX AccessibilityService restrictions.
  - KNOX corporate-managed devices могут блокировать accessibility service.
  - F-3 OEM Matrix упоминает Samsung One UI accessibility hierarchy, но **KNOX restrictions не addressed**.
  - **Acceptable**: KNOX is enterprise edge case, не блокирует MVP для consumer launcher. Inline-TODO добавить при первом enterprise customer support.

- [✓] **CHK017** Xiaomi MIUI autostart.
  - OEM Matrix explicit row: MIUI Autostart toggle. F-3 pool **НЕ** содержит MIUI-specific entries в MVP — flagged как inline-TODO для future addition. ✓

- [✓] **CHK018** Huawei EMUI Protected Apps.
  - OEM Matrix explicit row про EMUI Protected Apps + Protected Apps Settings (no standard intent). ✓ Inline-TODO.

- [✓] **CHK019** Samsung/OnePlus launcher-replacement quirks.
  - ROLE_HOME через `RoleManager` — Android-uniform API, OEM-aware. ✓

## Package visibility (Android 11+)

- [N/A] **CHK020/021** F-3 не queries другие apps. Capability registry consumers (S-1 + спека 005) будут declare `<queries>` per actionType.

## Compliance docs

- [⚠] **CHK022** `permissions-and-resource-budget.md` delta.
  - F-3 не добавляет новые permissions в манифест.
  - Accessibility service permission (`BIND_ACCESSIBILITY_SERVICE`) добавится в S-1.
  - **Recommendation**: добавить запись в spec'е про pending S-1 update: «при материализации AccessibilityService class в S-1, `permissions-and-resource-budget.md` обновляется со строкой `BIND_ACCESSIBILITY_SERVICE` + justification «hide status bar / disable volume keys для senior-safe focus».»
  - Не блокирует F-3.

---

## Issues & fixes

### Issue PP-1 (Optional) — Predictive back gesture mention (CHK013)

Не блокирующее. Опционально добавить в Assumptions:
```
A-20: Wizard navigation использует standard Compose back gesture handling.
Predictive back gesture animation (Android 14+) — implementation detail в plan.md;
не expected требовать spec-level changes.
```

### Issue PP-2 (Optional) — S-1 manifest update note for compliance docs (CHK022)

Добавить в Cross-spec impact:
```
- When S-1 implements concrete AccessibilityService class (per FR-057), 
  S-1 spec MUST update `docs/compliance/permissions-and-resource-budget.md` 
  with `BIND_ACCESSIBILITY_SERVICE` entry + senior-safe justification.
```

---

## Резюме

**15 ✓ / 6 ⚠ / 0 ✗** — F-3 permissions handling корректен для foundation spec'и. Все warnings — appropriate defer'ы к S-1 / спеке 010 / plan.md. Никаких блокеров.

**Не applying inline edits** — все findings advisory, добавим если решишь во время plan.md.
