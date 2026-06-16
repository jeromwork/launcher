# Checklist: notification-minimization

**Spec**: [`spec.md`](../spec.md) (F-3 Wizard Module + Localization + Senior UI Kit)
**Run date**: 2026-06-16
**Verdict**: 5 ✓ / 0 ⚠ / 0 ✗ + 15 N/A — trivially clean (F-3 не introduces push notifications)

> **Context**: F-3 — foundation; не introduces system push notifications. Только in-app UI overlays (hint, dialog) + diagnostic events для analytics. POST_NOTIFICATIONS pool entry — metadata для S-1, не actual push sending.

---

## Inventory

- [✓] **CHK001** Spec listsтет every notification event.
  - **In-app overlays** (US-7 + dialog patterns):
    - TutorialHint overlay (US-7, FR-023)
    - Hard-fail dialog (FR-016)
    - Self-attest dialog (FR-058)
    - Rationale screen (FR-008a)
  - **Diagnostic events** (analytics, не user-facing):
    - `wizardStarted`, `wizardStepCompleted`, `wizardCompleted`, `wizardCancelled` (A-17)
    - `wizardStepDenied(settingId, isPermanent)` (FR-008a)
  - **System push notifications**: **НЕТ** — F-3 не sends push notifications.

- [✓] **CHK002** Tier declared per event.
  - Все events explicitly in-app (overlay/dialog) или diagnostic (no user-facing). ✓

## System push justification

- [N/A] **CHK003-008** No push notifications. Не applicable.

## In-app indicators

- [✓] **CHK009** Low-urgency → in-app indicator.
  - F-3 TutorialHint mechanism — in-app overlay (FR-023), не push. ✓
  - Per CLAUDE.md rule 10 hierarchy correct.

- [✓] **CHK010** Indicator placement declared.
  - TutorialHint anchor: `HintAnchor` parameter (FR-023). Overlay rendered near anchor. ✓

- [✓] **CHK011** Indicators dismissible.
  - TutorialHint dismissed через «Понял» button. Persistent dismissed flag (FR-024). ✓
  - **Senior-friendly note**: explicit acknowledgement через button — acceptable pattern (avoids accidental dismissal по time-out).

## In-app notification center

- [N/A] **CHK012-013** F-3 не has accumulated events / notification center.

## Edge handling

- [N/A] **CHK014** Push fail handling — F-3 не sends push.

- [N/A] **CHK015** Critical events non-push fallback.
  - F-3 не has critical events (SOS, security incidents). N/A.

- [✓] **CHK016** No notification depends on POST_NOTIFICATIONS.
  - F-3 не depends на POST_NOTIFICATIONS для функционирования. POST_NOTIFICATIONS pool entry (FR-053a) — metadata для S-1 usage, не F-3 dependency. ✓

## Privacy / lock-screen

- [N/A] **CHK017-018** F-3 не sends push с PII payloads.

## Acceptance evidence

- [N/A] **CHK019** Notification matrix — F-3 has no system push; in-app overlays self-documented in FR's.
- [N/A] **CHK020** Push permission denied tests — F-3 не sends push.

---

## Резюме

**5 ✓ / 0 ⚠ / 0 ✗ + 15 N/A** — F-3 notification-minimization **trivially clean**. F-3 не sends system push; все user-facing events — in-app overlays / dialogs per CLAUDE.md rule 10 hierarchy.

**No spec edits required**.
