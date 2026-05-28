# Use-cases discovery — обзорный документ

> **Назначение этой папки**: разложить продукт по доменам, обдумать use case'ы в каждом, зафиксировать решения, и потом из этого собрать понятный roadmap.
>
> **Status**: 🟡 in progress — разделение `docs/product/user-journeys-draft.md` на per-domain документы (2026-05-27).

---

## 1. Зачем нужна эта папка (просто)

Когда продукт большой — а наш уже большой, 12 завершённых спек и ещё много впереди — **обдумать всё одним документом невозможно**. Один документ либо неполный, либо невозможно прочитать целиком, либо устаревает быстрее, чем дописывается.

Решение — **разделить по доменам** (по областям внимания) и обдумывать **по одному за раз**. Этот обзорный документ (README) — **точка входа**: он рассказывает, какие документы существуют, в каком состоянии каждый, в каком порядке их лучше читать, и какие решения уже зафиксированы.

Workflow: user открывает README → видит, что не обдумано → переходит в нужный доменный документ → читает разбор + use case'ы + открытые вопросы → фиксирует решения прямо в этом документе (раздел «Заметки решений») → возвращается в README и обновляет статус.

Потом, когда у нас по всем (или почти всем) доменам есть решения — собираем **vision document** и **roadmap v2** уже на устоявшейся базе.

## 2. Принцип разделения на документы (логика)

Каждый домен — это **отдельное измерение продукта**, в котором свои термины, свои use case'ы и свои one-way doors (необратимые решения). Если их смешивать в одном документе — нельзя сесть и обдумать «только pairing» или «только monetization», потому что они переплетены с остальным.

**Критерии хорошего разделения**:
- Документ должен закрывать **одну мысленную единицу** — то, что можно обдумать за один заход (30-60 мин).
- Документ должен быть **самостоятельным**: можно открыть, прочитать, понять, без бесконечных переходов в другие.
- Связи с другими доменами — через явные ссылки в конце, не через размытость границ.
- Каждый документ можно **наполнять отдельно** со временем, не ломая остальные.

## 3. Список документов

| # | Документ | Что обсуждаем | Status |
|---|---|---|---|
| — | **README.md** (этот) | обзор + статус всех остальных | 🟡 in progress |
| 01 | [01-vision-and-positioning.md](01-vision-and-positioning.md) | **Family Care Ecosystem** — vision зафиксирован 2026-05-27, 5 D-вопросов закрыты | 🟡 decided in principle |
| 02 | [02-actors-and-lifecycle.md](02-actors-and-lifecycle.md) | акторы (admin / Managed / co-admin / vendor) + стадии жизни (setup → daily → recovery → end-of-life) | 🆕 первый проход |
| 03 | [03-launcher-ui-and-accessibility.md](03-launcher-ui-and-accessibility.md) | локальный UI на устройстве Managed + доступность (зрение, тремор, когнитив) | 🆕 первый проход |
| 04 | [04-remote-management.md](04-remote-management.md) | админский UI и workflows (всё, что admin делает на своей стороне) | 🆕 первый проход |
| 05 | [05-pairing-identity-trust.md](05-pairing-identity-trust.md) | как admin и Managed связываются, identity model, recovery, multi-admin | 🆕 первый проход |
| 06 | [06-communications.md](06-communications.md) | звонки / видеозвонки / messengers (strategic must-have) | 🆕 первый проход |
| 07 | [07-data-and-privacy.md](07-data-and-privacy.md) | что хранится / где / кто видит / retention / end-of-life | 🆕 первый проход |
| 08 | [08-platform-android-and-ios.md](08-platform-android-and-ios.md) | Android OS quirks, OEM, iOS parity | 🆕 первый проход |
| 09 | [09-backend-and-reliability.md](09-backend-and-reliability.md) | бэкенд (Cloudflare/Firestore/FCM), reliability, performance, exit ramps | 🆕 первый проход |
| 10 | [10-monetization-distribution-legal.md](10-monetization-distribution-legal.md) | 🔵 **FROZEN** (out of dev roadmap per user 2026-05-27). Только **anti-tampering / защита от взлома** остаётся (M-009 + ADR-002). Остальное — отдельный проект. | 🔵 заморожено |
| 11 | [11-support-dev-process-future.md](11-support-dev-process-future.md) | support / ops / dev process / spec-kit / future verticals | 🆕 первый проход |
| 12 | [12-ai-integration.md](12-ai-integration.md) | AI affordance — App Actions (Layer 2), MCP server (Layer 3), Gemini Nano (Layer 1), AI-ready architecture | 🆕 первый проход |

**Status legend**:
- 🆕 первый проход — документ создан, но ещё не обсуждался с user'ом
- 🟡 in progress — частично обсуждено, есть открытые вопросы
- 🟢 решено — основные D-вопросы зафиксированы, можно строить roadmap
- 🔵 заморожено — отложено намеренно, вернёмся позже
- ⏸ заблокировано — нужны входные данные извне (юрист, регулятор и т.п.)

## 4. Рекомендуемый порядок прохода

Документы пронумерованы **в порядке логической зависимости**: каждый следующий опирается на решения предыдущего. Можно идти не строго по порядку, но имей в виду:

1. **01 Vision** — фундамент. Без ответа «что это вообще за продукт» обсуждать остальное вслепую. Особенно важно: companion-only vs self-serve, кто покупает (admin или Managed), что обещаем.
2. **02 Actors** — кто наши пользователи, какие у них стадии жизни с продуктом. Это база, на которой каждая следующая спека отвечает «для какого актора на какой стадии».
3. **03 Launcher UI + Accessibility** — это **продукт глазами Managed**. Тот самый «экран бабушки», ради которого всё строится. Если решения в 01/02 поменяются — UI поменяется радикально.
4. **04 Remote management** — продукт глазами admin'а. По принципу user'а *«удалённая настройка = та же настройка лаунчера»* — пересекается с 03, но фокус на админских workflows (список устройств, health monitoring, history, multi-admin coordination).
5. **05 Pairing & trust** — клей между 03 и 04. Что доверяем, как связываемся, что делать при потере. Сильно завязан на 07 (privacy) и на спек 011 (crypto).
6. **06 Communications** — стратегический must-have. Идёт после launcher UI, потому что *в каком виде показывать звонок* зависит от UI решений.
7. **07 Data & privacy** — кросс-режущая тема. Обсуждать после того, как понятно, что вообще генерируется и хранится (после 03-06). Имеет blocking-эффект на 10 (legal).
8. **08 Platform** — техническая основа. Можно отложить до тех пор, пока продуктовые решения 01-07 не зафиксированы — но имеет blocking-эффект на iOS-вопросы (D-14).
9. **09 Backend & reliability** — аналогично 08, технический фундамент. Имеет blocking-эффект на monetization (если backend orchestration сложнее, чем сейчас — это меняет cost model).
10. **10 Monetization + distribution + legal** — бизнес-слой. Делается ПОСЛЕ того, как продукт понятен. Хотя D-11 (timing) — стоит обсудить рано.
11. **11 Support + dev process + future** — operational layer. Делается последним, потому что зависит от того, что в проде, и кто за это отвечает.
12. **12 AI integration** — **cross-cutting**, не имеет фиксированного места в порядке. Лучше прочитать **рано** (после 01 vision), чтобы решения по action architecture, identity, privacy в остальных документах учитывали AI affordance. Особенно влияет на D-1 (self-serve становится реальнее с AI-помощником), D-12 (privacy posture для AI), D-15 (vendor integration через MCP).

**Альтернативный порядок «по болевым точкам»** — если хочется идти по тому, что сейчас больше всего болит:
1. 01 Vision (закрыть D-1 companion vs self-serve)
2. 03 Launcher UI (закрыть «загрузка vs empty state», D-7 6-tile limit)
3. 06 Communications (это стратегический must-have, ещё не делано)
4. 04 Remote management (расширить, потому что 12 спек уже это строили без vision'а)
5. … остальные по интересу

## 5. Notation legend (как читать use case таблицы)

В каждом доменном документе use case'ы записаны таблицей:

| ID | Кейс | Статус |
|---|---|---|

**Статусы кейса**:
- ✅ **закрыто** — есть спека, которая это покрывает, и она реализована
- 🟡 **частично** — есть спека или backlog-item, но не end-to-end
- ❌ **gap** — нет ни спеки, ни задачи в backlog; зафиксировать в roadmap
- 🔮 **future** — запланировано в FUTURE-SPEC-XXX, но ещё долго
- ❓ **open question** — не понятно, нужно ли это нам вообще; обсудить

**ID-префикс по доменам**:
- S-XXX — Scenarios (user journeys, doc 02)
- R-XXX — Remote management (doc 04)
- P-XXX — Pairing/trust (doc 05)
- A-XXX — Auth/identity (doc 05)
- C-XXX — Communications (doc 06)
- D-XXX — Data/privacy (doc 07)
- PL-XXX — Platform/OS (doc 08)
- iOS-XXX — iOS parity (doc 08)
- O-XXX — Operations/backend (doc 09)
- PF-XXX — Performance (doc 09)
- M-XXX — Monetization (doc 10)
- DIS-XXX — Distribution (doc 10)
- L-XXX — Legal/compliance (doc 10)
- SUP-XXX — Support (doc 11)
- DV-XXX — Dev process (doc 11)
- F-XXX — Future verticals (doc 11)
- D-NN — **Open Discussion question** (cross-domain, например D-1 «companion-only vs self-serve»)

## 6. Связь со спеками 001-012 и backlog

Эти документы **НЕ заменяют** ни спеки, ни backlog:

- **Спеки 001-012** (`specs/00X/`) — это **то, что уже решено и описано** функционально. Use-case документы могут ссылаться на спеки, но не противоречить им. Если выяснилось, что спека «решает не то» — это поднимается как D-вопрос и при необходимости делается новая спека.
- **`docs/dev/project-backlog.md`** — operational TODO list. Use-case документы могут породить новые TODO в backlog'е, но сами TODO не дублируют.
- **`docs/product/feature-priorities.md`, `roadmap.md`, `senior-safe-launcher-plan.md`** — это исторические артефакты, частично актуальны. Use-case документы реструктурируют их содержание под современный контекст.
- **`docs/product/context-decisions-and-open-questions.md`** — фиксирует 13 направлений (cross-platform, anti-abuse, distribution, etc.). Use-case документы детализируют каждое из них.

## 7. Открытые вопросы общего уровня (cross-document)

Эти D-вопросы пронумерованы сквозно, чтобы можно было ссылаться из любого документа. Полное обсуждение каждого — в указанном доменном документе.

| # | Вопрос | Где обсуждаем | Status |
|---|---|---|---|
| **D-1** | Companion-only vs self-serve (или hybrid) | 01-vision | 🟢 **RESOLVED 2026-05-27** — family-curated + self-recovery |
| **D-2** | Vertical-slice testing — каждая спека закрывает узкий end-to-end? | 11-dev-process | 🟢 **RESOLVED 2026-05-27 (evening)** — Workflow change: обязательная **«Local Test Path»** секция в `spec-template.md`. `procedure-cross-artifact-trace` верифицирует заполненность. Two-way door — можно откатить если overhead. |
| **D-3** | End-of-life сценарии (S-601..S-603) | 07-data-and-privacy | 🟢 **RESOLVED 2026-05-27 (evening)** — Family Group естественно справляется (death = remove-member case). Orphan admin case → 011 OWD-4 social recovery. Никаких специальных end-of-life features в MVP. |
| **D-4** | Конкурентные must-have (SOS, медкарта, медикаменты) | 01-vision / 06-communications | 🟢 **RESOLVED 2026-05-27 (evening)** — SOS = **configurable capability** в Capability Registry. Wizard step настраивает (recipients / actions / surfaces / delay). Medical platform — out. |
| **D-5** | Empty-state vs loading vs error — cross-spec rule | 03-launcher-ui | 🟢 **RESOLVED 2026-05-27 (evening)** — **top-level empty не существует** (Setup Wizard enforce'ит config). Component-level states (loading skeleton / missing-app / error) — cross-spec rule в `design-system.md`. |
| **D-6** | «Universal Launcher» vs «Senior Launcher» — нэйминг и позиционирование | 01-vision | 🟢 **RESOLVED 2026-05-27** — Family Care Ecosystem |
| **D-7** | Default tile limit | 03-launcher-ui | 🟢 **RESOLVED 2026-05-27 (evening)** — 3 grid presets выбираются в Setup Wizard: «Простой» 2×3=6 (default, Wiser), «Обычный» 3×4=12 (BIG), «Насыщенный» 4×5=20. |
| **D-8** | Dwell-to-activate | 03-launcher-ui | 🟢 **RESOLVED 2026-05-27 (evening)** — inline TODO + architectural readiness (target halo + touch debounce уже). Post-MVP implementation. В MVP wizard не показываем toggle (не врём про availability). |
| **D-9** | Hardware SOS через power-button | 06-communications | 🟢 **RESOLVED 2026-05-27 (evening)** — inline TODO post-MVP. `AccessibilityService` для triple-press — слишком invasive permission для MVP. Тайл + voice через capability покрывают 95% случаев. |
| **D-10** | Family-curated vs self-serve — расширение D-1 | 01-vision | 🟢 **RESOLVED 2026-05-27** — Family Group primary |
| **D-11** | Monetization model / timing | 01-vision | 🟢 **RESOLVED 2026-05-27 (evening)** — family monthly subscription. Детали (pricing/trial/refund/payment) остаются 🔵 frozen в doc 10. |
| **D-12** | Privacy posture default — telemetry | 07-data-and-privacy | 🟢 **RESOLVED 2026-05-27 (evening)** — **Opt-in telemetry**. Default OFF. User явно включает в Settings. Простая модель, GDPR-safe, fits privacy-positive positioning для senior audience. |
| **D-13** | Multi-admin privacy — admin1 видит то, что внёс admin2 | 04-remote-management | 🟢 **RESOLVED 2026-05-27** — Shared Trust Model |
| **D-14** | iOS timing | 08-platform-android-and-ios | 🟢 **RESOLVED 2026-05-27 (evening)** — iOS Admin в post-MVP v2. Architectural readiness через KMP / Compose Multiplatform уже есть (ADR-005). Inline TODO в UI-слое где relevant. Android-admin-Android-Managed валидируется сначала, iOS-admin добавляется как новый preset extending D-22 framework. |
| **D-15** | Vendor / partner integration — рамку сейчас или потом | 07-data-and-privacy | 🟢 **RESOLVED 2026-05-27** — caregivers/clinics in scope |
| **D-16** | Crash collection | 11-support-dev-process-future | 🟢 **RESOLVED 2026-05-27 (evening)** — **Primary**: Android Vitals (Google Play Console, automatic, no SDK). **Local crash log** в file. **Non-intrusive notification** «у вас был краш — отправить данные?». Send через Android share intent (email / messenger / любой канал). `CrashReporter` port для будущих adapter'ов. **No Crashlytics, no Sentry SDK в MVP**. Inline TODO: self-hosted Sentry post-MVP для non-Play distribution. |
| **D-17** | AI integration — что в MVP | 12-ai-integration | 🟢 **RESOLVED 2026-05-27 (evening)** — **только AI-ready architecture, без provider implementations**. `CapabilityRegistry` + `ExposureAdapter` ports + FakeAdapter для тестов. App Actions / MCP / Gemini Nano — ни один не реализуется в MVP. |
| **D-18** | AI privacy posture | 12-ai-integration | 🟢 **DEFERRED 2026-05-27 (evening)** — решается при написании implementation spec на конкретный adapter, не в MVP (т.к. ни один adapter не реализуется). |
| **D-19** | MCP server location | 12-ai-integration | 🟢 **DEFERRED 2026-05-27 (evening)** — то же: решается при написании MCP adapter spec, не в MVP. |
| **D-20** | AI-readiness checklist skill | 12-ai-integration | 🟢 **RESOLVED 2026-05-27 (evening)** — создать `checklist-ai-readiness` в `.claude/skills/`, парадигма spec-kit checklist'ов. Активация через `procedure-assess-spec-complexity`. |
| **D-21** | AI affordance как ось roadmap-обсуждений | 12-ai-integration | 🟢 **RESOLVED 2026-05-27 (evening)** — да, обязательная ось наряду с accessibility / privacy / one-way doors. |
| **D-22** | Universal Preset Architecture — что в MVP | 01-vision | 🟢 **RESOLVED 2026-05-27 (evening)** — Simple Launcher + Admin App как два пресета одной codebase. + новая задача `checklist-preset-readiness`. |
| **D-23** | Elderly-Friendly Messenger | 06-communications | 🟢 **RESOLVED 2026-05-27 (evening)** — **MVP = handoff (статус-кво)**. Post-MVP — отдельное приложение для пожилых на Jitsi Meet, доставляется через **preset bundle** + **SSO** с launcher. Тестируется отдельно. |
| **D-24** | Android TV form factor | 08-platform-android-and-ios | 🟢 **RESOLVED 2026-05-27 (evening)** — architectural readiness (KMP/CMP уже даёт), inline TODO. Real TV UI — post-MVP. |
| **D-25** | Family Group data model | 05-pairing-identity-trust | 🟢 **RESOLVED 2026-05-27 (evening)** — refined B+C: pair-keys + envelope encryption + server arbitration. **priv_G не хранится на сервере** (E2E preserved). |
| **D-26** | Shared Family Album | 07-data-and-privacy | 🟢 **RESOLVED 2026-05-27 (evening)** — MVP: только photos через спеку 012. Album как concept — architecturally ready (envelope encryption из D-25). Полный album UI (photos + videos + audio + memories) — v2. |
| **D-27** | Caregiver integration depth | 05-pairing-identity-trust | 🟢 **RESOLVED 2026-05-27 (evening)** — MVP: одна Family Group, **remote invite через signed link**, **role presets** (Medical Worker / Hired Caregiver / Family Caregiver / Volunteer), **TTL на membership** (не на group), **role-based filtering envelope wrappers** (producer выбирает recipients по category). Data model many-to-many user↔group для будущих use cases. |
| **D-29** | Subscription binding с группами | 10-monetization (frozen) | 🟢 **DEFERRED 2026-05-27 (evening)** — best-guess: subscription per admin identity. Точные tiers (по N групп / N людей / family pack scope) → отложены к monetization implementation spec. |

## 8. История этой папки

- 2026-05-27 — создана из `docs/product/user-journeys-draft.md`, разделено на per-domain документы. Оригинальный draft остаётся как историческое raw-research, новые документы переорганизуют и **расширяют** его содержимое в mentor-стиле.
- _(сюда добавляются изменения по мере работы)_

## 9. Что НЕ в этой папке

- Спецификации фич — это `specs/00X/`.
- ADR (architectural decision records) — это `docs/adr/`.
- Operational TODO — это `docs/dev/project-backlog.md`.
- Server migration roadmap — это `docs/dev/server-roadmap.md`.
- Compliance registers — это `docs/compliance/`.
- Маркетинг / Play Store copy / лендинги — это «другой проект» (см. реплику user'а 2026-05-27).

---

**Как начать работать с этой папкой**:
1. Открой [01-vision-and-positioning.md](01-vision-and-positioning.md) и прочитай.
2. В разделе «Заметки решений» зафиксируй, что решил.
3. Обнови статус документа в этой таблице (§3).
4. Переходи к следующему по [§4](#4-рекомендуемый-порядок-прохода).
