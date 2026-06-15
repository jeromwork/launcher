# 01. Vision & Positioning — Family Care Ecosystem

> **Status**: 🟡 **decided in principle, детали впереди** · **Major update**: 2026-05-27 (vision expansion от user)
> **Зачем читать**: это **зафиксированное видение продукта**. Дальше все спеки, фичи и архитектура сверяются с ним.
> **Что сюда не входит**: monetization, distribution, marketing — по решению user'а 2026-05-27 («всё, что не касается разработки, выкини из роадмап»). Исключение: **anti-tampering / защита от взлома** — это dev concern, остаётся в scope.

---

## 1. Vision Statement (зафиксировано user 2026-05-27)

> Этот раздел — **дословный текст видения**, изложенный user'ом. Это **источник истины** для всего продукта. Все остальные документы сверяются с ним.

### Главная идея продукта

Продукт **больше не рассматривается как просто launcher для пожилых**.

Теперь это:
- family care ecosystem,
- communication platform,
- remote support infrastructure,
- family coordination system.

Launcher — это только **интерфейсная поверхность** системы.

Настоящее **ядро** продукта:
- забота,
- связь,
- удалённая поддержка,
- безопасное сопровождение пожилого человека,
- снижение тревоги семьи,
- упрощение ежедневного взаимодействия.

**Главная цель**: создать единое безопасное пространство, где семья может:
- общаться,
- помогать,
- отслеживать состояние,
- быстро реагировать,
- координировать care-процессы.

### Universal Preset Architecture

Приложение строится как **единое ядро с набором пресетов**.

Каждый пресет:
- задаёт ограничения,
- меняет UX,
- включает разные flows,
- определяет доступные функции,
- адаптирует интерфейс под сценарий.

Но:
- кодовая база остаётся общей,
- core infrastructure едина,
- communication layer единый,
- synchronization layer единый,
- trust system единый.

Это позволяет:
- сохранить гибкость,
- быстро расширять платформу,
- не создавать полностью отдельные продукты.

При этом каждый пресет должен:
- отдельно тестироваться,
- иметь собственные onboarding flows,
- иметь собственные permission flows,
- иметь отдельную UX-валидацию.

Иначе возникают: UX-конфликты, permission conflicts, cognitive overload, inconsistent behavior.

### Main Preset: Simple Launcher

Главный family-care режим. Предназначен для: пожилых, vision-impaired, cognitively-sensitive users, пользователей с низкой цифровой грамотностью.

Основные принципы:
- минимальная когнитивная нагрузка,
- невозможность случайно сломать систему,
- упрощённая навигация,
- безопасный communication-first UX,
- крупные элементы интерфейса,
- ограниченные действия,
- минимальное количество шагов.

Simple Launcher становится: communication surface + monitoring endpoint + care endpoint.

Это уже не просто launcher. Это: **безопасное пространство пожилого пользователя + часть family ecosystem**.

### Admin Application

После настройки Simple Launcher — вторая половина системы, **отдельное приложение для семьи / admin**.

Admin app — это **не «панель управления launcher»**. Это: family coordination center + monitoring center + communication hub + remote assistance tool.

Через admin app семья: следит за состоянием устройства, получает alerts / health notifications, управляет конфигурацией, помогает пожилому удалённо, организует communication, управляет family group.

### Phone Health Monitoring

Одна из ключевых идей — мониторинг состояния телефона:
battery level, charging status, connectivity, internet availability, device offline state, app crashes, abnormal inactivity, SOS events, emergency triggers.

Семья получает: push notifications, alerts, status updates, critical warnings.

Это снижает: тревогу родственников, вероятность пропустить проблему, риск потери связи.

### Health Device Monitoring (будущее)

Система может расширяться на: smart watches, wearables, health sensors, smart speakers, medical devices.

Платформа должна поддерживать: device integrations, health alerts, wearable notifications, emergency escalation flows.

**Важно**: MVP **не** должен превращаться в полноценную medical platform. Главная цель — **family support infrastructure**.

### Integration With Caregivers & Clinics

Одна из сильнейших идей экосистемы — интеграция с сиделками, медсёстрами, caregiver services, clinic systems, social workers.

Это расширяет продукт из «launcher для пожилых» в «единое пространство координации ухода».

Семья получает: коммуникацию + coordination layer + emergency visibility + centralized communication.

### Communication Platform

Коммуникация становится **core feature**. Это **не** shortcut to messenger / WhatsApp tile / simple calling utility. Это **communication-first ecosystem**.

### Elderly-Friendly Messenger

Ключевая идея: удобный мессенджер для пожилых.

Особенности: extremely simplified UX, minimum interaction complexity, easy answer/join flows, large buttons, reduced cognitive load, family-oriented design.

Через него: семья поддерживает постоянную связь, организуются видеозвонки, подключаются родственники / сиделки / медсёстры, возможны group calls.

### Real-Time Care Communication

Мессенджер становится: communication layer + care coordination layer + social support layer.

Это особенно важно, потому что пожилому человеку важна **не только безопасность / monitoring / health alerts** — ему также нужна: социальная связь, ощущение присутствия семьи, ощущение поддержки, лёгкость коммуникации.

Это делает платформу не только техническим продуктом, но и **эмоциональной инфраструктурой семьи**.

### Android TV As Communication Device

Android TV рассматривается как: communication endpoint + ambient family presence device + simplified video-call surface.

Преимущества: большой экран, громкий звук, лёгкое управление, более естественная коммуникация.

TV может использоваться: для family calls, для quick join calls, для passive family presence, для communication with caregivers.

### Family Group System

Family Group — один из ключевых **архитектурных примитивов**.

Семейная группа: объединяет участников, объединяет устройства, управляет доверием, обеспечивает recovery, хранит shared state.

Участники: admin, co-admin, trusted relatives, caregivers, optional medical participants.

### Shared Trust Model

Каждый доверенный участник может: подтверждать устройства, помогать recovery, участвовать в onboarding, подтверждать новые connections.

Это делает систему: более устойчивой, менее зависимой от одного устройства, более семейно-ориентированной.

### Secure Configuration Architecture

Каждое устройство: хранит локальную конфигурацию + имеет encrypted cloud backup.

При изменениях: device проверяет server version → выполняется merge → updated config synchronizes → shared state обновляется.

Это обеспечивает: synchronization, consistency, recovery, cross-device continuity.

### Recovery Model

**Даже без admin**, пожилой пользователь должен иметь возможность: восстановить устройство, восстановить конфигурацию, получить свой state обратно.

Recovery flow: Google authentication + 2FA + PIN/password + optional family confirmation.

### Shared Family Album

Планируется family album system. Содержит: photos, videos, audio recordings, family memories.

Главные принципы: encrypted storage + centralized family access + elderly-safe access.

Пожилой пользователь: может просматривать, не может случайно удалить контент.

Управление: только trusted family members.

Это повышает: emotional attachment + family engagement + long-term retention.

### Retention Strategy

Retention строится **не вокруг launcher**. Retention строится вокруг: family dependency + shared memories + communication + care workflows + emotional attachment + trust network.

Это создаёт: высокий switching cost + strong ecosystem lock-in + long-term family usage.

### Main Strategic Risk

Главный риск — **scope explosion**. Есть риск одновременно строить: launcher + messenger + medical platform + smart-home system + TV ecosystem + caregiver platform + family cloud.

Это может разрушить фокус.

Поэтому главный фильтр:

> Если новая фича **НЕ усиливает** «Семья поддерживает связь и заботу о пожилом человеке через единое безопасное пространство» — такая фича должна считаться **suspect feature**.

---

## 1bis. Vision additions (2026-05-27, evening)

> User direct decisions:

### Монетизация — family monthly subscription

Модель монетизации зафиксирована: **ежемесячная семейная подписка**. Один платит — вся Family Group пользуется. Это закрывает базовый вопрос монетизации (D-11), детали (pricing tier, trial, refund policy, payment providers per platform) остаются **🔵 FROZEN** в doc 10 как не-dev concern до релиза.

### Retention ideas — parking lot (not committed)

> User 2026-05-27 evening: «не для реализации в MVP, для пометки — один из вариантов удержания, не обязательно к выполнению».
>
> Сюда складываем **идеи**, которые усиливают «семейную» ценность продукта, но **не commitment**. К каждой — одна-две caution-note, чтобы при подъёме помнить про подводные камни.

**PARK-001: Family Activity Challenges / gamification совместного времени**
- Метрики активности (часы на связи / просмотренные фото / совместные действия) → семейные challenges с положительным подкреплением.
- Зачем: усиливает retention через эмоциональную инфраструктуру, добавляет value подписке.
- Caution: gamification fatigue через 6 мес, риск patronizing UX («ты молодец, провёл 4 часа»), privacy для measurement, бабушка не должна видеть свои «negative metrics».
- Если поднимать — стартовать с soft metrics (статистика без бэйджей), family-level goals (не individual), opt-out в один тап.

_(сюда добавляем новые retention-идеи по ходу обсуждения)_

---

## 2. Что эта vision закрывает (resolved D-questions)

| ID | Вопрос | Status | Резолюция |
|---|---|---|---|
| **D-1** | Companion-only vs Self-serve | 🟢 RESOLVED | **Family-curated by default + self-recovery как safety net**. Family Group — primary unit, не отдельный admin. Recovery flow допускает self-restore через Google + 2FA + optional family confirmation. |
| **D-6** | Naming — Senior vs Universal | 🟢 RESOLVED | **«Family Care Ecosystem»** — продуктовое позиционирование. «Simple Launcher» — название первого пресета. Senior-стигма отвергнута на vision-уровне. |
| **D-10** | Family-curated vs Self-serve (extension of D-1) | 🟢 RESOLVED | **Family Group — primary architectural primitive**. См. секцию Family Group System. Retention построен на family dependency, не на individual users. |
| **D-13** | Multi-admin privacy boundaries | 🟢 RESOLVED | **Shared Trust Model** — внутри Family Group участники прозрачны друг для друга. Privacy boundary между **разными** Family Group, а не внутри одной. |
| **D-15** | Vendor / partner integration scope | 🟢 RESOLVED | **In scope явно**: caregivers, медсёстры, clinics, social workers. Architectural readiness — через 011 OWD-5 (per-app identity). Они становятся участниками Family Group с ограниченными правами. |
| **D-4** | SOS / медкарта / медикаменты — in-scope | 🟡 PARTIAL | **SOS — in** (emergency triggers, escalation flows упоминаются). **Полноценная medical platform — out** («MVP не должен превращаться в medical platform»). Медкарта / медикаменты — out, делегируем третьим. |
| **D-3** | End-of-life scenarios | 🟡 PARTIAL | Recovery model включает family confirmation. **Shared Family Album** подразумевает family ownership over memories. Но **детальные процедуры** (наследование, отвязка после смерти) ещё TBD. |
| **D-11** | Monetization timing / model | 🟢 RESOLVED | **Family monthly subscription** (user 2026-05-27 evening). Детали (tier, trial, refund, payment providers per platform) — остаются 🔵 frozen в doc 10. |
| **D-12** | Privacy posture для telemetry | 🟡 PARTIAL | Vision явно privacy-positive (encrypted storage, trust model). Конкретика telemetry — в 07-data-and-privacy. |

## 3. Новые открытые вопросы (D-22 .. D-27)

Vision вводит новые архитектурные конструкции — нужны решения:

### D-22. Universal Preset Architecture — что в MVP

**Контекст**: vision говорит «приложение строится как единое ядро с набором пресетов». MVP должен закладывать **архитектуру пресетов**, не только Simple Launcher.

**Варианты**:
- **MVP = только Simple Launcher**, остальные пресеты — extension points позже. Минус — может оказаться, что архитектура «выросла под один preset» и плохо обобщается.
- **MVP = preset framework + Simple Launcher как первый клиент**. Плюс — архитектурная честность с самого начала. Минус — больше работы на старте.
- **MVP = Simple Launcher + Admin App как два пресета** одного codebase. Это validates the framework.

**Рекомендация (best-guess)**: вариант 3. Simple Launcher и Admin App — это **уже два пресета**. Если построить их как разные сборки одной codebase — preset framework естественно вырастет.

### D-23. Elderly-Friendly Messenger — собственный или handoff

**Контекст**: vision говорит «удобный мессенджер для пожилых» как **core feature**. Это **большое** scope-добавление к спекам 002/005/006 (handoff to WhatsApp).

**Варианты**:
- **a. Handoff (как сейчас)**: тайл → WhatsApp / Telegram / Viber. Минус — UX не контролируем, бабушка попадает в чужой UX.
- **b. Embedded WebRTC**: свой видео-стек через WebRTC + signaling в Cloudflare Worker. Полный контроль UX, видим в каждом семейном устройстве. **Большая работа.**
- **c. Jitsi as a Service**: используем готовый Jitsi (open source), интегрируем как white-label. Среднее по сложности. Уже упоминался в 011 OWD как future integration.
- **d. Hybrid**: MVP — handoff (как сейчас), Elderly-Friendly Messenger — отдельная спека post-MVP.

**Регрет**: если делать сразу свой — задержка релиза на 6+ месяцев. Если оставаться на handoff — бабушка не получит обещанный «удобный мессенджер».

**Рекомендация**: **d. Hybrid с архитектурной готовностью**. MVP = handoff + extension point. Communication API design делается уже сейчас под будущий embedded messenger. Это совпадает с Capability Registry pattern (12-ai-integration).

### D-24. Android TV — в MVP или post-MVP

**Контекст**: vision вводит TV как «ambient family presence device». Это новая form factor.

**Варианты**:
- **MVP includes TV**: 3 пресета (Simple Launcher / Admin / TV). Втрое работа.
- **MVP excludes TV, post-MVP v2**: фокус на phone + admin сначала. TV — отдельная спека позже.
- **Architectural readiness**: KMP/Compose Multiplatform естественно поддерживает Android TV. Препарируемся, не строим UI.

**Рекомендация**: вариант 3. Архитектурная готовность через выбор стека уже есть (ADR-005). Полная TV вертикаль — post-MVP. Inline TODO в коде где relevant.

### D-25. Family Group — refactor или extension

**Контекст**: спека 007 строит **pair**-модель (admin ↔ Managed). Vision требует **group**-модель (N admins + M Managed + caregivers + ...). Это **разные** примитивы.

**Варианты**:
- **a. Refactor**: переписываем 007 на group-based с самого начала.
- **b. Extension**: Family Group = коллекция pair-ов с общим состоянием.
- **c. Постепенный refactor**: спека 013 «family-group» вводит концепт, 007 эволюционирует.

**Рекомендация**: **b или c**. Refactor (a) — too much rework. **Group = коллекция pair-ов** + общий trust state. Это совместимо с уже существующим кодом. Решение подтвердить отдельной спекой.

### D-26. Shared Family Album — в MVP scope?

**Контекст**: vision вводит family album (photos / videos / audio / memories). Это **новая большая feature**.

**Варианты**:
- **MVP includes album**: фотографии + видео + аудио. Большая работа.
- **MVP includes photos only** (через спеку 012): минимум, наполнить album'ом позже.
- **post-MVP**: album выносим в v2.

**Рекомендация**: **MVP includes photos only** (012). Album как concept — закладываем архитектурно (encrypted blob storage уже в 011), полный album UI — v2.

### D-27. Caregiver integration depth — read / participant / full

**Контекст**: Vision говорит «integration with caregivers, медсёстры, clinic systems». Какой уровень доступа?

**Варианты**:
- **Read-only health view**: caregiver видит status, не может менять config. Минимум.
- **Limited participant**: caregiver = ограниченный admin (свои тайлы, свои контакты).
- **Full Family Group member**: caregiver равноценный участник.

**Рекомендация**: tier-based. **Default = limited participant** (свои контакты, read health). Full participant — для close family caregiver, по explicit разрешению admin'а.

## 4. Что выведено из scope (per user 2026-05-27)

Следующие темы **вынесены из roadmap'а / vision** и не обсуждаются в этих документах:

- **Monetization детали** (pricing tier, trial period, refund policy, payment providers per platform). **Базовое решение — family monthly subscription — зафиксировано** в §1bis. Дальнейшие детали — `10-monetization-distribution-legal.md` помечен 🔵 frozen.
- **Distribution channels** (Play Store / App Store / RuStore / alternative) — выше.
- **Legal / compliance** (GDPR / 152-ФЗ / store policy) — выше.
- **Marketing / discovery / acquisition** — out (закрывается «в другом проекте» per user 2026-05-27).
- **Pricing strategy / B2B / B2C strategy** — out.
- **Support operations** (human helpdesk) — out (см. также 11-support-dev-process-future).

**Исключение**: **anti-tampering / защита от взлома** остаётся в scope — это dev concern (ADR-002). Конкретика — в `10-monetization-distribution-legal.md` M-009 + ADR-002.

## 5. Главные понятия Family Care Ecosystem (mentor)

- **Family Care Ecosystem** — продуктовое позиционирование. Не «launcher», а **экосистема ухода**.
- **Universal Preset Architecture** — один код, разные **пресеты** (Simple Launcher / Admin / TV / Caregiver / ...). Каждый пресет: своя UX, свой permission flow, свой onboarding, но **общий core** (sync, trust, communication, encryption).
- **Simple Launcher (preset)** — первый и главный preset для Managed устройства. Это **бывший «launcher»**, теперь — один из видов presetов.
- **Admin App** — отдельное приложение для семьи. Coordination center, не «панель управления».
- **Family Group** — архитектурный примитив. Объединяет N участников и M устройств. Расширение pair-модели из спеки 007.
- **Shared Trust Model** — внутри Family Group члены доверяют друг другу. Между группами — нет.
- **Phone Health Monitoring** — отслеживание состояния устройства, нотификация admin'у.
- **Health Device Monitoring** — будущее: wearables, sensors. Architectural readiness, не MVP.
- **Elderly-Friendly Messenger** — будущая (или гибрид сейчас) собственная коммуникационная поверхность. См. D-23.
- **Shared Family Album** — будущий family-shared media storage. См. D-26.
- **Caregiver Integration** — внешние участники Family Group с tier-based правами.
- **Suspect feature** — фича, не усиливающая главную ценность «семья поддерживает связь и заботу о пожилом через единое безопасное пространство». **Должна быть отклонена**.

## 6. Какие use case'ы из inventory (см. user-journeys-draft.md) теперь резолятся

| Use case | Резолюция |
|---|---|
| V-001 (внук покупает для бабушки) | Family Group invite, не один admin |
| V-002 (family pack) | Default model — family group из коробки |
| V-003 (самостоятельная бабушка) | Поддерживается через recovery flow с Google + 2FA |
| V-004 (клиника разворачивает) | Caregiver integration tier (D-27) |
| V-005 (хоспис / медцентр) | То же |
| V-006 (сосед / соц.работник) | Caregiver invite limited participant |
| V-007 (бабушка сама ставит, потом внук) | Family Group join inverse pairing |

## 7. Источники и ресёрч

- **Источник vision'a**: user 2026-05-27 (verbatim в §1).
- Aligned references:
  - GrandPad (family-curated tablet subscription) — `https://www.grandpad.net/`.
  - Family Link (Google) — child/parent model, но pattern переносится на adult-care.
  - Apple Family Sharing — модель shared participants + permissions tiers.
  - Doro (hardware bundle для пожилых) — родственная категория, для сравнения.

## 8. Связь с другими документами

- **02 Actors and lifecycle** — добавляются Caregiver actor и Family Group как primary scope (вместо pair).
- **03 Launcher UI** — это UI для **Simple Launcher preset**, не для всей системы.
- **04 Remote management** — это UI для **Admin App preset**.
- **05 Pairing & trust** — должен эволюционировать в Family Group model (D-25).
- **06 Communications** — D-23 Elderly-Friendly Messenger переопределяет scope.
- **07 Data & privacy** — Shared Family Album + Family Group privacy boundaries — детализация.
- **08 Platform** — Android TV как новый form factor (D-24).
- **10 Monetization / distribution / legal** — 🔵 **FROZEN** per user 2026-05-27.
- **12 AI integration** — Capability Registry должна работать для всех пресетов и для group, не только pair.

## 9. Заметки решений

| Дата | Решение | Regret-условие | Exit ramp |
|---|---|---|---|
| 2026-05-27 | Family Care Ecosystem positioning (не «launcher for elderly»). User direct decision. | Если в реальном маркете окажется, что target user — отдельный senior (без семьи), позиционирование на «family» отсечёт его. | Возможно сменить positioning marketing-side без переписывания кода. |
| 2026-05-27 | Universal Preset Architecture. User direct decision. | Если preset framework окажется over-engineered для нашего scale. | Можно деградировать в один монолитный app, потеряв modularity. |
| 2026-05-27 | Family Group как primary unit (D-10 resolved). | Если окажется, что 80% реальных users — solo admin, не family. | Family Group тривиально вырождается в N=1 (один admin + один Managed). |
| 2026-05-27 | Monetization / distribution / legal — out of dev scope. Anti-tampering — in. | Если ближе к релизу окажется, что эти темы блокирующие. | Документы помечены 🔵 frozen, не deleted — можно поднять обратно. |
| 2026-05-27 (evening) | **Monetization model = family monthly subscription**. User direct decision. | Если family unit окажется слишком крупным для adoption (solo users отсечены), или ARPU слишком низкий для покрытия Cloud costs. | Можно добавить individual tier поверх family pack. Architecture от этого не страдает. |
| 2026-05-27 (evening) | **Family Activity Challenges** → перенесено в **parking lot** (PARK-001), **не MVP commitment**. Идея retention-усиления, поднимем при необходимости. | n/a — parked. | n/a — parked. |
| 2026-05-27 (evening) | **D-22 RESOLVED — Preset architecture: вариант c.** Simple Launcher + Admin App как два пресета одной codebase. Сначала строим конкретно, framework вырастает естественно. **+ новая задача**: создать skill `checklist-preset-readiness` для активной защиты от unification erosion. **Sharing presets between users** — закрыто как нереализуемое (preset включает Android system settings, которые не шарятся). | Если выяснится, что Admin App почти не пересекается с Simple Launcher — preset framework окажется over-engineered для двух разных приложений. | Можно разделить на два независимых проекта без архитектурной катастрофы, потеряв modularity для будущих presets. |
| 2026-05-27 (evening) | **D-25 RESOLVED — Family Group: refined B+C (pair-keys + envelope encryption + server arbitration).** Сервер хранит membership list + pub-keys членов, **не** хранит priv_G. Контент шифруется envelope: K (per-content) + N wrapper'ов на каждого члена. Pair-keys из спеки 011 остаются для direct 1-to-1. Подсчёт «пар» = N на группу, не N². E2E preserved — сервер не может прочитать контент. Подробности — в [05-pairing-identity-trust.md](05-pairing-identity-trust.md) §Family Group Model. | Если в N=10+ группе envelope-overhead окажется значимым (маловероятно — 32 байта × N на content), или если сервер-арбитр станет single point of failure под нагрузкой. | Можно мигрировать к Signal-style group crypto (вариант A) в будущем — pair-keys + envelope остаются как fallback. Сервер-арбитраж заменяется на signed-chain membership ledger при необходимости. |
| 2026-05-27 (evening) | **D-23 RESOLVED — Messenger: MVP = handoff (status quo), post-MVP = отдельное Jitsi-based приложение для пожилых.** Доставка через **preset bundle** (launcher + messenger вместе), **SSO under the hood** (одна авторизация для обоих apps). Messenger тестируется отдельно как elderly product. Не блокирует MVP launcher. **Side-effect**: расширяет D-22 — preset = manifest на N связанных apps + shared identity (не одно приложение). | Если SSO между двумя apps технически окажется сложнее, чем кажется (Android cross-app auth не тривиальный), или Jitsi Meet open-source окажется недостаточно elderly-friendly без серьёзной кастомизации. | Можно переходить на embedded WebRTC в нашем app (вариант b из D-23) если SSO/separate-app path не сработает. Или к WhatsApp business API (paid) для full white-label. |
| 2026-05-27 (evening) | **D-24 RESOLVED — Android TV: architectural readiness, post-MVP UI.** KMP / Compose Multiplatform естественно поддерживает TV (ADR-005). Inline TODO в коде где relevant. Реальный TV preset — отдельная спека после v1 launcher. | Если в первый год adoption TV пресет окажется critical для значимого сегмента пользователей (например, B2B retirement homes), отложение задержит этот сегмент. | TV preset добавляется как третий пресет к codebase без переписывания — preset framework (D-22) на это рассчитан. |
| 2026-05-27 (evening) | **D-26 RESOLVED — Family Album: MVP только photos (спека 012), full album v2.** Concept architecturally ready (envelope encryption из D-25). Videos + audio + memories — v2. | Если конкуренты выпустят полноценный album раньше нас, мы потеряем differentiator «family memories». | Видео / аудио — additive фичи поверх существующего photo blob storage. Не требуют архитектурных изменений, добавляются как новые content types. |
| 2026-05-27 (evening) | **D-5, D-7, D-8 RESOLVED через unifying concept — Setup Wizard.** Top-level empty screen не существует (wizard enforce'ит config completion). Grid configurable в wizard (3 пресета: 2×3 / 3×4 / 4×5). Accessibility opt-ins surface'ятся в wizard / tutorial. Wizard включает Android permissions с deep-link в System Settings. **Connect**: расширяет спеку 010 / FUTURE-SPEC-006 в MUST-have priority. Component-level states (loading / error / missing) — cross-spec rule в `design-system.md`. | Если wizard окажется слишком длинным — пользователь бросит до завершения. Нужны checkpoint'ы + skip-options для optional steps. | Wizard разбит на phases: critical (permissions, role_home) — mandatory; optional (theme, grid, contact tiles) — skippable «настрою позже». Можно вернуться к skipped steps из Settings. |
| 2026-05-27 (evening) | **Wizard refined — independent reusable module + nested config templates.** Wizard стал **самостоятельным module** (`core/wizard/`), каждый preset declares свой `WizardManifest` (список steps + templates). Steps — reusable между preset'ами. **Config templates** = pure data (JSON) с готовыми раскладками внутри preset'а (например: «8 плиток + календарь снизу»). Templates как wire-format с schemaVersion. **Future**: templates technically shareable (без system settings) — inline TODO для curated marketplace. MVP scope: WizardEngine + 6-8 steps + 2 manifest'a + 2-3 templates на Simple Launcher. | Если wizard module окажется over-engineered для двух MVP preset'ов (Simple + Admin) — лишний overhead. | WizardEngine можно деградировать до прямого вызова setup-screens без manifest layer — потеряем reuse, но не критично. Templates остаются как pure data всегда. |
| 2026-05-27 (evening) | **NEW: CLAUDE.md rule 9 — Shareability-readiness for non-identity configurations.** Любая user-facing конфигурация (templates, layouts, themes, tutorials, wizard manifests), не зависящая от identity / PII / device-state, **обязательно** проектируется как portable shareable artifact с первого коммита: wire-format с schemaVersion, обезличенная форма, `ConfigSource` adapter pattern (Bundled — один из source'ов, не единственный). НЕ требует строить sharing UI сейчас. Обеспечивает: будущий sharing = additive adapter, без rewrite. **Применяется ко всем templates в wizard module** (D-22 / D-5 / D-7 / D-8 resolved). | Если выяснится, что в реальности sharing никому не нужен — rule создал overhead без value. | Adapter pattern сам по себе ценен (тестируемость через FakeConfigSource), даже если sharing никогда не будет. Format остаётся, sharing capability просто не активируется. |
| 2026-05-27 (evening) | **D-4 + D-9 RESOLVED — SOS = configurable capability.** Не hardcoded function — capability `trigger_emergency` в Capability Registry. Wizard step настраивает (recipients из Family Group / actions = call sequential + SMS с GPS / confirmation delay / surfaces). Surfaces автоматически: tile / voice (App Actions BII) / MCP (любой AI agent). Hardware power-button — inline TODO post-MVP. Medical platform — out (vision filter). Подробности в [06-communications.md §SOS](06-communications.md). | Если SOS-config окажется слишком сложным для бабушки настроить даже через wizard — никто не настроит, продукт без safety net. | Wizard provides «recommended defaults» в один тап (admin = sole recipient, default actions, 5s delay). Customization доступна, но не required. |
| 2026-05-27 (evening) | **D-17 RESOLVED — AI MVP = только architecture, без implementations.** Строим: `CapabilityRegistry` port, `ExposureAdapter` interface, FakeAdapter для тестов, capability declarations для всех actions. НЕ строим: App Actions adapter, MCP server, Gemini Nano integration. Каждый adapter — отдельная implementation spec позже. Соответствует CLAUDE.md rule 4 (Minimum Viable Architecture) и rule 6 (mock-first). | Если ни один реальный adapter не понадобится в первый год — Capability Registry layer окажется overhead без use. | Capability Registry полезен сам по себе для command dispatch (intent-driven actions через unified surface). FakeAdapter тестирует, без потери ценности. Real adapters — additive когда нужны. |
| 2026-05-27 (evening) | **D-18 + D-19 DEFERRED.** Privacy posture (on-device / cloud) и MCP server location — решаются при написании implementation spec на конкретный adapter (когда appears need). В MVP не нужны, т.к. ни один adapter не реализуется. | Если решения окажутся urgent (например, partner integration требует MCP cloud-server), приходится решать under-pressure. | Architectural readiness через Capability Registry даёт flexibility — оба decisions можно принять без переписывания existing code. |
| 2026-05-27 (evening) | **D-20 RESOLVED — `checklist-ai-readiness` skill создаётся.** В парадигме `.claude/skills/checklist-*`. Активация через `procedure-assess-spec-complexity` для спек, касающихся user actions / wire format / external surfaces. Проверяет: capability declared? voice phrase? machine-readable result? privacy data path defined? Documented AI Asymmetry (аналогично Documented Platform Asymmetry ADR-005)? | Если checklist окажется слишком noisy (срабатывает на каждый второй спек) — будет игнорироваться. | Активация conditional через assess-complexity — только когда релевантно. Если все equally noisy — настройка trigger'а. |
| 2026-05-27 (evening) | **D-21 RESOLVED — AI affordance как обязательная ось roadmap-обсуждений.** При планировании любой спеки сверка: «как этот feature exposed через Layer 2 / Layer 3?» Наряду с accessibility / privacy / one-way doors. | n/a — не accept-уровневое решение, это process improvement. | Можно отменить как rule, если окажется, что для большинства спек AI ось не применима. |
| 2026-05-27 (evening) | **D-14 RESOLVED — iOS Admin в post-MVP v2.** Architectural readiness уже через KMP + Compose Multiplatform (ADR-005). Inline TODO в UI-слое. Android-admin-Android-Managed валидируется первым, iOS-admin — отдельный preset extends D-22 framework позже. iOS Managed companion / iOS launcher (impossible) — out of scope для MVP. | Если US market admin'ов с iPhone окажется значимым сегментом и они откажутся ждать v2 — потеря adoption. | iOS-admin preset тривиально добавляется к codebase через preset framework, без переписывания core. KMP/CMP делает это additive change. |
| 2026-05-27 (evening) | **D-3 RESOLVED — End-of-life через Family Group естественные механики.** Death of member = case of remove-member (kicked through standard membership op by co-admin). Family Group continues, envelope wrappers новых content не включают умершего. Orphan admin scenario (последний admin умер) — закрывается social recovery (011 OWD-4 как будущая часть). **Никаких специальных end-of-life features в MVP.** | Если первый громкий случай (бабушка умерла, family заблокирована orphan scenario) случится до social recovery implementation — bad PR. | Social recovery — priority в roadmap'е post-MVP. Manual customer support intervention возможна для отдельных cases до этого. |
| 2026-05-27 (evening) | **D-12 RESOLVED — Opt-in telemetry**. Default OFF. User явно включает в Settings, если хочет помочь. Простая модель, GDPR-safe, аккуратно для senior audience. Сборы — anonymized aggregates only когда opted-in. **Не путать с crash collection** (D-16 — отдельно). | Низкий opt-in rate → метрик мало, продукт сложно улучшать data-driven. | Можно перевести на opt-out anonymized когда reputation product установлен и privacy-trust есть. |
| 2026-05-27 (evening) | **D-27 RESOLVED — Caregiver integration: one Family Group + role presets + role-based filtering.** MVP scope: one Family Group default per Managed (data model many-to-many для будущих контекстов). **Remote invite через signed link** (admin генерирует, шлёт любым channel, caregiver открывает в app, accept). **Role presets** при invite: Medical Worker / Hired Caregiver / Family Caregiver / Volunteer / Clinic Stay. **TTL на membership** (не на group). **Role-based envelope filtering**: producer выбирает recipients по content category + recipient roles. Family content → wrappers для family roles only. Care content → wrappers включают care roles. Audit log mandatory. **Optional advanced**: admin может вручную создать additional groups (Care Group, Friends Circle), но не auto. Подробности — [05-pairing-identity-trust.md §Caregiver Integration](05-pairing-identity-trust.md). | Если role-based filtering окажется too disciplined для admin'ов (каждый upload requires thinking «кому это видно») — confusion. | Можно перейти к Variant 2 (отдельные Care Groups) — data model уже поддерживает (many-to-many). Это additive change, не rewrite. |
| 2026-05-27 (evening) | **D-29 (new) DEFERRED — Subscription binding с группами.** Best-guess: subscription per admin identity (admin pays — все его primary groups активны). Точные tiers (по N групп / N людей / family pack scope) — отложены к monetization implementation spec. Architectural data model уже поддерживает (`user.subscription_state`, `group.primary_admin_id`). User considerations (Family-unit + tier based на N групп / N людей если превышено) — captured here для будущего monetization spec. | Если фактический use pattern окажется существенно отличным от per-admin (например, дети покупают подписку, admin'ы — родители) — пересмотр. | Subscription model — pure metadata layer поверх group structure. Любая monetization model реализуется без изменения crypto / group архитектуры. |
| 2026-05-27 (evening) | **D-2 RESOLVED — Vertical-slice testing as workflow rule.** Spec-template получает обязательную секцию **«Local Test Path»**. `procedure-cross-artifact-trace` verifies, что секция заполнена. Каждая спека отвечает: «как разработчик локально проверит фичу end-to-end». Closes S-105/S-801 (local dev experience). | Если для некоторых specs «local test path» не имеет смысла (например, чистая infra spec) — overhead. | Two-way door: можно сделать opt-out для определённых spec types, или удалить требование если оно не работает. |
| 2026-05-27 (evening) | **D-16 RESOLVED — Crash collection: Android Vitals + Local log + Share-intent.** Primary source — **Android Vitals** в Google Play Console (automatic, no SDK, no work). **Local crash log** в file пишется всегда. **Non-intrusive notification** при crash: «у вас был краш — отправить данные о сбое?». Send через **Android share intent** (email / messenger / любой channel пользователя). `CrashReporter` port (CLAUDE.md rule 2) — заготовка для будущих adapter'ов. **NO Crashlytics SDK, NO Sentry SDK в MVP**. Server-roadmap entry: post-MVP self-hosted Sentry для non-Play distribution coverage. | Если Vitals coverage окажется недостаточным (нужны custom breadcrumbs, real-time alerts) до того, как self-hosted Sentry готов — критические bugs могут пройти undetected. | CrashReporter port позволяет добавить любой adapter (Sentry / Cloudflare endpoint / даже Crashlytics opt-in если будет нужно) без переписывания. Adapter pattern preserves backend-substitution-readiness. |
| 2026-05-28 | **Localization MVP — all common languages from day 1.** Используем **system locale** (без app-level переключателя). Foundation: ADR-004 i18n-readiness обязательна с первого коммита. **CI fitness function**: проверка, что все строки переведены на supported languages. **Initial set** (best-guess based на «распространённые»): RU, EN, ES, ZH (Mandarin), AR, HI, PT, DE, FR, JA. Post-MVP добавляем по market signals. | Если перевод плохого качества — reputation hit. Особенно для senior audience, где text — critical. | Translation pipeline (community / professional / AI-assisted) — adapter pattern. Можно сменить provider без переписывания. Fitness function ловит missing translations до релиза. |
| 2026-05-28 | **Google OAuth + email-bound identity в MVP** (admin only). Подтверждает D-Pair-1 hybrid resolution: **admin = named auth (Google Sign-In + email-bound)**, **Managed = anonymous + paired** через спеку 007. Email используется для: subscription billing (D-11), account recovery, deletion confirmation (см. account deletion ниже), multi-device admin (D-Pair-1). | Если Google Sign-In integration усложняет onboarding admin'а (extra шаг auth перед setup wizard) — отток на этом шаге. | Anonymous auth для admin'а доступен как fallback, но без recovery / multi-device. Можно сделать «гость mode» если adoption suffers. Backend-substitution readiness — AuthProvider port (backlog AUTH-001). |
| 2026-05-28 | **Social recovery — НЕ MVP, accepted edge case.** Vision говорит «recovery without admin», но: orphan admin scenario (последний admin потерял все устройства, нет co-admin'a) = **total data loss**. Принято user'ом: «потерял так потерял». Спека 011 OWD-4 social recovery **не делается** в MVP. Post-MVP — open. | Первый громкий случай (семья потеряла всё) = bad PR. Acceptable risk per user's explicit decision. | Social recovery как fully spec — можем добавить в v2. Mitigation в MVP: подталкиваем admin'а добавить co-admin рано в setup wizard («рекомендуем добавить второго родственника как backup admin'a»). |
| 2026-05-28 | **Performance acceptance gates для MVP (D-33).** Cold start ≤ **1 sec p95** on Pixel 5 class device. Frame budget: **≥ 95% frames render < 16ms** on main flows. APK size **≤ 30 MB**. RAM peak **≤ 200 MB** on 2GB device. Battery passive (no foreground): **≤ 1% per day**. **Macrobenchmark в CI mandatory** для cold start (PERF-001). **APK size monitoring per PR**. | Если low-end devices (под 2GB RAM, под Pixel 5) — наш реальный target — нужны более жёсткие limits. Если Pixel 8+ — можем расслабить. | Каждый gate — adjustable per build flavor. Можем релаксировать для dev builds, держать жёстко для release. Не блокирует архитектурно. |
| 2026-05-28 | **NEW: CLAUDE.md rule 10 — Notification minimization (push hygiene).** Каждый push notification должен соответствовать **трём критериям** одновременно: actionable + time-sensitive + user-relevant. Иначе → in-app indicator или in-app notification center. Refuse pattern #13 в CLAUDE.md. **Новый skill `checklist-notification-minimization`** активируется через procedure-assess-spec-complexity при обнаружении push events в спеке. | Если строгая push hygiene приведёт к тому, что critical events не доходят до admin'а — потеря safety net. | Каждый push в продукте подлежит review по этому rule. Если выяснится, что какой-то event critical и должен push'иться — добавляется в спеку с явным severity rationale. Не нарушает rule. |
| 2026-05-28 | **Account deletion flow — MVP scope.** Settings → Account → «Удалить мой аккаунт» с explicit consequences list + re-auth. **Grace period 30 дней** soft delete (login still works, can cancel). После grace — hard delete batch job. Admin handover: при initiation singleton admin требуется designate successor co-admin OR group dissolution. Envelope wrapper cleanup (наш wrappers удалены, blob остаётся для других). Audit log с deletion hash (compliance proof). Email confirmation initiation + final. **AccountDeletion port** в core/domain с adapter implementation. **Mandatory для Google Play Policy + GDPR Art. 17 + 152-ФЗ**. | Если 30 дней grace окажется too long для какого-то рынка (RU например) или too short — adjustment. | Grace period configurable per region (post-MVP). Hard delete batch — manual cron в MVP, automated через Blaze Cloud Functions post-MVP. |
| _(заполняется при дальнейшем обсуждении)_ | | | |

---

## 1ter. Vision additions (2026-06-15)

Серия архитектурных принципов, зафиксированных при детальном разборе Phase 2. Полные decision documents — в [`docs/product/decisions/2026-06-15-deferred-cloud/`](../decisions/2026-06-15-deferred-cloud/).

### Каждое устройство самодостаточно

App ставится → wizard'ом настраивается → **работает локально** без Google Sign-In, без интернета, бессрочно.

Cloud features (pair, sync, push, remote management) — **дополнительный слой**, появляющийся только когда нужен. Sign-In запрашивается **в момент первого cloud action**, с понятным объяснением «что вы получите за Sign-In».

Это **не «бесплатная демо-версия»**. Local-mode launcher — самостоятельная ценность: упрощённая раскладка, контакты, темы, размер шрифта. Семья с одним пожилым без admin-родственника получает полезное приложение, не пользуясь никакой инфраструктурой.

### Конфиг принадлежит устройству, на котором живёт

Никакого «admin владеет бабушкиным конфигом». Конфиг бабушкиного телефона принадлежит **Google-аккаунту, в который вошли на её устройстве** (либо она сама, либо компетентный взрослый при первой настройке).

Pairing = **права** admin'а править её конфиг, **не передача собственности**. Revoke pair = admin теряет права; её конфиг остаётся в её namespace, без изменений. Удаление admin'ского аккаунта = удаление его namespace, **не** трогает чужие конфиги.

### Billing — только за cloud-инфраструктуру

Local-mode launcher — бесплатно бессрочно. Cloud-mode (pair, sync, push, remote) — после Sign-In = месяц trial → subscription.

Subscription expired = автоматический downgrade в local-mode (конфиг сохраняется, cloud features паузятся, можно возобновить).

Никакого forced engagement, ads, feature gating локальных функций.

### Setup persona = компетентный взрослый

Первая настройка телефона **всегда** делается человеком, способным пройти wizard и Sign-In (если cloud features нужны). Это может быть:
- сам пожилой пользователь (если у него достаточно опыта),
- родственник, который физически берёт его телефон в руки,
- родственник, который удалённо подключается **после** того, как кто-то компетентный сделал базовую настройку.

**Не предполагаем** «бабушка одна устанавливает телефон». Если человек настраивает телефон — значит он имеет на это компетенцию. Сокращения wizard'а ради «бабушка устанет» — отменены. Wizard может быть и 5, и 9 шагов; важна функциональность, а не количество.

### Pairing primary — QR через камеру

Два телефона физически рядом, один наводит камеру на QR-код на экране другого. Это уже реализовано в [спека 007](../../specs/007-pairing-and-firebase-channel/spec.md).

Любые remote-каналы (signed invite link через share intent, NFC, Bluetooth) — **additive add'ы** через [`PairingChannel` abstraction](../decisions/2026-06-15-deferred-cloud/04-pairing-channel-abstraction.md), появляются по мере необходимости (например, для caregiver remote invite в S-7). **QR не отвергнут**.

### Versioned config — без преждевременной generic абстракции

History rollback, multi-admin conflict resolution, local→cloud promotion merge — три use case для **одного** компонента `VersionedConfigViewer` в S-8.

Generic `Versioned<T>` НЕ делаем сейчас. `core/versioned-config/` — узкий модуль конкретно для `ConfigDocument`. Когда появится 2-й, 3-й потребитель — обобщаем по `Rule of Three`.

### Subscription защита от взлома

Cloud features всегда валидируются на сервере (server-validated entitlement JWT). Local mode не имеет cloud-features → нечего взламывать локально.

Новый skill [`checklist-tamper-resistance`](../../.claude/skills/checklist-tamper-resistance/SKILL.md) на любой cloud-feature спеке проверяет: используется ли server-validated entitlement, нет ли client-side license flag.
