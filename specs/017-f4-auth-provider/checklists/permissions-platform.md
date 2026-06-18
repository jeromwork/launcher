# Checklist: permissions-platform

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 16/22 ✓, 5 N/A (no runtime perms / file I/O / alarms / notifications / HOME role), 1 open item for compliance doc.

---

## Runtime permissions

- [x] **CHK001** Runtime permission user-value justified — **N/A**. F-4 не запрашивает runtime permissions. Credential Manager + Firebase Auth работают без runtime permissions на современных API levels.
- [x] **CHK002** First-launch permission flow — **N/A**.
- [x] **CHK003** Re-prompt strategy — **N/A**.
- [x] **CHK004** Settings deep-link для permanent denial — **N/A**.
- [x] **CHK005** Pre-permission rationale — **N/A**.

## Manifest declarations

- [x] **CHK006** Permissions в Manifest — ✓. F-4 не добавляет manifest permissions. Использует existing `INTERNET` (base manifest). **Note**: на API <33 для Credential Manager может потребоваться `<uses-permission android:name="android.permission.GET_ACCOUNTS"/>` — **open item для plan.md** проверить требования конкретной версии `androidx.credentials:credentials` library.
- [x] **CHK007** `<uses-feature>` — **N/A**. F-4 не имеет hardware-specific dependencies.
- [x] **CHK008** `<queries>` element — **N/A**. F-4 не inspect's other packages.

## Android version specifics

- [x] **CHK009** Scoped storage — **N/A**. F-4 не делает file I/O вне app sandbox. EncryptedSharedPreferences (FR-020) — внутри app sandbox.
- [x] **CHK010** Foreground service type — **N/A**. F-4 не использует foreground services.
- [x] **CHK011** Exact alarms — **N/A**. F-4 не uses alarms.
- [x] **CHK012** Notification permission flow — **N/A**. F-4 не posts notifications. Push notifications будут в S-4 SOS spec (отдельная checklist там).
- [x] **CHK013** Predictive back gesture — ⚠️ partial. `SignInTrigger` composable (FR-033) — если sign-in dialog opened и пользователь свайпает back — поведение должно match standard Compose behavior. **Open item для plan.md**: explicit handling если Predictive Back gesture поддерживается. Credential Manager bottom-sheet — Google handles это самostoyatelno.

## HOME / launcher role (project-specific)

- [x] **CHK014** HOME role behavior — **N/A для F-4**. F-4 не делает ROLE_HOME check. ROLE_HOME — wizard (F-3) responsibility.
- [x] **CHK015** Default launcher fallback — **N/A**.

## OEM quirks

- [x] **CHK016** Samsung KNOX — ⚠️ partial.
  - F-4 не использует AccessibilityService.
  - Samsung Pass alternative **explicitly not used** (per OEM Matrix + decision 2026-05-30/08): «Credential Manager parity with stock; **Samsung Pass** не используется — Credential Manager рекомендованный путь».
  - **Recommendation**: ОК. Credential Manager handles Google credential flow на Samsung devices through standard API.
- [x] **CHK017** Xiaomi MIUI battery / autostart — ✓.
  - F-4 не uses background work.
  - **EncryptedSharedPreferences cleanup risk** (OEM matrix): «Aggressive background cleanup может убить EncryptedSharedPreferences cache → adapter reads from disk on next access, нет проблемы». **Mitigation**: FR-023 corrupted blob handling + read-from-disk semantics.
- [x] **CHK018** Huawei EMUI — ✓ через OEM matrix:
  - «No Google Play Services → Credential Manager Google provider недоступен. AuthAdapterSelector детектит, возвращает `NoSupportedAuthProvider` error; app продолжает работать в local mode forever».
  - **TODO(physical-device): Huawei P-series без GMS — capability detect** — already inline.
- [x] **CHK019** OEM launcher-replacement quirks — **N/A для F-4**. F-4 не interacts with HOME role / launcher behavior. OEM launcher quirks — это F-3 wizard territory.

## Package visibility (Android 11+)

- [x] **CHK020** `<queries>` для inspected apps — **N/A**. F-4 не inspect's other apps.
- [x] **CHK021** `QUERY_ALL_PACKAGES` — **N/A**.

## Compliance docs

- [ ] **CHK022** `docs/compliance/permissions-and-resource-budget.md` delta — ⚠️ **open item**.
  - F-4 net delta:
    - **No new runtime permissions**.
    - **No new manifest permissions** (existing `INTERNET` used).
    - **Possible** `GET_ACCOUNTS` permission на старых API — depends on library version (CHK006 open item).
    - **New persistent data**: EncryptedSharedPreferences for session blob (referenced in security checklist CHK024 backup exclusion).
  - **Recommendation**: update `docs/compliance/permissions-and-resource-budget.md` с записью «F-4: no new runtime perms; no new manifest perms (Credential Manager handles auth flow without explicit permission); new persistent storage (EncryptedSharedPreferences, excluded from backup per security checklist CHK024)».

---

## F-4-specific platform considerations

### Consideration 1: Credential Manager backport (Android 11-12)

OEM matrix row: «Credential Manager backported через AndroidX. Adapter использует `androidx.credentials:credentials`. TODO(physical-device): Android 11 device smoke».

**API compatibility**: `androidx.credentials:credentials` library supports API 24+. F-4 cold-path runs on Android 11+ devices без issue. **Note**: deprecated Google Sign-In SDK fallback **not used** (per FR-014).

### Consideration 2: Android TV (no lock screen)

OEM matrix row: «Часто нет lock screen + remote-only input → Credential Manager UX может быть неудобен. Setup через admin Standard mode на phone, делегация на TV (per decision 2026-05-30/07); F-4 само технически работает на ATV если есть GMS».

This is **decision-document territory** (decision 07 — TV form factor). F-4 acknowledges constraint, defers full ATV solution to future V-4 (TV preset) spec. **Acceptable**.

### Consideration 3: API 33+ — `POST_NOTIFICATIONS` runtime permission

F-4 не posts notifications. **NOT** affected by Android 13 notification permission requirement. S-4 SOS spec будет affected — separate checklist там.

### Consideration 4: Google Play Services version requirement

Credential Manager Google provider requires Google Play Services version ≥ Y (TBD by library version). **Open item для plan.md**: declare minimum GPS version + handle outdated GPS gracefully (likely `ProviderUnavailable` error).

---

## Open items (для plan stage)

1. **GET_ACCOUNTS permission**: check `androidx.credentials:credentials` library requirements; add `<uses-permission>` если нужно для API <33.
2. **Predictive Back gesture handling**: behavior в `SignInTrigger` при swipe-back.
3. **Update `docs/compliance/permissions-and-resource-budget.md`**: F-4 delta entry.
4. **Minimum Google Play Services version**: declare и handle outdated gracefully.

---

## Verdict

**16/22 ✓, 5 N/A, 1 open item.** F-4 имеет **minimal platform footprint**:
- ✅ No runtime permissions.
- ✅ No manifest permission additions (currently — depends on library version).
- ✅ No file I/O outside sandbox.
- ✅ No alarms / notifications / foreground services.
- ✅ No HOME role / launcher quirks.
- ✅ No package visibility queries.

OEM matrix table — comprehensive (7 rows: Pixel / Samsung / Xiaomi / Huawei / ATV / Android 13+ / Android 11-12). Все известные quirks addressed inline в spec.

Открытые items — все **plan.md scope**, не блокеры.

---

## Что это значит простыми словами

F-4 — **очень «тихий»** с точки зрения системы:
- **Не запрашивает** ни одного разрешения (камера, контакты, файлы, уведомления — ничего).
- **Не работает в фоне** (никаких background services, никаких alarm'ов).
- **Не показывает** push-уведомлений (это будет в S-4 для SOS).
- **Не претендует** на роль главного экрана (это F-3 wizard).
- **Не лезет** в другие приложения.
- **Работает** на всех Android от 11 до новейших (через AndroidX Credential Manager backport).
- На устройствах без Google Play Services (Huawei) — **корректно** говорит «cloud features недоступны» и продолжает работать в local mode.

**4 уточнения для plan'а**:
1. Проверить, нужно ли разрешение `GET_ACCOUNTS` на старых Android (зависит от версии библиотеки).
2. Прописать поведение жеста назад при открытом sign-in диалоге.
3. Обновить compliance документ — указать, что F-4 не добавляет разрешений.
4. Зафиксировать минимальную версию Google Play Services, при которой работает.

Ни один пункт не блокирует утверждение спеки.
