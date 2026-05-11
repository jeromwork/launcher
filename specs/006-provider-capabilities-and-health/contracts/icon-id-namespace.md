# Contract: `iconId` Namespace Convention

**Version:** 1.0.0 · **Status:** Stable from спек 006 · **Owner:** spec 006
**Type:** String (NOT a wrapped class — see [data-model.md §4](../data-model.md#4-iconref-namespace-conventions))
**Test:** `IconRefNamespaceTest`

---

## Purpose

`iconId: String` фигурирует в `Capability` (FR-009) и в будущем в `/config` records (спек 008). Использует **namespace prefix convention** — без введения sealed class — чтобы wire-format мог расширяться через дополнительные namespaces без миграции существующих читателей.

---

## Format

```
<namespace>:<name>
```

- `<namespace>`: lowercase `[a-z][a-z0-9_-]*` (1+ chars).
- `:` separator (single colon, not URI scheme).
- `<name>`: `[A-Za-z0-9_-]{1,128}` (1..128 chars).

Validator regex: `^[a-z][a-z0-9_-]*:[A-Za-z0-9_-]{1,128}$`.

**Examples (valid):**
- `bundled:whatsapp`
- `bundled:system_settings`
- `custom:abc-123-def-456`
- `private:contact-photo-uuid-7890`

**Examples (invalid — reader returns `IconResolution.NotFound`, not crash):**
- `whatsapp` (no namespace prefix)
- `:whatsapp` (empty namespace)
- `BUNDLED:whatsapp` (uppercase namespace)
- `bundled:whats app` (space in name)
- `bundled:` + 200-char name (length exceeded)

---

## Reserved namespaces

| Namespace | Owner spec | Resolver | Use case |
|-----------|------------|----------|----------|
| `bundled:` | спек 006 | `BundledIconStorage` (this spec) | Built-in provider brand assets in APK |
| `custom:` | спек 007 / 009 | `RemoteIconStorage` (future) | Admin-uploaded custom tile icons via Firebase Storage |
| `private:` | спек 011 | `EncryptedMediaStorage` (future) | E2E-encrypted private photos (e.g. contact photos of family members) |

**Future namespaces** (when needed): claim by writing a contract amendment в этом файле. Forbidden namespaces: any uppercase, any reserved word collision (e.g. `default`, `null`, `none`, `system`).

---

## Resolution semantics

Per `IconStorage.resolve(iconId): IconResolution`:

- **Known namespace + resource exists:** `IconResolution.Drawable(handle)`.
- **Known namespace + resource missing** (e.g. `bundled:nonexistent`, `custom:deleted-uuid`): `IconResolution.Placeholder` (UI shows generic icon, log structured event per FR-052 category `missing_resource`).
- **Unknown namespace** (e.g. `future-thing:abc` in спеке 006 reader): `IconResolution.NotFound` (UI shows generic icon, log category `unknown_namespace`).
- **Invalid format** (regex mismatch): `IconResolution.NotFound`.

`BundledIconStorage` (sole implementation в спеке 006) recognises only `bundled:` namespace; everything else returns `Placeholder` (per FR-009 — graceful degradation для forward-compat unknown namespaces).

---

## Bundled name → drawable mapping

(Verbatim from [data-model.md §4](../data-model.md#4-iconref-namespace-conventions). Single source of truth: data-model.md.)

| iconId | Android drawable resource |
|--------|---------------------------|
| `bundled:app` | `R.drawable.provider_app` |
| `bundled:whatsapp` | `R.drawable.provider_whatsapp` |
| `bundled:telegram` | `R.drawable.provider_telegram` |
| `bundled:phone` | `R.drawable.provider_phone` |
| `bundled:sms` | `R.drawable.provider_sms` |
| `bundled:browser` | `R.drawable.provider_browser` |
| `bundled:youtube` | `R.drawable.provider_youtube` |
| `bundled:system_settings` | `R.drawable.provider_system_settings` |

---

## Test contract

`IconRefNamespaceTest` MUST cover:
1. `IconRef.bundled("whatsapp")` returns `"bundled:whatsapp"`.
2. `IconRef.isValid` rejects all invalid examples above and accepts all valid examples.
3. `IconRef.namespaceOf` and `nameOf` return correct parts for all valid forms.
4. `IconRef.namespaceOf("invalid")` returns null (no colon).
5. `IconRef.namespaceOf("bundled:whatsapp")` returns `"bundled"`.

`BundledIconStorageTest` (Android adapter, separate file) MUST cover:
1. Known `bundled:<name>` for all 8 providers → `IconResolution.Drawable`.
2. `bundled:unknown` → `IconResolution.Placeholder` + structured log event.
3. `custom:abc` → `IconResolution.Placeholder` (known namespace но no resolver в спеке 006).
4. `unknown:foo` → `IconResolution.NotFound`.
5. Invalid format `notvalid` → `IconResolution.NotFound`.

---

## Breaking-change policy

- **Namespace convention change** (e.g. switch separator from `:` to `/`): **major version bump** + migration. Cost: rewrite all stored iconIds.
- **Adding namespace:** **non-breaking** (forward-compat by design — existing readers see Placeholder/NotFound).
- **Removing namespace:** breaking ⟺ if any storage contains items in that namespace. Plan removal after deprecation period (≥ 1 major release).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** Контракт format'а строки `iconId` в `Capability`. Формат: `<namespace>:<name>`, например `bundled:whatsapp`. **Не sealed class** — обычная string с конвенцией. Зачем: расширение namespaces (`custom:` для спека 007, `private:` для спека 011) — без миграции wire-format.

**Конкретика, которую стоит запомнить:**
- Regex валидации: `^[a-z][a-z0-9_-]*:[A-Za-z0-9_-]{1,128}$`. namespace lowercase + alphanumeric/`-_`; name allows mixed case.
- Зарезервированы 3 namespaces: `bundled:` (спек 006), `custom:` (спек 007/009), `private:` (спек 011).
- 4 типа результатов resolve: `Drawable(id)` (нашли), `Placeholder` (известный namespace но missing), `NotFound` (неизвестный namespace или invalid format), invalid format.
- В спеке 006 единственная реализация — `BundledIconStorage`. Для `custom:`/`private:` возвращает `Placeholder` (не падает).
- Маппинг 8 known providers: `bundled:<name>` → `R.drawable.provider_<name>`.

**На что смотреть с осторожностью:**
- **Не вводить новые namespaces не-зарегистрированно**. Перед добавлением — обновить раздел Reserved namespaces в этом файле + соответствующий future spec.
- Изменение separator или regex format = **major version bump** контракта + миграция всех stored iconIds. Дорого.
- **Не путать `Placeholder` с `NotFound`**: первое — known namespace но resource missing (UI placeholder + log `missing_resource`); второе — unknown namespace (UI placeholder + log `unknown_namespace`). Разная диагностика.
