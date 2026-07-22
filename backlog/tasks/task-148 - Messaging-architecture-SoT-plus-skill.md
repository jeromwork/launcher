---
id: TASK-148
title: Messaging architecture SoT + messaging skill + architecture-sourcing meta-process
status: Done
updated_date: '2026-07-22 00:00'
assignee: []
created_date: '2026-07-22 12:20'
labels:
  - architecture
  - messaging
  - docs
milestone: m-1
dependencies: []
priority: high
ordinal: 148000
decision-supersedes: []
superseded-by: null
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Мы решили целиться в **мессенджер** как ядро (лаунчер и галерея — это «урезанный мессенджер»: один и тот же поток зашифрованных данных к получателям). Прежде чем писать код, провели 8 исследований по первоисточникам: что умеет средний мессенджер, что из этого есть готовое (либы/SDK), что придётся писать, и как всё это **правильно разрезать на абстракции**, чтобы потом не переписывать.

Эта задача — **консолидация того research'а в источник правды** (по образцу TASK-145 для крипты): короткий umbrella-файл + файл про стабильный «шов» + тонкий skill-роутер. Плюс — оформить сам приём «research → архитектура → doc + skill» как переиспользуемый мета-процесс.

**Только документы — ноль кода.**

## Зачем

Чтобы будущая сессия не пересобирала архитектуру мессенджера с нуля, а упиралась в готовый, выверенный разрез. Главная защита от переписывания — правильные швы (порты) и инвариант «фичи живут в домене, а не в вендорском адаптере».

## Что входит технически (для AI-агента)

- `docs/architecture/messaging.md` — umbrella: разрез (volatile↔stable), карта зон, build-vs-buy, copyable blueprints, **Open questions** (транспорт-решение полностью в файле, по стандарту ecs.md §9), **конвенция пометки устаревших решений**, routing.
- `docs/architecture/messaging-substrate.md` — стабильный шов: `MessagingPort`, конверт, модель порядка, офлайн-ящик, инвариант доменной таксономии.
- `docs/architecture/messaging-features.md` — таксономия фич (reactions/replies/edits/…) + group governance; всё — доменные типы, копия из Matrix/MIMI, per-feature либ нет.
- `docs/architecture/messaging-delivery.md` — blind-courier сервер (`DeliveryServicePort`): serialization/mailbox/KeyPackage/push; Cloudflare stopgap → свой Rust (axum/tokio/sqlx/Postgres/openmls).
- `docs/architecture/messaging-calls.md` — `CallPort`: Jitsi SFU (Apache-2.0) + SFrame/MLS; честный caveat про метаданные звонка.
- `docs/architecture/gallery.md` — **отдельный домен** (sibling мессенджеру) `MediaPort`: галерея/rich-media = указатели, transform-before-encrypt, permissive-кодеки; потребляется и альбомом, и вложениями чата.
- `.claude/skills/messaging/SKILL.md` — тонкий роутер.
- `.claude/skills/procedure-architecture-sourcing/SKILL.md` — мета-процесс (стандарт ecs: вся правда в файле; MVA про код, не про доку).
- `.claude/skills/procedure-archpack-integrity/SKILL.md` — аудит арх-паков: dangling-ссылки, устаревшие указатели, INDEX-drift, truth-in-task leak.
- Правка `crypto.md` Rejected (Matrix = Apache-2.0, а не AGPL) + кросс-линк. Регистрация всех зон в `INDEX.md`.
- **Разрез = один зонный файл на порт/границу изменчивости** (substrate/features/delivery/calls/media). Ноль заглушек — вся исследованная архитектура перенесена в файлы (стандарт ecs.md: правда в файле, не в таске).

## Состояние

**In Progress.** Research завершён (8 passes, 2026-07-22). Пишем SoT-файлы + skill. Ноль production-кода.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] messaging.md написан: umbrella с AI-TLDR + карта зон (built/designed) + разрез volatile↔stable + build-vs-buy + routing
- [x] #2 [hand] messaging-substrate.md написан: контракт MessagingPort + форма конверта + модель порядка + офлайн-ящик + инвариант «таксономия фич в домене, не в адаптере»
- [x] #3 [hand] skill `messaging` создан (тонкий роутер по образцу crypto/ecs), триггеры покрывают messenger-термины
- [x] #4 [hand] skill `procedure-architecture-sourcing` создан с гейтом значимости (research → архитектура → doc + skill)
- [x] #5 [hand] Неверная строка Rejected в crypto.md переписана на корректную причину (Matrix ≠ AGPL)
- [x] #6 [hand] INDEX.md обновлён: messaging-домен зарегистрирован
- [x] #7 [hand] Zero production-code: git diff не содержит .kt / .rs / .gradle.kts / .ts
- [x] #8 [hand] 4 зонных файла (features/delivery/calls/media) написаны ПОЛНОСТЬЮ, ноль заглушек; каждый self-sufficient (AI-TLDR + инварианты + build-vs-buy + industry grounding с URL + Rejected)
- [x] #9 [hand] skill procedure-archpack-integrity создан; конвенция пометки устаревших решений (⚠️ SUPERSEDED) + секция Open questions прописаны в umbrella (стандарт ecs.md — правда в файле, не в таске)
- [x] #10 [hand] Разрез уточнён: messaging = ТОЛЬКО мессенджер; галерея вынесена в отдельный домен gallery.md; config-sync помечен как отдельный домен (ecs.md, не faucet); «one pipe three faucets» ретайрнут
- [x] #11 [hand] Coverage-аудит: закрыты гэпы — multi-device / история / presence / personal-block вписаны (§Cross-cutting + features); integrity-чек чистый (мои ссылки резолвятся, ноль dead-refs на удалённые файлы)
- [x] #12 [hand] Research-гэпы закрыты: новый домен safety.md (location+SOS, LocationPort, MSC3488/3489); disappearing/view-once/contact-card/stickers вписаны в features+gallery с готовыми решениями/либами
- [x] #13 [hand] Публичные группы: архитектурный вердикт + one-way-door шов записаны в messaging.md §Public groups (INV-M6 transport-kind discriminator; reuse = identity/media/taxonomy/facade; НЕ reuse = MLS/blind-server; search index Tantivy/Meilisearch MIT, не Typesense GPL)
<!-- AC:END -->

## Discussion
<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 (2026-07-22, mentor skill)

#### A.1 Что за область
Выбор крипто/бэкенд-основы для будущего family-мессенджера (TASK-27) и обобщение приёма «копировать выверенную архитектуру, а не изобретать». Владелец рассматривал Matrix (SDK + homeserver на Rust) как готовую основу.

#### A.4 Уточняющие вопросы + ответы владельца
- **Q**: что привлекло в Matrix? **A**: нужна федерация/мессенджер; не хочется писать всю обвязку самому.
- **Q**: zero-knowledge (rule 13) — нерушим? **A**: для MVP это бонус, готов обсуждать компромисс.
- **Q**: PCS важна? **A**: изначально готов был пожертвовать; но research показал, что MLS даёт PCS бесплатно (Remove+Commit) — жертвовать не нужно.
- **Q**: лицензия AGPL допустима? **A**: точно нет; покупать коммерческую лицензию — нет.
- **Q**: свой сервер? **A**: Cloudflare+Firebase — временно; цель — свой сервер на Rust.
- **Q**: звонки? **A**: через Jitsi Meet.

#### B: Итоги research (8 passes, первоисточники)
- **Matrix SDK = Apache-2.0** (не AGPL — запись в crypto.md Rejected неверна). Но homeserver Matrix структурно видит граф участников → ломает rule 13; крипта Matrix = Olm/Megolm (нет PCS), и Matrix сам мигрирует на MLS (MSC4244/4256, не готово).
- **Permissive batteries-included MLS-стека НЕТ.** openmls (MIT) / mls-rs (Apache) — только крипто-библиотеки; Phoenix/Wire — AGPL; готового MLS-сервера под permissive — нет.
- **Копируемый чертёж есть**: RFC 9750 (скелет) + Phoenix docs (тело DS/QS/AS/federation/threat-model, ~60-70%) + Signal specs (обвязка) + MIMI (федерация). Копировать дизайн законно (clean-room), код AGPL — нельзя.
- **Уменьшение самописа**: на managed-примитивах 7/8 подсистем = тонкий клей, сервер остаётся слепым. Неустранимое ядро: (1) multi-device/MLS group state (аналог Sesame), (2) сам delivery-сервер (~низкие тысячи строк Rust).
- **Фичи мессенджера НЕ имеют per-feature либ нигде** — это «типизированное сообщение + правило». Переиспользуемое = (a) инфра-либы (openmls, кодеки, WebRTC/SFU LiveKit/Jitsi), (b) опубликованные спеки-таксономии (Matrix event types, MIMI). Обработчики пишем сами (крошечные).
- **Разрез (anti-rewrite)**: изменчивое (транспорт Matrix→MLS, сервер CF→Rust, звонки Jitsi, медиа, push) — за портами; стабильное (домен + таксономия фич) — в домене. Ключевой инвариант: **таксономия фич живёт в домене, адаптер только маршалит** — иначе переезд Matrix→MLS = переписать все фичи.

Полный research — в истории чата сессии 2026-07-22; ключевые ссылки-первоисточники вписаны в `messaging.md` (industry grounding).

### Decision (English, immutable) 🔒

**Choice**: Aim the architecture at the messenger as the core substrate ("one pipe, three faucets" — chat / gallery / config-sync). Adopt a facade (`MessagingPort`) over the transport so MVP MAY run on Matrix (Apache-2.0) while the target is MLS (openmls) on an own Rust delivery server; Cloudflare+Firebase are an explicit rule-8 stopgap. Feature taxonomy (reactions, replies, edits, roles, blocks) lives in the DOMAIN as our own typed messages — adapters only marshal to Matrix events / MLS app-messages. Server stays a dumb, blind courier (rule 13): opaque group id + epoch counter + opaque mailbox tokens; app messages loose-ordered (client sorts), only Commits serialized per epoch. Reuse published architecture blueprints (RFC 9750 + Phoenix specs + Signal + MIMI) — clean-room from docs, never from AGPL code. Calls = adopt a whole SFU (Jitsi, Apache-2.0). This SoT task writes only the stable, cutting-defining docs now; volatile zone internals are stubbed until built (MVA).

**Rationale**: No permissive batteries-included MLS messenger SDK or server exists (Phoenix/Wire AGPL). MLS gives PCS natively (no need to sacrifice it) and, via Phoenix's design, keeps the server metadata-blind (no need to sacrifice rule 13). Matrix is the only permissive batteries-included path but leaks the membership graph and uses weaker (Megolm) crypto that Matrix itself is replacing with MLS — so MLS is the forward-looking target. The facade turns "MVP-now → own-server-later" from a rewrite into an additive adapter swap (rules 1, 2, 6). Putting feature taxonomy in the domain (not the vendor adapter) is the single cut that prevents rewriting every feature on transport swap.

**Applies to**: TASK-27 (family messenger), gallery/album feature, config-sync substrate reuse; `docs/architecture/messaging.md` + `messaging-substrate.md`; skill `messaging`; the `procedure-architecture-sourcing` meta-process.

**Trade-offs accepted**: If MVP ships on Matrix, migrating to MLS is code-swappable but NOT data-swappable — Megolm history + Matrix identities do not auto-migrate (documented exit-ramp cost). Own Rust server is more upfront code than the managed-primitive stopgap. Volatile zone docs are deferred (context lives in the umbrella zone map + owning tasks, not full files yet).

**Exit ramp**: `MessagingPort` isolates the transport; a second adapter (Matrix→MLS) is additive. Server behind `DeliveryServicePort`; Cloudflare→own-Rust is the rule-8 migration with inline `TODO(server-roadmap)`. Calls behind `CallPort`; Jitsi→LiveKit is an adapter swap. Full data-migration cost (if MVP on Matrix) recorded at build time in TASK-27.

<!-- SECTION:DISCUSSION:END -->
