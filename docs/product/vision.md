# Vision — Family Care Ecosystem

> **Стратегический документ.** Что строим, зачем, какие принципы. Не tracker.
> Операционный план (что сделано / что в работе / что следующее) живёт в **Backlog.md**:
> запусти `backlog browser` (http://localhost:6420) или `backlog overview` для текстовой сводки.

---

## Что строим

**Family Care Ecosystem** — не «launcher для пожилых», а **система заботы**:

- **communication platform** — родственники держат связь с пожилым простым и безопасным способом,
- **remote support infrastructure** — admin (родственник) удалённо помогает с настройкой устройства,
- **family coordination system** — N admin'ов + M Managed-устройств + caregivers (сиделки/соцработники) координируются в едином пространстве.

Launcher — лишь **interface surface**. Ядро продукта: забота, связь, удалённая поддержка, безопасное сопровождение пожилого, снижение тревоги семьи.

---

## Главный фильтр фич

> Если фича **не усиливает** «семья поддерживает связь и заботу о пожилом через единое безопасное пространство» — это **suspect feature**, требует обоснования.

Применяется при оценке любой новой backlog-задачи. Если на этот вопрос нет уверенного «да» — задача в `drafts/` или `parking-lot` label, не в активный план.

---

## Архитектурные столпы

**Universal Preset Architecture:**
- Единое ядро (`core/`) + N preset'ов (Simple Launcher для Managed, Admin App для семьи, в будущем — Caregiver app, TV preset, Wearable).
- **Family Group** — primary primitive (N admin'ов + M Managed + caregivers + roles).
- **Envelope encryption** для shared content (E2E preserved, сервер не видит контент).
- **Capability Registry + Exposure Adapters** (AI-ready, без provider implementations в MVP — F-2 отложен до Phase 4).
- **Wizard module** с reusable steps + nested config templates (shareability-ready per CLAUDE.md rule 9).
- **Subscription per admin** (family monthly), cloud-only billing (per decision 2026-06-15-deferred-cloud/03 — anti-tamper).

**Каждое устройство самодостаточно:**
- Local mode работает без Google Sign-In, без cloud, без identity.
- Sign-In происходит **при первом cloud action**, не при запуске (decision 2026-06-15-deferred-cloud/01).
- Конфиг принадлежит локальному Google-аккаунту устройства (decision 02).
- Cloud features = opt-in upgrade, не required step.

**Backend substitution-ready:**
- Сейчас: Firebase + Cloudflare Worker (free tier) + Firestore Security Rules.
- Каждый external SDK через port + adapter (CLAUDE.md rule 2 — Anti-Corruption Layer).
- Server migration tracking в [`docs/dev/server-roadmap.md`](../dev/server-roadmap.md).

---

## Что explicitly OUT (для всего проекта)

- Monetization billing flows (frozen, base зафиксирован — реализация в S-10 cloud-only).
- iOS Admin — Phase 4 (V-1), не MVP.
- Android TV preset — Phase 4 (V-4).
- Full Family Album с видео/аудио — Phase 4 (V-3); MVP даёт только photos для контактов (S-5).
- Elderly-Friendly Messenger embedded — Phase 4 (V-2), отдельное приложение, не в launcher.
- Hardware SOS power-button — post-MVP (S-4 даёт software SOS).
- Dwell-to-activate accessibility — post-MVP (P-4 adaptive presets).
- Social recovery — Phase 5 L-6 (accepted edge case: «потерял так потерял» в MVP).
- AI provider implementations (App Actions, MCP server, Gemini Nano) — Phase 5 L-3 (architecture готовится через F-2 Phase 4).
- Crashlytics / Sentry SDK — не нужны, Android Vitals достаточно.

---

## Фазы (общий контур)

Детали и статус **каждой** задачи смотри в Backlog. Здесь только верхний уровень:

| Phase | Что | Где смотреть |
|---|---|---|
| **Phase 0 — Vision** | 28 D-вопросов закрыты (discussion 2026-05-27/28). | `docs/product/use-cases/`, `docs/product/decisions/` |
| **Phase 1 — Identity & Crypto Foundation** | F-3 Wizard, F-CRYPTO, F-4 AuthProvider, F-5b config E2E, F-5c FCM push, F-5 Root Key + Recovery. | Milestone `m-0` в backlog. |
| **Phase 2 — MVP Vertical Slices** | S-1..S-10 — Simple Launcher, Admin App, Contact Tiles, SOS, Photos, Deletion, Editor, Health Monitoring, Subscription Server Timer. | Milestone `m-1`. |
| **Phase 3 — Post-MVP Foundations** | P-1..P-10 — Preset schema v2, Android deep integration, authoring/sharing, adaptive UX, config copy, recovery 2FA, optional reminders, provider recipe catalogue, inventory sync, multi-app cohabitation. | Milestone `m-2`. |
| **Phase 4 — Platform Expansion** | V-1..V-7 + F-2 — iOS Admin, Messenger, Full Album, Android TV, Wearables, Caregiver invite, Audit Log, Capability Registry. | Milestone `m-3`. |
| **Phase 5 — Long-term Parking Lot** | L-1..L-15 — B2B clinics, marketplace, AI providers, self-hosted infra, backup, social recovery, etc. Активируются по сигналам рынка. | Milestone `m-4` (label `parking-lot`). |

**Critical path для production**: F-3 → F-CRYPTO → F-4 → F-5b/c/5 → S-1 → S-3 → S-4 → S-5 → S-6 → S-8 → soft launch gate → public release.

---

## Soft launch gate (release process)

**Обязательный** перед public release:

1. **MVP code-complete** (все Phase 1 + Phase 2 done).
2. **Soft launch с 5-10 друзьями** в течение **2 недель**.
3. Все P0/P1 bugs найденные friends — fixed.
4. **OEM matrix smoke**: Pixel + Samsung One UI + Xiaomi MIUI.
5. **Privacy Policy text** published.
6. **Account deletion flow** (S-6) tested end-to-end.
7. **Property-based crypto tests** все green.
8. **Friend crypto review** для F-CRYPTO / F-5 — done.
9. **`checklist-security` review** для всех F + S — passed.
10. → **Public release** + blogger outreach (user's primary security strategy).

---

## Security strategy

**Primary**: blogger / influencer outreach post-launch — рассчитываем что они найдут оставшиеся issues.

**Pre-launch mitigations** (бесплатные, не заменяют blogger outreach):
- `checklist-security` skill активируется через `procedure-assess-spec-complexity` для всех spec'и с crypto/auth/data flow surface.
- Friend crypto review для всех crypto-heavy спек.
- Property-based crypto tests (mandatory в Local Test Path): sign→tamper→verify fails; encrypt same K → different ciphertext; replay protection.
- Soft launch gate (см. выше).

**Acceptable risk в MVP** (closed only by blogger outreach):
- Heavy cryptographic side-channel attacks (timing, power analysis).
- Memory leaks с sensitive data после app close.
- Sophisticated multi-step race conditions.

---

## Exit ramps (one-way doors с regret conditions)

Каждый существенный one-way door в архитектуре имеет план отступления. Краткий summary:

| Decision | Exit ramp |
|---|---|
| Family Group + envelope encryption | Migrate to Signal-style group crypto (Phase 5 L-9). Pair-keys + envelope остаются как fallback. |
| Capability Registry pattern | Registry полезен сам по себе для intent dispatch, даже без AI implementations. |
| Wizard module + nested templates | Деградация до direct setup-screens возможна, без manifest layer — потеря reuse. |
| Family monthly subscription | Data model поддерживает individual / per-group tier additively. |
| Universal Preset Architecture | Split на independent apps возможен, без архитектурной катастрофы. |
| Android Vitals primary crash source | CrashReporter port позволяет добавить любой adapter (Sentry — L-4). |
| Google Sign-In для admin | AuthProvider port (F-4) позволяет сменить provider (Apple, Phone, Email, SSO). |
| Server-arbitration model | Если scale issue — migrate к signed-chain membership ledger. |
| Cloudflare Worker как backend | Migrate на own server (server-roadmap.md). |
| 30-day grace period для deletion | Configurable per region post-MVP. |
| Localization initial set | Add/remove на основе market signals; system locale fallback to EN. |
| Notification minimization (rule 10) | Каждый case подлежит review; можно ослабить если critical event миссится. |
| Performance gates (1s cold start) | Per-flavor adjustable; release blocker только для main user-facing flows. |
| ionspin libsodium-kmp | Любой Kotlin/Native crypto lib через AeadCipher port — F-CRYPTO одна неделя rewrap. |

---

## Watch list — тренды которые отслеживаем без коммитмента

Добавлено 2026-07-07 per crypto audit. Это **не roadmap**, а **радар** — вещи, которые проверяем раз в квартал, чтобы вовремя реагировать. Не блокирует MVP.

### MLS at scale — pre-clinic gate (item #7, Тема 7)

**Что отслеживаем**: MLS overhead для 100-member группы (clinic use case: 20 patients × 5 caregivers).
- Bandwidth Welcome message при join в большую группу.
- Epoch counter overflow (~4B commits — не проблема практически, но осведомлённость).
- Cross-group operations («выбрать контакт из семейной группы для добавления в клиническую»).

**Триггер validation**: перед первым clinic-preset paying customer или перед enterprise sales conversation. До этого — family scale 5-7 работает out-of-the-box с openmls.

**Where to look**: openmls issue tracker, MLS WG interop tests, Wire performance benchmarks (public reports).

### Decentralized transport — long-term substitution (item #8, CANDIDATE-9)

**Что отслеживаем**: возможность заменить Cloudflare Worker + Firestore на **decentralized** transport, если vendor lock-in станет реальной проблемой.

- **Nostr / Marmot** (Quartz KMP) Q4 2026 — единственный KMP-native MLS если созреет.
- **Iroh + p2panda-encryption** — future decentralized transport.
- **Matrix federation** — proven pattern, но overhead для наших use cases.

**Триггер evaluation**: (a) Cloudflare pricing change, ломающий unit economics; (b) regulatory pressure на centralized providers в конкретных регионах; (c) появление production-ready KMP-native MLS library с audit.

**Where to look**: Nostr NIPs, Iroh releases, p2panda spec, KMP MLS libraries state (annual audit).

---

## Связь с другими документами

- **Use-cases и D-вопросы**: [`docs/product/use-cases/`](use-cases/README.md) — обязательная база для понимания vision.
- **Decisions**: [`docs/product/decisions/`](decisions/) — ADR-подобные one-way door рассуждения.
- **Конституция**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md).
- **Engineering rules**: [`CLAUDE.md`](../../CLAUDE.md) (rules 1-10 + refuse patterns 1-13).
- **Server roadmap**: [`docs/dev/server-roadmap.md`](../dev/server-roadmap.md) (SRV-* tasks).
- **ADRs**: [`docs/adr/`](../adr/).
- **Operational план**: **Backlog.md** (запусти `backlog browser` или `backlog overview`).

---

## История

- **2026-05-28** — Полная перезапись roadmap'а от «launcher для пожилых» к **Family Care Ecosystem**. Phase 0 vision discussion с 28 D-вопросами закрыта 2026-05-27/28.
- **2026-06-15** — Deferred cloud architecture (6 decisions): Sign-In deferred, config ownership per device, billing cloud-only, QR pairing primary, wire-format versioning, app launch simplification. MVP теперь = Phase 2 + Phase 3.
- **2026-06-15 v3** — F-2 Capability Registry отложен в Phase 4. S-9 Phone Health Monitoring + S-10 Subscription Server Timer добавлены в Phase 2. V-6 Caregiver moved from S-7 в Phase 4 (требует больше инфры).
- **2026-06-17** — F-CRYPTO (spec 016) реализован. Validation strategy: RFC KAT + Wycheproof + property + industrial reference.
- **2026-06-18** — F-4 AuthProvider (spec 017) merged. Anonymous Firebase Auth удалён. Multi-app cohabitation → Phase 3 P-10.
- **2026-06-19** — F-5 переопределён как single-owner encryption + recovery (Google Sign-In + passphrase в Firestore + Android Autofill).
- **2026-06-20** — F-5b own config E2E (envelope variant) реализован. Spec 008 retired. F-5c FCM push rescoped.
- **2026-06-22** — F-5 (spec 020) Root Key Hierarchy + Recovery — в работе.
- **2026-06-23** — Roadmap.md заменён на этот vision.md. Операционный план перенесён в Backlog.md (48 task'ов, 5 milestones, граф зависимостей).

---

**Конец vision.md. Для конкретного «что делать следующего» — запусти `backlog browser`.**
