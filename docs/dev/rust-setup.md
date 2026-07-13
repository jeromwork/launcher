# Rust + Android toolchain — установка для F-CRYPTO

## Что это

В проекте launcher появился Rust-модуль `crypto-ffi/` — фундамент под будущую
криптографию (openmls, libsodium, шифрованное хранилище). Чтобы его собирать,
на dev-машине должны стоять:

- **Rust 1.97.0** — компилятор Rust (версия зафиксирована в
  `crypto-ffi/rust-toolchain.toml`, rustup сам поставит правильную при
  первой сборке).
- **Android NDK** — набор системных инструментов от Google, позволяет
  собирать нативный код (Rust, C++) под Android.
- **`aarch64-linux-android` target** — Rust умеет собирать под разные
  архитектуры; нам нужен arm64 (Android-телефоны).
- **cargo-ndk 4.1.2** — плагин, который склеивает Rust и Android NDK,
  чтобы собрать `.so`-библиотеку.
- **MSVC Build Tools** (только Windows) — линковщик Microsoft, Rust его
  использует под капотом.

Всё это ставится **один раз** при первом входе в F-CRYPTO работу.

## Windows setup (автомат)

1. Установить **Android Studio** с NDK:
   - Меню `Tools → SDK Manager → SDK Tools`.
   - Отметить `NDK (Side by side)` → Apply.
   - Дождаться установки (может занять 10–15 минут).

2. Убедиться что установлен **Git** (обычно уже есть, если работаете в
   репозитории).

3. Запустить автоматический скрипт из корня репо (PowerShell,
   **не** admin):
   ```powershell
   .\scripts\setup-rust-android.ps1
   ```
   Скрипт поставит: rustup + Rust 1.97, cargo-ndk 4.1.2, target
   `aarch64-linux-android`. Если MSVC Build Tools не найдены — предложит
   установить через `winget` (потребует admin в момент установки MSVC).

4. Verify. В **новом** окне PowerShell (чтобы PATH подхватился):
   ```powershell
   rustc --version         # rustc 1.97.0
   cargo ndk --version     # cargo-ndk 4.1.2
   ```

Подробности автоматизации: skill
[`rust-android-setup`](../../.claude/skills/rust-android-setup/SKILL.md).

## macOS / Linux setup (руками)

Best-effort — на этих ОС проект основной разработчик не запускает, но шаги
стандартные.

1. **rustup + Rust:**
   ```bash
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
   source "$HOME/.cargo/env"
   rustup default 1.97.0
   ```
   (`rust-toolchain.toml` перекроет default'ом внутри `crypto-ffi/` — это
   ожидаемо.)

2. **Android target:**
   ```bash
   rustup target add aarch64-linux-android
   ```

3. **cargo-ndk:**
   ```bash
   cargo install cargo-ndk --version 4.1.2 --locked
   ```

4. **Android NDK:**
   - macOS: `brew install --cask android-ndk`, либо через Android Studio
     SDK Manager (как на Windows).
   - Linux: скачать через Android Studio SDK Manager (`Tools → SDK
     Manager → SDK Tools → NDK`).

5. **Env var** — прописать в `~/.zshrc` или `~/.bashrc`:
   ```bash
   export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/26.1.10909125"
   ```
   (путь скорректировать под установленную версию).

## Verify: первая сборка

Из корня репозитория:

```
./gradlew :crypto-ffi:build
```

Ожидаемое время: **60–90 секунд** clean-build. Успех = `.so`-файл появился
в `app/src/main/jniLibs/arm64-v8a/libcrypto_ffi.so`, а Kotlin-биндинги
сгенерированы в `crypto-ffi/build/generated/uniffi/kotlin/`.

## Troubleshooting

**«linker `link.exe` not found» (Windows):**
MSVC Build Tools не установились. Запустите вручную:
```powershell
winget install Microsoft.VisualStudio.2022.BuildTools --override "--wait --add Microsoft.VisualStudio.Workload.VCTools"
```

**«error: could not find `rustup` toolchain 'aarch64-linux-android'» или
«no such target»:**
Пропущен шаг `rustup target add aarch64-linux-android`. Повторите его.

**«ANDROID_NDK_HOME is not set» / «NDK not found»:**
Переменная окружения не выставлена в текущей shell-сессии. На Windows
переоткройте PowerShell после установки NDK. На macOS/Linux проверьте
`echo $ANDROID_NDK_HOME` — путь должен указывать на существующую папку.

**«uniffi-bindgen: no such command»:**
Начиная с UniFFI 0.28+, `uniffi-bindgen` больше **не** ставится как
глобальный CLI. Он вызывается через `cargo run --bin uniffi-bindgen` из
`crypto-ffi/` — Gradle-таски это уже делают правильно. Если сталкиваетесь
руками, используйте эту форму.

**Windows: пути слишком длинные при сборке:**
Включить long-paths поддержку (один раз, admin):
```powershell
git config --system core.longpaths true
```

## Как обновить версию Rust

**Не делайте это внутри обычной задачи.** Bump версии Rust — отдельная
backlog-задача (см. Q3 clarification в
`specs/task-122-crypto-ffi-foundation/spec.md`), потому что затрагивает
всех разработчиков и CI.

Пошаговая процедура bump'а UniFFI (не Rust) описана в
[`crypto-ffi/README.md § How to safely update UniFFI version`](../../crypto-ffi/README.md#how-to-safely-update-uniffi-version).

## Related

- [`.claude/skills/rust-android-setup/SKILL.md`](../../.claude/skills/rust-android-setup/SKILL.md)
  — Windows automation внутри Claude Code.
- [`crypto-ffi/README.md`](../../crypto-ffi/README.md) — что делать, когда
  setup готов (как добавить Rust-функцию, как собирать, как запускать
  тесты).
- [`specs/task-122-crypto-ffi-foundation/`](../../specs/task-122-crypto-ffi-foundation/)
  — спека, план, задачи для F-CRYPTO foundation.
