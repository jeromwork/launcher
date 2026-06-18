# Checklist: failure-recovery

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 14/17 ✓, 3 open items for plan stage (diagnostics + idempotency formalization).

---

## Error categories

- [x] **CHK001** Every external-action FR lists failure mode — ✓.
  - `signIn()` → 5 named errors: `NetworkError`, `Cancelled`, `NoEmail`, `ProviderUnavailable`, `Unknown(message)` (FR-009).
  - Token refresh (FR-017) → refresh failure → `currentUser` → null.
  - `SessionStore.current()` corrupted blob (FR-023) → returns null, не crash.
  - Identity-links Firestore lookup (FR-016a) → ⚠️ **failure mode не специфицирован explicitly**. Network failure / Firestore offline / permission denied — что возвращается? **Open item**: добавить FR или edge case: «identity-links lookup network failure → adapter retries once с backoff, при failure → `AuthError.NetworkError`; permission denied → `AuthError.ProviderUnavailable`».
- [x] **CHK002** User-visible behaviour specified — ✓.
  - Wizard sign-in: при `Cancelled` → возврат на screen 2 без toast (per Q6).
  - При `NoEmail` → message «используйте личный Google-аккаунт» (US 2 #6).
  - При `NetworkError` → UI показывает retry (Edge case).
  - При `ProviderUnavailable` → «вход через Google недоступен на этом устройстве» (Edge case).
- [x] **CHK003** No silent failures of user-initiated actions — ✓ с одним open item.
  - User-initiated sign-in: каждый error path возвращает `Outcome.Failure(...)`, UI обязан handle (US 2 acceptance scenarios покрывают все варианты).
  - **Background failures** (token refresh, identity-links lookup) — частично silent: `currentUser` flow эмитит null, но UI должен слушать. **Open item** (см. dev-experience CHK018-019): structured WARN log при background failure.

## Fallbacks

- [x] **CHK004** Fallback depth defined — ✓ (no chains). F-4 не имеет fallback chains (single provider в MVP). `AuthAdapterSelector.pickAdapter()` (FR-018): single Google adapter → error `NoSupportedAuthProvider` если GMS недоступен. **No infinite chain risk**.
- [x] **CHK005** Fallback specified by data — **N/A**. No data-driven fallback.
- [x] **CHK006** If fallback fails → terminal behaviour — ✓. `NoSupportedAuthProvider` → app остаётся в local mode forever (per device-self-sufficiency CHK-DSS-002). Это **graceful** terminal: не crash, не «приложение сломано», просто cloud features unavailable.

## Retries

- [x] **CHK007** Retry behaviour explicit — ✓ (per clarify status item 5).
  - **Sign-in retry**: **NO automatic retry**. Error возвращается UI, UI решает (decision 03 «никакого forced engagement»). User вручную тапает «Войти» ещё раз.
  - **Token refresh retry**: spec FR-017 описывает auto-refresh при `expiresAt < now + 5min`, **но** не специфицирует retry policy при network error во время refresh. **Open item**: «refresh при NetworkError → retry once с 1s backoff, при second failure → `currentUser` → null (через silent transition)».
  - **Identity-links lookup retry**: см. CHK001 open item.
- [x] **CHK008** No infinite retry loops — ✓. No retry в spec — defacto безопасно.
- [x] **CHK009** Idempotency — ⚠️ partial.
  - `signIn()` сам по себе **не идемпотентен** в смысле user action (вызов дважды покажет two bottom-sheets если первый не closed). **Mitigation**: edge case «`AuthProvider.signIn` вызван дважды параллельно → adapter MUST deduplicate». Это идемпотентность по семантике, не по строгому math definition.
  - `signOut()` — идемпотентен (повторный вызов = no-op если уже signed out).
  - `EncryptedLocalSessionStore.save()` — идемпотентен (overwrite same data — same result).
  - Identity-links creation: **conditional create** (FR-016a: «если документа нет → генерирует новый UUID»). **Open item**: между check и create — race window. Plan.md должен specify через Firestore transaction (атомарность) или through Security Rules constraint (write only if document does not exist).

## Offline / degraded modes

- [x] **CHK010** Offline behaviour — ✓.
  - Sign-in offline: `NetworkError` (Edge case). App остаётся в local mode.
  - Token refresh offline: «Token expired offline (refresh невозможен) → `currentUser` остаётся `AuthIdentity` (last known), любой server-bound action falls back на retry-when-online» (Edge case).
  - Existing session offline: `currentUser` returns cached identity, consumer-сервисы knows offline через own indicators.
  - Identity-links lookup offline: **see CHK001 open item**.
- [x] **CHK011** Stale data TTL / freshness — ✓.
  - Token TTL: Firebase JWT ≈ 1 hour (mentioned in cannot-test-locally gaps).
  - Refresh trigger: `expiresAt < now + 5min` (FR-017) — 5-minute buffer.
  - Session persistence: encrypted indefinitely до app uninstall или manual signOut.
  - **Note**: identity-links cached locally? — Spec не указывает. **Open item**: если кешируется — TTL и invalidation policy.

## Permissions denied

- [x] **CHK012** First-time denial behaviour — **N/A для F-4**. F-4 не requests runtime permissions. Sign-in через Credential Manager — это не permission, это Google account picker. ROLE_HOME / runtime permissions — wizard (F-3) territory.
- [x] **CHK013** Permanent denial recovery — **N/A**.

## Recovery from invalid state

- [x] **CHK014** Corrupted state recovery — ✓.
  - FR-023: «Corrupted blob handling: parse failure → `current()` возвращает `null`, не crash. Log warning».
  - US 5 acceptance #3: «session blob в `SessionStore` corrupted → session игнорируется, `currentUser = null`, без crash».
  - **Recovery action**: silent — user повторно signs in. Это **acceptable** для session blob, потому что session re-establishable.
  - Identity-links inconsistency (например, link points к UUID для которого `/users/{UUID}` не существует) — **Open item**: spec не покрывает. Plan.md должен handle: при sign-in adapter обнаружил orphan link → recreate `/users/{UUID}` или log error.
- [x] **CHK015** No crash-as-recovery — ✓. Все error paths explicit return null / Outcome.Failure / log warning. Никаких `throw` для handled errors.

## Diagnostics

- [ ] **CHK016** Failure observable события — ⚠️ **open item**.
  - Spec не специфицирует diagnostic events с category / fields.
  - Background failures особенно risky (см. CHK001-003).
  - **Open item** (already raised by dev-experience CHK018): добавить FR в plan.md «структурированные log lines для auth events; tag `Auth`; categories: `sign_in.attempt`, `sign_in.success`, `sign_in.failure.{reason}`, `token_refresh.failure`, `session_blob.corrupted`, `identity_links.lookup.failure`».
- [ ] **CHK017** Failures aggregated by category — ⚠️ **open item**.
  - Spec не имеет structured log policy.
  - Без category-based aggregation — невозможно measure rate (например, «10% sign-in attempts fail с NetworkError» vs «10 unique error messages»).
  - **Open item**: log policy в plan.md — categories enum, не raw message strings.

---

## Open items (для plan stage)

1. **Identity-links lookup failure modes**: добавить explicit error mapping (`NetworkError`, `ProviderUnavailable` на permission denied) и retry policy. Edge case или новый FR.
2. **Token refresh retry policy**: 1 retry с 1s backoff при NetworkError, при second failure → `currentUser` → null silent transition.
3. **Identity-links create atomicity**: Firestore transaction или Security Rules constraint «create-only, no overwrites».
4. **Identity-links cache TTL** (если cache используется): explicit policy или explicit «no caching, lookup at each sign-in».
5. **Orphan identity-link recovery**: что делать если link points к non-existent `/users/{UUID}` (rare data corruption case).
6. **Structured diagnostic events**: tag `Auth`, category enum (sign_in.attempt/success/failure.*, token_refresh.*, etc.).
7. **Failure category aggregation**: log structure для measuring rates, не unique strings.

---

## Verdict

**14/17 ✓, 3 partial.** Spec covers major error categories well: AuthError sealed type, corrupted blob handling, Cancelled = legitimate choice. Two gaps:
- **Identity-links** (introduced в clarify Q1) — failure modes / retry / atomicity недостаточно специфицированы.
- **Diagnostics** — нет structured logging policy.

Ни один open item не блокирует merge, но все 7 должны быть addressed в plan.md / tasks.md до implementation.

---

## Что это значит простыми словами

Спека хорошо описывает что происходит когда что-то идёт не так:
- Если интернет пропал — приложение работает с последним известным состоянием.
- Если пользователь отменил вход в Google — никаких сообщений «вы что-то не так сделали», просто возврат к двум кнопкам.
- Если Google не вернул email (редкий случай) — понятное сообщение «используйте личный Google аккаунт».
- Если зашифрованный файл сессии повреждён — приложение не падает, пользователь просто заходит снова.
- Никаких автоматических повторов входа — если ошибка, пользователь сам решает, нажать ли «Войти» ещё раз.

**7 уточнений для plan'а** (не блокеры):
1-2. Что делать при сетевых ошибках во время поиска в таблице identity-links и обновления токена.
3. Как обеспечить, чтобы при одновременных входах с разных устройств не создалось два UUID для одного Google аккаунта (атомарность через транзакцию).
4. Кешировать ли результаты поиска identity-links или каждый раз идти в Firestore.
5. Что делать, если ссылка identity-link указывает на несуществующего пользователя (редкий случай повреждения данных).
6-7. Какие именно события auth логировать (категорически, не сырыми строками — чтобы можно было считать процент ошибок).

Эти 7 пунктов — улучшения диагностики и устойчивости, должны быть закрыты до начала реализации.
