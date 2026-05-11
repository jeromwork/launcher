# Dev Environment Setup

Чек-лист для развёртывания dev-окружения на новой машине. Обычно занимает 30 минут на чистом Windows.

## Что нужно установить (one-time per machine)

### 1. Системные зависимости

- [ ] **Git** ([git-scm.com](https://git-scm.com/download/win)) — `git --version` ≥ 2.40.
- [ ] **JDK 17** ([Adoptium](https://adoptium.net/temurin/releases/?version=17)) — `java -version` показывает 17.x.
  - Set `JAVA_HOME` env var → JDK path.
- [ ] **Android Studio** (latest stable) — для эмуляторов + gradle wrapper sync.
  - Установить Android SDK platforms 26-35 через SDK Manager.
- [ ] **Node.js LTS 20+** ([nodejs.org](https://nodejs.org/)) — `node --version` ≥ v20, `npm --version` ≥ 10.

### 2. CLI инструменты (через npm)

```powershell
npm install -g firebase-tools wrangler
firebase --version    # должно быть 15.x+
wrangler --version    # должно быть 4.x+
```

### 3. Аутентификация в облачных сервисах (требует браузер один раз каждое)

```powershell
firebase login    # выберешь g.jeromwork@gmail.com
wrangler login    # выберешь gpt1.jeromwork@gmail.com
```

Verify:
```powershell
firebase login:list           # должен показать g.jeromwork@gmail.com
firebase projects:list        # должен включать launcher-old-dev
wrangler whoami               # должен показать Account ID c8f9c8c59e930e0283d713b91c01fb13
```

### 4. Repo clone + build

```powershell
git clone <repo-url> launcher
cd launcher
.\gradlew assembleMockBackendDebug    # должно собраться без google-services.json
```

`mockBackend` flavor собирается **без** Firebase config — идеально для первой проверки что dev env работает.

Для `realBackend`:
```powershell
.\gradlew assembleRealBackendDebug    # требует app/google-services.json (уже коммичен)
```

---

## Что уже настроено в проекте (через git)

Ничего из перечисленного **не нужно** настраивать на новой машине — приходит вместе с `git pull`:

- ✅ `.firebaserc` — project alias `launcher-old-dev`.
- ✅ `app/google-services.json` — dev Firebase config (см. ниже про production).
- ✅ `firebase.json` + `firestore.rules` + `firestore.indexes.json`.
- ✅ `push-worker/wrangler.toml` — Account ID + Firebase project ID.
- ✅ `push-worker/src/index.js` — Worker stub (полная реализация в Phase 5 спека 007).

---

## Что НЕ в git (хранится только на cloud-сервисах)

| Артефакт | Где живёт | Доступ |
|---|---|---|
| `FIREBASE_SA_JSON` (service-account private key) | Cloudflare Secrets Store (encrypted) | Только Worker во время выполнения |
| Firebase Auth users (тестовые) | Firebase project (cloud) | Firebase Console |
| Firestore data (тестовая) | Firebase project (cloud) | Firebase Console + emulator (локально) |
| Wrangler OAuth токен | Локально в `%APPDATA%/xdg.config/.wrangler/config/` | Только текущая машина |
| Firebase OAuth токен | Локально в `%USERPROFILE%/.config/configstore/firebase-tools.json` | Только текущая машина |

---

## Production vs Dev (важно)

- **Dev**: `launcher-old-dev` Firebase project + `gpt1.jeromwork` Cloudflare account. Используем для всего development'а. `google-services.json` коммитится в git.
- **Production** (TODO-OPS-004 в backlog): будут отдельные `launcher-old-prod` Firebase project + отдельный Cloudflare environment. Production `google-services.json` **НИКОГДА не коммитится** — приходит через CI secrets.

**При работе с production credentials** на машине:
- НЕ кладите production `google-services.json` в `app/` (это путает gradle).
- НЕ запускайте `firebase deploy --project launcher-old-prod` с dev-кода.

---

## Запуск проверки окружения

После всех установок — запусти валидатор:

```powershell
.\scripts\check-dev-env.ps1
```

Скрипт пройдёт по всем требованиям и покажет что не настроено + как починить.

---

## Частые проблемы

### `firebase login` открывает браузер, но возвращает ошибку

Чаще всего — прокси на корпоративной сети. Попробуй из другой сети (домашняя WiFi / мобильный hotspot).

### `wrangler deploy` пишет «email not verified»

Cloudflare backend пропагирует verification status медленно (до часа). Если ты уже подтвердил email через письмо — подожди 10-30 минут и попробуй снова. Иногда помогает **logout + login на Cloudflare Dashboard** (не в wrangler) — это сбрасывает session cache.

### `./gradlew assembleRealBackendDebug` падает с «Could not find google-services.json»

Файл должен быть в `app/google-services.json` (есть в git). Если случайно удалил — `git checkout app/google-services.json`.

### Android Studio не находит эмуляторы

См. `.claude/skills/android-emulator/SKILL.md` — там полная процедура запуска эмуляторов.

---

## Cross-references

- `docs/dev/project-backlog.md` — operational TODOs (2FA, key rotation).
- `docs/compliance/permissions-and-resource-budget.md` — какие permissions запрашиваем и почему.
- `docs/compliance/country-legal-tax-register.md` — PII inventory.
- `docs/adr/` — архитектурные решения (ADR-001..006).
- `specs/007-pairing-and-firebase-channel/` — текущий active спек.
