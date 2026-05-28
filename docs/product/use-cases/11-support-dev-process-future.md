# 11. Support, Dev Process & Future Verticals — операционный слой и горизонт

> **Status**: 🟡 partially decided (D-2 + D-16 RESOLVED 2026-05-27 evening) · **Created**: 2026-05-27

## Vertical-slice testing — workflow rule (D-2 RESOLVED)

**Resolved**: workflow change. Каждая спека обязана иметь секцию **«Local Test Path»** в `spec.md`. `procedure-cross-artifact-trace` верифицирует заполненность.

### Что в Local Test Path

Каждая спека отвечает:
- **Что строит**: какая user-facing функциональность появляется.
- **Как разработчик локально это проверит end-to-end**: один эмулятор / два эмулятора / mock backend / fakeAdapter — конкретные команды.
- **Где fake / mock adapters** обеспечивают local-only testing.
- **Edge cases**, которые local path не покрывает (требует physical device / real backend).

### Зачем

Закрывает боль S-105 / S-801 (local dev experience). Раньше: каждая user-facing спека требовала второе устройство для admin'a — невозможно тестировать соло. Теперь: спека **обязана** объяснить, как локально проверить, иначе не проходит cross-artifact-trace.

### Action items

- Обновить `.specify/templates/spec-template.md` — добавить обязательную секцию.
- Обновить `procedure-cross-artifact-trace` skill для verification.

## Crash collection — Android Vitals + Local log + Share-intent (D-16 RESOLVED)

**Resolved**: privacy-respecting hybrid approach.

### Primary source — Android Vitals (Google Play Console)

- **Automatic**, без SDK в нашем app.
- Данные собираются из Android системы у users, opted-in в diagnostics sharing (Settings → Google → Auto-include diagnostics — стандартный Android setting, не наш).
- Доступны нам в **Play Console → Android vitals dashboard**.
- Covered: crash rate, ANR rate, stack traces, device + version breakdown.
- **Not covered**: non-Play distribution (AppGallery, RuStore, sideload, B2B partner builds).

### Local crash log

- App ловит uncaught exceptions, пишет в **local rotating file**.
- Доступен в **Settings → Diagnostics**.
- **Non-intrusive notification** при crash: «У вас был краш — отправить данные о сбое?». Один тап → preview данных → share intent.

### Send via Android share intent

- Admin / user выбирает channel: email / WhatsApp / Telegram / любой.
- App composes pre-filled message (или attachment) с crash data.
- **Никакого нашего endpoint в MVP** — пересылка через native Android share.
- Privacy: пользователь сам видит, что отправляет, и куда.

### Architecture — adapter pattern

```kotlin
// core/domain/
interface CrashReporter {
    fun reportCrash(info: CrashInfo)
    fun getLocalLog(): List<CrashInfo>
    fun clearLocalLog()
}

// adapters
class LocalOnlyCrashReporter : CrashReporter  // default, всегда
class RemoteEndpointCrashReporter : CrashReporter  // inline TODO post-MVP
class FakeCrashReporter : CrashReporter  // tests (rule 6)
```

### Что НЕ делаем в MVP

- **NO Crashlytics SDK**.
- **NO Sentry SDK**.
- **NO automatic remote crash reporting** (только share-intent на explicit user action).

### Post-MVP

- **Self-hosted Sentry** — когда non-Play distribution начнёт быть значимой, или когда нужны custom breadcrumbs / real-time alerts.
- Server-roadmap entry: `docs/dev/server-roadmap.md` — добавить как exit ramp.

### Privacy invariants

- Логи **sanitized от PII** перед записью (fitness function проверяет, что `phoneNumber`, `email`, `contactName` не попадают в plain text log statements).
- Stack trace + device model + OS version — безопасные данные.
- Memory dumps **не** сохраняются.


> **Зачем читать**: support и dev-process — это **операционный** слой, который держит уже выпущенный продукт. Future verticals — горизонт того, что **планируем**, но **не сейчас**. Все три вместе помогают понять, что не упустить из vision'а.
> **Источник**: `user-journeys-draft.md` §7.12 + §7.14 + §7.15.

---

## Что это за документ (просто)

Три темы, объединённые тем, что они **не features** в классическом смысле:

1. **Support / Operations** — что мы делаем, когда у пользователя проблема. Кому пишет admin, когда не работает? Куда сходятся crash reports? Кто отвечает на bad-reviews? Без этого продукт после релиза превращается в headless monster.

2. **Dev Process** — как мы сами работаем. Spec-kit, ADR, тестирование, секреты. Эти вопросы влияют на скорость и качество. Сейчас многое настроено хорошо (skill `android-emulator`, memory rules, checklists), но **vertical-slice testing** и **DX для local end-to-end** — gap.

3. **Future Verticals** — что **зафиксировано как будущее** в backlog'е (FUTURE-SPEC-001..010). Не делаем сейчас, но **архитектурно подготавливаем** (как 011 OWD-4/OWD-5).

## Главные понятия (просто)

### Support / Ops

- **Support tier** — level of service. T1 (FAQ + bot), T2 (human reply), T3 (escalation engineer).
- **Crash report** — automatic upload error info. Crashlytics / Sentry. Privacy-sensitive (D-16).
- **Telemetry** — anonymized usage metrics. Privacy-sensitive (D-12).
- **Known-issues banner** — баннер в app'е «знаем о проблеме X, work-around Y».
- **Beta channel** — отдельный track для testers (Play Internal Testing, TestFlight).

### Dev Process

- **Spec-kit** — наш workflow: `/speckit.specify` → `clarify` → `plan` → `tasks` → `analyze` → implementation.
- **ADR** — Architecture Decision Record. `docs/adr/ADR-XXX.md`.
- **Vertical slice** — узкий end-to-end test path per feature. Сейчас не enforce'ится (D-2).
- **OEM matrix** — матрица OEM × Android versions для smoke tests.
- **Constitution checks** — `procedure-constitution-check` skill.
- **Memory** — auto memory system, `C:\Users\user\.claude\projects\c--work-launcher\memory\`.

### Future Verticals

- **FUTURE-SPEC-NNN** — отложенные спеки. Не «idea backlog», а **зафиксированные направления** для будущей работы.

## Use case инвентарь

### Support / Ops

| ID | Кейс | Status |
|---|---|---|
| SUP-001 | Bug report from admin | ❌ |
| SUP-002 | Bug report from Managed (Managed не напишет — через admin'а) | ❌ |
| SUP-003 | Crash collection (Crashlytics? privacy concern) | ❌ D-16 |
| SUP-004 | Telemetry opt-in/opt-out | ❌ D-12 |
| SUP-005 | In-app support contact (email, chat) | ❌ |
| SUP-006 | FAQ / help center | 🔮 FUTURE-SPEC-006 |
| SUP-007 | Known-issues banner | ❌ |
| SUP-008 | Beta channel | ❌ |
| SUP-009 | Update notifications (new version) | ❌ |
| SUP-010 | AI/MCP-based support workflow | ❓ context §11 |
| SUP-011 | Refund flow (связан с M-007) | ❌ |
| SUP-012 | Negative review response process | ❌ |

### Dev Process

| ID | Кейс | Status |
|---|---|---|
| DV-001 | Spec sequencing (linear vs vertical-slice) | ❓ D-2 |
| DV-002 | Cross-artifact tracing | ✅ skill |
| DV-003 | Constitution checks | ✅ skill |
| DV-004 | Local dev experience — невозможно тестировать без двух устройств | ❌ S-105 / S-801 |
| DV-005 | Secrets handling | ✅ memory |
| DV-006 | Emulator workflow | ✅ skill |
| DV-007 | Physical-device QA | 🟡 deferred |
| DV-008 | OEM matrix smoke | ❌ SPEC010-DEV-001 |
| DV-009 | Elder-user testing (5 users) | ❌ SPEC010-DEV-002 |
| DV-010 | CI/CD pipeline | ❌ |
| DV-011 | Release management / changelog | ❌ |
| DV-012 | ADR maintenance | 🟡 |
| DV-013 | Documentation hygiene (this docs/product/use-cases/ structure) | 🟡 in progress |

### Future Verticals (из backlog FUTURE-SPEC-NNN)

| ID | Spec | Status | Notes |
|---|---|---|---|
| F-001 | Wearable monitor (часы — пульс, давление, шаги) | 🔮 | medical tier |
| F-002 | Security sensors (smart home alarm) | 🔮 | |
| F-003 | Closed messengers (LINE / WeChat / KakaoTalk) | 🔮 | Asia / partner |
| F-004 | Shared admin contact book | 🔮 | |
| F-005 | Preset editor (full settings) | 🔮 | |
| F-006 | Onboarding and tutorials | 🔮 | связан с SUP-006 |
| F-007 | Bidirectional pairing (Managed настраивает admin'а) | 🔮 | |
| F-008 | Family group shared encryption | 🔮 | |
| F-009 | Multi-device recovery + multi-device для одного владельца | 🔮 | |
| F-010 | Key rotation / forward secrecy | 🔮 | |

## Главные открытые вопросы

### D-2. Vertical-slice testing — фиксируем как rule

**Контекст**: уже несколько раз поднимался. Если каждая спека обязана включать local end-to-end test path — это **меняет spec-kit workflow**: добавляется обязательный «Test path» section в spec.md и tasks.md.

**Варианты**:
- **Fix as workflow change**: вносим в spec-template и в `procedure-cross-artifact-trace`. Каждая спека отвечает «как я локально это проверю».
- **Cultural guideline**: пишем в CLAUDE.md, но не enforce.
- **Не делаем**: leaves boil как сейчас.

**Регрет**: не делаем → каждая новая спека повторит S-105 / S-801.

**Рекомендация**: fix as workflow change. Это **two-way door** (можно откатить, если станет overhead).

### D-16. Crash collection — Crashlytics / Sentry / нет

См. также 07-data-and-privacy D-16.

**Варианты**:
- **Crashlytics**: easy, free, all goes to Google.
- **Self-hosted Sentry**: control, costs server.
- **No automatic, only manual reports**: max privacy, min insights.

**Рекомендация (best-guess)**: opt-in Crashlytics (по умолчанию off, явный consent в onboarding). Будущее — own-server Sentry, когда есть свой сервер.

### D-Sup-1. Support channel — email / in-app chat / Discord

**Контекст**: у нас будут вопросы. Сразу или нет.

**Варианты**:
- **Email только**: support@ourdomain. Simple. Async.
- **In-app contact form**: structured. С context (device, version). Хорошо.
- **Discord / Telegram community**: community-driven. Best для tech-savvy admins.
- **Help center (Zendesk / Intercom)**: professional. Стоит $.

**Рекомендация**: in-app contact form + email для MVP. Discord — позже, когда есть community.

### D-Dev-1. Spec-kit workflow — какие skill'ы менять

**Контекст**: после Phase 1 (12 спек) мы знаем, какие skill'ы помогают, какие нет. Стоит обновить.

**Рекомендация**: после `04-remote-management` (или 03) — sit down и обновить:
- `procedure-assess-spec-complexity` — добавить новые checklists (например, accessibility deep-dive).
- `checklist-elderly-friendly` — расширить per 6 доменов из 03.
- spec-template — добавить «Local Test Path» section.

### D-Future-1. Какие FUTURE-SPEC поднимать в MVP

**Контекст**: FUTURE-SPEC-006 (onboarding/tutorials) — нужен почти сразу после launch. FUTURE-SPEC-009 (multi-device recovery) — связан с S-501 (migration), который **должен быть в MVP**.

**Рекомендация**:
- **MVP+1**: FUTURE-SPEC-006 (onboarding), FUTURE-SPEC-009 (recovery basics).
- **MVP+2-3**: FUTURE-SPEC-010 (key rotation), FUTURE-SPEC-008 (family groups).
- **Long-term**: F-001 (wearable), F-002 (security), F-003 (closed messengers).

## Что в спеках / документах уже зафиксировано

| Документ | Что фиксирует |
|---|---|
| `docs/dev/project-backlog.md` секция «Future Specs» | FUTURE-SPEC-001..010 |
| `docs/dev/project-backlog.md` секция «Spec 010» | physical-device deferred tests |
| `.claude/skills/` | спек-kit workflow, checklists, procedure-skills |
| `CLAUDE.md` | engineering rules |
| `.specify/memory/constitution.md` | дисциплина |
| memory `testing_environment.md` | testing constraints |

## Связь с другими документами

- **02 Actors** — A6 (dev) — это про DV-004, S-105 / S-801.
- **03 Launcher UI** — touch debounce / accessibility — связан с DV-008 (OEM matrix).
- **07 Data & privacy** — D-12, D-16 (telemetry, crash).
- **10 Monetization** — M-007 refund handling = SUP-011.

## Источники

- `docs/dev/project-backlog.md`.
- `.specify/memory/constitution.md` Article XV (Support).
- [Crashlytics docs](https://firebase.google.com/docs/crashlytics).
- [Sentry self-hosted](https://develop.sentry.dev/self-hosted/).
- [Play Console Internal Testing](https://support.google.com/googleplay/android-developer/answer/3131213) — beta channel.
- Skills: `android-emulator`, `checklist-*`, `procedure-*`, `speckit-*`.

## Заметки решений

| Дата | Решение | Regret | Exit ramp |
|---|---|---|---|
| _(пусто)_ | | | |
