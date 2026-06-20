# Multi-app Cohabitation + Chain-of-trust Recovery — research notes (P-10 in Phase 3)

**Status**: research notes для будущей P-10 спеки в Phase 3. Spec.md ещё не написан, ожидаемый trigger — перед messenger MVP (~2026-11). Номер реальной спеки будет назначен на момент `/speckit.specify`.

См. roadmap §P-10: [`docs/product/roadmap.md`](../roadmap.md).

Created 2026-06-18 to lock context от mentor-сессии перед messenger spec. Перенесено сюда из `specs/017-multi-app-cohabitation/README.md` 2026-06-18 (вечер) после того, как номер 017 переназначен на F-4 AuthProvider + Google Sign-In.

**Trigger**: создание полной спеки запускается **до** написания `spec.md` для messenger MVP (планируется ~2026-11 по owner-roadmap). По roadmap'у — после P-9 в Phase 3.

---

## Background context (DO NOT lose)

Семейство приложений (~5 месяцев от 2026-06-18):

- **launcher** — это приложение (F-CRYPTO 1.0.0 готов).
- **messenger** — E2E чат для пожилых.
- **photo album** — управление семейными фото.

Каждое — отдельный Android-package, отдельный sandbox. Каждое имеет свой экземпляр `:core:crypto` и свои ключи. Android **не разрешает** одному app читать файлы другого.

---

## Owner vision (буквальные слова от 2026-06-18)

> «При восстановлении лаунчера мы подтвердили его — и сам лаунчер также мог подтвердить, что вот мессенджер тоже доверенный. Чтобы одни ключи шифровали другие ключи. Как двухфакторная авторизация между разными лаунчерами на разных телефонах — здесь чтобы примерно так же работало для разных приложений, чтобы одно подтверждало другое. Условно один клик — нажал пользователь, восстановил доступ. Чтоб не для каждого приложения свои.»

UX-цель: **один клик восстанавливает доступ ко всем same-family app на устройстве**.

---

## Owner decision 2026-06-19 (F-5 clarify session) — signing strategy + unified wizard target

> «Будущая фича Cross APP. Я вижу брокер-паттерн. Мы можем подписать одним signing key в Google Play, и тогда заработает. Мы можем скачивать конфиг такой, что сразу же будет установлено приложение наше для мессенджера и для фотографий. Не нужно будет у него тоже входить, потому что будет один и тот же UID. То есть можем сразу собрать расширенный конфиг, скачиваем абстрактный, в котором будет преднастроено всё. Это то, к чему я стремлюсь. Чтобы экосистема была сразу готовая. Все приложения собирают свои конфиги, wizards, которые нужно настроить, и в лаунчер отправляют. Один wizard сразу всё настраивает. И всё готовое получается.»

**Зафиксированные решения 2026-06-19**:

1. **Signing strategy экосистемы**: ВСЕ приложения семейства (launcher, messenger, photo album, future apps) подписываются **одним signing key** в Google Play. Это **owner-level architectural decision**, не technical implementation detail.

2. **Cross-app sharing path — broker pattern (Path A)**: При расширении экосистемы — broker pattern (Microsoft Authenticator модель). Launcher = key holder. Messenger / album bind'ятся к launcher через `signature`-level permission ContentProvider или AIDL. Чужие APK (не подписанные тем же ключом) — Android их криптографически отсекает, и пользователь это не может отключить.

3. **Unified wizard vision**: Один wizard, агрегирующий steps от всех установленных co-family apps. Сценарий: user устанавливает launcher → launcher tells server «какие co-family apps пользователь хочет?» → server возвращает unified config с wizard steps от каждого app'а → user проходит one combined wizard → все apps настроены сразу.

4. **One-click recovery vision (re-confirmed)**: При recovery launcher'а — broker pattern автоматически восстанавливает доступ ко всем co-family DEKs (messenger ключам, album ключам). User вводит passphrase **один раз**, всё работает.

**Что это означает для F-5 (spec 018)**:

- F-5 wire-format `RecoveryVaultBlob` и `KeyRegistry` MUST быть **app-agnostic** (FR-022..024 уже зафиксированы). Никаких `packageName` в namespace ключей — глобальные имена (`config-cipher-aead-v1`, `pair-x25519-v1`).
- F-5 НЕ реализует broker pattern — это P-10 territory. Но формат должен быть совместим.
- `IdentityProof.currentIdentity()` возвращает Google UID (от F-4) — это и есть «единый UID для всех co-family apps».

**Что это означает для S-2 / S-5 / V-2 / V-3**:

- Когда регистрируют свои DEKs в `KeyRegistry` — используют global names (не per-package).
- При планировании их spec'ов — учитывать, что они **потребители**, не **создатели** identity / recovery.
- Wizard каждой spec'и должен быть **fragmentable** — экспортируется как steps, которые launcher агрегирует.

**Что это означает для V-2 messenger / V-3 album spec planning**:

- Wizard каждой app'и спроектирован как **manifest** (Decision D-22), не hardcoded UI.
- Launcher exposes `WizardAggregator` port (future spec, скорее всего P-10), к которому co-family apps push'ат свои wizard manifests.

**Подводные камни записать**:

- **Signing key rotation в Google Play**: Play App Signing меняет upload key, но app signing key (используется для broker permission) — стабилен. **Критично сохранить** app signing key.
- **Если signing strategy потом изменится** (например, выпуск open-source форка с другим signing) → broker pattern для них недоступен, fallback на cloud path (Path B).
- **Uninstall launcher'а на устройстве**: messenger и album теряют broker → fallback на cloud path (Path B). Owner decision: реализовать оба пути не обязательно, **MVP P-10 = только broker path**, owner accepts «uninstall launcher = degrade» risk.

**Не блокирует F-5 spec 018 — F-5 ship'ится, P-10 строит broker pattern поверх существующего F-5.**

---

## Decision deferred to spec authoring time

Три технических варианта для `chain-of-trust` (см. `docs/dev/crypto-review.md` §A2):

- **B — ContentProvider + custom permission** (Signal-style). Cohabitation на одной платформе.
- **C — Server-mediated handoff**. Cross-platform recovery (launcher Android → messenger iPhone).
- **B + C гибрид** (рекомендация mentor-сессии).

Variant **A (Independent)** — текущее поведение MVP первого релиза каждого app. Не меняется без новой спеки.

---

## Research questions для будущей spec-фазы

1. **ContentProvider permission UX** на Android 15/16 — изменилось ли поведение custom permission'ов?
2. **iOS App Groups + shared Keychain** — какие ограничения для cross-app data в одной Team ID?
3. **Cross-platform handoff format** — какой wire format для encrypted-pending-handoff на сервере? CBOR? Protobuf?
4. **Standalone install** — если пользователь поставил только messenger (без launcher), как messenger делает свой recovery? Нужен ли отдельный standalone flow?
5. **Reverse trust** — может ли messenger подтверждать launcher (а не только launcher подтверждает messenger)? Влияет на UX, если пользователь восстанавливает messenger первым.
6. **Key rotation cascade** — если launcher rotation, messenger тоже rotation? Или независимо?
7. **Trust revocation** — если пользователь хочет «удалить messenger из доверенного семейства», как это работает?

---

## NOT в scope этой спеки

- Сам messenger MVP — отдельная спека.
- Сам photo album MVP — отдельная спека.
- Server architecture для handoff'а — отдельная спека (`server-roadmap` или новая F-Server).
- Key rotation real-impl — future rotation spec (TBD, номер будет назначен при `/speckit.specify`).
- Data export UI — `docs/dev/crypto-review.md` §A3, отдельная спека.

---

## Inline TODOs в коде, ссылающиеся на эту документацию

- `core/crypto/build.gradle.kts` — `TODO(extract-when-2nd-consumer)` + `TODO(pre-release-audit): library extract sequence`.
- `app/src/main/java/com/launcher/app/di/F016CryptoModule.kt` — `TODO(pre-release-audit): multi-app cohabitation`.
- `core/crypto/src/iosMain/kotlin/family/crypto/SecureKeyStore.ios.kt` — `TODO(pre-release-audit): App Groups + shared Keychain access groups`.

Все TODO grep-discoverable: `grep -r "TODO(pre-release-audit):" core/ app/ docs/ specs/`.

---

## TL;DR простым языком

Когда у нас будут 3 приложения одной семьи (launcher + messenger + photo) на одном телефоне, владелец хочет, чтобы пользователь **один раз** подтвердил «это новое устройство — реально я», и после этого **все 3 приложения** автоматически восстановили доступ. Не три раза по очереди.

**Сейчас (MVP)** — каждое приложение восстанавливается отдельно. Не идеально, но работает.

**Через ~5 месяцев** (когда выйдет messenger) — надо реализовать «launcher подтверждает мессенджер, мессенджер подтверждает фото» через ContentProvider + custom permission на Android и App Groups + Keychain sharing на iOS.

**Документ нужен** чтобы через 5 месяцев мы не забыли контекст и точно знали, какие вопросы решать.
