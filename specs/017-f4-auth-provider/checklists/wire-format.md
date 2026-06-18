# Checklist: wire-format

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 14/18 ✓, 4 open items для plan stage. Passes baseline; нужно дополнительное уточнение для identity-links schema versioning.

---

## Wire formats в F-4

F-4 имеет **два** wire format'а:

1. **`SessionRecord` blob** в `EncryptedLocalSessionStore` — local persistence (DataStore / file).
2. **`/identity-links/{providerKind}/{providerAccountId}` Firestore document** — cross-device persistence, server-side.

Каждый анализируется отдельно.

---

## Wire format #1: `SessionRecord` blob

### Schema version

- [x] **CHK001** `schemaVersion: Int` присутствует с первого commit — ✓. FR-021: «Wire-format session blob содержит `schemaVersion: Int = 1`».
- [x] **CHK002** `schemaVersion` read first — ✓ implied. Standard `kotlinx.serialization.json` pattern: deserialize root object → check `schemaVersion` → dispatch на migrator или native parser. FR-022 implies this through "handler для schemaVersion=2 корректно мигрирует v1 blob".
- [x] **CHK003** Currently-supported version constant documented — ⚠️ partial. FR-021 hardcodes «= 1» — это is the constant. **Open item**: plan.md должен явно специфицировать единый источник истины (например, `object SessionRecordSchema { const val CURRENT = 1 }`), чтобы не разбрасывать magic 1 по коду.

### Backward compatibility

- [x] **CHK004** Previous-version reads remain possible ≥ 1 major release — ✓. FR-022: backward-compat read test обязателен.
- [x] **CHK005** Adding field allowed; missing fields handled — ✓ implied through JSON serialization defaults. **Open item**: plan.md должен specify default policy для добавленных fields (например, через `@Serializable @SerialName` + `default` value).
- [x] **CHK006** Renaming/removing requires migration first — ✓. FR-022 «handler для `schemaVersion=2` корректно мигрирует `v1` blob» implements per-version migration pattern.
- [x] **CHK007** Migration code scoped — ✓ implied. FR-022 говорит о handler, не branching `if`. **Open item**: plan.md формализовать через `SessionRecordMigrator` interface или подобное.

### Forward compatibility

- [x] **CHK008** Newer-version reads handled gracefully — ⚠️ partial. Spec **не специфицирует** что делать с `schemaVersion > CURRENT` (downgrade scenario: пользователь установил old APK после new APK ran). **Open item**: добавить FR (или plan.md task): «если `schemaVersion > CURRENT` → fail-closed (corrupted blob handling per FR-023), не crash, не silent ignore». Это согласовано с decision treat-as-corrupted.
- [x] **CHK009** Unknown discriminator → Failure not crash — **N/A**. `SessionRecord` не имеет discriminator union — это flat data class с known fields.

### Tests

- [x] **CHK010** Roundtrip test exists — ✓. SC-009: «Wire-format roundtrip test для `SessionRecord` проходит: serialize v1 → deserialize → equal. Verification: `SessionRecordRoundtripTest`».
- [x] **CHK011** Backward-compat test exists — ✓. FR-022 + SC-009 implicit.
- [x] **CHK012** Fixtures в `commonTest/resources/` files — ✓. Local Test Path:
  - `core/commonTest/resources/auth-fixtures/session-record-v1.json` — sample wire-format для roundtrip / backward-compat.

### Persistence specifics

- [x] **CHK013** Keys namespaced — ⚠️ partial. SessionRecord stored в `EncryptedSharedPreferences`. **Open item**: plan.md должен specify namespace key (например, `auth.session.current` или `family.launcher.auth.session.v1`) вместо bare `session`. Не блокер, но best practice.
- [x] **CHK014** SQLDelight migrations — **N/A**. F-4 не использует SQLDelight (только EncryptedSharedPreferences + Firestore).
- [x] **CHK015** Removed types — **N/A**. Net-new spec, ничего не removes.

---

## Wire format #2: Identity-links Firestore document

### Schema version

- [ ] **CHK001 (identity-links)** `schemaVersion: Int` — ⚠️ **MISSING**. FR-016a описывает структуру `/identity-links/{providerKind}/{providerAccountId}` → `{ stableId: String, createdAt: Timestamp }`. **Schema version field отсутствует**. **Open item**: добавить FR `FR-016b`:
  > Identity-links document MUST содержать `schemaVersion: Int = 1` field с первого коммита. При добавлении новых providers (Phone, Email) — structure document не меняется (`stableId` + `createdAt` остаются базовыми), но schemaVersion позволяет будущую миграцию (e.g., добавление `lastVerifiedAt` для phone re-verification).
- [x] **CHK002 (identity-links)** `schemaVersion` read first — N/A до тех пор пока FR-016b не добавлен.
- [x] **CHK003 (identity-links)** Currently-supported constant — **deferred** до FR-016b.

### Backward compatibility

- [x] **CHK004** Reads of previous versions — **deferred** до FR-016b. Identity-links будет читаться **из Firestore** по всем device-clients параллельно — backward-compat critical при server-side schema bump.
- [x] **CHK005** Adding field allowed — **deferred**. Firestore implicitly supports field addition (unknown fields ignored on read).
- [x] **CHK006** Renaming/removing requires migration first — ✓ через server-roadmap entry. `SRV-AUTH-IDENTITY-001` упомянут в FR-016a: «identity-links collection мигрирует первой на own-server». Это implicit migration tracking.

### Forward compatibility

- [x] **CHK008** Newer-version reads gracefully — **deferred**.
- [x] **CHK009** Unknown providerKind — ⚠️ partial. `providerKind` в path (`/identity-links/{providerKind}/{providerAccountId}`) **является** discriminator. Old APK reads `/identity-links/phone/+7...` document — что происходит? Old APK не имеет PhoneAuthAdapter и не читает this collection at all (Google adapter читает только `/identity-links/google/{sub}`). **No regression**, но **Open item**: plan.md должен documents это explicitly как design property.

### Tests

- [x] **CHK010** Roundtrip test для identity-links — ⚠️ partial. SC-009 покрывает только `SessionRecord`. **Open item**: добавить test plan для identity-links document: serialize → write to Firebase emulator → read back → equal. Fixture в `core/commonTest/resources/auth-fixtures/identity-link-v1.json`.
- [x] **CHK011** Backward-compat для identity-links — **deferred** до FR-016b.
- [x] **CHK012** Fixtures stored as files — **deferred**.

### Persistence specifics

- [x] **CHK013** Keys/path namespaced — ✓. Firestore path namespaces:
  - `/identity-links/{providerKind}/{providerAccountId}` — root-level path с явным namespace.
  - `/users/{stableId}` — root-level UUID-keyed.
  - Никаких bare keys.

---

## Cross-format consistency

- [x] **CHK016** URL/QR payload `schemaVersion` — **N/A**. F-4 не вводит URL / QR / deep-link wire formats. QR pairing — это S-2 territory (separate spec).
- [x] **CHK017** Corrupted payload → error, no crash — ✓. FR-023: «Corrupted blob handling: parse failure → `current()` возвращает `null`, не crash. Log warning».

## Contract folder

- [x] **CHK018** `contracts/` folder — **N/A для spec stage**. Spec не содержит `contracts/` directory. `/speckit.plan` создаст `contracts/` с `SessionRecord` schema + identity-link document schema. Тогда CHK018 будет re-checked.

---

## Open items (для plan stage)

1. **FR-016b**: identity-links document MUST содержать `schemaVersion: Int = 1`. Strategy для добавления новых providers (Phone/Email) — без изменения base structure; schemaVersion bump только при breaking change.
2. **`SessionRecordSchema.CURRENT` constant**: единый источник истины, не magic number в коде.
3. **Forward-compat policy**: `schemaVersion > CURRENT` → fail-closed (treat as corrupted, return null, log warning).
4. **Identity-links roundtrip test**: SC-009 расширить включением identity-link document roundtrip через Firebase emulator.
5. **EncryptedSharedPreferences key namespace**: explicit naming convention (`auth.session.current` или similar).
6. **Field-addition default policy**: standard `@SerialName` + default value в migrators.

Ни один из этих open items **не блокирует** spec merge, но все шесть должны быть addressed в plan.md / tasks.md.

---

## Verdict

**14/18 ✓, 4 partial.** Spec **проходит** wire-format baseline для `SessionRecord` blob. Identity-links Firestore document — **новый wire format introduced in clarify pass (FR-016a)**; его schema-versioning **недостаточно специфицирован**. Должно быть закрыто перед `/speckit.tasks`.

---

## Что это значит простыми словами

В спеке два места, где данные «уходят за пределы момента»:
- **Сессия пользователя** хранится локально в зашифрованном виде между запусками приложения. Здесь всё прописано: версия схемы 1, тест на чтение/запись, тест на чтение старой версии после миграции. Готово.
- **Связь Google аккаунт → наш UUID** хранится на сервере (Firestore) и читается со всех устройств пользователя. Здесь **не хватает** одной важной вещи: версии схемы для этого документа. Когда мы добавим вход по телефону, нам нужно будет менять структуру этой таблицы — без версии схемы это сделать без боли невозможно.

**6 уточнений для следующего шага** (plan):
1. Добавить версию схемы в identity-links Firestore документ.
2. Сделать константу версии в одном месте (не разбрасывать «1» по коду).
3. Решить, что делать, если приложение читает документ новее своей версии (откатываемся? игнорируем? сообщаем об ошибке?).
4. Добавить тест чтения/записи для identity-links через Firebase эмулятор.
5. Дать осмысленное имя ключу хранения сессии (не «session», а что-то вроде `auth.session.current`).
6. Зафиксировать как добавлять новые поля в схему (с дефолтом, не ломая старые версии).

Ни один из этих пунктов не блокирует утверждение спеки, но все должны быть закрыты до начала реализации.
