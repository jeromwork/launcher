# Checklist: device-self-sufficiency

**Spec**: [`specs/017-f4-auth-provider/spec.md`](../spec.md)
**Run date**: 2026-06-18 (post-clarify pass)
**Verdict**: 17/17 ✓ — **passes cleanly**. F-4 — критический enabler для device self-sufficiency principle, и сделано правильно.

---

## Local viability

- [x] **CHK-DSS-001** Mode declared — ✓. F-4 — это **CLOUD-only enabler**, но **app сам без F-4 = LOCAL-only** (forever, бесплатно). F-4 declares:
  - **Local mode без F-4**: app fully usable indefinitely, no sign-in, no internet (User Story 1 + decision 2026-06-15-deferred-cloud/01).
  - **F-4 activated only**: при explicit user choice — wizard screen 2 «Войти» branch ИЛИ standalone `SignInTrigger` tap.
- [x] **CHK-DSS-002** LOCAL-only baseline works fully on fresh install — ✓.
  - User Story 1 (P1) — explicit negative test: «бабушка проходит wizard без Sign-In, использует launcher 10 минут, `currentUser` остаётся `null`, нет network calls к Firebase Auth».
  - FR-030: integration test `LocalModeNoSignInTest` верифицирует это в CI.
  - SC-004: «Cold-start app integration test проходит зелёным: wizard → main screen → 10s idle, без `AuthProvider.signIn(...)` calls».
- [x] **CHK-DSS-003** CLOUD-only justification — **N/A**. F-4 не cloud-only feature itself. F-4 — это **identity layer enabler** для future cloud features (S-2, S-4, S-5, S-8, S-9), каждая из которых **отдельно** будет проверена checklist'ом self-sufficiency.
- [x] **CHK-DSS-004** HYBRID local-baseline — ✓.
  - **Local baseline (without sign-in)**: launcher работает, плитки рендерятся из локального конфига, контакты звонят через ACTION_DIAL, темы применяются. Бесплатно бессрочно.
  - **Cloud enhancement (after sign-in)**: config syncs cross-device, push-уведомления от admin'а возможны (через S-4/S-8/etc), health-report admin'у (S-9), config backup на сервере.
  - Local-baseline **useful on its own** — это **главное** требование, удовлетворено.

## Sign-In trigger point

- [x] **CHK-DSS-005** Sign-In prompt **точно когда** — ✓.
  - **Не at app launch.** FR-030 / SC-004 verify это.
  - **Не at wizard step 1** (welcome). Wizard screen 1 — language / theme / font size / preset selection. Никакого sign-in.
  - **At wizard screen 2 «Настройка приложения»** — explicit user choice «Войти в Google для восстановления конфига» (per clarification Q5, User Story 2).
  - **Or at standalone `SignInTrigger`** (FR-033) — dynamically embedded anywhere; currently dёргается только из wizard.
- [x] **CHK-DSS-006** Sign-In prompt copy explains what unlocked — ✓.
  - Wizard button text (FR-033 / User Story 2): «Войти в Google для восстановления существующего конфига». **Explicitly** explains the value (recover existing config), not generic «sign in to continue».
  - Per clarification Q5: **NO** intermediate explainer screen — the **button label itself** is the explainer. Это минималистичная, sufficient implementation.
- [x] **CHK-DSS-007** Decline → graceful degradation — ✓.
  - User Story 2 acceptance #5 + clarification Q6: «пользователь нажимает Отмена → wizard остаётся на screen 2 с двумя кнопками, без toast'ов и сообщений; пользователь может снова нажать Войти или выбрать Настроить с нуля».
  - Edge cases section: «Пользователь отменил Sign-In bottom-sheet → `Outcome.Failure(AuthError.Cancelled)`. **Никакого** error toast — это легитимный choice».
  - **Никакого broken state**.

## Local→cloud promotion

- [x] **CHK-DSS-008** Local→cloud merge через `VersionedConfigViewer` (S-8) — ✓.
  - Clarification Q7 explicit: «config-sync применяет policy "сервер всегда приоритетнее, локальный мержится в серверный, push обратно". UI разруливает S-8 VersionedConfigViewer».
  - F-4 **не строит** свою merge UI — это S-8 responsibility.
  - FR-034 cross-reference на S-8 явный.
  - **No custom one-off merge dialog** — correct delegation.
- [x] **CHK-DSS-009** Different Google account case — ✓.
  - Edge case: «Sign-In trap (второй Google account): пользователь раньше Sign-In под account A, sign-out, теперь Sign-In под account B → это **новый чистый аккаунт** (per decision 2026-06-15/01 §Sign-In trap). `GoogleSignInAuthAdapter` находит/создаёт **новый** `stableId` для account B в `/identity-links/google/{sub_B}`. Локальный кеш (плитки, контакты, темы) **остаётся как был** — это рабочее состояние app'а (per clarification Q3)».
  - Identity-links design (FR-016a) гарантирует: new account → new UUID → fresh cloud namespace.
  - No prompt «продолжить со старым?» — **silent fresh start** per decision 01.

## Cloud→local downgrade

- [x] **CHK-DSS-010** Subscription expiry / offline → clean pause, NOT crash — ✓.
  - F-4 specific behaviour: `subscription_state` всегда `Unknown` в F-4 (FR-011) — реальный billing **не строится** в F-4.
  - При offline: edge case «Token expired offline (refresh невозможен) → `currentUser` остаётся `AuthIdentity` (last known), любой server-bound action falls back на retry-when-online. UI индикатор оффлайн».
  - Per decision 03 cloud-expired downgrade — handled in S-10 (subscription server timer), not F-4.
  - **F-4 does NOT make app unusable**.
- [x] **CHK-DSS-011** User sees WHAT stopped working — ✓. F-4 itself не имеет cloud features. Specific cloud feature spec'и (S-2/S-4/S-5/S-8/S-9) каждая ответственна за clear «cloud unavailable» messaging — это их checklist responsibility.

## Anti-patterns to refuse

- [x] **CHK-DSS-012** No mandatory Sign-In at first launch — ✓. FR-030 + User Story 1 + SC-004 — **triple-checked**.
- [x] **CHK-DSS-013** No mandatory pairing at first launch — ✓. F-4 не строит pairing (это S-2). Wizard screen 2 — sign-in choice (optional), not pairing.
- [x] **CHK-DSS-014** Local feature not bottlenecked behind cloud dep — ✓. F-4 не имеет local features which depend on cloud. Local features (launcher, плитки, контакты, темы, конфиг) — все работают без F-4.
- [x] **CHK-DSS-015** No anonymous Firebase Auth fallback — ✓. FR-029: «Anonymous Firebase Auth полностью удалён: Detekt rule ловит import `signInAnonymously` или `FirebaseAuth.getInstance().signInAnonymously` в любом файле». Per decision 2026-05-30 §02-identity-anonymous-removal.

## Cross-spec consistency

- [x] **CHK-DSS-016** Local device sees cloud-feature data unavailable gracefully — ✓ implied. F-4 specifically: на local-mode устройстве `currentUser == null` → consumer-сервисы (S-5 photos, S-8 sync) видят это через `currentUser` flow и **сами** решают как degrade (placeholder photo, no sync indicator). F-4 предоставляет signal, не диктует UX.
- [x] **CHK-DSS-017** No assumption cloud data always present — ✓. `AuthProvider.currentUser: Flow<AuthIdentity?>` — **nullable** signature. Consumer'ы вынуждены handle `null` case (compile-time safety). `signIn()` возвращает `Outcome<AuthIdentity, AuthError>` — typed errors, не silent assumption.

---

## Strengths (beyond baseline)

### Strength 1: F-4 — это **the enabler** для device self-sufficiency, не violator

Decision 2026-06-15/01 переопределил activation timing: F-4 **не запускается** на первом launch. Это центральная design constraint. F-4 spec fully embraces это:
- FR-030 (`LocalModeNoSignInTest`) — integration test проверяет no sign-in после wizard cold start.
- SC-004 — same test в CI пайплайне.
- User Story 1 (P1) — explicit negative test, **highest priority**.

### Strength 2: signOut — без factory reset (Q3)

Clarification Q3 устранила common anti-pattern: signOut НЕ стирает local cache. App продолжает работать с локальным state. Это **сильнее**, чем generic device-self-sufficiency requirement — даже после sign-out пользователь не теряет данные.

### Strength 3: F-4 после sign-in **только** save+emit (Q7)

Clarification Q7 устранила другой common pitfall: F-4 не вызывает config-sync, не пушит конфиг сам. Это означает что **boundary between identity and config is clean**: identity provider может «исчезнуть» (offline, error) и config layer продолжает работать независимо.

### Strength 4: Health-report через S-9 (Q8)

Clarification Q8 решил design challenge: «admin должен знать что Android-настройки сбиты на бабушкином устройстве». Решение — **отдельная** S-9 spec для health monitoring, F-4 только identifies устройство. Это правильное separation; F-4 не разрастается в monitoring tool.

### Strength 5: `SignInTrigger` reusable, не attached к одному месту (Q9)

Decoupling sign-in UI от wizard позволяет любому будущему UI (Settings, debug panel) инициировать sign-in **без дубликации**. Reusability **не bloat** because clarification Q9 — current consumer (wizard) уже есть.

---

## Verdict

**17/17 ✓.** F-4 — **canonical** реализация device self-sufficiency principle. Все anti-patterns refused; все promotion/degradation paths defined; никаких bottleneck'ов. App может работать **бесплатно бессрочно** без sign-in.

---

## Что это значит простыми словами

Спека F-4 правильно поддерживает главный принцип проекта: **«каждое устройство самодостаточно»**:
- Установил приложение → пользуешься сразу. Никаких просьб «войдите в Google». Никакого «бесплатный период закончился». Никаких ограничений на размер шрифта или количество плиток.
- Хочешь облачные функции (фотографии от внуков попадают в иконки плиток, admin может удалённо помогать, синхронизация между телефоном и планшетом) — заходишь в Google **тогда**, когда сам решил. Кнопка «Войти» **только** на втором экране визарда или в отдельном компоненте, который можно встроить в настройки.
- Если выходишь из аккаунта — **ничего не удаляется**. Плитки, контакты, темы — всё остаётся. Это просто «отключилась синхронизация с сервером, работаешь локально».
- Если попал на сторонний Google аккаунт (например, рабочий вместо личного) — приложение **не пугает** «продолжить со старым?». Просто новый чистый аккаунт.
- Если в твоей стране запретят Google — мы добавим вход по телефону, и **тот же UUID** будет работать. Никаких миграций для пользователя.
- Если интернет пропал — приложение продолжает работать. Локальный кеш цел.

**Все 17 проверок пройдены чисто.** Спека готова к `/speckit.plan`.
