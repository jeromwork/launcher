# 06. Communications — звонки, видеозвонки, messengers

> **Status**: 🟡 partially decided (D-23 RESOLVED 2026-05-27 evening) · **Created**: 2026-05-27

## SOS — configurable capability (D-4 + D-9 RESOLVED 2026-05-27 evening)

**Принцип**: SOS — **не hardcoded функция**, а **capability** в Capability Registry (doc 12). Wizard step настраивает поведение. Surfaces выбираются user'ом.

### Capability declaration

```kotlin
Capability(
    intentName = "trigger_emergency",
    description = "Activate emergency assistance — call sequential recipients + send SMS with location",
    voicePhrases = ["помогите", "SOS", "помощь"],
    params = [
        Param("recipients", List<ContactId>),
        Param("actions", Set<EmergencyAction>),  // { CALL_SEQUENTIAL, SMS_WITH_GPS }
        Param("confirmation_delay_sec", Int = 5),
    ],
    requiresConfirmation = true,
    auth = AuthScope.ManagedSelf,
)
```

### Wizard step

Default: **рекомендуется включить** (highlighted), но skippable.

User configures:
- **Recipients**: subset of Family Group (admin + co-admins typically).
- **Actions**: tick {call sequential, SMS with GPS}.
- **Confirmation delay**: slider 0 / 3 / 5 / 10 sec (default 5 — anti-mistap).
- **Surfaces**: tile (где разместить) / voice / оба.

If skip → banner в Settings «Emergency button not configured. Set up?».

### Surfaces (automatic via Capability Registry)

- **UI tile** — красный, по центру нижней половины (one-handed reach из doc 03). Configurable position в edit mode.
- **Voice** — через App Actions BII (Слой 2 doc 12). «Окей Google, SOS» или просто «помогите!».
- **MCP** — любой AI агент может trigger через `trigger_emergency` tool (Слой 3 doc 12).
- **Hardware power-button (triple-press)** — **inline TODO post-MVP**. `AccessibilityService` — слишком invasive permission для MVP. После пользовательских feedback'ов решим.

### What SOS does (по умолчанию)

При активации (после confirmation delay):
1. **Sequential calls**: набирает каждого recipient'а по очереди до первого ответа.
2. **Parallel SMS**: всем recipient'ам — SMS с **координатами GPS** (если permission есть) + текст «SOS — нужна помощь».
3. Если **ни один** recipient не ответил → fallback на системный emergency number (112 / 911).

Cancel: кнопка «отменить» доступна в течение confirmation delay. После — отмена технически невозможна (звонки/SMS уже идут).

### Privacy

GPS-coordinate sharing **только** через SOS-flow. Не trackingsystem. Permission requested justly: «для функции SOS». Inline TODO: `// TODO(post-mvp): user-configurable GPS share — last known location vs realtime`.

---

## Elderly-Friendly Messenger — final decision (D-23)

**MVP**: handoff (status quo, спека 002). Тайл WhatsApp / Telegram / Viber → пользователь попадает в чужое приложение → возвращается. Не блокирует релиз launcher'a.

**Post-MVP**: **отдельное elderly-friendly приложение** на базе Jitsi Meet (open source, проверенный). Не embedded в launcher. Свой UX, свой релизный цикл, тестируется как самостоятельный продукт.

**Связь между launcher и messenger**:
- Доставляются вместе как **preset bundle** (launcher + messenger). User-experience: одной установкой получает оба.
- **SSO (Single Sign-On) under the hood**: при авторизации launcher'а — авторизация в messenger'е автоматически. Один identity, два приложения.
- Setup-wizard launcher'а включает шаг создания комнат и настройки call-tiles в messenger'е.

**Что нужно добавить в roadmap**:
- Отдельная спека «elderly-messenger» (Jitsi-based) — post-MVP.
- Расширение D-22 preset framework: preset = manifest на N приложений + shared identity. Нужна спека «preset-bundle-installer».
- SSO implementation: Firebase Auth (или Google Sign-In) shared между apps через AccountManager / FirebaseAuth shared instance.

> **Зачем читать**: per `feature-priorities.md` это **strategic must-have**. Без надёжной коммуникации между бабушкой и внуком — продукта нет. Сейчас закрыт минимум (WhatsApp tile + return из 002), всё остальное — gaps.
> **Источник**: `user-journeys-draft.md` §7.4 + `feature-priorities.md` + спека 002.

---

## Что это за документ (просто)

Главная **ценность продукта** для бабушки — позвонить внуку. Не «использовать лаунчер», а «увидеть фото внука → нажать → услышать его голос». Всё остальное (UI, pairing, фото) — инфраструктура для этого.

Сейчас этот сценарий частично работает только через WhatsApp tile (002): tap → confirmation screen → WhatsApp → возврат. Это **handoff-модель** (мы не делаем сами звонок, мы передаём в установленный мессенджер).

Этот документ — про то, **какие коммуникационные сценарии** мы должны закрыть, **через что** (WhatsApp / Telegram / Viber / SMS / system phone), и **что делать, когда не работает** (нет app'а, нет интернета, нет связи).

## Главные понятия (просто)

- **Handoff** — наш паттерн: лаунчер инициирует, действие выполняется в установленном мессенджере. Альтернатива — embed (вызов внутри нашего app'а), которого мы НЕ делаем (слишком сложно).
- **Action** — действие, привязанное к тайлу. Например: «WhatsApp Call → contact X». В спеке 005 (Action Architecture v2) описано как wire format.
- **Provider** — конкретный мессенджер (WhatsApp, Telegram, Viber). Спека 006 даёт `Capability` — может ли provider что делать сейчас (app installed, online, version supports video).
- **Fallback chain** — если основной мессенджер не работает, пробуем следующий. Не зафиксирован сейчас.
- **Confirmation flow** — UI перед действием: «вы уверены, что хотите позвонить?» — anti-mistap. Спека 010 это закрыла для звонков.
- **Return continuity** — что показываем, когда пользователь возвращается из мессенджера. Спека 002 закрыла для WhatsApp.

## Use case инвентарь

| ID | Кейс | Status | Notes |
|---|---|---|---|
| C-001 | Audio-звонок через WhatsApp | ✅ (002) | core working |
| C-002 | Audio-звонок через Telegram / Viber / другие | 🟡 (005 action arch ready, не реализовано) | provider-specific |
| C-003 | Video-звонок | ❌ | feature-priorities must-have |
| C-004 | Contact не на этом мессенджере → fallback | ❌ | критично для UX |
| C-005 | App не установлен → install prompt с deep-link | 🟡 (006 capability) | |
| C-006 | Multi-messenger preference (WhatsApp → Viber → call) | ❌ | какой UI выбора |
| C-007 | Custom call confirmation (anti-mistap) | ✅ (010) | |
| C-008 | Return из мессенджера в лаунчер | ✅ (002) | |
| C-009 | iOS handoff | ❌ Documented Platform Asymmetry | пока Android-only |
| C-010 | Voice message playback / send | ❌ | возможно вне scope launcher'а |
| C-011 | Conference / group call | ❌ | вне MVP |
| C-012 | SMS fallback при нет интернета | ❌ | связан со спекой 013 |
| C-013 | Call quality issues — UX для бабушки | ❌ | «не слышу» сценарий |
| C-014 | Closed messengers (LINE, WeChat, KakaoTalk) | 🔮 FUTURE-SPEC-003 | для Asia / partner |
| C-015 | Jitsi / vendor conference | 🔮 (через 011 OWD-5) | future |
| C-016 | SOS как communications action | ❌ | связан с D-4 |
| C-017 | Звонок из admin-стороны (admin позвонил бабушке через app)? | ❓ | возможно вне scope |
| C-018 | «Не отвечает» — что показать бабушке | ❌ | UX сценарий |

## Главные открытые вопросы

### D-Comm-1. Какие мессенджеры в MVP scope

**Контекст**: сейчас 002 — только WhatsApp. Telegram / Viber есть в action architecture (005), но не реализованы. Multi-messenger setup сложно: разные deep links, разные permissions, разная reliability.

**Варианты**:
- **MVP: только WhatsApp**: фокус, минимум багов. Минус — Telegram-only пользователи (Россия, EU) не имеют входа.
- **MVP: WhatsApp + Telegram + Viber**: тройка для глобального покрытия. Минус — тройная сложность интеграции, тестирования.
- **MVP: один + adapter framework на остальных**: WhatsApp out-of-the-box, остальное — через спеку «провайдеры», добавляются легко.

**Рекомендация**: вариант 3. Архитектура 005/006 уже готова — добавление провайдера = одна спека на каждого, без переписывания.

### D-Comm-2. SOS как communications или отдельная категория

**Контекст**: SOS (D-4 из 01-vision) — must-have. Но **через что** делать SOS? Через communications inframework (как «звонок на специальный номер»)? Или отдельная категория?

**Варианты**:
- **SOS = special communications action**: SOS = «звонок + SMS + GPS» одним flow. Использует communications infra.
- **SOS = отдельная категория**: своя спека, своя UI, свой flow.

**Рекомендация**: hybrid. **Технически** = communications action (использует Action Architecture). **Visually** = отдельный SOS-тайл с красным дизайном (см. 03 UI). Спека на SOS включает обе стороны.

### D-Comm-3. Video calls — когда

**Контекст**: video — следующая большая фича. Технически сложнее (camera permissions, frontend UI для видео, bandwidth concerns).

**Варианты**:
- **MVP**: video одновременно с audio.
- **Post-MVP v2**: audio в MVP, video во втором релизе.
- **Inline TODO**: архитектурно подготовлено, но не построено.

**Рекомендация**: post-MVP v2. Audio калибровать сначала, потом video. Связано с тем, что video во многих мессенджерах требует разных deep links — отдельный provider work.

### D-Comm-4. SMS fallback — встроенный или внешний

**Контекст**: при нет интернета — звонок через мессенджер не работает. Но system phone (GSM) работает. И SMS работает.

**Варианты**:
- **Auto-fallback**: если WhatsApp call failed → автоматически предлагаем «позвонить через GSM или отправить SMS».
- **Manual fallback**: бабушка сама понимает, что не работает, и переключается.
- **Дублирующие тайлы**: каждый контакт имеет 2 тайла — «WhatsApp» и «GSM».

**Рекомендация**: auto-fallback (вариант 1). Простой для бабушки, не плодит UI.

## Что в спеках уже зафиксировано

| Спек | Что фиксирует |
|---|---|
| 002 WhatsApp tile | первая реализация handoff-pattern |
| 005 Action Architecture v2 | wire format actions, ProviderRegistry, dispatch model |
| 006 Provider Capabilities | health snapshot, capability detection (app installed, version) |
| 010 Setup Assistant | custom call confirmation (anti-mistap) |
| `feature-priorities.md` | must-have direction |

## Связь с другими документами

- **01 Vision** — communications = главная ценность продукта (D-4 SOS).
- **03 Launcher UI** — как visualize-тайл communications.
- **05 Pairing** — контакты приходят через admin'а, привязаны к pair-у.
- **07 Data & privacy** — privacy при коммуникации (handoff в third-party messenger = третий получает данные).
- **08 Platform** — call intents Android vs iOS differ.

## Источники

- Спеки 002, 005, 006, 010.
- `docs/research/messenger-calling-research.md` (если существует — проверить).
- [Android Intent ACTION_CALL](https://developer.android.com/reference/android/content/Intent#ACTION_CALL) — system phone reference.
- [WhatsApp Deep Linking](https://faq.whatsapp.com/5913398998672934) — официальная docs.

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |
