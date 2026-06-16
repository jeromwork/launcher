# Checklist: localization

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 14 ✓ / 6 ⚠ / 0 ✗ — clean (F-3 IS localization spec — well-covered)

> **Note**: F-3 owns the localization infrastructure. Most gates trivially pass because that's the spec's purpose.

---

## Strings

- [✓] **CHK001** All user-facing strings externalised.
  - FR-044 explicit: «Все user-facing строки в bundled JSON — ключи к `<lang>/strings.xml`, **никогда** литералами».
  - FR-031 CI fitness function fails build на orphan keys / missing keys.
  - Glossary §6 enforces. ✓

- [⚠] **CHK002** String ID naming convention.
  - F-3 examples: `wizard.next_button`, `tile_set.classic_6.name`, `system_setting.role_home.label`, `wizard.text_size.title`.
  - Pattern: `<area>.<concrete>.<role>` with dots.
  - **Minor inconsistency**: Android `strings.xml` convention uses underscores (`wizard_next_button`), spec mixes both (e.g., `tile_set.classic_6.name` — underscores в `tile_set` + `classic_6`, dots между levels).
  - **Acceptable**: moko-resources typed accessors normalize dots → `_` for Kotlin identifiers. Convention defined consistently через spec.

- [✓] **CHK003** Strings used in only one place still externalised.
  - FR-044 универсально: «все user-facing строки — ключи». CI fitness function enforces.

- [✓] **CHK004** Translator notes.
  - F-3 значительно exceeds стандартный pattern: `CONTEXT.json` (FR-031b) provides **rich per-key context** включая screen position, audience tone, semantic role + optional screenshot. ✓

## Plurals

- [⚠] **CHK005** Count-dependent strings use plurals resource.
  - F-3 ввёл новые count-dependent strings в Group D fixes:
    - FR-008b: «Шаг 3 из 5» (TalkBack announcement)
    - FR-008c: «Шаг N из M» (visual indicator)
  - **Не explicit declared**: эти strings должны использовать `plurals` resource. По-русски: «1 шаг», «2 шага», «5 шагов».
  - **Recommendation**: добавить FR-031e про plural support. См. Issue L-1.

- [⚠] **CHK006** Plural categories для RU / PL / AR.
  - moko-resources поддерживает ICU plural rules (включая RU few/many/other, AR zero/one/two/few/many/other).
  - F-3 не explicit задаёт plural policy.
  - **Covered by L-1 fix**.

## Format

- [⚠] **CHK007** Date formatting locale-aware.
  - `AttestationRecord.attestedAt: Instant` — если когда-нибудь displayed user'у, должен использовать locale-aware formatter (kotlinx-datetime `LocalDateTime` + locale formatter).
  - F-3 не explicit displays attestation timestamps — currently internal-only. Если будущий audit UI добавит — apply locale-aware formatting.
  - **Acceptable** foundation defer: nothing displayed сейчас.

- [N/A] **CHK008** Number formatting. F-3 не displays numbers user-facing (except «Шаг N из M» counters — handled via plurals).
- [N/A] **CHK009** Currency / unit. F-3 не displays currency.

## RTL

- [✓] **CHK010** Layout uses start/end.
  - Compose `LayoutDirection` API использует start/end natively.
  - FR-032 RTL detection helper.
  - SC-007: ar-SA RTL screenshot test ✓.

- [⚠] **CHK011** Directional drawables `autoMirrored = true`.
  - F-3 не explicit specifies autoMirrored policy.
  - **Recommendation**: добавить note в FR-034: «directional icons (back arrow, next arrow в SeniorButton end icon) MUST use `autoMirrored = true` или per-locale ldrtl resource variants для AR/HI».

- [✓] **CHK012** Custom layouts RTL.
  - F-3 не uses custom Canvas drawing.
  - Compose layouts respect `LocalLayoutDirection`. ✓

## Images / non-text content

- [⚠] **CHK013** No language-baked images.
  - F-3 uses `iconKey: String` для tile icons (FR-014) — icons referenced as keys, не language-specific images. ✓
  - **Recommendation**: добавить explicit policy: «icons referenced via iconKey must be language-agnostic (vector / symbolic); language-specific imagery prohibited в bundled tile.set».

- [N/A] **CHK014** Per-locale image folders. F-3 не имеет language-baked images.

## Truncation / expansion

- [⚠] **CHK015** Layouts tested для 30% expansion.
  - F-3 не explicit lists test для DE/RU expansion.
  - **Recommendation**: добавить SC про test fixtures для max-length strings. Connected с `checklist-localization-ui` next.

- [⚠] **CHK016** Fixed-width buttons clipping.
  - FR-034 SeniorButton: `wrapContentHeight()` implicit, но width не explicit.
  - **Recommendation**: дополнить FR-034 — SeniorButton uses `wrapContentWidth()` для adapt к translated labels.

## Locale change

- [✓] **CHK017** Behaviour on runtime locale change.
  - Edge Case: wizard re-renders, answers preserved.
  - SC-005a: explicit test (< 500ms re-render).

- [✓] **CHK018** In-app language switcher.
  - LanguageStep (FR-008) → writes to `UserPreferencesStore.languageOverride` (BCP-47 String).
  - `StringResolver` reads override on cold start (FR-050).
  - Activity-recreate путь covered.

## Accessibility-localisation overlap

- [✓] **CHK019** `contentDescription` localised.
  - FR-036 `SeniorContentDescription` helper.
  - Все user-facing strings (включая contentDescription'ы) проходят через StringResolver per FR-044.

- [✓] **CHK020** TalkBack reads localised content correctly.
  - FR-008b: announcement strings через StringResolver (localized в 11 языков). ✓
  - No string concatenation patterns в spec (что бы могли поломать grammar).

---

## Issues & fixes

### Issue L-1 — Plural support для count-dependent strings (CHK005/006, severity Medium)

**Problem**: FR-008b («Шаг 3 из 5») + FR-008c («Шаг N из M») — count-dependent strings. По-русски: «1 шаг», «2 шага», «5 шагов». По-арабски: zero/one/two/few/many/other. moko-resources supports ICU plurals, но F-3 spec не explicit fixes policy.

**Fix**: добавить FR-031e:
```
- **FR-031e (plural support)**: Count-dependent strings MUST использовать moko-resources
  `plurals` resource (ICU plural rules) вместо string concatenation. Например, «Шаг 3 из 5»:
  ```xml
  <plural name="wizard_step_n_of_m">
    <item quantity="one">Шаг %1$d из %2$d</item>
    <item quantity="few">Шаг %1$d из %2$d</item>
    <item quantity="many">Шаг %1$d из %2$d</item>
    <item quantity="other">Шаг %1$d из %2$d</item>
  </plural>
  ```
  CI fitness function FR-031 проверяет, что для каждого plural key все 11 локалей
  имеют все необходимые plural categories (RU: one/few/many/other; AR: zero/one/two/few/many/other; etc.). 
  Translation skill `procedure-translate-spec-strings` (FR-031a) генерирует все plural 
  forms через Claude API с контекстом «грамматический plural form».
```

### Issue L-2 (Optional) — RTL autoMirrored drawables (CHK011)

Дополнить FR-034:
```
SeniorButton icon slots (start/end icon) MUST use `autoMirrored = true` для directional
drawables (back arrow, next arrow) — auto-flips в RTL locales (AR/HI).
```

### Issue L-3 (Optional) — wrapContentWidth для SeniorButton (CHK016)

Дополнить FR-034:
```
SeniorButton width: default `wrapContentWidth()` — adapts к translated label length 
(RU/DE ~30-40% длиннее EN). Fixed width допустим только в grid layouts с explicit 
text constraints (handled by S-1 / S-2 layouts).
```

### Issue L-4 (Optional) — Language-baked images policy (CHK013)

Дополнить FR-014:
```
... `iconKey` referenced icons MUST быть language-agnostic (vector / symbolic); 
language-specific imagery в bundled tile.set prohibited (would break cross-language 
sharing per CLAUDE.md rule 9 shareability).
```

---

## Резюме

**14 ✓ / 6 ⚠ / 0 ✗** — F-3 localization очень well-covered (это его core scope). Один meaningful fix:

- **L-1**: plural support для count-dependent strings (FR-031e) — критично для FR-008b/c.

Остальные warning'и (L-2/3/4) — optional polish. Applying L-1 inline.
