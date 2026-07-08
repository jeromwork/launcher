---
id: TASK-116
title: Iconic pairing challenge component
status: Discussion
assignee: []
created_date: '2026-07-08 06:17'
updated_date: '2026-07-08'
labels:
  - component
  - ui
  - crypto
  - ux
  - phase-3
milestone: m-2
dependencies:
  - TASK-16
priority: medium
ordinal: 116000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Реюзабельный UI-компонент: показать пользователю **N иконок**, попросить выбрать **правильную**. Правильная определяется общим **seed'ом** между двумя устройствами / приложениями.

**Простой пример**: два устройства должны договориться «это те же самые пользователи». Оба показывают на экране одну и ту же иконку 🔥, потому что оба знают seed. Или одно устройство показывает 🔥, второе показывает три варианта 💧🔥📖, пользователь выбирает 🔥.

**Второй фактор аутентификации, дружелюбный для пожилых**: вместо PIN 6 цифр (страшно, долго) — тап на картинку. Проще и быстрее.

**Detereministic rendering**: иконка = base template (капля, огонь, книга и т.д.) + вариации (цвет, поворот, размер, акценты). Одинаковый seed → одинаковая иконка на обоих устройствах. Разные вызовы → разные вариации → защита от shoulder-surfing (наблюдатель не может «запомнить правильную» и использовать позже).

**Что происходит по шагам:**
1. Некий flow (cross-app pairing / QR SAS / recovery attestation) генерирует `challenge_seed` (32 байта random).
2. Оба участника (устройство A и устройство B) получают одинаковый seed через свой protocol.
3. Устройство A вызывает `IconChallengeGenerator.generateAnswer(seed)` — получает **одну** правильную иконку.
4. Устройство B вызывает `IconChallengeGenerator.generateChoices(seed, count=3)` — получает **три** варианта (правильный + N-1 distractors).
5. Устройство B отображает три варианта, пользователь тапает на совпадающую с той что на устройстве A.
6. `IconChallengeVerifier.verify(seed, chosen_index)` → match/no-match.

## Зачем

**Reuse >> 3 use cases уже видимы** (правило 4 CLAUDE.md test 1 — abstraction оправдана):

1. **TASK-115** — launcher как anchor подтверждает spoke app onboarding.
2. **TASK-67** (existing Draft) — QR pairing SAS verification. Сейчас планируется emoji sequence, iconic challenge — sensor-friendly альтернатива для пожилых.
3. **TASK-117** — social recovery attestation, дочка подтверждает восстановление мамы.
4. **TASK-103** — remote app lock trigger confirmation (защита от случайного нажатия admin'ом).
5. **TASK-10** — SOS confirmation (anti-accidental-tap для emergency button).
6. **TASK-114** — deterministic avatars for co-admins (бабушка запоминает «Таня = огонёк»).
7. **Future TASK-42 messenger** — contact avatars from identity.

**Consistency UX**: если разные task'и реализуют иконки по-разному, у пользователя разные UX везде. Единый компонент даёт единый visual language.

**Security invariants**: deterministic из seed, single-use challenge, timing constants, tap target ≥ 56dp — легко ошибиться в каждом feature отдельно, надёжнее централизованно.

## Что входит технически (для AI-агента)

**Domain layer** (`core/icons/`):
- `IconRenderer` port — deterministic `render(seed): SvgString`.
- `IconChallengeGenerator` port — `generateChoices(seed, count): List<IconVariation>` + `getCorrectIndex(seed): Int`.
- `IconChallengeVerifier` port — `verify(seed, chosenIndex): Boolean`.
- `IconVariation` value type — `{ baseIconId, color, rotation, scale, accent }`.

**Icon library** (`core/icons/library/`):
- 20-30 base icons в SVG-template форме (капля, огонь, книга, звезда, лист, ключ, замок, дом, сердце, часы, солнце, луна, гора, волна, цветок, дерево, птица, рыба, снежинка, гриб, ...).
- Каждая с parametrizable fill/stroke/rotation/accent.
- Distinct shapes — не путать между собой при уменьшенном рендере.

**Randomization dimensions**:
- Color palette — 10-15 senior-friendly цветов, contrast ≥ 4.5:1 против фона.
- Rotation — 0°/90°/180°/270° (или свободные углы, но risk шумного визуала).
- Scale — 0.9-1.1 (тонкая variation).
- Accent decorations — dots / waves / underline / none.

**UI Composable** (`app/ui/icons/IconChallengeDisplay.kt`):
- `IconChallengeDisplay(seed: ByteArray, count: Int, onSelect: (Int) -> Unit)`.
- Tap target ≥ 56dp (senior-safe override per constitution Article VIII §7).
- TalkBack contentDescription per icon.
- Distinct enough — visual QA required для проверки цветовой доступности.

**Fitness rules**:
- Konsist test: `IconChallengeDisplay` не может быть вызван с `count > 3` (cognitive load).
- Contrast ratio test для каждой цветовой пары.
- ContentDescription уникальность per rendered icon.
- Deterministic parity test: `render(seed)` возвращает identical output на JVM / Android / iOS.

**Wire format для seed** (per TASK-16 discipline):
- `IconChallengeSpec` — `{ schemaVersion, seed: 32 bytes, count: Int, ttl_seconds: Int }`.
- Roundtrip test.
- Backward-compat при добавлении новых base icons (feature-flag via schemaVersion suffix).

## Состояние

**Discussion, 2026-07-08.** Concept зафиксирован в mentor-сессии TASK-115. Реализация ждёт активации TASK-115 или TASK-117 (первый реальный consumer).

**Ещё открыто**:
- Точный count base icons (20 vs 30) и accent dimensions.
- Выбор SVG library (custom generated vs использование existing — material icons / tabler / iconify).
- Color palette design (кооперация с design-system.md).
- Тест-сценарии для visual accessibility (colorblind users, screen reader, low vision).

---

## Пример сценария (use-case)

Бабушка на новом планшете установила лаунчер и мессенджер (см. TASK-115). Мессенджер запрашивает подтверждение доверия от лаунчера.

**Экран лаунчера** (overlay или основной экран):
> «Мессенджер запрашивает подтверждение. Выберите эту иконку в мессенджере:»
> [Большая иконка 🔥 оранжевого цвета, повёрнутая на 90°]

**Экран мессенджера** (первый экран после install):
> «Выберите иконку, которую показывает лаунчер:»
> [💧 голубая] [🔥 оранжевая 90°] [📖 зелёная]

Бабушка (или дочка помогает) видит совпадение — тапает 🔥. Мессенджер: «Спасибо, восстанавливаем...».

**Что произошло технически**:
- seed сгенерирован сервером при `POST /v1/challenge/start` — 32 байта random.
- Мессенджер получил seed, вызвал `generateChoices(seed, 3)` → три варианта.
- Лаунчер (через FCM push или local IPC на том же устройстве) получил тот же seed, вызвал `render(getCorrectIndex(seed))` → правильная иконка.
- Пользователь тапнул index 1 (правильный).
- Мессенджер: `verify(seed, 1)` → true → продолжает.

<!-- SECTION:DESCRIPTION:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 — 2026-07-08 (concept)

Идея возникла в mentor-сессии TASK-115 когда владелец предложил использовать SVG иконки вместо PIN-кода как второй фактор при cross-app pairing. Обосновано:

- Пожилые лучше воспринимают визуальный образ чем цифры.
- SVG компактен (~1-2 KB), рисуется на любом размере без потерь.
- Programmable — randomization через изменение атрибутов в runtime.
- Anti-shoulder-surfing через рандомизированный рендеринг.
- Anti-automated-attack — атакующий с чужим устройством не видит правильную иконку.

Владелец подчеркнул: иконка будет использоваться в нескольких местах, не только в pairing. Значит — общий переиспользуемый компонент, не inline в каждом task'е.

### Decision (English, mutable pre-implementation) 🔒

*Not yet written. Session 2 formalizes when TASK-115 or TASK-117 approach implementation.*

<!-- SECTION:DISCUSSION:END -->
