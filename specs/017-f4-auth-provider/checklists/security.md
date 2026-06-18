# Checklist: security

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 20/24 ✓, 3 open items for plan stage, 1 N/A. Spec — security-critical (auth, tokens, PII) и handles fundamentals correctly; pending уточнения по logging policy и Firestore Security Rules.

---

## Data at rest (MASVS-STORAGE)

- [x] **CHK001** No PII в clear-text — ✓.
  - `email`, `displayName` (PII) — стored как часть `SessionRecord` в `EncryptedLocalSessionStore` (FR-020 — EncryptedSharedPreferences с TEE-backed master key).
  - `AuthIdentity` runtime-only (Flow emission), не persisted clear-text.
  - **No other PII storage** в F-4 scope. (Contacts / phones — это S-3 / S-5 territory.)
- [x] **CHK002** Sensitive data (tokens) в Encrypted/Keystore — ✓.
  - FR-020 explicit: `EncryptedLocalSessionStore` через Android EncryptedSharedPreferences (Jetpack Security `androidx.security:security-crypto`).
  - Master AES key в Android Keystore TEE.
  - `refreshToken: String?` — частью SessionRecord blob → encrypted.
  - `extra["firebase_jwt"]` — encrypted same way.
  - **Inline TODO** для будущего перехода на `SecureKeyStore` из F-CRYPTO (wrap pattern сильнее EncryptedSharedPreferences).
- [x] **CHK003** Cache TTL / clear-on-uninstall — ✓.
  - Session blob: уничтожается при app uninstall (default Android app data behavior).
  - Token TTL: Firebase JWT ≈ 1 hour (mentioned).
  - **No long-lived caches** introduced.
- [ ] **CHK004** Logs exclude PII — ⚠️ **open item**.
  - Spec не специфицирует logging policy.
  - **Risk**: developer случайно логирует `AuthIdentity` (с email/name), refreshToken, или Firebase JWT.
  - **Open item** (already raised by dev-experience CHK018 + failure-recovery CHK016): explicit log policy в plan.md:
    - Categories вместо enumeration: `sign_in.attempt providerKind=google` (not `email=anna@gmail.com`).
    - **Never log**: refreshToken, Firebase JWT, Google sub claim, full email.
    - **OK to log**: stableId UUID (это наш opaque identifier, не PII), error categories, presence flags (`refreshTokenPresent: true`).

## Data in transit (MASVS-NETWORK)

- [x] **CHK005** HTTPS / TLS 1.2+ — ✓.
  - Firebase Auth SDK uses HTTPS by default.
  - Firestore SDK uses HTTPS by default.
  - Credential Manager API uses HTTPS via Google servers.
  - **No cleartext exception** required для F-4. `android:cleartextTrafficPermitted` should remain false (project-level default).
- [x] **CHK006** Certificate pinning — **N/A для MVP**. Per project convention: certificate pinning не требуется в MVP (Firebase SDK handles это internally). Post-MVP consideration: certificate pinning для own-server endpoint (when cutover happens). Не F-4 concern.

## Authentication / Authorization (MASVS-AUTH)

- [x] **CHK007** Privileged actions list permission/role — ✓.
  - `signIn()` — public, any user может invoke.
  - `signOut()` — public.
  - `currentUser` flow — public read-only.
  - Identity-links Firestore writes — protected by Firebase auth UID == providerAccountId (FR-016a Security Rules).
  - `/users/{stableId}` writes — protected by Firebase auth UID match.
  - **`deleteAccount()`** (mentioned в FR-034 для S-6) — privileged, не в F-4 scope.
- [x] **CHK008** No security-by-obscurity — ✓.
  - F-4 endpoints — все documented в spec (Credential Manager API, Firebase Auth, Firestore paths).
  - Identity-links collection structure — explicit в FR-016a.
  - **No hidden URLs** or undocumented intents.

## Platform interaction (MASVS-PLATFORM)

- [x] **CHK009** Exported components justified — ✓.
  - F-4 не вводит exported activities / services / receivers.
  - Credential Manager — Google-provided UI, not our exported component.
  - **No new exported surface**.
- [x] **CHK010** Explicit intents — ✓.
  - F-4 не launches external intents.
  - Credential Manager invocation — through AndroidX API, не intent.
  - **No implicit intents to sensitive data**.
- [x] **CHK011** Deep links validated — **N/A**. F-4 не вводит deep links. Deep links для OAuth redirect (если бы использовался) — handled через Credential Manager internally, не наш surface.
- [x] **CHK012** Intent extras size-bounded — **N/A**. F-4 не accepts intent extras от external apps.
- [x] **CHK013** No exported ContentProvider — ✓. F-4 не вводит ContentProvider.
- [x] **CHK014** WebView restrictions — **N/A**. F-4 не использует WebView (Credential Manager bottom-sheet — Google-rendered, не WebView).

## Permissions (Article XIV)

- [x] **CHK015** Each permission justified by FR — ✓. F-4 не requests runtime permissions (Credential Manager и Firebase Auth работают без runtime permissions). **Manifest permissions** (если потребуются) — INTERNET (уже в base manifest), может быть GET_ACCOUNTS (но Credential Manager не требует на современных API levels).
- [x] **CHK016** No permission "future use" — ✓. No new permissions requested.
- [x] **CHK017** Fallback for denied permissions — **N/A** (no runtime permissions in F-4).
- [x] **CHK018** `docs/compliance/permissions-and-resource-budget.md` update — **N/A** (no new permissions).

## Privacy (Article XIV §3, §4)

- [x] **CHK019** No hidden collection — ✓.
  - F-4 collects: email, displayName, Google sub claim (через identity-links lookup), Firebase UID (через Firebase Auth SDK, exchanged for our UUID), refreshToken.
  - **All collection explicit** в spec и в pre-release tasks (Privacy Policy update — FR-032).
- [x] **CHK020** Local-first preferred — ✓.
  - F-4 запускается **только** при explicit user choice (wizard «Войти» или `SignInTrigger`).
  - Local mode forever supported (User Story 1, FR-030).
  - **Networked feature explicitly justified**: cloud features (sync, push, photos, SOS) requires identity → F-4 необходим для них.
- [x] **CHK021** User notice + opt-in — ✓.
  - Wizard screen 2 button label «Войти в Google для восстановления существующего конфига» — opt-in via tap.
  - Google's own Credential Manager bottom-sheet — Google-provided consent UI.
  - **Privacy Policy update** (FR-032) — pre-release task, обязан указать собираемые данные.
  - **Play Console Data Safety form** (FR-032) — required.
- [x] **CHK022** Data minimisation — ✓.
  - F-4 collects **минимум**: stableId (наш UUID), email, displayName, refreshToken, expiresAt.
  - **Не collects**: Google sub claim в `AuthIdentity` (он в identity-links collection separately), profile photo URL (per decision 2026-05-30/08 — only `openid email profile` scopes), любые другие OAuth claims.
  - **Smaller surface than ranges of decisions** allowed.

## Build hardening

- [x] **CHK023** No debug flags в release — ✓.
  - FR-019: build-config gate + Detekt rule ловит `FakeAuthAdapter` import в `:app:release`.
  - SC-010 sub-(c): «`FakeAuthAdapter` не импортируется в `:app:release`».
  - **Standard project convention**: `BuildConfig.DEBUG`-gated verbose logging.
- [ ] **CHK024** Backup rules reviewed — ⚠️ **open item**.
  - F-4 adds new persistent data: `EncryptedSharedPreferences` for session blob.
  - **Question**: should этот файл быть в `android:allowBackup`?
  - **Recommendation**: **NO** — session blob содержит refreshToken; auto-backup на Google Drive создаёт второй attack surface.
  - **Open item**: plan.md должен добавить exclusion в `data_extraction_rules.xml` для session blob path. Inline TODO:
    ```
    <!-- TODO(backup-exclusion): exclude auth session blob from auto-backup
         per checklist-security CHK024. File path: <encrypted-shared-prefs-name> -->
    ```

---

## F-4-specific security considerations

### Consideration 1: Identity-links spoofing (already in tamper-resistance)

Covered by tamper-resistance checklist. Key controls:
- Firestore Security Rules: write to `/identity-links/google/{sub}` only if `request.auth.uid == sub` AND document does NOT exist (no overwrites).
- **Open item для plan**: explicit Security Rules.

### Consideration 2: AI Affordance запрет на токены

Spec AI Affordance section explicit:
> **Никаких** `signIn(...)`, `signOut(...)`, `getRefreshToken(...)`, `currentSession()` capabilities. AI agent **не должен** инициировать Sign-In или иметь доступ к токенам.
>
> **Запрет** на чтение `refreshToken`, `extra` (Firebase JWT).

Это **stronger** чем дефолт — AI не получает write capabilities в identity, не получает токены. Это будет проверено `checklist-ai-readiness`.

### Consideration 3: PII leak через logs (open item already raised)

Risk: developer прокачивает `AuthIdentity` в Logcat для debug → leak email/name.

Mitigation:
- Spec needs explicit log policy (open item).
- `BuildConfig.DEBUG`-gated verbose logging.
- Detekt rule could enforce: no Log.* with `AuthIdentity` / `SessionRecord` parameter directly.

### Consideration 4: Refresh token theft

Risk: rooted device → adversary extracts refreshToken из EncryptedSharedPreferences master key TEE → impersonates user.

Mitigation:
- Android Keystore TEE makes extraction **non-trivial** but not impossible on compromised devices.
- Firebase Auth SDK has device fingerprint / IP checks server-side.
- **L0 уровень** acceptable для MVP (per decision 03).
- Inline TODO (FR-020): future migration to SecureKeyStore (F-CRYPTO wrap pattern сильнее).

### Consideration 5: OAuth scope creep

Risk: future maintainer добавляет дополнительные OAuth scopes (calendar, contacts) → expanded PII collection без Privacy Policy update.

Mitigation:
- Decision 2026-05-30/08 fixates: **only** `openid email profile` scopes.
- FR-032 mentions OAuth Consent Screen — pre-release.
- **Open item для plan**: Detekt rule или Gradle check на CredentialManager scope strings — must match whitelist `["openid", "email", "profile"]`.

---

## Open items (для plan stage)

1. **Logging policy** (CHK004, already raised by dev-experience + failure-recovery): structured log lines, no PII, no tokens; tag `Auth`; categories.
2. **Backup exclusion** (CHK024): exclude session blob from `android:allowBackup` / `data_extraction_rules.xml`.
3. **Firestore Security Rules для identity-links** (от tamper-resistance, повторяется): write only if auth UID matches AND document does NOT exist.
4. **OAuth scope whitelist enforcement**: Detekt / Gradle check.
5. **PII в logs Detekt rule**: ban `Log.*` calls with `AuthIdentity` / `SessionRecord` direct parameter.

---

## Verdict

**20/24 ✓, 3 open items, 1 N/A.** Spec правильно handles security fundamentals:
- ✅ PII encrypted at rest.
- ✅ Tokens в Keystore TEE-backed.
- ✅ No clear-text PII in storage.
- ✅ HTTPS via Firebase SDK.
- ✅ No exported components.
- ✅ Data minimisation (UUID stableId — opaque, smaller PII surface).
- ✅ Opt-in via explicit user choice.
- ✅ AI Affordance запрещает токены AI агенту.
- ✅ Privacy Policy + Data Safety pre-release tasks identified.

Открытые items — все **plan.md scope**, не блокеры spec merge.

---

## Что это значит простыми словами

Спека правильно обрабатывает безопасность:
- **PII и токены** (email, имя пользователя, токен обновления) хранятся **зашифрованными**. Ключ шифрования — в защищённой аппаратной зоне Android (TEE), его нельзя извлечь даже с рут-доступом простыми средствами.
- **HTTPS везде** — Firebase SDK сам обеспечивает.
- **Минимум данных** — берём только email и имя из Google аккаунта, ничего больше (никаких контактов, календаря, фото из Google).
- **Согласие пользователя** — вход в Google происходит **только** при явном нажатии кнопки, никаких автоматических входов.
- **AI агенту запрещено** иметь доступ к токенам или инициировать вход — это в спеке явно прописано.
- **Privacy Policy и Data Safety** — нужно обновить перед релизом (это admin задача).

**5 уточнений для plan'а** (не блокеры):
1. Прописать правила логирования (что логировать, что НЕ логировать — токены и email не должны попадать в логи).
2. Исключить файл сессии из Google автобэкапа — иначе токен утечёт в Google Drive пользователя.
3. Точные правила доступа к таблице identity-links на Firestore (чтобы нельзя было подменить чужую запись).
4. Запретить через линтер запрос лишних OAuth scopes (только email, profile).
5. Запретить через линтер прямой `Log.d(authIdentity)` — чтобы PII не попадало случайно в логи.
