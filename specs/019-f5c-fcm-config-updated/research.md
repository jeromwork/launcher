# Research: F-5c — One-way Doors & Alternatives Considered

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-06-21

> Per [CLAUDE.md rule 3](../../CLAUDE.md): для каждого one-way door documented alternatives considered, regret conditions, exit ramp.

## One-way doors в F-5c

### OWD-1: PushPayload wire-format breaking change (linkId → nullable)

**Decision**: Replace existing `PushPayload.linkId: String` (required) с new shape `linkId: String? = null` + first-class `ownerUid`, `eventType`, `triggerId` fields. Per [Clarifications Q1=C](spec.md#clarifications) (2026-06-20).

**Alternatives considered**:

| Alternative | Pros | Cons | Why rejected |
|---|---|---|---|
| **A: New `PushType.ConfigUpdated` sealed variant** + new payload type | Clean separation, no legacy mixing | Duplicates concept «config поменялся» (старый + новый = два type'а); legacy `ConfigChanged` остаётся indefinitely | Architectural clutter без long-term value |
| **B: Reuse existing `ConfigChanged`** + extend `extra: JsonObject` map | Minimum code change | `extra` becomes свалка; `linkId = ""` sentinel грязно; type-safety lost | Sentinel + freeform map = anti-pattern |
| **C: Breaking change `PushPayload` shape** ← **CHOSEN** | Cleanest data model; first-class fields; future schemaVersion 2 finally drops `linkId` | Migration of 8 existing files | No prod consumers; spec 008 rewrite parallel; migration manageable |

**Regret condition**: discovery что existing `LauncherPushReceiver.handleConfigChanged(linkId)` is actually called в production (i.e., some real device на pre-F-5c version). Then breaking change причинит crash для тех users.

**Mitigation**: grep audit done (8 files identified), все в migration scope. Pre-merge gate: verify no production app version released с current PushPayload shape (currently dev-only, no prod release).

**Exit ramp**: schemaVersion bump 1 → 2 + parallel reads на 1 major release (per FR-051, CLAUDE.md rule 5). Cost: 1-2 days work. Documented in spec.md Wire-format policy section.

---

### OWD-2: WorkManager-by-default dispatch (not fallback)

**Decision**: All push handlers dispatch через `BackgroundDispatcher` (= WorkManager на Android), не direct coroutine в `LauncherFirebaseMessagingService.onMessageReceived`. Per [scenarios discussion 2026-06-21](spec.md).

**Alternatives considered**:

| Alternative | Pros | Cons | Why rejected |
|---|---|---|---|
| **A: Direct coroutine в onMessageReceived, 10s budget, no fallback** | Simplest; no WorkManager dep | Fails for future long-running events (V-3 album photo download = МБ); two failure modes (timeout vs success) | Foundation для 9 consumers — config-only optimization wrong scope |
| **B: Direct coroutine с timeout, WorkManager fallback if exceeded** | Optimizes для typical case (config < 10s) | Two code paths (direct + fallback); per-event-type configurability hard | Two-path complexity без proportional benefit |
| **C: WorkManager-by-default + per-event-type timeout** ← **CHOSEN** | Unified model для all event types (config + future photos + future files); foundation handles lifecycle; consumer pure handler logic | +1 dep (WorkManager); +1 abstraction (BackgroundDispatcher port); ~50 LOC for adapter | 9 known consumers с varied payloads justify (rule 4 self-test) |

**Regret condition**: WorkManager itself becomes unreliable across OEMs (already known Xiaomi MIUI issues, mitigated через autostart deep-link). Если WorkManager fails > 5% events на real devices — degradation noticeable.

**Mitigation**:
- Reuse spec 007 OEM mitigations (autostart in wizard).
- Receiver idempotent (FR-044) — duplicate WorkManager retry safe.
- Pull-on-app-open ultimate safety net (FR-038).

**Exit ramp**: revert к direct coroutine + 10s budget — relatively easy refactor (single dispatcher swap). Cost: 1 day. But likely never needed.

---

### OWD-3: Hardcoded Worker URL в client code

**Decision**: Worker URL hardcoded constant `const WORKER_BASE_URL = "https://launcher-push.workers.dev/push"` в `core/push/androidMain/.../HttpPushTrigger.kt`. Per [Clarifications Q5=A](spec.md#clarifications) (2026-06-20).

**Alternatives considered**:

| Alternative | Pros | Cons | Why rejected |
|---|---|---|---|
| **A: Hardcoded URL** ← **CHOSEN** | Simplest; зеро config infrastructure | URL change requires app update + window when old clients hit deprecated endpoint | Sufficient для MVP; exit ramp tracked |
| **B: Custom domain сразу** (`api.familycare.app`) | URL stable forever; backend swap = DNS-level (no app update) | $20/year DNS + Cloudflare Pro setup сейчас | Premature ownership ceremony |
| **C: `*.workers.dev` + URL в Firestore remote-config** | Smen URL = update one doc, no app update | +1 Firestore read at app start (~50ms); risk if doc misconfigured | Over-engineering для уровня MVP |

**Regret condition**: production launch happens на `*.workers.dev`, потом мы хотим переехать на custom domain (rebranding / professional URL / Cloudflare account migration). Old prod app versions stuck на old URL — push channel breaks для тех users until they update app.

**Mitigation**: URL change coordinated с force-update mechanism (Firebase Remote Config min-app-version). Per [TODO-ARCH-001](../../docs/dev/project-backlog.md#todo-arch-001).

**Exit ramp**:
1. [TODO-ARCH-001](../../docs/dev/project-backlog.md#todo-arch-001) — custom domain setup ($20/year + DNS + Cloudflare Pages route).
2. New app version (deployed по schedule) reads new URL.
3. Old version users hit deprecated `*.workers.dev` URL — for 30 days CF redirect (302 → custom domain), then takedown.
4. Estimated cost: 1-2 days + 30-day deprecation window.

---

### OWD-4: Cloudflare Worker free tier hosting

**Decision**: Run Worker на CF free tier (`*.workers.dev`, free CPU 10ms, free KV 100K reads/day, no Workers Paid plan). Per CLAUDE.md rule 8 «бесплатный обход».

**Alternatives considered**:

| Alternative | Pros | Cons | Why rejected |
|---|---|---|---|
| **A: CF Workers free tier** ← **CHOSEN** | $0/month; zero ops; built-in DDoS protection; KV included | 10ms CPU limit (cold start risk); 100K KV reads/day limit | Sufficient для MVP scale (~5K daily calls) |
| **B: CF Workers paid tier** ($5/month) | 50ms CPU; unlimited KV; staging environment | $5/month operational cost | Premature for MVP; upgrade trivial when needed |
| **C: Own Ktor server (DigitalOcean, AWS, etc.)** | Full control; Firebase Admin SDK works; better observability | $10-30/month minimum; ops burden (deploy, monitor, scale) | Massive overhead для MVP |

**Regret condition**: CF Worker free tier limits hit в production (CPU exceeded, KV reads exhausted) — degraded service for paying customers.

**Mitigation**: monitoring via CF Analytics; alert at 80% threshold; upgrade к paid tier ($5/month) — instant operation.

**Exit ramp**: full migration к own backend per [SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md). Worker code ports 1:1 к Ktor server (Kotlin). Auth-jwt module portируется separately (Java JOSE library). Estimated: 5-7 days work when triggered. Documented in detail в server-roadmap.

---

### OWD-5: Firebase Auth ID-token as caller authentication

**Decision**: Worker validates Firebase Auth ID-token (issued by Google Firebase Auth) for caller authentication. Per F-4 / spec 017 — entire system uses Firebase Auth as identity provider.

**Alternatives considered**:

| Alternative | Pros | Cons | Why rejected |
|---|---|---|---|
| **A: Firebase Auth ID-token** ← **CHOSEN** | Already implemented в F-4; Google handles user storage; SSO with other Google services | Tight coupling к Google; migration к own auth = invasive | F-5c должен not introduce new identity provider; inherits F-4's choice |
| **B: Issue our own JWTs from custom auth server** | Full control; provider-agnostic; future-ready | Requires building entire auth server now; F-4 already shipped с Firebase | Premature; F-4 territory not F-5c |
| **C: API key per device** | Simple; no JWT validation overhead | Static credentials = security risk; rotation hard; no user identity | Inadequate для multi-user system |

**Regret condition**: Firebase Auth deprecated by Google или pricing changes drastically. Mitigation: F-4 territory — migration к own auth covered в F-4's server-roadmap.

**Exit ramp**: auth-jwt module designed для multiple providers. When F-4 introduces own auth provider:
1. auth-jwt module extends к accept tokens from new issuer (`iss` validation).
2. Mapping table «Google identity → internal UID» lives в auth-server.
3. Worker continues to call `verifyFirebaseIdToken()` — module name preserves даже if Firebase phased out (rename later if needed).
4. F-5c code unchanged — abstraction at auth-jwt boundary holds.

Documented в [auth-jwt module README](../../workers/_shared/auth-jwt/README.md) (written during implementation) + [SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md).

---

### OWD-6: FCM as push transport

**Decision**: Use Google FCM (Firebase Cloud Messaging) as the push transport mechanism. No abstraction layer для switching к other push providers (APNS, WebPush) сейчас.

**Alternatives considered**:

| Alternative | Pros | Cons | Why rejected |
|---|---|---|---|
| **A: FCM only** ← **CHOSEN** | Standard для Android; free unlimited tier (no quota issues для consumer apps); proven scale | Android-only (no iOS push without separate APNS); Google dependency | Project Android-first; iOS = Phase 4 future |
| **B: Universal push abstraction** (FCM + APNS adapter) | Future-ready для iOS | Premature; APNS code unused; complexity now для unrealized benefit | Rule 4 violation — no current consumer |
| **C: WebPush** | Provider-neutral standard; works in browsers + Android (limited support) | Limited adoption на Android; user must install PWA или special wrapper | Inadequate UX |

**Regret condition**: iOS app launched (V-1 в Phase 4) — needs APNS. Switching transport painful.

**Mitigation**:
- F-5c уже сделал event-level abstraction (`PushTrigger` port). Transport (FCM) lives в `dispatch/fcm-dispatcher.ts` (Worker) + `FcmTokenPublisher` (Android adapter).
- When iOS arrives — add `workers/push/src/dispatch/apns-dispatcher.ts` parallel к fcm-dispatcher; add iOS adapter `ApnsTokenPublisher`. Event-level code unchanged.
- Worker dispatcher selection by recipient device platform (added field в RecipientDeviceEntry).

**Exit ramp**: 5-7 days work when triggered (iOS spec V-1 start). Per server-roadmap.

---

### OWD-7: Workers KV as ephemeral cache

**Decision**: Use Cloudflare Workers KV для (a) JWKS cache, (b) idempotency dedupe (10-min TTL), (c) rate-limit counters (per-window TTL). Eventually consistent (60s globally). Per [Clarifications Q3](spec.md#clarifications).

**Alternatives considered**:

| Alternative | Pros | Cons | Why rejected |
|---|---|---|---|
| **A: Workers KV** ← **CHOSEN** | Free tier (100K reads/day); CF-native; minimum ops | Eventually consistent (60s global); per-instance Map не coordinates | Acceptable trade-off (receiver idempotency защищает downstream) |
| **B: Cloudflare Durable Objects** | Strongly consistent; stateful | Paid feature ($5/month minimum); over-engineering для current scale | Premature |
| **C: External Redis** (Upstash, Redis Cloud free tier) | Strongly consistent; sub-millisecond latency | Extra dependency; network hop ~10-50ms (CF Worker → Redis); auth setup | Network overhead defeats purpose |

**Regret condition**: KV eventually-consistent 60s window allows duplicate idempotency hits — caller's Idempotency-Key dedupe missed by Worker instance на other edge node. Result: duplicate FCM dispatches.

**Mitigation**:
- Receiver idempotent (FR-044 — debounce 2s on `triggerId`).
- FCM `collapse_key` (FR-008) — collapses duplicates on FCM side.
- Defence-in-depth: even if KV misses, downstream protected.

**Exit ramp**: upgrade к Durable Objects ($5/month) for stronger consistency if duplicate rate exceeds 1%. Or migrate к own Redis post-backend-migration. Documented в SRV-PUSH-FOUNDATION.

---

### OWD-8: Auth-jwt as TypeScript module (not Kotlin / not separate repo)

**Decision**: `workers/_shared/auth-jwt/` is TypeScript module within monorepo, imported by Worker via relative path / npm workspaces. Per [scenarios discussion 2026-06-21](spec.md).

**Alternatives considered**:

| Alternative | Pros | Cons | Why rejected |
|---|---|---|---|
| **A: TypeScript module в monorepo** ← **CHOSEN** | Same language as Worker; immediate extraction-ready; zero extra ops | TypeScript-only (cannot reuse в Kotlin / Java backend без re-write) | Acceptable — own backend migration involves re-write anyway |
| **B: Standalone repo с published npm package** | Reusable by future Workers + external projects | Premature; one consumer (Worker); ops overhead | Wait for second consumer trigger (TODO-ARCH-018) |
| **C: Kotlin module (KMP)** в `core/auth-jwt/` для shared with Android client | Could reuse если Android needs JWT validation | Android does NOT validate JWT (only acquires + forwards); KMP module solving non-existent problem | Wrong audience |

**Regret condition**: own backend migration starts (per SRV-PUSH-FOUNDATION), but auth-jwt code не easily portable к Kotlin/Java.

**Mitigation**: auth-jwt API surface (1 function: `verifyFirebaseIdToken`) trivially re-implementable в Java JOSE library (Nimbus). Migration cost ~1 day. Documented в auth-jwt README.

**Exit ramp**: при backend migration — rewrite auth-jwt в Kotlin/Java alongside other Worker porting. Worker JS imports become Java imports — API signature unchanged.

---

## Cross-spec architectural inheritance

F-5c inherits several decisions made by upstream specs without independent justification:

- **Firebase UID as primary identity** — inherited from F-4 (spec 017). F-5c uses `ownerUid: String` = Firebase UID directly. Exit ramp = F-4 territory.
- **Firestore as primary persistence** — inherited from F-5b (spec 018). F-5c extends F-5b directory entry с `fcmToken` field. Exit ramp = F-5b/SRV-PUSH-FOUNDATION migration.
- **Named Google Sign-In (not anonymous)** — inherited from decision 2026-05-30. F-5c does not deal с anonymous mode.
- **Device self-sufficiency principle** — inherited from decision 2026-06-15. F-5c CLOUD-only, NullPushTrigger fallback.

## Decisions explicitly NOT taken (out of scope)

- **Subscription gating** (entitlement check before push): S-10 territory (Phase 2). F-5c respects 401 from Worker — does not implement gating itself.
- **Group management UX** (add/remove admin, role enum): S-2 territory (Phase 2). F-5c uses existing F-5b grants — does not implement UI.
- **Per-event AI surfaces** (e.g. «AI summarises config diff»): N/A AI affordance — F-5c pure transport.
- **iOS APNS adapter**: Phase 4 (V-1). F-5c port-ready через BackgroundDispatcher abstraction.
- **Capability Registry integration**: deferred к F-2 в Phase 4+ per roadmap. F-5c provides inline `TODO(capability-registry)` markers за consumers (S-4, V-2) когда они write their specs.

---

## Краткое резюме (для не-разработчика)

Этот документ перечисляет **8 архитектурных решений**, которые сложно откатить позже (one-way doors). Для каждого:

1. **Что решено** + **почему именно так**.
2. **Какие альтернативы рассматривали** (обычно 2-3) + **почему отказались**.
3. **При каких обстоятельствах решение окажется плохим** (regret condition).
4. **План отхода** (exit ramp — что делать когда станет плохо).

**Главные решения**:
1. **Сломать существующий PushPayload format** (поле `linkId` → опционально) — это OK, потому что нет prod consumer'ов.
2. **WorkManager везде по умолчанию** (не fallback) — для поддержки будущих больших payload (фото, файлы).
3. **Hardcoded Worker URL** в client коде — простейший вариант, exit ramp на custom domain зафиксирован в backlog.
4. **Cloudflare Worker free tier** — $0/месяц, upgrade на платный тариф ($5/мес) когда упрёмся в лимиты.
5. **Firebase Auth ID-token** для auth — унаследовано от F-4, миграция на свой auth провайдер — F-4 territory.
6. **FCM only** (не делаем универсальный push) — iOS APNS добавится в Phase 4, foundation позволит без переписывания.
7. **Workers KV** для кэша (eventually consistent) — приемлемо, защита через идемпотентность receiver'а.
8. **Auth-jwt как TypeScript module** в monorepo — может быть extracted в отдельный repo когда появится второй consumer.

**Все 8 решений имеют документированные exit ramps** — не загнаны в угол.
