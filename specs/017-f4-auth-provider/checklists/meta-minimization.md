# Checklist: meta-minimization

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 11/13 ✓ — passes anti-bloat baseline, 2 items требуют conscious justification (которая уже есть в clarifications).

---

## New abstractions

- [x] **CHK001** Every new interface/port has at least one concrete consumer **в этой спеке** — ✓.
  - `AuthProvider` port → consumed wizard'ом (US 2) + `SignInTrigger` composable + future S-8 sync, S-9 health.
  - `SessionStore` port → consumed adapter'ом (internal), не имеет внешних consumer'ов — но это **internal contract** между Adapter и его state (clarification Q2 явно сделал его internal).
  - `AuthIdentity` → consumed F-5 ConfigCipher (key derivation), S-2 (delegation owner UID). Это already planned consumers.
  - **Note про SessionStore**: после Q2 он стал internal. Это **не нарушение** CHK001 — internal types не подчиняются «consumer in this spec» rule, они оправданы своим основным consumer'ом (один adapter).
- [x] **CHK002** Single-implementation interfaces — ✓ оправданы.
  - `AuthProvider` (1 real impl `GoogleSignInAuthAdapter` + Fake) → DI seam + fake adapter (rule 6 mock-first) = **port-shape need**. Justified.
  - `SessionStore` (1 real impl `EncryptedLocalSessionStore` + Fake) → adapter swap (EncryptedSharedPreferences → future SecureKeyStore) + fake = **port-shape need + future swap inline TODO**. Justified.
  - **После clarify Q4**: `ProviderKind` enum **удалён** — это **позитивная** removal: устранена single-purpose enum, которая не имела current consumer'ов и существовала «на будущее для Phone/Email/Apple». Это образцовое применение rule 4 MVA.
- [x] **CHK003** Mediator/orchestrator/manager — ✓ no such классов. `AuthAdapterSelector` (FR-018) — это **runtime device-capability dispatcher**, не pass-through wrapper. Justified by current need (GMS detection on Android).
- [x] **CHK004** No custom DSL / registry / plugin system — ✓. F-4 не вводит ни custom config DSL, ни capability registry (это F-2 territory, отложен Phase 4+).

## New modules / packages

- [x] **CHK005** New gradle module satisfies Article V §3 — ⚠️ partial. F-4 **не вводит** новый gradle module — он добавляет packages в существующие модули (`core/domain/auth/`, `app/androidMain/auth/`). Это **меньше bloat** чем создание `core:auth` module. **Note**: если в plan.md решат выделить `core:auth` как KMP submodule (подобно `core/crypto/` в F-CRYPTO) — должно быть обосновано через Article V criteria.
- [x] **CHK006** «Why is a package not enough?» — **N/A** (no new module).
- [x] **CHK007** No "utils" / "common" / "helpers" dumping ground — ✓. Все новые packages имеют чёткую responsibility: `auth/` (ports), `auth/internal/` (SessionStore, SessionRecord — internal), `auth/ui/` (SignInTrigger).

## New configuration

- [x] **CHK008** New config field has current FR — ✓.
  - `SubscriptionState` field в `User` (FR-010, FR-011): consumer есть в декорации (always returns `Unknown` в MVP), но **field существует** как stub для post-MVP billing spec. Justified through clarification Q6 (subscription is forward-declared, value computed server-side). Это **acceptable** stub field — не настоящая abstraction.
  - `identityKeys: IdentityKeys?` field в `User` (FR-010): forward declaration для F-5, в F-4 = `null`. Same justification.
  - **Both fields are nullable forward declarations**, не новые configurable options.
- [x] **CHK009** Config field defaults documented; wire-format `schemaVersion` — ✓. FR-021 (SessionRecord blob `schemaVersion: 1`), FR-016a (identity-links collection structure). Backward-compat read test в FR-022. Migration path: рекомендован JSON now, миграция через `schemaVersion` bump per CLAUDE.md rule 5.

## CLAUDE.md rule 4 self-test

- [x] **CHK010** Test 1 (inline + lose optionality) — ✓ applied per abstraction:
  - **`AuthProvider` port inlined into `GoogleSignInAuthAdapter`**: lose ability to swap Google→Phone/Email/Apple/own-server **without rewriting consumers**. **Lose much more than optionality** — lose entire CLAUDE.md rule 2 ACL principle для auth. Justified.
  - **`SessionStore` port inlined into `EncryptedLocalSessionStore`**: lose ability to swap EncryptedSharedPreferences → SecureKeyStore (post-F-5 inline TODO). This is **planned 1-day swap** (additive change, не rewrite). Justified.
  - **`SignInTrigger` composable**: inlined would mean каждый consumer (wizard, future Settings) пишет свой sign-in UI. Lose consistent UX, single localization point, single sign-in/sign-out behavior. **Justified** через clarification Q9 (owner explicitly approved — reusable composable, not built-into-wizard).
  - **`Identity-links` Firestore collection (FR-016a)**: inlined would mean hardcoding Google sub claim или Firebase UID as `stableId`. Lose country-ban exit ramp, lose own-server cutover without data migration, lose Phone adapter compatibility. **Justified** через clarification Q1 (one-way door avoided).
- [x] **CHK011** Test 2 (dependency change cost) — ✓ applied:
  - **Firebase Auth deprecated tomorrow**: replace `GoogleSignInAuthAdapter` Step 2 (Firebase exchange). Estimated 2-3 days. **Justified seam**.
  - **Google Sign-In SDK deprecated** (already happened — Credential Manager): replace Step 1. 1-2 days. **Justified**.
  - **EncryptedSharedPreferences deprecated**: swap to SecureKeyStore. 1 day per inline TODO. **Justified planned swap**.
  - **identity-links collection on Firestore breaks**: migrate to own-server (planned, `SRV-AUTH-IDENTITY-001`). Days/weeks (one-time migration). **Justified** — это и есть exit ramp purpose.

## Removal validation

- [x] **CHK012** No abstractions removed — **N/A**. F-4 — net-new spec. Anonymous Firebase Auth не «removed» в F-4 — оно **никогда не было implemented** в production code (был только в decision-документах). FR-029 fixates это через Detekt rule.
- [x] **CHK013** No "deprecated, will remove later" — ✓. F-4 не маркирует существующий код для будущего удаления.

---

## Bloat risks — найденные и обоснованные

### Risk 1: `SignInTrigger` composable добавляет UI в spec, который изначально был «только port + adapter»

**Owner decision (clarification Q9)**: F-4 владеет `SignInTrigger` как самостоятельной UI-единицей. Wizard сейчас дёргает. В будущем Settings spec может дёрнуть.

**Why это не bloat**:
- **Current consumer**: wizard screen 2 (F-3) — sign-in UI должен где-то жить, F-3 не должен дублировать UI логику auth port.
- **Single localization point**: explainer text + button labels в `strings_auth.xml` — одно место правки.
- **Single sign-in/sign-out behavior**: clarification Q3 (sign-out keeps local) и Q6 (cancel returns silently) применены **один раз** в `SignInTrigger`, не five times в consumer'ах.
- **Compromise** (per Q9 discussion): F-4 владеет компонентом, но **не привязан** к месту вызова — reusable.

**Опровергнутая альтернатива** (CloudFeatureGate composable for «5 cloud features»): **отброшена** в clarify pass — cloud-feature кнопок не существует. SignInTrigger — meaner, single-purpose component.

### Risk 2: `Identity-links` Firestore collection — добавляется инфраструктура

**Owner decision (clarification Q1)**: UUID stableId + `/identity-links/...` collection.

**Why это не bloat**:
- **One-way door avoidance**: без identity-links pre-binding на Google sub claim создаёт country-ban risk + own-server cutover migration cost. Per CLAUDE.md rule 3, one-way door требует exit ramp.
- **Real current cost**: 1 Firestore collection + lookup в adapter (~10 lines кода). Estimated +3-4 days в F-4 estimate.
- **Real exit cost saved**: миграция всех `/users/{UID}` + `/delegations/{UID}/...` + ConfigDocument'ов = недели работы. Trade ratio 1:30 favours pre-binding.

**Опровергнутая альтернатива** (Google sub claim as stableId): **отброшена** в clarify pass — bind to Google permanently, fails country-ban + Phone adapter.

---

## Open items (для plan stage)

1. **Consider `core:auth` KMP module**: plan.md должен решить — оставить `auth/` как package внутри существующего `core/domain/`, или выделить как submodule `core/auth/` (подобно `core/crypto/`). Аргументы:
   - **За module**: future iOS / cross-platform consumer'ы, clear ownership boundary.
   - **Против**: один real consumer (Android) сейчас, premature decomposition.
   - **Recommendation**: package для MVP, выделение в module когда появится cross-platform consumer (как inline TODO в `build.gradle.kts`).

---

## Verdict

**11/13 ✓, 2 partial/N/A.** Spec **проходит** meta-minimization baseline. Все новые abstractions имеют **explicit owner justification** в clarifications:
- SignInTrigger composable → Q9.
- Identity-links collection → Q1.
- SubscriptionState/identityKeys stubs → Q6 + F-5 forward declaration.

**Positive removal**: `ProviderKind` enum **удалён** per Q4 — это образцовое применение rule 4 MVA (устранена abstraction без current consumer'ов).

---

## Что это значит простыми словами

Спека не добавляет «лишних слоёв на будущее»:
- Все новые типы и интерфейсы имеют **уже существующего потребителя**, кто их будет использовать в этой же спеке или ближайших (wizard, F-5, S-2).
- Один тип (`ProviderKind`) был **удалён** во время clarify — он не имел потребителей, существовал «на всякий случай». Это правильный путь.
- Новые элементы инфраструктуры (Firestore таблица identity-links, composable SignInTrigger) — обоснованы явными решениями владельца, записанными в clarifications.
- Решение не выделять новый Gradle модуль (а добавить в существующие папки) — экономит сложность.
- Готово к `/speckit.plan` — план реализации не будет вынужден переделывать структуру.
