---
name: crypto-ffi-panic-check
description: Проверяет что panic-across-FFI контракт не сломан в `crypto-ffi/` — Rust `panic!()` конвертируется в Kotlin exception, а не в process abort. Grep'ает наличие `panics()` функции в `crypto-ffi/src/lib.rs` (exported через `#[uniffi::export]`) и теста `panic_isConvertedToKotlinException` в androidTest. Опционально прогоняет тест если эмулятор/устройство доступны. Использовать когда работаешь над PR, затрагивающим `crypto-ffi/**` (перед закрытием PR или при спорах о panic-обработке в FFI). Не использовать для обычной фича-работы вне crypto-ffi/ и для чисто Kotlin-only изменений. Обоснование: официальные UniFFI docs НЕ документируют panic contract — Element X и Matrix Rust SDK шипят panic smoke-tests именно потому что контракт может незаметно сломаться в UniFFI 0.29+ или при exit-ramp'е на manual JNI.
---

# Skill: crypto-ffi-panic-check

## Когда использовать

Триггеры:
- Работаешь над PR, который меняет что-то в `crypto-ffi/**` (Rust source, Gradle build, UniFFI-related файлы).
- Владелец задал вопрос «а если Rust паникует, что видит Kotlin?» или «уверены что не крашится процесс?».
- Собираешься закрыть PR по TASK-122 или future crypto-ffi feature-PR — перед `gh pr create` или merge.
- Обнаружил upgrade UniFFI версии (crate/bindgen/runtime) — panic-контракт мог измениться незаметно.

Не использовать:
- PR не трогает `crypto-ffi/**` вообще.
- Чисто Kotlin-only изменения (даже если в consuming module).
- Обычная domain-фича, не связанная с FFI boundary.

## Обоснование (зачем этот skill вообще существует)

Официальные UniFFI docs (Mozilla) НЕ документируют panic contract явно. Behavior «Rust panic → Kotlin exception» вычислен из чтения source кода UniFFI + issue #485 в трекере. Значит:

1. **Контракт может незаметно сломаться** при апгрейде UniFFI (0.28 → 0.29 → …) — тесты пропустят, panics станут process abort'ами.
2. **Exit ramp на manual JNI** (documented в docs/architecture/crypto.md) требует переписывания panic-обработки с нуля — smoke-test станет regression-guard'ом.
3. **Индустриальные лидеры** (Matrix Element X, Matrix Rust SDK) явно шипят panic smoke-tests именно из-за этого.
4. **Real incidents**: zcash#4652 (Rust panic крашил Android процесс до фикса), flutter_rust_bridge issue tracker имеет аналогичные баги.

Это точно fitness function по CLAUDE.md rule 7 — invariant, который дёшево проверить автоматически, дорого поймать в проде.

## Что делает skill (3 шага)

### Step 1 — Проверить наличие `panics()` функции

`grep` в `crypto-ffi/src/lib.rs`:

- Должна быть функция с сигнатурой примерно `pub fn panics(msg: String) -> String` (или `String` без ссылок).
- Должна быть помечена `#[uniffi::export]` (proc-macro-based) — если .udl используется вместо proc-macro, что-то пошло не так (см. spec Clarifications Q1).
- Тело должно вызывать `panic!(...)` безусловно (не под `if`, не под match branch, всегда).

Пример эталонной формы:

```rust
#[uniffi::export]
pub fn panics(msg: String) -> String {
    panic!("{}", msg)
}
```

Если функции нет / не exported / не паникует безусловно → **STEP FAILS**, см. Refusal patterns.

### Step 2 — Проверить наличие androidTest

`grep` в `crypto-ffi/src/androidTest/**/*.kt` (или `crypto-ffi/androidTest/**` — где project держит androidTests):

- Должен быть тест с именем содержащим `panic_isConvertedToKotlinException` (или очень близкий вариант).
- Должен вызывать `panics("<msg>")` (например `panics("test")`).
- Должен assert'ить что **exception** brought back в Kotlin (через `assertFailsWith<Exception>` / `assertThrows` / `try/catch` + `fail if no exception`).
- Должен провалиться если процесс упал бы (test infrastructure этого не поймает, но exception-assertion этого достаточна).

Пример эталонной формы:

```kotlin
@Test
fun panic_isConvertedToKotlinException() {
    assertFailsWith<Exception> {
        panics("test")
    }
}
```

Если теста нет / не asserts exception / не вызывает `panics` → **STEP FAILS**.

### Step 3 — Опциональный прогон

Если Android device / emulator подключён (`adb devices` показывает running arm64 device):

```
./gradlew :crypto-ffi:connectedAndroidTest --tests *panic*
```

Report:
- Зелёный → всё хорошо, panic-контракт держится.
- Красный → **CRITICAL** — панель не конвертируется в exception. Скорее всего сломался UniFFI upgrade. Show output владельцу, refuse to close PR.

Если device не подключён — skill не блокирует PR (Step 1 + Step 2 static checks достаточны для detection'а regression). Просто сообщи: «Panic test found in source, run pending на устройстве при следующей `Verification`-фазе».

## Refusal patterns

1. **`panics()` функция отсутствует** в `crypto-ffi/src/lib.rs` → refuse: «Panic smoke-test infrastructure not present. Cannot verify UniFFI panic contract per FR-011 spec/task-122-crypto-ffi-foundation. Add `#[uniffi::export] pub fn panics(msg: String) -> String { panic!(...) }` before proceeding». Пояснить владельцу: без этой функции невозможно детектировать сломанный контракт «Rust panic → Kotlin exception» — regression пойдёт в прод и приложение начнёт крашиться на любой Rust-ошибке.

2. **`panics()` есть но не `#[uniffi::export]`** — refuse: «Function `panics` не exported через UniFFI proc-macro. Kotlin не сможет вызвать её. Add `#[uniffi::export]` attribute». Ссылка на CLAUDE.md rule 7 (fitness function должна быть runnable).

3. **`panics()` есть но условная** (паник под `if`, match branch, feature flag) — refuse: «Panic must be unconditional. Смысл smoke-test'а — проверить что произвольный panic конвертируется. Условная panic может скипнуться на assertion path».

4. **androidTest отсутствует** — refuse: «`panic_isConvertedToKotlinException` test не найден в androidTest. Static exported function без runtime теста не даёт защиты. Add test that calls `panics(...)` and asserts Kotlin exception».

5. **androidTest есть, но не assert'ит exception** (просто вызывает `panics()` без try/catch/assertFailsWith) → refuse: «Test не отличает "exception thrown" от "process crashed". Оба случая приведут к test failure, но нам нужно knowing WHICH. Use `assertFailsWith<Exception> { panics(...) }`».

6. **Step 3 прогонялся и покраснел** — refuse to close PR / merge: «Panic contract BROKEN. Rust `panic!()` больше НЕ конвертируется в Kotlin exception. Это critical regression. Возможные причины: UniFFI upgrade сломал контракт, panic hook overridden где-то, или переход на manual JNI не docmented. Investigate before merge».

## Что skill НЕ делает

- Не переписывает Rust или Kotlin код автоматически.
- Не решает КАК починить сломанный контракт (это domain expert решение — понять что за upgrade / изменение сломало).
- Не проверяет другие FFI-контракты (memory safety, thread safety, etc.) — только panic.
- Не проверяет качество panic messages (что там читаемый текст) — только факт что exception виден.
- Не заменяет `pre-pr-backlog-sync` — они работают параллельно и на разных aspects.

## Exit ramp

Если UniFFI выпустит официальную документацию panic contract с гарантией стабильности API + добавит built-in `#[uniffi::assert_panic_converts]` или аналог runtime-assertion — этот skill можно упростить до линка «panic contract officially guaranteed since UniFFI vX.Y — этот skill deprecated». В backlog: sub-task «reassess crypto-ffi-panic-check skill after next UniFFI major bump».

Если проект уйдёт с UniFFI на manual JNI (exit ramp per docs/architecture/crypto.md) — skill остаётся релевантным, но проверяемая функция становится другой (native `Java_com_example_panics` JNI symbol вместо `#[uniffi::export]`).

## Связанные skills / документы

- **`rust-android-setup`** — one-off setup Rust toolchain на dev-машине. Этот skill гоняется после, когда PR-work уже идёт.
- **`android-emulator`** — для Step 3 (опциональный run) на эмуляторе.
- **`pre-pr-backlog-sync`** — гоняется параллельно перед `gh pr create`. Панель-check работает как один из fitness gate'ов, sync — как status-transition logic.
- **CLAUDE.md rule 7** — fitness functions where feasible. Этот skill — реализация rule 7 для конкретного FFI-инварианта.
- **spec.md FR-011** (specs/task-122-crypto-ffi-foundation/) — формальное требование.
- **UniFFI issue #485** (GitHub mozilla/uniffi-rs) — исходный research почему это надо smoke-тестировать.
