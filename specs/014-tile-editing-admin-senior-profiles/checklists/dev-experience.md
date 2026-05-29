# Dev Experience — spec 014

Generated: 2026-05-29 (speckit-clarify run).

Verifies developer can build/run/verify F-014 locally without prod accounts.

## Local-test path

- [x] **CHK001** §Local Test Path заполнена: 2 эмулятора (Pixel 7 + Pixel 4a) + Xiaomi для OEM coverage. Не placeholder.
- [x] **CHK002** Verification commands точные: `./gradlew :core:test --tests *TileEditTest`, `./gradlew :app:test --tests *EditModeTest`, `./gradlew :core:test --tests *RemoteEditTest`. Прямые gradle команды.
- [x] **CHK003** Verification command под 5 минут на ноуте — domain unit tests быстрые (no UI runtime), app tests Compose UI test с эмулятором займут больше, но individual test class — под 5 мин. ASSUMPTION OK для F-014.
- [x] **CHK004** Pure JVM unit testable пути есть: FR-001..FR-009 (domain verbs, profile selector, EditError variants) — все в `core/commonMain`, тестируются без эмулятора.
- [x] **CHK005** Эмулятор preset named: Pixel 7 + Pixel 4a. **Замечание**: spec не указывает exact preset ID из skill `android-emulator` (например `pixel_7_api_34`). Improvement: при `speckit-plan` уточнить preset name из android-emulator skill registry.

## Fake adapters

- [x] **CHK006** Все external ports имеют fakes (per §Local Test Path "Fake adapters used"):
  - `FakeConfigEditor` (спека 008) — для ConfigDocument ops.
  - `FakeProviderRegistry` (спека 005) — для picker tabs.
  - `FakeInstalledAppsCatalog` (спека 005) — для App tile type.
  - `FakeContactsRepository` (спека 011) — для Contact tile type.
- [x] **CHK007** Тесты не требуют real Firebase / real Cloudflare — `FakeConfigEditor` покрывает ConfigEditor port. F-014.1 server backup потребует Miniflare для Firestore Security Rules testing (per SC-004) — это local emulator, не production Firebase.
- [⚠️] **CHK008** DI wiring split дебаг/тест/release **не описан явно в спеке**. ASSUMPTION: уже settled per existing спеки 005/008/009 DI patterns. Improvement: plan.md должен explicit'но wire `BindsOptionalOf<ConfigEditor>` или равноценное.

## Fixtures

- [x] **CHK009** Fixtures listed:
  - `core/src/test/resources/fixtures/empty-workspace.json`
  - `core/src/test/resources/fixtures/simple-launcher-3-tiles.json`
  - `core/src/test/resources/fixtures/concurrent-edit-conflict.json`
- [x] **CHK010** Fixtures stable assumed (JSON, не Random). Cross-confirm в plan.md что нет `System.currentTimeMillis()` baked в.
- [⚠️] **CHK011** Cross-version fixtures: spec упоминает "schemaVersion bump 1→2 в F-014.1". **Не listed** fixture v1 для backward-compat test. Improvement: при F-014.1 plan нужен `simple-launcher-3-tiles.v1.json` для backward-compat read test.

## Cannot-test-locally gaps

- [x] **CHK012** §Cannot-test-locally gaps заполнена: 3 gaps listed (OEM keyboard, network latency real-world, animations smoothness OEM).
- [⚠️] **CHK013** TODO markers в коде/спеке: spec говорит `TODO(physical-device)` и `TODO(production-test)`. Это правильный format. Improvement: при tasks generation эти TODOs должны попасть в код как inline comments при первом touch'е affected files.
- [x] **CHK014** No silent "test in prod" — gaps explicit'но списаны.

## Build cycle

- [x] **CHK015** F-014 не вводит new libraries (per "Что НЕ строит этот спек" + SC-008 ≤300 KB APK delta). Clean-build time delta minimal — только domain ops + presentation extensions.
- [x] **CHK016** One-time setup steps: F-014.0 — никаких. F-014.1 потребует Firebase project + emulator setup (already documented per existing спека 007 setup). F-014.2 потребует encryption key setup (per future F-5 spec).
- [x] **CHK017** Credentials: F-014.0 no credentials. F-014.1 потребует Google Sign-In OAuth client ID (per F-4 setup, governed F-4 spec, not F-014).

## Crash + log diagnostics

- [⚠️] **CHK018** Logcat tagging не описан в спеке. ASSUMPTION: следует существующему pattern (per other specs). Improvement: plan.md должен define `private const val TAG = "F014.TileEdit"` или равноценный.
- [⚠️] **CHK019** Background coroutine failures: `pushPending` в ConfigEditor — background work. Если падает silently без логирования → admin не узнаёт. **Existing behavior** спеки 008 governs это. Verify в plan.md что `pushPending` failures emit error to UI surface.
- [x] **CHK020** Runtime feature flags: F-014 не вводит feature flags. Phasing (F-014.0/.1/.2) — gradle build flavor split или release branches, не runtime flags.

## Cross-developer reproducibility

- [x] **CHK021** No developer-machine paths embedded. Spec ссылается на gradle commands and fixture paths (relative to repo root). OK.
- [x] **CHK022** Onboarding: new developer следует existing `docs/dev/dev-environment.md` + читает spec 014 §Local Test Path. Под 1 page.

## Open items

1. **CHK005**: emulator preset name не uniqued. Plan.md должен specify preset ID из android-emulator skill.
2. **CHK008**: DI wiring split не explicit в спеке. Plan.md должен describe.
3. **CHK011**: cross-version fixture v1 не listed. При F-014.1 нужен.
4. **CHK013**: TODO markers формат явный, но enforcement на tasks.md generation level.
5. **CHK018-CHK019**: log tagging + background failure surfacing — design decisions для plan.md.

**Verdict**: PASS с 5 minor improvements для plan.md. Спека достаточно testable, локальный test path explicit, gaps честные.
