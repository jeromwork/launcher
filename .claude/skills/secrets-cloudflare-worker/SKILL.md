---
name: secrets-cloudflare-worker
description: Step-by-step guide для загрузки секретов (API keys, credentials, service-account JSON) в Cloudflare Worker через `wrangler secret put`. Используется при первой настройке Worker'а, при добавлении новых secrets (например, B2_KEY_ID для Backblaze blob storage spec 011), при ротации ключей, или при онбординге Worker'а на новую машину разработчика. Invoke whenever: пользователь спрашивает «как загрузить secret в Cloudflare», «как ввести wrangler secret», «что делать после `wrangler secret put`», «куда вставлять ключ»; пользователь видит prompt `Enter a secret value:` и не знает что делать; пользователь устанавливает spec 011 / spec 007 push-worker на новой машине; пользователь ротирует B2/Firebase/любой другой credential. Skill даёт точную пошаговую инструкцию с troubleshooting + verify шагами + safety guardrails (где хранить, что НЕ делать).
---

# Skill: Cloudflare Worker secrets — пошаговая загрузка

Используется для загрузки секретных credentials (API keys, JSON service accounts, токены) в Cloudflare Worker через `wrangler secret put`. Главная цель — не дать пользователю ошибиться при работе с командной строкой и не потерять секреты.

Output language — Russian. Code/commands/identifiers — as-is.

---

## When to invoke

Trigger signals (any one is enough):

- Пользователь явно спросил: «как загрузить secret», «как ввести wrangler secret», «как добавить ключ в Cloudflare», «как обновить B2_*/FIREBASE_*/любой secret в Worker'е».
- Пользователь видит prompt `? Enter a secret value:` и пишет «что вводить», «что делать», «куда вставлять».
- Пользователь устанавливает spec 011 / spec 007 push-worker на новой машине (онбординг новой dev-машины).
- Пользователь ротирует credentials (например, после подозрения на утечку, или раз в N месяцев по политике).
- Пользователь только что создал новый API key в стороннем сервисе (Backblaze, Firebase, R2, любой другой) и спрашивает «куда теперь его».
- Пользователь видит `Error: secret X not found` в логах Worker'а и спрашивает почему.

## When NOT to invoke

- Пользователь спрашивает про вообще архитектуру Cloudflare Workers / зачем нужны secrets / как они работают изнутри. Это — mentor.
- Пользователь спрашивает где **физически** хранить мастер-копии секретов (Bitwarden / KeePass / другое). Это — отдельный вопрос про password manager, не про wrangler.
- Пользователь просит написать код Worker'а, который читает secrets. Это — обычная task.

---

## Project context (актуальные secrets в repo)

В `c:\work\launcher\push-worker\` Worker имеет:

| Secret | Где использовать | Источник значения |
|---|---|---|
| `FIREBASE_SA_JSON` | spec 007 push relay — service account JSON для FCM API | Firebase Console → Project Settings → Service Accounts → Generate new private key |
| `B2_KEY_ID` | spec 011 — Backblaze B2 access (keyID) | Backblaze Console → Application Keys → Add a New Application Key |
| `B2_APPLICATION_KEY` | spec 011 — Backblaze B2 secret (applicationKey, показывается ОДИН раз) | Same — сразу при создании key, отдельной странице потом нет |

**Не secrets, а vars** (хранятся в `wrangler.toml` в открытую):

- `FIREBASE_PROJECT_ID` — публичный ID Firebase проекта.
- `B2_ENDPOINT` — публичный S3 endpoint Backblaze, типа `s3.eu-central-003.backblazeb2.com`.
- `B2_BUCKET_NAME` — публичное имя bucket'а, типа `launcher-private-media-eastclinic`.

⚠️ Никогда не путать: vars в `wrangler.toml` (commit в git) ≠ secrets через `wrangler secret put` (зашифрованно на Cloudflare, в git НЕТ).

---

## Pre-flight checklist

**Before** запуска `wrangler secret put` — убедись что:

1. ✅ **Cloudflare account залогинен** через wrangler: `npx wrangler whoami` показывает email/account_id, не «not logged in».
   - Если не залогинен: `npx wrangler login` → откроется браузер → авторизуйся.
2. ✅ **PWD = `c:\work\launcher\push-worker\`** (где лежит `wrangler.toml`). Команды должны запускаться оттуда.
3. ✅ **Значение secret'а доступно** у тебя в password manager (Bitwarden / KeePass / etc.) или у тебя на экране сервиса-источника (Backblaze / Firebase Console).
4. ✅ **Имя secret'а правильное** — оно должно совпадать с тем, что Worker читает через `env.NAME` (см. `push-worker/src/env.ts` — там полный список).

---

## Core procedure — пошагово

### Шаг 1 — Открой PowerShell в правильной папке

```powershell
cd c:\work\launcher\push-worker
```

Проверь: `pwd` или `ls wrangler.toml` — файл должен быть.

### Шаг 2 — Запусти команду `wrangler secret put`

```powershell
npx wrangler secret put <SECRET_NAME>
```

Где `<SECRET_NAME>` — имя secret'а (например `B2_KEY_ID`, `B2_APPLICATION_KEY`, `FIREBASE_SA_JSON`).

**Что увидишь:**

```
 ⛅️ wrangler 3.114.17 (update available 4.94.0)
---------------------------------------------------------
▲ [WARNING] The version of Wrangler you are using is now out-of-date.
  ...

? Enter a secret value: »
```

⚠️ **Warning про update игнорируй** — это не ошибка, Worker всё равно деплоится корректно.

Wrangler ждёт ввода. Курсор стоит после `»`.

### Шаг 3 — Скопируй значение из источника

- **Bitwarden / KeePass**: открой запись → найди нужное поле (keyID, applicationKey, etc.) → кликни иконку «copy» рядом с полем (или Ctrl+C по выделенному).
- **Backblaze/Firebase Console**: выдели значение мышкой → Ctrl+C.

⚠️ **Не копируй с лишними пробелами** в начале/конце — wrangler сохранит ровно то, что получит. Двойной клик по слову обычно безопаснее, чем тройной (тройной берёт окружающие пробелы).

### Шаг 4 — Вставь в PowerShell

Кликни в окно PowerShell (чтобы оно стало активным). Используй ОДИН из способов вставки:

| Способ | Где работает |
|---|---|
| **Правый клик мышью** в окне PowerShell | Windows PowerShell / классический (по умолчанию = paste) |
| **Ctrl+Shift+V** | Windows Terminal / pwsh 7 |
| **Shift+Insert** | Везде, fallback |
| **Ctrl+V** | Только некоторые PowerShell версии, не везде |

⚠️ **Wrangler НЕ покажет, что ты вставил** — символы не появляются, курсор не двигается. Это **правильно** (как ввод пароля в `sudo`). Не паникуй.

### Шаг 5 — Нажми Enter

```
✨ Success! Uploaded secret <SECRET_NAME>
```

Если показал это — secret загружен на Cloudflare зашифрованно, виден Worker'у через `env.<SECRET_NAME>`.

### Шаг 6 — Verify

```powershell
npx wrangler secret list
```

Должен показать массив, включая твой secret:

```json
[
  { "name": "B2_APPLICATION_KEY", "type": "secret_text" },
  { "name": "B2_KEY_ID", "type": "secret_text" },
  { "name": "FIREBASE_SA_JSON", "type": "secret_text" }
]
```

⚠️ **Значения секретов НЕ показываются** — только имена. Это **правильно** — Cloudflare не даёт способа прочитать secret после загрузки (даже владельцу account'а). Это zero-trust design.

### Шаг 7 — Deploy

После того как **все нужные secrets загружены**, выкатить Worker:

```powershell
npx wrangler deploy
```

Должен показать:

```
Uploaded launcher-push (X.X sec)
Published launcher-push (Y.Y sec)
  https://launcher-push.jeromwork.workers.dev
Current Version ID: ...
```

С этого момента — Worker использует новые secrets. Старые версии остаются как rollback (Cloudflare хранит N последних).

---

## Multi-secret batch — типичный flow

При первой настройке или ротации обычно загружают несколько secrets подряд. Шаблон:

```powershell
cd c:\work\launcher\push-worker

npx wrangler secret put B2_KEY_ID
# paste keyID → Enter

npx wrangler secret put B2_APPLICATION_KEY
# paste applicationKey → Enter

npx wrangler secret list
# verify всё на месте

npx wrangler deploy
# выкатить
```

Не запускай `wrangler deploy` посередине — он либо упадёт (отсутствующий secret в коде Worker'а), либо выкатит частично сломанную версию. Делай ВСЕ secrets, потом ОДИН deploy.

---

## Troubleshooting

| Симптом | Причина | Fix |
|---|---|---|
| `Not logged in` при `wrangler whoami` | Сессия истекла или новая машина | `npx wrangler login` — откроет браузер |
| `account_id not specified` | `wrangler.toml` потерял `account_id` | Проверь `wrangler.toml` — должна быть строка `account_id = "..."` |
| Случайно нажал Enter без вставки | Пустой secret загружен (ошибка) | Просто повтори `npx wrangler secret put <NAME>` — перезапишет |
| Вставка не работает (правый клик не paste) | Windows Terminal по умолчанию = другой shortcut | Пробуй Ctrl+Shift+V или Shift+Insert |
| `Error: KV namespace not found` при deploy | Не относится к secrets — другая конфигурация Worker'a | Не наш случай, проверь bindings в wrangler.toml |
| Worker логи показывают `undefined` для env.B2_KEY_ID | Secret не загружен / опечатка в имени | `wrangler secret list` — сверь имя |
| Secret загружен, но Worker всё равно не видит | Старая Worker version cached / не задеплоил после `secret put` | `wrangler deploy` ещё раз |
| `Error: secret X not found` в production | Secret загружен в dev env, нужен в production | Cloudflare Workers в основном single-env; если используешь environments (`[env.production]`) — нужен `wrangler secret put X --env production` |

---

## Safety rules — что НЕЛЬЗЯ делать

1. ❌ **Никогда не коммить secret в git** — даже в private repo, даже временно. История остаётся навсегда.
2. ❌ **Никогда не вводить secret в любое окно которое его echo'ит** (например, обычный echo в PowerShell, или просто `notepad`). Только wrangler stdin или password manager.
3. ❌ **Никогда не пересылать secret через мессенджеры / email** — их провайдеры читают сообщения.
4. ❌ **Никогда не вставлять secret в URL** (типа `?key=...`) — URL логируется веб-серверами и в access logs.
5. ❌ **Не использовать один и тот же secret для production и dev** — если dev утечёт, production тоже скомпрометирован.

## Safety rules — что ОБЯЗАТЕЛЬНО делать

1. ✅ **Сохраняй secret в password manager** (Bitwarden / KeePass) **ДО** `wrangler secret put` — потому что Cloudflare после загрузки сам уже не покажет.
2. ✅ **Документируй каждый secret в Bitwarden entry**: что это, для какого сервиса, дата создания, какие permissions.
3. ✅ **Ротируй secrets** каждые 12 месяцев (или сразу при подозрении на утечку): создай новый key в источнике → `wrangler secret put` (перезапишет) → `wrangler deploy` → удали старый key в источнике.
4. ✅ **Verify через `wrangler secret list`** ПОСЛЕ каждого `secret put` — убедись что имя правильное, опечаток нет.
5. ✅ **Deploy через `wrangler deploy`** после batch'а secrets, не до.

---

## Output format

Когда invoke'ишь skill в ответ на вопрос пользователя:

1. **Определи цель**: пользователь застрял на `Enter a secret value:`? Хочет загрузить новый secret? Хочет ротировать? — Подбери соответствующую section выше.
2. **Дай конкретные команды**, не общую теорию. Если у пользователя на экране prompt — скажи буквально «вставь значение из Bitwarden поля X, нажми Enter, должно показать Success».
3. **Не ленись на verify шаг** — после Success требуй `wrangler secret list` чтобы пользователь сам видел, что secret там.
4. **Если несколько secrets** — дай batch-шаблон, не последовательность отдельных диалогов.
5. **Один уровень paranoia** — упомяни safety rules один раз, не на каждом шаге.
6. **Финал — deploy** — напомни, что без `wrangler deploy` новые secrets Worker не подхватит.

## Anti-patterns в моём ответе

- ❌ Объяснять как работают Cloudflare Workers изнутри — не наш job, это mentor.
- ❌ Спрашивать «а ты сохранил secret?» — assume что да (иначе пользователь сам спросит, как сохранить).
- ❌ Давать общий теоретический совет «secrets важны» — пользователь это знает, давай конкретику.
- ❌ Бросать пользователя на полпути после `Enter a secret value:` — это критический момент, доводи до Success + verify.
