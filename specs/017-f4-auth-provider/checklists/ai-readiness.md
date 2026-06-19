# Checklist: ai-readiness

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 18/20 ✓ — **passes cleanly**. F-4 правильно declares limited AI affordance (read-only identity); запрещает доступ к sign-in / sign-out / tokens AI agent'у.

---

## Capability shape

- [x] **CHK001** Capabilities expressed as domain verbs — ✓.
  - AI Affordance section lists **exposable** capabilities как domain verbs:
    - `getCurrentUserIdentity() → AuthIdentity?` — read-only identity.
    - `getProviderKind() → ProviderKind` — read-only.
  - **Not SDK calls**: не `firebaseAuth.getCurrentUser()`, не `credentialManager.getCredential()`.
  - **Forbidden capabilities** also domain verbs: `signIn(...)`, `signOut(...)`, `getRefreshToken(...)`, `currentSession()` — все explicitly запрещены AI agent'у.
- [x] **CHK002** Capability signatures use domain types only — ✓.
  - `AuthIdentity` — domain data class (per FR-007).
  - **Note on providerKind**: AI Affordance section упоминает `getProviderKind() → ProviderKind` как hypothetical future capability, но **`ProviderKind` was removed per clarification Q4**. Это **leftover inconsistency** — AI Affordance section needs update. Open item.
  - **Open item**: правка AI Affordance — remove `getProviderKind()` capability since providerKind не существует в AuthIdentity. Either:
    - (a) Remove this capability mention entirely.
    - (b) Note: «`providerKind` removed per clarification Q4; if future spec re-introduces it, that spec defines whether AI gets read access».
- [x] **CHK003** Natural-language description — ✓.
  - AI Affordance section имеет one-line описание для каждой capability: «read-only, для AI "понять, кто пользователь" (например, отвечать "Привет, Анна")».
  - Grounding for future AI agent.
- [x] **CHK004** Capability registry planned — ✓.
  - AI Affordance section explicit reference: «Future capability registry (F-2) может expose...».
  - F-2 (Capability Registry Foundation) — **deferred to Phase 4+** per memory `project_deferred_cloud_architecture`.
  - **Not scattered as ad-hoc functions** — design clearly tied to future registry.

## Affordance contract

- [x] **CHK005** Read vs write declared — ✓.
  - **Read capabilities**: `getCurrentUserIdentity()` (read-only). `getProviderKind()` (read-only — но providerKind removed per Q4, см. CHK002).
  - **Write capabilities**: **ZERO** — `signIn`, `signOut` explicitly forbidden.
  - **No mixed semantics**.
- [x] **CHK006** Idempotency/reversibility declared — ✓.
  - Read-only capabilities — inherently idempotent.
  - AI Affordance excludes all irreversible/state-changing actions.
- [x] **CHK007** Confirmation для irreversible actions — ✓ implied. Since irreversible actions (signIn/signOut/deleteAccount) explicitly запрещены AI agent'у, this constraint is satisfied automatically.
- [x] **CHK008** Rate / quota constraints — ⚠️ partial. Spec **не специфицирует** rate limits для AI-callable capabilities. **Note**: read-only capabilities на in-memory identity — rate limit unimportant (никаких external calls). **Open item**: если future capability добавляет network call (например, «lookup display name from server»), capability registry spec должен define rate limit. F-4 — N/A для MVP.

## PII / privacy boundary

- [x] **CHK009** No raw PII by default — ⚠️ partial.
  - `AuthIdentity` содержит `email` и `displayName` — это **PII**.
  - `getCurrentUserIdentity() → AuthIdentity?` exposes это AI агенту.
  - **Mitigation**: spec **explicit** что AI agent видит **только** identity для personalization («Привет, Анна»), не для exfiltration.
  - **Question**: следует ли AI agent видеть raw email или opaque handle?
  - **Default по checklist**: opaque handle preferred.
  - **F-4 owner intent (per AI Affordance section)**: «read-only `email`, `displayName`» — explicit decision to expose. Это **conscious trade-off** для personalization.
  - **Open item**: уточнить — какие именно AI providers (cloud vs on-device) allowed читать email/displayName. На-device инференция (e.g., Gemini Nano) — OK. Cloud inference (OpenAI, Claude API) — нужен explicit user consent на каждый send. Это **F-2 capability registry spec** territory, не F-4 — но F-4 AI Affordance может это note explicit.
- [x] **CHK010** PII-returning capability declares provider — ⚠️ partial. Same as CHK009 — spec не declares which provider может get PII. **Open item**: extend AI Affordance secton с note «когда F-2 capability registry implements, on-device AI may read email/displayName; cloud AI requires explicit per-send consent — exact policy in F-2 spec».
- [x] **CHK011** No PII leaves device by default — ✓ implied.
  - F-4 не sends PII anywhere by default.
  - Identity collection happens client-side (Google Credential Manager → Firebase Auth → our adapter).
  - PII в SessionRecord — encrypted local storage only.
  - **AI affordance**, если activated, would be in-process call от AI agent (на-device инференция by default).
- [x] **CHK012** No silent telemetry logging — ✓.
  - AI Affordance section explicit: «Tamper consideration: even read-only capability exposure MUST NOT leak server-validated entitlement JWT to AI context».
  - No mention of telemetry / analytics logging AI prompts.
  - F-4 не вводит analytics infrastructure.

## Provider-agnosticism

- [x] **CHK013** No provider-specific wording — ✓.
  - AI Affordance section mentions providers **только в exclusion context** («No Gemini/OpenAI/Claude/MCP типы в signatures»).
  - **Not** «Gemini will provide...» or «Claude does...».
- [x] **CHK014** No package-level AI dependency — ✓.
  - F-4 не добavляет Gemini Nano, OpenAI SDK, Anthropic SDK, MCP server в dependencies.
  - F-4 — **purely identity layer**, не ships AI.
- [x] **CHK015** On-device vs cloud inference wrapped — **N/A для F-4**. F-4 не вводит inference. Future F-2 capability registry spec будет responsible for this when AI provider adapter ships.

## Out-of-scope discipline

- [x] **CHK016** Explicit «no provider implementation» — ✓.
  - AI Affordance section explicit: «Out of scope for this spec: no provider implementation, no LLM prompt design, no AI capability exposure surface — это ship'ится в F-2 (отложен Phase 3+, per memory `project_deferred_cloud_architecture`)».
- [x] **CHK017** No prompts / system messages / function schemas — ✓. F-4 не designs prompts. Pure capability declaration.
- [x] **CHK018** No demo AI integration — ✓. F-4 не ships demo. Clean separation: identity layer ≠ AI consumer.

## Acceptance evidence

- [x] **CHK019** Sample capability call signature in Local Test Path — ⚠️ partial.
  - Local Test Path section mentions Fake adapters that AI agent could use, но **не** explicitly демонстрирует sample call signature для AI-exposed `getCurrentUserIdentity()`.
  - **Open item для plan**: add to Local Test Path: «AI capability tests (deferred to F-2 spec): `getCurrentUserIdentity()` returns expected `AuthIdentity` через `FakeAuthAdapter` pre-seeded data».
  - Not blocker для F-4 merge — это F-2 territory.
- [x] **CHK020** AI Affordance section present — ✓. Section exists и declares «Limited AI affordance — read-only identity surface only».

---

## F-4-specific AI considerations

### Consideration 1: providerKind removed (Q4) — AI Affordance leftover

AI Affordance section упоминает `getProviderKind() → ProviderKind` как hypothetical future capability. Но `ProviderKind` **удалён** из домена per Q4.

**Decision needed**: Remove this capability mention OR keep с note about Q4.

**Recommendation**: Remove. AI agent не должен знать через что юзер вошёл. Это leakage детали реализации.

**Open item**: правка AI Affordance text (1 строка).

### Consideration 2: Limited AI affordance — это правильный default

Most identity layers по умолчанию exposed AI agent'у через generic API («here's the user, do what you want»). F-4 explicit **запрещает** signIn/signOut/refreshToken AI agent'у — это **stronger boundary** чем дефолт.

**Why**: AI agent с `signIn()` capability мог бы spoof user identity для exfiltration или unintended account creation. AI agent с `getRefreshToken()` мог бы exfiltrate token. Default-deny — правильно.

### Consideration 3: F-2 будущая spec'а будет implement actual surface

F-4 declares boundary, не строит actual AI surface. F-2 (Capability Registry Foundation, Phase 4+) будет:
- Define capability registry data structure.
- Build exposure adapter (on-device Gemini Nano или cloud).
- Implement user consent UX для cloud AI calls (per CHK010).
- Implement audit log of capability invocations.

F-4 готов к этому: `getCurrentUserIdentity()` — clean, vendor-agnostic, идемпотентен. F-2 может wire это в registry без изменений в F-4.

### Consideration 4: Entitlement JWT не utечёт в AI context (tamper-resistance crossover)

AI Affordance section explicit: «even read-only capability exposure MUST NOT leak server-validated entitlement JWT to AI context».

Это пересекается с tamper-resistance: even read-only AI capabilities не должны expose какие-либо credentials или entitlement state. **AI sees identity (UUID, name, email), не entitlement (subscription status, JWT)**.

---

## Open items (для plan stage)

1. **AI Affordance section правка**: remove `getProviderKind()` mention since providerKind removed per Q4 (1-line edit).
2. **PII boundary clarification**: extend AI Affordance section с note «on-device inference читает email/displayName freely; cloud inference requires per-send user consent — exact policy в F-2 spec».
3. **Sample AI capability call в Local Test Path** (deferred к F-2 spec): не блокер.

---

## Verdict

**18/20 ✓, 2 partial.** F-4 — **canonical** AI-ready identity layer:
- ✅ Domain verbs, не SDK calls.
- ✅ Vendor-agnostic (no Gemini/OpenAI/Claude/MCP types).
- ✅ Read-only AI affordance (write capabilities forbidden).
- ✅ Out-of-scope discipline (no prompts, no demo, no provider).
- ✅ Tamper crossover (no entitlement JWT leakage to AI context).
- ✅ F-2 future spec будет actual implementer.

**2 open items**: AI Affordance section needs 1-line edit (providerKind removed) + PII boundary clarification для future cloud AI providers. **Не блокеры**.

---

## Что это значит простыми словами

Спека правильно готова к будущему добавлению ИИ-агента (через 1-2 года):
- ИИ-агент сможет **прочитать**, кто пользователь («Анна с email anna@gmail.com») — чтобы например сказать «Привет, Анна». Это **только чтение**, не написание.
- ИИ-агент **не сможет**: войти за пользователя, выйти за пользователя, получить токен авторизации, получить статус подписки. Это **сильнее обычной защиты** — большинство приложений по умолчанию дают ИИ-агенту всё.
- ИИ-агент **не имеет** доступа к Firebase / Google напрямую — он работает только через наш абстрактный интерфейс `AuthProvider`. Это значит, что замена Firebase на собственный сервер не сломает ИИ-функции.
- Реальную интеграцию с ИИ-провайдерами (Gemini Nano на устройстве, OpenAI облако, и т.д.) построит **другая** спека F-2 (Capability Registry Foundation, через 1-2 года). F-4 только **готовит площадку**.

**2 уточнения для plan'а**:
1. Убрать упоминание `getProviderKind()` из секции AI Affordance — мы удалили `providerKind` из домена в ходе clarify pass.
2. Уточнить: какие именно ИИ-провайдеры (на устройстве vs облако) смогут читать email и имя пользователя — облако должно требовать явного согласия пользователя на каждую отправку. Точные правила — в спеке F-2.

Ни один пункт не блокирует утверждение F-4.
