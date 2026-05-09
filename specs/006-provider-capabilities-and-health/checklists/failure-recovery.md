# Checklist: failure-recovery — спек 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 · **Score:** 5 ✓ / 8 ◐ / 4 N/A / 0 ✗

Source: Article III §4 [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md).

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## Error categories

- [~] **CHK001** Each FR with external action lists ≥1 failure mode.
  - **Failure modes covered:** FR-005 (DataStore corrupted → empty snapshot fallback). FR-009 (unknown iconId namespace → Placeholder).
  - **Failure modes missing:**
    - FR-002 (PACKAGE broadcasts): broadcast не пришёл / system-throttled.
    - FR-018 (Health collectors): NetworkCallback не вызван / AudioManager throws / ContentObserver не fires.
    - FR-010 (BundledIconStorage): drawable resource missing в APK для `bundled:<name>`.
    - FR-026/027 (banner action buttons): system Settings intent не доступен / `setStreamVolume` отказался (DND restrictions).
  - **Fix:** добавить **FR-049**: «System failures (broadcast missed, callback not fired, system API throws) MUST not crash app. Snapshot uses last-known DataStore projection; missing data field defaults документированы (например `connectivity = None` if unknown, `batteryPercent = 0` if unknown). Periodic rebuild on `RESUMED` recovers from missed events».
- [~] **CHK002** User-visible behavior specified for each failure.
  - **Finding:** FR-027 кнопка «Включить звук» — если `setStreamVolume` отказался (DND restrictions), баннер не исчезнет (по FR-029), но пользователь не получает feedback «почему».
  - **Fix:** добавить **FR-050**: «Banner action button failures MUST surface to user via toast/snackbar with localised message (e.g. «Не удалось — проверьте настройки звука»). Action button MUST remain enabled for retry».
- [~] **CHK003** No silent failures of user-initiated actions.
  - Связано с CHK002. После FR-050 fix — passes.

## Fallbacks

- N/A **CHK004** Fallback depth / cycle protection.
  - Спек 006 не вводит fallback chains. Action fallback — спек 005.
- [x] **CHK005** Fallback specified by data, not hardcoded.
  - FR-026 «system Settings intent или wireless settings fallback» — это OS-level fallback, acceptable hardcode.
- [~] **CHK006** Fallback-also-fails terminal behavior.
  - **Finding:** не задокументировано (e.g., если **обе** intent variants для airplane settings не доступны).
  - **Fix:** в FR-049 / FR-050 добавить «If both primary action and fallback fail, surface toast «функция недоступна на этом устройстве», banner stays visible (state has not changed)».

## Retries

- N/A **CHK007** Retry behavior.
  - Спек 006 не имеет ретраев (event-driven, no network ops).
- N/A **CHK008** Infinite retry loops.
- [x] **CHK009** Idempotency.
  - Snapshot rebuild idempotent (reads current state). DataStore writes idempotent (overwrite). Banner button actions idempotent (`setStreamVolume(50%)` repeated = same effect).

## Offline / degraded modes

- N/A **CHK010** Offline behavior.
  - Спек 006 не читает с network (NFR-N01).
- N/A **CHK011** Stale data TTL.
  - `Health.lastSeen` существует, но TTL для consumers — задача спека 007 (Firestore export staleness rules). В спеке 006 consumers — local debug screens, no TTL needed.

## Permissions denied

- [x] **CHK012** Behavior when permission denied first time.
  - `ACCESS_NETWORK_STATE` — normal permission (auto-granted, no runtime dialog). NFR-008 explicit: zero new runtime permissions. No deny path.
- N/A **CHK013** Permanent denial.
  - Нет runtime permissions.

## Recovery from invalid state

- [~] **CHK014** Persistent state corruption recovery.
  - **Capability/Health snapshot corruption:** Edge Cases «DataStore-проекция повреждена → fallback в empty snapshot, in-memory rebuild от первого RESUMED». ✓
  - **LauncherSettings corruption:** не задокументировано explicit. По аналогии должно быть «fallback to default values (banners ON for simple-launcher, OFF otherwise)».
  - **Fix:** добавить **FR-051**: «`LauncherSettings` deserialization failure (corrupted DataStore file) MUST fallback to preset-defaults (per FR-032), not crash. Single recovery write rebuilds the file».
- [x] **CHK015** No crash-and-restart as recovery strategy.
  - Edge Cases explicitly «fallback в empty snapshot, не crash».

## Diagnostics

- [~] **CHK016** Failures observable, no PII.
  - **Finding:** спек не упоминает diagnostic events / logging strategy.
  - **Fix:** добавить **FR-052**: «Each recovery path (DataStore corruption, missing fixture drawable, banner action failure, system callback timeout) MUST emit structured log event with category (e.g. `corruption`, `missing_resource`, `system_api_failure`) and zero PII. Categories enable rate measurement in spec 007 telemetry».
- [~] **CHK017** Failures aggregated by category.
  - Связано с CHK016. После FR-052 — categorical structure exists. Aggregation = task в спеке 007 (Firebase Crashlytics + custom events).

---

## Open items для spec.md

**Add to spec.md before speckit-plan:**

- **FR-049** System callback / broadcast / API failure tolerance (CHK001).
- **FR-050** Banner action button failure surfaces to user via toast (CHK002, CHK003).
- **FR-051** LauncherSettings corruption fallback to preset defaults (CHK014).
- **FR-052** Structured log events for recovery paths, no PII, categorised (CHK016, CHK017).

## Itog

- 5 PASS, 8 PARTIAL (4 fixable добавлением FR), 4 N/A (no network/retries/permissions in spec 006), 0 hard FAIL.
- **Verdict:** спек **достаточно покрывает базовые failure paths** (DataStore corruption, unknown namespace), но **системные API failure и user feedback при button failure** — пробелы. 4 FR закрывают всё.

---

## TL;DR для нетехнического читателя

Этот checklist проверяет: **что произойдёт, если что-то сломается**. Это важно потому что у пожилого человека нет возможности сообщить разработчику о баге через issue tracker — приложение должно либо работать, либо понятно объяснять что не получилось.

**Что хорошо в спеке:**
- Если файл с сохранённым снапшотом повреждён — приложение не падает, просто пересобирает снапшот заново при следующем открытии.
- Если иконка имеет незнакомый формат — показывается заглушка вместо иконки, не crash.
- Никаких бесконечных циклов повторных попыток (которые разряжали бы батарею).

**Что нужно дополнить:**
- Что если кнопка «Включить звук» не сработала (например, телефон в режиме «не беспокоить» с системными ограничениями)? Сейчас баннер просто не исчезнет, и пожилой не поймёт почему. Нужно показать всплывающее сообщение «не удалось».
- Что если файл настроек повреждён (а не файл снапшота)? По аналогии — вернуться к настройкам по умолчанию.
- Что если системное событие (приложение установили, громкость изменилась) пришло, но мы его пропустили? Нужно явно сказать: при следующем возврате в лаунчер пересоберём снапшот заново и наверстаем.
- Что нужны **метки** для каждого случая «что-то пошло не так» (просто категории, без приватных данных). Это позволит в спеке 007 (когда подключим облако) собирать статистику «у скольких бабушек повреждается файл настроек в неделю» — это сигнал что нужно копать глубже.

Это всё мелкие правки, не пересмотр архитектуры.
