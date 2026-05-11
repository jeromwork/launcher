# Checklist: permissions-platform — спек 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 · **Score:** 13 ✓ / 2 ◐ / 4 N/A / 0 ✗

Source: Article XIV + Article III §3 [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md).

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## Permission / API surface introduced by spec 006

| # | Surface | Permission needed | Notes |
|---|---------|-------------------|-------|
| P1 | `ConnectivityManager.NetworkCallback` | `ACCESS_NETWORK_STATE` (normal, auto-granted) | Health collector |
| P2 | `PackageManager.queryIntentActivities` / `getPackageInfo` for known providers | `<queries>` element (Android 11+ visibility) | **CRITICAL** for FR-001/002 |
| P3 | `Intent.ACTION_BATTERY_CHANGED` sticky | none | Health collector |
| P4 | `AudioManager.setStreamVolume(STREAM_RING)` | none (system streams via standard AudioManager) | FR-027 banner action |
| P5 | `Settings.Global.AIRPLANE_MODE_ON` ContentObserver | none (public read) | Health + FR-026 banner |
| P6 | `Settings.System.VOLUME_CHANGED` ContentObserver | none | Health collector |
| P7 | `Settings.ACTION_AIRPLANE_MODE_SETTINGS` startActivity | none | FR-026 banner action |
| P8 | `ProcessLifecycleOwner.RESUMED` (androidx.lifecycle) | none | All collectors |

---

## Runtime permissions

- [x] **CHK001** Each runtime permission justified.
  - NFR-008 explicit «Zero new Android runtime permissions». `ACCESS_NETWORK_STATE` is normal permission (auto-granted, no dialog). N/A для остальных.
- N/A **CHK002** First-launch permission flow.
- N/A **CHK003** Re-prompt strategy.
- N/A **CHK004** Settings deep-link for permanent denial.
- N/A **CHK005** Pre-permission rationale screen.

## Manifest declarations

- [x] **CHK006** Required permissions listed, no broad ones.
  - Only `ACCESS_NETWORK_STATE` (already declared from prior specs likely). NFR-008 forbids `MODIFY_AUDIO_SETTINGS`, `CALL_PHONE`, `SEND_SMS`, `ACCESS_NOTIFICATION_POLICY`.
- [x] **CHK007** `<uses-feature>` matches hardware needs.
  - Спек не требует hardware features. Existing declarations not touched.
- [~] **CHK008** `<queries>` for package inspection (Android 11+).
  - **CRITICAL Finding:** FR-001/FR-002 inspect provider packages via `PackageManager`. On Android 11+ **without `<queries>` declaration** `getInstalledApplications` returns empty / `getPackageInfo` returns NameNotFoundException for unknown packages. **`Capability.available` will be `false` for everyone**, breaking core feature.
  - **Fix:** добавить **FR-053**: «AndroidManifest MUST declare `<queries>` element with explicit `<package android:name=…>` entries for each known provider (`com.whatsapp`, `com.whatsapp.w4b`, `org.telegram.messenger`, `org.telegram.plus`, others per spec 005 ProviderId mapping table). MUST NOT use `QUERY_ALL_PACKAGES` (Play policy violation)».

## Android version specifics

- [x] **CHK009** Scoped storage (Android 10+).
  - No file I/O outside app-private DataStore. ✓
- [x] **CHK010** Foreground service type (Android 14+).
  - NFR-N03 explicit «MUST NOT run a Foreground Service».
- [x] **CHK011** Exact alarms.
  - Спек не использует `AlarmManager`. WorkManager вынесен в спек 013.
- [x] **CHK012** POST_NOTIFICATIONS (Android 13+).
  - Спек не использует системные нотификации. FR-026 explicit «НЕ системные нотификации, банеры внутри лаунчера».
- [x] **CHK013** Predictive back gesture (Android 14+).
  - Спек не вводит back-overriding screens. Banner = in-app overlay.

## HOME / launcher role

- [x] **CHK014** HOME/ROLE_HOME behavior when denied.
  - Спек 006 не требует HOME role. Banners видны только при активном использовании app — что OK.
- [x] **CHK015** Default launcher fallback.
  - Не применимо.

## OEM quirks

- N/A **CHK016** Samsung KNOX / AccessibilityService.
  - Спек не использует AccessibilityService.
- [x] **CHK017** Xiaomi MIUI aggressive battery saver / autostart.
  - Спек 006 не имеет background work (только event-driven listeners когда process alive). Aggressive process kill ограничивает live observers, но при следующем RESUMED snapshot пересоберётся (graceful degradation).
- [x] **CHK018** Huawei EMUI protected apps.
  - Same as CHK017.
- N/A **CHK019** OEM launcher-replacement quirks.
  - Спек 006 не вводит HOME role logic.

## Package visibility

- See **CHK008** above (FR-053 covers this too).
- [x] **CHK021** `QUERY_ALL_PACKAGES` not used.
  - FR-053 explicit forbids.

## Compliance docs

- [~] **CHK022** [`docs/compliance/permissions-and-resource-budget.md`](../../../docs/compliance/permissions-and-resource-budget.md) updated.
  - **Finding:** spec ссылается на файл (References §8), но не fixes explicit task на update.
  - **Fix:** добавить **FR-054**: «In same PR `docs/compliance/permissions-and-resource-budget.md` MUST be updated с delta для спека 006: `<queries>` entries (full list), `ACCESS_NETWORK_STATE` (verify already declared), zero new runtime permissions added, zero foreground services added, manifest delta documented».

---

## Open items для spec.md

**Add to spec.md before speckit-plan:**

- **FR-053** `<queries>` declaration for known provider packages (CHK008, CHK020). **CRITICAL — без этого FR-001/002 не работают на Android 11+.**
- **FR-054** Compliance doc update task (CHK022).

## Itog

- 13 PASS, 2 PARTIAL (оба fixable добавлением FR), 4 N/A, 0 hard FAIL.
- **Verdict:** спек **очень дисциплинирован** по permissions — zero runtime permissions added, zero Foreground Service, zero exact alarms, zero broad queries. Один **critical gap** — `<queries>` declaration для Android 11+ package visibility, без которого Capability snapshot не работает на современных устройствах. Закрыть FR-053.

---

## TL;DR для нетехнического читателя

Этот checklist проверяет: **не запрашивает ли приложение лишних разрешений**, не нарушает ли правила Google Play и не сломается ли на новых версиях Android.

**Что хорошо в спеке:**
- Никаких новых разрешений-«страшилок» (не просим звонить, отправлять SMS, видеть контакты, обходить «не беспокоить»). Бабушка не увидит ни одного нового всплывающего окна «приложение хочет...».
- Никаких служб в фоне, которые жгут батарею.
- Не пытаемся обойти системные ограничения «не беспокоить» — уважаем выбор пользователя.

**Один критический пробел:**
- В современных версиях Android (11 и новее) приложение **по умолчанию не видит** какие приложения установлены — это защита приватности. Чтобы лаунчер мог проверить «есть ли WhatsApp» — нужно явно перечислить в манифесте список пакетов, которые мы хотим проверять. Это **обычная практика** (не нарушение правил Google), но забыть про неё легко. Без этой строчки в манифесте функция «показать какие приложения установлены» работать не будет на новых телефонах. Добавил как обязательное требование (FR-053).

**Что нужно сделать в этом же PR:**
- Обновить файл `permissions-and-resource-budget.md` (это документ, который ведёт учёт всех разрешений и ресурсов проекта). Добавить туда новые `<queries>` строки и подтвердить что мы не добавили никаких новых разрешений-«страшилок» (FR-054).
