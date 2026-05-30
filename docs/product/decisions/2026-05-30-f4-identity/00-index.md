# Решения от 2026-05-30 — F-4 Identity Layer + сопутствующие архитектурные сдвиги

**Дата обсуждения**: 2026-05-30
**Контекст**: Подготовка спеки 015 (F-4 AuthProvider + Google Sign-In). В ходе обсуждения серия решений переросла в **переосмысление нескольких архитектурных слоёв**: identity модель, app packaging (один app vs несколько), стратегия миграции на свой сервер, push transport.

**Статус**: фиксация принятых решений. Сборка воедино и финальное обдумывание концепции — на стороне владельца проекта.

---

## Документы в этой папке

| # | Файл | О чём |
|---|---|---|
| 01 | [`01-unified-app-model.md`](01-unified-app-model.md) | Один app с двумя UI-режимами (Standard / Senior), переключение через wizard. Не два APK. Preset = именованный набор runtime-конфигов. |
| 02 | [`02-identity-anonymous-removal.md`](02-identity-anonymous-removal.md) | Удаление anonymous Firebase Auth полностью. Каждый app = свой Google-аккаунт = свой Firebase UID. Pair = delegation. |
| 03 | [`03-auth-provider-port.md`](03-auth-provider-port.md) | `AuthProvider` port + `AuthMethod` sealed type (Google / Email / Phone / Apple). MVP реализует только Google. |
| 04 | [`04-google-as-one-of-many.md`](04-google-as-one-of-many.md) | Google Sign-In = частный случай. Регистрация через Google, сессия — будущий own JWT issuer (стандартный pattern). |
| 05 | [`05-own-server-migration-strategy.md`](05-own-server-migration-strategy.md) | Phased migration. Own-server начинается ПОСЛЕ MVP. Сейчас — только TODO в коде. FCM/APNs остаются как push transport forever. Обязательное правило `TODO(server-roadmap)` inline. |
| 06 | [`06-2fa-admin-device-migration.md`](06-2fa-admin-device-migration.md) | При смене телефона admin'а — восстановление через 2FA escrow на own-server (не Firebase). Отдельная спека post-own-server. |
| 07 | [`07-tv-and-other-form-factors.md`](07-tv-and-other-form-factors.md) | TV launcher — концептуальное понимание. Архитектурные швы: AuthProvider / EditUiProfile / ConfigDocument — расширяемые. Реализация TV — post-MVP. |
| 08 | [`08-f4-spec-scope.md`](08-f4-spec-scope.md) | Финальный scope спеки 015 (F-4 mega-block 12-16 недель). Что входит, что не входит, что переписывается. |
| 09 | [`09-google-sign-in-explained.md`](09-google-sign-in-explained.md) | Google Sign-In — концептуальное и техническое объяснение для не-Android-разработчика. Поток, термины, что настроить, типичные pitfalls. |
| 10 | [`10-three-axes-and-kmp-multiplatform.md`](10-three-axes-and-kmp-multiplatform.md) | Три ортогональных оси выбора (distribution / build / runtime mode). KMP мультиплатформенность — один codebase, разные UI source set'ы per platform. |
| 11 | [`11-google-services-breakdown.md`](11-google-services-breakdown.md) | «Google services» — это 7 разных сервисов, не один монолит. GMS / non-GMS устройства. FCM как особая боль. Billing. |
| 12 | [`12-spec-014-relationship.md`](12-spec-014-relationship.md) | Связь со спекой 014 (в работе). Что закрывает 014, что закрывает 015, dependency chain, interpretation α'. |

---

## Краткая сводка изменений vs предыдущая модель

| Слой | Было | Стало |
|---|---|---|
| App packaging | Отдельные preset-APK (Simple Launcher + Admin App) | **Один app**, runtime preset через wizard, 7-tap возвращает в Standard |
| Identity (admin) | Anonymous Firebase Auth | Google Sign-In (Firebase JWT сейчас → own JWT после cutover) |
| Identity (бабушка) | Anonymous Firebase Auth (paired) | **Тоже Google Sign-In** на своём устройстве. Anonymous удаляется полностью |
| Pair-binding | Связь двух anonymous UID через QR | **Delegation**: запись `/delegations/{ownerUid}/helpers/{helperUid}` с permissions |
| Own-server | «когда-нибудь» (rule 8) | **Через 6 месяцев** после MVP, phased. Сейчас только TODO в коде |
| Push transport | FCM | **FCM/APNs остаются forever**. Триггеры мигрируют на own-server |
| Spec 014 (текущая) | Profile selector на enum preset | **Совместима** (interpretation α'): preset = runtime named config, не build-time |
| MVP timeline | ~6-8 месяцев parallelized | **+3-4 месяца сдвиг** из-за F-4 mega-block (12-16 недель) и переписки 007-012 |

---

## Что НЕ менялось в обсуждении

- Constitution Article XIV §3 (backend substitution readiness) — остаётся в силе.
- CLAUDE.md rule 1 (domain isolation), rule 2 (ACL), rule 4 (MVA), rule 5 (wire-format versioning), rule 8 (server migration tracking), rule 9 (shareability), rule 10 (push hygiene) — остаются в силе.
- Spec 014 (tile editing F-014.0) — продолжает работу, не пересматривается на уровне scope/tasks.
- Roadmap deprecation F-1 (Family Group) — остаётся.
- F-5 (ConfigDocument E2E Encryption) — остаётся как production blocker, но теперь после F-4 mega-block.

---

## Открытые вопросы (не решены 2026-05-30)

1. **Distribution Play Store**: один listing с TV/phone разделением vs два listing'а — **зависит от того, можно ли регистрировать один APK в разных категориях с разными описаниями**. Sanity-check на стороне Play Console.
2. **Curated TV apps list** — формат, частота обновления, endpoint на нашем сервере. Откладывается до TV spec'а.
3. **non-GMS phone** — out of MVP. Long-term: возможный target после own-server (HMS / Email-Password / Phone Auth как primary).
4. **Spec 014 unified app model compatibility** — interpretation α' зафиксирована, но в самой spec 014 надо добавить one-liner comment про runtime named config (не build-time constant). Технически минорная правка.

---

## Дальнейшие действия (предлагаемая последовательность)

1. **Владелец проекта**: собирает воедино, обдумывает концепцию, подтверждает или корректирует решения в этих документах.
2. После подтверждения: обновляется [`docs/product/roadmap.md`](../../roadmap.md) с новой F-4 mega-block моделью + F-7 (2FA) + сдвиг timeline.
3. После подтверждения: обновляется [`docs/dev/server-roadmap.md`](../../../dev/server-roadmap.md) с таблицей миграции.
4. После подтверждения: создаётся ветка `015-f4-identity-layer`, запускается `/speckit.specify` со scope из [08-f4-spec-scope.md](08-f4-spec-scope.md).
5. **Параллельно**: при работе над любой спекой, трогающей сервер, **обязательно** inline TODO для own-server migration (новое правило, см. файл 05).
