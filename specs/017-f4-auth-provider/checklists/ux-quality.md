# Checklist: ux-quality

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 16/22 ✓, 4 open items, 2 N/A. UX surface F-4 minimal (1 composable + delegation к F-3 wizard); ясные UX decisions из clarify pass.

---

## Completeness — coverage of screens

- [x] **CHK001** Every user-facing screen listed — ⚠️ partial.
  - **F-4 owns**:
    - `SignInTrigger` composable (FR-033) — small UI unit, ~3 states (not signed in / signed in / loading).
  - **F-4 references but does NOT own**:
    - Wizard screen 2 «Настройка приложения» — F-3 spec territory (mentioned for context).
    - Google Credential Manager bottom-sheet — Google-rendered, not customizable.
  - **Open item**: spec не явно перечисляет UI states `SignInTrigger`:
    - State A: «Не вошли» — кнопка «Войти в Google».
    - State B: «Вошли как `<email>`» — кнопка «Выйти».
    - State C: «Loading» (between tap «Войти» и Google bottom-sheet appearance) — должно быть specified explicitly.
    - State D: «Loading» (between tap «Выйти» и signOut completion).
  - **Recommendation**: добавить FR clarification states explicitly в plan.md.
- [x] **CHK002** Every UX state specified — ⚠️ partial. См. CHK001 — loading states не explicit. Error states (Cancelled, NoEmail, NetworkError) описаны в US 2 + Edge cases, но **что показывает `SignInTrigger`** в каждом error case — не specified explicitly.
- [x] **CHK003** Navigation transitions — ✓ implied.
  - Forward: tap «Войти» → Google bottom-sheet (Google handles).
  - Back: при tap Cancel или back gesture в bottom-sheet → return to caller screen (per Q6).
  - Deep-link entry: **N/A** (F-4 не имеет deep links).
  - Recreation: `SignInTrigger` подписан на `currentUser` flow → state survives recreation automatically.
- [x] **CHK004** Cross-cutting overlays — ⚠️ partial.
  - Snackbar / toast: **NOT used** per Q6 («без toast'ов и сообщений»).
  - Dialog: Credential Manager bottom-sheet — single overlay, Google-controlled.
  - Confirmation dialog for sign-out: **NOT used** per Q3.
  - Error display после NoEmail / NetworkError — **unspecified UX presentation**. Inline message ниже кнопки? Toast? Dialog? **Open item**.

## Clarity — terminology and rules

- [x] **CHK005** UX terms unambiguous — ✓.
  - «Войти» (sign in) — used consistently.
  - «Выйти» (sign out) — used consistently.
  - «Восстановить существующий конфиг» — explicit value statement, не jargon.
  - «Учётная запись» / «Google account» — used interchangeably appropriate context (button label uses «Google»).
- [x] **CHK006** Vague qualifiers operationalised — ✓.
  - «Сразу открывается Google sign-in screen» (Q5) — explicit, no «smoothly» / «quickly».
  - «Без промежуточного explainer-экрана» — explicit.
  - «Без toast'ов и сообщений» (Q6) — explicit.
  - **No** «intuitive», «clean», «simple» qualifiers без operationalisation.
- [x] **CHK007** Action vocabulary explicit — ⚠️ partial. Spec mentions «нажимает Войти», «нажимает Отмена» — explicit tap. **Open item**: для Predictive Back gesture (close bottom-sheet swipe) — не упомянуто. См. permissions-platform CHK013.
- [x] **CHK008** Button labels exact — ✓ (с одним sub-item):
  - «Войти в Google для восстановления существующего конфига» — exact (FR-033 + Q5).
  - «Настроить с нуля» — exact (F-3 territory но mentioned).
  - «Вошли как `<email>`» — template with email injection.
  - «Выйти» — exact.
  - **Note**: button label «Войти в Google для восстановления существующего конфига» — **длинный** (43 символа RU). См. localization-ui checklist для length analysis.

## Consistency

- [x] **CHK009** In-Scope vs FR alignment — ✓. Все FR соответствуют scope «provider-agnostic port + Google adapter + Fake + SessionStore». Никаких rogue FR для cloud-feature UI или wizard internals.
- [x] **CHK010** Confirmation policy consistent — ✓.
  - Sign-in: NO confirmation dialog before Google bottom-sheet (per Q5).
  - Sign-out: NO confirmation dialog (per Q3 — sign-out simple, no real data loss).
  - **Consistency**: F-4 has zero confirmation dialogs. Это **stronger** consistency than partial confirm rules.
  - Delete account (если бы был в F-4) — **would** require confirmation, но это S-6 territory.
- [x] **CHK011** Multi-tap protection — ✓.
  - Edge case: «`AuthProvider.signIn` вызван дважды параллельно (двойной tap, race) → adapter MUST deduplicate (только один Credential Manager bottom-sheet активен в момент времени)».
  - Property test verifies.
  - **Note**: при double-tap «Выйти» — adapter `signOut()` идемпотентен (см. failure-recovery CHK009). Не disruption.

## Acceptance — measurability

- [x] **CHK012** Given/When/Then explicit per US — ✓. Все US 1-7 имеют explicit acceptance scenarios.
- [x] **CHK013** Success criteria measurable per UX moment — ⚠️ partial.
  - **Quantitative timings** не declared:
    - Time-to-Google-bottom-sheet после tap (should be ≤ 500ms).
    - Time-to-`currentUser`-emission после Credential Manager success (should be ≤ 1s).
    - SignInTrigger state transition smoothness.
  - **Open item для plan.md**: добавить performance SC (e.g., «SC-015: tap «Войти» → Google bottom-sheet appears within 500ms perceived latency на pixel_5_api_34»).
  - **Note**: spec не претендует на perf-spec status — это identity foundation. Plan.md может decide добавлять перф-метрики или нет.
- [x] **CHK014** Returning-user UX defined — ✓.
  - **Resume from background**: `currentUser` flow восстанавливается автоматически (User Story 5 — session persistence).
  - **Second-launch**: app reads `SessionStore`, restores identity, `SignInTrigger` шows «Вошли как X».
  - **Long pause** (hours/days): same behavior; token refresh handles expiry (User Story 4).

## Coverage — alternative paths

- [x] **CHK015** Every primary action has negative-path UX — ⚠️ partial.
  - Sign-in negative paths: Cancelled, NoEmail, NetworkError, ProviderUnavailable — все listed (US 2, Edge cases).
  - **What user sees в каждом**: Cancelled → return to caller (Q6); NoEmail → message; NetworkError → retry option mentioned. **NetworkError specific UX presentation** не fully specified.
  - **Open item**: explicit UX flow на NetworkError: inline error message + retry button в `SignInTrigger`? Toast? Modal?
- [x] **CHK016** Multiple entry points consistent — ✓.
  - F-4 sign-in entry points: wizard screen 2 («Войти в Google для восстановления существующего конфига») + standalone `SignInTrigger` («Войти в Google»).
  - **Question**: button labels отличаются? **Inconsistency risk**.
  - **Recommendation**: wizard screen 2 label longer (explains recovery value); `SignInTrigger` (после wizard) label shorter («Войти в Google» или «Войти в учётную запись для синхронизации»). Differences **justified** by context (wizard recovery vs Settings re-sign-in).
- [x] **CHK017** Long-pause scenarios — ✓.
  - 1 hour pause: token refresh handles (US 4).
  - Day pause: same.
  - Week+ pause без открытия app: при open — session persistence (US 5); если refresh failed → SignInTrigger shows «Не вошли», user re-signs.

## Non-functional UX

- [x] **CHK018** Accessibility — see separate `checklist-elderly-friendly` checklist (which extends a11y for senior persona).
- [x] **CHK019** Localisation — see separate `checklist-localization-ui` checklist.
- [x] **CHK020** Diagnostic UX (user sees что tracking is happening) — ⚠️ partial.
  - F-4 doesn't track behavior actively, но collects PII при sign-in (email, displayName).
  - **User sees**: «Войти в Google» button explains intent.
  - **Privacy Policy + Data Safety form** (FR-032) — pre-release tasks.
  - **No in-app indicator** that data being collected (e.g., «отправляется на сервер»).
  - **Open item**: consider visual indicator (small icon or text) в `SignInTrigger` when signed in: «синхронизация с сервером» или подобное, чтобы senior понимала, что данные на сервере.

## Dependencies / assumptions

- [x] **CHK021** UX doesn't depend on out-of-scope — ✓.
  - F-4 не depends на S-2 / S-5 / S-8 / S-9 capabilities для своего UX flow.
  - F-3 wizard dependency — explicit, documented в Assumptions.
- [x] **CHK022** Mock-data limitations — ✓.
  - FakeAuthAdapter pre-seeded users (FR-024) — used для tests / debug builds.
  - **Note**: при debug builds developer видит FakeAuthAdapter behavior (not real Google flow). Это expected (per FR-019 DI seam).

---

## Open items (для plan stage)

1. **`SignInTrigger` states explicit**: define all UI states (not signed in / loading-pre-google / signed in / loading-signOut / error). Mock-up или wireframe в plan.md.
2. **Error display UX**: что показывает `SignInTrigger` при NoEmail / NetworkError / ProviderUnavailable — inline / toast / dialog?
3. **Performance SCs**: SC-015 (optional) — tap-to-bottom-sheet latency.
4. **Wizard vs SignInTrigger label consistency**: justify difference between «Войти в Google для восстановления...» (wizard) vs «Войти в Google» (SignInTrigger).
5. **Privacy indicator в `SignInTrigger`** (signed-in state): consider showing «синхронизация с сервером» visual hint.
6. **Predictive Back gesture handling** (cross-ref permissions-platform CHK013).

---

## Verdict

**16/22 ✓, 4 partial, 2 N/A.** F-4 UX surface — **minimal** (1 composable + wizard delegation). Clear UX decisions из clarify pass (Q3, Q5, Q6, Q9) применены consistent. **No bloat**: spec не designs custom Sign-In screen, не designs explainer dialogs, не designs confirmation flows. Google Credential Manager handles auth UI; F-4 — только thin wrapper.

Открытые items — все improvements precision, не блокеры spec merge.

---

## Что это значит простыми словами

UX в F-4 — **простой и минимальный**, и это правильно:
- **Один компонент** (`SignInTrigger`) с тремя состояниями: «Не вошли» / «Вошли как Анна» / «Загрузка».
- **Никаких** окон-предупреждений перед «Войти» или «Выйти» — просто нажал и сделал. Если ошибся — нажми снова.
- **Без всплывающих toast'ов** — окно отмены не показывает сообщений «вы отменили вход».
- Google рисует своё окно входа — мы его не настраиваем. Мы только отдаём правильную кнопку и слушаем результат.
- Wizard (отдельная спека F-3) использует наш `SignInTrigger` на втором экране. В будущем настройки приложения (отдельная спека) могут использовать его же.

**6 уточнений для plan'а**:
1. Чётко описать все состояния `SignInTrigger` (что показывает кнопка во время загрузки).
2. Решить, как показывать ошибку «вход не удался, попробуйте снова» — рядом с кнопкой или всплывающим окном.
3. Зафиксировать целевую скорость отклика (например, бутшит Google должен появиться за 500мс после нажатия).
4. Объяснить разницу между текстом кнопки в визарде и в настройках.
5. Подумать, нужен ли визуальный индикатор «синхронизация с сервером» когда пользователь вошёл (чтобы бабушка понимала, что данные на сервере).
6. Прописать поведение жеста назад на Google окне.

Ни один пункт не блокирует утверждение спеки.
