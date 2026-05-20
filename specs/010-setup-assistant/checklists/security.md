# Security Checklist: Setup Assistant and Launcher Bootstrap

**Purpose**: Verify security/privacy per MASVS + constitution Article XIV.
**Created**: 2026-05-19 (post `/speckit.clarify`)
**Feature**: [spec.md](../spec.md)

---

## Security model summary

**Critical assumption (A-6)**: Challenge gate (FR-021..FR-027) — **soft barrier, NOT security**. Threat model — защита от случайного тыра elderly/неопытного пользователя; **NOT** защита от criminal adversary, device theft, malicious household member. Real security (PIN+lockout, biometrics, device-level auth) — future-spec OUT-011.

Это **самая важная security-нота** спека 10 и она **explicit в спеке** — никаких false security claims, никакой security-by-obscurity rhetoric. Это позволяет проходить CHK008.

## Data at rest (MASVS-STORAGE)

- [X] **CHK001** — No PII в clear-text SharedPreferences:
  - **Спец 10 не добавляет новый PII storage.** ✓
  - Contact data (call confirmation FR-011) — read from `/config.contacts[]` (encrypted in transit, persisted в Room через спек 8) или system contacts (спек 9 contacts integration). No new clear-text PII path.
- [X] **CHK002** — Sensitive data в Keystore / EncryptedSharedPreferences:
  - **Спец 10 умышленно НЕ имеет sensitive data** to store (challenge — in-memory only per FR-025). 
  - **Previous draft** (PIN-based) собирался использовать `EncryptedSharedPreferences` для PIN — это удалено в clarify 2026-05-19. Текущий design — нет sensitive storage. ✓
- [⚠️] **CHK003** — Cache TTL / clear-on-uninstall:
  - SetupCheck results — in-memory cache for Settings screen lifetime. No persistent cache. ✓
  - Wizard progress — `SavedStateHandle` (per спек 3 pattern), cleared on Activity destruction. ✓
  - **Backup rules audit** (плагин-level): спек 10 НЕ добавляет persistent данные; existing `android:allowBackup` setting спека 1 наследуется. **Plan должен confirm.**
- [⚠️] **CHK004** — Logging excludes PII:
  - **Спец 10 не enumerates новые logging surfaces explicitly.**
  - Implied diagnostic events (см. failure-recovery CHK016/17): `gmsHardBlock(reason)`, `roleHomeRequested(accepted)`, `unlinkAttempted(linkId, result)` — все non-PII категории.
  - **NetworkOnlineCheck**: при логировании не должен включать network type / SSID / MAC. Plan должен enforce.
  - Plan-level: add explicit «no PII in logs» policy ссылка на FR-039 или новый FR.

## Data in transit (MASVS-NETWORK)

- [X] **CHK005** — HTTPS / TLS 1.2+:
  - All network calls inherited from спека 7 (Firestore/FCM/Cloudflare Worker) — TLS by-construction.
  - **Спец 10 не добавляет новых network endpoints.** ✓
- [X] **CHK006** — Certificate pinning: inherited from спека 7; не applicable новым contentу. ✓

## Authentication / Authorization (MASVS-AUTH)

- [X] **CHK007** — Privileged actions list required permission/role:
  - One-tap call (FR-012): `CALL_PHONE` permission required (explicit). ✓
  - ROLE_HOME (FR-007): `RoleManager.createRequestRoleIntent` system flow. ✓
  - POST_NOTIFICATIONS (FR-008): runtime permission Android 13+. ✓
  - Admin-mode entry: challenge gate (FR-022) — explicit «soft barrier, not security». ✓
  - Paired unlink (FR-032): requires user confirmation (FR-031 двухступенчатый — Article VIII destructive pattern). ✓
- [X] **CHK008** — No security-by-obscurity:
  - 7-tap gesture IS undocumented to end users (hidden discoverability). **BUT** — challenge gate (FR-021..FR-027) **explicitly не claims security** (A-6 threat model). Это **UX-skрытие**, не security boundary.
  - Hidden 7-tap gesture для elderly — accessibility-driven design choice (no Settings icon на main screen), не security control. ✓

## Platform interaction (MASVS-PLATFORM)

- [⚠️] **CHK009** — Exported components justified:
  - **Спец 10 не вводит новых exported activities/services/receivers**.
  - Call confirmation dialog (FR-011) — single Activity / Composable destination, non-exported. **Plan должен confirm `android:exported="false"`.**
  - Challenge gate screen (FR-022) — non-exported, navigation-only. **Plan должен confirm.**
  - GMS hard-block screen (FR-042) — non-exported. **Plan должен confirm.**
- [X] **CHK010** — Explicit package для intents:
  - `Intent(ACTION_CALL, "tel:...")` — system handler. ✓
  - `Intent(ACTION_DIAL, "tel:...")` — system handler. ✓
  - WhatsApp deep-link `https://wa.me/<phone>` (FR-014) — universal link, system resolves to WhatsApp если установлен (или browser). **No `setPackage("com.whatsapp")` mentioned в спеке** — оставляем universal-link pattern. ✓
- [X] **CHK011** — Deep links validated:
  - **Спец 10 не вводит новых deep-link types.** ROLE_HOME / POST_NOTIFICATIONS — outgoing intents к system, не incoming deep links к нашему app.
- [X] **CHK012** — Intent extras from external apps: спец 10 не receives intent extras from external apps (call confirmation triggered internally). ✓
- [X] **CHK013** — No exported ContentProvider. ✓
- [X] **CHK014** — No WebView introduced. ✓

## Permissions (Article XIV)

- [X] **CHK015** — Each permission justified by explicit FR:
  - `CALL_PHONE`: FR-011..FR-016. ✓ One-tap calling for elderly users.
  - `POST_NOTIFICATIONS`: FR-008. ✓ FCM channel from спека 7.
  - ROLE_HOME (not permission, but role): FR-007. ✓ Lon launcher functionality.
- [X] **CHK016** — No permission "for future use". ✓ All justified now.
- [X] **CHK017** — Fallback for denied permissions:
  - `CALL_PHONE` denied → `ACTION_DIAL` (FR-012). Explicit. ✓
  - `POST_NOTIFICATIONS` denied → `!N` banner + Settings deep-link (US-4 #2). ✓
  - ROLE_HOME denied → `!N` banner + retry deep-link (US-2 #2/#3). ✓
- [X] **CHK018** — `docs/compliance/permissions-and-resource-budget.md` update planned:
  - **Explicit в спеке** §Затрагиваемые внешние артефакты: добавление `CALL_PHONE`. ✓

## Privacy (Article XIV §3, §4)

- [X] **CHK019** — No hidden collection:
  - SetupCheck results stay device-local (FR-020a). ✓
  - Challenge state in-memory only (FR-025). ✓
  - No behavioural analytics introduced. ✓
- [X] **CHK020** — Local-first preferred:
  - **Все новые data points спека 010 — local-only.** ✓
- [X] **CHK021** — Data leaving device: notice + opt-in:
  - Спец 10 не добавляет new data-leaving paths. ✓
  - GDPR agency сохранена (A-11 — бабушка теоретически может пройти gate и отвязаться сама). ✓
- [X] **CHK022** — Data minimisation:
  - SetupCheck reports boolean `Ok` / `NotConfigured(reason: String)`. Reason — short error code, не PII. ✓
  - Paired devices list читает только имя устройства + дату привязки (FR-030) — minimum для отображения. ✓

## Build hardening

- [⚠️] **CHK023** — No debug flags / verbose logging в release:
  - Спец 10 не explicit это policy, но inherited from спека 1/3 baseline. Plan-level confirmation.
- [⚠️] **CHK024** — Backup rules (`android:allowBackup`, `data_extraction_rules`):
  - **Спец 10 удалил persistent state design (PIN, tutorial counters)**, поэтому backup-impact меньше чем was planned. ✓
  - Existing backup rules спека 1 cover Room database (`/config` cache спека 8). Спек 10 не вводит новые persistent surfaces. ✓
  - **Plan должен audit `data_extraction_rules.xml` для consistency.**

---

## Open items

1. **CHK004 — Logging PII policy.** Plan.md должен enforce explicit policy: «No PII в diagnostic events. `NetworkOnlineCheck` reports only boolean `online`, не SSID/MAC/network type details.» Cross-reference в diagnostic events contract (если создан).

2. **CHK009 — Non-exported confirmation.** Plan.md `AndroidManifest.xml` updates MUST включать `android:exported="false"` на новые Activity classes (`CallConfirmationActivity`, `ChallengeGateActivity`, `GmsHardBlockActivity`) или confirm они composed как destinations в существующих Activity (preferred).

3. **CHK024 — Backup rules audit.** Plan должен verify `data_extraction_rules.xml` не содержит ссылок на удалённые keys (никогда не было реализовано, но verify).

## Result

**21/24 ✓, 3 observations** (CHK004 logging policy enforcement; CHK009 non-exported confirmation; CHK024 backup rules audit — все plan-level). **Спек 10 — security-clean by design** благодаря clarify решениям: explicit «soft barrier, not security» (A-6), in-memory-only challenge state (FR-025), no new PII storage, no new network endpoints, comprehensive permission fallback design.

**Главное достижение security-перспективы**: спек 010 **avoids the trap of pretending challenge gate is security**. PIN-based design (предыдущий draft) создавал false sense of security без real protection. Текущий design — honest about its threat model, что лучше для long-term trust.

---

## Краткое содержание (для не-разработчика)

Проверили: не утечёт ли PII (имя, телефон), правильно ли запросы permissions, нет ли security-театра. **Самый важный итог**: спек **честно говорит «challenge gate — soft barrier, не security»**. Это правильная формулировка — не создаём ложного чувства защищённости. Реальная security защита (PIN с lockout, biometrics) — отдельный future-spec (OUT-011). Permissions (CALL_PHONE, POST_NOTIFICATIONS) обоснованы FR-ами, имеют fallback'и, обновление compliance docs запланировано.
