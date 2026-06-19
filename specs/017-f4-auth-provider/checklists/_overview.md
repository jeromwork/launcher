# Checklists overview — Spec 017 F-4 AuthProvider

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Clarify pass**: 2026-06-18 (9 grey-zone questions closed via mentor mode)
**Checklists run**: 14 (3 always-on + 11 triggered)
**Overall verdict**: **PASSES baseline для `/speckit.plan`**. Все ✅ или partial (open items для plan stage). Никаких блокирующих failures.

---

## Summary table

| # | Checklist | Verdict | Open items | Notes |
|---|-----------|---------|------------|-------|
| 1 | [requirements-quality](requirements-quality.md) | **14/16 ✓** | 2 | Internal contradictions (4) fixed in-flight |
| 2 | [meta-minimization](meta-minimization.md) | **11/13 ✓** | 1 | `ProviderKind` removal — exemplary MVA |
| 3 | [dev-experience](dev-experience.md) | **19/22 ✓** | 3 | Setup doc + logging policy + onboarding |
| 4 | [domain-isolation](domain-isolation.md) | **16/16 ✓** | 0 | **Canonical rule 2 ACL implementation** |
| 5 | [wire-format](wire-format.md) | **14/18 ✓** | 6 | Identity-links schemaVersion missing (FR-016b proposed) |
| 6 | [device-self-sufficiency](device-self-sufficiency.md) | **17/17 ✓** | 0 | F-4 = canonical deferred-cloud enabler |
| 7 | [tamper-resistance](tamper-resistance.md) | **16/22 ✓** | 4 | 5 deferred к S-10; L2/L3 escalation TODOs |
| 8 | [failure-recovery](failure-recovery.md) | **14/17 ✓** | 7 | Identity-links retry + diagnostic events |
| 9 | [security](security.md) | **20/24 ✓** | 5 | Logging policy + backup exclusion + Security Rules |
| 10 | [backend-substitution](backend-substitution.md) | **15/16 ✓** | 4 | **Canonical** UUID + identity-links design |
| 11 | [ai-readiness](ai-readiness.md) | **18/20 ✓** | 3 | `getProviderKind()` mention needs removal |
| 12 | [permissions-platform](permissions-platform.md) | **16/22 ✓** | 4 | OEM matrix comprehensive; 5 N/A |
| 13 | [ux-quality](ux-quality.md) | **16/22 ✓** | 6 | SignInTrigger states; performance SCs |
| 14 | [elderly-friendly](elderly-friendly.md) | **12/22 ✓** | 8 | **«конфиг» jargon → «настройки»** recommended |
| 15 | [localization-ui](localization-ui.md) | **11/19 ✓** | 8 | Button label too long for DE/RU expansion |
| 16 | [state-management](state-management.md) | **13/17 ✓** | 4 | In-flight signIn during rotation |
| 17 | [core-quality](core-quality.md) | **11/18 ✓** | 7 | Pre-release tasks (FR-032) identified |

(Numbering 1-3 = always-on; 4-17 = triggered.)

---

## Cross-cutting open items (приоритизированы)

### CRITICAL (рекомендуется fix BEFORE plan stage, 1-line fixes):

1. **«конфиг» jargon → «настройки»** в button label:
   - Current: «Войти в Google для восстановления существующего конфига».
   - Recommended: «Войти в Google для восстановления настроек».
   - **Twofold improvement**: elderly-friendly CHK009 + localization-ui CHK-UI-002 (length expansion).
2. **AI Affordance section правка**: remove `getProviderKind()` capability mention (`providerKind` removed per Q4).

### HIGH (must address в plan.md):

3. **Identity-links schemaVersion** (FR-016b): wire-format + backend-substitution.
4. **Identity-links Firestore Security Rules**: write-only-if-auth-uid-matches AND not-exists. Security + tamper.
5. **Logging policy** (no PII, no tokens, structured categories): dev-experience + failure-recovery + security + core-quality.
6. **Backup exclusion** для session blob: security + core-quality.
7. **In-flight signIn coroutine scope** hoisting: state-management.
8. **SignInTrigger UI states explicit** (loading, error display): ux-quality + elderly-friendly.

### MEDIUM (plan.md improvements):

9. **Senior-safe visual baseline для SignInTrigger** (≥ 18sp / ≥ 56dp / ≥ 16dp / 4.5:1): elderly-friendly.
10. **Mockups в 3 locales** (EN/DE/AR): localization-ui.
11. **fontScale=2.0 test**: localization-ui + elderly-friendly.
12. **Light + dark theme** explicit для SignInTrigger: core-quality.
13. **Edge-to-edge inset handling**: core-quality.
14. **minSdk/targetSdk** explicit (24 / 35+): core-quality.
15. **Data Safety form** preparation (pre-release): core-quality.
16. **Cross-spec contracts** для S-8/S-6/S-9 (как FR-034 проверяется): requirements-quality.
17. **EncryptedSharedPreferences key namespace**: wire-format.
18. **OAuth scope whitelist** Detekt rule: security.

### LOW (deferred к consumer-spec / post-MVP):

19. **`AuthorizedRequestSigner` port** (для S-8 RPC signing): cross-ref в inline TODO.
20. **Play Integrity API** L2 escalation TODO: tamper-resistance.
21. **Code attestation** L3 TODO: tamper-resistance.
22. **Cloud AI provider consent UX** (F-2 territory): ai-readiness.

---

## Что было найдено и исправлено в clarify-aware diff

Перед записью чек-листов исправлены **4 внутренних противоречия** в spec.md между Clarifications table и User Stories / FR:

1. **US 1 acceptance #4**: ссылка на «cloud-feature кнопка» — заменена на «пользователь никогда не нажимает Войти в Google в wizard или SignInTrigger». Q5 boundary enforced.
2. **US 2 acceptance #2**: ссылка на `SessionStore.current()` возвращает non-null — переформулирована: SessionStore remains internal. Q2 boundary enforced.
3. **US 3 acceptance #4**: grep допускал `ProviderKind` в AuthProvider.kt — убрано. Q4 boundary enforced.
4. **US 4 description**: «cloud action» — заменены на «server interaction» / «consumer-сервис». Q5 boundary enforced.

---

## Notable strengths

1. **CLAUDE.md rule 2 ACL** для auth-providers — **canonical implementation**. Domain-isolation 16/16 clean.
2. **UUID stableId design** (clarification Q1) устранил the central one-way door — own-server cutover без миграции UID.
3. **Device self-sufficiency** 17/17 clean — F-4 = enabler, не violator принципа deferred-cloud.
4. **Provider-swap fitness function** (US 6 / SC-008) — **automated proof** того, что provider-agnostic дизайн работает.
5. **Anti-bloat discipline** — `ProviderKind` enum **удалён** в clarify pass; `CloudFeatureGate` composable **отброшен** как ошибочный.
6. **Backend substitution** 15/16 — explicit cost-of-swap table; 4 inline TODOs со specific exit destinations.
7. **Tamper resistance** — никаких client-side flags для billing; subscription stays `Unknown` в F-4.

---

## Готовность

✅ Spec **готова** к `/speckit.plan`.

После 2 critical fixes (jargon + AI Affordance leftover) — spec ready для plan stage без caveats.

22 cumulative open items для plan.md — все improvement в precision, никаких rearchitecture risks.

---

## Что это значит простыми словами

Все **14 чек-листов** прошли. Самая жёсткая проверка — **изоляция домена от Firebase / Google SDK** — пройдена идеально (16/16 без замечаний). F-4 — **образцовая** реализация принципа «если завтра Google исчезнет, мы переписываем один файл, не весь identity-слой».

**2 правки настоятельно рекомендуются сразу** (1 строка каждая):
1. В тексте кнопки заменить «конфига» на «настроек» — и понятнее бабушке, и короче на немецком.
2. Убрать упоминание `getProviderKind()` из секции «AI Affordance» — мы удалили это поле в clarify pass.

**Около 20 пунктов для следующего шага** (plan.md) — это улучшения, не блокеры:
- Версионирование схемы для Firestore таблицы identity-links.
- Правила доступа Firestore (чтобы нельзя было подменить чужую запись).
- Что и как логировать (без email и токенов в логах).
- Поведение при повороте экрана во время входа.
- Светлая / тёмная тема, поддержка крупного шрифта, макеты в 3 языках.
- Подготовка Data Safety формы Google Play перед релизом.

**Спека F-4 готова к следующему шагу `/speckit.plan`** — там будет конкретный план реализации и архитектурные решения.
