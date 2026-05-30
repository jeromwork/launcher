# 12. Связь со спекой 014 и что закрывает F-4

**Дата фиксации**: 2026-05-30
**Назначение**: явная карта пересечений между текущей спекой 014 (в работе) и новой спекой 015 (F-4) — чтобы при сборке решений воедино не было пробелов.

---

## Что закрывает спека 014 (текущая, в работе)

**Спека 014**: tile editing — admin and senior profiles.
- **Status**: F-014.0 phase scaffold готов (commit `f8cc374`, T001-T005).
- **Branch**: `014-tile-editing-admin-senior-profiles`.
- **Scope F-014.0**: local-only DataStore для named configs, без server backup, без Google Sign-In requirement.

**Что эта спека закрывает из roadmap'а:**

1. **Бывший слот F-1 (Family Group)** — DEPRECATED 2026-05-28, его работа **redirected** в expanding pair-based architecture in spec 014 (roadmap line 160).
2. **Новый Foundation-блок F-014** — tile editing layer, в исходном roadmap не существовал, добавлен 29 мая.
3. **Adjacent decisions** в backlog (TODO-RESEARCH-009/010, TODO-FUTURE-PRODUCT-006, TODO-FUTURE-SPEC-007/008).

**Что F-014 НЕ закрывает:**
- F-2 (Capability Registry), F-3 (Wizard Module), F-4 (AuthProvider), F-5 (E2E Encryption) — не трогает.

---

## Фазирование F-014 (внутри спеки 014)

| Фаза | Scope | Dependencies | Когда |
|---|---|---|---|
| **F-014.0** (текущая) | Local-only DataStore named configs | — | **Сейчас**, ship'ится независимо |
| **F-014.1** | Server backup of named configs | **Требует F-4** (AuthProvider + Google Sign-In) + TODO-RESEARCH-009 + TODO-RESEARCH-010 | После F-4 |
| **F-014.2** | Encryption | **Требует F-5** (ConfigDocument E2E Encryption) | После F-5 |

Поэтому **F-4 (новая спека 015) разблокирует F-014.1**.

---

## Что меняется в спеке 014 после решений 2026-05-30

### Концептуальные правки (не блокируют F-014.0 scope)

1. **Preset = runtime named config, не build-time variant** (см. [01-unified-app-model.md](01-unified-app-model.md)). В спеке 014 `EditUiProfileSelector` остаётся valid pure function — меняется только семантика **источника** preset'а.

2. **Standard / Senior — не два жёстких enum-режима**, а derived state от текущего preset'а (см. [01-unified-app-model.md](01-unified-app-model.md)). Preset содержит десятки полей (lock_volume, lock_shade, tile_size, и т.д.), UI читает поля.

3. **Owner модель**: каждый app = свой Google UID. Бабушкин телефон — самостоятельный app с собственным owner'ом. См. [02-identity-anonymous-removal.md](02-identity-anonymous-removal.md).

### Что НЕ переписывается в спеке 014 (interpretation α')

- ✅ Spec 014 scope/tasks остаются.
- ✅ F-014.0 scaffold (T001-T005) и дальнейшие T006+ продолжаются.
- ✅ `EditUiProfileSelector.kt` остаётся pure function.
- ✅ Q1.1-Q9 clarifications остаются valid.

### Минорная правка (предлагается)

Добавить one-liner comment рядом с `EditUiProfileSelector` (или в spec):

> Preset = runtime named config (хранится локально F-014.0, на сервере F-014.1), может быть user-customized — не build-time constant. См. `docs/product/decisions/2026-05-30-f4-identity/01-unified-app-model.md`.

Это не блокирует F-014.0, делается в любой момент.

---

## Что закрывает спека 015 (F-4, mega-block)

См. [08-f4-spec-scope.md](08-f4-spec-scope.md) для полного scope.

Кратко:
- Замена anonymous Firebase Auth на named identity (Google Sign-In MVP, расширяемо).
- Переписка identity слоя в спеках 007-012.
- AuthProvider port + AuthMethod sealed (Google / Email / Phone / Apple).
- Pair = delegation между two identified users.
- Wipe pre-F-4 anonymous data.
- Privacy Policy + Data Safety + OAuth Consent.

---

## Dependency chain после решений 2026-05-30

```
014 F-014.0 (текущая)
  └─ ship'ится independently — local-only, без server, без auth
  
015 F-4 mega-block (следующая)
  └─ переписывает 007-012 на named identity
  └─ разблокирует F-014.1, F-5, S-2, S-6, S-7

После F-4:
  ├─ F-014.1 (server backup of named configs) — продолжение спеки 014
  ├─ F-5 (ConfigDocument E2E Encryption) — production blocker
  └─ S-* (MVP slices)

После F-5:
  └─ F-014.2 (encryption of named configs) — финальная фаза спеки 014

Post-MVP:
  ├─ Own-server разработка (Phase 1/2/3 cutover)
  ├─ F-7 (2FA admin device migration через own-server escrow)
  └─ V-1 iOS, V-4 TV, etc.
```

---

## Известные пересечения / возможные конфликты

| Тема | В спеке 014 | В новой модели (014↔015) | Как разрешать |
|---|---|---|---|
| Profile selector | `EditUiProfileSelector` принимает `FlowPreset` enum | Preset = runtime named config с десятками полей | Interpretation α': enum остаётся как input, источник — runtime |
| Named configs storage | F-014.0: local DataStore; F-014.1: Firestore под `adminUid` | После F-4: Firestore под Google-bound UID | Совместимо — `adminUid` теперь Google UID |
| Multi-device admin sync | Q1.2: named configs per Google account | Совместимо | OK |
| Compatibility key `(presetId, deviceClass)` | Q1.1 | Совместимо — добавится поле `formFactor` для TV в будущем | OK |

---

## Связанные документы

- [00-index.md](00-index.md) — оглавление набора.
- [01-unified-app-model.md](01-unified-app-model.md) — preset как runtime named config.
- [02-identity-anonymous-removal.md](02-identity-anonymous-removal.md) — owner модель.
- [08-f4-spec-scope.md](08-f4-spec-scope.md) — scope спеки 015.
- [`specs/014-tile-editing-admin-senior-profiles/spec.md`](../../../../specs/014-tile-editing-admin-senior-profiles/spec.md) — текущая спека.
