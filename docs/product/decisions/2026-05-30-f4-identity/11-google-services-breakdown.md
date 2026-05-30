# 11. «Google services» — это не один монолит

**Дата фиксации**: 2026-05-30
**Назначение**: разъяснение «от чего именно мы хотим съехать», потому что Google services — это **семь разных вещей**, и каждое имеет свои implications для exit ramp.

---

## Семь разных Google сервисов

Самая частая ошибка — думать что «Google» это одно. На самом деле:

| # | Сервис | Что делает | Заменяем? | Цена замены | Наш план |
|---|---|---|---|---|---|
| 1 | **Google Sign-In** (OAuth) | Идентифицирует user'а через Google-аккаунт | ✅ да | средняя | **Остаётся forever** как один из identity providers |
| 2 | **Firebase Auth** | Хранит identity + token management | ✅ да | средняя | Заменяется own JWT issuer в Phase 1 cutover |
| 3 | **Firestore** | NoSQL database в облаке | ✅ да | большая | Заменяется own DB в Phase 2 |
| 4 | **FCM** (Firebase Cloud Messaging) | Push notifications для Android | ⚠️ да, но Android-specific | очень большая | **Остаётся forever** (transport); триггеры → own-server |
| 5 | **Google Play Services** (GMS) | System layer для всех Google API на Android | ❌ нет, это OS-уровень | невозможно | Зависит от GMS-устройств; non-GMS — отдельно |
| 6 | **Google Play Store** | Distribution | ✅ да (sideload / F-Droid) | средняя | Остаётся в MVP; sideload — опция в будущем |
| 7 | **Crashlytics / Analytics** | Telemetry | — | — | **НЕ используем** (Android Vitals достаточно, per roadmap) |

---

## Что значит «non-GMS device»

**GMS = Google Mobile Services** — системный пакет на Android, через который любое приложение пользуется Google API.

- На устройстве **либо есть GMS, либо нет**.
- Без GMS:
  - Google Sign-In SDK не работает.
  - Firebase Auth не работает (внутри пользуется GMS).
  - FCM не доставляет push (transport через GMS).
  - Google Maps SDK не работает.
  - Play Store отсутствует.

### Кто без GMS

- **Huawei** новых моделей (с 2019 после US ban) — своя HMS (Huawei Mobile Services) + AppGallery.
- **Xiaomi / Honor** в Китае — для китайского рынка (глобальные модели с GMS).
- **Amazon Fire tablets / Fire TV** — свой FireOS, Amazon Appstore.
- **Кастомные ROM** (LineageOS, GrapheneOS) — пользователь сам ставит GMS или нет.
- **Многие Android TV**: Huawei TVs, Xiaomi TVs (region-specific), небрендовые китайские TV boxes — часто без GMS.

### Решение

- **MVP**: только GMS-устройства (out of scope non-GMS).
- **Post-MVP, после own-server**: возможный target. Без Firebase Auth можно использовать любой identity provider — Email / Phone / HMS Account Kit (Huawei). Push на non-GMS — HMS Push Kit или собственный socket (плохой UX).

См. также [04-google-as-one-of-many.md](04-google-as-one-of-many.md) и [07-tv-and-other-form-factors.md](07-tv-and-other-form-factors.md).

---

## Где в нашем стеке какие зависимости живут

```
app/androidMain/
  ├─ auth/GoogleSignInAuthAdapter.kt   ← #1 (Google Sign-In SDK)
  │                                       └─ требует #5 (GMS)
  ├─ auth/FirebaseAuthBridge.kt        ← #2 (Firebase Auth SDK)
  ├─ data/FirestoreConfigStore.kt      ← #3 (Firestore SDK)
  └─ push/FcmTokenManager.kt           ← #4 (FCM SDK)

push-worker/                            ← Cloudflare Worker
  ├─ trigger.js                        ← вызывает FCM HTTP API (#4)
  └─ auth-verify.js                    ← верифицирует Firebase tokens (#2)

core/commonMain/                        ← НИКАКИХ Google-зависимостей (rule 1)
  ├─ auth/AuthProvider.kt              ← port
  ├─ data/ConfigStore.kt               ← port
  └─ push/PushPort.kt                  ← port
```

**Принцип**: ВСЕ Google-зависимости живут в `app/androidMain/`. Domain (`core/commonMain/`) не знает о них. Это и есть основа exit ramp — заменяя адаптер в `androidMain/`, мы не трогаем `commonMain/`.

---

## FCM — особая боль

Полный exit от FCM = поддержка ~4 разных push-механизмов + fallback logic. **Реальный pattern индустрии**: даже компании с own-server **всё равно** используют FCM + APNs как **transport**, а own server только **триггерит** их (как наш Cloudflare Worker сейчас).

**Наш план**: FCM + APNs остаются forever. Триггеры мигрируют на own-server.

Подробнее: [05-own-server-migration-strategy.md](05-own-server-migration-strategy.md).

---

## Crashlytics / Analytics — НЕ используем

Per roadmap: «Crashlytics / Sentry SDK не нужны, Android Vitals достаточно».

Поэтому в exit ramp **этот блок не появляется** — нечего мигрировать.

---

## Билинг как смежный вопрос

**Firebase Spark plan** — бесплатный tier:
- 50K Firestore reads/day, 20K writes/day, 20K deletes/day.
- 1 GB Firestore storage.
- Limited Firebase Auth users.

**Если MVP взлетит** — есть риск upgrade на Blaze (pay-as-you-go) **до** own-server cutover'а. Это **аргумент не затягивать** Phase 1 cutover.

Аналогично: **Cloudflare Worker free tier** — 100K requests/day. Если scale выше — нужен upgrade либо переезд на own-server.

Не блокирует F-4, но adjacent concern для timing миграции.

---

## Связанные документы

- [04-google-as-one-of-many.md](04-google-as-one-of-many.md) — Sign in with Google как identity verifier остаётся forever.
- [05-own-server-migration-strategy.md](05-own-server-migration-strategy.md) — phased migration таблица.
- [07-tv-and-other-form-factors.md](07-tv-and-other-form-factors.md) — non-GMS TVs как long-term target.
