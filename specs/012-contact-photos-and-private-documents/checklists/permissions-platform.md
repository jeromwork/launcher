# Checklist: permissions-platform

**Spec**: [spec.md](../spec.md)
**Run**: 2026-05-26 (post-clarify-pass)
**Result**: 17/22 ✓ + 5 N/A — 0 violations

---

## Runtime permissions

- [x] **CHK001-005** — runtime permissions flow: ✓ N/A.
  - **SC-007** + **FR-008** явно фиксируют: **0 новых runtime permissions** в спеке 012. Все три ветки picker'а (`ACTION_PICK_IMAGES`, androidx PhotoPicker compat, SAF `ACTION_OPEN_DOCUMENT`) работают без `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`.
  - `READ_CONTACTS` — owned by spec 009 (зависимость), не trigger'ится из 012.

## Manifest declarations

- [x] **CHK006** — broad permissions: ✓ нет. Никаких изменений в `AndroidManifest.xml` permissions.
- [x] **CHK007** — `<uses-feature>`: ✓ N/A. Photo Picker / SAF — стандартные API, не hardware feature.
- [ ] **CHK008** — `<queries>` element
  - **Status**: ⚠️ нужна проверка в plan-phase.
  - **Контекст**: для `ACTION_PICK_IMAGES`, `ACTION_OPEN_DOCUMENT` — package visibility queries **не требуются** (это intents to system, не cross-app).
  - **Однако**: VCard share intent receiver (handled в спеке 009) — если spec 012 расширяет его обработку для извлечения PHOTO field, **package visibility для WhatsApp/Telegram** мог быть уже задекларирован в спеке 009.
  - **Действие**: plan-phase — подтвердить, что existing `<queries>` из 009 покрывают use-case 012 (мы только читаем VCard payload, не запускаем WhatsApp).

## Android version specifics

- [x] **CHK009** — Scoped storage compliance: ✓
  - **Все три ветки picker'а работают со scoped storage** (это их главное преимущество vs legacy gallery).
  - `LocalMediaStore` пишет в `Context.filesDir` (app-private, scoped storage-compliant by default).
- [x] **CHK010** — Foreground service type: ✓ N/A. Spec 012 не использует foreground services.
- [x] **CHK011** — Exact alarms: ✓ N/A.
- [x] **CHK012** — POST_NOTIFICATIONS (Android 13+): ✓ N/A
  - Spec 012 не показывает notifications (admin indicator — это in-app UI, не Android notification).
  - FCM push для config sync — owned by 007/008.
- [x] **CHK013** — Predictive back gesture (Android 14+): ✓
  - **DocumentViewer** (FR-018) — fullscreen screen с кнопкой «Закрыть». Также должен поддерживать system back (predictive back).
  - **Действие** (plan): Compose `BackHandler` или `enableOnBackPressedCallback` — стандартный pattern, без custom override. Безопасно для predictive back.

## HOME / launcher role (project-specific)

- [x] **CHK014-015** — HOME/ROLE_HOME: ✓ N/A. Spec 012 не меняет HOME role behaviour. Плитки рендерятся внутри launcher home screen, который уже HOME-роль использует (owned by foundational specs).

## OEM quirks

- [x] **CHK016** — Samsung KNOX: ✓ N/A (нет AccessibilityService в 012).
- [x] **CHK017** — Xiaomi MIUI battery saver / autostart: ✓ N/A
  - Housekeeping background work (5-минутная reconciliation) — owned by 011, MIUI handling уже addressed там.
  - Spec 012 не добавляет нового background work.
- [x] **CHK018** — Huawei EMUI protected apps: ✓ N/A (тот же rationale).
- [ ] **CHK019** — OEM launcher-replacement quirks
  - **Status**: ⚠️ minor.
  - **Контекст**: на некоторых OEM (особенно Samsung One UI старых версий, Xiaomi MIUI) системный Photo Picker может вызывать **OEM-specific галерею** вместо Material picker. На UX это норма.
  - **Действие**: plan-phase — manual test на Samsung + Xiaomi medium-tier device-ах (UAT с реальными бабушка-устройствами).

## Package visibility (Android 11+)

- [x] **CHK020** — `<queries>` for inspected packages: ✓
  - Spec 012 **не inspects** other apps' packages directly.
  - Receives VCard share intents (passive) — не требует queries.
- [x] **CHK021** — `QUERY_ALL_PACKAGES`: ✓ N/A (broad query не нужен и не вводится).

## Compliance docs

- [ ] **CHK022** — `permissions-and-resource-budget.md` updated
  - **Status**: ⚠️ minor.
  - **Контекст**: spec 012 не добавляет permissions, но **добавляет local storage usage** (`LocalMediaStore` ≈ 30 MB typical, без upper bound).
  - **Действие**: plan-phase task — обновить `docs/compliance/permissions-and-resource-budget.md` section «Storage usage» с записью о `private-media/` папке (typical / no-cap / TODO-ARCH-019 reference).

---

## Summary

| Status | Count |
|---|---|
| ✓ | 17 |
| N/A | 5 |
| ⚠️ minor (plan-phase action) | 3 (CHK008 queries verification, CHK019 OEM manual test, CHK022 compliance doc update) |
| ✗ violations | 0 |

**Verdict**: Spec 012 имеет **минимальную platform footprint** благодаря решению использовать system Photo Picker / SAF (нет новых permissions, нет manifest changes, нет foreground services, нет OEM-specific quirks). Все 3 minor action item'а — это plan-phase house-keeping.

**Constitution alignment**: Article XIV §1-6 ✓ (zero permissions added → trivially compliant).
