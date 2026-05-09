# Checklist: security — спек 006

**Spec:** [`spec.md`](../spec.md) · **Run:** 2026-05-09 · **Score:** 15 ✓ / 2 ◐ / 7 N/A / 0 ✗

Source: [OWASP MASVS](https://mas.owasp.org/MASVS/), Article XIV [`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md).

Legend: `[x]` pass · `[~]` partial · `[ ]` fail · `N/A` not applicable.

---

## Data flows in spec 006

**Stored data (DataStore app-private dir, FBE-encrypted at rest by Android):**
- `Capability` snapshot
- `Health` snapshot
- `LauncherSettings`

**Network:** **none** (NFR-N01).

**Intents OUT:** FR-026 (system Settings airplane mode), FR-027 (no intent — local AudioManager call).

**Intents IN / external surfaces:** **none** (no new exported activities/services/receivers/providers, no deep links).

---

## Data at rest (MASVS-STORAGE)

- [~] **CHK001** No PII in clear-text storage.
  - **Finding:** `Capability` data — public software metadata (provider name, icon, version) — **не PII**. `Health` data — device telemetry (battery, connectivity, ringer volume, lastSeen, appVersion) — **не PII в строгом смысле**, но классифицируется как «device metadata» в GDPR/Russian law. `LauncherSettings` — boolean toggles, не PII.
  - DataStore is app-private + FBE-encrypted → защищено по умолчанию.
  - **Fix:** добавить **FR-056** в spec — explicit classification: «Capability/Health/LauncherSettings classified as **non-PII device telemetry** (no name, phone, email, contact ref, location). Stored in app-private DataStore with default Android FBE encryption. No additional encryption required for спека 006 (will be revisited in спеке 007 при cloud export для admin transparency)».
- N/A **CHK002** Sensitive data (auth tokens) → EncryptedSharedPreferences/Keystore.
  - Спек не хранит auth tokens / biometric refs.
- [x] **CHK003** Cache files TTL + clear-on-uninstall.
  - DataStore auto-cleared on uninstall. Snapshot always-fresh through event-driven rebuild — no TTL needed. Settings persistent, no TTL.
- [x] **CHK004** Logging excludes PII.
  - FR-052 explicit «structured log event with category and **zero PII**».

## Data in transit (MASVS-NETWORK)

- N/A **CHK005** HTTPS/TLS.
  - Спек 006 не делает network calls (NFR-N01).
- N/A **CHK006** Cert pinning.

## Authentication / Authorization (MASVS-AUTH)

- [x] **CHK007** Privileged actions justified.
  - FR-027 `AudioManager.setStreamVolume(STREAM_RING, ...)` — public API on standard streams, no permission required.
- [x] **CHK008** No security-by-obscurity.
  - No hidden URLs / undocumented intents.

## Platform interaction (MASVS-PLATFORM)

- [x] **CHK009** Exported components justified.
  - Спек **не вводит** новые exported activities/services/receivers/providers. All collectors are internal Android classes.
- [x] **CHK010** Intents to other apps explicit / setPackage.
  - FR-026 `Settings.ACTION_AIRPLANE_MODE_SETTINGS` — system intent, target = OS Settings app. setPackage not needed (target = system).
- N/A **CHK011** Deep links validated.
  - Спек не вводит deep links.
- [x] **CHK012** Intent extras from external apps size-bounded / type-checked.
  - Спек НЕ принимает intents from external apps. Banner buttons — internal only.
- N/A **CHK013** Exported ContentProvider.
  - Спек не вводит ContentProvider.
- N/A **CHK014** WebView.
  - Спек не использует WebView.

## Permissions (Article XIV)

- [x] **CHK015** Each permission justified by explicit FR.
  - `ACCESS_NETWORK_STATE` — justified by FR-018 (Health.connectivity observation).
  - `<queries>` declarations — justified by FR-001/002 (Capability detection per FR-053).
- [x] **CHK016** No permission for "future use".
  - Zero new permissions. NFR-N02 explicit.
- N/A **CHK017** Fallback for denied permissions.
  - `ACCESS_NETWORK_STATE` is normal permission, не runtime, не denied.
- [x] **CHK018** Updates `permissions-and-resource-budget.md` planned.
  - FR-054 explicit.

## Privacy (Article XIV §3, §4)

- [x] **CHK019** No hidden behavioural data collection.
  - `Health.lastSeen` is behavioural metadata, but **local-only** в спеке 006, не передаётся куда-либо. Спек 007 при экспорте в Firestore должен показать privacy policy.
- [x] **CHK020** Local-first preferred.
  - NFR-N01 explicit no network in спеке 006.
- N/A **CHK021** Data leaves device → notice + opt-in.
  - Data does NOT leave device в спеке 006.
- [x] **CHK022** Data minimisation.
  - Capability fields: все нужны (FR-001, US-1, US-2). `versionCode` — forward-compat for feature-detection.
  - Health fields: `lastSeen` имеет no current consumer в спеке 006 (forward-compat для admin UI спека 009). Acceptable as wire-format one-way door fixation. iconSha256 same — forward-compat для cache invalidation спека 007.
  - **Disposition:** small forward-compat overhead acceptable to avoid wire-format migration later.

## Build hardening

- [x] **CHK023** No debug flags in release.
  - FR-052 logging — categorized, zero PII, suitable for release. No verbose logging.
- [~] **CHK024** Backup rules для new persistent data.
  - **Finding:** спек не указывает Android Auto Backup policy для трёх новых DataStore файлов.
  - **Critical decisions:**
    - `capability_snapshot_json` — per-device transient (derived from installed packages). **EXCLUDE from backup** (old snapshot misleads UI on new device).
    - `health_snapshot_json` — per-device transient telemetry. **EXCLUDE from backup**.
    - `settings_datastore.preferences` — user banner toggles. **INCLUDE in backup** (user wants settings preserved on new device).
  - **Fix:** добавить **FR-057**: «Android Auto Backup rules (`backup_rules.xml` / `data_extraction_rules.xml`) MUST exclude `capability_snapshot_json` and `health_snapshot_json` (per-device transient state); MUST include `settings_datastore.preferences` (user preferences worth restoring on new device). XML file updates documented in plan.md».

---

## Open items для spec.md

**Add to spec.md before speckit-plan:**

- **FR-056** Privacy classification of Capability/Health/LauncherSettings (CHK001).
- **FR-057** Android Auto Backup rules для new DataStore files (CHK024).

## Itog

- 15 PASS, 2 PARTIAL (оба fixable добавлением FR), 7 N/A (no network/auth/deep-links/external intents/WebView/ContentProvider/runtime permissions in spec 006), 0 hard FAIL.
- **Verdict:** спек **очень чист по security/privacy**. Local-first, no network, no dangerous permissions, no exported components, no deep links, zero PII in logs. Два важных gap — explicit privacy classification и Android Auto Backup policy для новых persistent files.

---

## TL;DR для нетехнического читателя

Этот checklist проверяет **безопасность и приватность данных**.

**Что хорошо в спеке:**
- Никакие данные не уходят с телефона. Всё хранится локально.
- В файлах хранятся **технические данные о телефоне** (заряд, есть ли интернет, какие приложения установлены), но **никаких личных данных бабушки** — ни имени, ни номера телефона, ни контактов, ни местоположения. Это уровень «телефонная диагностика», не уровень «личные данные».
- Никаких новых разрешений, которые могли бы напугать пользователя.
- Логи (если что-то пошло не так) пишутся **категориями** (например «не удалось прочитать настройки»), а не **подробностями** (никаких имён, номеров).

**Что нужно дополнить:**
- **Явная классификация данных в спеке** — записать чёрным по белому «эти данные — не личные, это устройство-телеметрия». Это поможет в будущем, когда спек 007 будет передавать их в облако родственнику — будет понятно что разрешать, а что нет.
- **Правила резервного копирования Android.** Когда пользователь покупает новый телефон, Android может автоматически перенести данные приложения. Тут нужна аккуратность:
  - Снапшот «какие приложения установлены» **не надо** переносить — на новом телефоне другие приложения, старый снапшот будет вводить в заблуждение.
  - Снапшот «здоровье устройства» **не надо** переносить — это про конкретный телефон, не про человека.
  - Настройки «показывать ли баннер про авиарежим» **надо** переносить — пользователь не хочет настраивать заново.

Это всё мелкие правки, не пересмотр.
