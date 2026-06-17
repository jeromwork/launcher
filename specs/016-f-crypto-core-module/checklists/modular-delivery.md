# Checklist: modular-delivery — spec 016 F-CRYPTO

Run date: 2026-06-17.

## Scope of the feature

- [x] CHK001 — Spec явно declares: **form-factor-agnostic infrastructure** (KMP module работает на handheld + Android TV + Google TV + EOS + iOS). Targets `androidMain`, `jvmMain`, `iosX64`/`iosArm64`/`iosSimulatorArm64` declared (FR-002).
- [N/A] CHK002 — Form-factor-specific module не нужен. F-CRYPTO — agnostic.
- [x] CHK003 — No vendor SDK / platform API / form-factor UI assumption в shared code. FR-028 enforces. AeadCipher port не знает про D-pad / voice / head unit.

## Module placement

- [x] CHK004 — libsodium SDK живёт **только** в `core/crypto/libsodium/` adapter — no leakage в Core / app / другие feature modules.
- [x] CHK005 — Article V §7 answered: «Зачем не package?»
  1. Ownership boundary — отдельный maintainer (future extract).
  2. API boundary — domain ports separately versionable.
  3. Independent enable/disable — Detekt-проверка module dependencies (SC-006).
  4. Stable API — semver semantics (Clarifications Q8).
  5. Material testability — KAT/Wycheproof/property tests изолированы.
- [x] CHK006 — Regret condition explicit: «extract в отдельный репо при появлении 2-го реального потребителя» (FR-004 inline TODO). Trigger конкретный: мессенджер ИЛИ фото-приложение ИЛИ EOS/Android TV ИЛИ iOS launcher как реальный спекаподобный второй потребитель.

## Profile / preset declaration

- [N/A] CHK007..009 — F-CRYPTO не profile/preset; используется любым profile'ом который хочет крипту. Базовое приложение продолжает работать без F-CRYPTO модуля **только** если crypto-touching фичи (F-5, спека 011) отключены — это implicit dependency, **записать в plan-фазу**.

## Form-factor expansion

- [x] CHK010 — F-CRYPTO **готовит** form-factor expansion (iOS targets day 1 = readiness для iOS launcher / iOS messenger). Это и есть expansion-readiness.
- [x] CHK011 — Form-factor-specific SDKs нет.
- [N/A] CHK012 — F-CRYPTO не первая non-handheld form-factor спека (она infrastructure).

## One-way doors

- [x] CHK013 — F-CRYPTO new dependencies:
  - **libsodium (ionspin)** — two-way door (FR-014 fallback на BouncyCastle).
  - **Android Keystore** — стандартный Android API, не вендор.
  - **iOS Keychain** (future) — стандартный iOS API.
  Никаких vendor-specific dependencies без exit ramp.
- [x] CHK014 — «Vendor disappears» test: libsodium → BouncyCastle = 1 adapter module (`core/crypto/libsodium/`). ≈1-2 дня (см. domain-isolation CHK003).
- [x] CHK015 — F-CRYPTO **не использует** free workaround вместо server component. F-CRYPTO offline. Future server pieces (recovery, audit) — в server-roadmap (SRV-CRYPTO-003..007).

## Anti-bloat

- [x] CHK016 — `core/crypto/` — Gradle subproject с **7 ports + adapters + tests** — не single-class module.
- [x] CHK017 — Не pre-emptively split. iOS targets — **обещали в roadmap** (5 потребителей в перспективе), не «на всякий случай».
- [x] CHK018 — Future split анализирован как regret condition (CHK006), не сделан сейчас.

## Open issues

| # | Issue | Severity |
|---|---|---|
| O-1 | Implicit dependency «без F-CRYPTO не работают F-5/011» — plan-phase должен явно записать в `base application MUST require core:crypto module` (Article VII §6). | Minor |

## Result

**13/13 actionable PASS, 5 N/A, 1 minor open**.

**Verdict**: PASS. F-CRYPTO правильно form-factor-agnostic, экстракшн-ready (regret condition + inline TODO), без vendor lock-in.

---

## TL;DR простым языком

F-CRYPTO — infrastructure-модуль, работающий **на всех платформах**: handheld Android, Android TV, Google TV, EOS, iOS. Не привязан к одному «виду» устройства. Когда появится 2-й реальный потребитель (мессенджер, фото-приложение или iOS-лаунчер) — модуль будет вынесен в отдельный репозиторий за день работы (специальный TODO записан в `build.gradle.kts`). Если libsodium-библиотека завтра умрёт — заменим на BouncyCastle за 1-2 дня. Никаких «жёстких привязок».
