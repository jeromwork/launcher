# Checklist: wire-format — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass (CRITICAL — re-scope ввёл новый wire-format + breaking change PushPayload)

## Wire formats introduced by this spec

1. **PushTriggerRequest** — JSON body для `POST /push` (HTTP wire-format между client KMP и CF Worker).
2. **PushPayload** (refactored, breaking change) — FCM data-message payload (FCM wire-format Google → receiver Android).
3. **RecipientDeviceEntry extension** — добавляет `fcmToken: String?` к существующему F-5b Firestore doc `/users/{uid}/devices/{deviceId}` (Firestore persistence wire-format).

## Schema version

- [x] **CHK001** Every wire format carries `schemaVersion: Int` from first commit.
  - PushTriggerRequest: ✅ FR-001 + FR-013 explicit `schemaVersion: Int = 1`.
  - PushPayload: ✅ FR-024 explicit `schemaVersion: Int = 1`.
  - RecipientDeviceEntry: inherited from F-5b's existing schema (F-5b directory entry has its own schemaVersion per spec 018). Adding optional field `fcmToken: String?` — additive (CHK005), не requires bump.

- [⚠️] **CHK002** `schemaVersion` read first during deserialization.
  - **НЕ explicit FR в spec**. Standard pattern, но не зафиксирован.
  - **Action**: добавить FR-XXX в spec.md: «Deserializer MUST read `schemaVersion` first; if > MAX_SUPPORTED → fail fast без parsing остальных полей».

- [⚠️] **CHK003** `MAX_SUPPORTED` constant documented в коде (single source of truth).
  - FR-013 упоминает `MAX_SUPPORTED` (currently 1) но не pin'ит location.
  - **Action**: добавить в spec.md: «`MAX_SUPPORTED` константа живёт в одном файле `core/push/api/WireFormatVersion.kt` для Kotlin, `workers/family-push/src/contract/wire-format.ts` для Worker. Эти два должны быть синхронизированы при каждом bump».

## Backward compatibility

- [x] **CHK004** Reads of previous schema versions для 1 major release.
  - FR-051: «Adding new optional field — additive ... Adding new required field или renaming — major bump `schemaVersion 1 → 2` + parallel reads на 1 release».

- [x] **CHK005** Adding field allowed, missing fields handled with defaults.
  - PushPayload nullable defaults: `linkId: String? = null`, `payload: Map<String, String>? = null`, `ownerUid: String? = null`.
  - Kotlin Serialization handles missing fields via default values.

- [⚠️] **CHK006** Renaming/removing field requires versioned migration before breaking change ships.
  - FR-051 documents policy.
  - **Однако**: spec 019 САМ делает breaking change PushPayload (`linkId` from required в spec 007 → optional в spec 019). **Без schemaVersion bump** (стайs at 1).
  - **Accepted trade-off** (per Clarifications Q1=C): нет live consumers старого формата. Spec 007 declared `PushType.ConfigChanged` «forward-compat», никем не используется. Spec 008 (единственный potential consumer `linkId`) переписывается параллельно.
  - **Risk**: если есть dev devices с pre-F-5c app которые работают — они упадут при receive новой формы push.
  - **Action из meta-minimization**: запустить `Grep linkId` audit перед merge — убедиться что нет других consumers.

- [N/A] **CHK007** Migration code scoped.
  - Нет migration code нужно сейчас (first version). Future v1→v2 migration должна быть scoped (per FR-051). Future concern.

## Forward compatibility

- [⚠️] **CHK008** Reading newer schema versions handled gracefully (choice documented).
  - **Asymmetric strategy** (не задокументировано explicitly):
    - **Worker**: FR-013 «validates `schemaVersion <= MAX_SUPPORTED` → 400» — **fail-closed**.
    - **Receiver client**: FR-023 «Unknown eventType → silent log + ignore» — **fail-soft** для unknown discriminator. Не explicit для unknown schemaVersion.
  - **Argument for asymmetry**: Worker upgrade controlled (one deploy); client upgrade uncontrolled (users on old apps). Worker can fail-closed because deploys are atomic; client must fail-soft because mixed-version fleet.
  - **Action**: документировать asymmetry в spec.md: «Worker fail-closed на unknown schemaVersion (atomic deploy); receiver client fail-soft (skip unknown fields, log unknown discriminator)».

- [x] **CHK009** Discriminator (eventType) yields Failure on unknown, not crash.
  - Worker: FR-004 «Unknown eventType → 400».
  - Receiver: FR-023 «silent log + ignore».

## Tests

- [x] **CHK010** Roundtrip test exists для every wire-format type.
  - FR-050: «Roundtrip test (write → read → assert equal)».
  - Verification command: `./gradlew :core:push:jvmTest --tests *WireFormat*`.

- [x] **CHK011** Backward-compat test exists.
  - FR-050: «Backward-compat test (read v0 формат, который не существует пока, но contract запрещает регрессии)».
  - Acknowledges spec 019 IS the first version. Future v1→v2 backward-compat test обязателен при первом bump.

- [x] **CHK012** Test fixtures stored as files в `commonTest/resources/`.
  - Local Test Path: `core/push/commonTest/resources/push-payload-v1.json`.
  - Worker fixtures: `workers/family-push/test/fixtures/*.json`.

## Persistence specifics

- [N/A] **CHK013** SharedPreferences/DataStore keys namespaced.
  - Spec не вводит новых DataStore keys directly. FCM token writes в Firestore (не DataStore).
  - Если client cache idempotency-key state в DataStore — это будет implementation concern (plan.md).

- [N/A] **CHK014** SQLDelight migrations.
  - Spec не использует SQLDelight.

- [⚠️] **CHK015** Removed types have one-shot cleanup.
  - `linkId` field в PushPayload помечен deprecated (optional). Финальное removal — schemaVersion 2 bump в future spec (после spec 008 rewrite ships).
  - **Action из meta-minimization CHK013**: inline TODO в `core/push/api/PushPayload.kt`: «TODO(removal): drop `linkId` в schemaVersion 2 bump after spec 008 rewrite ships and no in-prod consumers remain».

## Deep-link / QR / exported config

- [⚠️] **CHK016** URL/QR payload embeds schemaVersion.
  - Worker URL static: `https://launcher-push.workers.dev/push`. **No `/v1/` в path**.
  - schemaVersion в body, не в URL.
  - **Acceptable strategy** (body-versioning): когда v2 нужен, body schemaVersion: 2, URL остаётся `/push`. Single endpoint, multi-version.
  - **Alternative** (URL-versioning): `/push/v1`, `/push/v2` — два endpoint'а. Жирнее.
  - **Action**: документировать в spec.md URL versioning choice: «Body-versioning chosen (schemaVersion в body); URL stable across versions. Worker reads `schemaVersion` from body, routes internally».

- [⚠️] **CHK017** Truncated/corrupted payload → user-facing error не crash.
  - Worker: malformed JSON → standard HTTP framework 400. ✅
  - Receiver: FCM data parse failure — **НЕ explicit FR**. Implicit through `LauncherFirebaseMessagingService` graceful handling, но не зафиксировано.
  - **Action**: добавить FR-XXX: «Receiver `LauncherFirebaseMessagingService.onMessageReceived` MUST handle malformed `data: Map<String, String>` (missing required fields, invalid JSON в payload field, schemaVersion > MAX_SUPPORTED) с Logcat warning + silent ignore. Never crash».

## Contract folder

- [⚠️] **CHK018** If `contracts/` exists: each contract file lists semantic version + breaking-change policy + roundtrip test link.
  - **Сейчас `specs/019-.../contracts/` НЕ существует.**
  - **Action**: создать в tasks.md задачи:
    - `contracts/push-trigger-request-v1.md` — wire-format для `POST /push` body, semantic version, breaking-change policy, link к roundtrip test.
    - `contracts/push-payload-v1.md` — wire-format для FCM data-message, semantic version, breaking-change policy, link к roundtrip test.
    - `contracts/event-type-registry.md` — реестр event types + per-event rules. Не wire-format strictly, но является stable contract между Worker и consumer specs (S-4, S-9, V-2).

## Summary

- **Pass**: 7/18
- **Partial/Warning**: 8/18
- **Fail**: 0/18
- **N/A**: 3/18 (CHK007, CHK013, CHK014)

**Big picture**: Wire format **спроектирован правильно** — schemaVersion с первого коммита, default-nullable fields, additive policy, roundtrip + backward-compat tests planned, fixtures стораджены файлами.

**Concerns** (все можно закрыть мелкими правками в spec.md):
1. `schemaVersion` MUST be read first — не explicit (CHK002).
2. `MAX_SUPPORTED` location не pin'нут (CHK003).
3. Forward-compat asymmetry (Worker fail-closed, client fail-soft) не задокументирована (CHK008).
4. Receiver malformed payload handling не explicit (CHK017).
5. URL versioning strategy (body-versioning) не задокументирована (CHK016).
6. Contracts folder ещё не создан — отложено в tasks.md (CHK018).
7. PushPayload breaking change без bump — accepted trade-off, но требует grep audit (CHK006 — already in meta-minimization actions).

## Action items (priority order)

1. **Высокая** (одна правка в spec.md): добавить блок «Wire-format policy» с:
   - schemaVersion-first deserialization (CHK002).
   - MAX_SUPPORTED location pinned (CHK003).
   - Forward-compat asymmetry documented (CHK008).
   - URL versioning strategy: body-versioning (CHK016).
   - Receiver malformed payload handling (CHK017).
2. **Средняя** (для tasks.md): создать `specs/019-.../contracts/` с тремя files (CHK018).
3. **Средняя** (из meta-minimization, повтор): grep audit `linkId` consumers + inline TODO для future removal (CHK006, CHK015).

---

## Заметка для новичка (TL;DR)

Проверено: **правильно ли версионированы данные**, которые летят по сети и могут «доехать» до разных версий приложения. Если завтра мы поменяем формат — старые телефоны не должны упасть, а должны мягко проигнорировать незнакомое.

**Хорошо сделано**:
- У каждого формата с самого начала есть номер версии (`schemaVersion: 1`).
- Все поля можно дополнять, не ломая старых читателей.
- Запланированы тесты «записал → прочитал → сравнил» и «прочитал старую версию».

**Чего не хватает** (8 «частично»):
- Не записано явно «номер версии читать первым» (стандарт, но не зафиксирован).
- Не записано как клиент должен реагировать на испорченный/неизвестный формат (не упасть, а проглотить тихо).
- Не создана отдельная папка `contracts/` с описанием каждого формата (нужно при /speckit.tasks).

**Главный риск**: мы делаем breaking change существующего `PushPayload` (поле `linkId` из обязательного в опциональное), но без bump'а версии. **Это допустимо**, потому что в проде нет ни одного потребителя старого формата, но **обязательно** перед merge'ем — прогнать `grep linkId` по коду чтобы убедиться. Если найдём — обрабатывать.

Это всё **не блокирует** переход к /speckit.plan, но требует одну правку в spec.md (блок «Wire-format policy») и одну операцию (grep linkId) до implementation start.
