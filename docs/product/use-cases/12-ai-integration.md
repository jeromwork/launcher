# 12. AI Integration — голосовое управление, агенты, MCP

> **Status**: 🆕 первый проход · **Created**: 2026-05-27
> **Зачем читать**: AI быстро становится первичным интерфейсом ко многим продуктам. Если наша архитектура не подготовлена — мы окажемся **невидимыми для AI-агентов** через 1-2 года и проиграем конкурентам, которые подготовились. Это **architectural posture**, а не «фича».
> **Источник**: ресёрч 2026-05-27 (Android App Actions, Gemini Nano, MCP, AI-launcher trends).

---

## Финальные резолюции (2026-05-27 evening)

> Все D-вопросы AI закрыты. Зафиксированный подход — **AI-ready architecture, ZERO adapter implementations в MVP**.

| D | Резолюция |
|---|---|
| **D-17** | **AI-ready, без provider implementations**: `CapabilityRegistry` + `ExposureAdapter` interface + FakeAdapter для тестов + capability declarations. **НЕ строим в MVP**: App Actions adapter, MCP server, Gemini Nano integration. Каждый adapter — отдельная implementation spec позже. |
| **D-18** | **DEFERRED** до implementation spec на конкретный adapter (когда нужно). В MVP не решаем. |
| **D-19** | **DEFERRED** до MCP adapter implementation spec. В MVP не решаем. |
| **D-20** | **Создаём `checklist-ai-readiness`** skill в `.claude/skills/`, парадигма spec-kit checklist'ов. Активация через `procedure-assess-spec-complexity`. |
| **D-21** | **AI affordance — обязательная ось roadmap-обсуждений** наряду с accessibility / privacy / one-way doors. |

### Что строим в MVP

```
core/
└── capability/                       ← NEW: AI-ready layer
    ├── CapabilityRegistry            ← port (interface)
    ├── Capability (data class)
    ├── ExposureAdapter               ← port для adapters
    └── FakeExposureAdapter           ← для тестов (rule 6 mock-first)
```

Каждая существующая user-facing action в спеках 005, 006, 009, 010 получает capability declaration. Сервис dispatcher'а маршрутизирует через registry. **Никакие реальные adapter'ы не пишутся** до отдельных спек.

### Action items для следующих шагов

- [ ] Создать skill `checklist-ai-readiness` в `.claude/skills/checklist-ai-readiness/SKILL.md`.
- [ ] Обновить `procedure-assess-spec-complexity` чтобы активировать новый checklist на user-facing / wire-format спек.
- [ ] Зафиксировать в spec-template обязательную секцию «AI Affordance» (короткий блок: «как этот feature доступен через AI, или явно Documented AI Asymmetry»).
- [ ] (Когда понадобится первый adapter) Написать spec на конкретный adapter — App Actions / MCP / etc.

---

## Что это за документ (просто)

Раньше пользователь нажимал кнопки. Потом стал говорить голосом («окей Гугл»). Сейчас — даёт задачи AI-агенту («подбери бабушке удобный пресет»), а агент сам ищет, решает, делает.

В нашем продукте это особенно важно:

- **Для Managed (бабушки)**: голос снижает cognitive load. Бабушка может сказать «позвони Артёму», даже если не помнит, где плитка. Research (PMC 2026) подтверждает — голос **снижает** нагрузку, но для 80+ usability падает (нужно zero-touch).
- **Для Admin'а**: вместо того, чтобы открывать наш app, заходить в editor, искать контакт, admin может сказать любому AI-агенту (Claude / Gemini / Siri) «добавь бабушке контакт Артёма с фото».

Это **не «фича AI»** — это вопрос «как наш продукт **доступен** для AI». Можно построить продукт так, что **никакой** AI не сможет с ним работать (захардкоженный UI, нет API), а можно построить так, что **любой** агент сможет (структурированные intents, MCP server, voice-friendly metadata).

Этот документ — про **архитектурную позу**: что мы делаем (не делаем) сейчас, чтобы через год-два не пришлось переписывать.

## Три слоя AI-экспозиции (mental model)

Это **главное**, что надо понять. AI касается нашего продукта на **трёх разных уровнях**, и для каждого свой подход.

### Слой 1 — AI внутри нашего app

Мы сами строим AI-функции. Например: image description для accessibility обрабатывается **внутри** app'а через Gemini Nano (on-device) или OpenAI API (cloud).

**Gemini Nano — насколько «умна»?**

- Размер ~3 млрд параметров. В ~100 раз меньше cloud-моделей (GPT-5 / Claude / Gemini Pro).
- Для **open-ended reasoning** — да, слаба. «Подбери бабушке удобный пресет» она не сделает.
- Для **узких структурированных задач** — конкурентна с cloud: summary, classification, image description, smart reply, spam detection.

**Что Gemini Nano реально может дать нам**:

| Use case | Зачем именно on-device Nano |
|---|---|
| Image description для accessibility («фото: улыбающийся мужчина с белой бородой») | privacy: фото внука никогда не уходит в облако |
| Auto-suggest contact name при загрузке фото admin'ом | UX-helper для admin'а |
| Spam call detection перед звонком бабушке | системный Google Phone уже использует Nano — можно reuse |
| Smart-empty-state copy в локальном языке | контекстная подсказка для бабушки |

**Что Nano НЕ должна делать у нас**:
- голосовое управление («позвони Артёму») — это **Слой 2** (Google Assistant), не наше.
- сложные рекомендации admin'у — это **Слой 3** (Claude/Gemini Pro через MCP), не on-device.

**Pros**: полный контроль UX, работает оффлайн, privacy-friendly (AICore не сохраняет input/output).
**Cons**: дорого (engineering effort), maintenance высокий, **дублируем то, что OS даёт** (если задача в области Layer 2).

**Когда оправдано**: только узкие privacy-sensitive on-device задачи, которых **нет в OS** (например, image description для contact photo, чтобы фото никогда не уехало в Google).

### Слой 2 — AI через OS

Не строим AI сами. **Экспонируем** наш app для системных AI-агентов (Google Assistant, Apple Siri, в будущем Apple Intelligence). На Android это делается через **App Actions** — XML-декларация в `shortcuts.xml`, и Google Assistant начинает понимать «позвони Артёму через [наш app]».

**Pros**: дешево (дни работы), пользуемся Google/Apple-вкладами в NLU, privacy не наша проблема (это решение user'а пользоваться Google).
**Cons**: ограничены тем, что платформа поддерживает; не differentiator.

**Когда оправдано**: **всегда**. Это **базовый минимум** в 2026 году.

### Слой 3 — AI снаружи (любой агент через MCP)

Мы экспонируем наш app (или его серверную сторону) через **MCP (Model Context Protocol)** — открытый стандарт от Anthropic (открыт Nov 2024, к 2026 стал де-факто стандартом, в Linux Foundation).

Тогда **любой AI-агент** — Claude Desktop / Cursor / любой Gemini-инстанс / любой OpenAI-агент / Claude Code — может управлять нашим продуктом. Admin сидит в Claude и говорит «настрой бабушке пресет для слабовидящих и добавь контакт Артёма» — Claude вызывает наш MCP, мы выполняем.

**Pros**: гигантский multiplier — у admin'а уже есть его любимый AI-агент, и наш продукт **подключается без работы с нашей стороны**. Конкурентное преимущество — большинство нашей категории этого не сделает.
**Cons**: security (кто может звать MCP), privacy (что отдаём в context), новизна (MCP всего 1.5 года).

**Когда оправдано**: для admin-стороны — **очень**. Для Managed (бабушки) — пока нет (бабушка не будет писать prompt'ы Claude'у).

## Архитектурный паттерн: Capability Registry + Exposure Adapters

**Ключевой архитектурный insight** (зафиксирован в discussion 2026-05-27 — user-replica «те же фичи, по разному отдаём тулзы»):

> Внутри — **один** Capability Registry. Снаружи — **много** Exposure Adapter'ов, каждый переводит каталог возможностей в формат одного конкретного AI-consumer'а.

```
       External AI consumers
       ─────────────────────
       Google Assistant   Claude/Gemini   Siri (iOS)   Future agent
              │                │             │              │
       ┌──────┴──────┐  ┌──────┴────┐ ┌──────┴─────┐ ┌──────┴────┐
       │ AppActions  │  │ MCP       │ │ Siri       │ │  ???      │
       │  Adapter    │  │ Server    │ │ Shortcuts  │ │  Adapter  │
       │ (Android)   │  │ (HTTP)    │ │  (iOS)     │ │           │
       └──────┬──────┘  └──────┬────┘ └──────┬─────┘ └──────┬────┘
              └────────────────┼─────────────┼──────────────┘
                               │             │
                  ┌────────────┴─────────────┴────────────┐
                  │  Capability Registry (single source)  │
                  │  • intent name (machine-readable)     │
                  │  • human-readable description         │
                  │  • voice-friendly phrases             │
                  │  • parameters + types                 │
                  │  • idempotency / auth / confirmation  │
                  └─────────────────┬─────────────────────┘
                                    │
                       ┌────────────┴────────────────┐
                       │ Domain (actions / contacts /│
                       │ config — спеки 005, 006,    │
                       │ 008, 009)                   │
                       └─────────────────────────────┘
```

### Свойства паттерна

- **Capability добавляется один раз** — становится доступной всем consumer'ам автоматически.
- **Новый AI-агент через год** → пишется один Adapter (1-3 дня), core не трогается.
- **Domain не знает о существовании AI** — знает только Capability Registry.
- **Adapter не содержит business logic** — только перевод формата.
- **Risk mitigation**: если MCP проиграет конкурирующему стандарту — пишем Adapter под новый, core не меняется.

### Это не новое — это расширение CLAUDE.md rule 2 (ACL)

**Anti-Corruption Layer** мы уже применяем для Firebase, Cloudflare Worker, vendor SDK. Каждый — port в domain'е, adapter в androidMain / iosMain / push-worker. AI-consumers — это **просто ещё один класс external dependencies**, к которым применяется тот же принцип.

```kotlin
// commonMain/domain/
interface CapabilityRegistry {
    fun list(): List<Capability>
    fun describe(intentName: String): Capability?
    suspend fun invoke(intentName: String, params: Map<String, Any>, actor: Actor): InvokeResult
}

interface Capability {
    val intentName: String        // "call_contact"
    val description: String       // "Initiate a call to a contact"
    val voicePhrases: List<String> // ["позвонить", "набрать"]
    val params: List<Param>        // [Param("contact_name", String)]
    val idempotent: Boolean
    val requiresConfirmation: Boolean
    val auth: AuthScope            // ManagedSelf / Admin / AnyAdmin
}
```

```kotlin
// androidMain/adapter/appactions/
class AppActionsAdapter(private val registry: CapabilityRegistry) {
    fun generateShortcutsXml(): String { /* maps to BII / Custom Intent */ }
    suspend fun handleAssistantIntent(intent: Intent) { registry.invoke(...) }
}

// push-worker/adapter/mcp/
class McpServerAdapter(private val registry: CapabilityRegistry) {
    fun listTools(): McpToolsResponse { /* maps to JSON Schema tools */ }
    suspend fun callTool(req: McpCallToolRequest) = registry.invoke(...)
}
```

### Что у нас уже есть для этого паттерна

- **Спека 005 (Action Architecture v2)** даёт intent-based wire format с `providerId`, `params`, dispatch model. Это **уже частичная Capability Registry** — нужно расширить metadata для AI consumer'ов.
- **Спека 006 (Provider Capabilities)** — structured health snapshots, готовы стать MCP resources (read-only data exposure).
- **`core/` (KMP commonMain)** — естественное место для Registry.
- **Дисциплина ports + adapters** уже принята проектом — применяем тот же подход.

### Что нужно добавить

1. **Расширить Action wire-format AI-метаданными**:
   - `humanReadableDescription: String` — для AI: «когда использовать этот action».
   - `voicePhrases: List<String>` — для App Actions BII matching.
   - `confirmation: ConfirmationRequirement` — нужно ли user-prompt перед выполнением.

2. **Создать `CapabilityRegistry` port** в `core/commonMain/domain/`.

3. **Первый AppActions Adapter** в `androidMain/adapter/appactions/`:
   - Генерирует `shortcuts.xml` из Registry на build-time или runtime.
   - Обрабатывает Assistant intents через broadcast receiver.

4. **Первый MCP Server Adapter** в `push-worker/` (Cloudflare Worker):
   - HTTP endpoint `/mcp` с MCP протоколом.
   - Authorization: admin's Firebase ID token.
   - Tools: list_managed_devices, get_config, apply_config, list_contacts, add_contact, и т.д.

5. **Fake adapter для testing** (CLAUDE.md rule 6 mock-first): InMemoryCapabilityRegistry для unit-тестов action'ов без full setup.

## Главные понятия (просто)

- **App Actions** — Android-механизм, который позволяет Google Assistant понять «позвонить Артёму через [наш app]». Декларируется в `shortcuts.xml`, поддерживается с Android 5.
- **Built-in Intent (BII)** — заранее заготовленные Google категории действий: `CALL`, `CREATE_MESSAGE`, `OPEN_APP_FEATURE`. Мы привязываем свои функции к BII.
- **Custom Intent** — наше собственное действие, если BII не подходит (например, «настрой бабушке пресет»).
- **Gemini Nano** — Google's on-device LLM, работает на Pixel 8+ (2024) и Pixel 10+ (2026). Запускается через AICore system service, **AICore не сохраняет input/output**. Бесплатно, privacy-positive. Использования: summary, smart reply, image description.
- **AICore** — Android system service для on-device AI. Сам обеспечивает privacy (isolation per-request).
- **ML Kit GenAI APIs** — публичный SDK Google для использования Gemini Nano из app'а.
- **MCP (Model Context Protocol)** — открытый протокол от Anthropic, ставший стандартом в 2025-2026 для AI-agent integration. Похож на «USB для AI» — любой агент подключается к любому продукту через единый интерфейс. Sandbox / authorization / discovery — всё в стандарте.
- **MCP server** — программа, которая экспонирует наши функции для AI-агентов. Может работать как HTTP-сервер, как stdio-процесс, или embedded в app'е.
- **AI affordance** — насколько фича «доступна для AI». High = есть structured intent + clear semantic + metadata. Low = захардкожен UI, ничего не выразить.
- **Tool / function** — единица возможностей в MCP. Например: `add_contact(name, phone, photo_url)`. Агент вызывает tool, получает результат.
- **Voice-first design** — UX-принцип: думаем сначала «как это сказать», потом «как это нажать». Полезно для accessibility, особенно для нашей аудитории.

## Use case инвентарь — AI-specific сценарии

User в реплике дал прямые примеры. Разбил их по слоям:

| ID | Сценарий | Слой | Закрытие |
|---|---|---|---|
| AI-001 | «Голосом позвони внуку Артёму» (Managed) | Слой 2 (App Actions BII `CALL`) | ❌ — нужна спека |
| AI-002 | «Голосом добавь контакт менеджер по строительству» (Admin) | Слой 2 или 3 | ❌ |
| AI-003 | «Подбери бабушке удобный пресет → я проверю → применю» (Admin через AI-агент) | Слой 3 (MCP) | ❌ |
| AI-004 | «Что у бабушки случилось за неделю?» (Admin → AI) | Слой 3 (MCP read-only access) | ❌ |
| AI-005 | «Позвони мне в 18:00 напомни принять таблетку» (Managed) | Слой 2 (Google Assistant reminders) | ❌ — out of scope, делегируем |
| AI-006 | «Сделай так, чтобы бабушке было видно лучше» (Admin via AI) | Слой 3 | ❌ |
| AI-007 | AI proactive: «бабушка не звонила Артёму неделю — напомнить?» | Слой 1 (on-device pattern detection) | ❌ |
| AI-008 | TalkBack / Voice Access (system-level AI accessibility) | Слой 2 (system services) | 🟡 (Часть в плане для 03 UI) |
| AI-009 | Claude Code / Cursor / другой AI-помощник для разработчиков, который знает наш app | Слой 3 (dev MCP server) | 🔮 интересно для DV-004 (S-105 boil) |
| AI-010 | Vendor / clinic AI-агент мониторит health Managed-устройств | Слой 3 + 011 OWD-5 | 🔮 future |
| AI-011 | AI авто-формулирует contact description для photo recognition | Слой 1 (Gemini Nano image description) | 🔮 |
| AI-012 | AI-генерация empty state copy / помощь бабушке «что сделать сейчас» | Слой 1 | ❌ |

## Главные открытые вопросы

### D-17. AI в MVP или после

**Контекст**: AI становится default-интерфейсом в 2026. Можно либо подготовиться сразу, либо догонять потом.

**Варианты**:
- **AI в MVP polностью**: Layer 1 + 2 + 3 сразу. Огромная work, но differentiator.
- **AI-ready architecture, без features**: ничего AI-специфичного не строим, но **структурно** готовы (intent-based actions, MCP-ready, voice metadata). **Рекомендуется.**
- **Игнорируем сейчас**: дешево, но через 1-2 года придётся переписывать.

**Регрет** для «игнорируем»: action architecture (спека 005) и provider registry (006) **уже отчасти intent-based** — это хорошо. Но wire format, security, identity — не AI-aware. Через 2 года = переписать spec 005-008.

**Регрет** для «полностью в MVP»: расширение scope MVP на ~30-50%. Запуск отложится.

**Рекомендация (best-guess)**:
- **Layer 2 (App Actions)** — **в MVP** (несколько дней работы, гигантский UX win для голосовых сценариев). Закрывает AI-001, AI-002, AI-005, AI-008.
- **Layer 3 (MCP)** — **в MVP скелет**: создать MCP server в admin-app с базовыми tools (list devices, get config, apply config). 1-2 недели. Это **сейчас differentiator**, через год — table stakes.
- **Layer 1 (on-device AI features)** — **post-MVP**. Gemini Nano integration в будущем для smart suggestions, photo description, etc. **Inline TODO в коде** — оставлять «зацепки» там, где Gemini Nano даст value.

### D-18. Privacy posture для AI

**Контекст**: AI — privacy-чувствительная тема. Voice data, photo recognition, contact data — всё это могут «утечь» через AI-вызовы.

**Варианты**:
- **On-device only (Gemini Nano)**: privacy-perfect, но ограничено возможностями локальной модели и устройства (Pixel 10+ для multimodal).
- **Cloud + opt-in**: больше capability, но нужен явный consent.
- **Hybrid**: routine задачи on-device, sophisticated — cloud + opt-in.

**Связан с D-12 (telemetry posture) и D-16 (crash collection)**.

**Рекомендация**: **on-device по умолчанию** + cloud только с явным opt-in admin'а (не Managed). Senior privacy первичен.

### D-19. MCP server — где живёт

**Контекст**: MCP server — это endpoint, к которому подключается AI-агент. Где он живёт физически?

**Варианты**:
- **В admin app (на устройстве admin'а)**: stdio MCP server, локальный. Agent должен быть на том же устройстве. Простой security. Минус — нельзя подключить cloud-агента.
- **В Cloudflare Worker** (расширяем существующий): HTTP MCP server. Любой агент подключается. Минус — нужна authorization (admin uid → MCP session).
- **В обоих местах**: local для quick prototyping, cloud для production scenarios.

**Рекомендация**: cloud (Cloudflare Worker), потому что это закрывает больше сценариев (AI-003 admin сидит в Claude desktop и говорит — Claude должен достучаться до **нашего** сервера, не до admin's phone). Authorization через admin's Firebase ID token.

### D-20. AI-readiness checklist — отдельный skill или часть существующих

**Контекст**: user предложил «создать skill / github speckit /checklist что бы всегда проверялось на ии».

**Варианты**:
- **Новый checklist skill** (`checklist-ai-readiness`): отдельный, инвокируется автоматически если спека касается user-facing flow или wire format. Чек: «есть ли intent для AI? есть ли voice-friendly description? есть ли machine-readable result?»
- **Добавить пункты в существующие checklist'ы**: расширить `checklist-ux-quality` и `checklist-wire-format`. Минус — размывается.
- **Не делаем checklist, делаем в spec-template**: добавить «AI Affordance» section в обязательный template. Минус — passive, не enforce.

**Рекомендация**: **новый checklist skill** (вариант 1). Можно сделать частью `procedure-assess-spec-complexity` — автоматически включается, если спека касается user action / wire format / external API.

### D-21. Roadmap обсуждения — AI как ось проверки

**Контекст**: user спросил «в роадмап обсуждения тоже нужно обсуждать?» — да.

**Что добавить**: при роадмап-планировании каждая спека сверяется по оси «AI affordance»:
- Слой 1: нужно ли on-device AI feature?
- Слой 2: нужны ли App Actions / voice-discoverable intents?
- Слой 3: нужно ли экспонировать через MCP?

Без этого через 2 года половина спек окажется AI-blind.

## Что в спеках уже зафиксировано (relevant)

| Спек | Что даёт для AI-readiness |
|---|---|
| 005 Action Architecture v2 | **intent-based actions с wire format** — это **отличная** база для AI exposure. Каждое action уже structured. |
| 006 Provider Capabilities | health snapshot — structured, AI-readable |
| 008 Bidirectional Config Sync | wire format для config — может стать tool через MCP |
| 011 Crypto Foundation | per-app identity (OWD-5) — это уже подготовка к **per-AI-agent identity** (vendor / partner / agent — каждый свой) |
| `RemoteSyncBackend` port | adapter pattern — может стать MCP transport |

**Главное**: наша архитектура (intent-driven actions + ports + structured wire format) **уже частично AI-ready**, благодаря дисциплине из CLAUDE.md rules 1-2. Мы не сильно отстаём — нужно дополнить, а не переписывать.

## Источники (research 2026-05-27)

### Layer 2 (App Actions / Assistant)
- [App Actions — Google for Developers](https://developers.google.com/assistant/app)
- [App Actions in Android](https://developer.android.com/develop/devices/assistant/overview)
- [Push dynamic shortcuts to Assistant](https://developer.android.com/develop/devices/assistant/dynamic-shortcuts)
- [Codelab — Extend Android app to Google Assistant](https://codelabs.developers.google.com/codelabs/appactions)

### Layer 1 (On-device AI / Gemini Nano)
- [Gemini Nano — Android Developers](https://developer.android.com/ai/gemini-nano)
- [Latest Gemini Nano + ML Kit GenAI APIs](https://android-developers.googleblog.com/2025/08/the-latest-gemini-nano-with-on-device-ml-kit-genai-apis.html)
- [Privacy and safety for Gemini Nano](https://android-developers.googleblog.com/2024/10/introduction-to-privacy-and-safety-gemini-nano.html)

### Layer 3 (MCP)
- [Model Context Protocol — official](https://modelcontextprotocol.io/)
- [MCP Wikipedia](https://en.wikipedia.org/wiki/Model_Context_Protocol) (история, статус)
- [Complete Guide to MCP in 2026 — DEV.to](https://dev.to/x4nent/complete-guide-to-mcp-model-context-protocol-in-2026-architecture-implementation-and-4a11)
- [MCP Adoption Statistics 2026](https://www.digitalapplied.com/blog/mcp-adoption-statistics-2026-model-context-protocol)
- [MCP Apps blog — Anthropic](https://blog.modelcontextprotocol.io/posts/2026-01-26-mcp-apps/)

### AI + Elderly
- [Factors influencing older adults adoption of AI voice assistants — PMC 2026](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12641254/)
- [VOLI — Voice Assistant for Quality of Life](https://today.ucsd.edu/story/making-voice-assistants-accessible-for-older-patients)
- [Bridging the Cognitive Gap — voice chatbot для пожилых, arXiv 2026](https://arxiv.org/pdf/2603.11303)
- [AI Tools Helping Elderly Users — TechTimes 2026](https://www.techtimes.com/articles/316939/20260521/how-ai-tools-are-helping-elderly-users-daily-life-accessibility.htm)

### AI + Launchers
- [Nova Launcher AI features apk-teardown](https://www.androidauthority.com/nova-launcher-ai-plus-subscription-apk-teardown-3658932/) — Nova AI был proactive suggestion model
- [AI-Powered Home & Lock Screen Customization](https://medium.com/design-bootcamp/ai-powered-home-lock-screen-customization-9fa50386a5f4)
- [I Tested 12 AI Agent Apps on Android — 2025](https://proditive.medium.com/i-tested-12-ai-agent-apps-on-android-my-5-essential-productivity-tools-for-2025-63ce8c9dcd7e)
- [minitap-ai/mobile-use GitHub](https://github.com/minitap-ai/mobile-use) — AI agents driving real Android apps like a human

## Связь с другими документами

- **01 Vision** — AI становится конкурентным фактором. Affecting positioning (D-1 self-serve more viable with AI helper).
- **02 Actors** — actor A6 (Dev) выигрывает от AI-developer-tools (Claude Code via MCP). Может появиться actor A7 (Claude / Gemini / любой AI-агент как «virtual admin»).
- **03 Launcher UI** — voice как первичный input, особенно для accessibility (D-8 dwell + voice fallback).
- **04 Remote management** — admin UX через AI: «настрой бабушке так, чтобы...».
- **06 Communications** — App Actions BII `CALL` закрывает голосовые звонки.
- **07 Data & privacy** — D-18 privacy posture для AI; on-device vs cloud.
- **09 Backend** — MCP server где живёт = backend choice.

## Конкретные next steps (если решение «AI-ready посevia not full AI»)

### 1. Создать новый checklist-skill `checklist-ai-readiness`

В `.claude/skills/checklist-ai-readiness/SKILL.md`. Активируется через `procedure-assess-spec-complexity`, если spec содержит user action, wire format, или external surface. Проверяет:
- Есть ли App Actions BII или Custom Intent для user-facing flow?
- Есть ли voice-friendly description?
- Есть ли machine-readable result?
- Есть ли MCP tool definition (если касается admin)?
- Privacy: какой data path при AI usage (on-device / cloud / hybrid)?
- Когда фича недоступна через AI — это документировано (Documented AI Asymmetry, аналогично Documented Platform Asymmetry в ADR-005)?

### 2. Добавить «AI Affordance» секцию в spec-template

Каждая новая спека отвечает на вышесказанные вопросы.

### 3. Заложить MCP server skeleton в Cloudflare Worker

Существующий push-relay Worker (007) — добавить routes под MCP protocol. 1-2 недели. Authorization через admin's Firebase ID token. **Это backend-уровень**, не feature.

### 4. Добавить App Actions для базовых сценариев в спеку 010 (или новую):
- BII `actions.intent.CALL` для голосового вызова контакта.
- BII `actions.intent.OPEN_APP_FEATURE` для «открой бабушкин экран».
- Custom intent для admin-flows (после MCP скелета).

### 5. Закрепить ADR-008 (новый)

`docs/adr/ADR-008-ai-affordance.md` — позиция: «мы строим AI-ready, не AI-built. Слой 2 — в MVP, Слой 3 — скелет в MVP. Слой 1 — post-MVP с inline TODO».

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |
