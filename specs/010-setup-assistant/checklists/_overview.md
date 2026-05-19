# Checklists Overview: Setup Assistant and Launcher Bootstrap

**Created**: 2026-05-19 (post `/speckit.clarify` + 13 triggered checklists)
**Spec**: [spec.md](../spec.md)

## Coverage

13 checklists run per [procedure-assess-spec-complexity](../../../.claude/skills/procedure-assess-spec-complexity/SKILL.md): 2 always-on + 11 triggered.

| Checklist | Result | Critical findings |
|-----------|--------|---------------------|
| [requirements-quality](requirements.md) | 15/16 ✓ | SC-006 weak FR traceability (non-blocker) |
| [meta-minimization](meta-minimization.md) | 10/13 ✓ | `Surface.MainScreen` enum future-only; Registry pattern borderline; `flows_mock` removal audit |
| [domain-isolation](domain-isolation.md) | 11/16 ✓ | **GMS port missing** (CRITICAL plan-level) |
| [wire-format](wire-format.md) | 18/18 ✓ | None — FR-040 closes область by-construction |
| [state-management](state-management.md) | 13/17 ✓ | Challenge gate state scope не decided; Font-scale × small-font edge |
| [failure-recovery](failure-recovery.md) | 13/17 ✓ | Paired unlink network fail not specified; Setup check exception not specified |
| [performance](performance.md) | 16/20 ✓ | SC-002 reconciliation с ADR-005 baseline (plan-level) |
| [security](security.md) | 21/24 ✓ | Logging PII policy enforcement; non-exported confirmation |
| [permissions-platform](permissions-platform.md) | 18/22 ✓ | **`<queries>` для `tel:` missing** (CRITICAL Android 11+); RoleManager API check |
| [ux-quality](ux-quality.md) | 17/22 ✓ | Avatar placeholder; rationale screens enumeration |
| [accessibility](accessibility.md) | 16/25 ✓ | Yellow badge contrast; Call confirmation TalkBack focus order; D-pad equivalent for 7-tap |
| [elderly-friendly](elderly-friendly.md) | 19/22 ✓ | **Wizard 4 steps needs progress indicator** (требует FR-008a); Call confirmation visual hierarchy |
| [localization](localization.md) | 16/20 ✓ | **Plurals для Russian badge a11y CRITICAL** (one/few/many/other) |

## Critical findings (must address)

Эти три **must be addressed** before `/speckit.plan` finalize Phase 0:

### 1. `<queries>` для `tel:` scheme (permissions-platform CHK008/CHK020)

Без `<queries>` declaration в `AndroidManifest.xml`, `Intent(ACTION_CALL, "tel:...")` и `Intent(ACTION_DIAL, "tel:...")` не работают на Android 11+ target SDK. Это **breaks FR-012/FR-014 функционально**.

**Fix**: добавить в `AndroidManifest.xml`:
```xml
<queries>
  <intent>
    <action android:name="android.intent.action.DIAL" />
    <data android:scheme="tel" />
  </intent>
  <intent>
    <action android:name="android.intent.action.CALL" />
    <data android:scheme="tel" />
  </intent>
</queries>
```

### 2. GMS availability port (domain-isolation CHK001/CHK002/CHK003/CHK015)

FR-042 напрямую ссылается на `GoogleApiAvailability.isGooglePlayServicesAvailable()` — vendor type в spec text. Должен быть behind port в `core/commonMain/api/setup/GmsAvailabilityPort.kt`.

**Fix**: plan.md introduces port + adapter + DI wiring; spec.md FR-042 rephrased через domain abstraction.

### 3. Plurals для Russian badge a11y (localization CHK005/CHK020)

TalkBack contentDescription «Критичных проблем N» / «Рекомендованных проблем M» **must use plurals resource** с 4 Russian forms (one/few/many/other). Иначе grammatically broken для primary persona (бабушка с TalkBack).

**Fix**: plan tasks.md enumerates `plurals` resources before challenge gate UI implementation.

## Recommended spec.md additions (before plan)

Из checklist findings, два FR additions улучшат spec quality:

### FR-008a — Wizard progress indicator (elderly-friendly CHK007)

«First-launch wizard MUST содержать visual progress indicator (текст «Шаг N из M» + visual dots / bar). На Android 13+ M = 4 (language, preset, ROLE_HOME, POST_NOTIFICATIONS), на Android < 13 M = 3 (POST_NOTIFICATIONS skipped). Progress updates after each step completed/skipped.»

### FR-017a — SetupCheck exception handling (failure-recovery CHK001/CHK002)

«Если `SetupCheck.check()` throws, System MUST интерпретировать как `NotConfigured(reason = exception.message)` и логировать diagnostic event `setupCheckException(id)`. Не должно crash'ить Settings UI.»

### FR-032a — Paired unlink network failure (failure-recovery CHK001/CHK002/CHK003)

«Если `LinkRegistry.deactivate(linkId)` fails из-за network unavailable, System MUST показать non-blocking toast «Не удалось отвязать — попробуйте позже», unlink action queued для retry при следующем online cycle. Бабушка видит устройство всё ещё в списке (state not lost).»

### Assumption A-13 — Challenge text small-font intentional (elderly-friendly CHK001)

«Challenge text ≤ 14sp намеренно ниже senior-safe baseline ≥ 18sp. Это **по дизайну** — soft barrier rely on visual difficulty for elderly. Article VIII §7 documented exception clause invoked.»

## Plan-level enumeration required

Findings которые plan.md должен enumerate (но не require spec edits):

- **state-management CHK001/005/009**: challenge gate state scope decision (ViewModel-scope recommended)
- **accessibility CHK005**: yellow badge WCAG-compliant shade specification (e.g. `#D97706`)
- **accessibility CHK011**: call confirmation TalkBack focus order (CANCEL first)
- **accessibility CHK016**: 7-tap gesture D-pad equivalent OR explicit OUT documentation
- **performance CHK017/018**: macrobenchmark + APK delta CI gates
- **security CHK004**: explicit «no PII в logs» policy for diagnostic events
- **security CHK009**: `android:exported="false"` audit для new Activities
- **ux-quality CHK002**: avatar placeholder design (initials когда нет фото)
- **elderly-friendly CHK006**: call confirmation CANCEL visual hierarchy spec
- **elderly-friendly CHK022**: Phase 14 senior-safe walkthrough на 5 elder users
- **localization CHK007**: locale-aware date formatting для paired devices «дата привязки»
- **localization CHK008**: phone number formatting (libphonenumber-equivalent)
- **localization CHK015**: layout expansion testing at fontScale 200% + Russian

## Aggregate verdict

**Spec 010 готов к `/speckit.plan`** with the following caveats:
- **3 critical plan-Phase-0 items** (см. above) — must be addressed in first plan iteration.
- **3 recommended spec.md additions** (FR-008a, FR-017a, FR-032a, A-13) — small, surgical, can be done before plan starts.
- **13 plan-level enumerations** — normal `/speckit.plan` work.

**Strength of спека**: clarify session 2026-05-19 resolved fundamental ambiguities (PIN→challenge, Settings preset visibility, GMS hard-block, tutorial removal) что dramatically simplified spec. Result — relatively clean checklist passes across all 13 dimensions.

---

## Краткое содержание (для не-разработчика)

Прошли 13 проверочных списков по спеку 010. Каждый список проверяет один из аспектов: качество требований, безопасность, доступность для пожилых, локализация, и т.д.

**Главный итог**: спек **готов идти дальше** (`/speckit.plan` — построение плана реализации), но есть **3 критичных пункта** которые **обязательно** надо учесть на первом этапе плана:

1. **Звонок через 7+ Android может не работать** без специальной строчки в манифесте (`<queries>` для `tel:`). Без неё — наш FR-012/FR-014 функционально мёртвый.

2. **Проверка наличия Google Play Services** сейчас в спеке напрямую ссылается на API Google (`GoogleApiAvailability`). По нашим правилам (CLAUDE.md rule 1) domain не должен видеть vendor API — нужен port-adapter wrap.

3. **TalkBack будет неправильно произносить** «Критичных проблем 2» (по-русски нужно «Две критичные проблемы»). Решается через `plurals` resource с четырьмя формами для русского языка (one/few/many/other).

**Также рекомендованы** (но не блокируют) **4 небольших дополнения в спек**:
- FR-008a — progress indicator в wizard'е (сейчас у нас 4 шага без индикатора, что выше senior-safe лимита 3).
- FR-017a — что делать если SetupCheck падает с exception.
- FR-032a — что делать если unlink не дошёл до сервера из-за отсутствия интернета.
- Assumption A-13 — явно зафиксировать что challenge text ≤14sp намеренно anti-elderly (это смысл soft barrier).

**Сильные стороны спека после clarify**: убрали PIN с lockout'ом (≈10 FR удалено), убрали tutorial overlay (5 FR + persistent state удалено), упростили модель до «soft barrier honest about its threat model». Это **значительно проще** реализовать и поддерживать.
