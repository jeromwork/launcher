# 08. Финальный scope спеки 015 (F-4)

**Дата фиксации**: 2026-05-30
**Spec branch (когда создадим)**: `015-f4-identity-layer`
**Estimate**: mega-block ~12-16 недель

---

## Цель спеки

Заменить anonymous Firebase Auth (текущая identity модель) на **unified named identity layer** через `AuthProvider` port, с Google Sign-In как первым реализованным провайдером. Переписать спеки 007-012 в части identity binding'а.

---

## Scope: что входит

### Domain (`core/commonMain/auth/`)
- `AuthProvider` port (см. [03-auth-provider-port.md](03-auth-provider-port.md)).
- `User` data class (REQUIRED email, без AnonymousUser).
- `AuthMethod` sealed type (Google + future cases).
- `AuthError` sealed type.
- Wire-format `/users/{uid}` с `schemaVersion: 1`.

### Adapter (`app/androidMain/auth/`)
- `GoogleSignInFirebaseAuthAdapter`:
  - Credential Manager API (рекомендованный путь, не deprecated Google Sign-In SDK).
  - Firebase Auth для session management (временно; см. inline TODO про own-server).
  - Email REQUIRED — refuse login при отсутствии.
  - Token refresh / session expiry handling.
- `AuthAdapterSelector` — runtime device-capability dispatch.
- `FakeAuthAdapter` для тестов (`core/commonTest/auth/`).

### Переписка существующих спек на named identity
- **007 pair-pairing**: pair = delegation между двумя Google-bound UID. Pair-binding модель документа в Firestore: `/delegations/{ownerUid}/helpers/{helperUid}`.
- **008 config sync**: UID источник = `AuthProvider.currentUser`, не `signInAnonymously()`. Schema bump ConfigDocument для Owner identity binding.
- **009 EditorScreen**: helper редактирует через delegated permission, validated server-side.
- **010 setup wizard**: первый шаг = Google Sign-In. Pair-binding — optional шаг.
- **011 contacts + E2E media**: crypto keys derived from Google-bound UID.
- **012 contact photos**: то же.

### Firestore Security Rules
- Переписать на `request.auth.uid` matching against named UIDs.
- Delegation-based access: helper может писать в owner config только если delegation существует и не revoked.

### Migration
- **Wipe** pre-F-4 anonymous pair'ов и configs (pre-release, реальных пользователей нет).
- НЕ строим migration tool — это разработка, очищаем вручную.

### Release prerequisites (записываются в spec, но pre-release tasks)
- Privacy Policy update — добавить «обрабатываем email, displayName, profile photo URL через Google Sign-In».
- Play Console Data Safety form — указать collected data + purpose.
- Firebase Console: включить Google Sign-In provider, добавить SHA-1 fingerprints (debug + release).
- OAuth Consent Screen в Google Cloud Console: запросить только `openid email profile` scopes (не больше — иначе нужна Google verification).

### Inline TODOs для own-server
Везде, где код вызывает Firebase Auth — `// TODO(server-roadmap): ...` (формат из файла 05).

---

## Scope: что НЕ входит

### Auth provider'ы
- ❌ Email/Password adapter — future spec.
- ❌ Phone (SMS OTP) adapter — future spec.
- ❌ Apple Sign-In adapter — future spec (V-1 iOS).
- ❌ Account linking — post-MVP.
- ❌ HMS / Huawei adapter — out of MVP.
- ❌ CrossDeviceAuthAdapter (TV) — post-MVP, V-4.

### Server cutover
- ❌ Own JWT issuer — Phase 1 own-server, post-MVP.
- ❌ Replacement of Firebase Auth — Phase 1 cutover.
- ❌ Own DB — Phase 2 cutover.

### Future-specific features
- ❌ 2FA helper device migration — отдельная спека post-own-server (см. [06](06-2fa-admin-device-migration.md)).
- ❌ Multi-account на одном устройстве (через Credential Manager) — UX это поддерживает «из коробки», но F-4 не строит per-account switching UX.

### UI
- ❌ Полный custom Sign-In screen — используем Credential Manager bottom-sheet (рисует Google).
- ❌ Senior-safe Sign-In UI — Google Sign-In UI не кастомизируется; настройка проходит в Standard mode (см. [01](01-unified-app-model.md)).

### Spec 014 changes
- ❌ Spec 014 (F-014.0) tile editing — продолжает работать без изменений. Минорный one-liner comment про runtime named config (не build-time) — отдельная задача, не блокирует F-014.0.

---

## Dependencies

### Готово до F-4
- Spec 014 F-014.0 (текущая работа) — может ship'иться **независимо** от F-4 (local-only DataStore).
- Firebase Auth setup в Firebase Console — admin task.
- Google Cloud Console: OAuth client ID — создаётся автоматически при включении Google Sign-In в Firebase.

### F-4 блокирует
- F-014.1 (server backup of named configs) — нуждается в stable Google UID.
- F-5 (ConfigDocument E2E Encryption) — нуждается в stable identity для key management.
- S-2 (Admin App Preset + Remote Pairing) — нуждается в named identity.
- S-6 (Account Deletion) — нуждается в Google email confirmation.
- S-7 (Caregiver Invite) — нуждается в named identity for invite linking.

---

## Local Test Path (D-2 mandatory)

- **JVM unit tests** на `core/commonTest/`:
  - `AuthProvider` port contract tests (через `FakeAuthAdapter`).
  - `User` invariants (email REQUIRED, refuse без email).
  - `AuthMethod` sealed exhaustive matching.

- **Android instrumented tests** на эмуляторе с Google Play Services:
  - Один smoke test реального Google Sign-In с тестовым `test-admin@fake.local` аккаунтом.
  - Token refresh test.
  - Session persistence через app restart.

- **Cross-device tests**:
  - Два эмулятора, оба с Google Sign-In, обмениваются delegation через QR.

- **Manual real device tests** (НЕ автоматизируется):
  - Реальное устройство, реальный Google account.
  - Smoke test sign-in / sign-out / delete account.
  - Smoke test pair establishment + revoke.

---

## Effort

**Mega-block ~12-16 недель** (3-4 месяца) одного разработчика, или меньше если parallelized:
- ~2 недели — `AuthProvider` port + `GoogleSignInFirebaseAuthAdapter` + `FakeAuthAdapter`.
- ~2 недели — `AuthAdapterSelector`, runtime detection, error handling.
- ~3-4 недели — переписка спек 007-010 (pairing, sync, editor, wizard).
- ~2-3 недели — переписка спек 011-012 (contacts + photos crypto rebinding).
- ~1-2 недели — Firestore Security Rules переписка + testing.
- ~1 неделя — Privacy Policy + Data Safety + OAuth Consent setup.
- ~1-2 недели — тестирование, integration, smoke.

---

## Что в спеке должно быть явно зафиксировано

1. **`AuthProvider` port — extensible sealed type** для AuthMethod. Future providers (Email/Phone/Apple) — first-class, не fallback.
2. **Email REQUIRED для User** — refuse без email.
3. **Anonymous Firebase Auth полностью удалён** — нигде в codebase не остаётся.
4. **Inline TODO(server-roadmap)** в каждом adapter'е (см. файл 05 — формат).
5. **Wipe migration** для pre-F-4 anonymous pair'ов.
6. **Senior accessibility для Sign-In UI** — Google рисует, не кастомизируется; настройка в Standard mode (см. файл 01).
7. **Cross-device pair recovery в MVP** — rescan QR (2FA migration отложена, см. файл 06).
8. **Privacy Policy + Data Safety + OAuth Consent** — pre-release tasks.

---

## Связанные документы

- [01-unified-app-model.md](01-unified-app-model.md) — один app, identity homogeneous.
- [02-identity-anonymous-removal.md](02-identity-anonymous-removal.md) — почему удаление anonymous.
- [03-auth-provider-port.md](03-auth-provider-port.md) — архитектура port'а.
- [04-google-as-one-of-many.md](04-google-as-one-of-many.md) — Google = один из.
- [05-own-server-migration-strategy.md](05-own-server-migration-strategy.md) — phased migration.
- [06-2fa-admin-device-migration.md](06-2fa-admin-device-migration.md) — отдельная спека post-own-server.
- [07-tv-and-other-form-factors.md](07-tv-and-other-form-factors.md) — TV swap в будущем.
- [`docs/product/roadmap.md`](../../roadmap.md) §F-4 — обновляется.
- [`docs/dev/server-roadmap.md`](../../../dev/server-roadmap.md) — обновляется.
- [`CLAUDE.md`](../../../../CLAUDE.md) rule 8 → расширяется или добавляется rule 11 про обязательный TODO(server-roadmap).
