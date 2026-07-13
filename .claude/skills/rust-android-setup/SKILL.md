---
name: rust-android-setup
description: Автоматизирует установку Rust + cargo-ndk + UniFFI + Android Rust targets на Windows dev-машине для F-CRYPTO wave (TASK-122+). Idempotent — можно запускать многократно, доустанавливает только недостающее. Проверяет наличие Android NDK (не устанавливает — это через Android Studio). Использовать когда владелец говорит «настрой Rust», «поставь toolchain для крипто», «новая машина, нужен F-CRYPTO stack», или перед первым `./gradlew :crypto-ffi:build` на fresh machine. Требует запуска через PowerShell от обычного пользователя (не admin — rustup ставится в user home). Единственный шаг, который может попросить admin — install MSVC Build Tools через winget, если отсутствуют.
---

# Skill: rust-android-setup

## Когда использовать

Триггеры:
- Владелец говорит «настрой Rust на этой машине», «поставь toolchain для F-CRYPTO», «новая машина, нужен crypto-ffi build».
- Первый `./gradlew :crypto-ffi:build` падает с `error: linker cc not found` / `cargo not found` / `no such target: aarch64-linux-android`.
- Онбординг нового разработчика или owner на new Windows dev-машину.
- Перед началом любой TASK-122+ implementation session, если prior verify не проходил.

Не использовать:
- Установка Android SDK / NDK сама по себе — это делается через **Android Studio** (interactive UI, не автоматизируется через bash).
- Ubuntu/Mac машины — этот skill Windows-specific. Для других OS писать отдельный skill.
- Обновление уже установленного Rust — это `rustup update`, не setup.

## Требования

- **Windows 10/11**, PowerShell 5+ (встроенный).
- **Android Studio** установлен (для NDK — skill проверяет, не ставит).
- **Git** установлен (для rustup requirements).
- **Интернет** для скачивания rustup, cargo-ndk, target'ов (~1.5 GB total).
- **User home path без пробелов** (стандартный `C:\Users\<username>\` — ок).

Не требуется admin для rustup (устанавливается в user home). Admin может понадобиться **один раз** для MSVC Build Tools install если отсутствуют — skill спросит подтверждение.

## Что делает skill

Idempotent 6-шаговый pipeline. Каждый шаг проверяет «уже установлено?» и пропускает если да.

### Step 1 — Verify prerequisites

- `where.exe git` — есть?
- Android Studio installed? Проверка через `Get-ChildItem "$env:LOCALAPPDATA\Google\AndroidStudio*"` или `HKLM:\SOFTWARE\Android Studio`.
- MSVC Build Tools installed? `where.exe cl.exe` OR `Get-ChildItem "$env:ProgramFiles\Microsoft Visual Studio\*\*\VC\Tools\MSVC"`.
- Если Android Studio нет → **STOP, refuse**: «Установи Android Studio с https://developer.android.com/studio, потом перезапусти skill».
- Если MSVC Build Tools нет → **PROMPT**: «cargo требует MSVC linker. Установить через `winget install Microsoft.VisualStudio.2022.BuildTools --silent --override "--wait --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"`? [Y/n]». По подтверждению — запустить (может потребовать admin elevation).

### Step 2 — Detect / install Android NDK

- Проверить `$env:ANDROID_HOME` или `$env:ANDROID_SDK_ROOT` — есть?
- Внутри — `ndk\` папка с subfolder'ами (версии)?
- Если NDK нет → **STOP, refuse**: «Android NDK не найден. Открой Android Studio → Tools → SDK Manager → SDK Tools tab → отметь `NDK (Side by side)` → Apply. После установки перезапусти skill». Skill не автоматизирует Android Studio install потому что там interactive licence acceptance.
- Если NDK есть → выбрать latest version, экспортировать в `$env:ANDROID_NDK_HOME` (session + user env через `[Environment]::SetEnvironmentVariable`).

### Step 3 — Install rustup + Rust stable

- `where.exe rustup` — есть?
- Если нет:
  - Download `https://win.rustup.rs/x86_64` → `$env:TEMP\rustup-init.exe`.
  - Run: `& $env:TEMP\rustup-init.exe -y --default-toolchain stable --profile minimal`.
  - После install: `$env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"` (session + user env через registry).
- Если есть: `rustup update stable` для актуализации.

### Step 4 — Install Android targets

Для каждого из 4 ABI:

```powershell
foreach ($target in @(
    'aarch64-linux-android',
    'armv7-linux-androideabi',
    'i686-linux-android',
    'x86_64-linux-android'
)) {
    if (-not (rustup target list --installed | Select-String $target)) {
        rustup target add $target
    }
}
```

### Step 5 — Install cargo-ndk + uniffi-bindgen

```powershell
if (-not (Get-Command cargo-ndk -ErrorAction SilentlyContinue)) {
    cargo install cargo-ndk --locked
}
if (-not (Get-Command uniffi-bindgen -ErrorAction SilentlyContinue)) {
    cargo install uniffi-bindgen --locked
}
```

`--locked` = ставим точные версии из Cargo.lock (repeatable installs).

### Step 6 — Verify

```powershell
# Smoke test: rustc + cargo-ndk работают.
rustc --version    # expect: rustc 1.xx.x (stable)
cargo --version    # expect: cargo 1.xx.x
cargo ndk --version  # expect: cargo-ndk x.y.z
uniffi-bindgen --version  # expect: uniffi-bindgen x.y.z

# Проверить env vars persisted.
[Environment]::GetEnvironmentVariable('ANDROID_NDK_HOME', 'User')  # non-empty
[Environment]::GetEnvironmentVariable('Path', 'User') -like "*.cargo\bin*"  # true
```

Если всё зелёное → «✅ Rust Android toolchain ready. Try `./gradlew :crypto-ffi:build` when TASK-122 lands».

Если что-то красное → показать owner'у конкретную ошибку + suggested fix. **НЕ пытаться замять / скипнуть**.

## Скрипт-компаньон

Skill вызывает `scripts/setup-rust-android.ps1` — файл в репо, поддерживается вместе со skill'ом. Ownership: этот skill.

**Почему скрипт в репо, а не только в skill'е**:
- Новый разработчик может запустить `.\scripts\setup-rust-android.ps1` в PowerShell до того, как склонирует Claude Code.
- Onboarding docs (`docs/dev/rust-setup.md`) могут ссылаться на скрипт.
- CI может использовать те же шаги для reproducibility.

Skill = wrapper вокруг скрипта + логика решения «когда запустить + что делать если fail».

## Refusal patterns

1. **Не-Windows машина** (`$IsLinux` или `$IsMacOS` true) — refuse: «Этот skill Windows-only. Для macOS/Linux нужен отдельный setup — стандартные steps на https://rustup.rs + `cargo install cargo-ndk uniffi-bindgen`».
2. **Admin права запрошены** для чего-то кроме MSVC Build Tools — refuse: «rustup и cargo не требуют admin. Если что-то просит admin — вероятно поломанный PATH или permission на `$env:USERPROFILE\.cargo`. Проверь ручную установку».
3. **Owner отказался от MSVC install** — refuse без него: «cargo без MSVC linker не соберёт ничего под Windows. Skill не может продолжать без Build Tools».
4. **NDK не найден** — refuse и **не пытаться** автоматизировать Android Studio (interactive licence, GUI флаги).
5. **PATH конфликт** (уже есть `$env:USERPROFILE\.cargo\bin` в session PATH но rustup нет) — не переписывать, показать owner'у: «PATH уже содержит .cargo\bin, но rustup отсутствует. Возможно, старая ручная установка. Проверь `where.exe rustup` вручную».

## Что skill НЕ делает

- Не устанавливает Android Studio (interactive licence).
- Не устанавливает NDK автоматически (interactive licence в Android Studio).
- Не запускает `./gradlew :crypto-ffi:build` — это работа TASK-122 implementation, не setup.
- Не пишет никакого Kotlin/Rust кода — только настройка среды.
- Не обновляет CI (`.github/workflows/`) — это отдельный шаг в TASK-122 spec.

## Связанные skills / документы

- **`android-emulator`** — smoke testing на реальном устройстве / эмуляторе. Использует ту же машину, но не крипто-специфично.
- **`docs/dev/rust-setup.md`** (создаётся вместе с TASK-122) — owner-friendly onboarding guide, ссылается на этот skill и на скрипт.
- **TASK-122 spec.md** (когда будет создан) — task-специфичное описание того, что делает crypto-ffi module. Этот skill = среда, TASK-122 = код.

## Exit ramp

Если UniFFI как проект внезапно исчезнет / cargo-ndk сломается на следующем major release Android:

1. **Manual JNI fallback**: убираем uniffi-bindgen из skill, добавляем manual `.h` header generation через `cbindgen`. +2-3 недели работы, известная процедура.
2. **Kotlin/Native alternative**: KMP native bindings. Pre-alpha на 2026-07, не production. Отклонено в TASK-58 research.
3. **Pure Kotlin fallback**: реализовать крипто на Kotlin через ionspin/libsodium-kmp (что мы уже делаем частично). Приемлемо для базовых primitives, не работает для MLS/openmls.

Exit ramp зафиксирован здесь чтобы будущий maintainer не изобретал колесо при поломке toolchain.
