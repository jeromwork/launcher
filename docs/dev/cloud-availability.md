# Cloud-функции и флаг `cloudAvailable` — кратко

Это документ для **владельца проекта и нового разработчика** (Senior, Junior).
Простой русский, без жаргона. Цель — за 10 минут понять, почему приложение
работает без интернета, и что именно меняется после входа в Google.

TASK-49 ввёл одно общее «правило игры» для всех будущих cloud-фич.

---

## Главная мысль одной строкой

**`cloudAvailable=true` ⇔ пользователь вошёл в Google.** Всё. Других условий нет.

---

## Зачем это нужно

Раньше каждая cloud-фича (sync конфига, push-уведомления, контакты) могла
самостоятельно решать, «работает ли облако сейчас». Это давало бабушке плохой
опыт:

- Без интернета приложение падало в Sign-In прямо на первом запуске.
- FCM token регистрировался в Firestore до того, как бабушка решила вообще
  пользоваться облаком.
- Кнопка SOS могла «зависнуть», если облако недоступно.

TASK-49 сводит всю историю к **одному булеву флагу** и трём правилам.

---

## Что такое `cloudAvailable`

Один булев флаг, хранящийся в DataStore (
`cloud_settings.preferences_pb`, ключ
`cloud.availability.is_available`).

- `false` — пользователь не вошёл. Это значение по умолчанию. Любая
  cloud-фича сама показывает экран `SignInExplanationScreen` или просто
  отступает в локальный fallback.
- `true` — пользователь вошёл через Google. Cloud-фичи активируются.

Флаг переживает kill процесса и reboot — это DataStore.

---

## Как меняется

Только через `AuthProvider` events. **Никаких других источников.**

```
AuthProvider.currentUser → CloudAvailabilityImpl.collect → DataStore.edit
```

- Sign-In успешен → AuthProvider эмитит `AuthIdentity` → флаг становится `true`.
- Sign-Out → AuthProvider эмитит `null` → флаг становится `false`.
- Token expiry, refresh failure — `AuthProvider` сам разруливает, мы
  только смотрим в его flow.

Никакой код **не имеет права** напрямую писать в этот DataStore. Если нужно
изменить состояние — звоните `AuthProvider.signIn()` / `signOut()`.

---

## Что считается «первым cloud-action»

Любое действие пользователя, которое **требует** облака:

- тап «Подключить родственника» в Settings,
- тап «Включить cloud-резерв конфига»,
- любая будущая фича вида «получать что-то с другого устройства».

Если `cloudAvailable=false` в момент этого действия — caller обязан
показать `SignInExplanationScreen` и дать пользователю выбор: войти или
отказаться. **Не показывать Sign-In автоматически.**

---

## Как cloud-фича читает флаг

Два способа:

```kotlin
class MyCloudFeature(private val cloudAvailability: CloudAvailability) {

    suspend fun onUserTriggered() {
        if (!cloudAvailability.isCloudAvailable()) {
            // покажи SignInExplanationScreen
            return
        }
        doCloudWork()
    }

    // Реактивный путь — для UI, которые включают/выключают свой
    // блок в зависимости от состояния.
    val visible: Flow<Boolean> = cloudAvailability.isCloudAvailableFlow
}
```

`isCloudAvailable()` — синхронный snapshot. `isCloudAvailableFlow` — Flow,
который эмитит `false → true → false` при изменениях; `distinctUntilChanged`,
дубли не приходят.

---

## Convention для cloud-фич

1. **Подписаться на `isCloudAvailableFlow`**, если фича — реактивная (UI,
   indicator, periodic worker). Снимать подписку при teardown.
2. **Не дёргать `isCloudAvailable()` в hot path** — DataStore делает диск-IO.
   Кэшировать `StateFlow` или один раз снять `first()`.
3. **При `false`** — отступать в локальный fallback (см. `LocalAlternative`
   port) или показывать `SignInExplanationScreen`. **Никогда не показывать
   ошибку «нет интернета»** — это путает бабушку.
4. **При transition `false → true`** — если фича копит «отложенную работу»
   (например, FCM token), сразу её обработать.
5. **Никаких прямых обращений к Firebase / Google Sign-In SDK** из фичи.
   Только через port `CloudAvailability` + `AuthProvider`.

---

## Что TASK-49 НЕ делает

Чтобы избежать недопонимания:

- **Не проверяет network connectivity.** `cloudAvailable=true` ≠ интернет
  есть. Если облако недоступно из-за сети — это retry/timeout caller'а.
- **Не проверяет GMS availability.** На устройствах без Google Play Services
  (Huawei) `AuthProvider` сам вернёт `null`, и флаг останется `false`
  навсегда. Это правильное поведение.
- **Не проверяет token expiry.** `AuthProvider` сам отдаёт `null`, когда
  refresh не получился.
- **Не управляет правом на отдельные cloud-фичи.** Это subscription /
  entitlement, отдельный port (S-10, будущее).
- **Не отменяет уже отправленные данные.** Если FCM token зарегистрирован в
  Firestore до Sign-Out — он там остаётся. Удаление — отдельная фича.

---

## SOS и другие «должно работать оффлайн» фичи

Port `LocalAlternative` — opt-in. Фича может реализовать его и работать
**вообще без облака**.

Пример: `SOSDialerAlternative` открывает Android dialer с `tel:112` (номер
зависит от locale: 102 для РФ/Беларуси, 911 для США/Канады, 112 для ЕС
и т.д. — см. `EmergencyNumberResolver`). Использует `Intent.ACTION_DIAL`,
runtime-permission не просит, intent безопасен даже в Airplane mode.

Не каждая фича может быть локальной. Но критические — обязаны.

---

## Будущее: переезд на свой сервер (server-roadmap)

Сейчас `AuthProvider` за кулисами использует Google Sign-In + Firebase Auth.
Это **временное решение** на бесплатном tier'е Firebase. См.
`docs/dev/server-roadmap.md` §SRV-AUTH-001.

Когда поедем на свой backend:

- Добавляется `OwnServerAuthProvider` adapter в TASK-3 territory.
- Реализует тот же port `AuthProvider` с тем же `currentUser: Flow<AuthIdentity?>`.
- **TASK-49 не меняется ни строкой.** `CloudAvailabilityImpl` подписан на
  port, не на Firebase.

Это и есть «эта architecture — текущий snapshot. Через 6-12 месяцев может
смениться без переписывания core».

<!-- TODO(server-roadmap SRV-AUTH-001): при переезде на own server добавить OwnServerAuthProvider adapter в TASK-3; CloudAvailability/CloudAvailabilityImpl не трогать. -->

---

## Файлы, в которые смотреть

- `core/cloud/src/commonMain/kotlin/com/launcher/cloud/api/CloudAvailability.kt` — port.
- `core/cloud/src/androidMain/kotlin/com/launcher/cloud/impl/CloudAvailabilityImpl.kt` — DataStore observer.
- `core/cloud/src/commonMain/kotlin/com/launcher/cloud/api/LocalAlternative.kt` — opt-in fallback port.
- `core/cloud/src/androidMain/kotlin/com/launcher/cloud/impl/SOSDialerAlternative.kt` — SOS реализация.
- `app/src/main/java/com/launcher/app/ui/onboarding/SignInExplanationScreen.kt` — единый экран объяснения.
- `app/src/main/java/com/launcher/app/auth/FcmTokenRegistrationGuard.kt` — гейтит FCM регистрацию.
- `app/src/main/java/com/launcher/app/di/CloudModule.kt` — Koin DI.

---

## TL;DR (для не-разработчика)

Одна правда о том, доступно ли облако: **флаг `cloudAvailable`**. Он
становится `true` только когда пользователь вошёл в Google, и `false`
когда вышел. Всё остальное в приложении (FCM-уведомления, сохранение
настроек в облако, контакты от внуков, удалённое управление) **смотрит
на этот флаг**.

Зачем так:

- Бабушка не должна видеть Sign-In на первом запуске. До первого
  cloud-действия — `cloudAvailable=false`, всё работает локально.
- Кнопка SOS, звонки, главный экран, темы — **не зависят** от облака.
- Когда бабушка сама нажмёт «подключить родственника» — покажется один
  единый экран `SignInExplanationScreen` с объяснением «зачем войти».
  Не раньше.

Кто меняет флаг: **только `AuthProvider`** (event «вошёл» / «вышел»).
Никаких прямых записей в DataStore из других мест.

Что под капотом сейчас: Google Sign-In + Firebase Auth. Это **временно**,
маршрут переезда на собственный сервер описан в
`docs/dev/server-roadmap.md` §SRV-AUTH-001. Когда переедем — внутри
заменяется один `AuthProvider` adapter; `CloudAvailability` не трогается.

Что TASK-49 **не делает**: не проверяет интернет, не проверяет наличие
GMS, не отменяет уже отправленное в Firestore, не отвечает за подписку
на платные фичи (это будет S-10 в будущем).

